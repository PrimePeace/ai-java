# ai-agent 模块实现文档

> 本文档逐接口分析 ai-agent 模块已实现的代码，讲清楚"用了什么技术、做了什么"。

---

## 1. ChatController — 对话接口

### 1.1 接口清单

| 方法   | 路径                                 | 功能                       | 鉴权            |
| ------ | ------------------------------------ | -------------------------- | --------------- |
| POST   | `/chat/session/create`               | 创建会话                   | `@RequireLogin` |
| GET    | `/chat/session/list`                 | 会话列表（可按 kbId 过滤） | `@RequireLogin` |
| GET    | `/chat/session/{sessionId}/messages` | 历史消息                   | `@RequireLogin` |
| DELETE | `/chat/session/{sessionId}`          | 删除会话                   | `@RequireLogin` |
| POST   | `/chat/session/{sessionId}/send`     | 提问（SSE 流式响应）       | `@RequireLogin` |

### 1.2 采用技术

| 技术                                               | 用途                                                                                                  |
| -------------------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| **Spring WebFlux `Flux<ServerSentEvent<String>>`** | SSE 流式响应，`produces = MediaType.TEXT_EVENT_STREAM_VALUE`                                          |
| **Spring AI `ChatModel`**                          | 云端 LLM 调用（OpenAI 兼容），`chatModel.stream(prompt)` 返回 `Flux<ChatResponse>`                    |
| **Spring AI `VectorStore`**                        | Redis 向量检索，`similaritySearch(SearchRequest)` 做相似度检索                                        |
| **MyBatis-Flex `QueryWrapper`**                    | 全量 ORM 查询（CRUD + 条件查询 + 排序 + 分页）                                                        |
| **Hutool `JSONUtil`**                              | citations JSON 序列化/反序列化（不依赖 ObjectMapper Bean）                                            |
| **Reactor**                                        | 流式编排：`doOnNext` 聚合、`mapNotNull` 过滤空 chunk、`concatWith` 拼接事件、`onErrorResume` 兜底异常 |
| **Lombok `@RequiredArgsConstructor`**              | 构造器注入                                                                                            |
| **Swagger/Knife4j `@Operation` / `@Tag`**          | 接口文档标注                                                                                          |

### 1.3 核心流程：SSE 流式问答（`POST /chat/session/{sessionId}/send`）

这是整个模块的核心入口，流程如下：

```
用户提问
  ↓
归属校验（取会话 → 确认属于当前用户 → 拿 kbId）
  ↓
过采样向量检索（topK × 2）
  kbId 过滤 + status=COMPLETED 文档过滤
  ↓
截取前 topK 条
  ↓
构建 citations（chunkId / docId / docName / content 节选 150 字 / score）
  ↓
加载历史对话（最近 N 轮，丢弃末尾孤立 user 消息）
  ↓
拼装 Prompt：SystemMessage + history + UserMessage（含参考资料前缀）
  ↓
user 消息落库 + 会话标题自动更新（首次提问截取前 20 字）
  ↓
chatModel.stream(prompt) → 装配 SSE 事件流
  ↓
SSE 事件序列：message（多次）→ citations → end
失败时：error 事件
```

**SSE 事件协议**：

| 事件名      | 内容                    | 说明                   |
| ----------- | ----------------------- | ---------------------- |
| `message`   | LLM 生成的文本片段      | 多次推送，前端逐段拼接 |
| `citations` | `List<CitationVO>` JSON | 流式结束后推送引用溯源 |
| `end`       | 无 data                 | 标记流式结束           |
| `error`     | 错误信息（去除换行）    | 异常兜底               |

### 1.4 关键设计决策

1. **过采样 + 过滤策略**：检索时 `topK * 2` 过采样，再用 `filterCompleted` 过滤掉 status 非 COMPLETED 的文档切片，最后截取前 `topK`。避免向量库返回已删除/未处理完的脏数据。
2. **空知识库保护**：先查 `completedCount`，无已完成文档时跳过向量检索，避免空库触发检索异常。
3. **历史消息清洗**：`getHistory` 会 `reverse` 后 `removeLast` 丢弃末尾连续的 user 消息（流式失败遗留的无应答提问），防止 LLM 看到连续 user 无 assistant 的异常上下文。
4. **异常隔离**：`saveAssistantMessage` 失败用 `try-catch` 吞掉（只 log），不影响 SSE 流已推送给用户的回答。
5. **Citation 内容快照**：citations 存的是 content 原文节选（前 150 字 + "..."），不反查已删除文档。

### 1.5 关联文件清单

**Controller**：

- `ai-agent/src/main/java/com/ai/aijava/agent/controller/ChatController.java`

**Service**：

- `ai-agent/src/main/java/com/ai/aijava/agent/service/RagChatService.java` — RAG 流式问答核心
- `ai-agent/src/main/java/com/ai/aijava/agent/service/ChatSessionService.java` — 会话 CRUD + 消息持久化 + 历史加载

**DTO/VO**：

- `ai-agent/src/main/java/com/ai/aijava/agent/dto/request/ChatSessionCreateRequest.java` — 创建会话入参
- `ai-agent/src/main/java/com/ai/aijava/agent/dto/request/ChatSendRequest.java` — 提问入参
- `ai-agent/src/main/java/com/ai/aijava/agent/dto/vo/ChatSessionVO.java` — 会话视图
- `ai-agent/src/main/java/com/ai/aijava/agent/dto/vo/ChatMessageVO.java` — 消息视图
- `ai-agent/src/main/java/com/ai/aijava/agent/dto/vo/CitationVO.java` — 引用溯源条目

**Entity**：

- `ai-agent/src/main/java/com/ai/aijava/agent/entity/ChatSession.java` — 会话表实体
- `ai-agent/src/main/java/com/ai/aijava/agent/entity/ChatMessage.java` — 消息表实体

**Config**：

- `ai-agent/src/main/java/com/ai/aijava/agent/config/AgentProperties.java` — 可配参数（topK、historyRounds、systemPrompt 等）

**Mapper**（MyBatis-Flex 接口，无 XML 手写 SQL）：

- `ChatSessionMapper`
- `ChatMessageMapper`
- `KnowledgeDocumentMapper`
- `KnowledgeBaseMapper`

---

## 2. KnowledgeBaseController — 知识库管理接口

### 2.1 接口清单

| 方法   | 路径         | 功能                   | 鉴权            | 审计                     |
| ------ | ------------ | ---------------------- | --------------- | ------------------------ |
| POST   | `/kb/create` | 创建知识库             | `@RequireLogin` | `@AuditLog(DATA_CREATE)` |
| GET    | `/kb/list`   | 我的知识库列表         | `@RequireLogin` | —                        |
| POST   | `/kb/update` | 修改知识库             | `@RequireLogin` | `@AuditLog(DATA_UPDATE)` |
| DELETE | `/kb/{id}`   | 删除知识库（级联清理） | `@RequireLogin` | `@AuditLog(DATA_DELETE)` |

### 2.2 采用技术

| 技术                             | 用途                                                                |
| -------------------------------- | ------------------------------------------------------------------- |
| **`@AuditLog` AOP**              | 知识库增/删/改操作自动记录审计日志（ai-basic 模块提供）             |
| **Spring `@Transactional`**      | 删除操作事务管理，保证级联删除原子性                                |
| **`@Resource` + `@Lazy` 自注入** | 解决同类内 `@Transactional` 方法调用的代理失效问题（Spring 经典坑） |
| **`VectorStore.delete()`**       | Redis 向量库批量删除（≤500/批）                                     |
| **MyBatis-Flex `QueryWrapper`**  | ORM 查询，GROUP BY 统计 docCount                                    |
| **`UserContext.getUserId()`**    | 统一用户上下文获取                                                  |

### 2.3 核心流程：删除知识库（级联清理全链）

这是本 Controller 最复杂的操作，涉及 5 个存储介质的清理：

```
归属校验（确认 kb 属于当前用户）
  ↓
① 前置缓存：查出所有 filePath + chunkId 列表
  ↓
② 删 Redis 向量：按 chunkId 分批（≤500/批）调用 vectorStore.delete()
  失败 → 中止整个流程（不删 MySQL，保证一致性）
  ↓
③ 删 MySQL 记录（@Transactional 单事务，顺序严格）：
  chat_message（关联 sessionIds）
  → chat_session（按 kbId）
  → document_chunk（按 kbId）
  → knowledge_document（按 kbId）
  → knowledge_base（按 id）
  失败 → 事务回滚 + 已删向量变脏数据（需人工清理）
  ↓
④ 删磁盘文件：遍历 filePath 逐个 delete
  失败 → 仅 log 不抛异常（文件可后续人工清理，不影响数据一致性）
```

**关键约束**：

- 步骤②和③必须按顺序：先删外部依赖（Redis 向量），再删内部数据（MySQL）
  - 如果先删 MySQL → 向量库残留脏向量（无法再通过 kbId 关联清理）
  - 如果向量删除失败 → 不删 MySQL（保证 MySQL 记录完整，可重试）
- `deleteRecordsTransaction` 必须通过 `self` 代理调用，否则 `@Transactional` 不生效
- 步骤④是事务外的操作，失败不影响已完成的数据库删除

### 2.4 关键设计决策

1. **docCount 不冗余**：列表接口的文档数通过 `GROUP BY` 实时统计，不在 knowledge_base 表设计冗余字段。
2. **自注入代理**：`@Resource` + `@Lazy` 注入 `KnowledgeBaseService self`，同类内调用 `@Transactional` 方法时通过代理触发事务（Spring AOP 自调用不经过代理的经典问题修正）。
3. **静默删除文件**：`deleteFileQuietly` 对磁盘文件删除做 try-catch，失败只记日志不抛异常。因为文件删除失败不影响数据库一致性，抛异常会导致整个流程失败。
4. **批量向量删除**：`vectorStore.delete()` 按 500 一批分批调用，避免单次删除过多 key 导致 Redis 阻塞。

### 2.5 关联文件清单

**Controller**：

- `ai-agent/src/main/java/com/ai/aijava/agent/controller/KnowledgeBaseController.java`

**Service**：

- `ai-agent/src/main/java/com/ai/aijava/agent/service/KnowledgeBaseService.java` — 知识库 CRUD + 级联删除 + 文档列表

**DTO/VO**：

- `ai-agent/src/main/java/com/ai/aijava/agent/dto/request/KnowledgeBaseCreateRequest.java` — 创建知识库入参
- `ai-agent/src/main/java/com/ai/aijava/agent/dto/request/KnowledgeBaseUpdateRequest.java` — 修改知识库入参
- `ai-agent/src/main/java/com/ai/aijava/agent/dto/vo/KnowledgeBaseVO.java` — 知识库视图（含 docCount）

**Entity**：

- `ai-agent/src/main/java/com/ai/aijava/agent/entity/KnowledgeBase.java` — 知识库表实体

**Mapper**：

- `KnowledgeBaseMapper`
- `KnowledgeDocumentMapper`
- `DocumentChunkMapper`
- `ChatSessionMapper`
- `ChatMessageMapper`

---

## 3. KnowledgeDocController — 知识库文档管理接口

### 3.1 接口清单

| 方法   | 路径                         | 功能                                 | 鉴权            | 审计                     |
| ------ | ---------------------------- | ------------------------------------ | --------------- | ------------------------ |
| POST   | `/kb/{kbId}/document/upload` | 上传文档（异步摄取）                 | `@RequireLogin` | `@AuditLog(DATA_CREATE)` |
| GET    | `/kb/{kbId}/document/list`   | 文档列表（含摄取状态，兼作进度轮询） | `@RequireLogin` | —                        |
| DELETE | `/kb/document/{docId}`       | 删除文档（级联清理）                 | `@RequireLogin` | `@AuditLog(DATA_DELETE)` |

### 3.2 采用技术

| 技术                               | 用途                                                    |
| ---------------------------------- | ------------------------------------------------------- |
| **Apache Tika `AutoDetectParser`** | 多格式文档解析（PDF/DOCX/MD/TXT 自动识别），提取纯文本  |
| **Spring AI `TokenTextSplitter`**  | 按 Token 数切分文档（chunkSize=800，重叠 + 保留分隔符） |
| **`@Async("ingestExecutor")`**     | 异步线程池执行文档摄取，不阻塞上传响应                  |
| **Spring `VectorStore.add()`**     | 批量向量化入库（内部自动调 EmbeddingModel）             |
| **`MultipartFile` + `Files.copy`** | 文件上传到磁盘（绝对路径，UUID 命名）                   |
| **状态机枚举 `DocStatus`**         | UPLOADED → PROCESSING → COMPLETED / FAILED              |

### 3.3 核心流程：上传 + 异步摄取

**上传编排（`DocumentIngestService.upload`，同步返回 docId）**：

```
校验知识库归属
  ↓
校验文件类型（白名单 pdf/docx/md/txt）与大小（≤20MB）
  ↓
存盘：{uploadDir}/{kbId}/{uuid}.{ext}
  使用绝对路径（避免 Tomcat 临时目录问题）
  ↓
落库 knowledge_document（status=UPLOADED）
  先入库再触发异步，保证 Worker 必能查到记录
  ↓
立即返回 docId 给前端
  ↓
触发异步 ingestWorker.ingest(docId)
```

**异步摄取（`DocumentIngestWorker.ingest`，`@Async` 线程池执行）**：

```
状态机：UPLOADED → PROCESSING
  ↓
1. Tika 解析磁盘文件 → 提取纯文本
  （解析结果为空 → 抛异常转 FAILED，如扫描版 PDF）
  ↓
2. TokenTextSplitter 切分
  （chunkSize=800 token，重叠 200 字符，最大 100 切片）
  ↓
3. chunk 批量入库（document_chunk 表）
  每条插入后获取自增 id
  同时构建 Vector Document（id=chunk 自增 id，metadata: kbId + docId）
  ↓
4. 向量入库（Redis VectorStore）
  内部自动调 EmbeddingModel 批量向量化
  ↓
状态机 → COMPLETED
失败 → FAILED（记录 errorMessage 截断 512 字符）
```

### 3.4 状态机：DocStatus

| 状态         | 含义               | 下一步                |
| ------------ | ------------------ | --------------------- |
| `UPLOADED`   | 已上传，待处理     | → PROCESSING          |
| `PROCESSING` | 解析/切分/向量化中 | → COMPLETED 或 FAILED |
| `COMPLETED`  | 摄取完成，可被检索 | 终态                  |
| `FAILED`     | 摄取失败           | 终态（只能删除重传）  |

**设计约束**：FAILED 状态没有重试/重处理接口，用户只能删除后重新上传。这是 MVP 阶段的简化设计。

### 3.5 关键设计决策

1. **异步与同步分离**：上传接口只返回 docId，不等待摄取完成。前端通过轮询 `/list` 接口的 status 字段获取进度。避免同步阻塞 HTTP 线程。
2. **先入库再异步**：`knowledgeDocumentMapper.insert(doc)` 在 `ingestWorker.ingest()` 之前执行，确保异步线程启动时 DB 已有 UPLOADED 记录（避免 race condition）。
3. **chunk 自增 id = 向量 Document id**：`document_chunk` 表插入后获取的自增 id 直接作为 Redis 向量 Document id，删除时无需额外映射表，通过 chunkId 即可定位向量。
4. **Tika 自动检测**：`AutoDetectParser` 根据文件内容自动识别格式（不依赖后缀），避免伪造扩展名导致解析异常。
5. **解析结果校验**：Tika 提取文本为空时主动抛异常（扫描版 PDF、空文档），不静默通过。
6. **errorMessage 截断**：失败原因最多存 512 字符，防止超长异常信息撑爆 DB 字段。

### 3.6 关联文件清单

**Controller**：

- `ai-agent/src/main/java/com/ai/aijava/agent/controller/KnowledgeDocController.java`

**Service**：

- `ai-agent/src/main/java/com/ai/aijava/agent/service/DocumentIngestService.java` — 上传编排（校验 → 存盘 → 落库 → 触发异步）
- `ai-agent/src/main/java/com/ai/aijava/agent/service/DocumentIngestWorker.java` — 异步摄取 Worker（解析 → 切分 → chunk 入库 → 向量入库）
- `ai-agent/src/main/java/com/ai/aijava/agent/service/KnowledgeBaseService.java` — 删除文档（复用）

**Pipeline**：

- `ai-agent/src/main/java/com/ai/aijava/agent/pipeline/DocumentParser.java` — Tika 解析器
- `ai-agent/src/main/java/com/ai/aijava/agent/pipeline/ChunkSplitter.java` — TokenTextSplitter 切分器

**DTO/VO**：

- `ai-agent/src/main/java/com/ai/aijava/agent/dto/vo/KnowledgeDocumentVO.java` — 文档视图（含 status + errorMessage）

**Entity**：

- `ai-agent/src/main/java/com/ai/aijava/agent/entity/KnowledgeDocument.java` — 文档表实体
- `ai-agent/src/main/java/com/ai/aijava/agent/entity/DocumentChunk.java` — 切片表实体

**Enum**：

- `ai-agent/src/main/java/com/ai/aijava/agent/enums/DocStatus.java` — 状态枚举

**Mapper**：

- `KnowledgeDocumentMapper`
- `DocumentChunkMapper`
- `KnowledgeBaseMapper`

---

## 4. MCP Server 与安全拦截器

ai-agent 模块对外暴露 MCP（Model Context Protocol）Server，允许 Claude Desktop、Cursor 等 AI 客户端直接调用知识库检索工具。这不是普通的 HTTP 接口，而是 MCP 协议端点。

### 4.1 MCP 安全拦截器（`McpSecurityInterceptor`）

**采用技术**：

| 技术                     | 用途                                                         |
| ------------------------ | ------------------------------------------------------------ |
| **`HandlerInterceptor`** | Spring MVC 拦截器，拦截 `/mcp`、`/sse`、`/mcp/messages` 路径 |
| **`@Value` 配置注入**    | 读取 `mcp.security.token` 配置项                             |

**功能**：

- 请求头校验：`X-MCP-Token` 必须与配置值 `mcp.security.token` 一致
- **未配置 token 时**：放行 + 打 WARN 告警（开发便利，生产必须配置）
- **已配置 token 时**：强校验，不匹配则返回 401 + `{"code":40103,"message":"MCP token 无效或为空"}`
- 返回 false 阻止请求继续

**设计理念**：MCP 无用户态（不走 JWT），由全局 token 保护。开发环境可以不配 token（降级放行），生产环境必须配。

### 4.2 Web 配置（`AgentWebConfig`）

**采用技术**：

| 技术                      | 用途                                      |
| ------------------------- | ----------------------------------------- |
| **`WebMvcConfigurer`**    | Spring MVC 配置接口，内聚在 ai-agent 模块 |
| **`InterceptorRegistry`** | 注册 `McpSecurityInterceptor` 到指定路径  |

**拦截路径**：

| 路径            | 说明                             |
| --------------- | -------------------------------- |
| `/mcp`          | MCP Streamable HTTP 单端点       |
| `/mcp/**`       | MCP 子路径（如 `/mcp/messages`） |
| `/sse`          | MCP fallback SSE 端点            |
| `/mcp/messages` | MCP 消息推送端点                 |

**设计意图**：MVC 配置内聚在 ai-agent 模块，不依赖 ai-web 的配置。ai-agent 作为独立 Maven 模块，自行注册拦截器到全局路径。

### 4.3 MCP 工具（`KnowledgeMcpTools`）

**采用技术**：

| 技术                               | 用途                                  |
| ---------------------------------- | ------------------------------------- |
| **`@McpTool` / `@McpToolParam`**   | Spring AI 2.0 MCP 注解，声明 MCP 工具 |
| **`VectorStore.similaritySearch`** | 跨库向量检索                          |
| **MyBatis-Flex `QueryWrapper`**    | 查询知识库列表                        |

**暴露的 MCP 工具**：

| 工具名               | 参数                            | 功能                                   |
| -------------------- | ------------------------------- | -------------------------------------- |
| `listKnowledgeBases` | 无                              | 列出平台全部知识库（ID + 名称 + 描述） |
| `searchKnowledge`    | `query`（必填）, `kbId`（可选） | 跨库检索，返回相关原文片段与向量 ID    |

**与对话接口的区别**：

| 维度     | 对话接口（ChatController）       | MCP 工具                             |
| -------- | -------------------------------- | ------------------------------------ |
| 鉴权     | `@RequireLogin`（JWT 用户态）    | `X-MCP-Token`（全局 token）          |
| 用户体验 | SSE 流式 + 历史上下文 + 引用溯源 | 一次性返回纯文本结果                 |
| 目标用户 | Web 前端用户                     | AI 客户端（Claude Desktop / Cursor） |

### 4.4 关联文件清单

- `ai-agent/src/main/java/com/ai/aijava/agent/config/AgentWebConfig.java` — Web MVC 配置，注册 MCP 拦截器
- `ai-agent/src/main/java/com/ai/aijava/agent/config/McpSecurityInterceptor.java` — MCP token 校验
- `ai-agent/src/main/java/com/ai/aijava/agent/service/mcp/KnowledgeMcpTools.java` — MCP 工具（列举知识库 + 跨库检索）

---

## 5. Redis 向量库定制配置（`RedisVectorStoreConfig`）

### 5.1 采用技术

| 技术                                                     | 用途                                                     |
| -------------------------------------------------------- | -------------------------------------------------------- |
| **Spring AI `RedisVectorStore`**                         | 覆盖自动配置，手动构建 Bean                              |
| **`@ConditionalOnClass` / `@ConditionalOnProperty`**     | 条件装配（Redis + Jedis + EmbeddingModel 存在时生效）    |
| **Jedis `RedisClient`**                                  | 从 Spring 管理的 `JedisConnectionFactory` 提取原生客户端 |
| **Redis Search `FT.ALTER` / `FT.DROPINDEX` / `FT.INFO`** | 动态管理向量索引 schema                                  |
| **HNSW 向量索引**                                        | `hnswM`、`hnswEfConstruction`、`hnswEfRuntime` 参数控制  |
| **Micrometer `ObservationRegistry`**                     | 向量检索可观测性（指标/Tracing）                         |

### 5.2 核心问题与解决方案

**问题**：Spring AI 自动创建的 Redis 向量索引默认不登记 `kbId` / `docId` 为 TAG 字段。没有 TAG 类型，`filterExpression("kbId == 'xxx'")` 会抛异常：`Not allowed filter identifier name: kbId`。

**解决方案**：

1. 手动构建 `RedisVectorStore` Bean，覆盖自动配置
2. 通过 `metadataFields()` 登记 `kbId` 和 `docId` 为 `TAG` 类型（支持等值/IN 过滤）
3. `ensureFilterableMetadata()` 在启动时动态检查/修复索引 schema

### 5.3 索引修复逻辑（`ensureFilterableMetadata`）

```
检查 FT.INFO 中 attributes 是否包含 kbId/docId
  ↓
都不缺 → 直接返回
  ↓
有缺失 → 构建 TAG SchemaField 列表
  ↓
尝试 FT.ALTER 追加字段
  成功 → 日志记录
  ↓
  失败（ALTER 不支持） → FT.DROPINDEX 删除索引
  （不删向量文档，由 initialize-schema 启动时重建）
```

**防御性设计**：

- `FT.ALTER` 失败后 `DROPINDEX` 只删索引结构，不删已存的向量文档（`kb:vector:*` key 仍保留）
- 所有异常都被 catch 并 warn 记录，不阻断应用启动
- `initializeSchema=true` 会在索引被删后自动重建

### 5.4 Jedis 客户端适配

Spring Data Redis 提供的是 `JedisConnectionFactory`，但 `RedisVectorStore` 需要 `RedisClient`（Jedis 原生）。配置类从 `JedisConnectionFactory` 提取连接信息（host/port/ssl/password/timeout），手动构建 `RedisClient`。

### 5.5 关联文件清单

- `ai-agent/src/main/java/com/ai/aijava/agent/config/RedisVectorStoreConfig.java` — Redis 向量库定制配置

---

> 已分析完毕 ai-agent 模块所有关键文件。如需继续分析其他模块（如前端页面、ai-web 启动配置等），请告知。
