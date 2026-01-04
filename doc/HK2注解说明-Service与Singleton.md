# HK2 注解说明：@Service 与 @Singleton

## 问题：能否同时使用 @Service 和 @Singleton？

### 简短回答

**可以同时使用！** 重要纠正：`@Service` 注解**默认是 `@PerLookup` 作用域**（每次注入创建新实例），**不是 Singleton**。`Hk2ServiceBinder` 会将所有 `@Service` 服务默认设置为 Singleton，以符合常见的使用习惯。如果类上有 `@Singleton` 注解，会自动注册为 Singleton 作用域，让代码更明确地表达意图。

### 详细说明

#### 1. @Service 注解的默认作用域（重要纠正）

**重要发现：** 在 HK2 框架中，`@Service` 注解标记的类**默认是 `@PerLookup` 作用域**，不是 Singleton。这意味着：

```java
@Service
public class MyService {
    // 这个类默认是 @PerLookup，每次注入都会创建新实例
}
```

**实际测试结果：**
- 仅使用 `@Service` → 每次注入创建新实例（PerLookup）
- 使用 `@Service` + `@Singleton` → 单例模式（Singleton）

**Hk2ServiceBinder 的处理方式：**
为了符合常见的使用习惯，`Hk2ServiceBinder` 会将所有 `@Service` 服务**默认设置为 Singleton**，即使类上没有 `@Singleton` 注解。

#### 2. HK2 的作用域机制

HK2 支持以下作用域：

- **@PerLookup（@Service 的默认）**：每次查找时创建新实例
- **@Singleton**：整个应用生命周期只有一个实例
- **@PerThread**：每个线程一个实例
- **自定义作用域**：可以定义自己的作用域

#### 3. Hk2ServiceBinder 的默认行为

`Hk2ServiceBinder` 会将所有 `@Service` 服务**默认设置为 Singleton**，即使类上没有 `@Singleton` 注解。这样做是为了：

1. **符合常见使用习惯**：大多数服务类都应该是单例
2. **避免意外行为**：防止因为忘记添加 `@Singleton` 导致每次注入都创建新实例
3. **代码简洁**：不需要每个服务类都添加 `@Singleton` 注解

#### 4. 什么时候需要显式指定 @Singleton？

虽然 `Hk2ServiceBinder` 默认会将所有服务设置为 Singleton，但建议显式添加 `@Singleton` 注解：

1. **代码可读性**：让代码更明确表达意图（强烈推荐）
2. **明确性**：明确告诉其他开发者这个服务是单例
3. **兼容性**：如果将来更换 Binder 实现，代码仍然正确

```java
// 手动绑定时需要明确指定作用域
bind(MyService.class)
    .to(MyService.class)
    .in(Singleton.class);  // 必须显式指定
```

#### 4. 为什么之前会出现错误？

之前我们遇到的错误：

```
The implementation class com.gutou.common.hk2.UnitOfWorkInterceptionService 
must be in the Singleton scope
```

**原因分析：**

1. **自动扫描 vs 手动绑定冲突**：
   - 类上有 `@Service` 注解 → 被 `Hk2ServiceBinder` 自动扫描并绑定
   - 同时在 `Hk2UnitOfWorkBinder` 中手动绑定
   - 导致同一个类被绑定了两次，可能作用域不一致

2. **InterceptionService 的特殊要求**：
   - `InterceptionService` 实现类必须是 Singleton 作用域
   - 自动扫描时可能没有正确识别作用域

3. **解决方案**：
   - 移除类上的 `@Service` 注解，改为手动绑定
   - 或者在自动扫描时特殊处理拦截器类

#### 5. 当前实现方案

现在我们采用了**混合方案**：

1. **拦截器类添加 @Service 注解**：让它们能被扫描到
2. **在 Hk2ServiceBinder 中特殊处理**：
   - 先手动绑定拦截器（确保正确的作用域和绑定方式）
   - 然后跳过拦截器类，避免重复绑定

```java
private void registerUnitOfWorkInterceptors() {
    // 手动绑定，确保正确的作用域
    bind(UnitOfWorkMethodInterceptor.class)
        .to(UnitOfWorkMethodInterceptor.class)
        .to(MethodInterceptor.class);
    
    bind(UnitOfWorkInterceptionService.class)
        .to(InterceptionService.class);
}

@Override
protected void configure() {
    // 先注册拦截器
    registerUnitOfWorkInterceptors();
    
    // 然后扫描服务，跳过拦截器类
    for (Class<?> serviceClass : sorted) {
        if (serviceClass == UnitOfWorkInterceptionService.class || 
            serviceClass == UnitOfWorkMethodInterceptor.class) {
            continue;  // 跳过，已经手动绑定了
        }
        // ... 绑定其他服务
    }
}
```

## 最佳实践

### 1. 普通服务类（方式一：仅使用 @Service）

```java
@Service  // Hk2ServiceBinder 会默认设置为 Singleton
public class UserServiceImpl implements UserServiceApi {
    // ...
}
```

**注意：** 虽然 HK2 的 `@Service` 默认是 `@PerLookup`，但 `Hk2ServiceBinder` 会将所有服务默认设置为 Singleton。

### 2. 明确指定 Singleton（推荐方式：同时使用 @Service 和 @Singleton）

```java
@Service
@Singleton  // 显式指定 Singleton，代码更明确表达意图（强烈推荐）
public class UserServiceImpl implements UserServiceApi {
    // ...
}
```

**推荐理由：**
- 代码更明确，表达意图清晰
- 不依赖 `Hk2ServiceBinder` 的特殊处理
- 如果将来更换 Binder 实现，代码仍然正确

### 3. 需要其他作用域的服务

```java
@Service
@PerLookup  // 需要显式指定非默认作用域
public class RequestScopedService {
    // ...
}
```

### 3. 手动绑定的服务

```java
// 在 AbstractBinder 中
bind(MyService.class)
    .to(MyService.class)
    .in(Singleton.class);  // 必须显式指定作用域
```

### 4. 拦截器等特殊服务

```java
@Service  // 添加注解以便扫描
@Singleton  // 也可以显式指定，Hk2ServiceBinder 会自动识别
public class MyInterceptor implements MethodInterceptor {
    // ...
}

// 在 Binder 中特殊处理
private void registerInterceptors() {
    bind(MyInterceptor.class)
        .to(MyInterceptor.class)
        .to(MethodInterceptor.class)
        .in(Singleton.class);  // 手动绑定时必须显式指定
}
```

### 5. Hk2ServiceBinder 的默认行为

`Hk2ServiceBinder` 会将所有 `@Service` 服务默认设置为 Singleton：

```java
// 如果类上有 @Singleton 注解
@Service
@Singleton
public class MyService {
    // 会注册为 Singleton 作用域（显式指定）
}

// 如果只有 @Service 注解
@Service
public class MyService {
    // 也会注册为 Singleton 作用域（Hk2ServiceBinder 的默认行为）
    // 但建议添加 @Singleton 注解，让代码更明确
}
```

**实现原理：**
- `Hk2ServiceBinder` 在绑定服务时，会将所有服务默认设置为 Singleton
- 即使类上没有 `@Singleton` 注解，也会调用 `.in(Singleton.class)` 指定作用域
- 这样做是为了符合常见的使用习惯，避免因为忘记添加 `@Singleton` 导致每次注入都创建新实例

## 总结

1. **重要纠正：`@Service` 默认是 `@PerLookup`**，不是 Singleton
2. **Hk2ServiceBinder 的默认行为**：会将所有 `@Service` 服务默认设置为 Singleton
3. **推荐做法**：同时使用 `@Service` 和 `@Singleton`，让代码更明确表达意图
4. **手动绑定时**需要显式指定作用域（`.in(Singleton.class)`）
5. **避免重复绑定**：如果使用 `@Service` 自动扫描，就不要手动绑定；反之亦然
6. **特殊服务**（如拦截器）可以在自动扫描的基础上，在 Binder 中特殊处理

## 参考

- HK2 官方文档：https://javaee.github.io/hk2/
- `@Service` 注解默认作用域：**@PerLookup**（每次注入创建新实例）
- `Hk2ServiceBinder` 默认行为：将所有 `@Service` 服务设置为 Singleton
- 作用域注解优先级：手动绑定 > 注解指定 > 默认值

