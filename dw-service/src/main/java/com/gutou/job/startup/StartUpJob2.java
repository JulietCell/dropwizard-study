package com.gutou.job.startup;

import com.gutou.job.JobService;
import com.gutou.model.entity.User;
import com.gutou.service.user.UserServiceApi;
import io.dropwizard.hibernate.UnitOfWork;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.hk2.api.Rank;
import org.jvnet.hk2.annotations.Service;

import java.util.List;

@Slf4j
@Service
@Rank(2)
public class StartUpJob2 implements JobService {
    @Inject
    private UserServiceApi userServiceApi;

    @Override
    @UnitOfWork
    public void execute() {
        log.info("StartUpJob2 execute");
        // 现在可以直接调用服务方法，@UnitOfWork 注解会自动管理会话和事务
        List<Long> list = userServiceApi.findAll().stream().map(User::getId).toList();
        log.info("all list is:{}", list);
    }
}
