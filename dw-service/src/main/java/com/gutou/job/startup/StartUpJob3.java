package com.gutou.job.startup;

import com.gutou.job.JobService;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.hk2.api.Rank;
import org.jvnet.hk2.annotations.Service;

@Slf4j
@Service
@Rank(3)
public class StartUpJob3 implements JobService {
    @Override
    public void execute() {
        log.info("StartUpJob3 execute");
    }
}
