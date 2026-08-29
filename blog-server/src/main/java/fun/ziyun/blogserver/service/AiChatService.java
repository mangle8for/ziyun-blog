package fun.ziyun.blogserver.service;

import fun.ziyun.blogserver.dto.AiChatDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 写作对话服务：把「编辑器任务」转发为大模型流式对话。
 *
 * <p>链路：编辑器任务（润色/续写/摘要…）→ 本服务按任务拼提示词 →
 * 默认供应商的 OpenAI 兼容接口（stream=true）→ 逐段转发给前端 SSE。</p>
 */
public interface AiChatService {

    /**
     * 发起流式写作任务。
     *
     * <p>事件约定（前端按事件名消费）：</p>
     * <ul>
     *   <li>delta：data 为 JSON {"t":"增量文本"}，多次推送</li>
     *   <li>done：生成正常结束，data 为 {}</li>
     *   <li>error：data 为 JSON {"message":"原因"}，之后连接关闭</li>
     * </ul>
     *
     * @return SseEmitter（超时 0 = 不设服务端超时，时长由上游接口超时兜底）
     */
    SseEmitter stream(AiChatDTO dto);
}
