package com.gutou.app.core;

import com.gutou.hk2.Hk2ServiceLocatorHolder;
import com.gutou.job.JobService;
import jakarta.inject.Inject;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.hk2.api.IterableProvider;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;

@Provider
@Slf4j
public class Hk2LocatorCaptureListener implements ApplicationEventListener {
    @Inject
    private ServiceLocator serviceLocator;
    @Inject
    private IterableProvider<JobService> jobServices;

    @Override
    public void onEvent(ApplicationEvent event) {
        if (event.getType() == ApplicationEvent.Type.INITIALIZATION_START) {
            log.info("APP IS INITIALIZATION_START");
        }

        if (event.getType() == ApplicationEvent.Type.INITIALIZATION_FINISHED) {
            log.info("APP IS INITIALIZATION_FINISHED");
            Hk2ServiceLocatorHolder.setLocator(serviceLocator);
            log.info("HK2 ServiceLocator captured: {}", serviceLocator.getName());

            for (JobService jobService : jobServices) {
                jobService.execute();
            }
        }
    }

    @Override
    public RequestEventListener onRequest(RequestEvent requestEvent) {
        return null;
    }
}
