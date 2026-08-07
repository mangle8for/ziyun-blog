package fun.ziyun.blogserver.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置。
 *
 * <p>设计说明（MP 插件机制 —— 对照原生 MyBatis）：</p>
 * <pre>
 * 原生 MyBatis 实现分页要自己写 Interceptor 拦截 Executor，或者
 * 引入 PageHelper —— PageHelper 的拦截器是全局性的，用 ThreadLocal
 * 传参，多线程/嵌套查询下容易「串页」出诡异 bug。
 *
 * MP 的 MybatisPlusInterceptor 也是拦截器实现，但设计更克制：
 *   1. 通过 InnerInterceptor 链式组合，每个插件只管一件事
 *      （分页只管分页、乐观锁只管版本控制）；
 *   2. 分页参数通过方法入参 Page&lt;T&gt; 传递，不依赖 ThreadLocal，
 *      天然线程安全，无串页问题；
 *   3. 分页插件底层用 jsqlparser 解析改写 SQL —— 它拿到的是
 *      你写好的业务 SQL，自动补 COUNT 查询与 LIMIT 分页，
 *      业务代码完全无感（这也是 3.5.9+ 把 jsqlparser 拆成独立
 *      依赖的原因，3.5.7 仍内嵌，所以本项目锁 3.5.7 少一个依赖）。
 * </pre>
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 插件注册入口：所有 InnerInterceptor 都挂在同一个
     * MybatisPlusInterceptor Bean 上，顺序即执行顺序。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页插件：必须指定 DbType.MYSQL，jsqlparser 据此生成对应方言的
        // LIMIT 语法（Oracle 是 ROWNUM / FETCH FIRST，MySQL 是 LIMIT offset, size）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        // 乐观锁插件：配合实体 @Version 字段使用（本项目实体暂未配置，
        // 先注册好组件，后续文章编辑并发场景可直接启用）
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
