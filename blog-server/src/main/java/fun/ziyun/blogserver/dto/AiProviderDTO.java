package fun.ziyun.blogserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * AI 供应商新增/更新请求体。
 *
 * <p>apiKey 的双语义：创建时必填；更新时留空 = 保留原 Key 不修改
 * （避免每次编辑其他字段都要重输密钥，也避免明文 Key 在前端回显）。</p>
 */
@Data
public class AiProviderDTO {

    /** 供应商名称（如 智谱GLM） */
    @NotBlank(message = "供应商名称不能为空")
    @Size(max = 50, message = "供应商名称不能超过 50 字")
    private String name;

    /**
     * OpenAI 兼容 Base URL：只到版本号为止（如 https://open.bigmodel.cn/api/paas/v4），
     * /chat/completions 由后端拼接。
     */
    @NotBlank(message = "Base URL 不能为空")
    @Size(max = 255, message = "Base URL 不能超过 255 字")
    private String baseUrl;

    /** API Key：创建必填；更新留空 = 保留原值（明文仅出现在请求瞬间，落库前加密） */
    @Size(max = 200, message = "API Key 不能超过 200 字")
    private String apiKey;

    /** 可选模型 ID 列表（如 ["glm-4.6","glm-4.5-air"]），至少一个 */
    @NotEmpty(message = "至少添加一个模型")
    @Size(max = 20, message = "模型数量不能超过 20 个")
    private List<@NotBlank(message = "模型 ID 不能为空") @Size(max = 100, message = "模型 ID 不能超过 100 字") String> models;

    /** 启用：0-停用 1-启用（缺省按启用处理） */
    private Integer enabled;

    /** 备注 */
    @Size(max = 255, message = "备注不能超过 255 字")
    private String remark;
}
