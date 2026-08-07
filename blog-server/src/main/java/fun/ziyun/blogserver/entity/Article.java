package fun.ziyun.blogserver.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章实体，对应表 article。
 *
 * <p>设计说明：实体只承载「表结构本身」，不做展示层字段拼装 ——
 * 分类名、标签列表、作者昵称等关联信息属于展示需求，
 * 由 Service 层组装成 VO 返回，避免实体被业务查询带偏
 * （比如为了列表展示给实体加一堆 @TableField(exist=false) 的冗余字段）。
 * 这是 DTO/VO 分层隔离的体现：实体 = 表的投影，VO = 接口的契约。</p>
 */
@Data
@TableName("article")
public class Article {

    /** 主键，雪花 ID（Long -> JSON String 由全局 JacksonConfig 保证） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 标题 */
    private String title;

    /** 摘要（列表页展示） */
    private String summary;

    /** 正文：Markdown 原文（LONGTEXT），前台由 md-editor-v3 的 MdPreview 渲染 */
    private String content;

    /** 封面图 URL（MinIO 对象地址） */
    private String cover;

    /**
     * 分类 ID（逻辑外键，可空 = 未分类）。
     * 为什么可空：新建文章时常先不选分类，强制 NOT NULL 会逼用户补全
     * 不必要的信息，破坏「先草稿后完善」的写作流。
     */
    private Long categoryId;

    /** 作者 ID（逻辑外键 -> user.id） */
    private Long authorId;

    /** 状态：0-草稿 1-发布 */
    private Integer status;

    /** 浏览量（P2 起由异步任务累加） */
    private Integer viewCount;

    /** 点赞数（v1 预留字段，暂无点赞接口） */
    private Integer likeCount;

    /** 创建时间（INSERT 时自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间（INSERT/UPDATE 时自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记 */
    @TableLogic
    private Integer deleted;
}
