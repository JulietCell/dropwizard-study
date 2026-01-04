package com.gutou.job.executor;

import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 全局线程池管理器（单例模式）
 * 继承 Managed 接口，在应用启动时初始化线程池，在应用关闭时优雅关闭
 * 注意：不需要 @Service 注解，因为它是单例模式，不依赖其他服务
 */
@Slf4j
public class GlobalThreadPoolManager implements Managed {

    private static volatile GlobalThreadPoolManager instance;
    private static final Object lock = new Object();

    private ExecutorService executorService;
    private ScheduledExecutorService scheduledExecutorService;
    
    // 线程池配置
    private static final int CORE_POOL_SIZE = 10;
    private static final int MAX_POOL_SIZE = 50;
    private static final long KEEP_ALIVE_TIME = 60L;
    private static final int QUEUE_CAPACITY = 1000;

    /**
     * 私有构造函数，确保单例模式
     */
    private GlobalThreadPoolManager() {
        // 防止外部实例化
    }

    /**
     * 获取单例实例（双重检查锁定模式）
     * 
     * @return GlobalThreadPoolManager 单例实例
     */
    public static GlobalThreadPoolManager getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new GlobalThreadPoolManager();
                }
            }
        }
        return instance;
    }

    /**
     * 应用启动时初始化线程池
     */
    @Override
    public void start() throws Exception {
        log.info("GlobalThreadPoolManager 启动中...");
        
        // 创建普通线程池
        executorService = new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            MAX_POOL_SIZE,
            KEEP_ALIVE_TIME,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(QUEUE_CAPACITY),
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);
                
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "global-pool-" + threadNumber.getAndIncrement());
                    thread.setDaemon(false);
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：调用者运行
        );
        
        // 创建定时任务线程池
        scheduledExecutorService = Executors.newScheduledThreadPool(
            CORE_POOL_SIZE,
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);
                
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "global-scheduled-pool-" + threadNumber.getAndIncrement());
                    thread.setDaemon(false);
                    return thread;
                }
            }
        );
        
        log.info("GlobalThreadPoolManager 启动完成 - 核心线程数: {}, 最大线程数: {}, 队列容量: {}",
            CORE_POOL_SIZE, MAX_POOL_SIZE, QUEUE_CAPACITY);
    }

    /**
     * 应用关闭时优雅关闭线程池
     */
    @Override
    public void stop() throws Exception {
        log.info("GlobalThreadPoolManager 关闭中...");
        
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdown();
            try {
                if (!scheduledExecutorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warn("定时任务线程池未能在10秒内关闭，强制关闭");
                    scheduledExecutorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("关闭定时任务线程池时被中断", e);
                scheduledExecutorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warn("线程池未能在10秒内关闭，强制关闭");
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("关闭线程池时被中断", e);
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        log.info("GlobalThreadPoolManager 关闭完成");
    }

    /**
     * 获取普通线程池
     * 
     * @return ExecutorService 线程池实例
     */
    public ExecutorService getExecutorService() {
        if (executorService == null) {
            throw new IllegalStateException("线程池尚未初始化，请确保 GlobalThreadPoolManager 已启动");
        }
        return executorService;
    }

    /**
     * 获取定时任务线程池
     * 
     * @return ScheduledExecutorService 定时任务线程池实例
     */
    public ScheduledExecutorService getScheduledExecutorService() {
        if (scheduledExecutorService == null) {
            throw new IllegalStateException("定时任务线程池尚未初始化，请确保 GlobalThreadPoolManager 已启动");
        }
        return scheduledExecutorService;
    }

    /**
     * 提交异步任务
     * 
     * @param task 要执行的任务
     * @return Future 对象，可用于获取任务执行结果
     */
    public <T> Future<T> submit(Callable<T> task) {
        return getExecutorService().submit(task);
    }

    /**
     * 提交异步任务（无返回值）
     * 
     * @param task 要执行的任务
     */
    public void execute(Runnable task) {
        getExecutorService().execute(task);
    }

    /**
     * 提交定时任务
     * 
     * @param task 要执行的任务
     * @param delay 延迟时间
     * @param unit 时间单位
     * @return ScheduledFuture 对象
     */
    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        return getScheduledExecutorService().schedule(task, delay, unit);
    }

    /**
     * 提交周期性任务（固定延迟）
     * 
     * @param task 要执行的任务
     * @param initialDelay 初始延迟时间
     * @param period 周期时间
     * @param unit 时间单位
     * @return ScheduledFuture 对象
     */
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        return getScheduledExecutorService().scheduleAtFixedRate(task, initialDelay, period, unit);
    }

    /**
     * 提交周期性任务（固定间隔）
     * 
     * @param task 要执行的任务
     * @param initialDelay 初始延迟时间
     * @param delay 间隔时间
     * @param unit 时间单位
     * @return ScheduledFuture 对象
     */
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit) {
        return getScheduledExecutorService().scheduleWithFixedDelay(task, initialDelay, delay, unit);
    }

    /**
     * 获取线程池状态信息
     * 
     * @return 线程池状态信息字符串
     */
    public String getStatus() {
        if (executorService instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor tpe = (ThreadPoolExecutor) executorService;
            return String.format(
                "线程池状态 - 核心线程数: %d, 当前线程数: %d, 最大线程数: %d, 队列大小: %d, 已完成任务数: %d",
                tpe.getCorePoolSize(),
                tpe.getActiveCount(),
                tpe.getMaximumPoolSize(),
                tpe.getQueue().size(),
                tpe.getCompletedTaskCount()
            );
        }
        return "线程池状态 - 无法获取详细信息";
    }
}

