package fun.ziyun.blogserver.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 全局序列化配置。
 *
 * <p>设计说明（雪花 ID 精度问题的经典解法）：</p>
 * <pre>
 * 问题链路：
 *   1. MyBatis-Plus 的 assign_id 主键策略生成 19 位雪花 Long（如 1912876423134150656）；
 *   2. 后端 JSON 序列化默认按数字输出：{"id": 1912876423134150656}；
 *   3. 前端 JS 的 Number 采用 IEEE 754 双精度，安全整数上限 2^53-1 ≈ 9007199254740991，
 *      仅 16 位 —— 19 位的雪花 ID 超出上限后末位被截断舍入；
 *   4. 前端拿着被改写的 id 调详情/更新接口 -> 查无此条 -> 经典玄学 bug。
 *
 * 解法：把 Long 类型统一序列化成字符串 "1912876423134150656"。
 * JSON 字符串不走数值精度运算，前后端无损传递；TS 侧 id 类型定义为 string。
 * 代价：前端排序/比较需字符串化处理（对 ID 场景无影响）。
 *
 * 对比原生写法：需要在每个实体 id 字段上手工加 @JsonSerialize(using=ToStringSerializer.class)，
 * 漏一个就出一个 bug；这里全局注册一次，所有 Long 字段统一生效。
 * </pre>
 */
@Configuration
public class JacksonConfig {

    /**
     * 注册到 Spring Boot 自动配置的 ObjectMapper 上。
     * Jackson2ObjectMapperBuilderCustomizer 是 Boot 提供的扩展点：
     * 它会在框架组装 ObjectMapper 时回调，比直接 new ObjectMapper() 更优雅 ——
     * 不会破坏 Boot 自带的时间格式化、空值策略等默认行为，只是做叠加定制。
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> builder
                // Long 包装类与 long 基本类型都要覆盖，缺一漏一
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance);
    }
}
