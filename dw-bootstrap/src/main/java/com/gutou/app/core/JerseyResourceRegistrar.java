package com.gutou.app.core;

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



