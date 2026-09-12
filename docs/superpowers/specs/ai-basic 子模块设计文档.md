# ai-basic 子模块设计文档

> 日期：2026-09-12
> 状态：已确认
> 目标：将单模块 Spring Boot 项目改造为 Maven 多模块结构，抽出通用基础库 ai-basic

---

## 1. 背景与目标

当前 ai-java 为单模块项目，通用类（统一响应、异常体系、工具类、上下文）与业务代码混在一起。为支持未来多应用模块（如 ai-admin、ai-job）复用通用基础能力，将项目改造为聚合 POM + 双子模块结构。

**核心约束**：

- 所有迁移类保留原包名 `com.ai.aijava.*`，Java 源码零改动（纯物理移动）
- 前端 `ai-java-front/` 与 HTTP 接口契约完全不动
- 不做任何业务逻辑变更

## 2. 目标结构

```
ai-java/                          # 根：聚合 POM（packaging=pom）
├── pom.xml                       #   只管版本 + 聚合，不含代码
├── ai-basic/                     # 通用基础库（普通 jar）
│   ├── pom.xml
│   └── src/main/java/com/ai/aijava/
│       ├── annotation/RequireLogin.java
│       ├── common/BaseResponse.java
│       ├── common/DeleteRequest.java
│       ├── common/ResultUtils.java
│       ├── context/UserContext.java
│       ├── exception/BusinessException.java
│       ├── exception/ErrorCode.java
│       ├── exception/GlobalExceptionHandler.java
│       ├── exception/ThrowUtils.java
│       ├── utils/BCryptUtils.java
│       └── utils/JwtUtils.java
└── ai-web/                       # 应用模块（可执行 jar）
    ├── pom.xml
    └── src/
        ├── main/java/com/ai/aijava/
        │   ├── AiJavaApplication.java
        │   ├── config/CorsConfig.java
        │   ├── config/JwtConfig.java
        │   ├── config/MyBatisFlexConfig.java
        │   ├── config/SecurityConfig.java
        │   ├── config/WebMvcConfig.java
        │   ├── controller/HealthController.java
        │   ├── controller/UserController.java
        │   ├── dto/request/RefreshTokenRequest.java
        │   ├── dto/request/UserLoginRequest.java
        │   ├── dto/request/UserRegisterRequest.java
        │   ├── dto/vo/UserLoginVO.java
        │   ├── dto/vo/UserVO.java
        │   ├── entity/User.java
        │   ├── entity/table/UserTableDef.java
        │   ├── interceptor/JwtInterceptor.java
        │   ├── mapper/UserMapper.java
        │   └── service/UserService.java
        ├── main/resources/（application*.yml 等，全部原样移入）
        └── test/（当前为空，测试文件已在历史提交中删除）
```

**归属说明**：

| 决策                                                  | 理由                                     |
| ----------------------------------------------------- | ---------------------------------------- |
| `JwtInterceptor` 留在 ai-web                          | 耦合 Spring MVC 拦截器注册与业务路由策略 |
| `RequireLogin`、`UserContext`、`JwtUtils` 入 ai-basic | 被拦截器与业务共同依赖的契约类/工具      |
| `GlobalExceptionHandler` 入 ai-basic                  | 通用异常响应格式，所有应用模块复用       |

## 3. Maven 依赖设计

### 3.1 父 POM（ai-java）

- `packaging` 由默认 jar 改为 `pom`
- 继续继承 `spring-boot-starter-parent 4.1.0`
- `<modules>`：ai-basic、ai-web
- 第三方版本号上提到 `<properties>`：lombok 1.18.36、hutool 5.8.38、knife4j 4.2.0、mybatis-flex 1.11.0、jjwt 0.12.6、swagger-annotations-jakarta 2.2.9、spring-security-crypto（由 Boot 管版本，无需 property）
- `<dependencyManagement>` 锁定版本，子模块声明不写版本号
- 移除 `spring-boot-maven-plugin`（父 POM 不打包可执行 jar）

### 3.2 ai-basic 依赖（保持轻量）

| 依赖                                                   | 用途                                              | 与现状对比                                    |
| ------------------------------------------------------ | ------------------------------------------------- | --------------------------------------------- |
| `spring-boot-starter-webmvc`                           | GlobalExceptionHandler 的 `@RestControllerAdvice` | 沿用                                          |
| `io.swagger.core.v3:swagger-annotations-jakarta` 2.2.9 | BaseResponse/GlobalExceptionHandler 的 `@Hidden`  | 替代全量 knife4j starter 传递，纯注入包      |
| `io.jsonwebtoken:jjwt-api` 0.12.6                      | JwtUtils                                          | impl/jackson（runtime）留在 ai-web            |
| `org.springframework.security:spring-security-crypto`  | BCryptUtils 的 `BCryptPasswordEncoder`            | 替代完整 security starter，仅加密算法无过滤链 |
| `lombok`（optional）                                   | 全部类                                            | 沿用                                          |

**版本兼容说明**：knife4j 4.2.0 → springdoc 2.2.0 → swagger-annotations 2.2.9，ai-basic 锁定 2.2.9 与 ai-web 的 knife4j 传递链一致，`mvn dependency:tree` 验证无版本冲突。

### 3.3 ai-web 依赖

- `com.ai:ai-basic:0.0.1-SNAPSHOT`（新增）
- 沿用原有全部应用依赖：spring-boot-starter-webmvc、spring-boot-starter-security、jjwt（api + impl + jackson）、mysql-connector-j（runtime）、HikariCP、spring-boot-starter-data-redis、commons-pool2、hutool-all、knife4j-openapi3-jakarta-spring-boot-starter、mybatis-flex-spring-boot3-starter、mybatis-flex-codegen、spring-boot-starter-aop、caffeine、lombok（optional）、spring-boot-starter-test（test）
- 承接 `spring-boot-maven-plugin`（lombok excludes）与 `maven-compiler-plugin` 的 `annotationProcessorPaths` 配置

## 4. 完整 POM 文件

### 4.1 根 POM

文件路径：`pom.xml`

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

### 4.2 ai-basic POM

文件路径：`ai-basic/pom.xml`

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

### 4.3 ai-web POM

文件路径：`ai-web/pom.xml`

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

**注意**：原 POM 中 `spring-boot-starter-aop` 显式写了 `<version>3.0.5</version>`，与 Boot 4.1 管理版本冲突。新 POM 移除该显式版本号，统一由 spring-boot-starter-parent 管理（修正既有隐患，非功能变更）。

## 5. 迁移步骤

全部使用 `git mv` 保留文件历史，Java 源码零改动。

```bash
# 1. 建目录结构
mkdir -p ai-basic/src/main/java/com/ai/aijava
mkdir -p ai-web

# 2. 迁移基础类 → ai-basic（11 个文件，包名不变）
git mv src/main/java/com/ai/aijava/annotation  ai-basic/src/main/java/com/ai/aijava/annotation
git mv src/main/java/com/ai/aijava/common      ai-basic/src/main/java/com/ai/aijava/common
git mv src/main/java/com/ai/aijava/context     ai-basic/src/main/java/com/ai/aijava/context
git mv src/main/java/com/ai/aijava/exception   ai-basic/src/main/java/com/ai/aijava/exception
git mv src/main/java/com/ai/aijava/utils       ai-basic/src/main/java/com/ai/aijava/utils

# 3. 应用代码整体 → ai-web
git mv src ai-web/src

# 4. 写入三个 POM（父 POM 原地改，ai-basic/ai-web 新建）
```

### CLAUDE.md 命令更新

| 原命令                | 新命令                                                                          |
| --------------------- | ------------------------------------------------------------------------------- |
| `mvn spring-boot:run` | `mvn spring-boot:run -pl ai-web`（根目录）或 `cd ai-web && mvn spring-boot:run` |
| `mvn clean package`   | 不变（根目录聚合构建）                                                          |
| `mvn test`            | 不变                                                                            |

## 6. 验证清单

| 验证项 | 命令/方式                          | 通过标准                                     |
| ------ | ---------------------------------- | -------------------------------------------- |
| 编译   | 根目录 `mvn clean compile`         | 全模块 BUILD SUCCESS                         |
| 依赖树 | `mvn -pl ai-basic dependency:tree` | swagger-annotations 2.2.9 无冲突             |
| 打包   | `mvn clean package`                | ai-web 生成可执行 jar，ai-basic 生成普通 jar |
| 启动   | `mvn spring-boot:run -pl ai-web`   | 应用正常启动，无 Bean 缺失                   |
| 接口   | curl 健康检查接口                  | 响应结构 `BaseResponse` 不变                 |
| 前端   | ai-java-front 联调登录             | 无需任何前端改动                             |

## 7. 风险与应对

| 风险                                                       | 应对                                                                                                  |
| ---------------------------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| lombok `annotationProcessorPaths` 无版本号写法在子模块失效 | 保持与原 POM 完全相同写法（依赖 spring-boot-starter-parent 的依赖管理解析）；若编译报错则显式补版本号 |
| swagger-annotations 与 knife4j 传递版本冲突                | 锁定 2.2.9 与 knife4j 4.2.0 传递链一致，`dependency:tree` 验证                                        |
| BCryptUtils 换 spring-security-crypto 后类缺失             | `spring-security-crypto` 包含 `BCryptPasswordEncoder`，编译即可验证                                   |
| Spring Boot 4.1 starter 命名差异                           | 沿用现有 `spring-boot-starter-webmvc` 命名，不引入猜测                                                |

## 8. 明确不做（YAGNI）

- 不新建 ai-admin 等未来模块
- 不为 ai-basic 编写新单测（原测试文件已删除，保持现状）
- 不修改任何 Java 类的代码逻辑
- 不调整前端项目

## 9. 涉及文件清单

| 操作 | 文件                                                                                              |
| ---- | ------------------------------------------------------------------------------------------------- |
| 修改 | `pom.xml`（根 POM）                                                                               |
| 新增 | `ai-basic/pom.xml`、`ai-web/pom.xml`                                                              |
| 移动 | 11 个基础类 → `ai-basic/src/main/java/com/ai/aijava/{annotation,common,context,exception,utils}/` |
| 移动 | `src/` 整体 → `ai-web/src/`（含 resources、test）                                                 |
| 修改 | `CLAUDE.md`（常用命令章节）                                                                       |
