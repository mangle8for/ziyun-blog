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
 * AI 模型供应商实体，对应表 ai_provider。
 *
 * <p>设计说明：单管理员博客场景下，供应商数量是个位数，
 * 模型列表（如 glm-4.6 / glm-4.5-air）用 JSON 数组存单列即可，
 * 不值得为「模型的增删改」单独建表拉一套 CRUD。</p>
 */
@Data
@TableName("ai_provider")
public class AiProvider {

    /** 主键，雪花 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 供应商名称（如 智谱GLM） */
    private String name;

    /** OpenAI 兼容 Base URL（如 https://open.bigmodel.cn/api/paas/v4） */
    private String baseUrl;

    /** API Key（AES/GCM 加密后的 Base64[iv+密文]，库中永不存明文） */
    private String apiKey;

    /** 可选模型 ID 的 JSON 数组字符串（如 ["glm-4.6","glm-4.5-air"]） */
    private String models;

    /** 启用：0-停用 1-启用 */
    private Integer enabled;

    /** 写作默认：0-普通 1-默认（全局仅一个） */
    private Integer isDefault;

    /** 备注 */
    private String remark;

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
