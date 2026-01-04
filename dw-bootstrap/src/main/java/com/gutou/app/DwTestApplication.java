package com.gutou.app;

import com.gutou.app.config.DwTestConfiguration;
import com.gutou.app.core.Hk2DaoBinder;
import com.gutou.app.core.Hk2LocatorCaptureListener;
import com.gutou.app.core.Hk2ServiceBinder;
import com.gutou.app.core.JerseyResourceRegistrar;
import com.gutou.job.executor.GlobalThreadPoolManager;
import com.gutou.model.entity.User;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.hibernate.HibernateBundle;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.hibernate.SessionFactory;

@Slf4j
public class DwTestApplication extends Application<DwTestConfiguration> {

    private final HibernateBundle<DwTestConfiguration> hibernateBundle =
            new HibernateBundle<DwTestConfiguration>(User.class) {
                @Override
                public DataSourceFactory getDataSourceFactory(DwTestConfiguration configuration) {
                    return configuration.getDataSourceFactory();
                }
            };

    public static void main(String[] args) throws Exception {
        new DwTestApplication().run(args);
    }

    @Override
    public String getName() {
        return "dw-test-02";
    }

    @Override
    public void initialize(Bootstrap<DwTestConfiguration> bootstrap) {
        bootstrap.addBundle(hibernateBundle);
    }

    @Override
    public void run(DwTestConfiguration configuration, Environment environment) {
        // 获取 Hibernate SessionFactory
        final SessionFactory sessionFactory = hibernateBundle.getSessionFactory();

        // 注册 HK2 绑定（依赖注入）
        environment.jersey().register(new AbstractBinder() {
            @Override
            protected void configure() {
                // 手动绑定的特殊服务（如 SessionFactory 等）
                bind(sessionFactory).to(SessionFactory.class);
            }
        });

        // 自动扫描并绑定所有 DAO 类（继承 AbstractDAO 的类）
        environment.jersey().register(new Hk2DaoBinder("com.gutou.dao", sessionFactory));

        // 自动扫描并绑定所有 @Service 注解的服务（包含 @UnitOfWork 拦截器注册）
        environment.jersey().register(new Hk2ServiceBinder("com.gutou"));

        // 自动扫描并注册所有 @Path 注解的 Resource 类（支持接口、抽象类、方法级别@Path）
        JerseyResourceRegistrar.registerResources(environment, "com.gutou.resource");

        environment.jersey().register(Hk2LocatorCaptureListener.class);

        // 注册全局线程池管理器（Managed）- 使用单例模式，不需要 @Service 注解
        GlobalThreadPoolManager threadPoolManager = GlobalThreadPoolManager.getInstance();
        environment.lifecycle().manage(threadPoolManager);

        log.info("=== 应用启动完成 ===");
        log.info("IOC 容器信息查看端点: GET /container");
        log.info("容器统计信息: GET /container/summary");
        log.info("按类型查询: GET /container/type/{完整类名}");
    }
}


