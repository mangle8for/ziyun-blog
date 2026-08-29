package fun.ziyun.blogserver.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fun.ziyun.blogserver.common.ResultCode;
import fun.ziyun.blogserver.config.AiProperties;
import fun.ziyun.blogserver.dto.AiChatDTO;
import fun.ziyun.blogserver.entity.AiProvider;
import fun.ziyun.blogserver.exception.BusinessException;
import fun.ziyun.blogserver.service.AiChatService;
import fun.ziyun.blogserver.service.AiProviderService;
import fun.ziyun.blogserver.util.AiCryptoUtil;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * AI 写作对话服务实现：SSE 流式代理。
 *
 * <p>技术选型说明：</p>
 * <pre>
 * 1. 项目是 Spring MVC（无 WebFlux），流式响应用 SseEmitter；
 * 2. 上游调用用 JDK 17 自带 java.net.http.HttpClient（零新增依赖），
 *    BodyHandlers.ofLines() 按行读流式响应体，天然可被 interrupt 中断 ——
 *    前端取消（AbortController）→ SseEmitter 回调 → future.cancel(true)
 *    → 断开上游连接，token 不再继续消耗；
 * 3. 独立小线程池（守护线程）：长连接流转发不能占用 AsyncConfig 里
 *    给浏览量计数准备的小业务池。
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private final AiProviderService aiProviderService;
    private final AiCryptoUtil aiCryptoUtil;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    private final ExecutorService streamExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread thread = new Thread(r, "ai-stream-" + SEQUENCE.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Override
    public SseEmitter stream(AiChatDTO dto) {
        String task = dto.getTask();
        if ("custom".equals(task) && (dto.getInstruction() == null || dto.getInstruction().isBlank())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "自定义指令不能为空");
        }
        String text = dto.getText() == null ? "" : dto.getText();
        if (text.isBlank() && !"continue".equals(task)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "处理内容不能为空");
        }
        // 超长正文截断：控制 token 成本，摘要/标题/标签场景足够
        final String finalText = text.length() > aiProperties.getMaxContextChars()
                ? text.substring(0, aiProperties.getMaxContextChars())
                : text;

        AiProvider provider = aiProviderService.getDefaultEnabled();
        List<String> models = parseModels(provider);
        String plainKey = aiCryptoUtil.decrypt(provider.getApiKey());

        SseEmitter emitter = new SseEmitter(0L);
        AtomicReference<Future<?>> running = new AtomicReference<>();
        // 客户端断开/超时/出错时取消上游请求（中断阻塞读 → 连接释放）
        Consumer<String> cancel = reason -> {
            Future<?> future = running.get();
            if (future != null && !future.isDone()) {
                log.info("AI 流式任务取消（{}），task={}", reason, task);
                future.cancel(true);
            }
        };
        emitter.onCompletion(() -> cancel.accept("客户端断开"));
        emitter.onTimeout(() -> cancel.accept("超时"));
        emitter.onError(t -> cancel.accept("错误"));

        Runnable job = () -> doStream(emitter, provider, plainKey, models, dto, finalText);
        running.set(streamExecutor.submit(job));
        return emitter;
    }

    /** 阻塞式拉取上游流并逐段转发（运行在 ai-stream 线程上，可被 interrupt 中断） */
    private void doStream(SseEmitter emitter, AiProvider provider, String plainKey, List<String> models,
                          AiChatDTO dto, String text) {
        String model = models.get(0);
        TaskSpec spec = buildSpec(dto, text);
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("stream", true);
            body.put("max_tokens", spec.maxTokens());
            body.put("temperature", spec.temperature());
            ArrayNode messages = body.putArray("messages");
            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", spec.systemPrompt());
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            user.put("content", spec.userPrompt());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(provider.getBaseUrl() + "/chat/completions"))
                    .timeout(Duration.ofSeconds(aiProperties.getReadTimeoutSeconds()))
                    .header("Authorization", "Bearer " + plainKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<java.util.stream.Stream<String>> response =
                    httpClient().send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() != 200) {
                String errorBody = response.body().collect(java.util.stream.Collectors.joining(" "));
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "上游返回 " + response.statusCode() + "：" + abbreviate(errorBody));
            }

            response.body().forEach(line -> {
                if (!line.startsWith("data:")) {
                    return;
                }
                String payload = line.substring(5).trim();
                if (payload.isEmpty() || "[DONE]".equals(payload)) {
                    return;
                }
                try {
                    JsonNode root = objectMapper.readTree(payload);
                    JsonNode delta = root.path("choices").path(0).path("delta").path("content");
                    if (delta.isMissingNode() || delta.isNull()) {
                        return;
                    }
                    String chunk = delta.asText();
                    if (!chunk.isEmpty()) {
                        ObjectNode event = objectMapper.createObjectNode();
                        event.put("t", chunk);
                        emitter.send(SseEmitter.event().name("delta")
                                .data(objectMapper.writeValueAsString(event)));
                    }
                } catch (Exception parseEx) {
                    log.warn("AI 上游数据行解析失败，跳过：{}", abbreviate(payload));
                }
            });

            emitter.send(SseEmitter.event().name("done").data("{}"));
            emitter.complete();
        } catch (InterruptedException e) {
            // 前端主动取消：上游连接已随中断释放，安静收尾
            Thread.currentThread().interrupt();
            emitter.complete();
        } catch (Exception e) {
            String message = e instanceof BusinessException be
                    ? be.getMessage()
                    : "AI 生成失败：" + rootMessage(e);
            log.warn("AI 流式任务失败：task={}, error={}", dto.getTask(), message);
            try {
                ObjectNode event = objectMapper.createObjectNode();
                event.put("message", message);
                emitter.send(SseEmitter.event().name("error")
                        .data(objectMapper.writeValueAsString(event)));
            } catch (Exception sendEx) {
                log.debug("错误事件发送失败（客户端可能已断开）");
            }
            emitter.completeWithError(e);
        }
    }

    // ---------- 提示词装配 ----------

    /** 任务参数包：各任务独立的系统提示词 / 输出上限 / 温度 */
    private record TaskSpec(String systemPrompt, String userPrompt, int maxTokens, double temperature) {
    }

    private TaskSpec buildSpec(AiChatDTO dto, String text) {
        return switch (dto.getTask()) {
            case "polish" -> new TaskSpec(
                    "你是专业的中文博客编辑。请润色用户给出的文字：保持原意、语气与既有 Markdown 行内标记"
                            + "（如 **加粗**、`代码`、[链接](url)）不变，提升流畅度与表达力；"
                            + "不要扩写、缩写、增删段落，不要输出任何解释或前后缀，只输出润色后的文本。",
                    text, 4096, 0.5);
            case "continue" -> new TaskSpec(
                    "你是博客文章的续写助手。基于用户给出的正文片段自然续写：延续原文语气、主题与 Markdown 格式，"
                            + "直接从断点继续输出内容，不要重复已有文字，不要添加标题、说明或总结性套话。",
                    text, 3072, 0.7);
            case "summary" -> new TaskSpec(
                    "为用户的博客文章撰写中文摘要：100 字以内，概括核心内容与结论，客观陈述；"
                            + "直接输出摘要文本，不要「摘要：」之类的前缀，不要分点。",
                    text, 500, 0.3);
            case "title" -> new TaskSpec(
                    "为用户的博客文章拟 5 个候选标题：每行一个，不带序号、不加书名号、末尾无标点；"
                            + "风格贴合内容（技术类优先准确清晰，也可有适度的吸引力），只输出 5 行标题。",
                    text, 300, 0.8);
            case "tags" -> new TaskSpec(
                    "从用户给出的候选标签列表中，为文章挑选最匹配的 3~5 个标签：每行输出一个，"
                            + "必须逐字来自候选列表，不要输出其他任何内容；若候选都不匹配，只输出：无",
                    "候选标签：\n" + dto.getContext() + "\n\n文章内容：\n" + text, 200, 0.2);
            case "custom" -> new TaskSpec(
                    "你是博客写作助手。严格按用户的指令处理给出的文本，只输出处理结果，不要解释。",
                    "指令：" + dto.getInstruction() + "\n\n文本：\n" + text, 4096, 0.7);
            default -> throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的任务类型");
        };
    }

    // ---------- 基础工具 ----------

    private HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(aiProperties.getConnectTimeoutSeconds()))
                .build();
    }

    private List<String> parseModels(AiProvider provider) {
        try {
            List<String> models = objectMapper.readValue(provider.getModels(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            if (!models.isEmpty()) {
                return models;
            }
        } catch (Exception ignored) {
            // 落入下方统一报错
        }
        throw new BusinessException(ResultCode.NOT_FOUND, "默认供应商未配置模型");
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() > 160 ? compact.substring(0, 160) + "…" : compact;
    }

    private String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }

    @PreDestroy
    void shutdown() {
        streamExecutor.shutdownNow();
    }
}
