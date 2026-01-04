# 技术实践：扩展 @UnitOfWork 注解支持 HK2 服务层

## 背景

在 Dropwizard 框架中，`@UnitOfWork` 注解默认只在 Jersey 资源层（Resource 层）生效，通过 Jersey 的拦截器机制自动管理 Hibernate 会话和事务。但在实际开发中，我们经常需要在服务层（Service 层）、启动任务（Startup Jobs）、定时任务（Scheduled Jobs）等非 HTTP 请求上下文中使用数据库操作，此时 `@UnitOfWork` 注解无法生效。

## 问题分析

### 原始问题

在启动任务中调用服务层方法时，出现以下错误：

```
org.hibernate.HibernateException: No session currently bound to execution context
```

**原因分析：**
1. `@UnitOfWork` 注解依赖 Jersey 的拦截器机制，只在 HTTP 请求处理时生效
2. 启动任务在 `INITIALIZATION_FINISHED` 事件中执行，此时没有 HTTP 请求上下文
3. 因此 Hibernate 会话无法自动绑定到执行上下文

### 临时解决方案的局限性

最初我们创建了 `HibernateSessionManager` 工具类来手动管理会话：

```java
HibernateSessionManager.executeInSession(sessionFactory, () -> {
    // 数据库操作
});
```

**缺点：**
- 需要手动注入 `SessionFactory`
- 代码冗余，每个需要数据库操作的地方都要包装
- 不够优雅，不符合声明式编程的理念

## 解决方案

### 核心思路

使用 HK2 的拦截器机制（`InterceptionService` 和 `MethodInterceptor`），自动拦截所有带有 `@UnitOfWork` 注解的方法，统一管理 Hibernate 会话和事务。

### 实现架构

```
┌─────────────────────────────────────────────────────────┐
│                   应用启动流程                            │
└─────────────────────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────┐
│          Hk2UnitOfWorkBinder (注册拦截器)               │
│  ┌──────────────────┐      ┌──────────────────┐        │
│  │ UnitOfWorkMethod │      │ UnitOfWorkInter- │        │
│  │   Interceptor    │      │  ceptionService  │        │
│  └──────────────────┘      └──────────────────┘        │
└─────────────────────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────┐
│              服务方法调用流程                            │
│                                                          │
│  @UnitOfWork                                            │
│  public void myMethod() {                               │
│      ┌──────────────────────────────────────┐          │
│      │ 1. UnitOfWorkInterceptionService     │          │
│      │    识别方法上的 @UnitOfWork 注解      │          │
│      └──────────────────────────────────────┘          │
│                    │                                    │
│                    ▼                                    │
│      ┌──────────────────────────────────────┐          │
│      │ 2. UnitOfWorkMethodInterceptor       │          │
│      │    执行拦截逻辑：                     │          │
│      │    - 打开会话                        │          │
│      │    - 绑定到线程上下文                │          │
│      │    - 开始事务                        │          │
│      │    - 执行原方法                      │          │
│      │    - 提交/回滚事务                  │          │
│      │    - 关闭会话                        │          │
│      └──────────────────────────────────────┘          │
│                    │                                    │
│                    ▼                                    │
│      ┌──────────────────────────────────────┐          │
│      │ 3. 实际业务方法执行                    │          │
│      └──────────────────────────────────────┘          │
└─────────────────────────────────────────────────────────┘
```

### 核心组件

#### 1. UnitOfWorkInterceptionService

**职责：** 识别需要拦截的方法

```java
public class UnitOfWorkInterceptionService implements InterceptionService {
    @Inject
    private UnitOfWorkMethodInterceptor methodInterceptor;

    @Override
    public List<MethodInterceptor> getMethodInterceptors(Method method) {
        // 检查方法是否带有 @UnitOfWork 注解
        if (method.isAnnotationPresent(UnitOfWork.class)) {
            return Collections.singletonList(methodInterceptor);
        }
        return Collections.emptyList();
    }
}
```

**关键点：**
- 实现 `InterceptionService` 接口
- 通过 `getMethodInterceptors` 方法返回需要应用的拦截器
- 检查方法上的 `@UnitOfWork` 注解

#### 2. UnitOfWorkMethodInterceptor

**职责：** 执行实际的拦截逻辑，管理 Hibernate 会话和事务

```java
public class UnitOfWorkMethodInterceptor implements MethodInterceptor {
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
        // 1. 检查是否已有活动会话（嵌套调用支持）
        // 2. 打开新会话
        // 3. 绑定到线程上下文
        // 4. 开始事务
        // 5. 执行原方法
        // 6. 提交/回滚事务
        // 7. 关闭会话
    }
}
```

**关键特性：**
- **嵌套调用支持：** 如果已有活动会话，复用现有会话，避免嵌套事务问题
- **异常处理：** 发生异常时自动回滚事务
- **资源清理：** 确保会话正确关闭，避免资源泄漏

#### 3. Hk2UnitOfWorkBinder

**职责：** 注册拦截器到 HK2 容器

```java
public class Hk2UnitOfWorkBinder extends AbstractBinder {
    @Override
    protected void configure() {
        // 先注册方法拦截器
        bind(UnitOfWorkMethodInterceptor.class)
            .to(UnitOfWorkMethodInterceptor.class)  // 具体类型，供注入使用
            .to(MethodInterceptor.class)              // 接口类型，供 HK2 使用
            .in(Singleton.class);
        
        // 再注册拦截服务
        bind(UnitOfWorkInterceptionService.class)
            .to(InterceptionService.class)
            .in(Singleton.class);
    }
}
```

**关键点：**
- **绑定顺序：** 先绑定 `MethodInterceptor`，再绑定 `InterceptionService`（解决依赖注入问题）
- **双重绑定：** `MethodInterceptor` 需要同时绑定到具体类型和接口类型
- **作用域：** 必须明确指定 `Singleton` 作用域

### 注册时机

在应用启动时，**必须在服务绑定之前**注册拦截器：

```java
@Override
public void run(DwTestConfiguration configuration, Environment environment) {
    // ... 其他初始化代码 ...
    
    // 注册 @UnitOfWork 拦截器支持（必须在服务绑定之前注册）
    environment.jersey().register(new Hk2UnitOfWorkBinder());
    
    // 自动扫描并绑定所有 @Service 注解的服务
    environment.jersey().register(new Hk2ServiceBinder("com.gutou"));
    
    // ... 其他代码 ...
}
```

## 使用示例

### 1. 服务层方法

```java
@Service
public class UserServiceImpl implements UserServiceApi {
    @Inject
    private UserDAO userDAO;
    
    @Override
    @UnitOfWork
    public List<User> findAll() {
        return userDAO.findAll();
    }
}
```

### 2. 启动任务

```java
@Service
@Rank(2)
public class StartUpJob2 implements JobService {
    @Inject
    private UserServiceApi userServiceApi;

    @Override
    @UnitOfWork
    public void execute() {
        List<Long> list = userServiceApi.findAll().stream()
            .map(User::getId)
            .toList();
        log.info("all list is:{}", list);
    }
}
```

### 3. 定时任务

```java
@Service
@Rank(5)
public class ScheduledJob2 implements JobService {
    @Inject
    private UserServiceApi userServiceApi;

    @Override
    public void execute() {
        Runnable runnable = this::doScheduledWork;
        GlobalThreadPoolManager.getInstance()
            .scheduleAtFixedRate(runnable, 1, 5, TimeUnit.SECONDS);
    }

    @UnitOfWork
    private void doScheduledWork() {
        List<Long> list = userServiceApi.findAll().stream()
            .map(User::getId)
            .toList();
        log.info("all list is:{}", list);
    }
}
```

## 技术要点总结

### 1. HK2 拦截器机制

- **InterceptionService：** 识别需要拦截的方法
- **MethodInterceptor：** 执行拦截逻辑（AOP Alliance 标准接口）
- **绑定顺序：** 必须注意依赖注入的顺序

### 2. 作用域管理

- `InterceptionService` 实现类必须是 `Singleton` 作用域
- 在绑定器中明确指定 `.in(Singleton.class)`
- 避免使用 `@Service` 注解自动扫描，改为手动绑定

### 3. 嵌套调用处理

```java
// 检查是否已有活动会话
boolean hasExistingSession = ManagedSessionContext.hasBind(sessionFactory);
if (hasExistingSession && existingSession.isOpen()) {
    // 复用现有会话，避免嵌套事务
    return methodInvocation.proceed();
}
```

### 4. 异常处理

```java
try {
    Object result = methodInvocation.proceed();
    session.getTransaction().commit();
    return result;
} catch (Exception e) {
    if (session.getTransaction().isActive()) {
        session.getTransaction().rollback();
    }
    throw e;
} finally {
    // 确保资源清理
    ManagedSessionContext.unbind(sessionFactory);
    session.close();
}
```

## 优势对比

### 使用前（手动管理）

```java
@Inject
private SessionFactory sessionFactory;

public void execute() {
    HibernateSessionManager.executeInSession(sessionFactory, () -> {
        userService.findAll();
    });
}
```

**缺点：**
- 需要注入 `SessionFactory`
- 代码冗余
- 容易忘记使用

### 使用后（声明式）

```java
@UnitOfWork
public void execute() {
    userService.findAll();
}
```

**优点：**
- 代码简洁
- 声明式编程
- 统一的事务管理
- 自动处理异常和资源清理

## 注意事项

1. **注册顺序：** 拦截器必须在服务绑定之前注册
2. **作用域：** 必须明确指定 `Singleton` 作用域
3. **绑定方式：** 使用手动绑定，避免与自动扫描冲突
4. **嵌套调用：** 自动支持嵌套调用，复用现有会话
5. **线程安全：** 每个线程有独立的会话上下文

## 扩展思考

### 可能的增强

1. **支持事务传播属性：** 类似 Spring 的 `@Transactional(propagation = ...)`
2. **支持只读事务：** `@UnitOfWork(readOnly = true)`
3. **支持超时设置：** `@UnitOfWork(timeout = 30)`
4. **支持隔离级别：** `@UnitOfWork(isolation = ...)`

### 性能考虑

- 拦截器会为每个带 `@UnitOfWork` 的方法创建代理
- 对于高频调用的方法，需要考虑性能影响
- 嵌套调用检测有轻微开销，但可以避免嵌套事务问题

## 总结

通过扩展 `@UnitOfWork` 注解支持 HK2 服务层，我们实现了：

1. ✅ **统一的声明式事务管理** - 在服务层、启动任务、定时任务中都可以使用
2. ✅ **代码简化** - 无需手动管理会话和事务
3. ✅ **更好的可维护性** - 集中管理事务逻辑
4. ✅ **嵌套调用支持** - 自动处理嵌套事务场景
5. ✅ **异常安全** - 自动回滚和资源清理

这是一个典型的 AOP（面向切面编程）应用场景，通过拦截器实现了横切关注点的统一处理。

