package fun.ziyun.blogserver.exception;

import fun.ziyun.blogserver.common.Result;
import fun.ziyun.blogserver.common.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import java.util.stream.Collectors;

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
        // 注意用 e.getMessage() 而非枚举默认消息：BusinessException 支持
        // 携带自定义消息（如"文章不存在，id=xxx"），此处必须保留，
        // 否则自定义消息被丢弃，前端只能看到笼统的"资源不存在"。
        return Result.fail(e.getResultCode(), e.getMessage());
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
     * 方法参数/路径参数校验异常：@PathVariable/@RequestParam 上的
     * 校验注解（如 @Min(1)）失败时抛此异常。
     *
     * <p>设计说明（两套参数校验触发点）：</p>
     * <pre>
     * 1. @RequestBody DTO + @Valid  -> MethodArgumentNotValidException
     *    （字段在 JSON body 里，异常里挂 BindingResult）；
     * 2. 路径/查询参数 + 类级 @Validated -> ConstraintViolationException
     *    （参数是方法签名的一部分，异常里挂 Violation 集合）。
     * 两套异常类型不同，必须分别处理，否则第二种会漏到 500 兜底 ——
     * 这也是本次踩到的真实坑（id=0 校验失败返回了 500）。
     * </pre>
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        return Result.fail(ResultCode.BAD_REQUEST, msg);
    }

    /**
     * 请求体解析失败：JSON 语法错误 / 类型不匹配 / body 缺失。
     *
     * <p>设计说明：Jackson 解析失败抛 HttpMessageNotReadableException，
     * 这是「客户端提交了无法解析的数据」—— 客户端问题应回 400。
     * 不处理会漏进兜底 500，语义错误且可能把解析细节（含字段名）
     * 原样泄漏到响应里。</p>
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.fail(ResultCode.BAD_REQUEST, "请求体格式错误");
    }

    /**
     * 类型不匹配：路径/查询参数无法转换为目标类型（如 id 传了非数字）。
     * 客户端提交了错误类型的数据 -> 400，与 ConstraintViolationException 相邻，
     * 但异常类型不同（Spring 转换层抛 MethodArgumentTypeMismatchException）。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.fail(ResultCode.BAD_REQUEST, "参数类型错误: " + e.getName());
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
