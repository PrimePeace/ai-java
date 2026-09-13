# ai-agent 项目开发知识库

> 本项目 ai-agent 模块所采用技术的官方文档地址索引，编码前查阅对应文档确认语法与版本兼容性。

---

## 核心框架

| 技术             | 版本      | 官方文档                                                      |
| ---------------- | --------- | ------------------------------------------------------------- |
| Spring Boot      | 4.1.0     | https://docs.spring.io/spring-boot/reference/                 |
| Spring Framework | 7.0       | https://docs.spring.io/spring-framework/reference/            |
| Java             | 21        | https://docs.oracle.com/en/java/javase/21/                    |
| Java             | 21API文档 | https://docs.oracle.com/en/java/javase/21/docs/api/index.html |

## Spring AI

| 技术                    | 版本  | 官方文档                                                                             |
| ----------------------- | ----- | ------------------------------------------------------------------------------------ |
| Spring AI（总览）       | 2.0.0 | https://docs.spring.io/spring-ai/reference/                                          |
| Getting Started         | —     | https://docs.spring.io/spring-ai/reference/getting-started.html                      |
| ChatModel & Prompt      | —     | https://docs.spring.io/spring-ai/reference/api/chatmodel.html                        |
| OpenAI 兼容模型         | —     | 见 `ChatModel` 页面内 `_openai` 锚点                                                 |
| VectorStore（含 Redis） | —     | https://docs.spring.io/spring-ai/reference/api/index.html#_vector_store_api          |
| Redis VectorStore       | —     | https://docs.spring.io/spring-ai/reference/api/index.html#_redis                     |
| TokenTextSplitter       | —     | https://docs.spring.io/spring-ai/reference/api/index.html#_text_splitting            |
| **MCP Server**          | —     | https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html                 |
| MCP Getting Started     | —     | https://docs.spring.io/spring-ai/reference/guides/getting-started-mcp.html           |
| MCP Server Boot Starter | —     | https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html |
| MCP Security            | —     | https://docs.spring.io/spring-ai/reference/api/mcp/mcp-security.html                 |
| ChatMemory              | —     | https://docs.spring.io/spring-ai/reference/api/chat-memory.html                      |
| ChatClient              | —     | https://docs.spring.io/spring-ai/reference/api/chatclient.html                       |
| Upgrade Notes           | —     | https://docs.spring.io/spring-ai/reference/upgrade-notes.html                        |
| Spring AI BOM           | —     | https://spring.io/projects/spring-ai                                                 |
| Spring AI GitHub        | —     | https://github.com/spring-projects/spring-ai                                         |

> 注：Spring AI 2.0 将 ChatModel、VectorStore、TokenTextSplitter 等 API 文档合并到了 `api/index.html` 单页，通过锚点定位。MCP 相关仍为独立页面。

## ORM / 数据库

| 技术                      | 版本       | 官方文档                                                        |
| ------------------------- | ---------- | --------------------------------------------------------------- |
| MyBatis-Flex              | 见 pom.xml | https://mybatis-flex.com/                                       |
| MyBatis-Flex 核心 API     | —          | https://mybatis-flex.com/zh/intro/what-is-mybatisflex.html      |
| MyBatis-Flex QueryWrapper | —          | https://github.com/mybatis-flex/mybatis-flex                    |
| MyBatis-Flex JavaDoc API  | —          | https://apidoc.gitee.com/mybatis-flex/mybatis-flex/             |
| MySQL                     | 8.x        | https://dev.mysql.com/doc/                                      |
| HikariCP                  | —          | https://github.com/brettwooldridge/HikariCP                     |
| Redis / Jedis             | —          | https://github.com/redis/jedis                                  |
| Redis Search              | —          | https://redis.io/docs/latest/develop/interact/search-and-query/ |

## 文档解析 / 文本处理

| 技术                  | 版本  | 官方文档                                                           |
| --------------------- | ----- | ------------------------------------------------------------------ |
| Apache Tika           | 2.9.4 | https://tika.apache.org/                                           |
| Tika Getting Started  | —     | https://tika.apache.org/3.1.0/gettingstarted.html                  |
| Tika AutoDetectParser | —     | https://tika.apache.org/3.3.2/examples.html                        |
| Tika API Javadoc      | —     | https://javadoc.io/doc/org.apache.tika/tika-core/latest/index.html |

## 工具库

| 技术                   | 版本 | 官方文档                                               |
| ---------------------- | ---- | ------------------------------------------------------ |
| Hutool                 | —    | https://doc.hutool.cn/                                 |
| Lombok                 | —    | https://projectlombok.org/features/                    |
| Knife4j（OpenAPI 3）   | —    | https://doc.xiaominfo.com/                             |
| Reactor（WebFlux）     | —    | https://projectreactor.io/docs/core/release/reference/ |
| Micrometer Observation | —    | https://docs.micrometer.io/micrometer/reference/       |

## 前端（ai-java-front）

| 技术         | 版本 | 官方文档                                |
| ------------ | ---- | --------------------------------------- |
| Vue 3        | —    | https://cn.vuejs.org/                   |
| TypeScript   | —    | https://www.typescriptlang.org/zh/docs/ |
| Vite         | —    | https://cn.vitejs.dev/                  |
| Pinia        | —    | https://pinia.vuejs.org/zh/             |
| Vue Router   | —    | https://router.vuejs.org/zh/            |
| Element Plus | —    | https://element-plus.org/zh-CN/         |
| axios        | —    | https://axios-http.com/                 |

## MCP 协议

| 技术         | 官方文档                                         |
| ------------ | ------------------------------------------------ |
| MCP 官方规范 | https://modelcontextprotocol.io/                 |
| MCP Java SDK | https://github.com/modelcontextprotocol/java-sdk |

---

## 快速查询指引

| 遇到问题                                          | 查哪里                             |
| ------------------------------------------------- | ---------------------------------- |
| Spring AI 注解语法（`@McpTool`、`@McpToolParam`） | Spring AI MCP Server 文档          |
| ChatModel 流式 API                                | Spring AI ChatModel 文档           |
| VectorStore 过滤语法                              | Spring AI VectorStore / Redis 文档 |
| TokenTextSplitter 参数                            | Spring AI TokenTextSplitter 文档   |
| Tika 解析异常排查                                 | Tika Getting Started + Javadoc     |
| MyBatis-Flex QueryWrapper 用法                    | MyBatis-Flex 核心文档              |
| Spring AI 版本升级                                | Spring AI Upgrade Notes            |
| 配置项 key 名确认                                 | Spring AI 参考文档 + IDE 自动补全  |

---

## 版本锁定

| 依赖          | 版本         | 配置文件位置                    |
| ------------- | ------------ | ------------------------------- |
| Spring Boot   | 4.1.0        | `pom.xml`                       |
| Spring AI BOM | 2.0.0        | `pom.xml` (`spring-ai.version`) |
| Apache Tika   | 2.9.4        | `ai-agent/pom.xml`              |
| MyBatis-Flex  | 见 `pom.xml` | `pom.xml`                       |
| Java          | 21           | `pom.xml`                       |
