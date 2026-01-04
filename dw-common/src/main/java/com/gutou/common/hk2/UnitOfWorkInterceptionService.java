package com.gutou.common.hk2;

import io.dropwizard.hibernate.UnitOfWork;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.ConstructorInterceptor;
import org.aopalliance.intercept.MethodInterceptor;
import org.glassfish.hk2.api.Filter;
import org.glassfish.hk2.api.InterceptionService;
import org.jvnet.hk2.annotations.Service;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * HK2 拦截服务
 * 用于识别需要拦截的方法（带有 @UnitOfWork 注解的方法）
 * 
 * 注意：@Service 注解默认就是 Singleton 作用域，不需要额外添加 @Singleton
 */
@Service
@Slf4j
public class UnitOfWorkInterceptionService implements InterceptionService {

    public UnitOfWorkInterceptionService() {
        log.info("new UnitOfWorkInterceptionService: " + System.identityHashCode(this));
    }

    @Inject
    private UnitOfWorkMethodInterceptor methodInterceptor;

    @Override
    public Filter getDescriptorFilter() {
        // 拦截所有服务
        return descriptor -> true;
    }

    @Override
    public List<MethodInterceptor> getMethodInterceptors(Method method) {
        // 检查方法是否带有 @UnitOfWork 注解
        if (method.isAnnotationPresent(UnitOfWork.class)) {
            // 返回拦截器实例
            return Collections.singletonList(methodInterceptor);
        }
        return Collections.emptyList();
    }

    @Override
    public List<ConstructorInterceptor> getConstructorInterceptors(Constructor<?> constructor) {
        // 构造函数级别不需要拦截
        return Collections.emptyList();
    }
}

