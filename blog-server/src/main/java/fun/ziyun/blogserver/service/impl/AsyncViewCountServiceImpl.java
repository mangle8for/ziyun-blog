package fun.ziyun.blogserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import fun.ziyun.blogserver.entity.Article;
import fun.ziyun.blogserver.mapper.ArticleMapper;
import fun.ziyun.blogserver.service.AsyncViewCountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 浏览量异步计数实现。
 *
 * <p>设计说明（计数链路与去重策略）：</p>
 * <pre>
 * 链路：详情接口 -> recordView()（@Async 异步提交）-> Redis 去重判断
 *       -> 命中才 UPDATE 自增。
 *
 * 为什么用 Redis 去重：直接每次访问都 UPDATE view_count，刷子一小时内
 * 刷新一万次浏览量虚高一万。用 SETNX 键 view:dedup:{articleId}:{ip}
 * 加 1 小时 TTL —— 同一 IP 一小时内对同一文章只计一次。
 *
 * 为什么计数不精确也不要紧：浏览量是「展示性指标」而非交易数据，
 * 牺牲精度换取性能与抗刷是行业主流做法（对比：库存这种精确数据
 * 绝不能用去重+异步的方案）。
 *
 * 容错取舍（与认证的 fail-closed 对比）：
 *   - 认证依赖 Redis 失败 -> 拒绝服务（安全优先）；
 *   - 浏览量依赖 Redis 失败 -> 静默跳过计数，不影响文章浏览主流程
 *     （统计特性丢了可接受）。日志记录便于事后发现。
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncViewCountServiceImpl implements AsyncViewCountService {

    /** 去重键前缀（TTL 1 小时，与「一小时内只计一次」语义一致） */
    private static final String VIEW_DEDUP_KEY = "view:dedup:";
    private static final Duration DEDUP_TTL = Duration.ofHours(1);

    private final StringRedisTemplate stringRedisTemplate;
    private final ArticleMapper articleMapper;

    @Override
    @Async("asyncExecutor")
    public void recordView(Long articleId, String ip) {
        try {
            // setIfAbsent：键不存在才写入（SETNX 语义），返回 true 表示首次访问。
            // 并发下 Redis 单线程执行保证原子性，不会出现双写双计数。
            Boolean firstView = stringRedisTemplate.opsForValue().setIfAbsent(
                    VIEW_DEDUP_KEY + articleId + ":" + ip, "1", DEDUP_TTL);
            if (Boolean.TRUE.equals(firstView)) {
                // SQL 层原子自增（view_count = view_count + 1）：
                // 不先查后改（并发下会互相覆盖），由数据库行锁保证正确
                articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                        .eq(Article::getId, articleId)
                        .setSql("view_count = view_count + 1"));
            }
        } catch (RedisConnectionFailureException e) {
            // 降级：Redis 不可用跳过计数，不影响主流程（见类注释）
            log.warn("浏览量计数跳过（Redis 不可用）: articleId={}", articleId);
        }
    }
}
