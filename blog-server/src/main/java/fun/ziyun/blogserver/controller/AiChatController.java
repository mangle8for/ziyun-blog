package fun.ziyun.blogserver.controller;

import fun.ziyun.blogserver.dto.AiChatDTO;
import fun.ziyun.blogserver.service.AiChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 写作对话接口（仅管理员）：SSE 流式代理大模型接口。
 *
 * <p>注意：本接口不是统一 Result 包装 —— SSE 的事件协议见 {@link AiChatService#stream}；
 * 进入流式前的参数/配置错误仍由全局异常处理器输出 Result JSON
 * （前端以响应 Content-Type 区分）。</p>
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Validated
public class AiChatController {

    private final AiChatService aiChatService;

    /**
     * 流式写作任务（润色/续写/摘要/标题/标签/自定义）。
     *
     * <p>X-Accel-Buffering: no —— 显式关闭 nginx 等网关的响应缓冲，
     * 打字机效果不依赖网关 location 配置。</p>
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody @Valid AiChatDTO dto, HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        return aiChatService.stream(dto);
    }
}
