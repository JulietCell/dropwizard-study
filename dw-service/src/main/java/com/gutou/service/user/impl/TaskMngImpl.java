package com.gutou.service.user.impl;

import com.gutou.service.user.TaskMngApi;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.jvnet.hk2.annotations.Service;

@Service
@Slf4j
@Singleton
public class TaskMngImpl implements TaskMngApi {

    public TaskMngImpl() {
        log.info("new TaskMngImpl {}", System.identityHashCode(this));
    }

    @Override
    public void execute() {
        log.info("TaskMngImpl execute");
    }
}

