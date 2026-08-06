package fun.ziyun.blogserver;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 紫云博客后端启动类。
 *
 * <p>设计说明：</p>
 * <pre>
 * @SpringBootApplication 由三个注解合成（可展开看源码）：
 *   1. @SpringBootConfiguration —— 声明本类也是配置类；
 *   2. @EnableAutoConfiguration —— 自动配置开关：classpath 上有哪个 starter，
 *      就自动装配哪套能力（如 classpath 有 mysql 驱动就自动配 DataSource）；
 *   3. @ComponentScan(当前包及子包) —— 扫描 @Controller/@Service/@Repository
 *      等组件装配进容器。
 *
 * @MapperScan("fun.ziyun.blogserver.mapper")：
 *   原生 MyBatis 需要给每个 Mapper 接口写 @Mapper 注解，或写 XML 注册；
 *   MP 提供包扫描：把指定包下所有继承 BaseMapper 的接口批量注册成 Bean，
 *   业务代码才能 @Autowired 注入 Mapper。扫描包必须在启动类所在包及其子包内，
 *   否则扫不到（这是初学者最常见的注入失败原因之一）。
 * </pre>
 */
@SpringBootApplication
@MapperScan("fun.ziyun.blogserver.mapper")
public class BlogServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlogServerApplication.class, args);
    }

}
