package com.gutou.service.user.service;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.jvnet.hk2.annotations.Service;

@Slf4j
@Service
@Singleton
public class AbcService {

    public AbcService() {
        log.info("new AbcService: " + System.identityHashCode(this));
    }

    @PostConstruct
    public void init() {
        log.info("AbcService init");
        log.info("@PostConstruct 不会“应用一启动就自动跑”。它只会在 这个对象真的被 HK2 创建出来并完成注入之后 才会执行。");
    }

    public void todo() {
        log.info("AbcService todo");
    }
}
