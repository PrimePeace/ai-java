# ai-basic 子模块改造实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers (subagent-driven-development recommended, or executing-plans) to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将单模块 ai-java 改造为聚合 POM + ai-basic（通用基础库）+ ai-web（应用模块）的 Maven 多模块结构。

**Architecture:** 根 POM 变聚合（packaging=pom）统一管版本；11 个基础类用 `git mv` 迁入 ai-basic（包名保持 `com.ai.aijava.*`，Java 源码零改动）；其余应用代码整体迁入 ai-web。纯结构迁移无新业务逻辑，验证方式为编译/依赖树/打包/启动/接口（不适用 TDD）。

**Tech Stack:** Java 21、Spring Boot 4.1.0、Maven 多模块、Lombok。

**设计文档:** `docs/superpowers/specs/2026-09-12-ai-basic-module-design.md`

**执行前置条件:**

- 工作区干净（`git status --short` 无输出）
- 所有命令在仓库根目录 `E:\ai-java\ai-java` 执行（bash shell）
- Task 1-5 为结构调整阶段，中间状态不可编译属预期；**禁止在 Task 5 完成前执行任何 mvn 编译命令**

---

### Task 1: 迁移 11 个基础类到 ai-basic

**Files:**

- Create: `ai-basic/src/main/java/com/ai/aijava/`（目录）
- Move: `src/main/java/com/ai/aijava/{annotation,common,context,exception,utils}` → `ai-basic/src/main/java/com/ai/aijava/`

- [ ] **Step 1: 创建 ai-basic 包目录**

```bash
mkdir -p ai-basic/src/main/java/com/ai/aijava
```

- [ ] **Step 2: git mv 五个基础包（11 个类）**

```bash
git mv src/main/java/com/ai/aijava/annotation  ai-basic/src/main/java/com/ai/aijava/annotation
git mv src/main/java/com/ai/aijava/common      ai-basic/src/main/java/com/ai/aijava/common
git mv src/main/java/com/ai/aijava/context     ai-basic/src/main/java/com/ai/aijava/context
git mv src/main/java/com/ai/aijava/exception   ai-basic/src/main/java/com/ai/aijava/exception
git mv src/main/java/com/ai/aijava/utils       ai-basic/src/main/java/com/ai/aijava/utils
```

- [ ] **Step 3: 验证迁移结果（11 个文件 + 原 src 对应目录消失）**

```bash
find ai-basic/src -name "*.java" | sort
```

预期输出（精确 11 行）:

```
ai-basic/src/main/java/com/ai/aijava/annotation/RequireLogin.java
ai-basic/src/main/java/com/ai/aijava/common/BaseResponse.java
ai-basic/src/main/java/com/ai/aijava/common/DeleteRequest.java
ai-basic/src/main/java/com/ai/aijava/common/ResultUtils.java
ai-basic/src/main/java/com/ai/aijava/context/UserContext.java
ai-basic/src/main/java/com/ai/aijava/exception/BusinessException.java
ai-basic/src/main/java/com/ai/aijava/exception/ErrorCode.java
ai-basic/src/main/java/com/ai/aijava/exception/GlobalExceptionHandler.java
ai-basic/src/main/java/com/ai/aijava/exception/ThrowUtils.java
ai-basic/src/main/java/com/ai/aijava/utils/BCryptUtils.java
ai-basic/src/main/java/com/ai/aijava/utils/JwtUtils.java
```

```bash
ls src/main/java/com/ai/aijava/
```

预期输出（不再含 annotation/common/context/exception/utils）:

```
AiJavaApplication.java
config
controller
dto
entity
interceptor
mapper
service
```

### Task 2: 写入 ai-basic/pom.xml

**Files:**

- Create: `ai-basic/pom.xml`

- [ ] **Step 1: 创建 `ai-basic/pom.xml`，内容如下（完整文件）**

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

    <artifactId>ai-basic</artifactId>
    <name>ai-basic</name>
    <description>通用基础库：统一响应、异常体系、工具类、上下文</description>

    <dependencies>
        <!-- GlobalExceptionHandler 的 @RestControllerAdvice -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>

        <!-- swagger 纯注解包（@Hidden） -->
        <dependency>
            <groupId>io.swagger.core.v3</groupId>
            <artifactId>swagger-annotations-jakarta</artifactId>
        </dependency>

        <!-- JwtUtils -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
        </dependency>

        <!-- BCryptUtils（仅加密算法，不引入完整 Security 过滤链） -->
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-crypto</artifactId>
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

### Task 3: 迁移应用代码到 ai-web

**Files:**

- Create: `ai-web/`（目录）
- Move: `src` → `ai-web/src`

- [ ] **Step 1: 创建 ai-web 目录**

```bash
mkdir -p ai-web
```

- [ ] **Step 2: git mv 整个 src 到 ai-web**

```bash
git mv src ai-web/src
```

- [ ] **Step 3: 验证迁移结果**

```bash
ls ai-web/src/main/java/com/ai/aijava/ && ls
```

预期输出:

```
AiJavaApplication.java
config
controller
dto
entity
interceptor
mapper
service
---
ai-basic
ai-java-front
ai-web
CLAUDE.md
docs
mvnw
mvnw.cmd
pom.xml
（以及其他原有根目录文件，src 不再存在于根目录）
```

### Task 4: 写入 ai-web/pom.xml

**Files:**

- Create: `ai-web/pom.xml`

- [ ] **Step 1: 创建 `ai-web/pom.xml`，内容如下（完整文件）**

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

    <artifactId>ai-web</artifactId>
    <name>ai-web</name>
    <description>ai-java 应用模块（可执行 jar）</description>

    <dependencies>
        <!-- 内部通用基础库 -->
        <dependency>
            <groupId>com.ai</groupId>
            <artifactId>ai-basic</artifactId>
        </dependency>

        <!-- web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>

        <!-- Spring Security (BCrypt + 安全基线) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <!-- JWT -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- mysql -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- 数据库连接池 -->
        <dependency>
            <groupId>com.zaxxer</groupId>
            <artifactId>HikariCP</artifactId>
        </dependency>

        <!-- Spring Data Redis -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- Redis 连接池 -->
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-pool2</artifactId>
        </dependency>

        <!-- Hutool 工具库 -->
        <dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
        </dependency>

        <!-- knife4j 接口文档 -->
        <dependency>
            <groupId>com.github.xiaoymin</groupId>
            <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
        </dependency>

        <!-- mybatis-flex -->
        <dependency>
            <groupId>com.mybatis-flex</groupId>
            <artifactId>mybatis-flex-spring-boot3-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mybatis-flex</groupId>
            <artifactId>mybatis-flex-codegen</artifactId>
        </dependency>

        <!-- 切面编程 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>

        <!-- caffeine 本地缓存 -->
        <dependency>
            <groupId>com.github.ben-manes.caffeine</groupId>
            <artifactId>caffeine</artifactId>
        </dependency>

        <!-- lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
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

**注意**: 与原根 POM 的差异——`spring-boot-starter-aop` 移除显式 `<version>3.0.5</version>`（与 Boot 4.1 冲突的既有隐患，统一由 parent 管理）；新增 ai-basic 依赖；其余依赖声明照搬原 POM。

### Task 5: 改造根 POM 为聚合 POM

**Files:**

- Modify: `pom.xml`（全量替换）

- [ ] **Step 1: 用以下内容全量替换根 `pom.xml`（完整文件）**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.0</version>
        <relativePath/> <!-- lookup parent from repository -->
    </parent>
    <groupId>com.ai</groupId>
    <artifactId>ai-java</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>ai-java</name>
    <description>ai-java</description>
    <url/>
    <licenses>
        <license/>
    </licenses>
    <developers>
        <developer/>
    </developers>
    <scm>
        <connection/>
        <developerConnection/>
        <tag/>
        <url/>
    </scm>

    <modules>
        <module>ai-basic</module>
        <module>ai-web</module>
    </modules>

    <properties>
        <java.version>21</java.version>
        <lombok.version>1.18.36</lombok.version>
        <hutool.version>5.8.38</hutool.version>
        <knife4j.version>4.2.0</knife4j.version>
        <mybatis-flex.version>1.11.0</mybatis-flex.version>
        <jjwt.version>0.12.6</jjwt.version>
        <swagger-annotations.version>2.2.9</swagger-annotations.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- 内部模块 -->
            <dependency>
                <groupId>com.ai</groupId>
                <artifactId>ai-basic</artifactId>
                <version>${project.version}</version>
            </dependency>

            <!-- lombok -->
            <dependency>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>${lombok.version}</version>
            </dependency>

            <!-- Hutool 工具库 -->
            <dependency>
                <groupId>cn.hutool</groupId>
                <artifactId>hutool-all</artifactId>
                <version>${hutool.version}</version>
            </dependency>

            <!-- knife4j 接口文档 -->
            <dependency>
                <groupId>com.github.xiaoymin</groupId>
                <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
                <version>${knife4j.version}</version>
            </dependency>

            <!-- mybatis-flex -->
            <dependency>
                <groupId>com.mybatis-flex</groupId>
                <artifactId>mybatis-flex-spring-boot3-starter</artifactId>
                <version>${mybatis-flex.version}</version>
            </dependency>
            <dependency>
                <groupId>com.mybatis-flex</groupId>
                <artifactId>mybatis-flex-codegen</artifactId>
                <version>${mybatis-flex.version}</version>
            </dependency>

            <!-- JWT -->
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-api</artifactId>
                <version>${jjwt.version}</version>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-impl</artifactId>
                <version>${jjwt.version}</version>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-jackson</artifactId>
                <version>${jjwt.version}</version>
            </dependency>

            <!-- swagger 纯注解包（@Hidden），与 knife4j 传递链版本一致 -->
            <dependency>
                <groupId>io.swagger.core.v3</groupId>
                <artifactId>swagger-annotations-jakarta</artifactId>
                <version>${swagger-annotations.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

### Task 6: 编译与依赖验证

- [ ] **Step 1: 全模块编译**

```bash
mvn clean compile
```

预期: `BUILD SUCCESS`，reactor 汇总列出 ai-basic、ai-java (parent)、ai-web 三项均为 SUCCESS。

若 lombok `annotationProcessorPaths` 报"dependencies must include version"类错误: 给 ai-basic 与 ai-web 两个 POM 的 `<path>` 补 `<version>${lombok.version}</version>` 后重试。

- [ ] **Step 2: 依赖树验证（swagger 版本无冲突 + ai-basic 依赖正确）**

```bash
mvn -pl ai-basic dependency:tree
```

预期输出包含（节选）:

```
com.ai:ai-basic:jar:0.0.1-SNAPSHOT
+- org.springframework.boot:spring-boot-starter-webmvc:jar:...
+- io.swagger.core.v3:swagger-annotations-jakarta:jar:2.2.9
+- io.jsonwebtoken:jjwt-api:jar:0.12.6
+- org.springframework.security:spring-security-crypto:jar:...
+- org.projectlombok:lombok:jar:1.18.36
```

```bash
mvn -pl ai-web dependency:tree | grep -E "swagger-annotations|ai-basic|security-crypto"
```

预期输出包含:

```
com.ai:ai-basic:jar:0.0.1-SNAPSHOT:compile
io.swagger.core.v3:swagger-annotations-jakarta:jar:2.2.9:compile
org.springframework.security:spring-security-crypto:jar:...:compile
```

（若 ai-web 树中出现第二个 swagger-annotations 版本则为冲突，需回头修 swagger-annotations.version；正常情况 knife4j 传递链即 2.2.9。）

### Task 7: 打包与启动验证

- [ ] **Step 1: 打包**

```bash
mvn clean package
```

预期: `BUILD SUCCESS`；生成 `ai-basic/target/ai-basic-0.0.1-SNAPSHOT.jar`（普通 jar）与 `ai-web/target/ai-web-0.0.1-SNAPSHOT.jar`（可执行 fat jar）。

```bash
ls ai-basic/target/*.jar ai-web/target/*.jar
```

- [ ] **Step 2: 启动应用**

后台运行（需要 MySQL/Redis 可用，与改造前启动条件相同）:

```bash
mvn spring-boot:run -pl ai-web
```

预期: 日志出现 `Started AiJavaApplication`，无 Bean 缺失/类找不到异常。验证后停止进程。

- [ ] **Step 3: 接口验证（应用启动状态下）**

```bash
curl -s http://localhost:8080/api/health
```

预期: 返回 `BaseResponse` 结构 JSON（`{"code":0,...}`；具体路径以 ai-web 中 HealthController 实际映射为准，若 404 则先查 `ai-web/src/main/java/com/ai/aijava/controller/HealthController.java` 的 `@RequestMapping` 值）。

### Task 8: 更新 CLAUDE.md 常用命令

**Files:**

- Modify: `CLAUDE.md`

- [ ] **Step 1: 将「常用命令」章节的「后端（项目根目录）」小节替换为:**

```markdown
### 后端（项目根目录）

- `mvn clean compile` # 清理并编译全模块
- `mvn clean package` # 生产构建（ai-basic 普通 jar + ai-web 可执行 jar）
- `mvn spring-boot:run -pl ai-web` # 启动后端开发服务器（或 cd ai-web && mvn spring-boot:run）
- `mvn test` # 运行测试
```

- [ ] **Step 2: 在 CLAUDE.md「架构概览」的「后端」小节开头（`Spring Boot 4.1 + Vue 3 ...` 之后）补充模块说明:**

```markdown
### 模块结构（Maven 多模块）

- **根 POM**：聚合 + dependencyManagement 统一管版本
- **ai-basic**：通用基础库（统一响应、异常体系、工具类、上下文、注解），包名 `com.ai.aijava.*`
- **ai-web**：应用模块（启动类、controller/service/mapper/entity/dto/config），依赖 ai-basic
```

### Task 9: 提交

- [ ] **Step 1: 检查变更清单**

```bash
git status --short
```

预期: 根 `pom.xml` 修改（M）、`ai-basic/`、`ai-web/` 新增（含 rename 标记 R 的迁移文件）、`CLAUDE.md` 修改（M）。

- [ ] **Step 2: 提交（rename 检测需 add -A 保证历史关联，但排除无关文件）**

```bash
git add -A pom.xml ai-basic ai-web CLAUDE.md
git commit -m "refactor: 拆分 ai-basic 通用基础库子模块，项目改造为 Maven 多模块结构"
```

注意: `git add` 限定路径，不引入 docs 等无关变更；commit message 不添加任何 AI 署名元数据。

- [ ] **Step 3: 确认提交与工作区干净**

```bash
git show --stat HEAD | head -30
git status --short
```

预期: 提交包含全部结构变更；工作区干净。

---

## 自审记录

- **Spec 覆盖**: 设计文档第 4 节三个 POM（Task 2/4/5）、第 5 节迁移步骤（Task 1/3）、第 6 节验证清单（Task 6/7）、CLAUDE.md 更新（Task 8）、提交（Task 9）——全覆盖。
- **占位符**: 无 TBD/TODO；所有代码块为完整文件内容。
- **类型一致性**: artifactId（ai-basic/ai-web/ai-java）、version（0.0.1-SNAPSHOT）、swagger-annotations 2.2.9 在各任务间一致。
