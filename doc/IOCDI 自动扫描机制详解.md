# IOC/DI 自动扫描机制详解

## 问题背景

在传统的 Dropwizard + HK2 项目中，我们需要手动绑定每个服务和资源：

```java
// 手动绑定方式 - 繁琐且容易遗漏
environment.jersey().register(new AbstractBinder() {
    @Override
    protected void configure() {
        bind(sessionFactory).to(SessionFactory.class);
        bind(userDAO).to(UserDAO.class);
        bind(taskMngImpl).to(TaskMngApi.class);
        // 每增加一个服务都要在这里添加...
    }
});

// 手动注册每个 Resource
environment.jersey().register(new UserResource(userDAO));
// 每增加一个 Resource 都要在这里添加...
```

这种方式存在以下问题：
1. **维护成本高**：每次新增服务都要修改主应用类
2. **容易遗漏**：忘记绑定的服务会导致运行时 NPE
3. **代码冗余**：绑定逻辑重复
4. **不便于扩展**：添加新功能需要多处修改

## 解决方案：自动扫描与绑定

我们实现了一套完整的自动扫描机制，实现零配置添加新功能。

### 1. 核心依赖

```xml
<!-- Reflections for automatic service scanning -->
<dependency>
    <groupId>org.reflections</groupId>
    <artifactId>reflections</artifactId>
    <version>0.10.2</version>
</dependency>
```

### 2. 自动绑定工具类

#### 2.1 Service 自动绑定 (`Hk2ServiceBinder.java`)

```java
package com.gutou.core;

import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.jvnet.hk2.annotations.Contract;
import org.jvnet.hk2.annotations.Service;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * HK2 服务自动绑定工具类
 * 自动扫描带有 @Service 注解的类，并绑定到它们实现的 @Contract 接口
 */
public class Hk2ServiceBinder extends AbstractBinder {
    
    private static final Logger log = LoggerFactory.getLogger(Hk2ServiceBinder.class);
    private final String basePackage;
    
    public Hk2ServiceBinder(String basePackage) {
        this.basePackage = basePackage;
    }
    
    @Override
    protected void configure() {
        Reflections reflections = new Reflections(basePackage, Scanners.TypesAnnotated);
        
        // 查找所有带有 @Service 注解的类
        Set<Class<?>> serviceClasses = reflections.getTypesAnnotatedWith(Service.class);
        
        log.info("发现 {} 个 @Service 注解的服务类", serviceClasses.size());
        
        for (Class<?> serviceClass : serviceClasses) {
            // 查找该类实现的所有接口
            Class<?>[] interfaces = serviceClass.getInterfaces();
            
            // 查找带有 @Contract 注解的接口
            Set<Class<?>> contractInterfaces = java.util.Arrays.stream(interfaces)
                    .filter(iface -> iface.isAnnotationPresent(Contract.class))
                    .collect(Collectors.toSet());
            
            if (contractInterfaces.isEmpty()) {
                // 如果没有找到 @Contract 接口，直接绑定服务类本身
                log.debug("绑定服务: {} (无 @Contract 接口)", serviceClass.getName());
                bindAsContract(serviceClass);
            } else {
                // 绑定到每个 @Contract 接口
                for (Class<?> contractInterface : contractInterfaces) {
                    log.debug("绑定服务: {} -> {}", serviceClass.getName(), contractInterface.getName());
                    bind(serviceClass).to(contractInterface);
                }
            }
        }
    }
}
```

**工作原理：**
1. 扫描指定包下所有带有 `@Service` 注解的类
2. 查找这些类实现的接口中带有 `@Contract` 注解的接口
3. 自动执行 `bind(ServiceImpl.class).to(ServiceApi.class)`
4. 如果没有找到 `@Contract` 接口，则直接绑定服务类本身

#### 2.2 DAO 自动绑定 (`Hk2DaoBinder.java`)

```java
package com.gutou.core;

import io.dropwizard.hibernate.AbstractDAO;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.hibernate.SessionFactory;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.Set;

/**
 * HK2 DAO 自动绑定工具类
 * 自动扫描所有继承 AbstractDAO 的类，并自动创建和绑定到 HK2 容器
 */
public class Hk2DaoBinder extends AbstractBinder {
    
    private static final Logger log = LoggerFactory.getLogger(Hk2DaoBinder.class);
    private final String basePackage;
    private final SessionFactory sessionFactory;
    
    public Hk2DaoBinder(String basePackage, SessionFactory sessionFactory) {
        this.basePackage = basePackage;
        this.sessionFactory = sessionFactory;
    }
    
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected void configure() {
        Reflections reflections = new Reflections(basePackage, Scanners.SubTypes);
        
        // 查找所有 AbstractDAO 的子类
        Set<Class<? extends AbstractDAO>> daoClasses = reflections.getSubTypesOf(AbstractDAO.class);
        
        log.info("发现 {} 个 DAO 类", daoClasses.size());
        
        for (Class<? extends AbstractDAO> daoClass : daoClasses) {
            // 跳过抽象类
            if (java.lang.reflect.Modifier.isAbstract(daoClass.getModifiers())) {
                log.debug("跳过抽象 DAO 类: {}", daoClass.getName());
                continue;
            }
            
            try {
                // 查找接受 SessionFactory 的构造函数
                Constructor<? extends AbstractDAO> constructor = daoClass.getConstructor(SessionFactory.class);
                
                // 创建 DAO 实例
                AbstractDAO<?> daoInstance = constructor.newInstance(sessionFactory);
                
                // 绑定到 DAO 类本身
                log.info("绑定 DAO: {}", daoClass.getName());
                bind(daoInstance).to((Class) daoClass);
                
            } catch (NoSuchMethodException e) {
                log.warn("DAO 类 {} 没有接受 SessionFactory 的构造函数，跳过", daoClass.getName());
            } catch (Exception e) {
                log.error("创建 DAO 实例失败: {}", daoClass.getName(), e);
            }
        }
    }
}
```

**工作原理：**
1. 扫描指定包下所有继承 `AbstractDAO` 的类
2. 检查是否具有接受 `SessionFactory` 的构造函数
3. 自动创建 DAO 实例并绑定到 HK2 容器
4. 跳过抽象类，只绑定具体实现

#### 2.3 Resource 自动注册 (`JerseyResourceRegistrar.java`)

```java
package com.gutou.core;

import io.dropwizard.core.setup.Environment;
import jakarta.ws.rs.Path;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Jersey Resource 自动注册工具类
 * 自动扫描并注册所有带有 @Path 注解的 Resource 类（支持类级别和方法级别）
 * 支持接口、抽象类和具体实现类
 */
public class JerseyResourceRegistrar {
    
    private static final Logger log = LoggerFactory.getLogger(JerseyResourceRegistrar.class);
    
    /**
     * 自动扫描并注册指定包下所有带有 @Path 注解的 Resource 类
     * 支持：
     * 1. 类级别的 @Path 注解
     * 2. 方法级别的 @Path 注解（需要类级别也有 @Path 或类实现了接口）
     * 3. 接口作为 Resource（Jersey 支持）
     * 4. 抽象类作为 Resource
     * 
     * @param environment Dropwizard Environment
     * @param basePackage 要扫描的基础包名
     */
    public static void registerResources(Environment environment, String basePackage) {
        Reflections reflections = new Reflections(basePackage, 
            Scanners.TypesAnnotated, 
            Scanners.MethodsAnnotated);
        
        // 1. 查找所有类级别带有 @Path 注解的类（包括接口、抽象类、具体类）
        Set<Class<?>> classLevelPathClasses = reflections.getTypesAnnotatedWith(Path.class);
        
        // 2. 查找所有方法级别带有 @Path 注解的类
        Set<Method> methodsWithPath = reflections.getMethodsAnnotatedWith(Path.class);
        Set<Class<?>> methodLevelPathClasses = methodsWithPath.stream()
            .map(Method::getDeclaringClass)
            .collect(Collectors.toSet());
        
        // 合并两类：类级别和方法级别的
        Set<Class<?>> allResourceClasses = classLevelPathClasses;
        allResourceClasses.addAll(methodLevelPathClasses);
        
        log.info("发现 {} 个 Resource 类（类级别: {}, 方法级别: {}）", 
            allResourceClasses.size(), 
            classLevelPathClasses.size(), 
            methodLevelPathClasses.size());
        
        for (Class<?> resourceClass : allResourceClasses) {
            // 跳过基本类型、数组、枚举等
            if (resourceClass.isPrimitive() || resourceClass.isArray() || resourceClass.isEnum()) {
                log.debug("跳过非标准类: {}", resourceClass.getName());
                continue;
            }
            
            // 记录类型信息
            String typeInfo = resourceClass.isInterface() ? "接口" : 
                             java.lang.reflect.Modifier.isAbstract(resourceClass.getModifiers()) ? "抽象类" : "具体类";
            
            log.info("注册 Resource {}: {}", typeInfo, resourceClass.getName());
            environment.jersey().register(resourceClass);
        }
    }
}
```

**特点：**
1. **支持接口作为 Resource**：Jersey 原生支持接口作为资源，支持接口-实现模式
2. **支持抽象类**：允许使用抽象类作为 Resource 基类
3. **支持方法级别 @Path**：即使类上没有 @Path，只要方法上有也会注册
4. **类型安全**：跳过基本类型、数组、枚举等不支持的类

### 3. 简化后的主应用类

```java
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
```

## 使用方式

### 1. 创建 Service 服务

**定义接口**（使用 `@Contract` 注解）：
```java
package com.gutou.core.application;

import org.jvnet.hk2.annotations.Contract;

@Contract
public interface TaskMngApi {
    TaskInfo execute();
}
```

**实现类**（使用 `@Service` 注解）：
```java
package com.gutou.core.application.impl;

import com.gutou.core.application.TaskMngApi;
import org.jvnet.hk2.annotations.Service;

@Service
public class TaskMngImpl implements TaskMngApi {
    
    @Override
    public TaskInfo execute() {
        // 实现逻辑
        return new TaskInfo();
    }
}
```

### 2. 创建 Resource 资源

**接口方式**（推荐）：
```java
package com.gutou.resources;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
public interface UserResourceApi {
    
    @GET
    @Path("/{id}")
    User getUser(Long id);
}
```

**实现类**（使用 `@Inject` 注入依赖）：
```java
package com.gutou.resources.impl;

import com.gutou.resources.UserResourceApi;
import com.gutou.dao.UserDAO;
import com.gutou.entity.User;
import jakarta.inject.Inject;

public class UserResourceImpl implements UserResourceApi {
    
    @Inject
    private UserDAO userDAO;
    
    @Override
    public User getUser(Long id) {
        return userDAO.findById(id).orElseThrow();
    }
}
```

**传统方式**（直接在类上使用 `@Path`）：
```java
package com.gutou.resources;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/products")
@Produces(MediaType.APPLICATION_JSON)
public class ProductResource {
    
    @Inject
    private ProductDAO productDAO;
    
    @GET
    @Path("/{id}")
    public Product getProduct(@PathParam("id") Long id) {
        return productDAO.findById(id).orElseThrow();
    }
}
```

### 3. 创建新的 DAO

```java
package com.gutou.dao;

import com.gutou.entity.Product;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.SessionFactory;

public class ProductDAO extends AbstractDAO<Product> {
    
    public ProductDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }
    
    // 自定义查询方法
    public List<Product> findAvailableProducts() {
        return currentSession().createQuery(
            "SELECT p FROM Product p WHERE p.status = 'AVAILABLE'", Product.class)
            .getResultList();
    }
}
```

**自动完成的工作：**
1. ✅ 自动创建 `ProductDAO` 实例
2. ✅ 自动绑定到 HK2 容器
3. ✅ 自动注入 SessionFactory
4. ✅ 无需修改主应用类

## 注解说明

### HK2 注解

| 注解 | 作用 | 使用位置 | 说明 |
|------|------|----------|------|
| `@Contract` | 标识服务接口 | 接口类 | 表示该接口是一个可被注入的服务契约 |
| `@Service` | 标识服务实现 | 实现类 | 表示该类是一个服务实现，会被自动扫描和绑定 |
| `@Inject` | 依赖注入 | 字段/构造函数/方法 | 标记需要注入的依赖项 |

### Jersey 注解

| 注解 | 作用 | 使用位置 |
|------|------|----------|
| `@Path` | 定义资源路径 | 类/方法 |
| `@GET`/`@POST`/`@PUT`/`@DELETE` | 定义 HTTP 方法 | 方法 |
| `@Produces` | 定义响应类型 | 类/方法 |
| `@Consumes` | 定义请求类型 | 类/方法 |
| `@PathParam` | 绑定路径参数 | 方法参数 |
| `@QueryParam` | 绑定查询参数 | 方法参数 |

## 依赖注入模式

### 1. 字段注入
```java
public class UserResource {
    @Inject
    private UserDAO userDAO;
    
    @Inject
    private TaskMngApi taskMngApi;
}
```

### 2. 构造函数注入
```java
public class UserResource {
    private final UserDAO userDAO;
    
    @Inject
    public UserResource(UserDAO userDAO) {
        this.userDAO = userDAO;
    }
}
```

### 3. 方法注入
```java
public class UserResource {
    private UserDAO userDAO;
    
    @Inject
    public void setUserDAO(UserDAO userDAO) {
        this.userDAO = userDAO;
    }
}
```

**推荐使用字段注入**，代码更简洁。

## 常见问题与解决方案

### 1. NPE：服务没有被注入

**症状：**
```java
java.lang.NullPointerException: Cannot invoke "com.gutou.core.application.TaskMngApi.execute()" because "this.taskMngApi" is null
```

**原因：**
- Resource 类通过 `new` 创建，而不是由 HK2 管理
- 服务没有被正确绑定到 HK2 容器

**解决方案：**
1. 确保 Resource 类被自动扫描注册（有 `@Path` 注解）
2. 确保服务实现类有 `@Service` 注解
3. 确保服务接口有 `@Contract` 注解
4. 在 Resource 类中使用 `@Inject` 而不是构造函数

### 2. 接口作为 Resource 不工作

**症状：**
- 接口定义了 `@Path`，但 Jersey 找不到端点

**解决方案：**
- 确保接口的具体实现类也被注册到 Jersey
- 或者使用抽象类作为基类

### 3. 方法级别 @Path 不生效

**症状：**
- 方法上有 `@Path` 注解，但访问时 404

**解决方案：**
- `JerseyResourceRegistrar` 会自动扫描方法级别的 `@Path`
- 确保扫描的包路径正确

### 4. 循环依赖

**症状：**
- 两个服务互相注入，导致启动失败

**解决方案：**
- 重构代码，打破循环依赖
- 使用懒加载或 Provider 模式

## 高级用法

### 1. 自定义注解扫描

可以扩展自动扫描机制，支持自定义注解：

```java
// 自定义注解
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoBind {
    Class<?>[] value() default {};
}

// 扩展 Hk2ServiceBinder
public class CustomServiceBinder extends AbstractBinder {
    @Override
    protected void configure() {
        Reflections reflections = new Reflections("com.gutou");
        Set<Class<?>> autoBindClasses = reflections.getTypesAnnotatedWith(AutoBind.class);
        
        for (Class<?> clazz : autoBindClasses) {
            AutoBind annotation = clazz.getAnnotation(AutoBind.class);
            if (annotation.value().length == 0) {
                bindAsContract(clazz);
            } else {
                for (Class<?> toInterface : annotation.value()) {
                    bind(clazz).to(toInterface);
                }
            }
        }
    }
}
```

### 2. 条件绑定

可以根据配置文件决定是否绑定某些服务：

```java
public class ConditionalServiceBinder extends AbstractBinder {
    private final boolean enableFeature;
    
    public ConditionalServiceBinder(boolean enableFeature) {
        this.enableFeature = enableFeature;
    }
    
    @Override
    protected void configure() {
        if (enableFeature) {
            bind(FeatureServiceImpl.class).to(FeatureServiceApi.class);
        }
    }
}
```

### 3. 生命周期管理

HK2 支持自定义生命周期：

```java
@Service
@PerLookup  // 每次查找创建新实例（默认）
// @Singleton  // 单例模式
// @PerThread  // 每个线程一个实例
public class TaskMngImpl implements TaskMngApi {
    // ...
}
```

## 性能优化建议

### 1. 限制扫描范围

```java
// 只扫描必要的包，减少启动时间
Reflections reflections = new Reflections(
    "com.gutou.core",      // 服务包
    "com.gutou.resources", // 资源包
    "com.gutou.dao"        // DAO包
);
```

### 2. 缓存扫描结果

在开发环境可以缓存 Reflections 的扫描结果：

```java
public class CachedServiceBinder extends AbstractBinder {
    private static Set<Class<?>> cachedServiceClasses;
    
    @Override
    protected void configure() {
        if (cachedServiceClasses == null) {
            Reflections reflections = new Reflections("com.gutou");
            cachedServiceClasses = reflections.getTypesAnnotatedWith(Service.class);
        }
        
        // 使用缓存结果
        for (Class<?> serviceClass : cachedServiceClasses) {
            // 绑定逻辑
        }
    }
}
```

### 3. 并行扫描

Reflections 支持并行扫描以提高性能：

```java
Reflections reflections = new Reflections(
    new ConfigurationBuilder()
        .setUrls(ClasspathHelper.forPackage("com.gutou"))
        .setScanners(Scanners.TypesAnnotated, Scanners.SubTypes)
        .setParallel(true)  // 启用并行
);
```

## 总结

通过自动扫描机制，我们实现了：

### ✅ 零配置开发体验
- 添加新服务：只需添加 `@Service` 和 `@Contract` 注解
- 添加新资源：只需添加 `@Path` 注解
- 添加新 DAO：只需继承 `AbstractDAO`
- 无需修改主应用类

### ✅ 依赖注入自动化
- 服务自动绑定
- DAO 自动创建和绑定
- Resource 自动注册
- 依赖自动注入

### ✅ 灵活性和扩展性
- 支持接口、抽象类、具体类
- 支持类级别和方法级别 `@Path`
- 支持自定义扩展

### ✅ 生产就绪
- 日志记录所有自动绑定过程
- 错误处理机制完善
- 性能可优化

这套自动扫描机制显著降低了 Dropwizard + HK2 项目的维护成本，使开发人员可以专注于业务逻辑，而不是框架配置。