## AI 知识库问答平台

地址 ：https://github.com/PrimePeace/ai-java

Spring Boot 4.1 + Vue 3 + TypeScript + MyBatis-Flex 全栈项目。

## 模块结构（Maven 多模块）

```
ai-java/
├── ai-basic/         # 通用基础库（统一响应、异常体系、工具类、上下文、注解）
├── ai-web/           # 应用模块（启动类、controller/service/mapper/entity/dto/config）
└── ai-java-front/    # 前端（Vue 3 + TypeScript + Vite）
```

- 根 POM：聚合 + dependencyManagement 统一管版本
- 基础类与应用代码包名统一为 `com.ai.aijava.*`

## 快速开始

### 后端（Java 21，项目根目录执行）

```
mvn clean compile                          # 编译全模块
mvn install -pl ai-basic -am -DskipTests   # 安装基础库（首次启动或 ai-basic 变更后需要）
mvn spring-boot:run -pl ai-web             # 启动（需先执行上一条 install）
```

启动后接口文档：[http://localhost:8120/api/doc.html（端口与](http://localhost:8120/api/doc.html%EF%BC%88%E7%AB%AF%E5%8F%A3%E4%B8%8E) context path 见 `ai-web/src/main/resources/application-prod.yml`）

### 前端（ai-java-front/）

```
npm install
npm run dev
```

------

## SubAgents 使用指南

本项目在 `.claude/agents/` 目录下配置了专属智能体，用于辅助开发工作。

### 可用 Agent

Agent说明**code-reviewer**代码审查 — 检查正确性、安全性和最佳实践**researcher**代码研究 — 深入探索代码库架构和数据流

### 使用方式

**方式一：自然语言调用**

直接告诉我你要使用的 Agent，例如：

- "用 code-reviewer 审查这个文件"
- "让 researcher 帮我找一下认证模块在哪里"

**方式二：使用命令**

```
/agent code-reviewer
/agent researcher
```

**方式三：自动分发**

多任务场景下，我会自动将子任务分发给对应的 Agent，例如：

- "帮我审查这三个文件并找到用户模块"
- "分析一下这个项目的架构"

### 注意事项

- Agent 配置存储在 `.claude/agents/` 目录下，可随时新增或修改
- 提到 Agent 名称即可自动调用，无需记住特殊命令格式

### 项目描述 

负责设计并实现一套私有知识库问答平台：用户创建知识库、上传文档后，系统异步解析并向量化，再基 于检索结果做多轮流式问答，并给出引用来源。用 Apache Tika 解析 PDF、Word、Markdown、TXT，按 Token 切分后做 Embedding，写入 Redis HNSW 向量库，并用知识库 ID 做元数据过滤；摄取过程用异步状态机跟踪上传、处理中、完 成和失败。 实现 RAG 流式问答：检索时过采样并过滤未完成片段，拼装系统提示词、知识片段和对话历史，通 过 SSE 推送答案，同时返回命中的文档片段作为引用。 自研提示词模板渲染，用变量白名单做字面替换，避免模板引擎和 JSON 大括号冲突；知识库可绑定 或解绑模板。对话记忆直接读写消息表，按轮次截取上下文，避免全量覆盖丢掉历史和引用。 用本地风格蒸馏替代官方模型微调：从知识库样本生成风格提示词，同一问题走 RAG 与风格两条链路 对比，支持人工评分和模型裁判，知识库可在 RAG、风格、自动之间切换。 以 MCP Server 向外部客户端暴露知识库列表和检索能力，并用 Token 做访问校验；仪表盘汇总知识 库文档量、最近会话和 Token 消耗。 

### 数据看板 
查看系统的知识库汇总和模型token消耗量

![](https://cdn.nlark.com/yuque/0/2026/png/65082519/1791369230585-5e2bd188-0107-4b74-8596-05585125ede7.png)



### 知识库管理
### 看板
查看所有知识库，用户可以上传word，pdf，markdown文件作为自己的知识库

![](https://cdn.nlark.com/yuque/0/2026/png/65082519/1791366736093-6c8f4fb9-2dc3-4b42-bea2-44d45023c542.png)

### prompt
提示词编辑中心，用户可以根据知识库，训练自己的模型回答的方式，更加灵活的使用自己的AI

![](https://cdn.nlark.com/yuque/0/2026/png/65082519/1791366754992-65af5a22-a6f7-4932-93b7-b46e777c05e4.png)



### 训练
模型训练集，用户可以根据自己的模板，通过生成数据集，风格面板，开始风格评测用户可以把自己的prompt喂给AI，然后让AI对用户上传的知识库，提出问题，再做出回答，然后手动打分，让模型训练成更加偏向个人的风格。

![](https://cdn.nlark.com/yuque/0/2026/png/65082519/1791366792690-46da510a-a9d2-4bbf-bcde-93f31d5619ed.png)



### 智能问答
模型训练完成之后，选择训练的知识库，就可以再这里来人工输入对话，就像和豆包，deepseek等聊天AI一样，提出问题，选择对应知识库，来回答选择知识库的内容。

![](https://cdn.nlark.com/yuque/0/2026/png/65082519/1791369286840-e6d5f098-efee-404d-a9d6-dd33e8d90db1.png)









