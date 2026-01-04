# Dropwizard + HK2 + Hibernate 框架快速搭建与入门指南

## 目录

1. [框架介绍](#框架介绍)
2. [项目搭建](#项目搭建)
3. [核心配置](#核心配置)
4. [代码结构](#代码结构)
5. [关键知识点](#关键知识点)
6. [IOC/DI 自动扫描机制](IOCDI%20自动扫描机制详解.md)
7. [最佳实践](#最佳实践)
8. [常见问题](#常见问题)

---

## 框架介绍

### 技术栈组合

- **Dropwizard 4.0.0**: 轻量级 Java Web 服务框架，基于 Jersey + Jetty + Jackson
- **HK2 3.0.4**: 轻量级依赖注入框架，Dropwizard 默认使用
- **Hibernate**: ORM 框架，通过 Dropwizard Hibernate Bundle 集成
- **Lombok 1.18.30**: 减少样板代码
- **MySQL**: 关系型数据库（也支持 H2、PostgreSQL 等）

### 框架特点

- **轻量级**: 相比 Spring Boot，更轻量、启动更快
- **生产就绪**: 内置监控、健康检查、指标收集
- **简单直接**: 约定优于配置，上手快
- **性能优秀**: 基于 Jetty 和 HikariCP 连接池

---

## 项目搭建

### 1. Maven 依赖配置

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.gutou</groupId>
    <artifactId>dw-test-01</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <dropwizard.version>4.0.0</dropwizard.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.dropwizard</groupId>
                <artifactId>dropwizard-bom</artifactId>
                <version>${dropwizard.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <!-- Dropwizard Core -->
        <dependency>
            <groupId>io.dropwizard</groupId>
            <artifactId>dropwizard-core</artifactId>
        </dependency>

        <!-- Dropwizard Hibernate -->
        <dependency>
            <groupId>io.dropwizard</groupId>
            <artifactId>dropwizard-hibernate</artifactId>
        </dependency>

        <!-- HK2 API -->
        <dependency>
            <groupId>org.glassfish.hk2</groupId>
            <artifactId>hk2-api</artifactId>
            <version>3.0.4</version>
        </dependency>

        <!-- HK2 Implementation -->
        <dependency>
            <groupId>org.glassfish.hk2</groupId>
            <artifactId>hk2-locator</artifactId>
            <version>3.0.4</version>
        </dependency>

        <!-- HK2 Utilities -->
        <dependency>
            <groupId>org.glassfish.hk2</groupId>
            <artifactId>hk2-utils</artifactId>
            <version>3.0.4</version>
        </dependency>

        <!-- Reflections for automatic service scanning -->
        <dependency>
            <groupId>org.reflections</groupId>
            <artifactId>reflections</artifactId>
            <version>0.10.2</version>
        </dependency>

        <!-- Jakarta Persistence API (JPA) -->
        <dependency>
            <groupId>jakarta.persistence</groupId>
            <artifactId>jakarta.persistence-api</artifactId>
            <version>3.1.0</version>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>1.18.30</version>
            <scope>provided</scope>
        </dependency>

        <!-- MySQL Driver -->
        <dependency>
            <groupId>mysql</groupId>
            <artifactId>mysql-connector-java</artifactId>
            <version>8.0.33</version>
        </dependency>

        <!-- H2 Database (可选，用于开发测试) -->
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <version>2.2.224</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Maven Shade Plugin - 打包可执行 JAR -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.1</version>
                <configuration>
                    <createDependencyReducedPom>true</createDependencyReducedPom>
                    <transformers>
                        <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
                        <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                            <mainClass>com.gutou.DwTestApplication</mainClass>
                        </transformer>
                    </transformers>
                </configuration>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>

            <!-- Maven Compiler Plugin - 支持 Lombok -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <source>21</source>
                    <target>21</target>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>1.18.30</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 2. 项目目录结构

```
dw-test-01/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/gutou/
│   │   │   ├── DwTestApplication.java      # 主应用类
│   │   │   ├── config/
│   │   │   │   └── DwTestConfiguration.java # 配置类
│   │   │   ├── entity/
│   │   │   │   └── User.java               # JPA 实体类
│   │   │   ├── dao/
│   │   │   │   └── UserDAO.java            # 数据访问层
│   │   │   ├── core/
│   │   │   │   ├── Hk2ServiceBinder.java   # Service 自动绑定工具
│   │   │   │   ├── Hk2DaoBinder.java       # DAO 自动绑定工具
│   │   │   │   └── JerseyResourceRegistrar.java # Resource 自动注册工具
│   │   │   └── resources/
│   │   │       └── UserResource.java       # REST API 端点
│   │   └── resources/
│   │       └── config.yml                  # 应用配置文件
│   └── test/
│       └── java/                           # 测试代码
└── target/                                 # 编译输出
```

---

## 核心配置

### 1. 配置文件 (config.yml)

```yaml
server:
  type: simple
  applicationContextPath: /
  adminContextPath: /admin
  connector:
    type: http
    port: 8080

logging:
  level: INFO
  loggers:
    com.gutou: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE

database:
  # MySQL 数据库配置
  driverClass: com.mysql.cj.jdbc.Driver
  url: jdbc:mysql://localhost:3306/dw_test?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8&useUnicode=true&allowPublicKeyRetrieval=true
  user: root
  password: 1234
  
  # Hibernate 配置
  properties:
    hibernate.dialect: org.hibernate.dialect.MySQLDialect
    hibernate.hbm2ddl.auto: update
    hibernate.show_sql: true
    hibernate.format_sql: true
    hibernate.use_sql_comments: true
```

**配置说明：**

- `hibernate.hbm2ddl.auto`:
  - `update`: 自动更新表结构（添加新字段）
  - `validate`: 只验证，不修改（生产环境推荐）
  - `create`: 每次启动删除重建（开发环境）
  - `create-drop`: 启动创建，关闭删除（测试环境）
  - `none`: 禁用 DDL 操作

- `allowPublicKeyRetrieval=true`: MySQL 8.0+ 需要此参数

### 2. 配置类 (DwTestConfiguration.java)

```java
package com.gutou.config;

import io.dropwizard.core.Configuration;
import io.dropwizard.db.DataSourceFactory;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public class DwTestConfiguration extends Configuration {

    @Valid
    @NotNull
    @JsonProperty("database")
    private DataSourceFactory database = new DataSourceFactory();

    public DataSourceFactory getDataSourceFactory() {
        return database;
    }

    public void setDataSourceFactory(DataSourceFactory dataSourceFactory) {
        this.database = dataSourceFactory;
    }
}
```

---

## 代码结构

### 1. 主应用类 (DwTestApplication.java)

**使用自动扫描机制（推荐）：**

```java
package com.gutou;

import com.gutou.config.DwTestConfiguration;
import com.gutou.entity.User;
import com.gutou.core.Hk2ServiceBinder;
import com.gutou.core.Hk2DaoBinder;
import com.gutou.core.JerseyResourceRegistrar;
import io.dropwizard.core.Application;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.hibernate.HibernateBundle;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.hibernate.SessionFactory;

public class DwTestApplication extends Application<DwTestConfiguration> {

    // Hibernate Bundle - 管理 SessionFactory
    private final HibernateBundle<DwTestConfiguration> hibernateBundle =
            new HibernateBundle<DwTestConfiguration>(User.class) {
                @Override
                public DataSourceFactory getDataSourceFactory(DwTestConfiguration configuration) {
                    return configuration.getDataSourceFactory();
                }
            };

    public static void main(String[] args) throws Exception {
        new DwTestApplication().run(args);
    }

    @Override
    public String getName() {
        return "dw-test-01";
    }

    @Override
    public void initialize(Bootstrap<DwTestConfiguration> bootstrap) {
        bootstrap.addBundle(hibernateBundle);  // 注册 Hibernate Bundle
    }

    @Override
    public void run(DwTestConfiguration configuration, Environment environment) {
        // 获取 Hibernate SessionFactory
        final SessionFactory sessionFactory = hibernateBundle.getSessionFactory();

        // 注册 HK2 绑定（依赖注入）
        environment.jersey().register(new AbstractBinder() {
            @Override
            protected void configure() {
                // 手动绑定的特殊服务（如 SessionFactory 等）
                bind(sessionFactory).to(SessionFactory.class);
            }
        });
        
        // 自动扫描并绑定所有 DAO 类（继承 AbstractDAO 的类）
        environment.jersey().register(new Hk2DaoBinder("com.gutou", sessionFactory));
        
        // 自动扫描并绑定所有 @Service 注解的服务
        environment.jersey().register(new Hk2ServiceBinder("com.gutou"));

        // 自动扫描并注册所有 @Path 注解的 Resource 类
        JerseyResourceRegistrar.registerResources(environment, "com.gutou");
    }
}
```

**手动绑定方式（不推荐，仅作对比）：**

```java
@Override
public void run(DwTestConfiguration configuration, Environment environment) {
    final SessionFactory sessionFactory = hibernateBundle.getSessionFactory();
    final UserDAO userDAO = new UserDAO(sessionFactory);

    // 手动绑定每个服务
    environment.jersey().register(new AbstractBinder() {
        @Override
        protected void configure() {
            bind(sessionFactory).to(SessionFactory.class);
            bind(userDAO).to(UserDAO.class);
            // 每增加一个服务都要在这里添加...
        }
    });

    // 手动注册每个 Resource
    environment.jersey().register(new UserResource(userDAO));
    // 每增加一个 Resource 都要在这里添加...
}
```

**关键点：**

- `HibernateBundle`: Dropwizard 的 Hibernate 集成，管理 SessionFactory 生命周期
- `AbstractBinder`: HK2 的依赖绑定，注册服务供注入使用
- **自动扫描机制**：使用 `Hk2ServiceBinder`、`Hk2DaoBinder`、`JerseyResourceRegistrar` 实现零配置
- 实体类需要在 `HibernateBundle` 构造函数中注册

### 2. 实体类 (User.java)

```java
package com.gutou.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

**注解说明：**

- `@Entity`: 标识为 JPA 实体类
- `@Table(name = "users")`: 指定数据库表名
- `@Id`: 标识主键字段
- `@GeneratedValue(strategy = GenerationType.IDENTITY)`: 主键自增策略
- `@Column`: 列映射和约束
  - `nullable = false`: 不允许 NULL（主要在 DDL 生成时生效）
  - `unique = true`: 唯一约束
- `@PrePersist`: 持久化前回调，自动设置创建时间
- `@PreUpdate`: 更新前回调，自动更新修改时间
- `@Data`: Lombok 注解，生成 getter/setter/toString/equals/hashCode
- `@Builder`: Lombok 注解，提供 Builder 模式

### 3. DAO 层 (UserDAO.java)

```java
package com.gutou.dao;

import com.gutou.entity.User;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class UserDAO extends AbstractDAO<User> {

    private static final Logger logger = LoggerFactory.getLogger(UserDAO.class);

    public UserDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    public Optional<User> findById(Long id) {
        logger.debug("查找用户，ID: {}", id);
        return Optional.ofNullable(get(id));
    }

    public Optional<User> findByEmail(String email) {
        Query<User> query = currentSession().createQuery(
            "SELECT u FROM User u WHERE u.email = :email", User.class);
        query.setParameter("email", email);
        return Optional.ofNullable(query.uniqueResult());
    }

    public List<User> findAll() {
        Query<User> query = currentSession().createQuery(
            "SELECT u FROM User u", User.class);
        return query.getResultList();
    }

    public User create(User user) {
        logger.info("创建新用户: {}", user.getUsername());
        return persist(user);
    }

    public User update(User user) {
        return persist(user);
    }

    public void delete(User user) {
        currentSession().remove(user);
    }

    public void deleteById(Long id) {
        findById(id).ifPresent(this::delete);
    }
}
```

**关键点：**

- 继承 `AbstractDAO<T>`: Dropwizard 提供的 DAO 基类
- `currentSession()`: 获取当前 Hibernate Session
- `createQuery()`: 创建 JPQL 查询（使用实体类名，不是表名）
- 查询封装在 DAO 方法中，不推荐使用 `@NamedQuery`（保持实体类简洁）

### 4. REST 资源 (UserResource.java)

```java
package com.gutou.resources;

import com.gutou.entity.User;
import com.gutou.dao.UserDAO;
import io.dropwizard.hibernate.UnitOfWork;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    private static final Logger logger = LoggerFactory.getLogger(UserResource.class);
    private final UserDAO userDAO;

    public UserResource(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    @GET
    @UnitOfWork
    public List<User> getAllUsers() {
        logger.debug("获取所有用户列表");
        return userDAO.findAll();
    }

    @GET
    @Path("/{id}")
    @UnitOfWork
    public Response getUserById(@PathParam("id") Long id) {
        return userDAO.findById(id)
                .map(user -> Response.ok(user).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @UnitOfWork
    public Response createUser(User user) {
        logger.info("创建用户请求: username={}, email={}", user.getUsername(), user.getEmail());
        if (userDAO.findByEmail(user.getEmail()).isPresent()) {
            logger.warn("用户创建失败，邮箱已存在: {}", user.getEmail());
            return Response.status(Response.Status.CONFLICT)
                    .entity("User with email " + user.getEmail() + " already exists")
                    .build();
        }
        User created = userDAO.create(user);
        logger.info("用户创建成功: id={}, username={}", created.getId(), created.getUsername());
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @UnitOfWork
    public Response updateUser(@PathParam("id") Long id, User user) {
        return userDAO.findById(id)
                .map(existing -> {
                    existing.setUsername(user.getUsername());
                    existing.setEmail(user.getEmail());
                    existing.setFullName(user.getFullName());
                    User updated = userDAO.update(existing);
                    return Response.ok(updated).build();
                })
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @DELETE
    @Path("/{id}")
    @UnitOfWork
    public Response deleteUser(@PathParam("id") Long id) {
        return userDAO.findById(id)
                .map(user -> {
                    userDAO.delete(user);
                    return Response.noContent().build();
                })
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    @Path("/email/{email}")
    @UnitOfWork
    public Response getUserByEmail(@PathParam("email") String email) {
        return userDAO.findByEmail(email)
                .map(user -> Response.ok(user).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }
}
```

**关键点：**

- `@Path`: 定义资源路径
- `@UnitOfWork`: **必须添加**，用于管理 Hibernate Session 和事务
- `@GET/@POST/@PUT/@DELETE`: HTTP 方法注解
- `@PathParam`: 路径参数绑定
- 使用 SLF4J 进行日志记录

---

## 关键知识点

### 1. @UnitOfWork 注解

**作用：** 管理 Hibernate Session 和事务

**必须添加的原因：**

- Dropwizard Hibernate 需要 `@UnitOfWork` 来绑定 Session 到当前线程
- 没有此注解会抛出：`No session currently bound to execution context`

**使用位置：**

```java
@GET
@UnitOfWork  // 必须添加！
public List<User> getAllUsers() {
    return userDAO.findAll();
}
```

### 2. JPA 注解详解

#### @Id 和 @GeneratedValue

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;
```

- `@Id`: 标识主键字段（必须）
- `GenerationType.IDENTITY`: 使用数据库自增（MySQL、SQL Server）

#### @Column 约束

```java
@Column(name = "username", nullable = false, unique = true)
private String username;
```

**重要说明：**

- `nullable = false`: 主要在 DDL 生成时生效，运行时不会验证
- 如果表已存在，`update` 模式不会修改已有列的约束
- 要在运行时验证，需使用 Bean Validation 的 `@NotNull`

**约束验证层次：**

```
应用代码
  ↓
JPA/Hibernate (不会验证 @Column nullable)
  ↓
数据库层 ← 在这里验证并抛出异常
```

#### @PrePersist 和 @PreUpdate

```java
@PrePersist
protected void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
}

@PreUpdate
protected void onUpdate() {
    updatedAt = LocalDateTime.now();
}
```

- `@PrePersist`: 实体持久化前执行（创建时）
- `@PreUpdate`: 实体更新前执行
- 常用于自动设置时间戳

### 3. Hibernate Session 管理

**DDL 执行时机：**

- 应用启动时，`SessionFactory` 初始化阶段
- 通过 `hibernate.hbm2ddl.auto` 配置控制
- 执行代码在 Hibernate 框架内部，不在项目代码中

**验证方法：**

- 查看启动日志（配置 `hibernate.show_sql: true`）
- 测试添加新字段，观察是否自动创建列

### 4. HK2 依赖注入

**手动绑定方式：**

```java
// 在 Application.run() 中注册绑定
environment.jersey().register(new AbstractBinder() {
    @Override
    protected void configure() {
        bind(sessionFactory).to(SessionFactory.class);
        bind(userDAO).to(UserDAO.class);
    }
});
```

**使用方式：**

```java
// Resource 类通过构造函数注入
public UserResource(UserDAO userDAO) {
    this.userDAO = userDAO;
}

// 或使用字段注入
@Inject
private UserDAO userDAO;
```

**注意：** 手动绑定方式需要为每个服务手动配置，维护成本高。推荐使用自动扫描机制。

### 5. Lombok 使用

**常用注解：**

- `@Data`: 生成 getter/setter/toString/equals/hashCode
- `@Builder`: 提供 Builder 模式
- `@NoArgsConstructor`: 无参构造函数（JPA 要求）
- `@AllArgsConstructor`: 全参构造函数（Builder 需要）

**Builder 使用示例：**

```java
User user = User.builder()
    .username("alice")
    .email("alice@example.com")
    .fullName("Alice Zhang")
    .build();
```

---

## 最佳实践

### 1. 项目结构

```
entity/    - JPA 实体类（只包含字段定义和 JPA 注解）
dao/       - 数据访问层（查询逻辑封装在方法中）
resources/ - REST API 端点（业务逻辑）
config/    - 配置类
```

### 2. 实体类设计

- ✅ 使用 Lombok 减少样板代码
- ✅ 使用 Builder 模式构造对象
- ✅ 不在实体类中使用 `@NamedQuery`（查询放在 DAO 中）
- ✅ 使用 `@PrePersist`/`@PreUpdate` 自动管理时间戳

### 3. DAO 层设计

- ✅ 继承 `AbstractDAO<T>`
- ✅ 查询逻辑封装在方法中，使用 `createQuery()` 而不是 `@NamedQuery`
- ✅ 使用 SLF4J 记录关键操作日志

### 4. Resource 层设计

- ✅ 所有数据库操作方法必须添加 `@UnitOfWork`
- ✅ 使用 SLF4J 记录请求日志
- ✅ 返回适当的 HTTP 状态码

### 5. 配置建议

**开发环境：**
```yaml
hibernate.hbm2ddl.auto: update  # 自动更新表结构
hibernate.show_sql: true         # 显示 SQL
```

**生产环境：**
```yaml
hibernate.hbm2ddl.auto: validate # 只验证，不修改
hibernate.show_sql: false        # 关闭 SQL 日志
```

### 6. 数据库迁移

- 生产环境推荐使用 Flyway 或 Liquibase 管理数据库版本
- 不要依赖 `hbm2ddl.auto` 在生产环境修改表结构

---

## 常见问题

### 1. No session currently bound to execution context

**原因：** Resource 方法缺少 `@UnitOfWork` 注解

**解决：** 在所有数据库操作方法上添加 `@UnitOfWork`

```java
@GET
@UnitOfWork  // 添加这个注解
public List<User> getAllUsers() {
    return userDAO.findAll();
}
```

### 2. Public Key Retrieval is not allowed

**原因：** MySQL 8.0+ 需要允许公钥检索

**解决：** 在 JDBC URL 中添加参数

```yaml
url: jdbc:mysql://localhost:3306/dw_test?allowPublicKeyRetrieval=true
```

### 3. Unsupported character encoding 'utf8mb4'

**原因：** Java 不支持 `utf8mb4` 作为编码名称

**解决：** 使用 `utf8`，MySQL 会自动映射到 `utf8mb4`

```yaml
url: jdbc:mysql://localhost:3306/dw_test?characterEncoding=utf8
```

### 4. @Column(nullable=false) 约束不生效

**原因：** 

- `@Column(nullable=false)` 主要在 DDL 生成时生效
- 如果表已存在，`update` 模式不会修改已有列的约束
- JPA 运行时不会验证此约束

**解决：**

- 如果表是手动创建的，需要手动添加 NOT NULL 约束
- 或者使用 Bean Validation 的 `@NotNull` 进行运行时验证

### 5. 如何查看执行的 SQL

**配置日志：**

```yaml
logging:
  loggers:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

---

## 快速开始

### 1. 编译项目

```bash
mvn clean package
```

### 2. 创建数据库

```sql
CREATE DATABASE dw_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 3. 运行应用

```bash
java -jar target/dw-test-01-1.0-SNAPSHOT.jar server src/main/resources/config.yml
```

### 4. 测试 API

```bash
# 获取所有用户
curl http://localhost:8080/users

# 创建用户
curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{"username":"test","email":"test@example.com","fullName":"Test User"}'

# 根据 ID 获取用户
curl http://localhost:8080/users/1
```

### 5. 访问管理界面

- 应用地址: http://localhost:8080
- 管理界面: http://localhost:8080/admin
- 健康检查: http://localhost:8080/admin/healthcheck

---

## 总结

本文档涵盖了 Dropwizard + HK2 + Hibernate 框架的：

1. ✅ 完整项目搭建步骤
2. ✅ 核心配置说明
3. ✅ 代码结构示例
4. ✅ 关键知识点详解
5. ✅ 最佳实践建议
6. ✅ 常见问题解决方案

**核心要点：**

- `@UnitOfWork` 注解是必须的
- DDL 执行在 SessionFactory 初始化时
- `@Column` 约束主要在数据库层生效
- 查询逻辑封装在 DAO 方法中
- 使用 Lombok 简化代码

希望这份文档能帮助您快速上手 Dropwizard + HK2 + Hibernate 框架！


