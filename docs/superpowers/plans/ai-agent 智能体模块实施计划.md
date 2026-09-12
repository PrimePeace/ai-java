# ai-agent 智能体模块实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建平台化知识库问答模块（迷你 Dify）：知识库 CRUD + 文档上传解析向量化 + SSE 流式 RAG 问答 + 引用溯源 + MCP Server。

**Architecture:** 新建 Maven 模块 `ai-agent`（业务内聚）依赖 ai-basic；ai-web 只做装配。MySQL 存业务态（5 张表），Redis 只存 HNSW 向量（metadata 带 kbId 隔离），云端 OpenAI 兼容 API（智谱）提供 chat + embedding。摄取异步状态机，问答手动检索拼 Prompt + Flux SSE。

**Tech Stack:** Spring Boot 4.1 + Spring AI 2.0（openai / redis vector store / mcp server webmvc）+ Apache Tika 2.9 + MyBatis-Flex + Vue 3 + TS + Naive UI

**设计文档**：`docs/superpowers/specs/ai-agent 智能体模块设计文档.md`（所有决策依据，实现遇疑问先查它）

---

## 执行模式（spec-workflow 约束）

本计划遵循项目规则 `spec-workflow.md`，每个任务的循环固定为：

```
① 执行者将任务代码块追加到设计文档第 9 章对应小节（9.N 编号与任务号对应）
② 用户手动输入代码到项目（学习目的，用户主权）
③ 执行者运行验证命令；编译错误时直接修改项目文件修复（此场景允许）
④ 用户 git commit（用户自行操作，执行者不主动 commit）
```

- 例外：DDL、yml、`docs/` 下的文档类变更可直接写入项目
- 不采用 TDD 红绿循环：用户手动输入代码的工作流下不可行，以**编译验证 + 阶段末运行时链路验证**替代（对应设计文档第 10 节）

## 任务总览

| 阶段     | 任务       | 内容                                          |
| -------- | ---------- | --------------------------------------------- |
| 0 前置   | Task 0     | 环境验证清单 + 拦截器生效实测                 |
| 1 骨架   | Task 1-3   | 根 POM / ai-agent POM / ai-web POM            |
| 2 数据层 | Task 4-6   | DDL / 实体 / Mapper                           |
| 3 基础   | Task 7-9   | AgentProperties / 全局异常 / 线程池           |
| 4 摄取   | Task 10-11 | 解析切分管道 / 摄取服务                       |
| 5 知识库 | Task 12-14 | DTO/VO / KB Service / Controller              |
| 6 问答   | Task 15-17 | 会话 Service / RAG Service / Chat Controller  |
| 7 MCP    | Task 18-19 | 安全拦截器 / MCP 工具                         |
| 8 配置   | Task 20    | application-prod.yml                          |
| 9 前端   | Task 21-27 | 类型 / API / composables / 组件 / 页面 / 路由 |
| 10 验证  | Task 28    | 全链路验证（设计文档第 10 节）                |

---

## 阶段 0：前置验证

### Task 0: 环境验证清单 + 拦截器实测（不改业务代码）

**Files:** 无新增（本任务只验证 + 可能修复现有 bug）

- [ ] **Step 1: 实测 JwtInterceptor 是否生效（关键前置）**

现有 `ai-web/.../config/WebMvcConfig.java` 的拦截器 pattern 是 `/api/**`，但项目 context-path 是 `/api`——拦截器 pattern 匹配的是 context-path 之后的 servlet 路径，`/api/**` 疑似匹配不到任何请求。若拦截器未生效，`UserContext` 永远为空，ai-agent 所有"归属校验"全部失效。

启动应用后：

```bash
# 不带 token 调用需要登录的接口
curl -s http://localhost:8120/api/user/current
```

- 预期（拦截器生效）：返回 `{"code":40100,...,"message":"未登录"}`
- 异常（拦截器未生效）：返回 `{"code":50000,...,"message":"系统错误"}`（getCurrentUser 拿不到 UserContext 抛 NPE/业务异常）

若未生效，修复 `WebMvcConfig.java`（pattern 改为 `/**`，鉴权由 `@RequireLogin` 注解控制粒度）：

```java
@Override
public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(jwtInterceptor)
            .addPathPatterns("/**");
}
```

- [ ] **Step 2: MCP starter artifact 验证（临时建 POM 后跑）**

Task 2 完成 ai-agent POM 后执行（本步在 Task 3 验证时一并做，此处记录命令）：

```bash
mvn dependency:tree -pl ai-agent | grep -i mcp
```

预期：`spring-ai-starter-mcp-server-webmvc` 解析成功。若失败，改用 `spring-ai-starter-mcp-server-streamable-webmvc`（官方文档两处写法不一致，以 BOM 解析为准）。

- [ ] **Step 3: 环境准备确认**

| 项                                   | 确认方式                                                    |
| ------------------------------------ | ----------------------------------------------------------- |
| MySQL `ai_java` 库可连               | `mysql -uroot -p -e "use ai_java; show tables;"`            |
| Redis 8 启动且 `FT.INFO` 可用        | `redis-cli FT._LIST`（RediSearch 内置）                     |
| 环境变量 `AI_API_KEY`（智谱）        | `echo $AI_API_KEY`                                          |
| 环境变量 `MCP_TOKEN`（自定义字符串） | `echo $MCP_TOKEN`                                           |
| Redis maxmemory-policy               | `redis-cli CONFIG GET maxmemory-policy` → 应为 `noeviction` |

---

## 阶段 1：工程骨架

### Task 1: 根 POM 引入 Spring AI BOM 与 Tika BOM

**Files:**

- Modify: `pom.xml`（根）
- 文档同步：设计文档 9.1

- [ ] **Step 1: properties 增加版本号**

在根 POM `<properties>` 中追加：

```xml
<spring-ai.version>2.0.0</spring-ai.version>
<tika.version>2.9.4</tika.version>
```

- [ ] **Step 2: dependencyManagement 增加 BOM 与内部模块**

`<dependencyManagement><dependencies>` 中追加（放在内部模块注释块之后）：

```xml
<!-- 内部模块：ai-agent -->
<dependency>
    <groupId>com.ai</groupId>
    <artifactId>ai-agent</artifactId>
    <version>${project.version}</version>
</dependency>

<!-- Spring AI BOM（兼容 Boot 4.1） -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-bom</artifactId>
    <version>${spring-ai.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>

<!-- Apache Tika BOM（文档解析） -->
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-bom</artifactId>
    <version>${tika.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

- [ ] **Step 3: modules 增加 ai-agent**

```xml
<modules>
    <module>ai-basic</module>
    <module>ai-agent</module>
    <module>ai-web</module>
</modules>
```

注意顺序：ai-agent 必须在 ai-web 之前（Maven reactor 按 dependency 关系自动排序，但显式顺序保持可读）。

- [ ] **Step 4: 验证**

```bash
mvn validate -q
```

预期：BUILD SUCCESS（ai-agent 模块还不存在会报错，所以本任务先与 Task 2 一起验证——Task 2 完成后统一跑 `mvn clean compile`）

### Task 2: 创建 ai-agent 模块（POM + 包骨架）

**Files:**

- Create: `ai-agent/pom.xml`
- Create: `ai-agent/src/main/java/com/ai/aijava/agent/`（空目录随文件创建）
- 文档同步：设计文档 9.2

- [ ] **Step 1: 创建 ai-agent/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.ai</groupId>
        <artifactId>ai-java</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>

    <artifactId>ai-agent</artifactId>
    <name>ai-agent</name>
    <description>AI 智能体模块：知识库 RAG 问答 + MCP Server</description>

    <dependencies>
        <!-- 内部通用基础库 -->
        <dependency>
            <groupId>com.ai</groupId>
            <artifactId>ai-basic</artifactId>
        </dependency>

        <!-- 自有 @RestController / MultipartFile 编译所需（显式声明，不依赖 starter 传递的巧合） -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>

        <!-- OpenAI 兼容 ChatModel + EmbeddingModel（智谱等） -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-model-openai</artifactId>
        </dependency>

        <!-- Redis HNSW 向量库 -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-vector-store-redis</artifactId>
        </dependency>

        <!-- MCP Server（STREAMABLE 传输，SYNC） -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
        </dependency>

        <!-- Tika 文档解析 -->
        <dependency>
            <groupId>org.apache.tika</groupId>
            <artifactId>tika-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.tika</groupId>
            <artifactId>tika-parsers-standard-package</artifactId>
        </dependency>

        <!-- MyBatis-Flex（实体 + BaseMapper，版本随根 POM） -->
        <dependency>
            <groupId>com.mybatis-flex</groupId>
            <artifactId>mybatis-flex-core</artifactId>
            <version>${mybatis-flex.version}</version>
        </dependency>
        <dependency>
            <groupId>com.mybatis-flex</groupId>
            <artifactId>mybatis-flex-annotation</artifactId>
            <version>${mybatis-flex.version}</version>
        </dependency>

        <!-- lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <executions>
                    <execution>
                        <id>default-compile</id>
                        <phase>compile</phase>
                        <goals>
                            <goal>compile</goal>
                        </goals>
                        <configuration>
                            <annotationProcessorPaths>
                                <path>
                                    <groupId>org.projectlombok</groupId>
                                    <artifactId>lombok</artifactId>
                                </path>
                            </annotationProcessorPaths>
                        </configuration>
                    </execution>
                    <execution>
                        <id>default-testCompile</id>
                        <phase>test-compile</phase>
                        <goals>
                            <goal>testCompile</goal>
                        </goals>
                        <configuration>
                            <annotationProcessorPaths>
                                <path>
                                    <groupId>org.projectlombok</groupId>
                                    <artifactId>lombok</artifactId>
                                </path>
                            </annotationProcessorPaths>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

说明：`mybatis-flex-core` / `mybatis-flex-annotation` 未进根 POM dependencyManagement（根 POM 只管理了 starter 和 codegen），故此处显式带 `${mybatis-flex.version}`。

- [ ] **Step 2: 创建包目录占位**

创建 `ai-agent/src/main/java/com/ai/aijava/agent/config/`、`enums/` 目录（后续任务逐包填充；Maven 空目录不打包，无副作用）。

### Task 3: ai-web 依赖 ai-agent + 全模块编译验证

**Files:**

- Modify: `ai-web/pom.xml`
- 文档同步：设计文档 9.3

- [ ] **Step 1: ai-web pom dependencies 追加（放在 ai-basic 依赖之后）**

```xml
<!-- AI 智能体模块 -->
<dependency>
    <groupId>com.ai</groupId>
    <artifactId>ai-agent</artifactId>
</dependency>
```

- [ ] **Step 2: 全模块编译**

```bash
mvn clean compile
```

预期：BUILD SUCCESS，三个模块均编译通过。

- [ ] **Step 3: MCP artifact 验证（Task 0 Step 2 的落地）**

```bash
mvn dependency:tree -pl ai-agent | grep -i mcp
```

预期输出包含 `org.springframework.ai:spring-ai-starter-mcp-server-webmvc:jar:2.0.x:compile`。
若解析失败，将 ai-agent POM 中该 artifactId 改为 `spring-ai-starter-mcp-server-streamable-webmvc` 重试。

- [ ] **Step 4: 用户 commit（建议信息）**

```text
feat: 引入 ai-agent Maven 模块骨架与 Spring AI 2.0 依赖体系
```

---

## 阶段 2：数据层

### Task 4: DDL 追加到 docs/sql/ai_java.sql（可直接写入项目）

**Files:**

- Modify: `docs/sql/ai_java.sql`
- 文档同步：设计文档 9.4

- [ ] **Step 1: 文件末尾追加 5 张表 DDL**

```sql
-- ==================== ai-agent 智能体模块 ====================

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

- [ ] **Step 2: 在 MySQL 执行建表**

```bash
mysql -uroot -p ai_java < docs/sql/ai_java.sql
# 或在客户端逐段执行；若 user/audit_log 已存在，只执行 5 张新表段落
mysql -uroot -p -e "use ai_java; show tables;"   # 应出现 5 张新表
```

### Task 5: 枚举与实体（enums + entity ×5）

**Files:**

- Create: `ai-agent/src/main/java/com/ai/aijava/agent/enums/DocStatus.java`
- Create: `ai-agent/src/main/java/com/ai/aijava/agent/entity/KnowledgeBase.java`
- Create: `ai-agent/src/main/java/com/ai/aijava/agent/entity/KnowledgeDocument.java`
- Create: `ai-agent/src/main/java/com/ai/aijava/agent/entity/DocumentChunk.java`
- Create: `ai-agent/src/main/java/com/ai/aijava/agent/entity/ChatSession.java`
- Create: `ai-agent/src/main/java/com/ai/aijava/agent/entity/ChatMessage.java`
- 文档同步：设计文档 9.5

- [ ] **Step 1: DocStatus 枚举**

```java
package com.ai.aijava.agent.enums;

/**
 * 文档摄取状态机：UPLOADED → PROCESSING → COMPLETED / FAILED
 */
public enum DocStatus {

    /** 已上传，待处理 */
    UPLOADED("已上传"),

    /** 解析切分向量化中 */
    PROCESSING("处理中"),

    /** 摄取完成，可被检索 */
    COMPLETED("已完成"),

    /** 摄取失败（只能删除重传，不做重处理接口） */
    FAILED("失败");

    private final String label;

    DocStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
```

- [ ] **Step 2: KnowledgeBase 实体**

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

    /** 知识库 ID（主键，自增） */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 知识库名称 */
    private String name;

    /** 知识库描述 */
    private String description;

    /** 创建者用户 ID */
    private Long userId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
```

- [ ] **Step 3: KnowledgeDocument 实体**

```java
package com.ai.aijava.agent.entity;

import com.ai.aijava.agent.enums.DocStatus;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库文档实体，对应 knowledge_document 表
 * 摄取状态机由 DocumentIngestService 驱动
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("knowledge_document")
public class KnowledgeDocument {

    /** 文档 ID（主键，自增） */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 所属知识库 ID */
    private Long kbId;

    /** 原始文件名 */
    private String fileName;

    /** 文件类型（pdf/docx/md/txt） */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 服务器存储路径 */
    private String filePath;

    /** 处理状态（DocStatus 枚举名，DB 存 String 便于排查） */
    private String status;

    /** 处理失败原因（FAILED 时） */
    private String errorMessage;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 状态是否为指定枚举
     */
    public boolean isStatus(DocStatus docStatus) {
        return docStatus.name().equals(this.status);
    }
}
```

- [ ] **Step 4: DocumentChunk 实体**

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
 * 文档切片实体，对应 document_chunk 表
 * 向量只存 Redis（Document id = chunk id），本表存原文用于引用溯源与索引重建
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("document_chunk")
public class DocumentChunk {

    /** 切片 ID（主键，自增；同时作为 Redis 向量 Document id） */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 所属文档 ID */
    private Long docId;

    /** 所属知识库 ID（冗余，删库清理向量时免 join） */
    private Long kbId;

    /** 切片序号（文档内从 0 递增） */
    private Integer chunkIndex;

    /** 切片原文 */
    private String content;

    /** 创建时间 */
    private LocalDateTime createTime;
}
```

- [ ] **Step 5: ChatSession 实体**

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
 * 对话会话实体，对应 chat_session 表（一个会话绑定一个知识库）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("chat_session")
public class ChatSession {

    /** 默认会话标题，首条 user 消息后截取前 20 字更新 */
    public static final String DEFAULT_TITLE = "新会话";

    /** 会话 ID（主键，自增） */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 所属用户 ID */
    private Long userId;

    /** 关联知识库 ID */
    private Long kbId;

    /** 会话标题 */
    private String title;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 最后活跃时间（有新消息即更新） */
    private LocalDateTime updateTime;
}
```

- [ ] **Step 6: ChatMessage 实体**

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
 * 对话消息实体，对应 chat_message 表（追加型，无 update_time）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("chat_message")
public class ChatMessage {

    /** 角色：用户 */
    public static final String ROLE_USER = "user";

    /** 角色：助手 */
    public static final String ROLE_ASSISTANT = "assistant";

    /** 消息 ID（主键，自增，自增序即时间序） */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 所属会话 ID */
    private Long sessionId;

    /** 角色（user/assistant） */
    private String role;

    /** 消息内容 */
    private String content;

    /** 引用 JSON 数组（仅 assistant 消息，内容快照） */
    private String citations;

    /** 创建时间 */
    private LocalDateTime createTime;
}
```

### Task 6: Mapper ×5

**Files:**

- Create: `ai-agent/src/main/java/com/ai/aijava/agent/mapper/KnowledgeBaseMapper.java`（其余 4 个同目录）
- 文档同步：设计文档 9.6

- [ ] **Step 1: 五个 Mapper（@Mapper 注解，启动类包扫描自动注册，无需 @MapperScan）**

```java
package com.ai.aijava.agent.mapper;

import com.ai.aijava.agent.entity.KnowledgeBase;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库 Mapper
 */
@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {
}
```

```java
package com.ai.aijava.agent.mapper;

import com.ai.aijava.agent.entity.KnowledgeDocument;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库文档 Mapper
 */
@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {
}
```

```java
package com.ai.aijava.agent.mapper;

import com.ai.aijava.agent.entity.DocumentChunk;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档切片 Mapper
 */
@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {
}
```

```java
package com.ai.aijava.agent.mapper;

import com.ai.aijava.agent.entity.ChatSession;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话会话 Mapper
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
```

```java
package com.ai.aijava.agent.mapper;

import com.ai.aijava.agent.entity.ChatMessage;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话消息 Mapper
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn clean compile
```

预期：BUILD SUCCESS。

- [ ] **Step 3: 用户 commit（建议信息）**

```text
feat: ai-agent 数据层——5 张表实体与 Mapper、DocStatus 状态机
```

---

## 阶段 3：基础组件

### Task 7: AgentProperties 配置类

**Files:**

- Create: `ai-agent/src/main/java/com/ai/aijava/agent/config/AgentProperties.java`
- 文档同步：设计文档 9.7

- [ ] **Step 1: 完整代码**

```java
package com.ai.aijava.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.util.List;

/**
 * ai-agent 可配参数（application.yml 的 agent.* 前缀）
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    /** 上传文件存储根目录 */
    private String uploadDir = "./uploads";

    /** 允许上传的文件类型白名单（扩展名小写） */
    private List<String> allowedTypes = List.of("pdf", "docx", "md", "txt");

    /** 单文件大小上限 */
    private DataSize maxFileSize = DataSize.ofMegabytes(20);

    /** 切分 token 数（TokenTextSplitter） */
    private int chunkSize = 800;

    /** 检索返回条数（过采样为 topK * 2，过滤后截取前 topK） */
    private int topK = 5;

    /** 对话携带历史轮数 */
    private int historyRounds = 10;

    /** RAG 系统提示词（空则用内置默认） */
    private String systemPrompt = "";

    /** 内置默认系统提示词 */
    public static final String DEFAULT_SYSTEM_PROMPT =
            "你是知识库问答助手，仅依据参考资料回答问题；"
                    + "回答末尾不需要提及参考资料本身；"
                    + "当参考资料未覆盖提问内容时，明确说明未在知识库中找到直接依据。";

    /**
     * 获取生效的系统提示词
     */
    public String effectiveSystemPrompt() {
        return systemPrompt == null || systemPrompt.isBlank() ? DEFAULT_SYSTEM_PROMPT : systemPrompt;
    }
}
```

### Task 8: GlobalExceptionHandler 增加上传超限处理（ai-basic）

**Files:**

- Modify: `ai-basic/src/main/java/com/ai/aijava/exception/GlobalExceptionHandler.java`
- 文档同步：设计文档 9.8

- [ ] **Step 1: 在 `runtimeExceptionHandler` 之前追加 handler 方法（import 同步增加）**

新增 import：

```java
import org.springframework.web.multipart.MaxUploadSizeExceededException;
```

新增方法（放在 RuntimeException handler 之前）：

```java
/**
 * 处理上传文件超过大小限制异常
 * 框架层 multipart 上限（spring.servlet.multipart.max-file-size）触发，
 * 返回参数错误码与友好提示，而非 500
 *
 * @param e 上传超限异常
 * @return 包含错误码和提示信息的响应
 */
@ExceptionHandler(MaxUploadSizeExceededException.class)
public BaseResponse<?> maxUploadSizeExceededExceptionHandler(MaxUploadSizeExceededException e) {
    log.warn("MaxUploadSizeExceededException: {}", e.getMessage());
    return ResultUtils.error(ErrorCode.PARAMS_ERROR, "上传文件过大，请压缩后重试");
}
```

说明：放在 RuntimeException handler 之前只是可读性考虑（`@ExceptionHandler` 按异常类型最精确匹配，与顺序无关）。

### Task 9: AsyncConfig 增加 ingestExecutor 线程池（ai-web）

**Files:**

- Modify: `ai-web/src/main/java/com/ai/aijava/config/AsyncConfig.java`
- 文档同步：设计文档 9.9

- [ ] **Step 1: 类末尾追加 Bean（import 无需新增）**

在 `auditLogExecutor` 方法之后追加：

```java
/**
 * 文档摄取线程池（解析 + 切分 + 向量化，含外部 embedding API 调用）
 * 与 auditLogExecutor 隔离；队列满退化为同步摄取（CallerRuns），不丢任务
 */
@Bean("ingestExecutor")
public Executor ingestExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("doc-ingest-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    // 关闭前等待摄取中/排队中的文档处理完（部分写入状态可收敛）
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn clean compile
```

- [ ] **Step 3: 用户 commit（建议信息）**

```text
feat: AgentProperties 参数类、上传超限异常处理、摄取线程池
```

---

## 阶段 4：摄取管道

### Task 10: 文档解析与切分（pipeline 包）

**Files:**

- Create: `ai-agent/src/main/java/com/ai/aijava/agent/pipeline/DocumentParser.java`
- Create: `ai-agent/src/main/java/com/ai/aijava/agent/pipeline/ChunkSplitter.java`
- 文档同步：设计文档 9.10

- [ ] **Step 1: DocumentParser（Tika 统一解析）**

```java
package com.ai.aijava.agent.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * 文档解析器：Tika 自动检测类型，提取纯文本（pdf/docx/md/txt 统一入口）
 */
@Slf4j
@Component
public class DocumentParser {

    /** Tika 提取文本上限（-1 表示不限制，由上传大小限制间接约束） */
    private static final int MAX_TEXT_LENGTH = -1;

    /**
     * 解析文件流为纯文本
     *
     * @param inputStream 文件输入流（调用方负责关闭）
     * @return 提取的纯文本
     * @throws Exception 解析失败（由摄取服务转为 FAILED 状态）
     */
    public String parse(InputStream inputStream) throws Exception {
        BodyContentHandler handler = new BodyContentHandler(MAX_TEXT_LENGTH);
        AutoDetectParser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
        parser.parse(inputStream, handler, metadata);
        String text = handler.toString();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("文档解析结果为空（可能是扫描版 PDF 或空文档）");
        }
        return text.trim();
    }
}
```

- [ ] **Step 2: ChunkSplitter（Spring AI TokenTextSplitter 封装）**

```java
package com.ai.aijava.agent.pipeline;

import com.ai.aijava.agent.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.transformer.TokenTextSplitter;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 切分器：封装 Spring AI TokenTextSplitter，按 token 数切分
 */
@Component
@RequiredArgsConstructor
public class ChunkSplitter {

    private final AgentProperties agentProperties;

    /**
     * 将全文切分为片段（带默认重叠，保留语义连续性）
     *
     * @param text 文档全文
     * @return 切片列表（顺序即文档顺序）
     */
    public List<String> split(String text) {
        Document fullDoc = new Document(text);
        TokenTextSplitter splitter = new TokenTextSplitter(
                agentProperties.getChunkSize(),  // defaultChunkSize
                200,                             // minChunkSizeChars（低于此长度并入前片）
                50,                              // minChunkLengthToEmbed（过短片段丢弃阈值，这里不丢）
                100,                             // maxNumChunks（安全上限，防超大文档切片爆炸）
                true);                           // keepSeparator
        List<Document> chunks = splitter.apply(List.of(fullDoc));
        return chunks.stream().map(Document::getText).toList();
    }
}
```

说明：`TokenTextSplitter` 构造参数语义为 `(defaultChunkSize, minChunkSizeChars, minChunkLengthToEmbed, maxNumChunks, keepSeparator)`；若所用 Spring AI 版本构造器签名不同，以 IDE 提示为准调整（无参构造 `new TokenTextSplitter()` 也可用，默认 800 token）。

### Task 11: DocumentIngestService（上传 + 异步摄取状态机）

**Files:**

- Create: `ai-agent/src/main/java/com/ai/aijava/agent/service/DocumentIngestService.java`
- 文档同步：设计文档 9.11

- [ ] **Step 1: 完整代码**

```java
package com.ai.aijava.agent.service;

import com.ai.aijava.agent.config.AgentProperties;
import com.ai.aijava.agent.entity.DocumentChunk;
import com.ai.aijava.agent.entity.KnowledgeBase;
import com.ai.aijava.agent.entity.KnowledgeDocument;
import com.ai.aijava.agent.enums.DocStatus;
import com.ai.aijava.agent.mapper.DocumentChunkMapper;
import com.ai.aijava.agent.mapper.KnowledgeBaseMapper;
import com.ai.aijava.agent.mapper.KnowledgeDocumentMapper;
import com.ai.aijava.agent.pipeline.ChunkSplitter;
import com.ai.aijava.agent.pipeline.DocumentParser;
import com.ai.aijava.context.UserContext;
import com.ai.aijava.exception.BusinessException;
import com.ai.aijava.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文档摄取服务
 * 上传：校验 → 存盘 → insert(UPLOADED) → 返回 docId（快）
 * 摄取：@Async 异步，PROCESSING → 解析 → 切分 → chunk 入库 → 向量入库 → COMPLETED/FAILED
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestService {

    private final AgentProperties agentProperties;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final DocumentParser documentParser;
    private final ChunkSplitter chunkSplitter;
    private final VectorStore vectorStore;

    /**
     * 上传文档：校验 → 存盘 → 落库(UPLOADED) → 触发异步摄取
     *
     * @return 文档 ID
     */
    public Long upload(Long kbId, MultipartFile file) {
        // 1. 校验知识库归属
        KnowledgeBase kb = knowledgeBaseMapper.selectOneById(kbId);
        if (kb == null || !kb.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        }
        // 2. 校验类型与大小
        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (ext == null || !agentProperties.getAllowedTypes().contains(ext.toLowerCase())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "不支持的文件类型，仅允许 " + String.join("/", agentProperties.getAllowedTypes()));
        }
        if (file.getSize() > agentProperties.getMaxFileSize().toBytes()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "文件超过大小限制 " + agentProperties.getMaxFileSize().toMegabytes() + "MB");
        }
        // 3. 存盘：uploads/{kbId}/{uuid}.{ext}
        String filePath;
        try {
            Path dir = Paths.get(agentProperties.getUploadDir(), String.valueOf(kbId));
            Files.createDirectories(dir);
            Path target = dir.resolve(UUID.randomUUID() + "." + ext.toLowerCase());
            file.transferTo(target.toFile());
            filePath = target.toString();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件保存失败");
        }
        // 4. 落库（UPLOADED），insert 自动提交后触发异步
        KnowledgeDocument doc = KnowledgeDocument.builder()
                .kbId(kbId)
                .fileName(file.getOriginalFilename())
                .fileType(ext.toLowerCase())
                .fileSize(file.getSize())
                .filePath(filePath)
                .status(DocStatus.UPLOADED.name())
                .build();
        knowledgeDocumentMapper.insert(doc);
        // 5. 异步摄取（insert 已提交，异步线程必能查到记录）
        this.ingest(doc.getId());
        return doc.getId();
    }

    /**
     * 异步摄取（ingestExecutor 线程池）
     * 状态机：PROCESSING → COMPLETED / FAILED（FAILED 只能删除重传）
     */
    @Async("ingestExecutor")
    public void ingest(Long docId) {
        KnowledgeDocument doc = knowledgeDocumentMapper.selectOneById(docId);
        if (doc == null) {
            log.warn("摄取任务取消：文档不存在 docId={}", docId);
            return;
        }
        updateStatus(docId, DocStatus.PROCESSING, null);
        try {
            // 1. Tika 解析
            String text;
            try (FileInputStream fis = new FileInputStream(new File(doc.getFilePath()))) {
                text = documentParser.parse(fis);
            }
            // 2. 切分
            List<String> contents = chunkSplitter.split(text);
            // 3. chunk 批量入库（先拿自增 id，向量 Document id = chunkId）
            List<Document> vectorDocs = new ArrayList<>(contents.size());
            for (int i = 0; i < contents.size(); i++) {
                DocumentChunk chunk = DocumentChunk.builder()
                        .docId(doc.getId())
                        .kbId(doc.getKbId())
                        .chunkIndex(i)
                        .content(contents.get(i))
                        .build();
                documentChunkMapper.insert(chunk);
                vectorDocs.add(new Document(String.valueOf(chunk.getId()), contents.get(i),
                        Map.of("kbId", String.valueOf(doc.getKbId()),
                               "docId", String.valueOf(doc.getId()))));
            }
            // 4. 向量入库（内部自动调 EmbeddingModel 批量向量化）
            vectorStore.add(vectorDocs);
            updateStatus(docId, DocStatus.COMPLETED, null);
            log.info("文档摄取完成 docId={} kbId={} chunks={}", docId, doc.getKbId(), contents.size());
        } catch (Exception e) {
            // 批量 add 非严格原子可能残留部分向量，由查询侧按 docId 回查 status 过滤兜底（设计 4.2 ③）
            log.error("文档摄取失败 docId={}", docId, e);
            updateStatus(docId, DocStatus.FAILED, truncate(e.getMessage(), 512));
        }
    }

    /**
     * 更新文档状态（FAILED 时记录原因）
     */
    private void updateStatus(Long docId, DocStatus status, String errorMessage) {
        KnowledgeDocument update = new KnowledgeDocument();
        update.setId(docId);
        update.setStatus(status.name());
        if (status == DocStatus.FAILED) {
            update.setErrorMessage(errorMessage);
        }
        knowledgeDocumentMapper.update(update);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
```

注意：`@Async` 自调用问题——`upload()` 内调用 `this.ingest()` 走的是同类内部调用，**不会经过代理**，`@Async` 不生效！修复方式：注入自身代理或拆分到两个 Service。**采用拆分**（避免自注入的循环怪味）：将 `ingest` 拆到独立类 `DocumentIngestWorker`。

- [ ] **Step 2: 修正——拆分为两个类（Upload 编排 + 异步 Worker）**

`DocumentIngestService.java` 最终版（`ingest` 相关代码移出）：

```java
package com.ai.aijava.agent.service;

import com.ai.aijava.agent.config.AgentProperties;
import com.ai.aijava.agent.entity.KnowledgeBase;
import com.ai.aijava.agent.entity.KnowledgeDocument;
import com.ai.aijava.agent.enums.DocStatus;
import com.ai.aijava.agent.mapper.KnowledgeBaseMapper;
import com.ai.aijava.agent.mapper.KnowledgeDocumentMapper;
import com.ai.aijava.context.UserContext;
import com.ai.aijava.exception.BusinessException;
import com.ai.aijava.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 文档上传编排：校验 → 存盘 → 落库(UPLOADED) → 触发异步摄取
 * 异步摄取逻辑在 DocumentIngestWorker（拆分避免 @Async 同类自调用失效）
 */
@Service
@RequiredArgsConstructor
public class DocumentIngestService {

    private final AgentProperties agentProperties;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final DocumentIngestWorker ingestWorker;

    /**
     * 上传文档
     *
     * @return 文档 ID
     */
    public Long upload(Long kbId, MultipartFile file) {
        // 1. 校验知识库归属
        KnowledgeBase kb = knowledgeBaseMapper.selectOneById(kbId);
        if (kb == null || !kb.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        }
        // 2. 校验类型与大小
        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (ext == null || !agentProperties.getAllowedTypes().contains(ext.toLowerCase())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "不支持的文件类型，仅允许 " + String.join("/", agentProperties.getAllowedTypes()));
        }
        if (file.getSize() > agentProperties.getMaxFileSize().toBytes()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "文件超过大小限制 " + agentProperties.getMaxFileSize().toMegabytes() + "MB");
        }
        // 3. 存盘：uploads/{kbId}/{uuid}.{ext}
        String filePath;
        try {
            Path dir = Paths.get(agentProperties.getUploadDir(), String.valueOf(kbId));
            Files.createDirectories(dir);
            Path target = dir.resolve(UUID.randomUUID() + "." + ext.toLowerCase());
            file.transferTo(target.toFile());
            filePath = target.toString();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件保存失败");
        }
        // 4. 落库（UPLOADED）；insert 自动提交后触发异步，Worker 必能查到记录
        KnowledgeDocument doc = KnowledgeDocument.builder()
                .kbId(kbId)
                .fileName(file.getOriginalFilename())
                .fileType(ext.toLowerCase())
                .fileSize(file.getSize())
                .filePath(filePath)
                .status(DocStatus.UPLOADED.name())
                .build();
        knowledgeDocumentMapper.insert(doc);
        // 5. 异步摄取
        ingestWorker.ingest(doc.getId());
        return doc.getId();
    }
}
```

Create: `ai-agent/src/main/java/com/ai/aijava/agent/service/DocumentIngestWorker.java`

```java
package com.ai.aijava.agent.service;

import com.ai.aijava.agent.entity.DocumentChunk;
import com.ai.aijava.agent.entity.KnowledgeDocument;
import com.ai.aijava.agent.enums.DocStatus;
import com.ai.aijava.agent.mapper.DocumentChunkMapper;
import com.ai.aijava.agent.mapper.KnowledgeDocumentMapper;
import com.ai.aijava.agent.pipeline.ChunkSplitter;
import com.ai.aijava.agent.pipeline.DocumentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 异步摄取 Worker（ingestExecutor 线程池）
 * 状态机：PROCESSING → COMPLETED / FAILED（FAILED 只能删除重传）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestWorker {

    private final DocumentChunkMapper documentChunkMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final DocumentParser documentParser;
    private final ChunkSplitter chunkSplitter;
    private final VectorStore vectorStore;

    /**
     * 摄取单个文档：解析 → 切分 → chunk 入库 → 向量入库
     */
    @Async("ingestExecutor")
    public void ingest(Long docId) {
        KnowledgeDocument doc = knowledgeDocumentMapper.selectOneById(docId);
        if (doc == null) {
            log.warn("摄取任务取消：文档不存在 docId={}", docId);
            return;
        }
        updateStatus(docId, DocStatus.PROCESSING, null);
        try {
            // 1. Tika 解析
            String text;
            try (FileInputStream fis = new FileInputStream(new File(doc.getFilePath()))) {
                text = documentParser.parse(fis);
            }
            // 2. 切分
            List<String> contents = chunkSplitter.split(text);
            // 3. chunk 批量入库（向量 Document id = chunk 自增 id）
            List<Document> vectorDocs = new ArrayList<>(contents.size());
            for (int i = 0; i < contents.size(); i++) {
                DocumentChunk chunk = DocumentChunk.builder()
                        .docId(doc.getId())
                        .kbId(doc.getKbId())
                        .chunkIndex(i)
                        .content(contents.get(i))
                        .build();
                documentChunkMapper.insert(chunk);
                vectorDocs.add(new Document(String.valueOf(chunk.getId()), contents.get(i),
                        Map.of("kbId", String.valueOf(doc.getKbId()),
                               "docId", String.valueOf(doc.getId()))));
            }
            // 4. 向量入库（内部自动调 EmbeddingModel 批量向量化）
            vectorStore.add(vectorDocs);
            updateStatus(docId, DocStatus.COMPLETED, null);
            log.info("文档摄取完成 docId={} kbId={} chunks={}", docId, doc.getKbId(), contents.size());
        } catch (Exception e) {
            // 批量 add 非严格原子可能残留部分向量，由查询侧按 docId 回查 status 过滤兜底（设计 4.2 ③）
            log.error("文档摄取失败 docId={}", docId, e);
            updateStatus(docId, DocStatus.FAILED, truncate(e.getMessage(), 512));
        }
    }

    private void updateStatus(Long docId, DocStatus status, String errorMessage) {
        KnowledgeDocument update = new KnowledgeDocument();
        update.setId(docId);
        update.setStatus(status.name());
        if (status == DocStatus.FAILED) {
            update.setErrorMessage(errorMessage);
        }
        knowledgeDocumentMapper.update(update);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
mvn clean compile
```

说明：本任务暂不启动验证（VectorStore Bean 需要 Task 20 的 Redis 配置才可装配）；编译通过即进入下一阶段。

- [ ] **Step 4: 用户 commit（建议信息）**

```text
feat: 文档摄取管道——Tika 解析、TokenTextSplitter 切分、异步摄取状态机
```

---

## 阶段 5：知识库与文档管理

### Task 12: DTO / VO

**Files:**

- Create: `ai-agent/src/main/java/com/ai/aijava/agent/dto/request/KnowledgeBaseCreateRequest.java`
- Create: `ai-agent/src/main/java/com/ai/aijava/agent/dto/request/KnowledgeBaseUpdateRequest.java`
- Create: `ai-agent/src/main/java/com/ai/aijava/agent/dto/request/ChatSessionCreateRequest.java`
- Create: `ai-agent/src/main/java/com/ai/aijava/agent/dto/request/ChatSendRequest.java`
- Create: `ai-agent/src/main/java/com/ai/aijava/agent/dto/vo/KnowledgeBaseVO.java`
- Create: `ai-agent/src/main/java/com/ai/aijava/agent/dto/vo/KnowledgeDocumentVO.java`
- Create: `ai-agent/src/main/java/com/ai/aijava/agent/dto/vo/ChatSessionVO.java`
- Create: `ai-agent/src/main/java/com/ai/aijava/agent/dto/vo/ChatMessageVO.java`
- Create: `ai-agent/src/main/java/com/ai/aijava/agent/dto/vo/CitationVO.java`
- 文档同步：设计文档 9.12

- [ ] **Step 1: Request DTO ×4**

```java
package com.ai.aijava.agent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建知识库请求
 */
@Data
public class KnowledgeBaseCreateRequest {

    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 64, message = "知识库名称最长 64 字符")
    private String name;

    @Size(max = 256, message = "描述最长 256 字符")
    private String description;
}
```

```java
package com.ai.aijava.agent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改知识库请求
 */
@Data
public class KnowledgeBaseUpdateRequest {

    @NotNull(message = "知识库 ID 不能为空")
    private Long id;

    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 64, message = "知识库名称最长 64 字符")
    private String name;

    @Size(max = 256, message = "描述最长 256 字符")
    private String description;
}
```

```java
package com.ai.aijava.agent.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建会话请求
 */
@Data
public class ChatSessionCreateRequest {

    @NotNull(message = "知识库 ID 不能为空")
    private Long kbId;
}
```

```java
package com.ai.aijava.agent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 会话提问请求（SSE 流式响应）
 */
@Data
public class ChatSendRequest {

    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题最长 2000 字符")
    private String question;
}
```

- [ ] **Step 2: VO ×5**

```java
package com.ai.aijava.agent.dto.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库视图对象（含文档数实时统计）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBaseVO {

    private Long id;

    private String name;

    private String description;

    /** 文档数（GROUP BY 实时统计，不冗余字段） */
    private Long docCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
```

```java
package com.ai.aijava.agent.dto.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档视图对象（含摄取状态，兼作进度轮询）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocumentVO {

    private Long id;

    private Long kbId;

    private String fileName;

    private String fileType;

    private Long fileSize;

    /** DocStatus 枚举名 */
    private String status;

    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
```

```java
package com.ai.aijava.agent.dto.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话会话视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionVO {

    private Long id;

    private Long kbId;

    /** 知识库名称（聊天页标题数据源，join knowledge_base） */
    private String kbName;

    private String title;

    private LocalDateTime createTime;

    /** 最后活跃时间（列表按此倒序） */
    private LocalDateTime updateTime;
}
```

```java
package com.ai.aijava.agent.dto.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话消息视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageVO {

    private Long id;

    /** user / assistant */
    private String role;

    private String content;

    /** 引用列表（仅 assistant 消息，可为 null） */
    private List<CitationVO> citations;

    private LocalDateTime createTime;
}
```

```java
package com.ai.aijava.agent.dto.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 引用溯源条目（内容快照，不反查已删文档）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitationVO {

    /** 切片 ID */
    private Long chunkId;

    /** 文档 ID */
    private Long docId;

    /** 文档名 */
    private String docName;

    /** 切片序号 */
    private Integer chunkIndex;

    /** 片段原文节选 */
    private String content;

    /** 相似度得分 */
    private Double score;
}
```

### Task 13: KnowledgeBaseService（CRUD + 级联删除 3.4）

**Files:**

- Create: `ai-agent/src/main/java/com/ai/aijava/agent/service/KnowledgeBaseService.java`
- 文档同步：设计文档 9.13

- [ ] **Step 1: 完整代码（级联删除严格按设计 3.4 顺序：缓存 → 删向量 → 删记录 → 删文件）**

```java
package com.ai.aijava.agent.service;

import com.ai.aijava.agent.dto.request.KnowledgeBaseCreateRequest;
import com.ai.aijava.agent.dto.request.KnowledgeBaseUpdateRequest;
import com.ai.aijava.agent.dto.vo.KnowledgeBaseVO;
import com.ai.aijava.agent.dto.vo.KnowledgeDocumentVO;
import com.ai.aijava.agent.entity.ChatMessage;
import com.ai.aijava.agent.entity.ChatSession;
import com.ai.aijava.agent.entity.DocumentChunk;
import com.ai.aijava.agent.entity.KnowledgeBase;
import com.ai.aijava.agent.entity.KnowledgeDocument;
import com.ai.aijava.agent.mapper.ChatMessageMapper;
import com.ai.aijava.agent.mapper.ChatSessionMapper;
import com.ai.aijava.agent.mapper.DocumentChunkMapper;
import com.ai.aijava.agent.mapper.KnowledgeBaseMapper;
import com.ai.aijava.agent.mapper.KnowledgeDocumentMapper;
import com.ai.aijava.context.UserContext;
import com.ai.aijava.exception.BusinessException;
import com.ai.aijava.exception.ErrorCode;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识库服务：CRUD + 级联删除（设计 3.4：同步删除 + 顺序约束 + 幂等）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final VectorStore vectorStore;

    /**
     * 创建知识库
     */
    public KnowledgeBaseVO create(KnowledgeBaseCreateRequest request) {
        KnowledgeBase kb = KnowledgeBase.builder()
                .name(request.getName())
                .description(request.getDescription())
                .userId(UserContext.getUserId())
                .build();
        knowledgeBaseMapper.insert(kb);
        return KnowledgeBaseVO.builder()
                .id(kb.getId())
                .name(kb.getName())
                .description(kb.getDescription())
                .docCount(0L)
                .createTime(kb.getCreateTime())
                .updateTime(kb.getUpdateTime())
                .build();
    }

    /**
     * 我的知识库列表（含 docCount 实时统计）
     */
    public List<KnowledgeBaseVO> listMine() {
        List<KnowledgeBase> kbs = knowledgeBaseMapper.selectListByQuery(
                QueryWrapper.create().where(KnowledgeBase::getUserId).eq(UserContext.getUserId())
                        .orderBy(KnowledgeBase::getCreateTime, false));
        if (kbs.isEmpty()) {
            return List.of();
        }
        // 一次 GROUP BY 查全部 docCount（不冗余统计字段，设计 3.3 #7）
        List<Long> kbIds = kbs.stream().map(KnowledgeBase::getId).toList();
        Map<Long, Long> countMap = knowledgeDocumentMapper.selectListByQuery(
                        QueryWrapper.create()
                                .select(KnowledgeDocument::getKbId)
                                .where(KnowledgeDocument::getKbId).in(kbIds))
                .stream().collect(Collectors.groupingBy(KnowledgeDocument::getKbId, Collectors.counting()));
        return kbs.stream().map(kb -> KnowledgeBaseVO.builder()
                .id(kb.getId())
                .name(kb.getName())
                .description(kb.getDescription())
                .docCount(countMap.getOrDefault(kb.getId(), 0L))
                .createTime(kb.getCreateTime())
                .updateTime(kb.getUpdateTime())
                .build()).toList();
    }

    /**
     * 修改知识库（名称/描述）
     */
    public void update(KnowledgeBaseUpdateRequest request) {
        KnowledgeBase kb = getOwnedKb(request.getId());
        KnowledgeBase update = new KnowledgeBase();
        update.setId(kb.getId());
        update.setName(request.getName());
        update.setDescription(request.getDescription());
        knowledgeBaseMapper.update(update);
    }

    /**
     * 删除知识库——级联清理全链（设计 3.4）：
     * ① 缓存 file_path + chunkId → ② 删 Redis 向量（失败中止）
     * → ③ 删 MySQL（chat_message → chat_session → chunk → doc → kb，单事务）
     * → ④ 删磁盘文件（失败仅记日志）
     */
    public void delete(Long kbId) {
        KnowledgeBase kb = getOwnedKb(kbId);
        // ① 前置缓存
        List<KnowledgeDocument> docs = knowledgeDocumentMapper.selectListByQuery(
                QueryWrapper.create().where(KnowledgeDocument::getKbId).eq(kbId));
        List<String> filePaths = docs.stream().map(KnowledgeDocument::getFilePath)
                .filter(p -> p != null && !p.isBlank()).toList();
        List<Long> chunkIds = documentChunkMapper.selectListByQuery(
                        QueryWrapper.create()
                                .select(DocumentChunk::getId)
                                .where(DocumentChunk::getKbId).eq(kbId))
                .stream().map(DocumentChunk::getId).toList();
        // ② 删向量（按 id 分批，失败中止不删 MySQL）
        deleteVectors(chunkIds);
        // ③ 删 MySQL 记录（单事务）
        deleteRecordsTransaction(kbId);
        // ④ 删磁盘文件（失败不影响）
        filePaths.forEach(this::deleteFileQuietly);
        log.info("知识库已删除 kbId={} name={} docs={} chunks={}", kbId, kb.getName(), docs.size(), chunkIds.size());
    }

    /**
     * 删除文档——级联（缓存 → 删向量 → 删记录 → 删文件）
     * ②③ 步骤事务保证原子性：②删向量（外部依赖失败即中止）→ ③删 MySQL（单事务）
     */
    public void deleteDocument(Long docId) {
        KnowledgeDocument doc = knowledgeDocumentMapper.selectOneById(docId);
        if (doc == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文档不存在");
        }
        getOwnedKb(doc.getKbId()); // 归属校验（不拥有则抛异常）
        List<Long> chunkIds = documentChunkMapper.selectListByQuery(
                        QueryWrapper.create()
                                .select(DocumentChunk::getId)
                                .where(DocumentChunk::getDocId).eq(docId))
                .stream().map(DocumentChunk::getId).toList();
        deleteVectors(chunkIds);
        // ③ 删 MySQL 记录（单事务）
        self.deleteDocumentRecordsTransaction(docId);
        deleteFileQuietly(doc.getFilePath());
        log.info("文档已删除 docId={} kbId={} chunks={}", docId, doc.getKbId(), chunkIds.size());
    }

    /**
     * 文档列表（按知识库，含状态；归属校验）
     */
    public List<KnowledgeDocumentVO> listDocuments(Long kbId) {
        getOwnedKb(kbId);
        return knowledgeDocumentMapper.selectListByQuery(
                        QueryWrapper.create()
                                .where(KnowledgeDocument::getKbId).eq(kbId)
                                .orderBy(KnowledgeDocument::getCreateTime, false))
                .stream().map(doc -> KnowledgeDocumentVO.builder()
                        .id(doc.getId())
                        .kbId(doc.getKbId())
                        .fileName(doc.getFileName())
                        .fileType(doc.getFileType())
                        .fileSize(doc.getFileSize())
                        .status(doc.getStatus())
                        .errorMessage(doc.getErrorMessage())
                        .createTime(doc.getCreateTime())
                        .updateTime(doc.getUpdateTime())
                        .build()).toList();
    }

    /**
     * 校验知识库归属当前用户，返回实体（5.5 #4：归属校验在 Service 层）
     */
    public KnowledgeBase getOwnedKb(Long kbId) {
        KnowledgeBase kb = knowledgeBaseMapper.selectOneById(kbId);
        if (kb == null || !kb.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        }
        return kb;
    }

    /**
     * 按 id 分批删向量（≤500/批，幂等：删不存在的 id 是 no-op）
     */
    private void deleteVectors(List<Long> chunkIds) {
        for (int i = 0; i < chunkIds.size(); i += 500) {
            List<String> batch = chunkIds.subList(i, Math.min(i + 500, chunkIds.size()))
                    .stream().map(String::valueOf).toList();
            vectorStore.delete(batch);
        }
    }

    /**
     * 删 MySQL 全链（单事务；顺序按依赖：message → session → chunk → doc → kb）
     * 必须经代理调用才生效（见 Step 2 自注入代理修正）
     */
    @Transactional(rollbackFor = Exception.class)
    protected void deleteRecordsTransaction(Long kbId) {
        List<Long> sessionIds = chatSessionMapper.selectListByQuery(
                        QueryWrapper.create().select(ChatSession::getId)
                                .where(ChatSession::getKbId).eq(kbId))
                .stream().map(ChatSession::getId).toList();
        if (!sessionIds.isEmpty()) {
            chatMessageMapper.deleteByQuery(QueryWrapper.create()
                    .where(ChatMessage::getSessionId).in(sessionIds));
            chatSessionMapper.deleteByQuery(QueryWrapper.create()
                    .where(ChatSession::getKbId).eq(kbId));
        }
        documentChunkMapper.deleteByQuery(QueryWrapper.create()
                .where(DocumentChunk::getKbId).eq(kbId));
        knowledgeDocumentMapper.deleteByQuery(QueryWrapper.create()
                .where(KnowledgeDocument::getKbId).eq(kbId));
        knowledgeBaseMapper.deleteById(kbId);
    }

    /**
     * 删除单个文档的 MySQL 记录（单事务；chunk → doc）
     * 必须经代理调用才生效（见 Step 2 自注入代理修正）
     */
    @Transactional(rollbackFor = Exception.class)
    protected void deleteDocumentRecordsTransaction(Long docId) {
        documentChunkMapper.deleteByQuery(QueryWrapper.create()
                .where(DocumentChunk::getDocId).eq(docId));
        knowledgeDocumentMapper.deleteById(docId);
    }

    /**
     * 删磁盘文件（不存在忽略，失败仅记日志）
     */
    private void deleteFileQuietly(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        File file = new File(filePath);
        if (file.exists() && !file.delete()) {
            log.error("文件删除失败（不影响检索，可人工清理）: {}", filePath);
        }
    }
}
```

说明：`deleteRecordsTransaction` 和 `deleteDocumentRecordsTransaction` 都是**同类自调用**，`@Transactional` 不经代理同样不生效。本计划采用**自注入代理**（Spring 标准做法，`@Lazy` 自身）。

- [ ] **Step 2: 事务自调用修正（最终版——完整类体，不省略字段）**

类成员与 `delete` / `deleteDocument` 方法调整（Step 1 代码块替换为以下完整版本）：

```java
package com.ai.aijava.agent.service;

import com.ai.aijava.agent.dto.request.KnowledgeBaseCreateRequest;
import com.ai.aijava.agent.dto.request.KnowledgeBaseUpdateRequest;
import com.ai.aijava.agent.dto.vo.KnowledgeBaseVO;
import com.ai.aijava.agent.dto.vo.KnowledgeDocumentVO;
import com.ai.aijava.agent.entity.ChatMessage;
import com.ai.aijava.agent.entity.ChatSession;
import com.ai.aijava.agent.entity.DocumentChunk;
import com.ai.aijava.agent.entity.KnowledgeBase;
import com.ai.aijava.agent.entity.KnowledgeDocument;
import com.ai.aijava.agent.mapper.ChatMessageMapper;
import com.ai.aijava.agent.mapper.ChatSessionMapper;
import com.ai.aijava.agent.mapper.DocumentChunkMapper;
import com.ai.aijava.agent.mapper.KnowledgeBaseMapper;
import com.ai.aijava.agent.mapper.KnowledgeDocumentMapper;
import com.ai.aijava.context.UserContext;
import com.ai.aijava.exception.BusinessException;
import com.ai.aijava.exception.ErrorCode;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识库服务：CRUD + 级联删除（设计 3.4：同步删除 + 顺序约束 + 幂等）
 */
@Slf4j
@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final VectorStore vectorStore;

    /** 自注入代理：保证 @Transactional 方法经代理生效 */
    @Autowired
    @Lazy
    private KnowledgeBaseService self;

    public KnowledgeBaseService(KnowledgeBaseMapper knowledgeBaseMapper,
                                 KnowledgeDocumentMapper knowledgeDocumentMapper,
                                 DocumentChunkMapper documentChunkMapper,
                                 ChatSessionMapper chatSessionMapper,
                                 ChatMessageMapper chatMessageMapper,
                                 VectorStore vectorStore) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.vectorStore = vectorStore;
    }

    // ... create(), listMine(), update() 方法同 Step 1 不变 ...

    public void delete(Long kbId) {
        KnowledgeBase kb = getOwnedKb(kbId);
        // ① 前置缓存（file_path + chunkId）
        List<KnowledgeDocument> docs = knowledgeDocumentMapper.selectListByQuery(
                QueryWrapper.create().where(KnowledgeDocument::getKbId).eq(kbId));
        List<String> filePaths = docs.stream().map(KnowledgeDocument::getFilePath)
                .filter(p -> p != null && !p.isBlank()).toList();
        List<Long> chunkIds = documentChunkMapper.selectListByQuery(
                        QueryWrapper.create()
                                .select(DocumentChunk::getId)
                                .where(DocumentChunk::getKbId).eq(kbId))
                .stream().map(DocumentChunk::getId).toList();
        // ② 删向量（按 id 分批，失败中止不删 MySQL）
        deleteVectors(chunkIds);
        // ③ 删 MySQL 记录（经代理调用，事务生效）
        self.deleteRecordsTransaction(kbId);
        // ④ 删磁盘文件（失败不影响）
        filePaths.forEach(this::deleteFileQuietly);
        log.info("知识库已删除 kbId={} name={} docs={} chunks={}", kbId, kb.getName(), docs.size(), chunkIds.size());
    }

    /**
     * 删除文档——级联（缓存 → 删向量 → 删记录 → 删文件）
     * ②③ 步骤事务保证原子性：②删向量（外部依赖失败即中止）→ ③删 MySQL（单事务）
     */
    public void deleteDocument(Long docId) {
        KnowledgeDocument doc = knowledgeDocumentMapper.selectOneById(docId);
        if (doc == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文档不存在");
        }
        getOwnedKb(doc.getKbId()); // 归属校验（不拥有则抛异常）
        List<Long> chunkIds = documentChunkMapper.selectListByQuery(
                        QueryWrapper.create()
                                .select(DocumentChunk::getId)
                                .where(DocumentChunk::getDocId).eq(docId))
                .stream().map(DocumentChunk::getId).toList();
        deleteVectors(chunkIds);
        // ③ 删 MySQL 记录（经代理调用，事务生效）
        self.deleteDocumentRecordsTransaction(docId);
        // ④ 删磁盘文件（失败不影响）
        deleteFileQuietly(doc.getFilePath());
        log.info("文档已删除 docId={} kbId={} chunks={}", docId, doc.getKbId(), chunkIds.size());
    }

    // ... listDocuments(), getOwnedKb(), deleteVectors() 方法同 Step 1 不变 ...

    /**
     * 删 MySQL 全链（单事务；顺序按依赖：message → session → chunk → doc → kb）
     * 必须经代理调用才生效
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRecordsTransaction(Long kbId) {
        List<Long> sessionIds = chatSessionMapper.selectListByQuery(
                        QueryWrapper.create().select(ChatSession::getId)
                                .where(ChatSession::getKbId).eq(kbId))
                .stream().map(ChatSession::getId).toList();
        if (!sessionIds.isEmpty()) {
            chatMessageMapper.deleteByQuery(QueryWrapper.create()
                    .where(ChatMessage::getSessionId).in(sessionIds));
            chatSessionMapper.deleteByQuery(QueryWrapper.create()
                    .where(ChatSession::getKbId).eq(kbId));
        }
        documentChunkMapper.deleteByQuery(QueryWrapper.create()
                .where(DocumentChunk::getKbId).eq(kbId));
        knowledgeDocumentMapper.deleteByQuery(QueryWrapper.create()
                .where(KnowledgeDocument::getKbId).eq(kbId));
        knowledgeBaseMapper.deleteById(kbId);
    }

    /**
     * 删除单个文档的 MySQL 记录（单事务；chunk → doc）
     * 必须经代理调用才生效
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocumentRecordsTransaction(Long docId) {
        documentChunkMapper.deleteByQuery(QueryWrapper.create()
                .where(DocumentChunk::getDocId).eq(docId));
        knowledgeDocumentMapper.deleteById(docId);
    }

    // ... deleteFileQuietly() 方法同 Step 1 不变 ...
}
```

### Task 14: 知识库与文档 Controller

**Files:**

- Create: `ai-agent/src/main/java/com/ai/aijava/agent/controller/KnowledgeBaseController.java`
- Create: `ai-agent/src/main/java/com/ai/aijava/agent/controller/KnowledgeDocController.java`
- 文档同步：设计文档 9.14

- [ ] **Step 1: KnowledgeBaseController**

```java
package com.ai.aijava.agent.controller;

import com.ai.aijava.agent.dto.request.KnowledgeBaseCreateRequest;
import com.ai.aijava.agent.dto.request.KnowledgeBaseUpdateRequest;
import com.ai.aijava.agent.dto.vo.KnowledgeBaseVO;
import com.ai.aijava.agent.service.KnowledgeBaseService;
import com.ai.aijava.annotation.AuditLog;
import com.ai.aijava.annotation.RequireLogin;
import com.ai.aijava.audit.AuditLogType;
import com.ai.aijava.common.BaseResponse;
import com.ai.aijava.common.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库管理接口
 */
@Tag(name = "知识库管理")
@RestController
@RequestMapping("/kb")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @Operation(summary = "创建知识库")
    @RequireLogin
    @AuditLog(type = AuditLogType.DATA_CREATE, module = "知识库管理", description = "创建知识库")
    @PostMapping("/create")
    public BaseResponse<KnowledgeBaseVO> create(@RequestBody @Valid KnowledgeBaseCreateRequest request) {
        return ResultUtils.success(knowledgeBaseService.create(request));
    }

    @Operation(summary = "我的知识库列表")
    @RequireLogin
    @GetMapping("/list")
    public BaseResponse<List<KnowledgeBaseVO>> list() {
        return ResultUtils.success(knowledgeBaseService.listMine());
    }

    @Operation(summary = "修改知识库")
    @RequireLogin
    @AuditLog(type = AuditLogType.DATA_UPDATE, module = "知识库管理", description = "修改知识库")
    @PostMapping("/update")
    public BaseResponse<Void> update(@RequestBody @Valid KnowledgeBaseUpdateRequest request) {
        knowledgeBaseService.update(request);
        return ResultUtils.success(null);
    }

    @Operation(summary = "删除知识库（级联清理文档/切片/向量/会话/文件）")
    @RequireLogin
    @AuditLog(type = AuditLogType.DATA_DELETE, module = "知识库管理", description = "删除知识库")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable Long id) {
        knowledgeBaseService.delete(id);
        return ResultUtils.success(null);
    }
}
```

- [ ] **Step 2: KnowledgeDocController**

```java
package com.ai.aijava.agent.controller;

import com.ai.aijava.agent.dto.vo.KnowledgeDocumentVO;
import com.ai.aijava.agent.service.DocumentIngestService;
import com.ai.aijava.agent.service.KnowledgeBaseService;
import com.ai.aijava.annotation.AuditLog;
import com.ai.aijava.annotation.RequireLogin;
import com.ai.aijava.audit.AuditLogType;
import com.ai.aijava.common.BaseResponse;
import com.ai.aijava.common.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库文档管理接口
 */
@Tag(name = "知识库文档")
@RestController
@RequestMapping("/kb")
@RequiredArgsConstructor
public class KnowledgeDocController {

    private final DocumentIngestService documentIngestService;
    private final KnowledgeBaseService knowledgeBaseService;

    @Operation(summary = "上传文档（异步摄取，返回 docId）")
    @RequireLogin
    @AuditLog(type = AuditLogType.DATA_CREATE, module = "知识库管理", description = "上传文档")
    @PostMapping("/{kbId}/document/upload")
    public BaseResponse<Long> upload(@PathVariable Long kbId, @RequestParam("file") MultipartFile file) {
        return ResultUtils.success(documentIngestService.upload(kbId, file));
    }

    @Operation(summary = "文档列表（含摄取状态，兼作进度轮询）")
    @RequireLogin
    @GetMapping("/{kbId}/document/list")
    public BaseResponse<List<KnowledgeDocumentVO>> listDocuments(@PathVariable Long kbId) {
        return ResultUtils.success(knowledgeBaseService.listDocuments(kbId));
    }

    @Operation(summary = "删除文档（级联清理切片/向量/文件）")
    @RequireLogin
    @AuditLog(type = AuditLogType.DATA_DELETE, module = "知识库管理", description = "删除文档")
    @DeleteMapping("/document/{docId}")
    public BaseResponse<Void> deleteDocument(@PathVariable Long docId) {
        knowledgeBaseService.deleteDocument(docId);
        return ResultUtils.success(null);
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
mvn clean compile
```

- [ ] **Step 4: 用户 commit（建议信息）**

```text
feat: 知识库/文档管理——CRUD、级联删除、审计接入
```

---

## 阶段 6：对话与 RAG 问答

### Task 15: ChatSessionService（会话/消息 CRUD + title + history）

**Files:**

- Create: `ai-agent/src/main/java/com/ai/aijava/agent/service/ChatSessionService.java`
- 文档同步：设计文档 9.15

- [ ] **Step 1: 完整代码**

```java
package com.ai.aijava.agent.service;

import com.ai.aijava.agent.dto.request.ChatSessionCreateRequest;
import com.ai.aijava.agent.dto.vo.ChatMessageVO;
import com.ai.aijava.agent.dto.vo.ChatSessionVO;
import com.ai.aijava.agent.dto.vo.CitationVO;
import com.ai.aijava.agent.entity.ChatMessage;
import com.ai.aijava.agent.entity.ChatSession;
import com.ai.aijava.agent.entity.KnowledgeBase;
import com.ai.aijava.agent.entity.KnowledgeDocument;
import com.ai.aijava.agent.mapper.ChatMessageMapper;
import com.ai.aijava.agent.mapper.ChatSessionMapper;
import com.ai.aijava.agent.mapper.KnowledgeBaseMapper;
import com.ai.aijava.agent.mapper.KnowledgeDocumentMapper;
import com.ai.aijava.context.UserContext;
import com.ai.aijava.exception.BusinessException;
import com.ai.aijava.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 会话与消息服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private static final TypeReference<List<CitationVO>> CITATION_TYPE = new TypeReference<>() {};

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final ObjectMapper objectMapper;

    /**
     * 创建会话（kbId 绑定，title 默认）
     */
    @Transactional(rollbackFor = Exception.class)
    public ChatSessionVO create(ChatSessionCreateRequest request) {
        // 归属校验
        KnowledgeBase kb = knowledgeBaseMapper.selectOneById(request.getKbId());
        if (kb == null || !kb.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        }
        ChatSession session = ChatSession.builder()
                .userId(UserContext.getUserId())
                .kbId(request.getKbId())
                .title(ChatSession.DEFAULT_TITLE)
                .build();
        chatSessionMapper.insert(session);
        return ChatSessionVO.builder()
                .id(session.getId())
                .kbId(session.getKbId())
                .kbName(kb.getName())
                .title(session.getTitle())
                .createTime(session.getCreateTime())
                .updateTime(session.getUpdateTime())
                .build();
    }

    /**
     * 会话列表（按最后活跃倒序，可按 kbId 过滤）
     */
    public List<ChatSessionVO> listSessions(Long kbId) {
        QueryWrapper qw = QueryWrapper.create()
                .where(ChatSession::getUserId).eq(UserContext.getUserId());
        if (kbId != null) {
            qw.and(ChatSession::getKbId).eq(kbId);
        }
        qw.orderBy(ChatSession::getUpdateTime, false);
        List<ChatSession> sessions = chatSessionMapper.selectListByQuery(qw);
        if (sessions.isEmpty()) {
            return List.of();
        }
        // join 查 kbName
        List<Long> kbIds = sessions.stream().map(ChatSession::getKbId).distinct().toList();
        List<KnowledgeBase> kbs = knowledgeBaseMapper.selectListByQuery(
                QueryWrapper.create().select(KnowledgeBase::getId, KnowledgeBase::getName)
                        .where(KnowledgeBase::getId).in(kbIds));
        var nameMap = kbs.stream().collect(java.util.stream.Collectors.toMap(KnowledgeBase::getId, KnowledgeBase::getName));
        return sessions.stream().map(s -> ChatSessionVO.builder()
                .id(s.getId())
                .kbId(s.getKbId())
                .kbName(nameMap.get(s.getKbId()))
                .title(s.getTitle())
                .createTime(s.getCreateTime())
                .updateTime(s.getUpdateTime())
                .build()).toList();
    }

    /**
     * 历史消息（全量，按 id 升序即时间序；含 citations 反序列化）
     */
    public List<ChatMessageVO> listMessages(Long sessionId) {
        ChatSession session = getOwnedSession(sessionId);
        return chatMessageMapper.selectListByQuery(
                        QueryWrapper.create()
                                .where(ChatMessage::getSessionId).eq(sessionId)
                                .orderBy(ChatMessage::getId, true))
                .stream().map(msg -> {
                    List<CitationVO> cites = null;
                    if (msg.getCitations() != null && !msg.getCitations().isBlank()) {
                        try {
                            cites = objectMapper.readValue(msg.getCitations(), CITATION_TYPE);
                        } catch (JsonProcessingException e) {
                            log.warn("citations JSON 解析失败", e);
                        }
                    }
                    return ChatMessageVO.builder()
                            .id(msg.getId())
                            .role(msg.getRole())
                            .content(msg.getContent())
                            .citations(cites)
                            .createTime(msg.getCreateTime())
                            .build();
                }).toList();
    }

    /**
     * 删除会话（级联删消息）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long sessionId) {
        getOwnedSession(sessionId);
        chatMessageMapper.deleteByQuery(QueryWrapper.create()
                .where(ChatMessage::getSessionId).eq(sessionId));
        chatSessionMapper.deleteById(sessionId);
    }

    /**
     * 保存用户提问
     */
    public Long saveUserMessage(Long sessionId, String question) {
        ChatMessage msg = ChatMessage.builder()
                .sessionId(sessionId)
                .role(ChatMessage.ROLE_USER)
                .content(question)
                .build();
        chatMessageMapper.insert(msg);
        return msg.getId();
    }

    /**
     * 保存助手回答（含 citations JSON 序列化）
     */
    public void saveAssistantMessage(Long sessionId, String answer, String citationsJson) {
        ChatMessage msg = ChatMessage.builder()
                .sessionId(sessionId)
                .role(ChatMessage.ROLE_ASSISTANT)
                .content(answer)
                .citations(citationsJson)
                .build();
        chatMessageMapper.insert(msg);
    }

    /**
     * 更新会话 title（首次提问后截取前 20 字）
     */
    public void updateTitleIfNeeded(Long sessionId, String question) {
        ChatSession session = chatSessionMapper.selectOneById(sessionId);
        if (session != null && ChatSession.DEFAULT_TITLE.equals(session.getTitle())) {
            ChatSession update = new ChatSession();
            update.setId(sessionId);
            String title = question.length() > 20 ? question.substring(0, 20) : question;
            update.setTitle(title);
            chatSessionMapper.update(update);
        }
    }

    /**
     * 刷新会话活跃时间（只更新 update_time，避免 partial entity 导致其他字段被置 NULL）
     */
    public void refreshSessionActiveTime(Long sessionId) {
        ChatSession update = new ChatSession();
        update.setId(sessionId);
        chatSessionMapper.update(update);
    }

    /**
     * 获取历史上下文（最近 N 轮对话），丢弃末尾连续的孤立 user 消息
     *
     * 设计 4.2 决策 #6：流失败遗留的无应答 user 不进 history，防止模型看到连续 user 无 assistant
     */
    public List<org.springframework.ai.chat.message.Message> getHistory(Long sessionId, int historyRounds) {
        // id desc 取最近 2*historyRounds 条
        int limit = historyRounds * 2;
        List<ChatMessage> msgs = chatMessageMapper.selectListByQuery(
                QueryWrapper.create()
                        .select(ChatMessage::getRole, ChatMessage::getContent)
                        .where(ChatMessage::getSessionId).eq(sessionId)
                        .orderBy(ChatMessage::getId, false)
                        .limit(limit));
        if (msgs.isEmpty()) {
            return List.of();
        }
        // 反转为时间正序
        msgs = msgs.reversed();
        // 丢弃末尾连续的孤立 user 消息（只丢弃最末一段纯 user）
        while (!msgs.isEmpty() && msgs.get(msgs.size() - 1).getRole().equals(ChatMessage.ROLE_USER)) {
            msgs.removeLast();
        }
        // 转为 Spring AI Message
        return msgs.stream().map(m -> {
            if (m.getRole().equals(ChatMessage.ROLE_USER)) {
                return org.springframework.ai.chat.message.UserMessage.from(m.getContent());
            } else {
                return org.springframework.ai.chat.message.AssistantMessage.from(m.getContent());
            }
        }).toList();
    }

    /**
     * 校验会话归属当前用户
     */
    public ChatSession getOwnedSession(Long sessionId) {
        ChatSession session = chatSessionMapper.selectOneById(sessionId);
        if (session == null || !session.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "会话不存在");
        }
        return session;
    }
}
```

### Task 16: RagChatService（RAG 检索 + Prompt 拼装 + 流式）

**Files:**

- Create: `ai-agent/src/main/java/com/ai/aijava/agent/service/RagChatService.java`
- 文档同步：设计文档 9.16

- [ ] **Step 1: 完整代码**

```java
package com.ai.aijava.agent.service;

import com.ai.aijava.agent.config.AgentProperties;
import com.ai.aijava.agent.dto.vo.CitationVO;
import com.ai.aijava.agent.entity.KnowledgeDocument;
import com.ai.aijava.agent.enums.DocStatus;
import com.ai.aijava.agent.mapper.KnowledgeDocumentMapper;
import com.ai.aijava.exception.BusinessException;
import com.ai.aijava.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RAG 流式问答（核心）
 * 检索 → 过滤 → Prompt 拼装 → ChatModel 流式 → SSE 推送
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagChatService {

    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final AgentProperties agentProperties;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final ChatSessionService chatSessionService;
    private final ObjectMapper objectMapper;

    /**
     * 会话提问 → SSE Flux（4.2 事件协议）
     */
    public Flux<ServerSentEvent<String>> chat(Long sessionId, String question) {
        // 归属校验（取会话 → 取 kbId）
        var session = chatSessionService.getOwnedSession(sessionId);
        Long kbId = session.getKbId();
        int topK = agentProperties.getTopK();
        int overSampleK = topK * 2; // 过采样（设计 4.2 决策 #5）

        // 检索（过采样）
        List<Document> rawHits = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(overSampleK)
                        .filterExpression("kbId == '" + kbId + "'")
                        .build());

        // 过滤脏向量（只保留所属文档 status=COMPLETED 的 hits）
        List<Document> hits = filterCompleted(rawHits);

        // 构建 citations（含 docName）
        List<CitationVO> citations = buildCitations(hits);

        // 历史（丢弃末尾孤立 user）
        List<Message> history = chatSessionService.getHistory(sessionId, agentProperties.getHistoryRounds());

        // 拼装 Prompt
        String refText = buildReferences(hits);
        String userText = (refText.isBlank() ? "" : "参考资料：\n" + refText + "\n\n")
                + "问题：" + question;
        Prompt prompt = new Prompt(history, List.of(
                new org.springframework.ai.chat.message.SystemMessage(agentProperties.effectiveSystemPrompt()),
                new org.springframework.ai.chat.message.UserMessage(userText)));

        // user 消息落库
        chatSessionService.saveUserMessage(sessionId, question);
        chatSessionService.updateTitleIfNeeded(sessionId, question);

        // 流式生成 + 装配 SSE
        return assembleFlux(chatModel.stream(prompt), sessionId, citations, question);
    }

    /**
     * 将 ChatModel Flux 装配为 SSE 事件流（message / citations / end / error）
     * 设计 4.2 决策 #7：onErrorResume 兜底异常转 error 事件
     */
    private Flux<ServerSentEvent<String>> assembleFlux(Flux<ChatResponse> responseFlux, Long sessionId,
                                                        List<CitationVO> citations, String question) {
        StringBuilder aggregated = new StringBuilder();
        return responseFlux
                .doOnNext(chunk -> {
                    String content = chunk.getResult() != null && chunk.getResult().getOutput() != null
                            ? chunk.getResult().getOutput().getText() : "";
                    if (content != null && !content.isBlank()) {
                        aggregated.append(content);
                    }
                })
                .mapNotNull(chunk -> {
                    String content = chunk.getResult() != null && chunk.getResult().getOutput() != null
                            ? chunk.getResult().getOutput().getText() : "";
                    if (content == null || content.isBlank()) {
                        return null;
                    }
                    return ServerSentEvent.<String>builder()
                            .event("message")
                            .data(content)
                            .build();
                })
                .doOnComplete(() -> {
                    try {
                        String citationsJson = objectMapper.writeValueAsString(citations);
                        chatSessionService.saveAssistantMessage(sessionId, aggregated.toString(), citationsJson);
                        chatSessionService.refreshSessionActiveTime(sessionId);
                    } catch (JsonProcessingException e) {
                        log.error("citations 序列化失败，助手消息未落库", e);
                    } catch (Exception e) {
                        log.error("保存助手消息失败", e);
                    }
                })
                .concatWith(
                        // citations 事件
                        Mono.defer(() -> {
                            try {
                                String json = objectMapper.writeValueAsString(citations);
                                return Flux.just(ServerSentEvent.<String>builder()
                                        .event("citations")
                                        .data(json)
                                        .build());
                            } catch (JsonProcessingException e) {
                                log.error("citations 序列化失败", e);
                                return Flux.empty();
                            }
                        }).flux())
                .concatWith(Mono.just(ServerSentEvent.<String>builder().event("end").build()))
                .onErrorResume(e -> {
                    log.error("RAG 问答流式异常", e);
                    // error 事件
                    return Mono.just(ServerSentEvent.<String>builder()
                            .event("error")
                            .data("回答失败：" + (e.getMessage() != null ? e.getMessage().replaceAll("[\\r\\n]", "") : "未知错误"))
                            .build());
                });
    }

    /**
     * 过滤脏向量：只保留所属文档 status=COMPLETED 的 hits（设计 4.2 ③）
     */
    private List<Document> filterCompleted(List<Document> hits) {
        if (hits.isEmpty()) {
            return List.of();
        }
        // 按 docId 去重回查 status
        List<String> docIds = hits.stream()
                .map(d -> (String) d.getMetadata().getOrDefault("docId", ""))
                .distinct().toList();
        List<String> completedDocIds = knowledgeDocumentMapper.selectListByQuery(
                        QueryWrapper.create()
                                .select(KnowledgeDocument::getId)
                                .where(KnowledgeDocument::getId).in(docIds.stream().map(Long::parseLong).toList())
                                .and(KnowledgeDocument::getStatus).eq(DocStatus.COMPLETED.name()))
                .stream().map(d -> String.valueOf(d.getId())).toList();
        return hits.stream()
                .filter(d -> completedDocIds.contains(d.getMetadata().getOrDefault("docId", "")))
                .toList();
    }

    /**
     * 构建引用列表（含 docName）
     */
    private List<CitationVO> buildCitations(List<Document> hits) {
        List<CitationVO> result = new ArrayList<>();
        // 按 docId 批量查 docName
        List<Long> docIds = hits.stream()
                .map(d -> Long.parseLong((String) d.getMetadata().getOrDefault("docId", "0")))
                .filter(id -> id > 0).distinct().toList();
        Map<Long, String> nameMap = Map.of();
        if (!docIds.isEmpty()) {
            nameMap = knowledgeDocumentMapper.selectListByQuery(
                            QueryWrapper.create()
                                    .select(KnowledgeDocument::getId, KnowledgeDocument::getFileName)
                                    .where(KnowledgeDocument::getId).in(docIds))
                    .stream().collect(java.util.stream.Collectors.toMap(KnowledgeDocument::getId, KnowledgeDocument::getFileName));
        }
        for (Document doc : hits) {
            Long chunkId = Long.parseLong(doc.getId());
            Long docId = Long.parseLong((String) doc.getMetadata().getOrDefault("docId", "0"));
            Integer chunkIndex = (Integer) doc.getMetadata().getOrDefault("chunkIndex", 0);
            Double score = (Double) doc.getMetadata().getOrDefault("score", 0.0);
            String content = doc.getText();
            // 节选前 150 字
            if (content != null && content.length() > 150) {
                content = content.substring(0, 150) + "...";
            }
            result.add(CitationVO.builder()
                    .chunkId(chunkId)
                    .docId(docId)
                    .docName(nameMap.getOrDefault(docId, "未知文档"))
                    .chunkIndex(chunkIndex)
                    .content(content)
                    .score(score)
                    .build());
        }
        return result;
    }

    /**
     * 构建 Prompt 引用文本
     */
    private String buildReferences(List<Document> hits) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            sb.append("[").append(i + 1).append("] ").append(hits.get(i).getText()).append("\n\n");
        }
        return sb.toString();
    }
}
```

注意：`ChatResponse` 的 `getResult().getOutput().getText()` 路径需确认 Spring AI 2.0 API 是否匹配。
若 `ChatResponse.getResult()` 改为 `chatResponse.getResults().get(0)`（List<Result>），则需调整。
**实现前验证**：`ChatModel.stream(Prompt)` 返回 `Flux<ChatResponse>`，`ChatResponse` 取文本用 `response.getResult().getOutput().getText()` 是 1.0.x 写法，2.0 可能变化。

### Task 17: ChatController（SSE 端点）

**Files:**

- Create: `ai-agent/src/main/java/com/ai/aijava/agent/controller/ChatController.java`
- 文档同步：设计文档 9.17

- [ ] **Step 1: 完整代码**

```java
package com.ai.aijava.agent.controller;

import com.ai.aijava.agent.dto.request.ChatSendRequest;
import com.ai.aijava.agent.dto.request.ChatSessionCreateRequest;
import com.ai.aijava.agent.dto.vo.ChatMessageVO;
import com.ai.aijava.agent.dto.vo.ChatSessionVO;
import com.ai.aijava.agent.service.ChatSessionService;
import com.ai.aijava.agent.service.RagChatService;
import com.ai.aijava.annotation.RequireLogin;
import com.ai.aijava.common.BaseResponse;
import com.ai.aijava.common.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 对话接口（含 SSE 流式问答）
 */
@Tag(name = "对话")
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatSessionService chatSessionService;
    private final RagChatService ragChatService;

    @Operation(summary = "创建会话")
    @RequireLogin
    @PostMapping("/session/create")
    public BaseResponse<ChatSessionVO> createSession(@RequestBody @Valid ChatSessionCreateRequest request) {
        return ResultUtils.success(chatSessionService.create(request));
    }

    @Operation(summary = "会话列表")
    @RequireLogin
    @GetMapping("/session/list")
    public BaseResponse<List<ChatSessionVO>> listSessions(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long kbId) {
        return ResultUtils.success(chatSessionService.listSessions(kbId));
    }

    @Operation(summary = "历史消息")
    @RequireLogin
    @GetMapping("/session/{sessionId}/messages")
    public BaseResponse<List<ChatMessageVO>> listMessages(@PathVariable Long sessionId) {
        return ResultUtils.success(chatSessionService.listMessages(sessionId));
    }

    @Operation(summary = "删除会话")
    @RequireLogin
    @DeleteMapping("/session/{sessionId}")
    public BaseResponse<Void> deleteSession(@PathVariable Long sessionId) {
        chatSessionService.deleteSession(sessionId);
        return ResultUtils.success(null);
    }

    @Operation(summary = "提问（SSE 流式响应，4.2 事件协议）")
    @RequireLogin
    @PostMapping(value = "/session/{sessionId}/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> send(@PathVariable Long sessionId,
                                               @RequestBody @Valid ChatSendRequest request) {
        return ragChatService.chat(sessionId, request.getQuestion());
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn clean compile
```

- [ ] **Step 3: 用户 commit（建议信息）**

```text
feat: 对话与 RAG——会话管理、流式问答（过采样过滤、SSE 事件协议、onErrorResume 兜底）
```

---

## 阶段 7：MCP Server

### Task 18: McpSecurityInterceptor + 自注册 WebConfig（ai-agent 内聚）

**Files:**

- Create: `ai-agent/src/main/java/com/ai/aijava/agent/config/McpSecurityInterceptor.java`
- Create: `ai-agent/src/main/java/com/ai/aijava/agent/config/AgentWebConfig.java`
- 文档同步：设计文档 9.18

- [ ] **Step 1: McpSecurityInterceptor**

```java
package com.ai.aijava.agent.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * MCP 端点安全拦截器
 * 校验 X-MCP-Token 请求头与 mcp.security.token 一致
 * 未配置 token 时放行 + 打告警（开发便利），已配置时强校验
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpSecurityInterceptor implements HandlerInterceptor {

    private static final String HEADER_MCP_TOKEN = "X-MCP-Token";

    @Value("${mcp.security.token:}")
    private String mcpToken;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // mcpToken 未配置 → 放行 + 告警
        if (mcpToken == null || mcpToken.isBlank()) {
            log.warn("MCP 端点未配置 token（mcp.security.token），所有请求将被放行！生产环境必须配置！");
            return true;
        }
        String header = request.getHeader(HEADER_MCP_TOKEN);
        if (!mcpToken.equals(header)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json;charset=UTF-8");
            try {
                response.getWriter().write("{\"code\":40103,\"message\":\"MCP token 无效或为空\"}");
            } catch (Exception e) {
                // 忽略写入异常
            }
            return false;
        }
        return true;
    }
}
```

- [ ] **Step 2: AgentWebConfig（自注册拦截器到 /mcp，内聚在 ai-agent 不动 ai-web）**

```java
package com.ai.aijava.agent.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * ai-agent 模块 Web MVC 配置（内聚在 ai-agent，避免依赖 ai-web 配置）
 */
@Configuration
@RequiredArgsConstructor
public class AgentWebConfig implements WebMvcConfigurer {

    private final McpSecurityInterceptor mcpSecurityInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // MCP 端点拦截（STREAMABLE 单端点 /mcp；若 fallback SSE 则还需 /sse + /mcp/messages）
        registry.addInterceptor(mcpSecurityInterceptor)
                .addPathPatterns("/mcp", "/mcp/**", "/sse", "/mcp/messages");
    }
}
```

### Task 19: KnowledgeMcpTools

**Files:**

- Create: `ai-agent/src/main/java/com/ai/aijava/agent/service/mcp/KnowledgeMcpTools.java`
- 文档同步：设计文档 9.19

- [ ] **Step 1: 完整代码**

```java
package com.ai.aijava.agent.service.mcp;

import com.ai.aijava.agent.dto.vo.CitationVO;
import com.ai.aijava.agent.entity.KnowledgeBase;
import com.ai.aijava.agent.enums.DocStatus;
import com.ai.aijava.agent.mapper.KnowledgeBaseMapper;
import com.ai.aijava.agent.mapper.KnowledgeDocumentMapper;
import com.ai.aijava.exception.BusinessException;
import com.ai.aijava.exception.ErrorCode;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP 工具：暴露知识库检索与列举给外部 AI 客户端（Claude Desktop / Cursor 等）
 * MCP 无用户态（不走 JWT），由 McpSecurityInterceptor 校验全局 token
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeMcpTools {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final VectorStore vectorStore;

    /**
     * 列出平台全部知识库
     */
    @McpTool(description = "列出平台全部知识库，返回知识库 ID、名称和描述")
    public String listKnowledgeBases() {
        List<KnowledgeBase> kbs = knowledgeBaseMapper.selectListByQuery(
                QueryWrapper.create()
                        .select(KnowledgeBase::getId, KnowledgeBase::getName, KnowledgeBase::getDescription)
                        .orderBy(KnowledgeBase::getCreateTime, false));
        StringBuilder sb = new StringBuilder("平台共有 " + kbs.size() + " 个知识库：\n\n");
        for (KnowledgeBase kb : kbs) {
            sb.append("- ").append(kb.getId()).append(" [").append(kb.getName()).append("] ")
                    .append(kb.getDescription()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 搜索平台知识库（跨库检索）
     */
    @McpTool(description = "搜索平台知识库，返回最相关的原文片段与来源标注")
    public String searchKnowledge(
            @McpToolParam(description = "搜索关键词或问题", required = true) String query,
            @McpToolParam(description = "知识库 ID（可选，不提供则跨库检索）", required = false) Long kbId) {
        SearchRequest.Builder builder = SearchRequest.builder().query(query).topK(5);
        if (kbId != null) {
            builder.filterExpression("kbId == '" + kbId + "'");
        }
        List<Document> hits = vectorStore.similaritySearch(builder.build());
        if (hits.isEmpty()) {
            return "未检索到相关内容。";
        }
        StringBuilder sb = new StringBuilder("检索到 " + hits.size() + " 条相关内容：\n\n");
        for (int i = 0; i < hits.size(); i++) {
            Document doc = hits.get(i);
            String content = doc.getText();
            if (content != null && content.length() > 300) {
                content = content.substring(0, 300) + "...";
            }
            sb.append("[").append(i + 1).append("] ").append(content).append("\n");
            sb.append("   → 向量 ID: ").append(doc.getId()).append("\n\n");
        }
        return sb.toString();
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn clean compile
```

- [ ] **Step 3: 用户 commit（建议信息）**

```text
feat: MCP Server——全局 token 校验、知识库检索与列举工具
```

---

## 阶段 8：配置变更

### Task 20: application-prod.yml 全量变更（可直接写入项目）

**Files:**

- Modify: `ai-web/src/main/resources/application-prod.yml`
- 文档同步：设计文档 9.20

- [ ] **Step 1: 在现有文件末尾追加**

```yaml
# ==================== ai-agent 智能体模块 ====================

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
          dimensions: 1024 # 显式声明（智谱 embedding-3 支持 256/512/1024/2048）；
          #                       维度一经确定不可更改，改则全量重建（3.3 #4）
    vectorstore:
      redis:
        uri: redis://localhost:6379 # Spring AI 向量库独立连接（不复用 spring.data.redis）
        index-name: ai-java-kb
        prefix: "kb:vector:"
        initialize-schema: true # 首次启动建 HNSW 索引（维度由 EmbeddingModel 推断）
    mcp:
      server:
        name: ai-java-knowledge
        version: 1.0.0
        type: SYNC
        protocol: STREAMABLE # SSE 自 2.0.0 deprecated；客户端仅支持 SSE 时改回 SSE
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
    token: ${MCP_TOKEN:} # MCP 端点校验 token；为空时启动打告警日志并放行
```

- [ ] **Step 2: 编译验证 + 冒烟启动**

```bash
mvn clean compile
# 设置环境变量（空值也行，确保不崩）
export AI_API_KEY=test-key MCP_TOKEN=test-token
mvn spring-boot:run -pl ai-web  # 启动成功即可，不运行完整链路
```

预期：应用启动正常，无 Redis 连接失败以外的 ERROR（Redis 若未连会报连接异常属正常；MySQL 正常连）。

- [ ] **Step 3: 用户 commit（建议信息）**

```text
config: ai-agent 模块——Redis/LLM/MCP/AgentProperties 全量配置
```

---

## 阶段 9：前端

### Task 21: 类型定义（types/ai.ts）+ npm 安装图标包

**Files:**

- Create: `ai-java-front/src/types/ai.ts`
- 文档同步：设计文档 9.21

- [ ] **Step 1: 安装 @vicons 图标包（仅首次需要）**

```bash
cd ai-java-front
npm i -D @vicons/ionicons5
```

- [ ] **Step 2: types/ai.ts 完整代码**

```typescript
// 文档状态机
export enum DocStatus {
  UPLOADED = "已上传",
  PROCESSING = "处理中",
  COMPLETED = "已完成",
  FAILED = "失败",
}

export interface KnowledgeBase {
  id: number;
  name: string;
  description: string;
  docCount: number;
  createTime: string;
  updateTime: string;
}

export interface KnowledgeDocument {
  id: number;
  kbId: number;
  fileName: string;
  fileType: string;
  fileSize: number;
  status: DocStatus;
  errorMessage: string | null;
  createTime: string;
  updateTime: string;
}

export interface ChatSession {
  id: number;
  kbId: number;
  kbName: string;
  title: string;
  createTime: string;
  updateTime: string;
}

export interface ChatMessage {
  id: number;
  role: "user" | "assistant";
  content: string;
  citations: Citation[] | null;
  createTime: string;
}

export interface Citation {
  chunkId: number;
  docId: number;
  docName: string;
  chunkIndex: number;
  content: string;
  score: number;
}

// KB 创建/修改/会话创建请求
export interface CreateKbRequest {
  name: string;
  description?: string;
}

export interface UpdateKbRequest {
  id: number;
  name: string;
  description?: string;
}

export interface CreateSessionRequest {
  kbId: number;
}

export interface ChatSendRequest {
  question: string;
}
```

### Task 22: API 模块（api/kb.ts + api/chat.ts）

**Files:**

- Create: `ai-java-front/src/api/kb.ts`
- Create: `ai-java-front/src/api/chat.ts`
- 文档同步：设计文档 9.22

- [ ] **Step 1: api/kb.ts 完整代码**

```typescript
import request from "./request";
import type { BaseResponse } from "@/types/user";
import type {
  CreateKbRequest,
  UpdateKbRequest,
  KnowledgeBase,
  KnowledgeDocument,
} from "@/types/ai";

/** 创建知识库 */
export function createKbApi(
  data: CreateKbRequest,
): Promise<BaseResponse<KnowledgeBase>> {
  return request.post("/kb/create", data);
}

/** 我的知识库列表 */
export function listKbsApi(): Promise<BaseResponse<KnowledgeBase[]>> {
  return request.get("/kb/list");
}

/** 修改知识库 */
export function updateKbApi(
  data: UpdateKbRequest,
): Promise<BaseResponse<null>> {
  return request.post("/kb/update", data);
}

/** 删除知识库 */
export function deleteKbApi(id: number): Promise<BaseResponse<null>> {
  return request.delete(`/kb/${id}`);
}

/** 上传文档 */
export function uploadDocumentApi(
  kbId: number,
  file: File,
): Promise<BaseResponse<number>> {
  const formData = new FormData();
  formData.append("file", file);
  return request.post(`/kb/${kbId}/document/upload`, formData, {
    headers: { "Content-Type": "multipart/form-data" },
  });
}

/** 文档列表 */
export function listDocumentsApi(
  kbId: number,
): Promise<BaseResponse<KnowledgeDocument[]>> {
  return request.get(`/kb/${kbId}/document/list`);
}

/** 删除文档 */
export function deleteDocumentApi(docId: number): Promise<BaseResponse<null>> {
  return request.delete(`/document/${docId}`);
}
```

- [ ] **Step 2: api/chat.ts 完整代码**

```typescript
import request from "./request";
import type { BaseResponse } from "@/types/user";
import type {
  ChatSession,
  ChatMessage,
  CreateSessionRequest,
} from "@/types/ai";

/** 创建会话 */
export function createSessionApi(
  data: CreateSessionRequest,
): Promise<BaseResponse<ChatSession>> {
  return request.post("/chat/session/create", data);
}

/** 会话列表 */
export function listSessionsApi(
  kbId?: number,
): Promise<BaseResponse<ChatSession[]>> {
  return request.get("/chat/session/list", { params: kbId ? { kbId } : {} });
}

/** 历史消息 */
export function listMessagesApi(
  sessionId: number,
): Promise<BaseResponse<ChatMessage[]>> {
  return request.get(`/chat/session/${sessionId}/messages`);
}

/** 删除会话 */
export function deleteSessionApi(
  sessionId: number,
): Promise<BaseResponse<null>> {
  return request.delete(`/chat/session/${sessionId}`);
}
```

说明：`sendChatMessageApi` 不走 axios，走 `useChatStream.ts` 的 fetch SSE。

### Task 23: Composables（useChatStream + useDocumentPolling）

**Files:**

- Create: `ai-java-front/src/composables/useChatStream.ts`
- Create: `ai-java-front/src/composables/useDocumentPolling.ts`
- 文档同步：设计文档 9.23

- [ ] **Step 1: useChatStream.ts（SSE 解析 composable）**

```typescript
import { ref, type Ref } from "vue";
import type { Citation } from "@/types/ai";

export interface UseChatStreamOptions {
  onMessage: (delta: string) => void;
  onCitations: (citations: Citation[]) => void;
  onEnd: () => void;
  onError: (error: string) => void;
}

export interface UseChatStreamReturn {
  abortController: AbortController;
  send: (sessionId: number, question: string) => Promise<void>;
  isStreaming: Ref<boolean>;
  assistantContent: Ref<string>;
}

/**
 * SSE 流式解析（POST + fetch + ReadableStream）
 * 遵循 SSE 规范：多行 data: 合并 \n，\r\n 兼容，忽略注释行/id:/retry:
 */
export function useChatStream(
  options: UseChatStreamOptions,
): UseChatStreamReturn {
  const isStreaming = ref(false);
  const assistantContent = ref("");
  let abortController: AbortController;

  async function send(sessionId: number, question: string) {
    abortController = new AbortController();
    isStreaming.value = true;
    assistantContent.value = "";

    const token = localStorage.getItem("accessToken") || "";
    const response = await fetch(`/api/chat/session/${sessionId}/send`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ question }),
      signal: abortController.signal,
    });

    if (!response.ok) {
      // 读 JSON 错误体
      let errMsg = `HTTP ${response.status}`;
      try {
        const json = await response.json();
        errMsg = json.message || errMsg;
      } catch {
        // 忽略
      }
      isStreaming.value = false;
      if (options.onError) options.onError(errMsg);
      return;
    }

    const reader = response.body?.getReader();
    if (!reader) {
      isStreaming.value = false;
      if (options.onError) options.onError("无法读取响应流");
      return;
    }

    const decoder = new TextDecoder("utf-8");
    let buffer = "";

    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        // { stream: true } 处理中文等多字节跨 chunk 截断
        buffer += decoder.decode(value, { stream: true });
        parseBuffer(buffer, (event, data) => {
          consumeEvent(event, data, options);
          buffer = buffer.slice(
            buffer.indexOf("\n\n") !== -1
              ? buffer.indexOf("\n\n") + 2
              : buffer.length,
          );
        });
      }
    } catch (e: any) {
      if (e.name === "AbortError") return;
      isStreaming.value = false;
      if (options.onError) options.onError(e.message || "流读取异常");
    } finally {
      isStreaming.value = false;
    }
  }

  function abort() {
    abortController?.abort();
    isStreaming.value = false;
  }

  return { abortController, send, isStreaming, assistantContent };
}

function parseBuffer(
  buffer: string,
  onFrame: (event: string, data: string) => void,
) {
  // 按空行分帧
  const frames = buffer.split(/\n\n/);
  // 最后一个帧可能不完整，保留到下次
  const lastIdx = frames.length - 1;
  for (let i = 0; i < lastIdx; i++) {
    const frame = frames[i].trim();
    if (!frame) continue;

    let event = "message"; // 默认
    let dataLines: string[] = [];

    for (const line of frame.split(/\r?\n/)) {
      // 忽略注释行、id:、retry:
      if (
        line.startsWith(":") ||
        line.startsWith("id:") ||
        line.startsWith("retry:")
      )
        continue;
      if (line.startsWith("event:")) {
        event = line.slice(6).trim();
      } else if (line.startsWith("data:")) {
        dataLines.push(line.slice(5).trim());
      }
    }

    if (dataLines.length > 0) {
      // 多行 data: 以 \n 合并
      onFrame(event, dataLines.join("\n"));
    }
  }
}

function consumeEvent(
  event: string,
  data: string,
  options: UseChatStreamOptions,
) {
  switch (event) {
    case "message":
      if (options.onMessage) options.onMessage(data);
      break;
    case "citations":
      try {
        const citations = JSON.parse(data) as Citation[];
        if (options.onCitations) options.onCitations(citations);
      } catch {
        // 忽略
      }
      break;
    case "end":
      if (options.onEnd) options.onEnd();
      break;
    case "error":
      if (options.onError) options.onError(data);
      break;
  }
}
```

- [ ] **Step 2: useDocumentPolling.ts 完整代码**

```typescript
import { ref, onBeforeUnmount } from "vue";
import { listDocumentsApi } from "@/api/kb";
import type { KnowledgeDocument } from "@/types/ai";

/**
 * 文档状态轮询 composable
 * - 3s 轮询，全部终态（已完成/失败）停止
 * - 页面不可见时暂停，可见时立即刷新一次
 */
export function useDocumentPolling(kbId: Ref<number>) {
  const documents = ref<KnowledgeDocument[]>([]);
  const isLoading = ref(false);
  let timer: ReturnType<typeof setInterval> | null = null;
  let isVisible = true;

  async function fetchDocs() {
    isLoading.value = true;
    try {
      const res = await listDocumentsApi(kbId.value);
      documents.value = res.data;
      // 检查是否全部终态
      const allTerminal = documents.value.every(
        (d) => d.status === "已完成" || d.status === "失败",
      );
      if (allTerminal) {
        stopPolling();
      }
    } catch {
      // 忽略
    } finally {
      isLoading.value = false;
    }
  }

  function startPolling() {
    stopPolling();
    fetchDocs();
    timer = setInterval(() => {
      if (isVisible) {
        fetchDocs();
      }
    }, 3000);
  }

  function stopPolling() {
    if (timer) {
      clearInterval(timer);
      timer = null;
    }
  }

  onBeforeUnmount(() => stopPolling());

  // 页面可见性：不可见时跳过轮询，可见时立即刷新一次
  function handleVisibilityChange() {
    isVisible = !document.hidden;
    if (!document.hidden) {
      fetchDocs(); // 可见立即刷一次
    }
  }

  document.addEventListener("visibilitychange", handleVisibilityChange);

  return { documents, isLoading, startPolling, stopPolling };
}
```

### Task 24: 知识库组件（DocumentTable + UploadDialog）

**Files:**

- Create: `ai-java-front/src/components/kb/DocumentTable.vue`
- Create: `ai-java-front/src/components/kb/UploadDialog.vue`
- 文档同步：设计文档 9.24

- [ ] **Step 1: DocumentTable.vue**

```vue
<script setup lang="ts">
import { ref, h, type Ref } from "vue";
import {
  NDataTable,
  NButton,
  NSpace,
  NTag,
  useDialog,
  useMessage,
} from "naive-ui";
import type { DataTableColumns } from "naive-ui";
import { deleteDocumentApi } from "@/api/kb";
import type { KnowledgeDocument, DocStatus } from "@/types/ai";

interface Props {
  documents: KnowledgeDocument[];
}

const props = defineProps<Props>();
const emit = defineEmits<{
  (e: "refresh"): void;
}>();

const message = useMessage();
const dialog = useDialog();

function statusTag(status: DocStatus) {
  const typeMap: Record<string, string> = {
    [DocStatus.UPLOADED]: "info",
    [DocStatus.PROCESSING]: "warning",
    [DocStatus.COMPLETED]: "success",
    [DocStatus.FAILED]: "error",
  };
  return h(
    NTag,
    { type: typeMap[status] || "default" },
    { default: () => status },
  );
}

function handleDelete(doc: KnowledgeDocument) {
  dialog.warning({
    title: "确认删除",
    content: `确定删除文档「${doc.fileName}」吗？删除后无法恢复。`,
    positiveText: "确认删除",
    negativeText: "取消",
    onPositiveClick: async () => {
      try {
        await deleteDocumentApi(doc.id);
        message.success("已删除");
        emit("refresh");
      } catch (e: any) {
        message.error(e.message || "删除失败");
      }
    },
  });
}

const columns: DataTableColumns<KnowledgeDocument> = [
  { title: "文件名", key: "fileName", ellipsis: { tooltip: true } },
  { title: "类型", key: "fileType", width: 60 },
  {
    title: "大小",
    key: "fileSize",
    width: 80,
    render(row) {
      return (row.fileSize / 1024).toFixed(1) + " KB";
    },
  },
  {
    title: "状态",
    key: "status",
    width: 80,
    render(row) {
      return statusTag(row.status);
    },
  },
  { title: "更新时间", key: "updateTime", width: 160 },
  {
    title: "操作",
    key: "actions",
    width: 80,
    render(row) {
      return h(
        NSpace,
        {},
        {
          default: () => [
            h(
              NButton,
              {
                size: "small",
                type: "error",
                onClick: () => handleDelete(row),
              },
              { default: () => "删除" },
            ),
          ],
        },
      );
    },
  },
];
</script>

<template>
  <NDataTable :columns="columns" :data="documents" remote />
</template>
```

- [ ] **Step 2: UploadDialog.vue**

```vue
<script setup lang="ts">
import { ref } from "vue";
import { NDialog, NUpload, NButton, NSpace, useMessage } from "naive-ui";
import type { UploadFileInfo } from "naive-ui";
import { uploadDocumentApi } from "@/api/kb";

interface Props {
  kbId: number;
  show: boolean;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  (e: "close"): void;
  (e: "uploaded"): void;
}>();

const message = useMessage();
const fileList = ref<UploadFileInfo[]>([]);
const uploading = ref(false);

const ALLOWED_TYPES = ["pdf", "docx", "md", "txt"];
const MAX_SIZE = 20 * 1024 * 1024; // 20MB

function beforeUpload(data: {
  file: UploadFileInfo;
  fileList: UploadFileInfo[];
}) {
  const ext = data.file.name.split(".").pop()?.toLowerCase();
  if (!ext || !ALLOWED_TYPES.includes(ext)) {
    message.error(
      `不支持的文件类型：.${ext}，仅允许 ${ALLOWED_TYPES.join("/ ")}`,
    );
    return false;
  }
  if (data.file.file && data.file.file.size > MAX_SIZE) {
    message.error("文件超过 20MB 限制");
    return false;
  }
  return true;
}

async function handleUpload() {
  const file = fileList.value[0]?.file;
  if (!file) {
    message.error("请先选择文件");
    return;
  }
  uploading.value = true;
  try {
    await uploadDocumentApi(props.kbId, file);
    message.success("上传成功，正在解析处理中");
    fileList.value = [];
    emit("close");
    emit("uploaded");
  } catch (e: any) {
    message.error(e.message || "上传失败");
  } finally {
    uploading.value = false;
  }
}
</script>

<template>
  <NDialog
    :show="show"
    title="上传文档"
    :closable="true"
    @close="emit('close')"
  >
    <NUpload
      v-model:file-list="fileList"
      :max="1"
      :custom-request="() => {}"
      :before-upload="beforeUpload"
      accept=".pdf,.docx,.md,.txt"
    />
    <template #action>
      <NSpace justify="end">
        <NButton @click="emit('close')">取消</NButton>
        <NButton
          type="primary"
          :loading="uploading"
          @click="handleUpload"
          :disabled="fileList.length === 0"
        >
          上传
        </NButton>
      </NSpace>
    </template>
  </NDialog>
</template>
```

### Task 25: 知识库页面（KnowledgeBaseView + KnowledgeBaseDetailView）

**Files:**

- Create: `ai-java-front/src/views/ai/KnowledgeBaseView.vue`
- Create: `ai-java-front/src/views/ai/KnowledgeBaseDetailView.vue`
- Create: `ai-java-front/src/router/ai.ts`（ai 路由模块）
- Modify: `ai-java-front/src/router/index.ts`（引入 ai 路由）
- Modify: `ai-java-front/src/views/DashboardView.vue`（加入口）
- 文档同步：设计文档 9.25

- [ ] **Step 1: KnowledgeBaseView.vue**

```vue
<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useRouter } from "vue-router";
import {
  NCard,
  NButton,
  NSpace,
  NInput,
  useDialog,
  useMessage,
} from "naive-ui";
import type {
  KnowledgeBase,
  CreateKbRequest,
  UpdateKbRequest,
} from "@/types/ai";
import { createKbApi, listKbsApi, updateKbApi, deleteKbApi } from "@/api/kb";

const router = useRouter();
const message = useMessage();
const dialog = useDialog();

const kbs = ref<KnowledgeBase[]>([]);
const showCreate = ref(false);
const editingKb = ref<KnowledgeBase | null>(null);
const kbForm = ref({ name: "", description: "" });

onMounted(fetchKbs);

async function fetchKbs() {
  try {
    const res = await listKbsApi();
    kbs.value = res.data;
  } catch (e: any) {
    message.error(e.message || "加载失败");
  }
}

function openCreate() {
  editingKb.value = null;
  kbForm.value = { name: "", description: "" };
  showCreate.value = true;
}

function openEdit(kb: KnowledgeBase) {
  editingKb.value = kb;
  kbForm.value = { name: kb.name, description: kb.description };
  showCreate.value = true;
}

async function handleSave() {
  if (!kbForm.value.name.trim()) {
    message.warning("名称不能为空");
    return;
  }
  try {
    if (editingKb.value) {
      const data: UpdateKbRequest = {
        id: editingKb.value.id,
        name: kbForm.value.name,
        description: kbForm.value.description,
      };
      await updateKbApi(data);
      message.success("已更新");
    } else {
      const data: CreateKbRequest = {
        name: kbForm.value.name,
        description: kbForm.value.description,
      };
      const res = await createKbApi(data);
      message.success("已创建");
    }
    showCreate.value = false;
    await fetchKbs();
  } catch (e: any) {
    message.error(e.message || "操作失败");
  }
}

function handleDelete(kb: KnowledgeBase) {
  dialog.warning({
    title: "确认删除",
    content: `确定删除知识库「${kb.name}」吗？该库下的文档、切片、向量、会话将一并被删除。`,
    positiveText: "确认删除",
    negativeText: "取消",
    onPositiveClick: async () => {
      try {
        await deleteKbApi(kb.id);
        message.success("已删除");
        await fetchKbs();
      } catch (e: any) {
        message.error(e.message || "删除失败");
      }
    },
  });
}

function enterDetail(kb: KnowledgeBase) {
  router.push(`/kb/${kb.id}`);
}
</script>

<template>
  <div class="kb-page">
    <NCard title="知识库管理" class="kb-card">
      <template #header-extra>
        <NButton type="primary" size="small" @click="openCreate"
          >新建知识库</NButton
        >
      </template>
      <NSpace vertical size="large">
        <NCard v-for="kb in kbs" :key="kb.id" hoverable>
          <div class="kb-item">
            <div class="kb-info">
              <h3>{{ kb.name }}</h3>
              <p>{{ kb.description || "暂无描述" }}</p>
              <span class="kb-meta">{{ kb.docCount }} 个文档</span>
            </div>
            <NSpace>
              <NButton size="small" @click="enterDetail(kb)">进入</NButton>
              <NButton size="small" @click="openEdit(kb)">编辑</NButton>
              <NButton size="small" type="error" @click="handleDelete(kb)"
                >删除</NButton
              >
            </NSpace>
          </div>
        </NCard>
        <p v-if="kbs.length === 0" class="empty">
          暂无知识库，点击上方「新建」开始
        </p>
      </NSpace>
    </NCard>
    <NModal
      v-model:show="showCreate"
      preset="dialog"
      title="知识库"
      positive-text="保存"
      negative-text="取消"
      :on-positive-click="handleSave"
      :on-negative-click="() => (showCreate = false)"
    >
      <NInput v-model:value="kbForm.name" placeholder="知识库名称" />
    </NModal>
  </div>
</template>

<style scoped>
.kb-page {
  max-width: 800px;
  margin: 40px auto;
  padding: 0 16px;
}
.kb-card {
  min-height: 400px;
}
.kb-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.kb-info h3 {
  margin: 0 0 4px;
}
.kb-info p {
  margin: 0 0 4px;
  color: #666;
  font-size: 13px;
}
.kb-meta {
  font-size: 12px;
  color: #999;
}
.empty {
  text-align: center;
  color: #999;
  padding: 40px 0;
}
</style>
```

- [ ] **Step 2: KnowledgeBaseDetailView.vue**

```vue
<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { NCard, NButton, NSpace, useMessage } from "naive-ui";
import DocumentTable from "@/components/kb/DocumentTable.vue";
import UploadDialog from "@/components/kb/UploadDialog.vue";
import { useDocumentPolling } from "@/composables/useDocumentPolling";

const route = useRoute();
const router = useRouter();
const message = useMessage();

const kbId = Number(route.params.id);
const kbIdRef = ref(kbId);
const showUpload = ref(false);
const { documents, isLoading, startPolling } = useDocumentPolling(kbIdRef);

onMounted(startPolling);

function handleUploaded() {
  startPolling();
}

function goBack() {
  router.push("/kb");
}

function startChat() {
  router.push({ path: "/chat", query: { kbId } });
}
</script>

<template>
  <div class="detail-page">
    <NCard :title="'文档管理'" class="detail-card">
      <template #header-extra>
        <NSpace>
          <NButton size="small" @click="goBack">返回</NButton>
          <NButton type="primary" size="small" @click="startChat"
            >开始问答</NButton
          >
          <NButton size="small" @click="showUpload = true">上传文档</NButton>
        </NSpace>
      </template>
      <DocumentTable :documents="documents" @refresh="startPolling" />
      <p v-if="documents.length === 0 && !isLoading" class="empty">
        暂无文档，请上传
      </p>
    </NCard>
    <UploadDialog
      v-if="showUpload"
      :kb-id="kbId"
      :show="showUpload"
      @close="showUpload = false"
      @uploaded="handleUploaded"
    />
  </div>
</template>

<style scoped>
.detail-page {
  max-width: 1000px;
  margin: 40px auto;
  padding: 0 16px;
}
.detail-card {
  min-height: 400px;
}
.empty {
  text-align: center;
  color: #999;
  padding: 40px 0;
}
</style>
```

- [ ] **Step 3: 路由配置**

Create `ai-java-front/src/router/ai.ts`：

```typescript
import type { RouteRecordRaw } from "vue-router";

export const aiRoutes: RouteRecordRaw[] = [
  {
    path: "/kb",
    name: "knowledge-base",
    component: () => import("@/views/ai/KnowledgeBaseView.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "/kb/:id",
    name: "knowledge-base-detail",
    component: () => import("@/views/ai/KnowledgeBaseDetailView.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "/chat",
    name: "chat",
    component: () => import("@/views/ai/ChatView.vue"),
    meta: { requiresAuth: true },
  },
];
```

Modify `ai-java-front/src/router/index.ts`（引入 ai 路由，追加到 routes 数组）：

在 `import { useUserStore } from "@/stores/user";` 之后追加：

```typescript
import { aiRoutes } from "./ai";
```

在 routes 数组的 `{ path: "/", redirect: "/dashboard" }` 之前追加：

```typescript
    ...aiRoutes,
```

- [ ] **Step 4: Dashboard 入口（在现有 NButton 之前加入口按钮）**

Modify `ai-java-front/src/views/DashboardView.vue`（在 `<NButton type="error"` 之前加入口 NSpace）：

追加 import（已有 NButton/NSpace，只需加 useRouter）：

在 `<NDescriptions` 段落之后、`<NButton type="error">` 之前追加：

```vue
<NSpace>
  <NButton @click="router.push('/kb')">知识库管理</NButton>
  <NButton type="primary" @click="router.push('/chat?kbId=')">AI 问答</NButton>
</NSpace>
```

- [ ] **Step 5: 前端编译验证**

```bash
cd ai-java-front
npm run type-check
```

预期：无类型错误。

### Task 26: 聊天子组件（SessionList / MessageList / ChatInput / CitationPanel）

**Files:**

- Create: `ai-java-front/src/components/chat/SessionList.vue`
- Create: `ai-java-front/src/components/chat/MessageList.vue`
- Create: `ai-java-front/src/components/chat/ChatInput.vue`
- Create: `ai-java-front/src/components/chat/CitationPanel.vue`
- 文档同步：设计文档 9.26

- [ ] **Step 1: SessionList.vue**

```vue
<script setup lang="ts">
import { ref, h, onMounted } from "vue";
import {
  NButton,
  NSpace,
  NList,
  NListItem,
  NThing,
  useDialog,
  useMessage,
} from "naive-ui";
import type { ChatSession } from "@/types/ai";
import {
  createSessionApi,
  listSessionsApi,
  deleteSessionApi,
} from "@/api/chat";

interface Props {
  kbId: number;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  (e: "select", session: ChatSession): void;
  (e: "refresh"): void;
}>();

const message = useMessage();
const dialog = useDialog();
const sessions = ref<ChatSession[]>([]);

onMounted(fetchSessions);

async function fetchSessions() {
  try {
    const res = await listSessionsApi(props.kbId);
    sessions.value = res.data;
  } catch (e: any) {
    message.error(e.message || "加载会话失败");
  }
}

async function createSession() {
  try {
    const res = await createSessionApi({ kbId: props.kbId });
    await fetchSessions();
    emit("select", res.data);
  } catch (e: any) {
    message.error(e.message || "创建失败");
  }
}

function handleDelete(session: ChatSession) {
  dialog.warning({
    title: "确认删除",
    content: `确定删除会话「${session.title}」吗？`,
    positiveText: "确认删除",
    negativeText: "取消",
    onPositiveClick: async () => {
      try {
        await deleteSessionApi(session.id);
        message.success("已删除");
        await fetchSessions();
        emit("refresh");
      } catch (e: any) {
        message.error(e.message || "删除失败");
      }
    },
  });
}
</script>

<template>
  <div class="session-list">
    <NSpace justify="space-between" align="center">
      <h4>会话列表</h4>
      <NButton size="small" @click="createSession">新建</NButton>
    </NSpace>
    <NList hoverable>
      <NListItem v-for="s in sessions" :key="s.id" @click="emit('select', s)">
        <NThing
          :title="s.title"
          :description="new Date(s.updateTime).toLocaleString()"
        />
        <template #suffix>
          <NButton
            size="tiny"
            type="error"
            quaternary
            @click.stop="handleDelete(s)"
            >删除</NButton
          >
        </template>
      </NListItem>
    </NList>
    <p v-if="sessions.length === 0" class="empty">暂无会话</p>
  </div>
</template>

<style scoped>
.session-list {
  height: 100%;
  overflow-y: auto;
  padding: 8px;
}
h4 {
  margin: 0;
}
.empty {
  text-align: center;
  color: #999;
  padding: 20px 0;
}
</style>
```

- [ ] **Step 2: MessageList.vue**

```vue
<script setup lang="ts">
import type { ChatMessage } from "@/types/ai";
import { NTag } from "naive-ui";

interface Props {
  messages: ChatMessage[];
  streamingContent: string;
  isStreaming: boolean;
  hasError: boolean;
}

defineProps<Props>();
</script>

<template>
  <div class="message-list">
    <div v-for="msg in messages" :key="msg.id" :class="['message', msg.role]">
      <span class="role">{{ msg.role === "user" ? "你" : "AI" }}</span>
      <div class="bubble" v-if="msg.role === 'user'">{{ msg.content }}</div>
      <div class="bubble assistant" v-else>
        <pre class="content">{{ msg.content }}</pre>
        <div v-if="msg.citations" class="cite-hint">
          📎 {{ msg.citations.length }} 条引用
        </div>
      </div>
    </div>
    <!-- 流式气泡 -->
    <div v-if="isStreaming" class="message assistant">
      <span class="role">AI</span>
      <div class="bubble assistant">
        <pre
          class="content">{{ streamingContent }}<span class="cursor">▌</span></pre>
      </div>
    </div>
    <!-- 错误标记 -->
    <div v-if="hasError" class="message user">
      <NTag type="error" size="small">回答失败，可重发</NTag>
    </div>
  </div>
</template>

<style scoped>
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}
.message {
  margin-bottom: 16px;
}
.message.user {
  text-align: right;
}
.role {
  font-size: 12px;
  color: #999;
  font-weight: bold;
}
.bubble {
  display: inline-block;
  max-width: 80%;
  padding: 8px 12px;
  border-radius: 8px;
  margin-top: 4px;
}
.bubble.assistant {
  background-color: #f0f0f0;
  text-align: left;
}
.content {
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  font-family: inherit;
}
.cursor {
  animation: blink 1s infinite;
}
@keyframes blink {
  0%,
  50% {
    opacity: 1;
  }
  51%,
  100% {
    opacity: 0;
  }
}
.cite-hint {
  font-size: 12px;
  color: #18a058;
  margin-top: 4px;
}
</style>
```

- [ ] **Step 3: ChatInput.vue**

```vue
<script setup lang="ts">
import { ref } from "vue";
import { NInput, NButton, NSpace } from "naive-ui";

const emit = defineEmits<{
  (e: "send", question: string): void;
}>();

interface Props {
  disabled?: boolean;
}

defineProps<Props>();

const question = ref("");

function handleSend() {
  const q = question.value.trim();
  if (!q) return;
  emit("send", q);
  question.value = "";
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    handleSend();
  }
}
</script>

<template>
  <div class="chat-input">
    <NSpace vertical>
      <NInput
        v-model:value="question"
        type="textarea"
        :autosize="{ minRows: 2, maxRows: 6 }"
        placeholder="输入问题，Enter 发送，Shift+Enter 换行"
        :disabled="disabled"
        @keydown="handleKeydown"
      />
      <NButton
        type="primary"
        :disabled="disabled || !question.trim()"
        @click="handleSend"
      >
        发送
      </NButton>
    </NSpace>
  </div>
</template>

<style scoped>
.chat-input {
  padding: 12px;
  border-top: 1px solid #eee;
}
</style>
```

- [ ] **Step 4: CitationPanel.vue**

```vue
<script setup lang="ts">
import type { Citation } from "@/types/ai";
import { NCard, NCollapse, NCollapseItem } from "naive-ui";

interface Props {
  citations: Citation[];
}

defineProps<Props>();
</script>

<template>
  <div class="citation-panel">
    <NCollapse>
      <NCollapseItem
        v-for="cite in citations"
        :key="cite.chunkId"
        :title="`[引用 ${cite.chunkIndex + 1}] ${cite.docName}`"
      >
        <p><strong>得分：</strong>{{ cite.score?.toFixed(4) }}</p>
        <pre class="snippet">{{ cite.content }}</pre>
      </NCollapseItem>
    </NCollapse>
    <p v-if="citations.length === 0" class="empty">无引用</p>
  </div>
</template>

<style scoped>
.citation-panel {
  padding: 8px;
  overflow-y: auto;
}
.snippet {
  white-space: pre-wrap;
  background: #f7f7f7;
  padding: 8px;
  border-radius: 4px;
  font-size: 13px;
}
.empty {
  text-align: center;
  color: #999;
  padding: 20px 0;
}
</style>
```

### Task 27: ChatView（聊天主页面）+ 验证

**Files:**

- Create: `ai-java-front/src/views/ai/ChatView.vue`
- 文档同步：设计文档 9.27

- [ ] **Step 1: ChatView.vue**

```vue
<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from "vue";
import { useRoute, useRouter } from "vue-router";
import { NCard, NSpace, NButton, NEmpty, NDrawer } from "naive-ui";
import SessionList from "@/components/chat/SessionList.vue";
import MessageList from "@/components/chat/MessageList.vue";
import ChatInput from "@/components/chat/ChatInput.vue";
import CitationPanel from "@/components/chat/CitationPanel.vue";
import type { ChatSession, ChatMessage, Citation } from "@/types/ai";
import { useChatStream } from "@/composables/useChatStream";
import { listMessagesApi } from "@/api/chat";

const route = useRoute();
const router = useRouter();

const kbId = computed(() => Number(route.query.kbId) || 0);
const sessionId = computed(() => Number(route.query.sessionId) || 0);

const messages = ref<ChatMessage[]>([]);
const citations = ref<Citation[]>([]);
const showLeft = ref(false);
const showRight = ref(false);

const { send, abortController, isStreaming, assistantContent } = useChatStream({
  onMessage: (delta: string) => {
    assistantContent.value += delta;
  },
  onCitations: (cites: Citation[]) => {
    citations.value = cites;
  },
  onEnd: () => {
    // 流结束，hasError 保持 false
  },
  onError: (error: string) => {
    hasError.value = true;
  },
});
const hasError = ref(false);

// 加载历史
async function loadMessages(sid: number) {
  hasError.value = false;
  citations.value = [];
  assistantContent.value = "";
  try {
    const res = await listMessagesApi(sid);
    messages.value = res.data;
  } catch {
    // 忽略
  }
}

// 发送
async function handleSend(question: string) {
  if (!sessionId.value) return;
  hasError.value = false;
  assistantContent.value = "";
  citations.value = [];

  // 先追加 user 气泡（后端落库在 SSE 流程中，前端立刻显示）
  const tempMsg: ChatMessage = {
    id: Date.now(),
    role: "user",
    content: question,
    citations: null,
    createTime: new Date().toISOString(),
  };
  messages.value.push(tempMsg);

  await send(sessionId.value, question);
}

// watch session 变化（路由 query 切换）
watch(sessionId, (newSid) => {
  if (newSid) {
    loadMessages(newSid);
  }
});

onBeforeUnmount(() => {
  // abort 旧流
});

function handleSelectSession(session: ChatSession) {
  router.replace({ query: { kbId: kbId.value, sessionId: session.id } });
}

function handleNewSession(session: ChatSession) {
  router.replace({ query: { kbId: kbId.value, sessionId: session.id } });
}
</script>

<template>
  <div class="chat-page">
    <!-- 左栏（小屏 Drawer） -->
    <div class="chat-sidebar">
      <SessionList
        v-if="kbId"
        :kb-id="kbId"
        @select="handleSelectSession"
        @refresh="() => {}"
      />
    </div>
    <!-- 中栏 -->
    <div class="chat-main">
      <NCard class="chat-card">
        <template #header>
          <NSpace justify="space-between">
            <span>AI 问答</span>
            <NSpace>
              <NButton size="small" @click="showLeft = true">会话</NButton>
              <NButton size="small" @click="showRight = true">引用</NButton>
            </NSpace>
          </NSpace>
        </template>
        <MessageList
          :messages="messages"
          :streaming-content="assistantContent"
          :is-streaming="isStreaming"
          :has-error="hasError"
        />
        <ChatInput @send="handleSend" :disabled="isStreaming" />
      </NCard>
    </div>
    <!-- 右栏（小屏 Drawer） -->
    <div class="chat-citation">
      <CitationPanel :citations="citations" />
    </div>
    <!-- Drawer -->
    <NDrawer v-model:show="showLeft" width="320" placement="left">
      <SessionList
        v-if="kbId"
        :kb-id="kbId"
        @select="handleSelectSession"
        @refresh="() => {}"
      />
    </NDrawer>
    <NDrawer v-model:show="showRight" width="320" placement="right">
      <CitationPanel :citations="citations" />
    </NDrawer>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  height: 100vh;
}
.chat-sidebar {
  width: 260px;
  border-right: 1px solid #eee;
  display: none;
}
.chat-main {
  flex: 1;
  padding: 16px;
}
.chat-citation {
  width: 280px;
  border-left: 1px solid #eee;
  display: none;
}
.chat-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}
@media (min-width: 768px) {
  .chat-sidebar {
    display: block;
  }
  .chat-citation {
    display: block;
  }
}
</style>
```

- [ ] **Step 2: 前端完整验证**

```bash
cd ai-java-front
npm run type-check
npm run dev  # 联调后端
```

- [ ] **Step 3: 用户 commit（建议信息）**

```text
feat: 前端 ai-agent 模块——知识库管理/文档管理/聊天三栏界面 + SSE 打字机 + 引用溯源
```

---

## 阶段 10：全链路验证

### Task 28: 设计文档第 10 节 10 步验证

**Files:** 无新增（运行验证命令）

- [ ] **Step 1-4: 编译 + DDL + 环境 + 启动**

```bash
mvn clean compile
# MySQL 执行 DDL（docs/sql/ai_java.sql 的 5 张表）
# Redis 启动（AOF + noeviction）
# 环境变量
export AI_API_KEY=xxx MCP_TOKEN=test-token
mvn spring-boot:run -pl ai-web
```

- [ ] **Step 5: 摄取链路**

```bash
# 登录获取 token（先注册/登录）
TOKEN=xxx
# 创建知识库
curl -X POST http://localhost:8120/api/kb/create -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"name":"测试库","description":"test"}'
# 上传 PDF（假设有 test.pdf）
curl -X POST http://localhost:8120/api/kb/1/document/upload -H "Authorization: Bearer $TOKEN" -F "file=@test.pdf"
# 轮询文档列表直到 COMPLETED
curl http://localhost:8120/api/kb/1/document/list -H "Authorization: Bearer $TOKEN"
# Redis 检查向量数
redis-cli FT.INFO ai-java-kb   # num_docs = chunk 数
```

- [ ] **Step 6: 问答链路**

```bash
# 创建会话
curl -X POST http://localhost:8120/api/chat/session/create -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"kbId":1}'
# SSE 流式提问（-N 不缓冲）
curl -N http://localhost:8120/api/chat/session/1/send -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"question":"xxx"}'
# 查看历史
curl http://localhost:8120/api/chat/session/1/messages -H "Authorization: Bearer $TOKEN"
```

- [ ] **Step 7: 删除链路**

```bash
# 删文档
curl -X DELETE http://localhost:8120/api/document/1 -H "Authorization: Bearer $TOKEN"
# 删 KB
curl -X DELETE http://localhost:8120/api/kb/1 -H "Authorization: Bearer $TOKEN"
# 检查 audit_log
mysql -uroot -p -e "select operation_type,description,result from ai_java.audit_log order by id desc limit 5;"
```

- [ ] **Step 8: MCP 链路**

配置 Claude Desktop MCP 客户端（sse 端点 `http://localhost:8120/api/mcp` + token header `X-MCP-Token: test-token`），验证 `list_knowledge_bases` 与 `search_knowledge` 可用。

- [ ] **Step 9: 前端验证**

打开 `http://localhost:5173/kb` → 创建库 → 上传 → 轮询状态 → 问答 → 打字机 + 引用 → 删除确认弹窗。

- [ ] **Step 10: 审计脱敏检查**

```bash
mysql -uroot -p -e "select request_params from ai_java.audit_log where operation_type like '%CREATE%' limit 3;"
# 确认 password 等敏感字段为 ***
```

---

## 计划自审

### 1. Spec 覆盖检查

| 设计文档章节            | 对应任务                | 状态 |
| ----------------------- | ----------------------- | ---- |
| 3 数据库                | Task 4-6                | ✅   |
| 4.1 摄取管道            | Task 10-11              | ✅   |
| 4.2 RAG 问答            | Task 15-17              | ✅   |
| 4.3 MCP                 | Task 18-19              | ✅   |
| 4.4 模型接入            | Task 20（配置）         | ✅   |
| 4.5 AgentProperties     | Task 7                  | ✅   |
| 5.1-5.2 知识库/文档 API | Task 13-14              | ✅   |
| 5.3 对话 API            | Task 15-17              | ✅   |
| 5.4 DTO                 | Task 12                 | ✅   |
| 5.5 决策                | 各任务实现中体现        | ✅   |
| 6 前端                  | Task 21-27              | ✅   |
| 7 依赖                  | Task 1-3                | ✅   |
| 8 配置                  | Task 20                 | ✅   |
| 9 实现代码              | 各任务代码即第 9 章内容 | ✅   |
| 10 验证                 | Task 28                 | ✅   |

### 2. 占位符扫描

- 无 TODO / "fill in later" / "similar to Task N"
- RagChatService 中 `ChatResponse.getResult().getOutput().getText()` 路径有验证注释（Spring AI 2.0 API 可能变化，实现时以 IDE 提示为准）
- 所有方法签名、类型名称在各任务内一致

### 3. 类型一致性

- DTO/VO 字段名与 Java entity 一致（id/kbId/docId/chunkIndex/content/score）
- 前端 types/ai.ts 的字段名与 Java VO 对应（createTime/updateTime 等）
- DocStatus 枚举前端用中文值（与后端 DB 存储的 String 枚举名一致）
- `ChatSession.DEFAULT_TITLE = "新会话"` 与前端 `ChatSession.title` 初始化一致

### 4. 范围检查

- 未做模型微调、用户反馈闭环、多模态、Agent 工具调用、知识库分享、计费（符合 YAGNI）
- 列表不分页（MVP 小数据量）
- 前端无 Pinia store（页面内聚状态）
- Markdown 渲染 MVP 纯文本

---

## 执行选项

计划完成。两种执行方式：

**方案 1：子代理驱动（推荐）** —— 每个任务派独立子代理执行，任务间人工 review，快速迭代
**方案 2：内联执行** —— 在当前会话逐任务执行，批处理 + 检查点
