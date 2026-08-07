package fun.ziyun.blogserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置。
 *
 * <p>设计说明（为什么要自定义线程池 + 参数依据）：</p>
 * <pre>
 * @EnableAsync 开启后，标 @Async 的方法由「任务执行器」提交到线程池执行。
 * 不配置的话 Boot 用 SimpleAsyncTaskExecutor —— 每个任务直接 new 一个线程，
 * 高并发下线程数无上限，直接打爆内存（生产事故级别）。
 *
 * 参数依据（个人博客的浏览量场景）：
 *   - 核心 2 / 最大 4：博客流量低，异步任务（浏览量计数）轻量短暂，
 *     2~4 个线程足够，线程过多反而浪费切换开销；
 *   - 队列 100：流量突发时任务先进队列排队，不立即新建线程
 *     （新建线程成本高，队列是缓冲层）；
 *   - 拒绝策略 CallerRunsPolicy：队列也满时【调用线程自己执行】——
 *     牺牲一点请求延迟换任务不丢失。对比默认 AbortPolicy 直接抛异常丢任务，
 *     与 DiscardPolicy 静默丢弃，CallerRuns 在「延迟」与「可靠性」间取平衡。
 * </pre>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("asyncExecutor")
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("blog-async-");
        // CallerRunsPolicy：拒绝时调用线程（Tomcat 线程）直接执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅关闭：等池内任务执行完再释放
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
