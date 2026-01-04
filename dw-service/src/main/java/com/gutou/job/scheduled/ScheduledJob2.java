package com.gutou.job.scheduled;

import com.gutou.job.JobService;
import com.gutou.job.executor.GlobalThreadPoolManager;
import com.gutou.model.entity.User;
import com.gutou.service.user.UserServiceApi;
import com.gutou.service.user.service.AbcService;
import io.dropwizard.hibernate.UnitOfWork;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.hk2.api.Rank;
import org.jvnet.hk2.annotations.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Rank(5)
@Service
public class ScheduledJob2 implements JobService {
    @Inject
    private UserServiceApi userServiceApi;
    @Inject
    private AbcService abcService;

    @Override
    public void execute() {
        log.info("ScheduledJob2 execute");
        Runnable runnable = this::doScheduledWork;
        GlobalThreadPoolManager.getInstance().scheduleAtFixedRate(runnable, 1, 5, TimeUnit.SECONDS);
    }

    /**
     * 定时任务的具体工作方法
     * 使用 @UnitOfWork 注解自动管理事务
     */
    @UnitOfWork
    private void doScheduledWork() {
        log.info("scheduleAtFixedRate ScheduledJob2");
        List<Long> list = userServiceApi.findAll().stream().map(User::getId).toList();
        log.info("all list is:{}", list);
        abcService.todo();
    }
}
