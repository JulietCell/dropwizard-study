package com.gutou.app.core;

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
        
        // 查找所有 AbstractDAO 的子类（使用原始类型避免泛型问题）
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
                
                // 绑定到 DAO 类本身（使用原始类型避免泛型问题）
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



