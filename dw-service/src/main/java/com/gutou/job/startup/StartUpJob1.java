package com.gutou.job.startup;

import com.gutou.job.JobService;
import com.gutou.service.user.service.AbcService;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.hk2.api.Rank;
import org.jvnet.hk2.annotations.Service;

@Slf4j
@Service
@Rank(1)
public class StartUpJob1 implements JobService {
    @Inject
    private AbcService abcService;
    @Override
    public void execute() {
        log.info("StartUpJob1 execute");
        abcService.todo();
    }
}
