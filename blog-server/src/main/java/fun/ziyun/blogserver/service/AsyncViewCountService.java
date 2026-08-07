package fun.ziyun.blogserver.service;

/**
 * 浏览量异步计数服务。
 * 独立成 Service 的原因：@Async 方法必须在独立 Bean 上才能被
 * Spring 代理拦截 —— 若写在业务 Service 内部用 this 调用，
 * 代理不生效（经典的 @Async 失效坑），故拆出专门的服务类。
 */
public interface AsyncViewCountService {

    /**
     * 记录一次文章访问（异步执行，不阻塞请求线程）。
     *
     * @param articleId 文章 ID
     * @param ip        访客 IP（用于短时去重防刷）
     */
    void recordView(Long articleId, String ip);
}
