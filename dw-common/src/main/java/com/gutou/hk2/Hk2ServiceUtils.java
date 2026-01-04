package com.gutou.hk2;

import lombok.extern.slf4j.Slf4j;
import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.utilities.BuilderHelper;

import java.util.*;
import java.util.function.Supplier;

@Slf4j
public class Hk2ServiceUtils {

    private Hk2ServiceUtils() {
    }

    /**
     * 查找服务：找不到返回 Optional.empty()
     */
    public static <T> Optional<T> findService(Class<T> type) {
        return Optional.ofNullable(type)
                .flatMap(t -> Optional.ofNullable(getServiceOrNullInternal(t)));
    }

    /**
     * 获取必须存在的服务：找不到就抛异常
     */
    public static <T> T getRequiredService(Class<T> type) {
        return findService(type).orElseThrow(() -> new NoSuchElementException(
                "HK2 service not found: " + (type == null ? "null" : type.getName())
        ));
    }

    /**
     * 获取服务：找不到返回 null（不推荐滥用，但有时方便）
     */
    public static <T> T getServiceOrNull(Class<T> type) {
        return type == null ? null : getServiceOrNullInternal(type);
    }

    /**
     * 列出所有服务描述（descriptor，不触发实例化）
     */
    public static List<ActiveDescriptor<?>> listServiceDescriptors() {
        return withFallback(
                () -> Hk2ServiceLocatorHolder.getLocator().getDescriptors(BuilderHelper.allFilter()),
                Collections::emptyList
        );
    }

    /**
     * 打印所有服务 descriptor 到日志
     */
    public static void logAllServiceDescriptors() {
        List<ActiveDescriptor<?>> descriptors = listServiceDescriptors();

        log.info("一共有{}个服务被HK2容器管理", descriptors.size());

        descriptors.stream()
                .sorted(Comparator.comparing(d -> String.valueOf(d.getImplementation())))
                .forEach(d -> log.info("impl={}, scope={}, name={}, contracts={}",
                        d.getImplementation(),
                        d.getScope(),
                        d.getName(),
                        d.getAdvertisedContracts()));
    }

    // ===================== internal helpers =====================

    private static <T> T getServiceOrNullInternal(Class<T> type) {
        try {
            T service = Hk2ServiceLocatorHolder.getLocator().getService(type);
            if (service == null) {
                log.error("HK2 service is null, type={}", type.getName());
            }
            return service;
        } catch (IllegalStateException e) {
            // locator 未就绪
            log.error("HK2 ServiceLocator not initialized when requesting {}", type.getName(), e);
            return null;
        } catch (RuntimeException e) {
            // 依赖缺失/创建失败等
            log.error("Failed to get HK2 service: {}", type.getName(), e);
            return null;
        }
    }

    /**
     * 执行 supplier，失败就走 fallback（适用于 locator 未初始化或运行时异常）
     */
    private static <T> T withFallback(Supplier<T> supplier, Supplier<T> fallback) {
        try {
            return supplier.get();
        } catch (IllegalStateException e) {
            log.warn("HK2 locator unavailable: {}", e.toString());
            return fallback.get();
        } catch (RuntimeException e) {
            log.warn("HK2 operation failed: {}", e.toString());
            return fallback.get();
        }
    }
}