package fun.ziyun.blogserver.exception;

import fun.ziyun.blogserver.common.ResultCode;
import lombok.Getter;

/**
 * 业务异常：业务规则不满足时主动抛出。
 *
 * <p>设计说明（为什么要有业务异常）：</p>
 * <pre>
 * 原生写法：在 Service/Controller 里 if (!条件) { return Result.fail(...); }，
 * 每处都要手写返回语句，代码里到处都是错误处理分支，业务逻辑被穿插得支离破碎。
 * 异常式写法：不满足条件时直接 throw new BusinessException(ResultCode.NOT_FOUND)，
 * 调用链一路上抛，最终由 GlobalExceptionHandler 统一捕获并转成 Result。
 * 好处：
 *   1. 业务方法不必声明返回值里的"错误语义"，专注正常流程；
 *   2. 错误响应格式全局唯一，不会出现某个接口忘包装的漏网之鱼；
 *   3. 代码可读性接近"先说正常流程，异常情况交给兜底"。
 * </pre>
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 对应的业务码，GlobalExceptionHandler 据此决定响应 code 与 HTTP 状态 */
    private final ResultCode resultCode;

    /**
     * @param resultCode 错误类型（自带默认消息）
     */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.resultCode = resultCode;
    }

    /**
     * @param resultCode 错误类型
     * @param msg        覆盖默认消息的具体描述（如"文章 id=999 不存在"）
     */
    public BusinessException(ResultCode resultCode, String msg) {
        super(msg);
        this.resultCode = resultCode;
    }
}
