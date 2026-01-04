package com.gutou.app.core;

import com.gutou.common.hk2.UnitOfWorkInterceptionService;
import com.gutou.common.hk2.UnitOfWorkMethodInterceptor;
import jakarta.inject.Singleton;
import org.aopalliance.intercept.MethodInterceptor;
import org.glassfish.hk2.api.InterceptionService;
import org.glassfish.hk2.api.Rank;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.hk2.utilities.binding.ServiceBindingBuilder;
import org.jvnet.hk2.annotations.Contract;
import org.jvnet.hk2.annotations.Service;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * HK2 服务自动绑定工具类
 * 自动扫描带有 @Service 注解的类，并绑定到它们实现的 @Contract 接口
 * 同时自动注册 @UnitOfWork 拦截器支持
 */
public class Hk2ServiceBinder extends AbstractBinder {

    private static final Logger log = LoggerFactory.getLogger(Hk2ServiceBinder.class);
    private final String basePackage;

    public Hk2ServiceBinder(String basePackage) {
        this.basePackage = basePackage;
    }

    @Override
    protected void configure() {
        // 1. 先注册 @UnitOfWork 拦截器（必须在服务绑定之前）
        registerUnitOfWorkInterceptors();
        
        // 2. 扫描并绑定所有 @Service 注解的服务
        Reflections reflections = new Reflections(basePackage, Scanners.TypesAnnotated);

        Set<Class<?>> serviceClasses = reflections.getTypesAnnotatedWith(Service.class);
        log.info("发现 {} 个 @Service 注解的服务类", serviceClasses.size());

        // 建议：为了 serviceId/绑定顺序稳定，可先排个序（可选）
        List<Class<?>> sorted = new ArrayList<>(serviceClasses);
        sorted.sort(Comparator.comparing(Class::getName));

        for (Class<?> serviceClass : sorted) {
            // 跳过拦截器类，它们已经在上面的 registerUnitOfWorkInterceptors 中处理了
            if (serviceClass == UnitOfWorkInterceptionService.class || 
                serviceClass == UnitOfWorkMethodInterceptor.class) {
                continue;
            }
            
            int rank = getRank(serviceClass);
            // 重要：@Service 注解默认是 @PerLookup（每次注入创建新实例），不是 Singleton
            // 为了符合常见的使用习惯，Hk2ServiceBinder 会将所有 @Service 服务默认设置为 Singleton
            // 如果类上有 @Singleton 注解，明确使用 Singleton；否则也默认设置为 Singleton
            boolean isSingleton = true; // 默认都设置为 Singleton，符合常见使用习惯

            Set<Class<?>> contractInterfaces = Arrays.stream(serviceClass.getInterfaces())
                    .filter(iface -> iface.isAnnotationPresent(Contract.class))
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            if (contractInterfaces.isEmpty()) {
                String scopeInfo = isSingleton ? " (Singleton)" : "";
                log.info("绑定服务: {} (无 @Contract 接口), rank={}{}", serviceClass.getName(), rank, scopeInfo);
                ServiceBindingBuilder<?> builder = bindAsContract(serviceClass);
                builder.ranked(rank);
                if (isSingleton) {
                    builder.in(Singleton.class);
                }
            } else {
                for (Class<?> contractInterface : contractInterfaces) {
                    String scopeInfo = isSingleton ? " (Singleton)" : "";
                    log.info("绑定服务: {} -> {}, rank={}{}", serviceClass.getName(), contractInterface.getName(), rank, scopeInfo);
                    ServiceBindingBuilder<?> b = bind(serviceClass).to(contractInterface);
                    b.ranked(rank);
                    if (isSingleton) {
                        b.in(Singleton.class);
                    }
                }
            }
        }
    }

    /**
     * 注册 @UnitOfWork 拦截器支持
     * 这些拦截器需要特殊绑定，不能像普通服务那样处理
     * 注意：手动绑定时必须显式指定 Singleton 作用域，即使类上有 @Service 注解
     */
    private void registerUnitOfWorkInterceptors() {
        log.info("注册 @UnitOfWork 拦截器支持");
        
        // 先注册方法拦截器（执行实际的拦截逻辑）
        // 需要同时绑定到具体类型和接口类型，以便其他类可以注入
        bind(UnitOfWorkMethodInterceptor.class)
            .to(UnitOfWorkMethodInterceptor.class)  // 绑定到具体类型，供 UnitOfWorkInterceptionService 注入
            .to(MethodInterceptor.class)              // 绑定到接口类型，供 HK2 拦截机制使用
            .in(Singleton.class);                     // 必须显式指定 Singleton 作用域
        
        // 然后注册拦截服务（识别需要拦截的方法）
        // InterceptionService 实现类必须是 Singleton 作用域
        bind(UnitOfWorkInterceptionService.class)
            .to(InterceptionService.class)
            .in(Singleton.class);                     // 必须显式指定 Singleton 作用域
        
        log.info("@UnitOfWork 拦截器注册完成");
    }

    /**
     * 检查类是否有 @Singleton 注解
     * 支持同时使用 @Service 和 @Singleton 注解
     * 
     * @param serviceClass 服务类
     * @return 如果有 @Singleton 注解返回 true，否则返回 false
     */
    private boolean isSingleton(Class<?> serviceClass) {
        return serviceClass.isAnnotationPresent(Singleton.class);
    }

    private int getRank(Class<?> serviceClass) {
        Rank r = serviceClass.getAnnotation(Rank.class);
//        return (r == null) ? 0 : r.value();
        // 默认是Rank越大优先级越高，当然也可以反过来设置，如下
        // 你的业务语义：数字越小越优先
        int original = (r == null) ? 0 : r.value();

        // 反转给 HK2：original 越小 => hk2Rank 越大（越优先）
        return Integer.MAX_VALUE - original;
    }
}



