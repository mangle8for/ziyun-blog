package fun.ziyun.blogserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 序列化配置。
 *
 * <p>设计说明（序列化器的选择 —— 经典坑）：</p>
 * <pre>
 * Spring Data Redis 默认使用 JdkSerializationRedisSerializer（JDK 序列化）。
 * 问题：
 *   1. 存入 Redis 的是 Java 对象二进制流，redis-cli 直接看是一堆
 *      \xAC\xED... 乱码，无法人肉排查数据；
 *   2. 跨语言（如其他服务用 Go/Python 读）完全不兼容；
 *   3. key 若也用 JDK 序列化，会带类信息前缀，与直连客户端写入的
 *      key 对不上。
 * 本配置：key 用 StringRedisSerializer（人类可读），value 用
 * GenericJackson2JsonRedisSerializer（JSON，且保留 @class 类型信息，
 * 反序列化时能还原成原类型 —— 代价是存 JSON 体积略大，可接受）。
 * 注意两个序列化器必须配对使用，否则读回的类型对不上。
 * </pre>
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();

        // key / hash key：字符串（便于 redis-cli 查看与跨客户端操作）
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        // value / hash value：JSON（跨语言可读）
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        // 必须调用 afterPropertiesSet 完成初始化（RedisTemplate 非自动装配）
        template.afterPropertiesSet();
        return template;
    }
}
