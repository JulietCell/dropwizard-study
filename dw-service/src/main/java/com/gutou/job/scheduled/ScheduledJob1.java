package com.gutou.job.scheduled;

import com.gutou.job.JobService;
import com.gutou.job.executor.GlobalThreadPoolManager;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.hk2.api.Rank;
import org.jvnet.hk2.annotations.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Rank(4)
@Service
public class ScheduledJob1 implements JobService {
    @Override
    public void execute() {
        log.info("ScheduledJob1 execute");
        Runnable runnable = ()-> log.info("scheduleAtFixedRate ScheduledJob1");
        GlobalThreadPoolManager.getInstance().scheduleAtFixedRate(runnable,0,5, TimeUnit.SECONDS);
    }
}
