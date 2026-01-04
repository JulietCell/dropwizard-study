package com.gutou.common.hk2;

import io.dropwizard.hibernate.UnitOfWork;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.jvnet.hk2.annotations.Service;

import java.lang.reflect.Method;

/**
 * HK2 方法拦截器
 * 拦截带有 @UnitOfWork 注解的方法，自动管理 Hibernate 会话和事务
 * 
 * 注意：@Service 注解默认就是 Singleton 作用域，不需要额外添加 @Singleton
 */
@Slf4j
@Service
public class UnitOfWorkMethodInterceptor implements MethodInterceptor {

    public UnitOfWorkMethodInterceptor() {
        log.info("new UnitOfWorkMethodInterceptor: " + System.identityHashCode(this));
    }

    @Inject
    private SessionFactory sessionFactory;

    @Override
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
        Method method = methodInvocation.getMethod();
        UnitOfWork unitOfWork = method.getAnnotation(UnitOfWork.class);

        // 如果没有 @UnitOfWork 注解，直接执行原方法
        if (unitOfWork == null) {
            return methodInvocation.proceed();
        }

        // 检查是否已经有活动的会话（嵌套调用）
        boolean hasExistingSession = ManagedSessionContext.hasBind(sessionFactory);
        Session existingSession = hasExistingSession ? sessionFactory.getCurrentSession() : null;

        // 如果已经有会话，说明是嵌套调用，直接执行方法
        if (existingSession != null && existingSession.isOpen()) {
            log.debug("检测到嵌套 @UnitOfWork 调用，复用现有会话: {}", method.getName());
            return methodInvocation.proceed();
        }

        // 创建新会话并管理事务
        Session session = null;
        try {
            // 打开新会话
            session = sessionFactory.openSession();
            
            // 绑定到当前线程上下文
            ManagedSessionContext.bind(session);
            
            // 开始事务
            session.beginTransaction();
            
            log.debug("开始 @UnitOfWork 事务: {}", method.getName());
            
            try {
                // 执行原方法
                Object result = methodInvocation.proceed();
                
                // 提交事务
                if (session.getTransaction().isActive()) {
                    session.getTransaction().commit();
                    log.debug("提交 @UnitOfWork 事务: {}", method.getName());
                }
                
                return result;
            } catch (Exception e) {
                // 回滚事务
                if (session.getTransaction().isActive()) {
                    session.getTransaction().rollback();
                    log.warn("回滚 @UnitOfWork 事务: {}, 原因: {}", method.getName(), e.getMessage());
                }
                throw e;
            }
        } catch (Throwable t) {
            log.error("执行 @UnitOfWork 方法时发生错误: {}", method.getName(), t);
            throw t;
        } finally {
            // 解绑并关闭会话
            if (session != null) {
                try {
                    ManagedSessionContext.unbind(sessionFactory);
                    if (session.isOpen()) {
                        session.close();
                    }
                } catch (Exception e) {
                    log.error("关闭会话时发生错误: {}", method.getName(), e);
                }
            }
        }
    }
}

