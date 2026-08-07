package fun.ziyun.blogserver.exception;

import fun.ziyun.blogserver.common.Result;
import fun.ziyun.blogserver.common.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器。
 *
 * <p>设计说明（为什么用 @RestControllerAdvice）：</p>
 * <pre>
 * 原生写法：每个 Controller 都用 try/catch 包业务代码，再各自拼错误 Result，
 * 10 个接口就要写 10 遍重复代码，且极易漏 catch 导致前端收到默认 500 页面。
 * @RestControllerAdvice 是 Spring MVC 的「全局 AOP 通知」，拦截所有 Controller
 * 抛出的异常，按 @ExceptionHandler 声明的异常类型路由到对应处理方法。
 * 这样业务代码无需任何 try/catch，出错时异常自动冒泡到这里统一收口：
 *   - 已知的业务异常  -> 转成对应的 Result（如 NOT_FOUND）
 *   - 参数校验失败     -> 取第一条校验错误消息返回 400
 *   - 未知异常         -> 记日志 + 返回 500，避免堆栈泄漏给前端
 * </pre>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：按枚举映射 HTTP 状态码与业务码。
     * 401/403 等特殊码在 P2 接入 Security 后由 Security 的入口点处理，
     * 这里处理的是 Controller 层业务规则抛出的异常。
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        // 4xx 归为客户端问题，不需要记 error 级日志（避免日志噪音）
        if (e.getResultCode().getCode() >= 500) {
            log.error("业务异常", e);
        }
        return Result.fail(e.getResultCode());
    }

    /**
     * 参数校验异常：@Valid 校验 DTO 失败时由框架抛出。
     *
     * <p>设计说明（校验消息的取法）：MethodArgumentNotValidException 里
     * 挂着所有未通过校验的 FieldError 列表，这里只取第一条 —— 前端逐条
     * 提示反而体验差，一次报一个最明确的错误即可。</p>
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError == null
                ? ResultCode.BAD_REQUEST.getMsg()
                : fieldError.getField() + " " + fieldError.getDefaultMessage();
        return Result.fail(ResultCode.BAD_REQUEST, msg);
    }

    /**
     * 静态资源/路径未映射异常：Spring 6.1+ 对访问不存在的 URL 抛此异常。
     *
     * <p>设计说明（兜底 handler 的一个经典坑）：</p>
     * <pre>
     * 没写本方法前，该异常会被下方兜底 handler 接住 -> 返回 500。
     * 但"路径不存在"是客户端问题（404），不是服务器故障（500），
     * 两者语义完全不同：监控告警按 5xx 计数，404 刷屏会误触发告警。
     * 单独接管并映射为 404，同时保持响应 JSON 风格一致。
     * </pre>
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResourceFound(NoResourceFoundException e) {
        return Result.fail(ResultCode.NOT_FOUND);
    }

    /**
     * 兜底：任何未识别的异常。必须记完整堆栈（排查问题全靠它），
     * 但响应只给用户通用提示，不暴露内部细节（防信息泄漏）。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail(ResultCode.SERVER_ERROR);
    }
}
