# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# ai-java 项目开发指南

Spring Boot 4.1 + Vue 3 + TypeScript + MyBatis-Flex 全栈项目（当前为初始化骨架，业务模块待开发）。

## 常用命令

### 后端（项目根目录）

- `mvn clean compile` # 清理并编译全模块
- `mvn clean package` # 生产构建（ai-basic 普通 jar + ai-web 可执行 jar）
- `mvn install -pl ai-basic -am -DskipTests` # 安装基础库到本地仓库（首次启动或 ai-basic 变更后需要）
- `mvn spring-boot:run -pl ai-web` # 启动后端开发服务器（需先执行上一条 install）
- `mvn test` # 运行测试

### 前端（ai-java-front/）

- `npm run dev` # 启动 Vite 开发服务器
- `npm run build` # 生产构建（并行执行 type-check + build-only）
- `npm run type-check` # TypeScript 类型检查（vue-tsc --build）
- `npm run preview` # 预览生产构建

## 架构概览

### 模块结构（Maven 多模块）

- **根 POM**：聚合 + dependencyManagement 统一管版本，不含代码
- **ai-basic**：通用基础库（统一响应、异常体系、工具类、上下文、注解），包名 `com.ai.aijava.*`
- **ai-web**：应用模块（启动类、controller/service/mapper/entity/dto/config），依赖 ai-basic

### 后端（Java 21 + Spring Boot 4.1）

- **基础包**：`com.ai.aijava`
- **入口类**：`ai-web/src/main/java/com/ai/aijava/AiJavaApplication.java`
- **ORM**：MyBatis-Flex（含代码生成器 `mybatis-flex-codegen`，TableDef 由生成器产出，不手写）
- **数据库**：MySQL + HikariCP 连接池
- **缓存**：Redis（含 commons-pool2 连接池）+ Caffeine 本地缓存
- **接口文档**：Knife4j（OpenAPI 3，地址 `http://localhost:8120/api/doc.html`；端口 8120、context path `/api` 见 `ai-web/src/main/resources/application-prod.yml`）
- **工具库**：Hutool、Lombok、Spring AOP

### 前端（ai-java-front/）

- **框架**：Vue 3 + TypeScript + Vite
- **状态管理**：Pinia（`src/stores/`）
- **路由**：Vue Router（`src/router/index.ts`）
- **类型检查**：构建时强制 `vue-tsc` 类型检查

## 详细规范

代码规范、防御性规则等不在本文件重复，详见 `.claude/rules/`：

- `rules.md` — 后端分层 / Lombok / MyBatis-Flex / 前端组合式 API 等编码规范
- `claude-code-defensive.md` — AI 协作防御性规则（禁止测试篡改、过度工程化、配置开关掩盖错误等）
- `chinese-language.md` — 默认使用简体中文回复、注释、commit message
- `date-calc.md` — 日期计算规则（禁止默认月末对齐）
- `file-size-limit.md` — 文件行数限制（Java 300 / Vue 200 / Go 400）
- `bash-style.md` / `doc-sync.md` / `ops-safety.md` — 其他场景规则

## SubAgents

`.claude/agents/` 配置了专属智能体，可自然语言调用或 `/agent` 触发：

- **code-reviewer** — 代码审查（正确性、安全性、最佳实践）
- **researcher** — 代码研究（架构探索、数据流追踪）
