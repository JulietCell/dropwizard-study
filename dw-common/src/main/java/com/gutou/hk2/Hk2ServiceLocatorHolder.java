package com.gutou.hk2;

import org.glassfish.hk2.api.ServiceLocator;

import java.util.concurrent.atomic.AtomicReference;

public final class Hk2ServiceLocatorHolder {
    private static final AtomicReference<ServiceLocator> LOCATOR = new AtomicReference<>();

    private Hk2ServiceLocatorHolder() {
    }

    /**
     * 只允许设置一次（避免被后续不小心覆盖）
     */
    public static void setLocator(ServiceLocator locator) {
        if (locator == null) return;
        LOCATOR.compareAndSet(null, locator);
    }

    public static ServiceLocator getLocator() {
        ServiceLocator locator = LOCATOR.get();
        if (locator == null) {
            throw new IllegalStateException("HK2 ServiceLocator not initialized yet.");
        }
        return locator;
    }
}
