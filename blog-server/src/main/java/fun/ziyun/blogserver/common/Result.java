package fun.ziyun.blogserver.common;

import lombok.Data;

/**
 * 统一 API 响应包装。
 *
 * <p>设计说明：</p>
 * <pre>
 * 不包装的写法：Controller 直接返回业务对象/void，前端要靠 HTTP 状态码 +
 * 各种非标准响应体猜测结果，错误信息更是五花八门。
 * 包装成固定结构 {code, msg, data} 后：
 *   - 前端拦截器统一解包，业务代码只关心 data；
 *   - 业务失败（如"文章不存在"）也是 HTTP 200 + 非 200 的 code 返回，
 *     而非抛 HTTP 错误 —— 这样前端能拿到结构化的错误消息统一弹提示；
 *   - 真正的 HTTP 错误（401/403/500）保留给系统级异常。
 * </pre>
 *
 * @param <T> data 字段的业务数据类型
 */
@Data
public class Result<T> {

    /** 业务码，见 {@link ResultCode} */
    private Integer code;

    /** 提示信息 */
    private String msg;

    /** 业务数据，成功时为业务对象，失败时为 null */
    private T data;

    /** 成功（无数据） */
    public static <T> Result<T> ok() {
        return build(ResultCode.SUCCESS, null);
    }

    /** 成功（带数据） */
    public static <T> Result<T> ok(T data) {
        return build(ResultCode.SUCCESS, data);
    }

    /** 失败（使用枚举自带的默认消息） */
    public static <T> Result<T> fail(ResultCode resultCode) {
        return build(resultCode, null);
    }

    /** 失败（业务层自定义更具体的消息） */
    public static <T> Result<T> fail(ResultCode resultCode, String msg) {
        Result<T> result = build(resultCode, null);
        result.setMsg(msg);
        return result;
    }

    /** 构造器集中在此，避免各 Controller 各写一套包装逻辑 */
    private static <T> Result<T> build(ResultCode resultCode, T data) {
        Result<T> result = new Result<>();
        result.setCode(resultCode.getCode());
        result.setMsg(resultCode.getMsg());
        result.setData(data);
        return result;
    }
}
