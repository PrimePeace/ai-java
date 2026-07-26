# ai-java

## SubAgents 使用指南

本项目在 `.claude/agents/` 目录下配置了专属智能体，用于辅助开发工作。

### 可用 Agent

| Agent | 说明 |
|-------|------|
| **code-reviewer** | 代码审查 — 检查正确性、安全性和最佳实践 |
| **researcher** | 代码研究 — 深入探索代码库架构和数据流 |

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