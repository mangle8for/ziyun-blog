package fun.ziyun.blogserver.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，对应表 `user`。
 *
 * <p>设计说明（MyBatis-Plus 注解与原生 MyBatis 对照）：</p>
 * <pre>
 * 原生 MyBatis 的实体是「纯 POJO」，字段与表列名的映射关系全部写在
 * Mapper XML 的 resultMap 里，每张表都要手写一份冗长映射；
 * MP 用注解把映射声明直接放在实体上：
 *   @TableName  —— 实体 -> 表名（默认驼峰转下划线，user 无下划线可省略，
 *                  但 user 是 MySQL 函数名，显式反引号转义最稳）
 *   @TableId   —— 主键声明 + 主键生成策略
 *   @TableLogic —— 逻辑删除字段：所有自动 SQL 追加 deleted=0 条件
 *   @TableField —— 字段级补充说明（此处用于自动填充）
 * </pre>
 */
@Data
@TableName("`user`")
public class User {

    /**
     * 主键：IdType.ASSIGN_ID = 雪花算法由应用层生成（配合全局配置）。
     * 注意实体上的注解优先级高于全局配置，写在这里更直观。
     * 序列化：JacksonConfig 已全局把 Long 转 String，前端拿到的是
     * "1912876423134150656" 而非数字，避免 JS 精度丢失。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户名（唯一索引 uk_username 兜底防重） */
    private String username;

    /**
     * 密码哈希。
     * @JsonIgnore：实体被序列化时排除本字段 —— 如果直接把实体返回给前端，
     * 密码哈希会泄漏出去（泄露哈希后可用字典/彩虹表离线爆破）。
     * P2 接入认证后会改用 UserVO 层彻底隔离，这里先双保险。
     */
    @JsonIgnore
    private String password;

    private String nickname;

    private String avatar;

    private String email;

    /** 角色：0-普通用户 1-管理员（TINYINT 映射 int） */
    private Integer role;

    /** 状态：0-禁用 1-正常 */
    private Integer status;

    /**
     * 创建时间自动填充。
     * FieldFill.INSERT = 仅插入时由 MetaObjectHandler 填充，
     * 不依赖数据库 DEFAULT CURRENT_TIMESTAMP —— 应用层填充的好处：
     * 插入后实体对象本身就带着时间值，无需回查数据库。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间：插入与更新时都自动填充（对应数据库 ON UPDATE CURRENT_TIMESTAMP） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记：@TableLogic 声明后，
     * MP 会把 deleteById 自动改写成 UPDATE ... SET deleted=1，
     * 查询自动追加 AND deleted=0 —— 数据不会物理消失。
     */
    @TableLogic
    private Integer deleted;
}
