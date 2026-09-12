# ai-agent 智能体模块设计文档

> **状态**：设计完成（第 1-8、10 节已确认，待用户审阅全文 → writing-plans）
> **日期**：2026-09-12
> **参考**：编程导航《SpringAI + RAG + MCP 全栈实战》课程思路
> **恢复方式**：新会话打开本文档，从「0.4 下一步」继续

---

## 0. 进度与上下文（新会话交接区）

### 0.1 已确认决策（用户逐项拍板，勿重复询问）

| #   | 决策点       | 结论                                                                                                            |
| --- | ------------ | --------------------------------------------------------------------------------------------------------------- |
| 1   | 产品形态     | 平台化通用知识库问答（迷你版 Dify），用户上传什么就答什么                                                       |
| 2   | 大模型接入   | 云端 API（OpenAI 兼容协议：DeepSeek / 智谱 / 通义；细化见 4.4——chat/embedding 同厂商，换 embedding 需全量重建） |
| 3   | 向量数据库   | Redis 向量检索（`spring-ai-starter-vector-store-redis`，HNSW + metadata 过滤）                                  |
| 4   | 自主学习含义 | RAG 动态知识库（上传即学习）+ 多轮对话记忆；不做模型微调                                                        |
| 5   | MVP 范围     | 核心闭环（知识库 CRUD + 文档上传解析 + SSE 流式问答 + 对话历史）+ 前端界面 + 引用溯源 + MCP Server              |
| 6   | MCP 角色     | MCP Server 模式（暴露知识库工具给 Claude Desktop / Cursor 等外部 AI 客户端）                                    |
| 7   | 代码组织     | **新建 Maven 模块 `ai-agent`**（用户明确要求工程化，不采纳 ai-web 内分包方案）                                  |

### 0.2 关键技术验证结论（已用 Context7 查证）

- **Spring AI 2.0.x 支持 Spring Boot 4.0.x / 4.1.x**（项目是 Boot 4.1.0，兼容 ✅）
- BOM：`org.springframework.ai:spring-ai-bom:2.0.0`
- Redis 向量库：`spring-ai-starter-vector-store-redis`，支持 HNSW、COSINE、`filterExpression("kbId == 'xx'")` metadata 过滤、`initialize-schema`
- MCP Server：`spring-ai-starter-mcp-server-webmvc`（项目是 WebMVC 栈），`@McpTool` + `@McpToolParam` 注解暴露工具，`protocol: STREAMABLE`（SSE 传输自 2.0.0 起已 deprecated），`type: SYNC`
- OpenAI 兼容：`spring-ai-starter-model-openai`，`spring.ai.openai.base-url` 指向国内厂商

### 0.3 项目现状（复用清单）

| 已有资产              | 位置                                             | 复用方式                                      |
| --------------------- | ------------------------------------------------ | --------------------------------------------- |
| 统一响应/异常         | ai-basic `common` / `exception`                  | 直接用 `ResultUtils` / `BusinessException`    |
| 登录态                | ai-basic `UserContext` + ai-web `JwtInterceptor` | 问答/管理接口自动获得当前用户                 |
| 审计日志              | ai-basic `@AuditLog` AOP                         | 敏感操作（上传/删除知识库）挂注解             |
| Redis 依赖            | ai-web pom 已有 `spring-boot-starter-data-redis` | 需补连接配置；向量库 starter 在 ai-agent 引入 |
| 前端��础设施          | ai-java-front `api/request.ts` + 路由守卫        | 新增 ai/ 页面与 api 模块                      |
| MyBatis-Flex 代码生成 | ai-web codegen                                   | TableDef 输出目标���为 ai-agent 模块          |

### 0.4 下一步（新会话从这里继续）

```
1. ✅ 第 3 节数据库设计（5 张表 + 3.4 删除流程）→ 用户已确认（含评审修订：MEDIUMTEXT、复合索引、级联删会话、同步删除顺序约束）
2. ✅ 第 4 节核心组件设计（摄取管道 / RAG 问答 / MCP 工具）→ 用户已确认（含评审修订：@Async 事务后触发、查询侧过滤脏向量、同厂商限制）
3. ✅ 第 5 节：API 接口定义 → 用户已确认（含 10 条决策：SSE 协议、双层上传限制、MCP token 等）
4. ✅ 第 6 节：前端页面设计 → 用户已确认（含评审修订：流取消、SSE 解析规范、重发语义）
5. ✅ 第 7/8 节（依赖 + 配置，含 Redis 持久化与 maxmemory 策略）+ 第 10 节验证方式 → 全文自审完成
6. ⏳ 用户审阅全文 → git commit 设计文档
7. 调用 superpowers:writing-plans 生成实施计划
8. 按 spec-workflow 规则：实现代码先写入本文档第 9 章，用户手动输入项目
```

---

## 1. 需求概要

特定领域的 AI 问答助手，平台化通用知识库问答能力：

- **用户故事**：登录用户创建知识库 → 上传文档（PDF/Word/MD/TXT）→ 系统异步解析切分向量化入库 → 用户在聊天界面提问 → 系统检索知识库片段拼装 Prompt → 大模型流式生成答案 → 展示引用来源
- **多轮对话**：会话内保留最近 N 轮上下文
- **引用溯源**：回答附带命中的文档片段来源，可展开查看原文
- **MCP Server**：外部 AI 客户端（Claude Desktop/Cursor）通过 MCP 协议即插即用本平台知识库
- **明确不做（YAGNI）**：模型微调、用户反馈闭环调优、多模态、Agent 工具调用（MCP Client）、知识库分享协作、计费

## 2. 模块架构设计（✅ 用户已确认）

### 2.1 Maven 模块结构

```
ai-java（根 POM，聚合 + dependencyManagement 统一版本）
├── ai-basic      # 已有：统一响应/异常/上下文/审计/工具类
├── ai-agent      # ⭐ 新增：AI 智能体模块
│   └── 依赖：ai-basic + Spring AI 2.0（openai starter + redis vector store
│            + mcp server webmvc）+ Apache Tika
└── ai-web        # 应用装配层：启动类、application.yml、Security/JWT/拦截器、Knife4j
    └── 依赖：ai-agent
```

**依赖铁律**：`ai-web → ai-agent → ai-basic` 单向依赖，禁止反向。ai-web 只做装配，AI 业务全部内聚在 ai-agent。

### 2.2 ai-agent 包结构（包根 `com.ai.aijava.agent`）

```
com.ai.aijava.agent
├── config/        # RagVectorStoreConfig（Redis 向量库定制）、AgentProperties（模型/检索参数）
├── controller/    # KnowledgeBaseController / KnowledgeDocController / ChatController
├── service/
│   ├── KnowledgeBaseService      # 知识库 CRUD
│   ├── DocumentIngestService     # 摄取管道编排（异步状态机）
│   ├── RagChatService            # RAG 检索 + 流式问答 + 引用溯源
│   └── mcp/ KnowledgeMcpTools    # @McpTool 工具
├── pipeline/      # DocumentParser（Tika 统一解析）+ ChunkSplitter（切分封装）
├── mapper/        # Mapper（ai-web 启动类 @MapperScan 覆盖 com.ai.aijava.**.mapper）
├── entity/        # 实体 + TableDef（生成器产出，输出目标指向 ai-agent）
├── dto/request/   # 入参 DTO
├── dto/vo/        # 出参 VO（含 ChatMessageVO、CitationVO）
└── enums/         # DocStatus（UPLOADED/PROCESSING/COMPLETED/FAILED）等
```

启动类在 `com.ai.aijava`（ai-web），自动扫到 `com.ai.aijava.agent.*`，无需额外 @ComponentScan。

### 2.3 整体数据流

```
┌─ 前端 ai-java-front ─────────────────────────────────────────────┐
│ 聊天界面(SSE打字机) / 知识库管理 / 文档上传+进度轮询 / 引用溯源面板 │
└────────────────────────┬─────────────────────────────────────────┘
                         │ HTTP / SSE
┌─ ai-web（装配）────────▼─────────────────────────────────────────┐
│ JWT 鉴权 → UserContext / @AuditLog / 全局异常 / BaseResponse      │
├─ ai-agent（业务）────────────────────────────────────────────────┤
│ ① 摄取：上传 → Tika 解析 → 切分 → Embedding → Redis 向量入库     │
│         （异步任务 + DocStatus 状态机 + MySQL 存业务态）          │
│ ② 问答：提问 → KNN 检索(kbId 过滤) → Prompt 拼装(系统+知识+历史)  │
│         → ChatModel 流式生成 → SSE 推送 → 对话落库 + 引用返回     │
│ ③ MCP：@McpTool 暴露知识库检索，Claude Desktop/Cursor 即插即用    │
└────────┬──────────────────────┬─────────────────┬────────────────┘
     MySQL（新表）        Redis 8（HNSW 向量）   云端 LLM API
     业务态+切片原文+对话  kbId metadata 隔离     DeepSeek/智谱/通义
```

### 2.4 工程化关键决策

1. **双存储分工**：MySQL 是 source of truth（文档/切片/对话），Redis 只存向量与索引；Redis 可随时从 MySQL 重建索引
2. **多知识库隔离**：向量 metadata 带 `kbId`，检索时 filterExpression 过滤，一套 HNSW 索引服务全部知识库
3. **摄取异步化**：解析+向量化可能超 10s，异步状态机（UPLOADED → PROCESSING → COMPLETED/FAILED）规避网关超时与重复上传
4. **MCP 端点鉴权**：MCP 端点（STREAMABLE 单端点，默认 `/mcp`）不走 JWT（客户端是程序非登录用户），JwtInterceptor 白名单放行，由 `McpSecurityInterceptor` 校验 `X-MCP-Token`（见第 8 节）

## 3. 数据库设计（✅ 用户已确认）

### 3.1 表关系

```
user ──1:n── knowledge_base ──1:n── knowledge_document ──1:n── document_chunk
                     │                    (状态机)              (冗余 kb_id)
                     │
                     └──1:n── chat_session ──1:n── chat_message
                          (会话绑定单库)        (citations 内嵌 JSON 快照)
```

### 3.2 DDL

```sql
-- 知识库表
CREATE TABLE `knowledge_base` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '知识库ID',
    `name`        VARCHAR(64)  NOT NULL COMMENT '知识库名称',
    `description` VARCHAR(256) DEFAULT '' COMMENT '知识库描述',
    `user_id`     BIGINT       NOT NULL COMMENT '创建者用户ID',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库表';

-- 知识库文档表（摄取状态机：UPLOADED → PROCESSING → COMPLETED/FAILED）
CREATE TABLE `knowledge_document` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '文档ID',
    `kb_id`         BIGINT       NOT NULL COMMENT '所属知识库ID',
    `file_name`     VARCHAR(256) NOT NULL COMMENT '原始文件名',
    `file_type`     VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '文件类型（pdf/docx/md/txt）',
    `file_size`     BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    `file_path`     VARCHAR(512) NOT NULL DEFAULT '' COMMENT '服务器存储路径',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'UPLOADED' COMMENT '处理状态（DocStatus枚举名）',
    `error_message` VARCHAR(512) DEFAULT NULL COMMENT '处理失败原因（FAILED时）',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_kb_status_time` (`kb_id`, `status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档表';

-- 文档切片表（向量只存 Redis，原文存此表用于引用溯源与索引重建）
CREATE TABLE `document_chunk` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '切片ID',
    `doc_id`      BIGINT      NOT NULL COMMENT '所属文档ID',
    `kb_id`       BIGINT      NOT NULL COMMENT '所属知识库ID（冗余，清理向量时免join）',
    `chunk_index` INT         NOT NULL COMMENT '切片序号（文档内从0递增）',
    `content`     MEDIUMTEXT  NOT NULL COMMENT '切片原文',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_doc_index` (`doc_id`, `chunk_index`),
    INDEX `idx_kb_id` (`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档切片表';

-- 对话会话表
CREATE TABLE `chat_session` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '会话ID',
    `user_id`     BIGINT       NOT NULL COMMENT '所属用户ID',
    `kb_id`       BIGINT       NOT NULL COMMENT '关联知识库ID（一个会话绑定一个知识库）',
    `title`       VARCHAR(128) NOT NULL DEFAULT '' COMMENT '会话标题（默认取首条提问前20字符）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后活跃时间',
    PRIMARY KEY (`id`),
    INDEX `idx_user_update` (`user_id`, `update_time`),
    INDEX `idx_kb_id` (`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话会话表';

-- 对话消息表（追加型，无 update_time；idx_session_id 隐含主键后缀，等效 (session_id, id)）
CREATE TABLE `chat_message` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    `session_id`  BIGINT      NOT NULL COMMENT '所属会话ID',
    `role`        VARCHAR(16) NOT NULL COMMENT '角色（user/assistant）',
    `content`     MEDIUMTEXT  NOT NULL COMMENT '消息内容',
    `citations`   MEDIUMTEXT  DEFAULT NULL COMMENT '引用JSON数组（仅assistant消息：[{docId,docName,chunkIndex,content,score}]）',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话消息表';
```

### 3.3 关键设计决策

| #   | 决策                                 | 理由                                                                                                |
| --- | ------------------------------------ | --------------------------------------------------------------------------------------------------- |
| 1   | 物理删除 + 级联清理                  | 一致性由 3.4 删除流程的顺序约束保证；`@AuditLog` 只负责操作追溯（谁删了什么），不承担数据一致性     |
| 2   | 删 KB 级联删会话+消息                | 会话绑定单库是 MVP 决策的自然延伸，避免 kb_id 悬空与 citations 反查已删文档；删库后历史对话随库消亡 |
| 3   | chunk 表不存向量                     | 向量只在 Redis HNSW；索引丢失时用 MySQL 原文重新 embedding 重建                                     |
| 4   | embedding 模型全局固定               | kb 不做模型版本管理；换 embedding 模型 = 全量重建索引（配置变更，非数据迁移）                       |
| 5   | status 存枚举名（VARCHAR）           | 与 `DocStatus` 枚举一一对应，可读性好，风格同 `audit_log.operation_type`                            |
| 6   | chunk 表冗余 kb_id                   | 删整个知识库时按 kbId 批量清向量与记录，免 join document 表                                         |
| 7   | 不冗余 doc_count / chunk_count       | MVP 数据量小，列表页 `GROUP BY count` 实时统计；数据量上来后再加缓存或冗余（演进项，非 MVP）        |
| 8   | citations 内嵌 JSON（MEDIUMTEXT）    | 引用是内容快照（含片段原文节选），只随消息展示、不反查已删文档，不建关联表（YAGNI）                 |
| 9   | 会话绑定单个知识库（kb_id NOT NULL） | MVP 交互最简单：进入某知识库后开始问答；跨库问答不做                                                |
| 10  | content/citations 用 MEDIUMTEXT      | Tika 解析的极端大片段与 LLM 长回答可能逼近 TEXT 64KB 上限，MEDIUMTEXT 零成本防御                    |

### 3.4 删除流程（同步删除 + 顺序约束 + 幂等）

Redis / MySQL / 磁盘三方无法共事务，通过固定删除顺序把失败方向收敛为"少检索、不污染"：

**删除知识库**：

```
① 前置：查出该 KB 下所有文档的 file_path + 所有 chunkId 列表并缓存（MySQL 删除后就没了）
② 删 Redis 向量：vectorStore.delete(chunkIdList) 按 id 分批删（每批 ≤500）；
   （向量 Document id = chunkId，见 4.1 决策 1；按 id 删除是 VectorStore 基础必选实现，
    优于按 filterExpression 删除的各实现差异）
   失败则整体中止（BusinessException），不删 MySQL
③ 删 MySQL 记录（单事务）：
   chat_message → chat_session → document_chunk → knowledge_document → knowledge_base
   （按 session_id / kb_id 依赖顺序删除；失败可重试，此时向量已删，检索已安全）
④ 删磁盘文件：按 ① 缓存的路径删；文件不存在则忽略；失败仅记 error 日志，不影响检索
```

**删除文档**：

```
① 前置：缓存该文档 file_path + chunkId 列表（查 document_chunk.id，按 doc_id 走 uk_doc_index 前缀）
② 删 Redis 向量：vectorStore.delete(chunkIdList) 分批删；失败中止
③ 删 MySQL 记录：document_chunk → knowledge_document（单事务）
④ 删磁盘文件：同上
```

**失败语义**（每步幂等，重试安全）：

| 失败点     | 状态                     | 影响                   | 恢复方式           |
| ---------- | ------------------------ | ---------------------- | ------------------ |
| Redis 删除 | MySQL/文件均在，向量仍在 | 检索仍能召回（未删成） | 用户重试删除       |
| MySQL 删除 | 向量已删，记录残留       | 检索不到（安全侧）     | 用户重试删除       |
| 文件删除   | 记录已删，文件残留       | 仅磁盘垃圾，不影响检索 | 记日志，可人工清理 |

## 4. 核心组件设计（✅ 用户已确认）

### 4.1 摄取管道（DocumentIngestService + pipeline 包）

```
POST /kb/{kbId}/document/upload（MultipartFile）
  → 校验（类型白名单/大小/KB归属）→ 存盘 uploads/{kbId}/{uuid}.{ext}
  → insert knowledge_document（status=UPLOADED）→ 立即返回 docId
  → @Async("ingestExecutor") 异步摄取：
       status=PROCESSING（先置位，防前端轮询误判）
       ① DocumentParser：Tika ��取纯文本（pdf/docx/md/txt 统一入口）
       ② ChunkSplitter：TokenTextSplitter 切分（chunkSize 可配，默认 800 token）
       ③ chunks 批量 insert document_chunk（拿到自增 id）
       ④ 构建 Spring AI Document(id=chunkId, content, metadata={kbId, docId})
          → vectorStore.add()（内部自动调 EmbeddingModel 批量向量化）
       status=COMPLETED
       └ 异常 → status=FAILED + error_message（截断 512）
```

| #   | 决策                                                           | 理由                                                                                                                                         |
| --- | -------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | chunk 自增 id 直接作向量 Document id                           | 引用溯源时从检索结果直接得 chunkId，免二次映射                                                                                               |
| 2   | 先 insert chunk 拿 id，再 add 向量                             | add() 自动 embedding，无需手动调 EmbeddingModel                                                                                              |
| 3   | FAILED 文档只能删除重传，不做"重新处理"接口                    | 重处理需清旧 chunk+向量+状态回滚（幂等复杂度），MVP 不值得；删除走 3.4 级联清理（含向量+文件）                                               |
| 4   | 专属线程池 ingestExecutor（core 2/max 4/queue 100/CallerRuns） | 摄取含外部 embedding API 调用（秒级），与 auditLogExecutor 隔离；队列满退化为同步摄取，上传接口多等几秒但不丢任务（MVP 可接受）              |
| 5   | @Async 触发点在 insert 提交之后                                | 上传方法不开外层事务（单条 insert 自动提交），异步线程必能查到 UPLOADED 记录；若未来加事务，改用 TransactionSynchronization afterCommit 触发 |
| 6   | 向量 add 部分失败由查询侧过滤兜底（见 4.2 ③）                  | `vectorStore.add(List)` 批量写��非严格原子，FAILED 文档可能残留部分向量；不依赖删除侧假设，检索时按 docId 回查 status 只保留 COMPLETED       |

### 4.2 RAG 问答（RagChatService）

```
POST /chat/session/{sessionId}/send（question）→ SSE 流
  ① 查历史：chat_message 按该会话取最近 N 轮（id desc limit 2N → 反转为时间正序，
     只取 role/content 必要字段，不含当前问题；
     丢弃末尾连续的孤立 user 消息——流失败遗留的无 assistant 应答 user 不进 history）
  ② 落库当前 user 消息（在查历史之后，避免当前问题重复进入 history；
     若会话 title 仍为默认值"新会话"，顺带更新为问题前 20 字）
  ③ 检索（带过采样）：vectorStore.similaritySearch(
        SearchRequest.query(question).topK(topK * 2)
        .filterExpression("kbId == '{kbId}'"))
     → 按 docId 批量回查 knowledge_document（取 docName；只保留所属文档 status=COMPLETED 的 hits）
     → 截取前 topK 条（过采样防 topK 全为脏向量时误答"无资料"）
  ④ 引用构建：citations = [{chunkId, docId, docName, chunkIndex, content节选, score}]
  ⑤ Prompt 手动拼装（不用 QuestionAnswerAdvisor）：
     system  = "你是知识库问答助手，仅依据参考资料回答；资料未覆盖时明确说明"
     user    = "参考资料：\n[1] {chunk1}\n[2] {chunk2}...\n\n问题：{question}"
  ⑥ chatClient.prompt().system().user().messages(history)
        .stream().content() → Flux<String>
  ⑦ doOnComplete：assistant 消息 + citations 落库、会话 update_time 刷新
     → 推送 citations 事件 → end 事件
```

**SSE 事件协议**：

| event     | data                | 说明                                                                                                         |
| --------- | ------------------- | ------------------------------------------------------------------------------------------------------------ |
| message   | delta 文本片段      | 前端打字机追加                                                                                               |
| citations | citations JSON 数组 | 流结束时装载，前端展开溯源面板                                                                               |
| end       | -                   | 流正常结束                                                                                                   |
| error     | 错误信息            | 流异常；assistant 消息不落库（MVP 不存半截回答），user 消息已落库，前端需将本条标记为"回答失败"（见第 6 节） |

| #   | 决策                                         | 理由                                                                                                                        |
| --- | -------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- |
| 1   | 手动检索 + 手动拼 Prompt，不用 Advisor       | 引用溯源需要 score/chunkId/docName 定制结构，内置 RAG Advisor 不透明                                                        |
| 2   | 对话记忆直接查 chat_message，不用 ChatMemory | 历史本就落库，查最近 N 轮拼 messages，无内存态/双写一致性问题；顺序约束：先查历史（排除当前问题）→ 落 user 消息 → 拼 Prompt |
| 3   | 检索后按 docId 回查过滤 status=COMPLETED     | 兜底两类脏向量：摄取批量部分失败（FAILED 文档残留向量）、删除流程竞态残留；回查本为取 docName，过滤零额外成本               |
| 4   | 检索 0 命中仍调模型                          | system 提示约束其说明"未在知识库找到直接依据"，不返回 citations                                                             |
| 5   | 检索过采样 topK × 2，过滤后截前 topK         | topK 全为脏向量时过滤后为 0，表现为"有资料却答不知道"；过采样成本一次多余检索，消除该缺陷                                   |
| 6   | history 丢弃末尾连续孤立 user 消息           | 流失败遗留的无应答 user 若进 history，模型看到连续 user 无 assistant；只丢末尾连续段（中间孤立轮不影响语义，保留真实历史）  |
| 7   | Flux 装配处 `onErrorResume` 转 error 事件    | SSE 响应已 200 提交，`GlobalExceptionHandler` 接不住流内异常；不兜底则连接直接断，前端只见"网络错误"而非"回答失败"          |

### 4.3 MCP Server（KnowledgeMcpTools）

```
@McpTool("搜索平台知识库，返回最相关的原文片段")
searchKnowledge(query, kbId 可选, topK 可选默认5)
  → kbId 缺省 = 跨库检索（自托管平台，MVP 接受）

@McpTool("列出平台全部知识库")
listKnowledgeBases()
  → [{kbId, name, description}]
```

- `/mcp/**` 不走 JWT（客户端是程序），JwtInterceptor 白名单放行；预留简单 token 校验配置位（第 8 节）
- 工具返回纯文本（片段 + 来源标注），同步 type: SYNC
- 传输用 STREAMABLE（MCP 2025-03 规范，单一端点）；若外部客户端仅支持 SSE，配置改回 `protocol: SSE`（2.0 已标记 deprecated，兜底可用）

### 4.4 模型接入关键决策：chat 与 embedding 同厂商

**问题**：`spring-ai-starter-model-openai` 的 chat 与 embedding 共用一个 `base-url`。DeepSeek 无公开 embedding 端点，若 chat 用 DeepSeek、embedding 用智谱，单一 starter 配不下来。

**决策**：chat 与 embedding 选**同一家 OpenAI 兼容厂商**。候选：智谱（`glm-4-flash` 免费 + `embedding-3`）、通义（`qwen-turbo` + `text-embedding-v3`）���

**关键限制**："只改 base-url + model + embedding-model 三项"仅适用于**空库或新库**。换 embedding 厂商/模型后，不同模型维度与语义空间不兼容、不能混用，已有向量必须**全量重嵌入并重建索引**（呼应 3.3 决策 #4）。若未来确需 chat/embedding 双厂商，手动注册两个 `OpenAiChatModel`/`OpenAiEmbeddingModel` Bean（演进项，非 MVP）。

### 4.5 AgentProperties 可配参数

| 参数                   | 默认            | 说明           |
| ---------------------- | --------------- | -------------- |
| `agent.upload-dir`     | `./uploads`     | 文件存储根目录 |
| `agent.allowed-types`  | pdf,docx,md,txt | 上传类型白名单 |
| `agent.max-file-size`  | 20MB            | 单文件上限     |
| `agent.chunk-size`     | 800             | 切分 token 数  |
| `agent.top-k`          | 5               | 检索返回条数   |
| `agent.history-rounds` | 10              | 携带历史轮数   |
| `agent.system-prompt`  | （内置默认）    | RAG 系统提示词 |

## 5. API 接口定义（✅ 用户已确认）

### 5.1 知识库管理（KnowledgeBaseController，`/kb`）

| 方法   | 路径         | 说明                                   | 审计                     |
| ------ | ------------ | -------------------------------------- | ------------------------ |
| POST   | `/kb/create` | 创建知识库（name, description）        | `@AuditLog(DATA_CREATE)` |
| GET    | `/kb/list`   | 我的知识库列表（含 docCount 实时统计） | -                        |
| POST   | `/kb/update` | 修改名称/描述                          | `@AuditLog(DATA_UPDATE)` |
| DELETE | `/kb/{id}`   | 删除知识库（3.4 级联清理全链）         | `@AuditLog(DATA_DELETE)` |

### 5.2 文档管理（KnowledgeDocController）

| 方法   | 路径                         | 说明                                                 | 审计                     |
| ------ | ---------------------------- | ---------------------------------------------------- | ------------------------ |
| POST   | `/kb/{kbId}/document/upload` | 上传文档（MultipartFile）→ 返回 docId，异步摄取      | `@AuditLog(DATA_CREATE)` |
| GET    | `/kb/{kbId}/document/list`   | 文档列表（含 status/errorMessage，兼作摄取进度轮询） | -                        |
| DELETE | `/document/{docId}`          | 删除文档（级联：向量+chunk+记录+文件，见 3.4）       | `@AuditLog(DATA_DELETE)` |

### 5.3 对话（ChatController，`/chat`）

| 方法   | 路径                                 | 说明                                       |
| ------ | ------------------------------------ | ------------------------------------------ |
| POST   | `/chat/session/create`               | 创建会话（kbId），title 默认"新会话"       |
| GET    | `/chat/session/list`                 | 会话列表（按最后活跃倒序，可按 kbId 过滤） |
| GET    | `/chat/session/{sessionId}/messages` | 历史消息（全量，含 citations）             |
| POST   | `/chat/session/{sessionId}/send`     | **提问 → SSE 流**（4.2 事件协议）          |
| DELETE | `/chat/session/{sessionId}`          | 删除会话（级联删消息）                     |

### 5.4 DTO 清单

| 类型    | 类                           | 字段                                                                                 |
| ------- | ---------------------------- | ------------------------------------------------------------------------------------ |
| Request | `KnowledgeBaseCreateRequest` | name*, description                                                                   |
| Request | `KnowledgeBaseUpdateRequest` | id*, name*, description                                                              |
| Request | `ChatSessionCreateRequest`   | kbId*                                                                                |
| Request | `ChatSendRequest`            | question*                                                                            |
| VO      | `KnowledgeBaseVO`            | id, name, description, docCount, createTime                                          |
| VO      | `KnowledgeDocumentVO`        | id, kbId, fileName, fileType, fileSize, status, errorMessage, createTime, updateTime |
| VO      | `ChatSessionVO`              | id, kbId, kbName, title, createTime, updateTime                                      |
| VO      | `ChatMessageVO`              | id, role, content, citations[], createTime                                           |
| VO      | `CitationVO`                 | chunkId, docId, docName, chunkIndex, content（节选）, score                          |

### 5.5 关键决策

| #   | 决策                                                             | 理由                                                                                                                                                                                |
| --- | ---------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | SSE 接口不走 `BaseResponse` 包装；其余接口统一 `BaseResponse<T>` | 流式协议按 4.2 事件协议；连接建立失败（401/会话不存在）仍走统一异常 → JSON 错误响应                                                                                                 |
| 2   | 列表接口 MVP 不分页                                              | 自托管平台数据量小，全量返回；分页为演进项（复用现有 paginate 模式成本低）                                                                                                          |
| 3   | 不做独立 `/document/{id}/status` 轮询接口                        | 文档列表已含 status + updateTime，上传后轮询 list 即可                                                                                                                              |
| 4   | 归属校验在 Service 层（userId 比对）                             | 所有 KB/文档/会话操作先校验资源属于当前用户，防越权（等保要求）；MCP 工具无用户态，鉴权见 #8                                                                                        |
| 5   | 上传大小双层限制                                                 | 框架层 `spring.servlet.multipart.max-file-size` 拒绝超限；`GlobalExceptionHandler` 增加 `MaxUploadSizeExceededException` 处理，返回友好 JSON；应用层 `agent.max-file-size` 二次校验 |
| 6   | POST SSE 与原生 EventSource 不兼容                               | 浏览器 EventSource 仅支持 GET；前端用 fetch + ReadableStream 解析 SSE 流（见第 6 节），不引第三方库                                                                                 |
| 7   | 路径风格沿用项目现有 RPC 风格（同 `/user/login`）                | 与现有代码一致优先；RESTful 风格统一为演进项，前端按现有风格拼接                                                                                                                    |
| 8   | MCP `/mcp/**` 鉴权                                               | 不走 JWT，但必须配置 token 校验位（`X-MCP-Token` 请求头，yml 配置，未配置时启动打告警日志），详见第 8 节                                                                            |
| 9   | 删除级联向量清理方式                                             | 查 chunkId 列表 → `vectorStore.delete(idList)` 按 id 分批删（≤500/批），见 3.4                                                                                                      |
| 10  | 会话 title 生成规则                                              | 创建时默认"新会话"；首条 user 消息落库后截取前 20 字更新（见 4.2 ②）                                                                                                                |

## 6. 前端设计（✅ 用户已确认）

### 6.1 页面与路由

| 路由                     | 页面（views/ai/）             | 功能                                           |
| ------------------------ | ----------------------------- | ---------------------------------------------- |
| `/kb`                    | `KnowledgeBaseView.vue`       | 知识库卡片列表：创建/编辑/删除（二次确认）     |
| `/kb/:id`                | `KnowledgeBaseDetailView.vue` | 文档管理：上传、状态轮询、删除；入口"开始问答" |
| `/chat?kbId=&sessionId=` | `ChatView.vue`                | 聊天主界面（三栏布局见 6.2）                   |

- 均 `requiresAuth: true`；`/dashboard` 加两张入口卡片（知识库管理 / AI 问答）
- **路由与流的同步**：创建会话后 `router.replace` 更新 `sessionId`（刷新不丢）；`watch` `route.query` 变化时**先 abort 旧 SSE 流、清空半截 assistant 内容与 citations** 再加载新会话���防旧流写入新会话）；`onBeforeUnmount` 调 `AbortController.abort()`

### 6.2 组件拆分（Vue 单文件 ≤200 行约束）

```
views/ai/ChatView.vue                    # 三栏骨架 + 状态编排
components/chat/
  SessionList.vue                        # 左栏：会话列表（按库过滤、新建、删除；title 展示）
  MessageList.vue                        # 中栏：消息气泡 + 打字机渲染 + 失败标记
  ChatInput.vue                          # 底部输入框（Enter 发送 / Shift+Enter 换行）
  CitationPanel.vue                      # 右栏：引用溯源（点击条目展开/折叠该片段原文）

views/ai/KnowledgeBaseDetailView.vue     # 编排
components/kb/
  DocumentTable.vue                      # NDataTable：文件名/大小/状态/操作
  UploadDialog.vue                       # NUpload 上传对话框（类型/大小前端预校验）
composables/
  useChatStream.ts                       # SSE 解析（见 6.3）
  useDocumentPolling.ts                  # 文档状态轮询（见 6.4）
```

### 6.3 API 模块与 SSE 工具

```
api/kb.ts        # createKb/listKbs/updateKb/deleteKb/uploadDocument/listDocuments/deleteDocument（走 axios）
api/chat.ts      # createSession/listSessions/listMessages/deleteSession（走 axios）
composables/useChatStream.ts   # SSE 专用：fetch POST + Authorization 头
                               #   + ReadableStream + TextDecoder 按空行分帧解析 event/data
                               #   回调：onMessage(delta)/onCitations(list)/onEnd/onError
types/ai.ts      # KnowledgeBase / KnowledgeDocument(DocStatus) / ChatSession / ChatMessage / Citation
```

**SSE 解析规范要点**（useChatStream 实现约束）：

- `new TextDecoder("utf-8")` 解码时传 `{ stream: true }`，处理中文等多字节字符跨 chunk 截断
- 帧分隔兼容 `\n\n` 与 `\r\n\r\n`（统一 normalize `\r\n` → `\n` 后再分帧）
- 多行 `data:` 以 `\n` 合并；无 `event:` 字段默认 `message`
- 忽略注释行（`:` 开头）与 `id:` / `retry:` 字段

### 6.4 关键交互

| 场景       | 行为                                                                                                                                                                            |
| ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 上传后进度 | `useDocumentPolling(kbId)`：3s 轮询 `document/list`，存在 `UPLOADED/PROCESSING` 继续，全部终态停止；组件卸载清除定时器；`visibilitychange` 页面不可见暂停、恢复可见立即刷新一次 |
| 打字机     | `message` 事件 delta 追加到当前 assistant 气泡；`citations` 事件填充溯源面板；`end` 收尾                                                                                        |
| 流失败     | user 气泡保留 + 红色"回答失败"标记；**重发 = 将原问题填回输入框由用户手动再发**（新增一条真实 user 消息，历史保真；不做 regenerate 协议）                                       |
| SSE 401    | fetch 响应非 200 时读 JSON 错误体，复用 token 失效逻辑跳登录                                                                                                                    |
| 删除确认   | 知识库/文档/会话删除均 `useDialog().warning` 二次确认（等保敏感操作要求）                                                                                                       |
| 删除后跳转 | 删除当前会话 → 停留 `/chat?kbId=`（该库剩余会话第一个或空态）；删除知识库 → 跳 `/kb`（若正处 `/chat?kbId=` 该库同样跳 `/kb`）                                                   |
| 引用溯源   | 点击 citation 条目展开/折叠该片段原文（chunk content 快照已含于 citations，无需请求）；不提供文档内锚点定位（MVP 无文档预览页）                                                 |

### 6.5 决策表

| #   | 决策                                        | 理由                                                                                    |
| --- | ------------------------------------------- | --------------------------------------------------------------------------------------- |
| 1   | UI 库 Naive UI，按需导入 + `h()` 渲染操作列 | 项目规则强制（`naive-ui.md`）；新组件使用前查 `docs/naiveui-usage.md`，未收录的用后回填 |
| 2   | SSE 用原生 `fetch` + `ReadableStream`       | POST SSE 与 EventSource 不兼容（5.5 #6）；解析逻辑 ~60 行可控，不引第三方库             |
| 3   | MVP 不新建 Pinia store                      | 聊天状态页面内聚，无跨页共享；store 为演进项                                            |
| 4   | 图标包按需安装 `@vicons/ionicons5`          | 规则推荐方案，避免全量图标包                                                            |
| 5   | 文件命名沿用现有 PascalCase                 | 与 `LoginView.vue` 现状一致，优先一致性                                                 |
| 6   | assistant 消息 MVP 纯文本渲染（pre-wrap）   | Markdown 渲染需 sanitize 防 XSS，为演进项；纯文本零风险                                 |
| 7   | 前端类型/大小预校验仅为 UX，后端为权威校验  | 前端硬编码与 `AgentProperties` 默认值一致；配置下发接口为演进项                         |
| 8   | 响应式桌面优先                              | 左栏（会话）/右栏（引用）可折叠；小屏（<768px）两栏改 NDrawer，中栏消息区始终可见       |

## 7. 新增依赖（✅ 清单已定）

### 7.1 后端 Maven

**根 POM** `dependencyManagement` 新增：

```xml
<!-- Spring AI 全家桶版本（兼容 Boot 4.1，已查证） -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-bom</artifactId>
    <version>2.0.0</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
<!-- Tika 统一版本 -->
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-bom</artifactId>
    <version>2.9.4</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

**新建 ai-agent 模块 POM** 依赖：

| 依赖                                                             | 用途                                                                                 |
| ---------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| `com.ai.aijava:ai-basic`                                         | 统一响应/异常/上下文/审计注解                                                        |
| `org.springframework.boot:spring-boot-starter-webmvc`            | 自有 @RestController/MultipartFile 等编译所需（显式声明，不依赖 starter 传递的巧合） |
| `org.springframework.ai:spring-ai-starter-model-openai`          | ChatModel + EmbeddingModel（OpenAI 兼容）                                            |
| `org.springframework.ai:spring-ai-starter-vector-store-redis`    | Redis HNSW 向量库                                                                    |
| `org.springframework.ai:spring-ai-starter-mcp-server-webmvc`     | MCP Server（STREAMABLE 传输，SYNC）                                                  |
| `org.apache.tika:tika-core`                                      | 文档解析入口                                                                         |
| `org.apache.tika:tika-parsers-standard-package`                  | pdf/docx 等标准格式解析器                                                            |
| `com.mybatis-flex:mybatis-flex-core` + `mybatis-flex-annotation` | 实体与 BaseMapper（版本随根 POM）                                                    |

**ai-web POM** 新增：`com.ai.aijava:ai-agent`（依赖铁律：`ai-web → ai-agent → ai-basic` 单向）。

**说明**：

- 主栈仍为 WebMVC；`Flux` SSE 返回值由 spring-ai 传递的 reactor-core 支持，starter 传递的 spring-webflux 仅作 OpenAI API 的 HTTP 客户端，不启用 WebFlux 服务端
- MyBatis-Flex 启动器与 `@MapperScan("com.ai.aijava.**.mapper")` 已在 ai-web，覆盖 ai-agent 的 mapper 包
- **实施阶段第一步验证清单**（写代码前先逐项跑通）：

| #   | 待确认项                                                                                    | 确认方式                                                                      |
| --- | ------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| 1   | `spring-ai-starter-mcp-server-webmvc` vs `...-streamable-webmvc`                            | `mvn dependency:tree -pl ai-agent`（官方文档两处写法不一致，以 BOM 解析为准） |
| 2   | `spring.ai.vectorstore.redis.*` 键名（uri/index-name/prefix/initialize-schema）             | spring-configuration-metadata.json 或 IDE 自动补全                            |
| 3   | `spring.ai.mcp.server.streamable-http.mcp-endpoint` 键名                                    | 同上                                                                          |
| 4   | `spring.ai.openai.embedding.options.dimensions` 是否被 2.0 的 `OpenAiEmbeddingOptions` 支持 | 源码 / 官方文档（不支持则改用 API 默认维度并记录）                            |
| 5   | `JwtInterceptor` 白名单匹配是否在 context-path 之后（配 `/mcp` 还是 `/api/mcp`���           | 阅读现有拦截器实现                                                            |
| 6   | spring-ai-bom 2.0.0 → 2.0.1 可升性                                                          | `mvn versions:display-dependency-updates`                                     |

- Tika 锁 2.9.4 稳定线；spring-ai-bom 本文锁定 2.0.0 GA（2.0.1 已于 2026-08-20 发布，上表 #6 确认后可升）

### 7.2 前端 npm

```bash
npm i -D @vicons/ionicons5
```

（naive-ui / vfonts 已安装；SSE 解析用原生 fetch，无新依赖）

## 8. 配置变更（✅ 已定）

`ai-web/src/main/resources/application-prod.yml` 新增：

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      # password: xxx
  servlet:
    multipart:
      max-file-size: 20MB # 框架层上限（5.5 #5）
      max-request-size: 25MB
  ai:
    openai:
      api-key: ${AI_API_KEY} # 环境变量注入，禁止写入仓库
      base-url: https://open.bigmodel.cn/api/paas/v4 # 智谱；换厂商改这里
      chat:
        options:
          model: glm-4-flash
      embedding:
        options:
          model: embedding-3
          dimensions: 1024 # 显式声明维度（智谱 embedding-3 支持 256/512/1024/2048，默认 2048）；
          #                 维度一经使用不可更改，改则全量重建索引（3.3 决策 #4）；1024 兼顾质量与内存
    vectorstore:
      redis:
        uri: redis://localhost:6379 # Spring AI 向量库独立连接配置（不复用 spring.data.redis）
        index-name: ai-java-kb
        prefix: "kb:vector:"
        initialize-schema: true # 首次启动建 HNSW 索引（维度由 EmbeddingModel 实际输出推断）
    mcp:
      server:
        name: ai-java-knowledge
        version: 1.0.0
        type: SYNC
        protocol: STREAMABLE # SSE 自 2.0.0 deprecated；客户端仅支持 SSE 时改回 SSE（端点随之变为 /sse + /mcp/messages）
        streamable-http:
          mcp-endpoint: /mcp # 实际完整路径 /api/mcp（受 server.servlet.context-path 影响）
          keep-alive-interval: 30s

agent:
  upload-dir: ./uploads
  allowed-types: pdf,docx,md,txt
  max-file-size: 20MB
  chunk-size: 800
  top-k: 5
  history-rounds: 10
  # system-prompt: 覆盖内置默认提示词（可选）

mcp:
  security:
    token: ${MCP_TOKEN:} # MCP 端点的校验 token；为空时启动打告警日志
```

**代码侧配置变更**：

| 变更                                      | 说明                                                                                         |
| ----------------------------------------- | -------------------------------------------------------------------------------------------- |
| ~~JwtInterceptor 白名单~~                 | **无需变更**：拦截器是 `@RequireLogin` 注解驱动（无注解即放行），MCP 端点天然不经过 JWT 校验 |
| `McpSecurityInterceptor`（ai-agent 新增） | 拦截 MCP 端点，校验 `X-MCP-Token` 请求头与 `mcp.security.token` 一致，不一致返回 401         |
| `AgentProperties`（ai-agent）             | 绑定 `agent.*`（见 4.5）                                                                     |
| `GlobalExceptionHandler`（ai-basic）      | 新增 `MaxUploadSizeExceededException` 处理（5.5 #5）                                         |
| `AsyncConfig`（ai-web）                   | 新增 `ingestExecutor` 线程池（4.1 #4）                                                       |

**Redis 运维要求**（部署检查项）：

- 开启持久化，推荐 AOF + `appendfsync everysec`（向量写入频率不高，每秒刷盘足够且丢损可控）
- `maxmemory-policy` 设为 `noeviction`（向量 key 被逐出 = 静默丢数据；该实例当前仅承担向量库，无缓存逐出诉求）
- `maxmemory` 必须显式设置（建议物理内存 60%~70%），不设可能吃满内存导致 Redis OOM
- Redis 8.x（内置 RediSearch，HNSW 向量能力，`initialize-schema` 自动建索引）
- 监控向量索引内存（`FT.INFO ai-java-kb` 的 sizes），chunk 增长快时提前扩容

**MCP 部署注意**：

- `mcp.security.token` 是全局单一 token，持有者可 `listKnowledgeBases` 列出并跨库检索**所有用户**的知识库——token 泄露等同于全平台知识库读权限
- MCP 端点仅限内网或可信客户端访问；公网部署必须叠加网关层 IP 白名单 / mTLS

**换 embedding 模型全量重建 SOP**（3.3 #4 的可执行步骤，维度不匹配时 `initialize-schema` 不会改建好的索引、直接报错）：

```
① 停止应用（最稳妥地停写入）
② redis-cli FT.DROPINDEX ai-java-kb DD   # 删索引 + 关联向量文档
③ 确认 kb:vector:* 前缀 key 已清（SCAN 抽查，残留则手动删）
④ MySQL 清理旧切片与文档记录（MVP 无重摄取接口，文档需重新上传）：
   DELETE FROM document_chunk;
   DELETE FROM knowledge_document;
   （knowledge_base / chat_* 保留，历史对话仍在）
⑤ 修改 yml：embedding model + dimensions
⑥ 启动应用（initialize-schema 按新维度重建索引）→ 用户重新上传文档
```

> 演进项（非 MVP）：提供"按 chunk 原文重嵌入"管理接口，免文档重传（chunk 原文在 MySQL，无需重新解析切分）。

## 9. 完整实现代码（⏳ 留空：实施阶段按 spec-workflow 写入本文档，用户手动输入项目）

## 10. 验证方式

1. `mvn clean compile` → 全模块编译通过（含新 ai-agent 模块）
2. MySQL 执行 5 张表 DDL（同步维护 `docs/sql/ai_java.sql`）
3. 环境准备：Redis 8（AOF + noeviction）、环境变量 `AI_API_KEY`（智谱）、`MCP_TOKEN`
4. `mvn install -pl ai-basic -am -DskipTests` → `mvn spring-boot:run -pl ai-web` 启动
5. **摄取链路**：`POST /api/kb/create` 建库 → `POST /api/kb/{kbId}/document/upload` 传 PDF → 轮询 `GET document/list` 至 COMPLETED → Redis 中 `FT.INFO ai-java-kb` 向量数 = chunk 数
6. **问答链路**：`POST /api/chat/session/create` → `POST /send`（curl -N 观察 SSE）→ message 流式输出、citations 含 docName/chunkIndex/score → `GET messages` 历史含 user+assistant+citations
7. **删除链路**：删文档 → Redis 向量数减少、chunk/doc 记录消失；删 KB → 会话/消息/chunk 级联删除、文件目录清空；`audit_log` 表有对应 DATA_DELETE 审计记录
8. **MCP 链路**：Claude Desktop 配置 Streamable HTTP 端点（`http://localhost:8120/api/mcp`）+ token（header `X-MCP-Token`）→ `list_knowledge_bases`、`search_knowledge` 可用；错误 token 返回 401
9. **前端**：上传进度轮询自动停止；打字机输出中文无乱码；快速切换会话旧流中止；流失败显示"回答失败"且重发可用；删除二次确认弹窗
10. 脱敏检查：`audit_log.request_params` 中上传/删除操作参数无敏感信息
