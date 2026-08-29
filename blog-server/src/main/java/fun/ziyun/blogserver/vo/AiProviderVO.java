package fun.ziyun.blogserver.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 供应商视图对象（管理端列表/表单回显）。
 *
 * <p>安全设计：API Key 永不回显明文，只带掩码（如 sk-****abcd），
 * 前端编辑其他字段时 Key 留空提交即表示「保留原值」。</p>
 */
@Data
public class AiProviderVO {

    /** 供应商 ID */
    private Long id;

    /** 供应商名称 */
    private String name;

    /** OpenAI 兼容 Base URL */
    private String baseUrl;

    /** API Key 掩码（仅用于回显提示，非明文） */
    private String apiKeyMasked;

    /** 可选模型 ID 列表 */
    private List<String> models;

    /** 启用：0-停用 1-启用 */
    private Integer enabled;

    /** 写作默认：0-普通 1-默认 */
    private Integer isDefault;

    /** 备注 */
    private String remark;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
