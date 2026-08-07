package fun.ziyun.blogserver.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 公共字段自动填充处理器。
 *
 * <p>设计说明（应用层填充 vs 数据库 DEFAULT）：</p>
 * <pre>
 * 方案 A（数据库层）：建表时写 DEFAULT CURRENT_TIMESTAMP，
 *   优点：SQL 少写一列；缺点：INSERT 后 Java 实体里 createTime 仍是 null，
 *   想回显到前端必须再查一次库。
 * 方案 B（本类，应用层）：每次 insert/update 由 MyBatis 插入时回调本类，
 *   把时间值直接写进实体字段再落库 —— 实体与数据库数据完全一致，
 *   后续代码直接用实体字段即可，省一次回查。
 * 方案 C（ORM 框架另设默认值）：不如 B 直观。
 * 主流项目两种都用（DB 默认值兜底 + 应用层填充），本项目选 B 单路径，
 * 保持行为单一，避免同一字段两个赋值来源互相迷惑。
 *
 * strictInsertFill 与 insertFill 的区别：
 *   strict* 系列会先检查实体字段是否为 null，非 null（调用方显式赋值）
 *   时不覆盖 —— 尊重业务显式指定的时间（如导入历史数据场景）。
 * </pre>
 */
@Component
public class MybatisPlusMetaObjectHandler implements MetaObjectHandler {

    /** 插入时填充：createTime 与 updateTime 同时落值（初始即相等） */
    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    /** 更新时只填充 updateTime（createTime 保持首次写入值不变） */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
