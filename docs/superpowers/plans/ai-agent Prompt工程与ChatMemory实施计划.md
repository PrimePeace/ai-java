# ai-agent Prompt 工程与 ChatMemory 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落地设计文档定义的 Prompt 工程（模板 CRUD + KB 绑定 + 变量渲染）与 ChatMemory（自定义 `DbChatMemory` 对齐 Spring AI 抽象）两个模块。

**Architecture:** 新增 `prompt_template` 表与 `/prompt` 接口，KB 弱引用绑定模板；`DbChatMemory implements ChatMemory` 直读直写 `chat_message` 表（单一数据源，覆盖 Spring AI 自动配置）；`RagChatService` 改为模板渲染 + chatMemory 读写；前端新增模板管理页与 KB 绑定下拉。

**Tech Stack:** Spring Boot 4.1 / Spring AI 2.0.0 / MyBatis-Flex 1.10.3 / Vue 3 + TS + naive-ui

**用户指定执行要点（贯穿全程）：**

1. **数据库变更优先**：Task 1 先改 `ai_java.sql`（CREATE 加列/加索引 + `prompt_template` 建表），存量库 ALTER **单独执行、禁止写入 `ai_java.sql`**
2. **后端编译验证优先**：任务边界按"可编译点"切分；Task 6（Service 改造批，4 文件必须同批）后全模块编译；Task 7 启动验证 Bean 注入与依赖关系
3. **前端联调**：Task 10 专项处理下拉框 `0` 与 `null` 的转换（`KbFormModal` 内 `?? 0` / 后端三态语义），Task 11 全链路验收

**文档审查修订（2026-09-13，与设计文档 0.4 同步，仅改文档）：** 渲染按 key 长度降序；ALTER 不进 `ai_java.sql`；Mapper 补 `@Mapper`；KB 弹窗抽到 `components/kb/KbFormModal.vue`；模板弹窗路径改为 `components/prompt/`。

---

## 代码来源声明（重要）

**本计划所有实现代码的唯一来源：`docs/superpowers/specs/ai-agent Prompt工程与ChatMemory设计文档.md` 第 8 章。**

每个"写入文件"步骤的定位方法（子代理执行时按此操作）：

1. Read 设计文档，搜索精确的文件路径标题行，例如：`**文件路径：`ai-agent/src/main/java/com/ai/aijava/agent/entity/PromptTemplate.java`**`
2. 复制该标题下方第一个代码围栏（` ```java `/` ```vue `/` ```typescript `）内**全部内容**（不含围栏行本身）
3. 原样写入目标项目文件——**禁止转写、禁止"优化"、禁止改动任何字符**（设计文档代码已按 spec-code-completeness 校验完整性，且关键 API 签名已从 jar 反编译验证）

修改类任务（整文件替换）同理：设计文档中给出的是**完整新版**，直接整文件覆盖项目原文件。

---

### Task 1: 数据库变更（SQL 优先，阻塞后续全部任务）

**Files:**

- Modify: `docs/sql/ai_java.sql`

- [ ] **Step 1: 修改 `knowledge_base` 建表语句加列 + 加索引**

在 `docs/sql/ai_java.sql` 中定位 `knowledge_base` 的 CREATE TABLE（约 48 行起）：

1. 在 `description` 行之后插入一行（取自设计文档 8.1 改动 1 第一块）：

```sql
    `prompt_template_id` BIGINT DEFAULT NULL COMMENT '绑定的提示词模板ID（NULL=默认模板）',
```

2. 在 `INDEX idx_user_id` 行之后追加（取自设计文档 8.1 改动 1 第二块）：

```sql
    INDEX `idx_prompt_template_id` (`prompt_template_id`)
```

注意：`idx_user_id` 原行末无逗号，追加索引后 **`idx_user_id` 行必须加逗号**。

- [ ] **Step 2: 文件末尾只追加 `prompt_template` 建表（不要写 ALTER）**

在 `ai_java.sql` 文件末尾（`chat_message` 建表语句之后）追加，内容取自设计文档 8.1 节「改动 2」代码块（搜索标题 `**改动 2**`）。**禁止把 ALTER 写入本文件**——新库 CREATE 已含 `prompt_template_id`，整文件重放再 ALTER 会 Duplicate column。

```sql
-- ==================== Prompt 工程 ====================

-- 提示词模板表
CREATE TABLE `prompt_template` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '模板ID',
    `user_id`         BIGINT       NOT NULL COMMENT '所属用户ID',
    `name`            VARCHAR(64)  NOT NULL COMMENT '模板名称',
    `description`     VARCHAR(256) DEFAULT '' COMMENT '模板描述',
    `system_template` TEXT         NOT NULL COMMENT '系统提示词模板（变量 {kbName} {kbDescription}）',
    `user_template`   TEXT         NOT NULL COMMENT '用户消息模板（变量 {question} {references} {referencesBlock}）',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提示词模板表';
```

- [ ] **Step 3: 在已运行的 MySQL 上执行存量库 ALTER（不要对空库重放整份 ai_java.sql 后再跑）**

连接配置见 `ai-web/src/main/resources/application-prod.yml` 的 `spring.datasource`。SQL 取自设计文档 8.1「改动 3」：

```sql
ALTER TABLE `knowledge_base`
    ADD COLUMN `prompt_template_id` BIGINT DEFAULT NULL COMMENT '绑定的提示词模板ID（NULL=默认模板）' AFTER `description`;
ALTER TABLE `knowledge_base`
    ADD INDEX `idx_prompt_template_id` (`prompt_template_id`);
```

- [ ] **Step 4: 验证表结构**

```bash
mysql -u<user> -p <database> -e "SHOW COLUMNS FROM knowledge_base LIKE 'prompt_template_id'; SHOW INDEX FROM knowledge_base WHERE Key_name='idx_prompt_template_id'; SHOW TABLES LIKE 'prompt_template';"
```

预期输出：`prompt_template_id` 列存在（Default 为 NULL）；`idx_prompt_template_id` 索引存在；`prompt_template` 表存在。

- [ ] **Step 5: Commit**

```bash
git add docs/sql/ai_java.sql
git commit -m "feat(agent): 新增 prompt_template 表并扩展 knowledge_base 模板绑定列"
```

---

### Task 2: PromptTemplate 实体与 DTO（纯新增，编译安全）

**Files:**

- Create: `ai-agent/src/main/java/com/ai/aijava/agent/entity/PromptTemplate.java`
- Create: `ai-agent/src/main/java/com/ai/aijava/agent/mapper/PromptTemplateMapper.java`
- Create: `ai-agent/src/main/java/com/ai/aijava/agent/dto/request/PromptTemplateCreateRequest.java`
- Create: `ai-agent/src/main/java/com/ai/aijava/agent/dto/request/PromptTemplateUpdateRequest.java`
- Create: `ai-agent/src/main/java/com/ai/aijava/agent/dto/vo/PromptTemplateVO.java`

- [ ] **Step 1: 从设计文档 8.2 节复制 5 个文件**

按"代码来源声明"的定位方法，从设计文档 8.2 节依次复制以下 5 个代码块，原样写入对应项目文件：

| 设计文档定位标题（搜索 `文件路径：`）                       | 目标文件   |
| ----------------------------------------------------------- | ---------- |
| `ai-agent/.../entity/PromptTemplate.java`                   | 同路径新建 |
| `ai-agent/.../mapper/PromptTemplateMapper.java`             | 同路径新建 |
| `ai-agent/.../dto/request/PromptTemplateCreateRequest.java` | 同路径新建 |
| `ai-agent/.../dto/request/PromptTemplateUpdateRequest.java` | 同路径新建 |
| `ai-agent/.../dto/vo/PromptTemplateVO.java`                 | 同路径新建 |

复制后自查：`PromptTemplateMapper` 必须有 `@Mapper`（与现有 5 个 Mapper 一致；仅靠 `@MapperScan` 也能扫到，但项目约定每个接口都标注）。

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl ai-agent -am -q
```

预期：BUILD SUCCESS（纯新增文件，不触碰现有代码）。

- [ ] **Step 3: Commit**

```bash
git add ai-agent/src/main/java/com/ai/aijava/agent/entity/PromptTemplate.java ai-agent/src/main/java/com/ai/aijava/agent/mapper/PromptTemplateMapper.java ai-agent/src/main/java/com/ai/aijava/agent/dto/request/PromptTemplateCreateRequest.java ai-agent/src/main/java/com/ai/aijava/agent/dto/request/PromptTemplateUpdateRequest.java ai-agent/src/main/java/com/ai/aijava/agent/dto/vo/PromptTemplateVO.java
git commit -m "feat(agent): 提示词模板实体、Mapper 与 DTO"
```

---

### Task 3: PromptTemplateService 与 Controller（纯新增）

**Files:**

- Create: `ai-agent/src/main/java/com/ai/aijava/agent/service/PromptTemplateService.java`
- Create: `ai-agent/src/main/java/com/ai/aijava/agent/controller/PromptTemplateController.java`

- [ ] **Step 1: 从设计文档 8.2 节复制 2 个文件**

| 设计文档定位标题                                        | 目标文件   |
| ------------------------------------------------------- | ---------- |
| `ai-agent/.../service/PromptTemplateService.java`       | 同路径新建 |
| `ai-agent/.../controller/PromptTemplateController.java` | 同路径新建 |

关键逻辑确认（复制后自查，不修改）：

- `PromptTemplateService` 注入 `PromptTemplateMapper` + `KnowledgeBaseMapper`（**不是** `KnowledgeBaseService`，避免循环依赖——`KnowledgeBaseService` 将在 Task 6 注入 `PromptTemplateService`，方向单向）
- `delete()` 内先 `UpdateEntity` 批量解绑 KB 再删模板，`@Transactional` 包裹
- `renderSystem`/`renderUser` 为纯字面替换（`String.replace`），**按 key 长度降序**（`referencesBlock` 先于 `references`）；`loadBoundTemplate` 对 NULL/已删/归属不符三种情况回退默认模板
- `description` 为 null 时写入 `""`（与表 DEFAULT 对齐，避免 INSERT NULL）

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl ai-agent -am -q
```

预期：BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git add ai-agent/src/main/java/com/ai/aijava/agent/service/PromptTemplateService.java ai-agent/src/main/java/com/ai/aijava/agent/controller/PromptTemplateController.java
git commit -m "feat(agent): 提示词模板服务与 /prompt 管理接口"
```

---

### Task 4: DbChatMemory（ChatMemory 实现，纯新增）

**Files:**

- Create: `ai-agent/src/main/java/com/ai/aijava/agent/memory/DbChatMemory.java`

- [ ] **Step 1: 新建 `memory` 包并复制文件**

从设计文档 8.2 节复制 `ai-agent/.../memory/DbChatMemory.java` 代码块，写入项目（`memory` 包为新包）。

关键点确认（复制后自查）：

- `@Component` 注册——依赖已验证的 `ChatMemoryAutoConfiguration#chatMemory` 的 `@ConditionalOnMissingBean`，自定义 Bean **覆盖**自动配置（不会出现两个 ChatMemory Bean 冲突）
- 只实现 `add(String, List<Message>)`（抽象方法）；单条 `add(String, Message)` 是接口 default 方法自动转调
- `get()` 双向 turn 对齐：头部剥离连续 assistant + 尾部剥离连续 user（对齐 Spring AI 2.0 MessageWindowChatMemory 的 turn-boundary 理念）
- `toEntity` 从 `message.getMetadata().get("citations")` 取 citations JSON 落列（key 常量 `CITATIONS_KEY`）

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl ai-agent -am -q
```

预期：BUILD SUCCESS。此时 `DbChatMemory` 仅注册为 Bean，尚无人注入（`ChatSessionService`/`RagChatService` 仍用旧逻辑），编译与运行均安全。

- [ ] **Step 3: Commit**

```bash
git add ai-agent/src/main/java/com/ai/aijava/agent/memory/DbChatMemory.java
git commit -m "feat(agent): DbChatMemory 对话记忆实现，直读直写 chat_message 并覆盖自动配置"
```

---

### Task 5: KnowledgeBase 数据层加字段（编译安全的前置修改）

**Files:**

- Modify: `ai-agent/src/main/java/com/ai/aijava/agent/entity/KnowledgeBase.java`（整文件替换）
- Modify: `ai-agent/src/main/java/com/ai/aijava/agent/dto/request/KnowledgeBaseUpdateRequest.java`（整文件替换）
- Modify: `ai-agent/src/main/java/com/ai/aijava/agent/dto/vo/KnowledgeBaseVO.java`（整文件替换）

- [ ] **Step 1: 从设计文档 8.3 节整文件替换 3 个文件**

| 设计文档定位标题                                           | 项目文件（覆盖） |
| ---------------------------------------------------------- | ---------------- |
| `ai-agent/.../entity/KnowledgeBase.java`                   | 同路径覆盖       |
| `ai-agent/.../dto/request/KnowledgeBaseUpdateRequest.java` | 同路径覆盖       |
| `ai-agent/.../dto/vo/KnowledgeBaseVO.java`                 | 同路径覆盖       |

变更内容：entity 加 `promptTemplateId` 字段；UpdateRequest 加可选 `promptTemplateId`（**无 @NotNull**——null/0/>0 三态语义）；VO 加 `promptTemplateId`。

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl ai-agent -am -q
```

预期：BUILD SUCCESS（加字段不破坏现有调用——注意：`KnowledgeBaseService.listMine`/`create` 此时**尚未**给 VO 的 `promptTemplateId` 赋值，编译不受影响，Task 6 补齐）。

- [ ] **Step 3: Commit**

```bash
git add ai-agent/src/main/java/com/ai/aijava/agent/entity/KnowledgeBase.java ai-agent/src/main/java/com/ai/aijava/agent/dto/request/KnowledgeBaseUpdateRequest.java ai-agent/src/main/java/com/ai/aijava/agent/dto/vo/KnowledgeBaseVO.java
git commit -m "feat(agent): knowledge_base 实体与 DTO 增加 promptTemplateId 绑定字段"
```

---

### Task 6: Service 层改造批（4 文件必须同批，批后全模块编译）

**为什么必须同批**（编译依赖链，任何一个单独先改都会编译失败）：

```
AgentProperties 删 systemPrompt/effectiveSystemPrompt → RagChatService 调用点失效
ChatSessionService 删 getHistory/saveUserMessage/saveAssistantMessage → RagChatService 调用点失效
KnowledgeBaseService.update 改造 → 注入 PromptTemplateService（Task 3 已就绪）
RagChatService 重写 → 依赖以上三者新签名
```

**Files:**

- Modify: `ai-agent/src/main/java/com/ai/aijava/agent/config/AgentProperties.java`（整文件替换）
- Modify: `ai-agent/src/main/java/com/ai/aijava/agent/service/KnowledgeBaseService.java`（整文件替换）
- Modify: `ai-agent/src/main/java/com/ai/aijava/agent/service/ChatSessionService.java`（整文件替换）
- Modify: `ai-agent/src/main/java/com/ai/aijava/agent/service/RagChatService.java`（整文件替换）

- [ ] **Step 1: 从设计文档 8.3 节整文件替换 4 个文件**

| 设计文档定位标题                                 | 项目文件（覆盖） |
| ------------------------------------------------ | ---------------- |
| `ai-agent/.../config/AgentProperties.java`       | 同路径覆盖       |
| `ai-agent/.../service/KnowledgeBaseService.java` | 同路径覆盖       |
| `ai-agent/.../service/ChatSessionService.java`   | 同路径覆盖       |
| `ai-agent/.../service/RagChatService.java`       | 同路径覆盖       |

- [ ] **Step 2: 核对 Bean 注入与依赖关系（用户要点 2）**

替换完成后，逐一核对（Read 项目文件确认，不是凭记忆）：

| 类                      | 新增注入                                                     | 移除依赖             | 方向检查                    |
| ----------------------- | ------------------------------------------------------------ | -------------------- | --------------------------- |
| `KnowledgeBaseService`  | `PromptTemplateService`（构造器第 7 参）                     | —                    | KBS → PTS ✅ 单向           |
| `ChatSessionService`    | `ChatMemory`（`@RequiredArgsConstructor` 自动）              | —                    | CSS → ChatMemory ✅         |
| `RagChatService`        | `KnowledgeBaseMapper`、`PromptTemplateService`、`ChatMemory` | —（保留原 5 个依赖） | RCS → PTS/ChatMemory/CSS ✅ |
| `PromptTemplateService` | （Task 3 已建，不注入任何 Service）                          | —                    | PTS → Mapper only ✅ 无环   |

循环依赖结论：`PromptTemplateService` 只依赖 Mapper，`KnowledgeBaseService → PromptTemplateService` 单向，全图无环。

- [ ] **Step 3: 全模块编译验证**

```bash
mvn clean compile -q
```

预期：BUILD SUCCESS。

常见编译错误排查（若失败）：

- `cannot find symbol: method effectiveSystemPrompt()` → AgentProperties 未替换或 RagChatService 用了旧版
- `cannot find symbol: method getHistory(...)` / `saveUserMessage(...)` → ChatSessionService 与 RagChatService 版本不匹配（必须同批）
- `constructor KnowledgeBaseService cannot be applied` → 构造器参数与注入不匹配，对照设计文档 8.3 的构造器（7 参数）
- `AssistantMessage(String,Map)` not visible → metadata 构造器是 protected，必须走 `AssistantMessage.builder().content(...).properties(...).build()`（设计文档代码已是 Builder 写法）

替换后自查 `KnowledgeBaseService.update`：有 `@Transactional`；`templateId < 0` 抛 PARAMS_ERROR。

- [ ] **Step 4: Commit**

```bash
git add ai-agent/src/main/java/com/ai/aijava/agent/config/AgentProperties.java ai-agent/src/main/java/com/ai/aijava/agent/service/KnowledgeBaseService.java ai-agent/src/main/java/com/ai/aijava/agent/service/ChatSessionService.java ai-agent/src/main/java/com/ai/aijava/agent/service/RagChatService.java
git commit -m "refactor(agent): RagChatService 接入模板渲染与 DbChatMemory，收编历史读写逻辑"
```

---

### Task 7: 后端启动验证（Bean 装配 + 接口暴露）

- [ ] **Step 1: 安装依赖模块到本地仓库**

```bash
mvn install -pl ai-basic,ai-agent -am -DskipTests -q
```

预期：BUILD SUCCESS。

- [ ] **Step 2: 启动应用**

```bash
mvn spring-boot:run -pl ai-web
```

启动日志检查点（用户要点 2 的运行时验证）：

| 检查点                                               | 预期                                       |
| ---------------------------------------------------- | ------------------------------------------ |
| 无 `NoUniqueBeanDefinitionException`                 | `DbChatMemory` 成功覆盖自动配置 ChatMemory |
| 无 `UnsatisfiedDependencyException` / 循环依赖报错   | Task 6 Step 2 的依赖图验证通过             |
| 无 `Not allowed filter identifier` 等 Redis 索引报错 | 向量库配置不受影响                         |
| Tomcat started on port 8120                          | 启动成功                                   |

- [ ] **Step 3: 验证接口暴露**

浏览器打开 `http://localhost:8120/api/doc.html`，确认"提示词模板"分组下有 4 个接口（create/list/update/delete）。

可另用 curl 快速验证（未登录应返回 40100 而非 404，证明路由存在）：

```bash
curl -s http://localhost:8120/api/prompt/list
```

预期：`{"code":40100,...}` 之类的未登录响应（不是 404）。

- [ ] **Step 4: 停止应用**

Ctrl+C 停止（后续前端联调时再启动）。

---

### Task 8: 前端类型与 API 层

**Files:**

- Modify: `ai-java-front/src/types/ai.ts`（整文件替换）
- Create: `ai-java-front/src/api/prompt.ts`

- [ ] **Step 1: 从设计文档 8.5 节整文件替换 `types/ai.ts`**

搜索设计文档定位标题 `ai-java-front/src/types/ai.ts`，整文件覆盖。变更：`KnowledgeBase` 加 `promptTemplateId: number | null`；`UpdateKbRequest` 加 `promptTemplateId?: number | null`；新增 `PromptTemplate`/`CreatePromptRequest`/`UpdatePromptRequest`。

- [ ] **Step 2: 从设计文档 8.4 节新建 `api/prompt.ts`**

搜索定位标题 `ai-java-front/src/api/prompt.ts`，复制写入。

- [ ] **Step 3: 类型检查**

```bash
cd ai-java-front && npm run type-check
```

预期：无错误。（此时 `prompt.ts` 尚无页面调用，仅类型层就绪。）

- [ ] **Step 4: Commit**

```bash
git add ai-java-front/src/types/ai.ts ai-java-front/src/api/prompt.ts
git commit -m "feat(front): 提示词模板类型定义与 API 封装"
```

---

### Task 9: 模板管理页面（组件 + 页面 + 路由 + 菜单）

**Files:**

- Create: `ai-java-front/src/components/prompt/PromptEditModal.vue`
- Create: `ai-java-front/src/views/ai/PromptTemplateView.vue`
- Modify: `ai-java-front/src/router/ai.ts`（整文件替换）
- Modify: `ai-java-front/src/layouts/AppSider.vue`（整文件替换）

- [ ] **Step 1: 从设计文档 8.4 节新建 2 个组件文件**

| 设计文档定位标题                                         | 目标文件                                           |
| -------------------------------------------------------- | -------------------------------------------------- |
| `ai-java-front/src/components/prompt/PromptEditModal.vue` | 同路径新建（`components/prompt/` 目录若不存在则创建） |
| `ai-java-front/src/views/ai/PromptTemplateView.vue`      | 同路径新建                                         |

- [ ] **Step 2: 从设计文档 8.5 节整文件替换 `router/ai.ts` 与 `AppSider.vue`**

`router/ai.ts` 变更：新增 `/prompt` 路由（name: `prompt-template`，menu: `prompt`）。
`AppSider.vue` 变更 3 处：import `DocumentTextOutline`；`menuOptions` 加"提示词模板"项；`menuRoutes` 加 `prompt: "/prompt"`。

- [ ] **Step 3: 类型检查**

```bash
cd ai-java-front && npm run type-check
```

预期：无错误。

- [ ] **Step 4: Commit**

```bash
git add ai-java-front/src/components/prompt/PromptEditModal.vue ai-java-front/src/views/ai/PromptTemplateView.vue ai-java-front/src/router/ai.ts ai-java-front/src/layouts/AppSider.vue
git commit -m "feat(front): 提示词模板管理页、编辑弹窗组件与路由菜单"
```

---

### Task 10: KB 编辑弹窗绑定下拉（0 与 null 转换专项）

**Files:**

- Create: `ai-java-front/src/components/kb/KbFormModal.vue`（从设计文档 8.4 节复制；抽出弹窗以避免 `KnowledgeBaseView` 超 Vue 200 行）
- Modify: `ai-java-front/src/views/ai/KnowledgeBaseView.vue`（整文件替换，只保留列表/删除）

- [ ] **Step 1: 从设计文档复制 2 个文件**

| 设计文档定位标题                                    | 操作     |
| --------------------------------------------------- | -------- |
| `ai-java-front/src/components/kb/KbFormModal.vue`   | 8.4 节新建 |
| `ai-java-front/src/views/ai/KnowledgeBaseView.vue`  | 8.5 节覆盖 |

- [ ] **Step 2: 核对 0/null 转换链路（用户要点 3，复制后 Read `KbFormModal.vue` 逐行确认）**

完整转换链在 **`KbFormModal.vue`**（不再在列表页），三处缺一不可：

**① 下拉选项构造（`templateOptions`）**——"默认模板"固定 value=0：

```typescript
const templateOptions = computed(() => [
  { label: "默认模板", value: 0 },
  ...templates.value.map((t) => ({ label: t.name, value: t.id })),
]);
```

**② 编辑回显（`watch show`）**——后端 `null`（默认模板）转前端 `0`：

```typescript
promptTemplateId: props.kb.promptTemplateId ?? 0,   // null → 0（NSelect 不能用 null 作选中值）
```

**③ 保存提交（`handleSave`）**——前端 `0` 原样传后端（由后端三态语义消化，前端不做 0→null 转换）：

```typescript
const data: UpdateKbRequest = {
  id: props.kb.id,
  name: form.value.name,
  description: form.value.description,
  promptTemplateId: form.value.promptTemplateId, // 0=解绑；>0=绑定
};
```

后端三态语义（`KnowledgeBaseService.update`，Task 6 已落地）对照：

| 前端提交值              | 后端收到  | 行为                                                  |
| ----------------------- | --------- | ----------------------------------------------------- |
| `0`（下拉选"默认模板"） | `Long 0`  | `UpdateEntity` 置 `prompt_template_id = NULL`（解绑） |
| 模板 id                 | `Long id` | 校验归属后绑定                                        |
| 字段不传（旧客户端）    | `null`    | 不修改绑定                                            |

**边界自查**：新建弹窗不显示模板下拉、不传 `promptTemplateId`（创建请求无此字段），KB 建立后 `prompt_template_id = NULL` = 默认模板 ✅。`handleSave` 的 catch 必须 `return false`（失败不关弹窗）。两个 Vue 文件均 ≤200 行。

- [ ] **Step 3: 类型检查**

```bash
cd ai-java-front && npm run type-check
```

预期：无错误。

- [ ] **Step 4: Commit**

```bash
git add ai-java-front/src/components/kb/KbFormModal.vue ai-java-front/src/views/ai/KnowledgeBaseView.vue
git commit -m "feat(front): 知识库编辑弹窗支持绑定提示词模板（默认模板/解绑三态）"
```

---

### Task 11: 全链路联调验收（对照设计文档 9.2）

- [ ] **Step 1: 启动后端与前端**

```bash
# 终端 1
mvn spring-boot:run -pl ai-web
# 终端 2
cd ai-java-front && npm run dev
```

- [ ] **Step 2: 按 13 项清单逐项验收**

执行设计文档 **9.2 节功能验收表**全部 13 项，重点覆盖：

| 优先级 | 场景                                  | 关联要点                |
| ------ | ------------------------------------- | ----------------------- |
| 高     | #2 多轮对话上下文生效                 | ChatMemory 核心价值     |
| 高     | #3 流式完成 citations 落库            | metadata 传递链路       |
| 高     | #4 未知变量 `{qustion}` 创建报 40000  | 变量校验                |
| 高     | #5 绑定模板后回答风格变化             | Prompt 工程核心价值     |
| 高     | #7 删除模板 → KB 回退默认             | 解绑链路                |
| 高     | #8 下拉选"默认模板"（0 值）→ 解绑生效 | 用户要点 3 的端到端验证 |
| 高     | #13 同时使用 `{references}` 与 `{referencesBlock}` | 降序替换，防前缀截断 |
| 中     | #6 空知识库无"参考资料："残留         | `{referencesBlock}`     |
| 中     | #10 越权绑定他人模板报 40400          | 归属校验                |

- [ ] **Step 3: 验收问题处理约定**

- 编译/运行错误 → 直接修改项目文件修复（spec-workflow 约定的例外）
- 行为与设计不符 → 回设计文档核对，先改文档再改代码（保持文档与代码同步）

- [ ] **Step 4: 收尾 Commit（如有联调修复）**

```bash
git add -A
git commit -m "fix(agent): 联调修复（如有）"
```

---

## 自审记录

- **Spec 覆盖**：设计文档 8.1（SQL，含存量 ALTER 独立执行）→ Task 1；8.2 后端新增 8 文件 → Task 2/3/4；8.3 后端修改 7 文件 → Task 5（3 个）/Task 6（4 个）；8.4 前端新增 4 文件 → Task 8（1 个 prompt.ts）/Task 9（2 个模板页）/Task 10（1 个 KbFormModal）；8.5 前端修改 4 文件 → Task 8（1 个 types）/Task 9（2 个路由菜单）/Task 10（1 个 KnowledgeBaseView）；9.1 编译验证 → Task 2-6/8-10 各步；9.2 验收 13 项 → Task 11。设计文档第 6.1 节 8 个前端文件 + 第 4.1 节 15 个后端文件 + SQL = 24 项全部有任务覆盖 ✅
- **占位符扫描**：无 TBD/TODO/"适当处理"类表述；所有代码步骤有精确来源定位或内联代码 ✅
- **类型一致性**：`promptTemplateId` 全链路 Long/number | null 语义一致；`CITATIONS_KEY` 在 DbChatMemory（定义）与 RagChatService（引用 `DbChatMemory.CITATIONS_KEY`）一致；`UpdateEntity` 用法在 Task 3（解绑）与 Task 6（绑定置空）一致；`render()` 按 key 长度降序 ✅
- **执行要点映射**：SQL 优先 → Task 1 阻塞声明且 ALTER 不进 `ai_java.sql`；编译验证 → 任务边界即编译点 + Task 6 依赖链说明 + Task 7 启动验证；0/null 转换 → Task 10 Step 2 专项三处代码核对（位于 KbFormModal） ✅
- **行数**：KbFormModal / KnowledgeBaseView / PromptEditModal / PromptTemplateView 均按 ≤200 行拆分；RagChatService / KnowledgeBaseService 仍 ≤300 行 ✅
