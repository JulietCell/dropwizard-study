package com.gutou.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.hk2.api.Descriptor;
import org.glassfish.hk2.api.Filter;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * HK2 IOC 容器查看工具
 * 用于查看容器中注册的所有服务
 */
@Path("/container")
@Produces(MediaType.APPLICATION_JSON)
public class ContainerInspectorResource {

    private static final Logger log = LoggerFactory.getLogger(ContainerInspectorResource.class);
    
    private final ServiceLocator serviceLocator;

    @Inject
    public ContainerInspectorResource(ServiceLocator serviceLocator) {
        this.serviceLocator = serviceLocator;
    }

    /**
     * 获取容器中所有注册的服务信息
     */
    @GET
    public Response getAllServices() {
        try {
            Map<String, Object> result = new HashMap<>();
            
            // 获取所有服务描述符（使用 Filter.all() 的替代方式）
            @SuppressWarnings("unchecked")
            List<Descriptor> allDescriptors = (List<Descriptor>) (List<?>) serviceLocator.getDescriptors(new Filter() {
                @Override
                public boolean matches(Descriptor d) {
                    return true; // 匹配所有描述符
                }
            });
            
            List<Map<String, Object>> services = new ArrayList<>();
            
            for (Descriptor descriptor : allDescriptors) {
                Map<String, Object> serviceInfo = new HashMap<>();
                serviceInfo.put("serviceType", descriptor.getImplementation());
                serviceInfo.put("contracts", descriptor.getAdvertisedContracts());
                serviceInfo.put("scope", descriptor.getScope());
                serviceInfo.put("rank", descriptor.getRanking());
                serviceInfo.put("name", descriptor.getName());
                
                // 尝试获取服务实例（如果已创建）
                try {
                    String implClass = descriptor.getImplementation();
                    if (implClass != null) {
                        Class<?> clazz = Class.forName(implClass);
                        Object instance = serviceLocator.getService(clazz);
                        serviceInfo.put("instance", instance != null ? instance.getClass().getName() : "null");
                        serviceInfo.put("instanceHashCode", instance != null ? instance.hashCode() : null);
                    } else {
                        serviceInfo.put("instance", "not available");
                    }
                } catch (Exception e) {
                    serviceInfo.put("instance", "not created yet");
                    serviceInfo.put("error", e.getMessage());
                }
                
                services.add(serviceInfo);
            }
            
            result.put("totalServices", services.size());
            result.put("services", services);
            
            // 按服务类型分组统计
            Map<String, Long> typeCount = services.stream()
                .map(s -> (String) s.get("serviceType"))
                .collect(Collectors.groupingBy(
                    type -> type != null ? type : "unknown",
                    Collectors.counting()
                ));
            result.put("typeStatistics", typeCount);
            
            log.info("查询容器信息: 共 {} 个服务", services.size());
            
            return Response.ok(result).build();
            
        } catch (Exception e) {
            log.error("获取容器信息失败", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }

    /**
     * 获取指定类型的服务信息
     */
    @GET
    @Path("/type/{typeName}")
    public Response getServicesByType(@jakarta.ws.rs.PathParam("typeName") String typeName) {
        try {
            Class<?> clazz = Class.forName(typeName);
            
            // 直接通过类型获取服务
            @SuppressWarnings("unchecked")
            List<ServiceHandle<?>> handles = (List<ServiceHandle<?>>) (List<?>) serviceLocator.getAllServiceHandles(clazz);
            
            List<Map<String, Object>> services = new ArrayList<>();
            for (ServiceHandle<?> handle : handles) {
                Descriptor descriptor = handle.getActiveDescriptor();
                Map<String, Object> serviceInfo = new HashMap<>();
                serviceInfo.put("serviceType", descriptor.getImplementation());
                serviceInfo.put("contracts", descriptor.getAdvertisedContracts());
                
                try {
                    Object instance = handle.getService();
                    serviceInfo.put("instance", instance != null ? instance.getClass().getName() : "null");
                    serviceInfo.put("instanceHashCode", instance != null ? instance.hashCode() : null);
                } catch (Exception e) {
                    serviceInfo.put("instance", "not created yet");
                }
                
                services.add(serviceInfo);
            }
            
            return Response.ok(Map.of(
                "type", typeName,
                "count", services.size(),
                "services", services
            )).build();
            
        } catch (ClassNotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Type not found: " + typeName))
                .build();
        } catch (Exception e) {
            log.error("查询服务失败", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }

    /**
     * 获取容器统计信息（简化版）
     */
    @GET
    @Path("/summary")
    public Response getSummary() {
        try {
            @SuppressWarnings("unchecked")
            List<Descriptor> allDescriptors = (List<Descriptor>) (List<?>) serviceLocator.getDescriptors(new Filter() {
                @Override
                public boolean matches(Descriptor d) {
                    return true; // 匹配所有描述符
                }
            });
            
            Map<String, Object> summary = new HashMap<>();
            summary.put("totalServices", allDescriptors.size());
            
            // 按包名分组
            Map<String, Long> packageCount = allDescriptors.stream()
                .map(Descriptor::getImplementation)
                .filter(Objects::nonNull)
                .map(type -> {
                    int lastDot = type.lastIndexOf('.');
                    return lastDot > 0 ? type.substring(0, lastDot) : "default";
                })
                .collect(Collectors.groupingBy(
                    pkg -> pkg,
                    Collectors.counting()
                ));
            summary.put("packageStatistics", packageCount);
            
            return Response.ok(summary).build();
            
        } catch (Exception e) {
            log.error("获取统计信息失败", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
}

