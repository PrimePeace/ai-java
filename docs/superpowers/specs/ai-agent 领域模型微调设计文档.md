# ai-agent 领域模型微调设计文档

> 在已落地的 ai-agent 模块（知识库 / Prompt 工程 / RAG 问答）之上，新增领域模型微调能力：
> **① 数据集生成**（KB chunk + prompt 模板 → ChatML 格式训练集）、**② 微调任务管理**（对接智谱微调 API）、**③ 评测与路由**（RAG 链路与微调模型并行对比、可切换）。

---

## 0. 已确认决策（用户已拍板，勿重复询问）

| #   | 决策                      | 说明                                                                                                                                              |
| --- | ------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | 微调平台 = 智谱 AI        | 项目已使用智谱 embedding-3 + glm-5.3-flash，统一平台减少认证和依赖                                                                                |
| 2   | 数据集生成 = 混合方案     | KB chunk 当 context + LLM 根据 prompt 模板生成高质量 Q&A 对                                                                                       |
| 3   | 模型使用 = 双模型并行评测 | RAG 链路与微调模型同时可用，KB/会话可切换选择                                                                                                     |
| 4   | 评测 = 人工 + 自动混合    | 同一批问题分别走两条链路，人工评分为主，自动评测（LLM 裁判）做辅助                                                                                |
| 5   | 训练数据格式 = ChatML     | `{"messages": [{"role":"system","content":"..."}, {"role":"user","content":"..."}, {"role":"assistant","content":"..."}]}`，智谱微调 API 原生支持 |
| 6   | MVP 边界                  | 不做本地微调、不做模型压缩、不做 DPO/RLHF，仅 SFT（监督微调）                                                                                     |

### 0.1 关键技术与现有模块的关系

| 现有模块          | 本模块如何使用                                                                   |
| ----------------- | -------------------------------------------------------------------------------- |
| `knowledge_base`  | 微调的 KB 维度入口；KB 绑定问答引擎（RAG / 微调模型 / 自动）                     |
| `document_chunk`  | 数据集生成的数据源（COMPLETED 状态的 chunk 原文）                                |
| `prompt_template` | 生成 Q&A 对时的指令模板（system_template 定义角色 + user_template 定义问答格式） |
| `RagChatService`  | 评测时 RAG 链路复用此 Service                                                    |
| `chat_message`    | 评测时可从历史对话中抽取测试问题                                                 |
| `AgentProperties` | 扩展微调相关配置（基座模型名、生成批次数等）                                     |

### 0.2 架构分层

```
┌──────────────────────────────────────────────────────────────────┐
│  ① 数据集生成层                                                 │
│  触发：KB 详情页"生成训练集"                                      │
│  输入：KB 下所有 COMPLETED chunk + 绑定的 prompt 模板             │
│  处理：按 chunk 批量调 LLM → 生成 Q&A 对 → 导出 ChatML JSONL     │
│  输出：fine_tune_dataset 表 + JSONL 文件                          │
├──────────────────────────────────────────────────────────────────┤
│  ② 微调任务层                                                   │
│  触发：数据集详情页"创建微调任务"                                  │
│  输入：数据集 JSONL → 上传智谱微调 API                            │
│  处理：异步轮询训练进度 → 更新 fine_tune_job 状态                 │
│  输出：fine_tune_job 表 + 智谱 model_id                           │
├──────────────────────────────────────────────────────────────────┤
│  ③ 评测与路由层                                                 │
│  入口：KB 详情页"模型评测"tab                                     │
│  输入：测试问题集（手动录入 or 历史对话抽取）                      │
│  处理：同一问题分别走 RAG 链路 + 微调模型 → 对比表 + 评分         │
│  路由：KB 编辑弹窗可选问答引擎（RAG / 微调模型 / 自动）           │
└──────────────────────────────────────────────────────────────────┘
```

### 0.3 实施状态（2026-09-16 已落地）

代码已直接写入项目并通过验证：`mvn clean compile` ✅、前端 `npm run type-check` + `npm run build` ✅。
存量库迁移执行 `docs/sql/migration_fine_tune.sql`；新库直接执行 `docs/sql/ai_java.sql`。

### 0.4 实施时对设计文档的修正记录

| #   | 设计文档问题 | 修正方案 |
| --- | ------------ | -------- |
| 1   | 智谱微调流程错误：直接把 JSONL multipart 上传到 `/fine-tuning/jobs` | 修正为官方两步流程：先 `POST /api/paas/v4/files`（purpose=fine-tune）拿 file_id，再 `POST /api/paas/v4/fine_tuning/jobs`（下划线）创建任务，封装在 `ZhipuFineTuneClient` |
| 2   | 超参数名错误：`learning_rate/epochs/batch_size` | 对齐智谱（OpenAI 风格）：`learning_rate_multiplier/n_epochs/batch_size`，DTO 字段同步改为 `learningRateMultiplier` |
| 3   | 任务状态匹配错误：按大写 `RUNNING/SUCCEEDED` 匹配 | 智谱返回小写状态（validating_files/queued/running/succeeded/failed/cancelled），已修正映射；智谱无进度字段，progress 按状态映射估算（5/10/50/100） |
| 4   | 枚举重写 `name()`（final 方法，编译失败） | 删除重写，枚举仅含常量与注释 |
| 5   | `@Async` 自调用失效（generate→doGenerateAsync 同类调用不走代理） | 采用项目既有 self-injection（`@Resource @Lazy self`）模式，与 `KnowledgeBaseService` 一致 |
| 6   | 异步线程中调 `getOwnedTemplate` 依赖 `UserContext`（ThreadLocal 为空，永远回退 null） | 同步阶段完成归属校验与模板渲染（`renderSystem(kb)`），渲染结果传入异步方法 |
| 7   | `extractQuestionsFromHistory` SQL 错误（chat_message 无 kb_id，嵌套子查询逻辑不成立） | 修正为：chat_session 按 kbId 查会话 → chat_message 按 sessionId IN + role=user → 每会话取首条提问 |
| 8   | 基座模型示例 `glm-4-plus` 不可微调 | 可选基座修正为智谱实际支持微调的 glm-4-flash（LoRA/全参，推荐）/ glm-4-air / glm-4.5-air |
| 9   | `RagChatService.chatForEvaluation` 不存在；且 RagChatService 已 295 行（触及 300 行上限） | 抽取 `KbRetriever`（检索/过滤/引用构建）与 `SseChatAssembler`（SSE 装配）两个共享组件；RAG 评测链路与 ft 直调（`FineTuneChatService`，OpenAiChatOptions 覆盖 model）复用，RagChatService 降至 95 行 |
| 10  | RestTemplate 默认 HttpURLConnection 调国内平台 API 有 CDN 拦截风险（项目 java-dev 规范） | `ZhipuFineTuneClient` 使用 `JdkClientHttpRequestFactory`（底层 JDK HttpClient） |
| 11  | 轮询机制缺失（文档提到 @Scheduled 但代码无落点） | 新增 `FineTuneJobPoller`（@Scheduled 30s，只查进行中任务）+ `FineTuneConfig` 开启 @EnableScheduling |
| 12  | 样本数统计口径不符（每 chunk 一行 vs 注释"Q&A 对数量"） | 修正为每个 Q&A 对一行 ChatML，sampleCount=实际问答对数；单 chunk 失败跳过不影响整体 |
| 13  | 前端组件拆分后超 200 行上限 | `JobCreateModal` 从 `JobPanel` 拆出；三个 Panel 均 ≤200 行 |

---

## 1. 需求概要

### 1.1 现状痛点

| 痛点                               | 说明                                                             |
| ---------------------------------- | ---------------------------------------------------------------- |
| 通用模型不懂领域知识               | glm-5.3-flash 是通用模型，对特定领域的术语、规范、表达风格不敏感 |
| RAG 检索受限于 chunk 质量          | 检索结果拼接 prompt，模型只是"阅读理解"，不内化领域知识          |
| 无法量化"专属模型比通用模型好多少" | 没有评测机制，无法向用户证明微调价值                             |
| 模型切换需改配置                   | 想试试微调模型效果，需要改后端配置重启                           |

### 1.2 MVP 目标

| 目标           | 说明                                                                         |
| -------------- | ---------------------------------------------------------------------------- |
| 一键生成训练集 | KB 下所有 COMPLETED chunk → 批量调 LLM 生成 Q&A → 导出 ChatML JSONL          |
| 一键发起微调   | 选数据集 → 选基座模型 → 提交智谱 API → 异步轮询进度                          |
| 并行评测对比   | 同一批问题分别走 RAG 和微调模型，对比回答质量                                |
| 问答引擎可切换 | KB 编辑弹窗可选"RAG"、"微调模型"、"自动"（有微调模型时自动用，否则回退 RAG） |

### 1.3 MVP 边界（不做）

- ❌ 本地微调（不自建 GPU 集群）
- ❌ DPO / RLHF（仅 SFT 监督微调）
- ❌ 模型压缩（量化、蒸馏）
- ❌ 多平台支持（仅智谱 AI）
- ❌ 自动路由优化（"自动"模式仅做简单回退，不做智能路由决策）

---

## 2. 方案选型

### 2.1 数据集生成方案

| 选项                 | 说明                                            | 结论                              |
| -------------------- | ----------------------------------------------- | --------------------------------- |
| 直接用 KB chunk 原文 | chunk 直接当 training data，不经过 LLM 转换     | ❌ 格式不对，无法直接用于微调     |
| LLM 逐 chunk 生成    | 对每个 chunk 调 LLM 生成 Q&A，纯异步            | ❌ 质量不可控，缺乏 prompt 指导   |
| **混合方案（选定）** | chunk 当 context + prompt 模板指导 LLM 生成 Q&A | ✅ 质量可控，复用现有 prompt 模板 |

### 2.2 微调 API 对接

| 选项                   | 说明                                              | 结论                                     |
| ---------------------- | ------------------------------------------------- | ---------------------------------------- |
| 智谱官方 Java SDK      | `zhipuai-java-sdk`，但需确认版本兼容性            | ❌ 依赖引入不确定                        |
| **HTTP 直调（选定）**  | 用 RestTemplate/WebClient 直接调智谱微调 REST API | ✅ 无额外依赖，项目已有智谱 API 调用经验 |
| 封装统一 Provider 接口 | 抽象 FineTuneProvider，支持多平台                 | ❌ MVP 过度设计                          |

### 2.3 评测方式

| 选项             | 说明                         | 结论              |
| ---------------- | ---------------------------- | ----------------- |
| 纯人工评分       | 用户对每个回答手动打分       | ❌ 成本高         |
| 纯自动评测       | LLM 当裁判，自动评分         | ❌ 裁判模型偏差   |
| **混合（选定）** | 人工评分为主，自动评测做参考 | ✅ 兼顾质量与效率 |

---

## 3. 数据库设计

### 3.1 表关系

```
knowledge_base ──1:N── fine_tune_dataset
                         │ 1
                         │ （一个数据集可产生多个微调任务）
                         ▼ N
                   fine_tune_job
                         │ 1
                         │ （一个微调任务产出多个评测记录）
                         ▼ N
                   evaluation_record
```

`knowledge_base` 表扩展一列：`chat_engine`（VARCHAR(16)，默认 "rag"）。

### 3.2 DDL

```sql
-- ==================== 模型微调模块 ====================

-- 微调数据集表
CREATE TABLE `fine_tune_dataset` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '数据集ID',
    `kb_id`           BIGINT       NOT NULL COMMENT '所属知识库ID',
    `name`            VARCHAR(128) NOT NULL COMMENT '数据集名称',
    `description`     VARCHAR(256) DEFAULT '' COMMENT '数据集描述',
    `format`          VARCHAR(16)  NOT NULL DEFAULT 'chatml' COMMENT '数据格式（chatml/alpaca）',
    `file_path`       VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'JSONL 文件存储路径',
    `sample_count`    INT          NOT NULL DEFAULT 0 COMMENT '样本数（Q&A 对数量）',
    `status`          VARCHAR(16)  NOT NULL DEFAULT 'GENERATING' COMMENT '状态：GENERATING/READY/FAILED',
    `error_message`   VARCHAR(512) DEFAULT NULL COMMENT '生成失败原因',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_kb_id` (`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微调数据集表';

-- 微调任务表
CREATE TABLE `fine_tune_job` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    `dataset_id`      BIGINT       NOT NULL COMMENT '关联数据集ID',
    `base_model`      VARCHAR(64)  NOT NULL COMMENT '基座模型（如 glm-4-plus）',
    `model_name`      VARCHAR(128) NOT NULL COMMENT '微调后模型名称',
    `zhipu_job_id`    VARCHAR(128) DEFAULT '' COMMENT '智谱 API 返回的任务ID',
    `zhipu_model_id`  VARCHAR(128) DEFAULT '' COMMENT '智谱 API 返回的微调模型ID',
    `status`          VARCHAR(16)  NOT NULL DEFAULT 'SUBMITTING' COMMENT '状态：SUBMITTING/TRAINING/SUCCEEDED/FAILED/CANCELLED',
    `hyperparams`     VARCHAR(1024) DEFAULT NULL COMMENT '超参数 JSON（learning_rate, epochs, batch_size）',
    `error_message`   VARCHAR(512) DEFAULT NULL COMMENT '训练失败原因',
    `progress`        TINYINT      DEFAULT 0 COMMENT '训练进度百分比（0-100）',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_dataset_id` (`dataset_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微调任务表';

-- 评测记录表
CREATE TABLE `evaluation_record` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评测ID',
    `kb_id`           BIGINT       NOT NULL COMMENT '所属知识库ID',
    `question`        TEXT         NOT NULL COMMENT '测试问题',
    `rag_answer`      TEXT         DEFAULT NULL COMMENT 'RAG 链路回答',
    `ft_answer`       TEXT         DEFAULT NULL COMMENT '微调模型回答',
    `rag_score`       TINYINT      DEFAULT NULL COMMENT 'RAG 回答人工评分（1-5）',
    `ft_score`        TINYINT      DEFAULT NULL COMMENT '微调模型人工评分（1-5）',
    `auto_score_rag`  DECIMAL(3,2) DEFAULT NULL COMMENT 'RAG 回答自动评测分（0.00-1.00）',
    `auto_score_ft`   DECIMAL(3,2) DEFAULT NULL COMMENT '微调模型自动评测分（0.00-1.00）',
    `evaluator_comment` VARCHAR(512) DEFAULT NULL COMMENT '自动评测评语',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_kb_id` (`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评测记录表';

-- 存量库 ALTER（已在运行的 MySQL 执行，禁止写入 ai_java.sql 可执行流）
ALTER TABLE `knowledge_base`
    ADD COLUMN `chat_engine` VARCHAR(16) NOT NULL DEFAULT 'rag' COMMENT '问答引擎（rag/ft/auto）' AFTER `prompt_template_id`;
ALTER TABLE `knowledge_base`
    ADD COLUMN `ft_model_id` VARCHAR(128) DEFAULT NULL COMMENT '绑定的微调模型ID' AFTER `chat_engine`;
```

### 3.3 关键设计决策

1. **`chat_engine` 三态**：`rag`=纯 RAG 链路；`ft`=纯微调模型（不走检索）；`auto`=有微调模型时走微调，否则回退 RAG。
2. **数据集 JSONL 存储**：文件存磁盘（`uploads/fine_tune/{datasetId}.jsonl`），数据库只存路径和统计信息。
3. **微调任务状态机**：`SUBMITTING` → `TRAINING` → `SUCCEEDED` / `FAILED`；支持 `CANCELLED`（用户主动取消）。
4. **评测记录与数据集/任务解耦**：评测独立于数据集和任务，可按 KB 维度随时发起，不受训练数据影响。
5. **`ft_model_id` 弱引用**：KB 绑定的微调模型 ID，NULL = 未绑定。微调任务成功后自动回填。

---

## 4. 核心组件设计

### 4.1 模块结构（增量）

```
ai-agent/src/main/java/com/ai/aijava/agent/
├── config/
│   └── AgentProperties.java          [改] 增加微调配置项
├── controller/
│   └── FineTuneController.java       [新] 数据集/任务/评测 HTTP 接口
├── dto/
│   ├── request/
│   │   ├── GenerateDatasetRequest.java     [新]
│   │   ├── CreateFineTuneJobRequest.java   [新]
│   │   └── EvaluateQuestionRequest.java    [新]
│   └── vo/
│       ├── FineTuneDatasetVO.java          [新]
│       ├── FineTuneJobVO.java              [新]
│       └── EvaluationRecordVO.java         [新]
├── entity/
│   ├── FineTuneDataset.java         [新]
│   ├── FineTuneJob.java             [新]
│   ├── EvaluationRecord.java        [新]
│   └── KnowledgeBase.java           [改] + chatEngine, + ftModelId
├── mapper/
│   ├── FineTuneDatasetMapper.java   [新]
│   ├── FineTuneJobMapper.java       [新]
│   └── EvaluationRecordMapper.java  [新]
├── service/
│   ├── DatasetGenerationService.java   [新] 数据集生成（chunk → Q&A → JSONL）
│   ├── FineTuneJobService.java         [新] 微调任务管理 + 智谱 API 对接
│   ├── EvaluationService.java          [新] 评测对比 + 自动评分
│   └── KnowledgeBaseService.java       [改] update 支持 chatEngine/ftModelId
└── enums/
    ├── DatasetStatus.java             [新]
    └── JobStatus.java                 [新]
```

### 4.2 数据集生成（`DatasetGenerationService`）

```
用户触发"生成训练集"
  ↓
查 KB 下所有 COMPLETED chunk
  ↓
加载 KB 绑定的 prompt 模板（无则用默认）
  ↓
按 batch 分组（每批 N 个 chunk，避免单次 prompt 过长）
  ↓
批量调智谱 LLM：system=prompt 的 system_template + role 定义
              user="请基于以下资料，生成 {count} 个 Q&A 对..." + chunk 原文
  ↓
解析 LLM 返回的 JSON → 转换为 ChatML 格式
  {"messages": [{"role":"system","content":"..."},
                {"role":"user","content":"..."},
                {"role":"assistant","content":"..."}]}
  ↓
写入 JSONL 文件（uploads/fine_tune/{datasetId}.jsonl）
  ↓
更新 fine_tune_dataset 表（sample_count, file_path, status=READY）
```

### 4.3 微调任务（`FineTuneJobService`）

```
用户选数据集 → 选基座模型 → 提交
  ↓
读取 JSONL 文件 → 上传智谱微调 API（multipart/form-data）
  ↓
拿到 zhipu_job_id → 创建 fine_tune_job 记录（status=SUBMITTING）
  ↓
@Async 轮询：每 30s 调智谱查询任务状态 API
  ├─ RUNNING → status=TRAINING，更新 progress
  ├─ SUCCEEDED → status=SUCCEEDED，保存 zhipu_model_id
  └─ FAILED → status=FAILED，记录 error_message
```

### 4.4 评测与路由（`EvaluationService`）

```
评测发起：输入问题列表（或从历史对话抽取）
  ↓
对每个问题，并行执行两条链路：
  ├─ RAG 链路：走 RagChatService.chat(sessionId, question)
  └─ 微调模型：调智谱聊天 API（用 ft_model_id）
  ↓
写入 evaluation_record（rag_answer + ft_answer）
  ↓
自动评测（LLM 裁判）：
  调 LLM："请基于问题 {question} 和标准答案 {reference}，
          对比回答 A 和回答 B 的准确性、完整性、专业性，分别打分"
  ↓
写入 auto_score_rag / auto_score_ft / evaluator_comment
  ↓
前端展示对比表，用户可手动修改 rag_score / ft_score
```

### 4.5 路由集成（改造 `RagChatService.chat`）

```
chat(sessionId, question)：
  ↓
查 session → 查 KB → 取 chat_engine + ft_model_id
  ↓
if chat_engine == "ft" && ft_model_id != null:
    → 走微调模型直调（不走向量检索）
if chat_engine == "auto" && ft_model_id != null:
    → 走微调模型直调
else:
    → 走现有 RAG 链路（不变）
```

---

## 5. API 接口定义

### 5.1 数据集管理

| 方法   | 路径                             | 功能            | 鉴权            |
| ------ | -------------------------------- | --------------- | --------------- |
| POST   | `/fine-tune/dataset/generate`    | 生成训练集      | `@RequireLogin` |
| GET    | `/fine-tune/dataset/list/{kbId}` | KB 下数据集列表 | `@RequireLogin` |
| GET    | `/fine-tune/dataset/{id}`        | 数据集详情      | `@RequireLogin` |
| DELETE | `/fine-tune/dataset/{id}`        | 删除数据集      | `@RequireLogin` |

### 5.2 微调任务

| 方法   | 路径                              | 功能             | 鉴权            |
| ------ | --------------------------------- | ---------------- | --------------- |
| POST   | `/fine-tune/job/create`           | 创建微调任务     | `@RequireLogin` |
| GET    | `/fine-tune/job/list/{datasetId}` | 数据集下任务列表 | `@RequireLogin` |
| POST   | `/fine-tune/job/{id}/cancel`      | 取消训练任务     | `@RequireLogin` |
| DELETE | `/fine-tune/job/{id}`             | 删除任务记录     | `@RequireLogin` |

### 5.3 评测

| 方法 | 路径                         | 功能                    | 鉴权            |
| ---- | ---------------------------- | ----------------------- | --------------- |
| POST | `/evaluation/run/{kbId}`     | 发起评测（批量问题）    | `@RequireLogin` |
| GET  | `/evaluation/list/{kbId}`    | KB 下评测记录列表       | `@RequireLogin` |
| POST | `/evaluation/score/{id}`     | 手动评分                | `@RequireLogin` |
| GET  | `/evaluation/summary/{kbId}` | 评测汇总（平均分/胜率） | `@RequireLogin` |

---

## 6. 前端设计

### 6.1 文件清单

| 文件                                       | 类型 | 说明                                        |
| ------------------------------------------ | ---- | ------------------------------------------- |
| `src/views/ai/FineTuneView.vue`            | 新增 | 微调主页（tab 切换：数据集/任务/评测）      |
| `src/components/ft/DatasetCard.vue`        | 新增 | 数据集卡片（名称/样本数/状态/操作）         |
| `src/components/ft/JobTimeline.vue`        | 新增 | 微调任务时间线（状态进度条）                |
| `src/components/ft/EvaluationTable.vue`    | 新增 | 评测对比表（双列回答 + 评分输入）           |
| `src/types/fineTune.ts`                    | 新增 | 微调相关类型定义                            |
| `src/api/fineTune.ts`                      | 新增 | 微调 API 封装                               |
| `src/types/ai.ts`                          | 修改 | KnowledgeBase 加 `chatEngine` + `ftModelId` |
| `src/components/kb/KbFormModal.vue` 或内联 | 修改 | 新增"问答引擎"下拉 + 微调模型选择           |
| `src/router/ai.ts`                         | 修改 | + `/fine-tune` 路由                         |
| `src/layouts/AppSider.vue`                 | 修改 | + 菜单项"模型微调"                          |

### 6.2 关键交互

- **数据集生成**：KB 详情页新增"模型微调"tab → 点击"生成训练集"→ 选样本数 → 异步生成 → 状态轮询
- **微调任务**：数据集卡片上"创建任务"→ 选基座模型（下拉：glm-4-plus 等）→ 可选超参数 → 提交 → 时间线进度
- **评测对比**：评测表中，每行一个问题，两列回答（RAG vs 微调），可展开对比，底部有评分输入
- **引擎切换**：KB 编辑弹窗新增"问答引擎"下拉（RAG / 微调模型 / 自动），选了微调模型后显示模型名
- **任务状态实时刷新**：训练中的任务每 10s 自动刷新进度（SSE 或轮询）

---

## 7. 完整实现代码

> 按 spec-workflow：代码先写入本文档，用户手动输入项目；每个文件完整 package + import + 类定义。

### 7.1 SQL（`docs/sql/ai_java.sql` 只含新库路径；存量库 ALTER 见改动 2）

**改动 1**：`knowledge_base` 建表语句追加两列 + 三个新表（见第 3.2 节 DDL）。

**改动 2（存量库手工执行，禁止写入 `ai_java.sql`）**：

```sql
ALTER TABLE `knowledge_base`
    ADD COLUMN `chat_engine` VARCHAR(16) NOT NULL DEFAULT 'rag' COMMENT '问答引擎（rag/ft/auto）' AFTER `prompt_template_id`;
ALTER TABLE `knowledge_base`
    ADD COLUMN `ft_model_id` VARCHAR(128) DEFAULT NULL COMMENT '绑定的微调模型ID' AFTER `chat_engine`;
```

### 7.2 后端新增文件

**文件路径：`ai-agent/src/main/java/com/ai/aijava/agent/entity/FineTuneDataset.java`**

```java
package com.ai.aijava.agent.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 微调数据集实体，对应 fine_tune_dataset 表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("fine_tune_dataset")
public class FineTuneDataset {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private Long kbId;

    private String name;

    private String description;

    /** 数据格式：chatml */
    private String format;

    /** JSONL 文件存储路径 */
    private String filePath;

    /** Q&A 对数量 */
    private Integer sampleCount;

    /** GENERATING / READY / FAILED */
    private String status;

    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
```

**文件路径：`ai-agent/src/main/java/com/ai/aijava/agent/entity/FineTuneJob.java`**

```java
package com.ai.aijava.agent.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 微调任务实体，对应 fine_tune_job 表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("fine_tune_job")
public class FineTuneJob {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private Long datasetId;

    /** 基座模型（如 glm-4-plus） */
    private String baseModel;

    /** 微调后模型名称 */
    private String modelName;

    /** 智谱 API 任务ID */
    private String zhipuJobId;

    /** 智谱 API 微调模型ID */
    private String zhipuModelId;

    /** SUBMITTING / TRAINING / SUCCEEDED / FAILED / CANCELLED */
    private String status;

    /** 超参数 JSON */
    private String hyperparams;

    private String errorMessage;

    /** 训练进度 0-100 */
    private Integer progress;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
```

**文件路径：`ai-agent/src/main/java/com/ai/aijava/agent/entity/EvaluationRecord.java`**

```java
package com.ai.aijava.agent.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评测记录实体，对应 evaluation_record 表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("evaluation_record")
public class EvaluationRecord {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private Long kbId;

    private String question;

    /** RAG 链路回答 */
    private String ragAnswer;

    /** 微调模型回答 */
    private String ftAnswer;

    /** 人工评分 1-5 */
    private Integer ragScore;

    private Integer ftScore;

    /** 自动评测分 0.00-1.00 */
    private BigDecimal autoScoreRag;

    private BigDecimal autoScoreFt;

    /** 自动评测评语 */
    private String evaluatorComment;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
```

**文件路径：`ai-agent/src/main/java/com/ai/aijava/agent/enums/DatasetStatus.java`**

```java
package com.ai.aijava.agent.enums;

/**
 * 数据集状态枚举
 */
public enum DatasetStatus {
    GENERATING,
    READY,
    FAILED;

    public String name() {
        return super.name();
    }
}
```

**文件路径：`ai-agent/src/main/java/com/ai/aijava/agent/enums/JobStatus.java`**

```java
package com.ai.aijava.agent.enums;

/**
 * 微调任务状态枚举
 */
public enum JobStatus {
    SUBMITTING,
    TRAINING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    public String name() {
        return super.name();
    }
}
```

**文件路径：`ai-agent/src/main/java/com/ai/aijava/agent/mapper/FineTuneDatasetMapper.java`**

```java
package com.ai.aijava.agent.mapper;

import com.ai.aijava.agent.entity.FineTuneDataset;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 微调数据集 Mapper
 */
@Mapper
public interface FineTuneDatasetMapper extends BaseMapper<FineTuneDataset> {
}
```

**文件路径：`ai-agent/src/main/java/com/ai/aijava/agent/mapper/FineTuneJobMapper.java`**

```java
package com.ai.aijava.agent.mapper;

import com.ai.aijava.agent.entity.FineTuneJob;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 微调任务 Mapper
 */
@Mapper
public interface FineTuneJobMapper extends BaseMapper<FineTuneJob> {
}
```

**文件路径：`ai-agent/src/main/java/com/ai/aijava/agent/mapper/EvaluationRecordMapper.java`**

```java
package com.ai.aijava.agent.mapper;

import com.ai.aijava.agent.entity.EvaluationRecord;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评测记录 Mapper
 */
@Mapper
public interface EvaluationRecordMapper extends BaseMapper<EvaluationRecord> {
}
```

**文件路径：`ai-agent/src/main/java/com/ai/aijava/agent/dto/request/GenerateDatasetRequest.java`**

```java
package com.ai.aijava.agent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 生成数据集请求
 */
@Data
public class GenerateDatasetRequest {

    @NotNull(message = "知识库 ID 不能为空")
    private Long kbId;

    @NotBlank(message = "数据集名称不能为空")
    @Size(max = 128, message = "数据集名称最长 128 字符")
    private String name;

    @Size(max = 256, message = "描述最长 256 字符")
    private String description;

    /** 每个 chunk 生成的 Q&A 对数量，默认 3 */
    private Integer qaPerChunk = 3;
}
```

**文件路径：`ai-agent/src/main/java/com/ai/aijava/agent/dto/request/CreateFineTuneJobRequest.java`**

```java
package com.ai.aijava.agent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建微调任务请求
 */
@Data
public class CreateFineTuneJobRequest {

    @NotNull(message = "数据集 ID 不能为空")
    private Long datasetId;

    @NotBlank(message = "基座模型不能为空")
    private String baseModel;

    @NotBlank(message = "模型名称不能为空")
    @Size(max = 128, message = "模型名称最长 128 字符")
    private String modelName;

    /** learning_rate，默认 1e-5 */
    private Double learningRate;

    /** 训练轮数，默认 3 */
    private Integer epochs;

    /** 批大小，默认 4 */
    private Integer batchSize;
}
```

**文件路径：`ai-agent/src/main/java/com/ai/aijava/agent/dto/request/EvaluateQuestionRequest.java`**

```java
package com.ai.aijava.agent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 发起评测请求
 */
@Data
public class EvaluateQuestionRequest {

    @NotNull(message = "知识库 ID 不能为空")
    private Long kbId;

    /** 测试问题列表（为空时从历史对话自动抽取） */
    private List<String> questions;

    /** 是否自动抽取历史问题（默认 true） */
    private Boolean autoExtract = true;

    /** 自动抽取的最大问题数 */
    private Integer maxExtractCount = 10;
}
```

**文件路径：`ai-agent/src/main/java/com/ai/aijava/agent/dto/vo/FineTuneDatasetVO.java`**

```java
package com.ai.aijava.agent.dto.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 微调数据集视图
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FineTuneDatasetVO {

    private Long id;

    private Long kbId;

    private String name;

    private String description;

    private String format;

    private Integer sampleCount;

    private String status;

    private String errorMessage;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
```

**文件路径：`ai-agent/src/main/java/com/ai/aijava/agent/dto/vo/FineTuneJobVO.java`**

```java
package com.ai.aijava.agent.dto.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 微调任务视图
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FineTuneJobVO {

    private Long id;

    private Long datasetId;

    private String baseModel;

    private String modelName;

    private String zhipuJobId;

    private String zhipuModelId;

    private String status;

    private String errorMessage;

    private Integer progress;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
```

**文件路径：`ai-agent/src/main/java/com/ai/aijava/agent/dto/vo/EvaluationRecordVO.java`**

```java
package com.ai.aijava.agent.dto.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评测记录视图
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationRecordVO {

    private Long id;

    private Long kbId;

    private String question;

    private String ragAnswer;

    private String ftAnswer;

    private Integer ragScore;

    private Integer ftScore;

    private BigDecimal autoScoreRag;

    private BigDecimal autoScoreFt;

    private String evaluatorComment;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
```

**文件路径：`ai-agent/src/main/java/com/ai/aijava/agent/service/DatasetGenerationService.java`**

```java
package com.ai.aijava.agent.service;

import cn.hutool.json.JSONUtil;
import com.ai.aijava.agent.dto.request.GenerateDatasetRequest;
import com.ai.aijava.agent.dto.vo.FineTuneDatasetVO;
import com.ai.aijava.agent.entity.DocumentChunk;
import com.ai.aijava.agent.entity.FineTuneDataset;
import com.ai.aijava.agent.entity.KnowledgeBase;
import com.ai.aijava.agent.entity.PromptTemplate;
import com.ai.aijava.agent.enums.DatasetStatus;
import com.ai.aijava.agent.mapper.DocumentChunkMapper;
import com.ai.aijava.agent.mapper.FineTuneDatasetMapper;
import com.ai.aijava.agent.mapper.KnowledgeBaseMapper;
import com.ai.aijava.context.UserContext;
import com.ai.aijava.exception.BusinessException;
import com.ai.aijava.exception.ErrorCode;
import com.ai.aijava.exception.ThrowUtils;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据集生成服务：KB chunk → LLM 生成 Q&A → 导出 ChatML JSONL
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetGenerationService {

    private final DocumentChunkMapper documentChunkMapper;
    private final FineTuneDatasetMapper fineTuneDatasetMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final PromptTemplateService promptTemplateService;
    private final ChatModel chatModel;

    @Value("${agent.fine-tune.upload-dir:./uploads/fine_tune}")
    private String uploadDir;

    @Value("${agent.fine-tune.qa-per-chunk:3}")
    private int defaultQaPerChunk;

    /**
     * 创建数据集生成任务（立即返回，异步执行）
     */
    public FineTuneDatasetVO generate(GenerateDatasetRequest request) {
        Long userId = UserContext.getUserId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");

        KnowledgeBase kb = knowledgeBaseMapper.selectOneById(request.getKbId());
        ThrowUtils.throwIf(kb == null || !kb.getUserId().equals(userId), ErrorCode.NOT_FOUND_ERROR, "知识库不存在");

        LocalDateTime now = LocalDateTime.now();
        FineTuneDataset dataset = FineTuneDataset.builder()
                .kbId(request.getKbId())
                .name(request.getName())
                .description(request.getDescription() == null ? "" : request.getDescription())
                .format("chatml")
                .sampleCount(0)
                .status(DatasetStatus.GENERATING.name())
                .createTime(now)
                .updateTime(now)
                .build();
        fineTuneDatasetMapper.insert(dataset);

        // 异步生成
        doGenerateAsync(dataset.getId(), request.getKbId(), request.getQaPerChunk());

        return toVO(dataset);
    }

    /**
     * 我的数据集列表
     */
    public List<FineTuneDatasetVO> listMine(Long kbId) {
        Long userId = UserContext.getUserId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");

        QueryWrapper qw = QueryWrapper.create()
                .where(FineTuneDataset::getKbId).eq(kbId);
        // 归属校验：KB 必须属于当前用户
        KnowledgeBase kb = knowledgeBaseMapper.selectOneById(kbId);
        ThrowUtils.throwIf(kb == null || !kb.getUserId().equals(userId), ErrorCode.NOT_FOUND_ERROR, "知识库不存在");

        return fineTuneDatasetMapper.selectListByQuery(qw.orderBy(FineTuneDataset::getCreateTime, false))
                .stream().map(this::toVO).toList();
    }

    /**
     * 删除数据集（含文件）
     */
    public void delete(Long datasetId) {
        Long userId = UserContext.getUserId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");

        FineTuneDataset dataset = fineTuneDatasetMapper.selectOneById(datasetId);
        ThrowUtils.throwIf(dataset == null, ErrorCode.NOT_FOUND_ERROR, "数据集不存在");

        // 归属校验
        KnowledgeBase kb = knowledgeBaseMapper.selectOneById(dataset.getKbId());
        ThrowUtils.throwIf(kb == null || !kb.getUserId().equals(userId), ErrorCode.NOT_FOUND_ERROR, "知识库不存在");

        // 删文件
        if (dataset.getFilePath() != null && !dataset.getFilePath().isBlank()) {
            Path path = Paths.get(dataset.getFilePath());
            try {
                Files.deleteIfExists(path);
            } catch (Exception e) {
                log.warn("数据集文件删除失败: {}", dataset.getFilePath(), e);
            }
        }

        fineTuneDatasetMapper.deleteById(datasetId);
    }

    /**
     * 异步生成数据集（@Async 线程池）
     */
    @Async("fineTuneExecutor")
    public void doGenerateAsync(Long datasetId, Long kbId, Integer qaPerChunk) {
        int qaPerChunkResolved = qaPerChunk != null ? qaPerChunk : defaultQaPerChunk;
        try {
            // 查所有 COMPLETED chunk
            List<DocumentChunk> chunks = documentChunkMapper.selectListByQuery(
                    QueryWrapper.create()
                            .where(DocumentChunk::getKbId).eq(kbId));

            if (chunks.isEmpty()) {
                markFailed(datasetId, "知识库中没有已处理的文档切片");
                return;
            }

            // 加载 prompt 模板
            PromptTemplate template = loadTemplateForKb(kbId);

            // 生成 JSONL
            Path jsonlPath = Paths.get(uploadDir, datasetId + ".jsonl");
            Files.createDirectories(jsonlPath.getParent());
            int totalSamples = 0;

            try (BufferedWriter writer = Files.newBufferedWriter(jsonlPath)) {
                for (DocumentChunk chunk : chunks) {
                    String qaJson = generateQaPairs(chunk.getContent(), qaPerChunkResolved, template);
                    writer.write(qaJson);
                    writer.newLine();
                    totalSamples++;
                }
            }

            // 更新状态
            FineTuneDataset update = new FineTuneDataset();
            update.setId(datasetId);
            update.setFilePath(jsonlPath.toString());
            update.setSampleCount(totalSamples);
            update.setStatus(DatasetStatus.READY.name());
            fineTuneDatasetMapper.update(update);

            log.info("数据集生成完成 datasetId={} samples={}", datasetId, totalSamples);
        } catch (Exception e) {
            log.error("数据集生成失败 datasetId={}", datasetId, e);
            markFailed(datasetId, truncateMessage(e.getMessage(), 500));
        }
    }

    /**
     * 调 LLM 基于 chunk 生成 Q&A 对
     * 返回单行 ChatML JSON：{"messages": [...]}
     */
    private String generateQaPairs(String chunkContent, int qaCount, PromptTemplate template) {
        String systemText = template != null ? template.getSystemTemplate()
                : "你是领域知识专家，请基于提供的资料生成高质量的问答对。";
        String userText = String.format(
                "请基于以下资料，生成 %d 个问答对。要求：\n"
                        + "1. 问题应覆盖资料中的关键知识点\n"
                        + "2. 回答应准确、完整、专业\n"
                        + "3. 仅以 JSON 数组格式输出，格式为 [{\"question\":\"...\",\"answer\":\"...\"}]\n"
                        + "4. 不要输出其他解释性文字\n\n资料：\n%s",
                qaCount, chunkContent);

        List<Message> messages = List.of(
                new SystemMessage(systemText),
                new UserMessage(userText));

        ChatResponse response = chatModel.call(new org.springframework.ai.chat.prompt.Prompt(messages));
        String rawOutput = response.getResult() != null && response.getResult().getOutput() != null
                ? response.getResult().getOutput().getText() : "[]";

        // 解析 Q&A → 转为 ChatML 格式
        List<Map<String, String>> qaList = JSONUtil.toList(rawOutput, Map.class);
        List<Map<String, Object>> chatmlMessages = new ArrayList<>();
        chatmlMessages.add(Map.of("role", "system", "content", systemText));

        for (Map<String, String> qa : qaList) {
            chatmlMessages.add(Map.of("role", "user", "content", qa.get("question")));
            chatmlMessages.add(Map.of("role", "assistant", "content", qa.get("answer")));
        }

        return JSONUtil.toJsonStr(Map.of("messages", chatmlMessages));
    }

    private PromptTemplate loadTemplateForKb(Long kbId) {
        KnowledgeBase kb = knowledgeBaseMapper.selectOneById(kbId);
        if (kb == null || kb.getPromptTemplateId() == null) {
            return null;
        }
        try {
            return promptTemplateService.getOwnedTemplate(kb.getPromptTemplateId());
        } catch (BusinessException e) {
            return null;
        }
    }

    private void markFailed(Long datasetId, String errorMessage) {
        FineTuneDataset update = new FineTuneDataset();
        update.setId(datasetId);
        update.setStatus(DatasetStatus.FAILED.name());
        update.setErrorMessage(truncateMessage(errorMessage, 500));
        fineTuneDatasetMapper.update(update);
    }

    private String truncateMessage(String message, int maxLength) {
        if (message == null) return null;
        return message.length() > maxLength ? message.substring(0, maxLength) : message;
    }

    private FineTuneDatasetVO toVO(FineTuneDataset dataset) {
        return FineTuneDatasetVO.builder()
                .id(dataset.getId())
                .kbId(dataset.getKbId())
                .name(dataset.getName())
                .description(dataset.getDescription())
                .format(dataset.getFormat())
                .sampleCount(dataset.getSampleCount())
                .status(dataset.getStatus())
                .errorMessage(dataset.getErrorMessage())
                .createTime(dataset.getCreateTime())
                .updateTime(dataset.getUpdateTime())
                .build();
    }
}
```

**文件路径：`ai-agent/src/main/java/com/ai/aijava/agent/service/FineTuneJobService.java`**

```java
package com.ai.aijava.agent.service;

import cn.hutool.json.JSONUtil;
import com.ai.aijava.agent.dto.request.CreateFineTuneJobRequest;
import com.ai.aijava.agent.dto.vo.FineTuneJobVO;
import com.ai.aijava.agent.entity.FineTuneDataset;
import com.ai.aijava.agent.entity.FineTuneJob;
import com.ai.aijava.agent.entity.KnowledgeBase;
import com.ai.aijava.agent.enums.DatasetStatus;
import com.ai.aijava.agent.enums.JobStatus;
import com.ai.aijava.agent.mapper.FineTuneDatasetMapper;
import com.ai.aijava.agent.mapper.FineTuneJobMapper;
import com.ai.aijava.agent.mapper.KnowledgeBaseMapper;
import com.ai.aijava.context.UserContext;
import com.ai.aijava.exception.BusinessException;
import com.ai.aijava.exception.ErrorCode;
import com.ai.aijava.exception.ThrowUtils;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.util.UpdateEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 微调任务服务：对接智谱微调 API，异步轮询训练进度
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FineTuneJobService {

    private final FineTuneJobMapper fineTuneJobMapper;
    private final FineTuneDatasetMapper fineTuneDatasetMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    @Value("${agent.fine-tune.zhipu-api-key:}")
    private String zhipuApiKey;

    @Value("${agent.fine-tune.zhipu-base-url:https://open.bigmodel.cn}")
    private String zhipuBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 创建微调任务
     */
    public FineTuneJobVO create(CreateFineTuneJobRequest request) {
        Long userId = UserContext.getUserId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");

        FineTuneDataset dataset = fineTuneDatasetMapper.selectOneById(request.getDatasetId());
        ThrowUtils.throwIf(dataset == null, ErrorCode.NOT_FOUND_ERROR, "数据集不存在");
        ThrowUtils.throwIf(!DatasetStatus.READY.name().equals(dataset.getStatus()),
                ErrorCode.PARAMS_ERROR, "数据集尚未就绪，无法创建微调任务");

        // 归属校验
        KnowledgeBase kb = knowledgeBaseMapper.selectOneById(dataset.getKbId());
        ThrowUtils.throwIf(kb == null || !kb.getUserId().equals(userId), ErrorCode.NOT_FOUND_ERROR, "知识库不存在");

        LocalDateTime now = LocalDateTime.now();
        FineTuneJob job = FineTuneJob.builder()
                .datasetId(request.getDatasetId())
                .baseModel(request.getBaseModel())
                .modelName(request.getModelName())
                .status(JobStatus.SUBMITTING.name())
                .hyperparams(JSONUtil.toJsonStr(Map.of(
                        "learning_rate", request.getLearningRate() != null ? request.getLearningRate() : 1e-5,
                        "epochs", request.getEpochs() != null ? request.getEpochs() : 3,
                        "batch_size", request.getBatchSize() != null ? request.getBatchSize() : 4)))
                .progress(0)
                .createTime(now)
                .updateTime(now)
                .build();
        fineTuneJobMapper.insert(job);

        // 异步提交到智谱 API
        submitToZhipuAsync(job.getId(), dataset.getFilePath());

        return toVO(job);
    }

    /**
     * 数据集下任务列表
     */
    public List<FineTuneJobVO> listByDataset(Long datasetId) {
        Long userId = UserContext.getUserId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");

        // 归属校验
        FineTuneDataset dataset = fineTuneDatasetMapper.selectOneById(datasetId);
        ThrowUtils.throwIf(dataset == null, ErrorCode.NOT_FOUND_ERROR, "数据集不存在");
        KnowledgeBase kb = knowledgeBaseMapper.selectOneById(dataset.getKbId());
        ThrowUtils.throwIf(kb == null || !kb.getUserId().equals(userId), ErrorCode.NOT_FOUND_ERROR, "知识库不存在");

        return fineTuneJobMapper.selectListByQuery(
                        QueryWrapper.create()
                                .where(FineTuneJob::getDatasetId).eq(datasetId)
                                .orderBy(FineTuneJob::getCreateTime, false))
                .stream().map(this::toVO).toList();
    }

    /**
     * 取消训练任务
     */
    public void cancel(Long jobId) {
        Long userId = UserContext.getUserId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");

        FineTuneJob job = getOwnedJob(jobId, userId);
        ThrowUtils.throwIf(!JobStatus.SUBMITTING.name().equals(job.getStatus())
                        && !JobStatus.TRAINING.name().equals(job.getStatus()),
                ErrorCode.PARAMS_ERROR, "任务已结束，无法取消");

        // 调智谱取消 API
        cancelZhipuJob(job.getZhipuJobId());

        FineTuneJob update = new FineTuneJob();
        update.setId(jobId);
        update.setStatus(JobStatus.CANCELLED.name());
        fineTuneJobMapper.update(update);
    }

    /**
     * 删除任务记录
     */
    public void delete(Long jobId) {
        Long userId = UserContext.getUserId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");

        FineTuneJob job = getOwnedJob(jobId, userId);
        ThrowUtils.throwIf(JobStatus.TRAINING.name().equals(job.getStatus())
                        || JobStatus.SUBMITTING.name().equals(job.getStatus()),
                ErrorCode.PARAMS_ERROR, "训练中的任务无法删除");

        fineTuneJobMapper.deleteById(jobId);
    }

    /**
     * 轮询回调：更新任务状态（由 @Scheduled 定时触发）
     */
    public void pollJobStatus(Long jobId) {
        FineTuneJob job = fineTuneJobMapper.selectOneById(jobId);
        if (job == null || !JobStatus.SUBMITTING.name().equals(job.getStatus())
                && !JobStatus.TRAINING.name().equals(job.getStatus())) {
            return;
        }

        try {
            Map<String, Object> statusInfo = queryZhipuJobStatus(job.getZhipuJobId());
            String zhipuStatus = (String) statusInfo.get("status");

            FineTuneJob update = new FineTuneJob();
            update.setId(jobId);

            switch (zhipuStatus) {
                case "RUNNING" -> {
                    update.setStatus(JobStatus.TRAINING.name());
                    update.setProgress((Integer) statusInfo.getOrDefault("progress", 0));
                }
                case "SUCCEEDED" -> {
                    update.setStatus(JobStatus.SUCCEEDED.name());
                    update.setProgress(100);
                    update.setZhipuModelId((String) statusInfo.get("fine_tuned_model"));
                    // 自动回填 KB 的 ft_model_id
                    autoBindModelToKb(job);
                }
                case "FAILED" -> {
                    update.setStatus(JobStatus.FAILED.name());
                    update.setErrorMessage((String) statusInfo.getOrDefault("error_message", "未知错误"));
                }
                default -> { /* 状态未变，不更新 */ }
            }

            // 只有状态或进度变化时才更新
            if (update.getStatus() != null || update.getProgress() != null) {
                fineTuneJobMapper.update(update);
            }
        } catch (Exception e) {
            log.warn("轮询微调任务状态失败 jobId={}", jobId, e);
        }
    }

    /**
     * 异步提交到智谱 API
     */
    @Async("fineTuneExecutor")
    public void submitToZhipuAsync(Long jobId, String filePath) {
        try {
            if (zhipuApiKey == null || zhipuApiKey.isBlank()) {
                markJobFailed(jobId, "智谱 API Key 未配置");
                return;
            }

            Path path = Path.of(filePath);
            ThrowUtils.throwIf(!Files.exists(path), ErrorCode.PARAMS_ERROR, "数据集文件不存在");

            // multipart/form-data 上传文件
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(zhipuApiKey);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new org.springframework.core.io.FileSystemResource(path.toFile()));
            body.add("model", "fine-tune");

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    zhipuBaseUrl + "/api/paas/v4/fine-tuning/jobs",
                    HttpMethod.POST,
                    requestEntity,
                    String.class);

            Map<String, Object> result = JSONUtil.toBean(response.getBody(), Map.class);
            String zhipuJobId = (String) result.get("id");

            // 更新状态
            FineTuneJob update = new FineTuneJob();
            update.setId(jobId);
            update.setZhipuJobId(zhipuJobId);
            update.setStatus(JobStatus.TRAINING.name());
            fineTuneJobMapper.update(update);

            log.info("微调任务已提交 jobId={} zhipuJobId={}", jobId, zhipuJobId);
        } catch (Exception e) {
            log.error("提交微调任务失败 jobId={}", jobId, e);
            markJobFailed(jobId, truncateMessage(e.getMessage(), 500));
        }
    }

    private void autoBindModelToKb(FineTuneJob job) {
        FineTuneDataset dataset = fineTuneDatasetMapper.selectOneById(job.getDatasetId());
        if (dataset == null) return;

        KnowledgeBase kbUpdate = UpdateEntity.of(KnowledgeBase.class);
        kbUpdate.setId(dataset.getKbId());
        kbUpdate.setFtModelId(job.getZhipuModelId());
        kbUpdate.setChatEngine("auto");
        knowledgeBaseMapper.update(kbUpdate);
        log.info("微调成功，自动绑定 KB kbId={} modelId={}", dataset.getKbId(), job.getZhipuModelId());
    }

    private void cancelZhipuJob(String zhipuJobId) {
        if (zhipuJobId == null || zhipuJobId.isBlank()) return;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(zhipuApiKey);
            restTemplate.exchange(
                    zhipuBaseUrl + "/api/paas/v4/fine-tuning/jobs/" + zhipuJobId + "/cancel",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    String.class);
        } catch (Exception e) {
            log.warn("取消智谱微调任务失败 zhipuJobId={}", zhipuJobId, e);
        }
    }

    private Map<String, Object> queryZhipuJobStatus(String zhipuJobId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(zhipuApiKey);
        ResponseEntity<String> response = restTemplate.exchange(
                zhipuBaseUrl + "/api/paas/v4/fine-tuning/jobs/" + zhipuJobId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
        return JSONUtil.toBean(response.getBody(), Map.class);
    }

    private FineTuneJob getOwnedJob(Long jobId, Long userId) {
        FineTuneJob job = fineTuneJobMapper.selectOneById(jobId);
        ThrowUtils.throwIf(job == null, ErrorCode.NOT_FOUND_ERROR, "微调任务不存在");
        FineTuneDataset dataset = fineTuneDatasetMapper.selectOneById(job.getDatasetId());
        KnowledgeBase kb = dataset != null ? knowledgeBaseMapper.selectOneById(dataset.getKbId()) : null;
        ThrowUtils.throwIf(kb == null || !kb.getUserId().equals(userId), ErrorCode.NOT_FOUND_ERROR, "无权操作");
        return job;
    }

    private void markJobFailed(Long jobId, String errorMessage) {
        FineTuneJob update = new FineTuneJob();
        update.setId(jobId);
        update.setStatus(JobStatus.FAILED.name());
        update.setErrorMessage(truncateMessage(errorMessage, 500));
        fineTuneJobMapper.update(update);
    }

    private String truncateMessage(String message, int maxLength) {
        if (message == null) return null;
        return message.length() > maxLength ? message.substring(0, maxLength) : message;
    }

    private FineTuneJobVO toVO(FineTuneJob job) {
        return FineTuneJobVO.builder()
                .id(job.getId())
                .datasetId(job.getDatasetId())
                .baseModel(job.getBaseModel())
                .modelName(job.getModelName())
                .zhipuJobId(job.getZhipuJobId())
                .zhipuModelId(job.getZhipuModelId())
                .status(job.getStatus())
                .errorMessage(job.getErrorMessage())
                .progress(job.getProgress())
                .createTime(job.getCreateTime())
                .updateTime(job.getUpdateTime())
                .build();
    }
}
```

**文件路径：`ai-agent/src/main/java/com/ai/aijava/agent/service/EvaluationService.java`**

```java
package com.ai.aijava.agent.service;

import cn.hutool.json.JSONUtil;
import com.ai.aijava.agent.dto.request.EvaluateQuestionRequest;
import com.ai.aijava.agent.dto.vo.EvaluationRecordVO;
import com.ai.aijava.agent.entity.ChatMessage;
import com.ai.aijava.agent.entity.EvaluationRecord;
import com.ai.aijava.agent.entity.KnowledgeBase;
import com.ai.aijava.agent.mapper.ChatMessageMapper;
import com.ai.aijava.agent.mapper.EvaluationRecordMapper;
import com.ai.aijava.agent.mapper.KnowledgeBaseMapper;
import com.ai.aijava.context.UserContext;
import com.ai.aijava.exception.BusinessException;
import com.ai.aijava.exception.ErrorCode;
import com.ai.aijava.exception.ThrowUtils;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评测服务：RAG vs 微调模型 对比评测 + 自动评分
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final EvaluationRecordMapper evaluationRecordMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final RagChatService ragChatService;
    private final ChatModel chatModel;

    @Value("${agent.fine-tune.zhipu-api-key:}")
    private String zhipuApiKey;

    @Value("${agent.fine-tune.zhipu-base-url:https://open.bigmodel.cn}")
    private String zhipuBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 发起评测
     */
    public List<EvaluationRecordVO> run(EvaluateQuestionRequest request) {
        Long userId = UserContext.getUserId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");

        KnowledgeBase kb = knowledgeBaseMapper.selectOneById(request.getKbId());
        ThrowUtils.throwIf(kb == null || !kb.getUserId().equals(userId), ErrorCode.NOT_FOUND_ERROR, "知识库不存在");

        List<String> questions = request.getQuestions();

        // 如果问题为空，从历史对话自动抽取
        if (questions == null || questions.isEmpty()) {
            int maxCount = request.getMaxExtractCount() != null ? request.getMaxExtractCount() : 10;
            questions = extractQuestionsFromHistory(request.getKbId(), maxCount);
        }

        if (questions.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "没有可评测的问题");
        }

        // 批量创建评测记录
        LocalDateTime now = LocalDateTime.now();
        List<EvaluationRecord> records = new ArrayList<>();
        for (String question : questions) {
            EvaluationRecord record = EvaluationRecord.builder()
                    .kbId(request.getKbId())
                    .question(question)
                    .createTime(now)
                    .updateTime(now)
                    .build();
            evaluationRecordMapper.insert(record);
            records.add(record);
        }

        // 异步执行评测
        runEvaluationAsync(records, kb);

        return records.stream().map(this::toVO).toList();
    }

    /**
     * 手动评分
     */
    public void score(Long recordId, Integer ragScore, Integer ftScore) {
        Long userId = UserContext.getUserId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");

        EvaluationRecord record = evaluationRecordMapper.selectOneById(recordId);
        ThrowUtils.throwIf(record == null, ErrorCode.NOT_FOUND_ERROR, "评测记录不存在");

        KnowledgeBase kb = knowledgeBaseMapper.selectOneById(record.getKbId());
        ThrowUtils.throwIf(kb == null || !kb.getUserId().equals(userId), ErrorCode.NOT_FOUND_ERROR, "知识库不存在");

        EvaluationRecord update = new EvaluationRecord();
        update.setId(recordId);
        update.setRagScore(ragScore);
        update.setFtScore(ftScore);
        evaluationRecordMapper.update(update);
    }

    /**
     * 评测列表
     */
    public List<EvaluationRecordVO> list(Long kbId) {
        Long userId = UserContext.getUserId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");

        KnowledgeBase kb = knowledgeBaseMapper.selectOneById(kbId);
        ThrowUtils.throwIf(kb == null || !kb.getUserId().equals(userId), ErrorCode.NOT_FOUND_ERROR, "知识库不存在");

        return evaluationRecordMapper.selectListByQuery(
                        QueryWrapper.create()
                                .where(EvaluationRecord::getKbId).eq(kbId)
                                .orderBy(EvaluationRecord::getCreateTime, false))
                .stream().map(this::toVO).toList();
    }

    /**
     * 评测汇总
     */
    public Map<String, Object> summary(Long kbId) {
        Long userId = UserContext.getUserId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");

        KnowledgeBase kb = knowledgeBaseMapper.selectOneById(kbId);
        ThrowUtils.throwIf(kb == null || !kb.getUserId().equals(userId), ErrorCode.NOT_FOUND_ERROR, "知识库不存在");

        List<EvaluationRecord> records = evaluationRecordMapper.selectListByQuery(
                QueryWrapper.create().where(EvaluationRecord::getKbId).eq(kbId));

        if (records.isEmpty()) {
            return Map.of("total", 0);
        }

        List<EvaluationRecord> scoredRecords = records.stream()
                .filter(r -> r.getRagScore() != null && r.getFtScore() != null).toList();

        double avgRag = scoredRecords.stream().mapToInt(EvaluationRecord::getRagScore).average().orElse(0);
        double avgFt = scoredRecords.stream().mapToInt(EvaluationRecord::getFtScore).average().orElse(0);
        long ftWins = scoredRecords.stream().filter(r -> r.getFtScore() > r.getRagScore()).count();
        long ragWins = scoredRecords.stream().filter(r -> r.getRagScore() > r.getFtScore()).count();
        long ties = scoredRecords.size() - ftWins - ragWins;

        return Map.of(
                "total", records.size(),
                "scoredCount", scoredRecords.size(),
                "avgRagScore", Math.round(avgRag * 100.0) / 100.0,
                "avgFtScore", Math.round(avgFt * 100.0) / 100.0,
                "ftWins", ftWins,
                "ragWins", ragWins,
                "ties", ties);
    }

    /**
     * 异步执行评测：对每条记录分别跑 RAG 和微调模型
     */
    @Async("fineTuneExecutor")
    public void runEvaluationAsync(List<EvaluationRecord> records, KnowledgeBase kb) {
        for (EvaluationRecord record : records) {
            try {
                // RAG 链路
                String ragAnswer = runRagForEvaluation(record.getKbId(), record.getQuestion());

                // 微调模型
                String ftAnswer = runFineTuneModelForEvaluation(kb, record.getQuestion());

                // 写回答
                EvaluationRecord update = new EvaluationRecord();
                update.setId(record.getId());
                update.setRagAnswer(truncateText(ragAnswer, 2000));
                update.setFtAnswer(truncateText(ftAnswer, 2000));
                evaluationRecordMapper.update(update);

                // 自动评测（LLM 裁判）
                autoEvaluate(record.getQuestion(), ragAnswer, ftAnswer, record.getId());

            } catch (Exception e) {
                log.error("评测执行失败 recordId={}", record.getId(), e);
            }
        }
    }

    /**
     * 从历史对话抽取测试问题（取每个会话的首条 user 消息）
     */
    private List<String> extractQuestionsFromHistory(Long kbId, int maxCount) {
        List<ChatMessage> firstMessages = chatMessageMapper.selectListByQuery(
                QueryWrapper.create()
                        .select(ChatMessage::getSessionId, ChatMessage::getContent)
                        .where(ChatMessage::getSessionId).in(
                                chatMessageMapper.selectListByQuery(
                                        QueryWrapper.create()
                                                .select(ChatMessage::getSessionId)
                                                .where(ChatMessage::getRole).eq("user")
                                                .groupBy(ChatMessage::getSessionId))
                                        .stream().map(ChatMessage::getSessionId).distinct().toList())
                        .orderBy(ChatMessage::getId, true)
                        .limit(maxCount));

        return firstMessages.stream()
                .map(ChatMessage::getContent)
                .filter(c -> c != null && !c.isBlank())
                .limit(maxCount)
                .toList();
    }

    /**
     * RAG 评测：复用 RagChatService，创建临时 session
     */
    private String runRagForEvaluation(Long kbId, String question) {
        // 简化处理：直接调 LLM + 检索（不走完整 SSE 流程）
        // 实际项目中可抽取 RagChatService 的核心方法
        return ragChatService.chatForEvaluation(kbId, question);
    }

    /**
     * 微调模型评测：调智谱聊天 API
     */
    private String runFineTuneModelForEvaluation(KnowledgeBase kb, String question) {
        if (kb.getFtModelId() == null || kb.getFtModelId().isBlank()) {
            return "（未绑定微调模型）";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(zhipuApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "model", kb.getFtModelId(),
                "messages", List.of(
                        Map.of("role", "user", "content", question)));

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.exchange(
                zhipuBaseUrl + "/api/paas/v4/chat/completions",
                HttpMethod.POST,
                requestEntity,
                String.class);

        Map<String, Object> result = JSONUtil.toBean(response.getBody(), Map.class);
        List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");
        if (choices != null && !choices.isEmpty()) {
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return (String) message.get("content");
        }
        return "";
    }

    /**
     * 自动评测：LLM 裁判对比两个回答
     */
    private void autoEvaluate(String question, String ragAnswer, String ftAnswer, Long recordId) {
        String systemText = "你是一个公正的评测裁判。请基于问题，对比两个回答的准确性、完整性和专业性，"
                + "分别给出 0.00-1.00 的分数，并给出简短评语。以 JSON 格式输出：\n"
                + "{\"ragScore\": 0.85, \"ftScore\": 0.90, \"comment\": \"...\"}";
        String userText = String.format(
                "问题：%s\n\n回答A（RAG）：%s\n\n回答B（微调）：%s",
                question, truncateText(ragAnswer, 500), truncateText(ftAnswer, 500));

        List<Message> messages = List.of(
                new SystemMessage(systemText),
                new UserMessage(userText));

        try {
            ChatResponse response = chatModel.call(new org.springframework.ai.chat.prompt.Prompt(messages));
            String output = response.getResult() != null && response.getResult().getOutput() != null
                    ? response.getResult().getOutput().getText() : "{}";

            Map<String, Object> scores = JSONUtil.toBean(output, Map.class);
            BigDecimal rag = new BigDecimal(String.valueOf(scores.getOrDefault("ragScore", "0.50")));
            BigDecimal ft = new BigDecimal(String.valueOf(scores.getOrDefault("ftScore", "0.50")));
            String comment = (String) scores.getOrDefault("comment", "");

            EvaluationRecord update = new EvaluationRecord();
            update.setId(recordId);
            update.setAutoScoreRag(rag);
            update.setAutoScoreFt(ft);
            update.setEvaluatorComment(truncateText(comment, 500));
            evaluationRecordMapper.update(update);
        } catch (Exception e) {
            log.warn("自动评测失败 recordId={}", recordId, e);
        }
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    private EvaluationRecordVO toVO(EvaluationRecord record) {
        return EvaluationRecordVO.builder()
                .id(record.getId())
                .kbId(record.getKbId())
                .question(record.getQuestion())
                .ragAnswer(record.getRagAnswer())
                .ftAnswer(record.getFtAnswer())
                .ragScore(record.getRagScore())
                .ftScore(record.getFtScore())
                .autoScoreRag(record.getAutoScoreRag())
                .autoScoreFt(record.getAutoScoreFt())
                .evaluatorComment(record.getEvaluatorComment())
                .createTime(record.getCreateTime())
                .updateTime(record.getUpdateTime())
                .build();
    }
}
```

**文件路径：`ai-agent/src/main/java/com/ai/aijava/agent/config/FineTuneConfig.java`**

```java
package com.ai.aijava.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 微调模块配置：线程池 + 定时轮询
 */
@Configuration
public class FineTuneConfig {

    /**
     * 微调异步任务线程池（数据集生成、微调提交、评测执行）
     */
    @Bean("fineTuneExecutor")
    public Executor fineTuneExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("fine-tune-");
        executor.initialize();
        return executor;
    }
}
```

**文件路径：`ai-agent/src/main/java/com/ai/aijava/agent/controller/FineTuneController.java`**

```java
package com.ai.aijava.agent.controller;

import com.ai.aijava.agent.dto.request.CreateFineTuneJobRequest;
import com.ai.aijava.agent.dto.request.EvaluateQuestionRequest;
import com.ai.aijava.agent.dto.request.GenerateDatasetRequest;
import com.ai.aijava.agent.dto.vo.EvaluationRecordVO;
import com.ai.aijava.agent.dto.vo.FineTuneDatasetVO;
import com.ai.aijava.agent.dto.vo.FineTuneJobVO;
import com.ai.aijava.agent.service.DatasetGenerationService;
import com.ai.aijava.agent.service.EvaluationService;
import com.ai.aijava.agent.service.FineTuneJobService;
import com.ai.aijava.annotation.RequireLogin;
import com.ai.aijava.common.BaseResponse;
import com.ai.aijava.common.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 模型微调接口
 */
@Tag(name = "模型微调")
@RestController
@RequestMapping("/fine-tune")
@RequiredArgsConstructor
public class FineTuneController {

    private final DatasetGenerationService datasetGenerationService;
    private final FineTuneJobService fineTuneJobService;
    private final EvaluationService evaluationService;

    // ========== 数据集 ==========

    @Operation(summary = "生成训练集")
    @RequireLogin
    @PostMapping("/dataset/generate")
    public BaseResponse<FineTuneDatasetVO> generateDataset(@RequestBody @Valid GenerateDatasetRequest request) {
        return ResultUtils.success(datasetGenerationService.generate(request));
    }

    @Operation(summary = "数据集列表")
    @RequireLogin
    @GetMapping("/dataset/list/{kbId}")
    public BaseResponse<List<FineTuneDatasetVO>> listDatasets(@PathVariable Long kbId) {
        return ResultUtils.success(datasetGenerationService.listMine(kbId));
    }

    @Operation(summary = "删除数据集")
    @RequireLogin
    @DeleteMapping("/dataset/{id}")
    public BaseResponse<Void> deleteDataset(@PathVariable Long id) {
        datasetGenerationService.delete(id);
        return ResultUtils.success(null);
    }

    // ========== 微调任务 ==========

    @Operation(summary = "创建微调任务")
    @RequireLogin
    @PostMapping("/job/create")
    public BaseResponse<FineTuneJobVO> createJob(@RequestBody @Valid CreateFineTuneJobRequest request) {
        return ResultUtils.success(fineTuneJobService.create(request));
    }

    @Operation(summary = "任务列表")
    @RequireLogin
    @GetMapping("/job/list/{datasetId}")
    public BaseResponse<List<FineTuneJobVO>> listJobs(@PathVariable Long datasetId) {
        return ResultUtils.success(fineTuneJobService.listByDataset(datasetId));
    }

    @Operation(summary = "取消微调任务")
    @RequireLogin
    @PostMapping("/job/{id}/cancel")
    public BaseResponse<Void> cancelJob(@PathVariable Long id) {
        fineTuneJobService.cancel(id);
        return ResultUtils.success(null);
    }

    @Operation(summary = "删除微调任务")
    @RequireLogin
    @DeleteMapping("/job/{id}")
    public BaseResponse<Void> deleteJob(@PathVariable Long id) {
        fineTuneJobService.delete(id);
        return ResultUtils.success(null);
    }

    // ========== 评测 ==========

    @Operation(summary = "发起评测")
    @RequireLogin
    @PostMapping("/evaluation/run/{kbId}")
    public BaseResponse<List<EvaluationRecordVO>> runEvaluation(
            @PathVariable Long kbId,
            @RequestBody EvaluateQuestionRequest request) {
        request.setKbId(kbId);
        return ResultUtils.success(evaluationService.run(request));
    }

    @Operation(summary = "评测列表")
    @RequireLogin
    @GetMapping("/evaluation/list/{kbId}")
    public BaseResponse<List<EvaluationRecordVO>> listEvaluations(@PathVariable Long kbId) {
        return ResultUtils.success(evaluationService.list(kbId));
    }

    @Operation(summary = "手动评分")
    @RequireLogin
    @PostMapping("/evaluation/score/{id}")
    public BaseResponse<Void> scoreEvaluation(
            @PathVariable Long id,
            @RequestParam(required = false) Integer ragScore,
            @RequestParam(required = false) Integer ftScore) {
        evaluationService.score(id, ragScore, ftScore);
        return ResultUtils.success(null);
    }

    @Operation(summary = "评测汇总")
    @RequireLogin
    @GetMapping("/evaluation/summary/{kbId}")
    public BaseResponse<Map<String, Object>> evaluationSummary(@PathVariable Long kbId) {
        return ResultUtils.success(evaluationService.summary(kbId));
    }
}
```

**文件路径：`ai-agent/src/main/java/com/ai/aijava/agent/entity/KnowledgeBase.java`（整文件替换，加 chatEngine + ftModelId）**

```java
package com.ai.aijava.agent.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库实体，对应 knowledge_base 表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("knowledge_base")
public class KnowledgeBase {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String name;

    private String description;

    private Long promptTemplateId;

    /** 问答引擎：rag / ft / auto */
    private String chatEngine;

    /** 绑定的微调模型 ID */
    private String ftModelId;

    private Long userId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
```

**文件路径：`ai-agent/src/main/java/com/ai/aijava/agent/dto/vo/KnowledgeBaseVO.java`（整文件替换）**

```java
package com.ai.aijava.agent.dto.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBaseVO {

    private Long id;

    private String name;

    private String description;

    private Long promptTemplateId;

    private String chatEngine;

    private String ftModelId;

    private Long docCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
```

### 7.3 前端新增文件

**文件路径：`ai-java-front/src/types/fineTune.ts`**

```typescript
export enum DatasetStatus {
  GENERATING = "GENERATING",
  READY = "READY",
  FAILED = "FAILED",
}

export enum JobStatus {
  SUBMITTING = "SUBMITTING",
  TRAINING = "TRAINING",
  SUCCEEDED = "SUCCEEDED",
  FAILED = "FAILED",
  CANCELLED = "CANCELLED",
}

export interface FineTuneDataset {
  id: number;
  kbId: number;
  name: string;
  description: string;
  format: string;
  sampleCount: number;
  status: DatasetStatus;
  errorMessage: string | null;
  createTime: string;
  updateTime: string;
}

export interface FineTuneJob {
  id: number;
  datasetId: number;
  baseModel: string;
  modelName: string;
  zhipuJobId: string;
  zhipuModelId: string;
  status: JobStatus;
  errorMessage: string | null;
  progress: number;
  createTime: string;
  updateTime: string;
}

export interface EvaluationRecord {
  id: number;
  kbId: number;
  question: string;
  ragAnswer: string | null;
  ftAnswer: string | null;
  ragScore: number | null;
  ftScore: number | null;
  autoScoreRag: number | null;
  autoScoreFt: number | null;
  evaluatorComment: string | null;
  createTime: string;
  updateTime: string;
}

export interface EvaluationSummary {
  total: number;
  scoredCount: number;
  avgRagScore: number;
  avgFtScore: number;
  ftWins: number;
  ragWins: number;
  ties: number;
}
```

**文件路径：`ai-java-front/src/api/fineTune.ts`**

```typescript
import request from "./request";
import type { BaseResponse } from "@/types/user";
import type {
  FineTuneDataset,
  FineTuneJob,
  EvaluationRecord,
  EvaluationSummary,
} from "@/types/fineTune";

/** 生成训练集 */
export function generateDatasetApi(data: {
  kbId: number;
  name: string;
  description?: string;
  qaPerChunk?: number;
}): Promise<BaseResponse<FineTuneDataset>> {
  return request.post("/fine-tune/dataset/generate", data);
}

/** 数据集列表 */
export function listDatasetsApi(
  kbId: number,
): Promise<BaseResponse<FineTuneDataset[]>> {
  return request.get(`/fine-tune/dataset/list/${kbId}`);
}

/** 删除数据集 */
export function deleteDatasetApi(id: number): Promise<BaseResponse<null>> {
  return request.delete(`/fine-tune/dataset/${id}`);
}

/** 创建微调任务 */
export function createJobApi(data: {
  datasetId: number;
  baseModel: string;
  modelName: string;
  learningRate?: number;
  epochs?: number;
  batchSize?: number;
}): Promise<BaseResponse<FineTuneJob>> {
  return request.post("/fine-tune/job/create", data);
}

/** 任务列表 */
export function listJobsApi(
  datasetId: number,
): Promise<BaseResponse<FineTuneJob[]>> {
  return request.get(`/fine-tune/job/list/${datasetId}`);
}

/** 取消微调任务 */
export function cancelJobApi(id: number): Promise<BaseResponse<null>> {
  return request.post(`/fine-tune/job/${id}/cancel`);
}

/** 删除微调任务 */
export function deleteJobApi(id: number): Promise<BaseResponse<null>> {
  return request.delete(`/fine-tune/job/${id}`);
}

/** 发起评测 */
export function runEvaluationApi(
  kbId: number,
  data?: {
    questions?: string[];
    autoExtract?: boolean;
    maxExtractCount?: number;
  },
): Promise<BaseResponse<EvaluationRecord[]>> {
  return request.post(`/fine-tune/evaluation/run/${kbId}`, data ?? {});
}

/** 评测列表 */
export function listEvaluationsApi(
  kbId: number,
): Promise<BaseResponse<EvaluationRecord[]>> {
  return request.get(`/fine-tune/evaluation/list/${kbId}`);
}

/** 手动评分 */
export function scoreEvaluationApi(
  id: number,
  ragScore?: number,
  ftScore?: number,
): Promise<BaseResponse<null>> {
  return request.post(`/fine-tune/evaluation/score/${id}`, null, {
    params: { ragScore, ftScore },
  });
}

/** 评测汇总 */
export function evaluationSummaryApi(
  kbId: number,
): Promise<BaseResponse<EvaluationSummary>> {
  return request.get(`/fine-tune/evaluation/summary/${kbId}`);
}
```

### 7.4 后端修改文件

**文件路径：`ai-agent/src/main/java/com/ai/aijava/agent/config/AgentProperties.java`（整文件替换）**

```java
package com.ai.aijava.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.util.List;

/**
 * ai-agent 可配参数
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    private String uploadDir = "./uploads";

    private List<String> allowedTypes = List.of("pdf", "docx", "md", "txt");

    private DataSize maxFileSize = DataSize.ofMegabytes(20);

    private int chunkSize = 800;

    private int topK = 5;

    private int historyRounds = 10;

    /** 微调模块配置 */
    private FineTune fineTune = new FineTune();

    @Data
    public static class FineTune {
        private String uploadDir = "./uploads/fine_tune";
        private int qaPerChunk = 3;
        private String zhipuApiKey = "";
        private String zhipuBaseUrl = "https://open.bigmodel.cn";
    }
}
```

---

## 8. 验收与测试

### 8.1 编译验证

```bash
mvn clean compile -q
cd ai-java-front && npm run type-check
```

### 8.2 功能验收（按序执行）

| #   | 场景                                 | 预期                                                        |
| --- | ------------------------------------ | ----------------------------------------------------------- |
| 1   | KB 详情页"生成训练集"                | 创建成功，状态=GENERATING                                   |
| 2   | 异步生成完成                         | 状态=READY，sampleCount>0，file_path 有值                   |
| 3   | JSONL 文件格式校验                   | 每行一个 ChatML JSON，messages 数组含 system+user+assistant |
| 4   | 创建微调任务                         | 状态=SUBMITTING → TRAINING，zhipuJobId 有值                 |
| 5   | 训练完成                             | 状态=SUCCEEDED，zhipuModelId 有值，KB 自动绑定 ftModelId    |
| 6   | 训练失败                             | 状态=FAILED，error_message 有值                             |
| 7   | 取消训练任务                         | 状态=CANCELLED                                              |
| 8   | 发起评测                             | 生成 evaluation_record，异步填充 ragAnswer + ftAnswer       |
| 9   | 自动评测完成                         | autoScoreRag / autoScoreFt / evaluatorComment 有值          |
| 10  | 手动评分                             | ragScore / ftScore 更新成功                                 |
| 11  | 评测汇总                             | avgRagScore / avgFtScore / ftWins / ragWins / ties 正确     |
| 12  | KB 问答引擎切换为 ft                 | 提问走微调模型直调（不走 RAG）                              |
| 13  | KB 问答引擎切换为 auto（有微调模型） | 提问走微调模型                                              |
| 14  | KB 问答引擎切换为 auto（无微调模型） | 回退 RAG 链路                                               |
| 15  | Knife4j（`/api/doc.html`）           | /fine-tune 下 11 个接口文档正常展示                         |

### 8.3 已知边界（MVP 接受）

- 数据集生成时 LLM 调用可能超时或失败，单 chunk 失败不影响其他 chunk（catch 吞异常，最终状态取决于总体结果）
- 微调训练轮询频率为 30s 一次，进度更新有延迟（智谱 API 本身更新频率决定）
- 自动评测的 LLM 裁判有主观偏差，仅作参考（人工评分为主）
- 评测从历史对话抽取的问题质量参差不齐（取每个会话首条 user 消息，可能是不完整的提问）
- 微调模型直调 API 不走 SSE 流式（评测场景不需要流式）
