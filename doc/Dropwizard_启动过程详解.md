# Dropwizard 启动过程详解

## 概述

Dropwizard 应用的启动过程分为几个明确的阶段，每个阶段都有特定的职责。理解启动过程有助于调试问题和优化应用性能。

---

## 启动流程概览

```
1. main() 方法
   ↓
2. Application.run()
   ↓
3. 解析命令行参数和配置文件
   ↓
4. Bootstrap.initialize() - 初始化阶段
   ↓
5. Bootstrap.run() - 运行阶段
   ↓
6. Environment 设置
   ↓
7. Application.run(config, environment) - 应用运行阶段
   ↓
8. Jetty 服务器启动
   ↓
9. 应用就绪
```

---

## 详细启动过程

### 阶段 1: 程序入口 (main 方法)

```java
public static void main(String[] args) throws Exception {
    new DwTestApplication().run(args);
}
```

**执行内容：**
- 创建 Application 实例
- 调用 `run(args)` 方法，传入命令行参数

**命令行参数格式：**
```bash
java -jar app.jar server config.yml
#          ↑      ↑
#      命令    配置文件路径
```

---

### 阶段 2: Application.run() - 框架入口

Dropwizard 框架内部执行：

```java
Application.run(String... args)
```

**执行步骤：**

1. **解析命令行参数**
   - 解析命令（如 `server`、`check`、`db migrate` 等）
   - 解析配置文件路径

2. **加载配置文件**
   - 读取 YAML 配置文件（如 `config.yml`）
   - 使用 Jackson 反序列化为 Configuration 对象

3. **验证配置**
   - 使用 Bean Validation 验证配置对象
   - 如果验证失败，抛出异常并退出

---

### 阶段 3: Bootstrap.initialize() - 初始化阶段

**执行时机：** 在加载配置文件之后，运行之前

**调用位置：** Dropwizard 框架内部调用

```java
Bootstrap<DwTestConfiguration> bootstrap = new Bootstrap<>(this);
bootstrap.initialize();  // 内部调用
```

**您的代码：**

```java
@Override
public void initialize(Bootstrap<DwTestConfiguration> bootstrap) {
    bootstrap.addBundle(hibernateBundle);  // 在这里注册 Bundle
}
```

**执行内容：**

1. **调用 `initialize()` 方法**
   - 注册默认的 Bundle（如日志、指标等）
   - 调用您重写的 `initialize()` 方法
   - 在这里可以注册自定义 Bundle

2. **注册 Bundle**
   - `HibernateBundle` 被注册
   - Bundle 会保存配置，但**还未执行初始化**

**重要：** 此时 Bundle 只是注册，还未运行！

---

### 阶段 4: Bootstrap.run() - 运行阶段

**执行时机：** 配置加载和验证完成后

**Dropwizard 框架内部执行：**

```java
Bootstrap.run(Configuration config, Environment environment)
```

**执行步骤：**

1. **创建 Environment 对象**
   - 包含应用的运行时环境（Jersey、Jetty、Health Checks 等）

2. **运行所有注册的 Bundle**
   - 按注册顺序调用每个 Bundle 的 `run()` 方法
   - **这里会执行 HibernateBundle.run()**

3. **HibernateBundle.run() 执行**
   ```
   HibernateBundle.run()
     ↓
   创建 DataSource（数据库连接池）
     ↓
   创建 SessionFactory
     ↓
   读取实体类元数据（@Entity、@Table、@Column 等）
     ↓
   读取配置：hibernate.hbm2ddl.auto = update
     ↓
   执行 DDL 操作（如果需要）
     ↓
   SessionFactory 创建完成
   ```

4. **注册健康检查**
   - 注册数据库连接健康检查
   - 注册其他默认健康检查

---

### 阶段 5: Application.run() - 应用运行阶段

**执行时机：** Bundle 运行完成后

**您的代码：**

```java
@Override
public void run(DwTestConfiguration configuration, Environment environment) {
    // 1. 获取 SessionFactory（此时已经创建完成）
    final SessionFactory sessionFactory = hibernateBundle.getSessionFactory();

    // 2. 创建 DAO 实例
    final UserDAO userDAO = new UserDAO(sessionFactory);

    // 3. 注册 HK2 绑定（依赖注入）
    environment.jersey().register(new AbstractBinder() {
        @Override
        protected void configure() {
            bind(sessionFactory).to(SessionFactory.class);
            bind(userDAO).to(UserDAO.class);
        }
    });

    // 4. 注册 REST 资源
    environment.jersey().register(new UserResource(userDAO));
}
```

**执行内容：**

1. **获取已创建的组件**
   - SessionFactory 已在 Bootstrap.run() 阶段创建
   - 通过 `getSessionFactory()` 获取实例

2. **创建业务组件**
   - 创建 DAO 实例
   - 创建 Service 实例（如果有）

3. **注册依赖注入绑定**
   - 使用 HK2 的 `AbstractBinder` 注册服务
   - 供后续依赖注入使用

4. **注册 REST 资源**
   - 注册 Resource 类（REST 端点）
   - Jersey 会扫描 `@Path` 注解

5. **注册其他组件**
   - 注册过滤器（Filter）
   - 注册异常处理器（ExceptionMapper）
   - 注册自定义健康检查（HealthCheck）

---

### 阶段 6: Jetty 服务器启动

**执行时机：** `run()` 方法执行完成后

**Dropwizard 框架内部执行：**

1. **创建 Jetty 服务器**
   - 根据配置创建 HTTP 连接器
   - 创建应用上下文（Application Context）
   - 创建管理上下文（Admin Context）

2. **注册 Handler**
   - 应用 Handler（处理业务请求）
   - 管理 Handler（处理管理请求，如健康检查、指标）

3. **启动服务器**
   - 绑定端口（默认 8080）
   - 启动 Jetty 容器
   - 开始监听 HTTP 请求

---

### 阶段 7: 应用就绪

**执行完成后的状态：**

- ✅ 服务器启动成功
- ✅ REST API 可以接受请求
- ✅ 管理界面可以访问（http://localhost:8080/admin）
- ✅ 健康检查可用（http://localhost:8080/admin/healthcheck）

**日志输出示例：**

```
INFO  [2025-12-31 15:11:06] org.eclipse.jetty.server.Server: Started Server@xxx
INFO  [2025-12-31 15:11:06] org.eclipse.jetty.server.AbstractConnector: Started ServerConnector@xxx{HTTP/1.1, (http/1.1)}{0.0.0.0:8080}
```

---

## 完整时序图

```
main(args)
  ↓
Application.run(args)
  ↓
├─ 解析命令行参数
├─ 加载 config.yml
├─ 验证配置
  ↓
Bootstrap.initialize()
  ↓
├─ 调用您的 initialize() 方法
│  └─ bootstrap.addBundle(hibernateBundle)  ← 注册 Bundle
  ↓
Bootstrap.run(config, environment)
  ↓
├─ 创建 Environment
├─ 运行所有 Bundle
│  └─ HibernateBundle.run()
│     ├─ 创建 DataSource
│     ├─ 创建 SessionFactory  ← DDL 在这里执行
│     └─ 注册健康检查
  ↓
Application.run(config, environment)  ← 您的 run() 方法
  ↓
├─ 获取 SessionFactory
├─ 创建 DAO/Service
├─ 注册 HK2 绑定
├─ 注册 REST 资源
  ↓
Jetty 服务器启动
  ↓
├─ 创建连接器
├─ 绑定端口 8080
├─ 启动容器
  ↓
应用就绪 ✅
```

---

## 关键点说明

### 1. Bundle 的执行顺序

Bundle 按注册顺序执行：

```java
@Override
public void initialize(Bootstrap<DwTestConfiguration> bootstrap) {
    bootstrap.addBundle(bundle1);  // 先执行
    bootstrap.addBundle(hibernateBundle);  // 后执行
    bootstrap.addBundle(bundle3);  // 最后执行
}
```

### 2. SessionFactory 的创建时机

**重要：** SessionFactory 在 `Bootstrap.run()` 阶段创建，不是在 `Application.run()` 中创建！

```java
// ❌ 错误理解：以为在这里创建
@Override
public void run(...) {
    // SessionFactory 已经创建好了
    final SessionFactory sf = hibernateBundle.getSessionFactory();
}

// ✅ 实际创建时机：在 Bootstrap.run() 中
// HibernateBundle.run() → SessionFactory 创建
```

### 3. DDL 执行时机

DDL 执行发生在 SessionFactory 创建过程中：

```
Bootstrap.run()
  ↓
HibernateBundle.run()
  ↓
SessionFactoryFactory.build()
  ↓
读取 hibernate.hbm2ddl.auto = update
  ↓
执行 DDL（如果需要）← 在这里！
  ↓
SessionFactory 创建完成
```

### 4. 配置的加载和验证

```java
// 配置加载流程
config.yml
  ↓
Jackson 反序列化
  ↓
DwTestConfiguration 对象
  ↓
Bean Validation 验证（@NotNull、@Valid 等）
  ↓
验证通过，继续启动
```

---

## 生命周期方法总结

| 方法 | 执行时机 | 主要用途 |
|------|---------|---------|
| `main()` | 程序入口 | 启动应用 |
| `initialize(Bootstrap)` | 配置加载后 | 注册 Bundle、添加命令 |
| `run(Configuration, Environment)` | Bundle 运行后 | 注册资源、创建组件 |

---

## 启动日志分析

**典型的启动日志顺序：**

```
1. INFO  [xxx] io.dropwizard.Application: Starting dw-test-01
2. INFO  [xxx] io.dropwizard.setup.Bootstrap: Registering jersey handler
3. INFO  [xxx] org.hibernate.Version: HHH000412: Hibernate ORM core version
4. INFO  [xxx] io.dropwizard.hibernate.SessionFactoryFactory: Entity classes: [com.gutou.entity.User]
5. INFO  [xxx] org.hibernate.dialect.Dialect: HHH000400: Using dialect: org.hibernate.dialect.MySQLDialect
6. INFO  [xxx] io.dropwizard.jersey.DropwizardResourceConfig: The following paths were found...
7. INFO  [xxx] org.eclipse.jetty.server.Server: Started Server
8. INFO  [xxx] org.eclipse.jetty.server.AbstractConnector: Started ServerConnector
```

**日志解读：**

- 第 1 行：应用开始启动
- 第 2 行：注册 Jersey Handler
- 第 3-5 行：Hibernate 初始化（SessionFactory 创建）
- 第 6 行：扫描 REST 资源
- 第 7-8 行：Jetty 服务器启动

---

## 常见启动问题

### 1. 配置文件加载失败

**错误：** `Unable to parse configuration file`

**原因：** YAML 语法错误或文件路径不正确

**解决：** 检查配置文件语法和路径

### 2. 配置验证失败

**错误：** `ConstraintViolationException`

**原因：** 配置对象验证失败（如 `@NotNull` 字段为 null）

**解决：** 检查配置文件，确保必填字段已配置

### 3. 数据库连接失败

**错误：** `Unable to create initial connections of pool`

**原因：** 数据库配置错误或数据库服务未启动

**解决：** 检查数据库配置、服务状态、网络连接

### 4. SessionFactory 创建失败

**错误：** `Unable to build SessionFactory`

**原因：** 实体类配置错误、数据库方言不匹配等

**解决：** 检查实体类注解、数据库方言配置

---

## 启动性能优化建议

1. **减少 Bundle 数量**：只注册必要的 Bundle
2. **优化数据库连接池**：合理配置连接池大小
3. **延迟初始化**：对于不常用的组件，使用懒加载
4. **减少启动时的数据库操作**：避免在启动时执行大量查询

---

## 总结

Dropwizard 的启动过程清晰、阶段明确：

1. **main()** → 程序入口
2. **配置加载** → 解析和验证配置文件
3. **initialize()** → 注册 Bundle
4. **Bootstrap.run()** → 运行 Bundle（SessionFactory 创建）
5. **run()** → 注册资源和组件
6. **Jetty 启动** → 服务器就绪

理解启动过程有助于：
- ✅ 定位问题（知道问题发生在哪个阶段）
- ✅ 优化性能（知道哪些操作在启动时执行）
- ✅ 扩展功能（知道在哪里添加自定义组件）

