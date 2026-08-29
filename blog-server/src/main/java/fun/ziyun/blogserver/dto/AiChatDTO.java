package fun.ziyun.blogserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 写作任务请求体（POST /api/v1/ai/chat/stream，SSE 流式响应）。
 */
@Data
public class AiChatDTO {

    /** 任务类型：polish-润色选中 continue-续写 summary-摘要 title-标题建议 tags-标签推荐 custom-自定义指令 */
    @NotBlank(message = "任务类型不能为空")
    @Pattern(regexp = "polish|continue|summary|title|tags|custom",
            message = "不支持的任务类型")
    private String task;

    /** 处理对象文本：选中文本 / 光标前文（续写）/ 正文纯文本（摘要、标题、标签） */
    @Size(max = 20000, message = "文本内容过长")
    private String text;

    /** 附加上下文：如标签推荐的候选标签列表 */
    @Size(max = 4000, message = "附加上下文过长")
    private String context;

    /** 自定义指令（task=custom 时必填，由 Service 校验） */
    @Size(max = 1000, message = "自定义指令不能超过 1000 字")
    private String instruction;
}
