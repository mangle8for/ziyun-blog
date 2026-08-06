package fun.ziyun.blogserver.common;

import lombok.Getter;

/**
 * 统一响应码枚举。
 *
 * <p>设计说明（为什么用枚举而不是魔法数字）：</p>
 * <pre>
 * 原生写法常在 Controller 里直接返回 "200"/"500"，或者各写各的错误消息，
 * 时间一长就会出现：同一个含义有多个 code、同一 code 对应不同消息。
 * 枚举把「代码」与「默认消息」绑定为一对一关系，全局只有一份定义，
 * 新增错误类型 = 新增一个枚举值，扩展时不会破坏既有调用方。
 * </pre>
 */
@Getter
public enum ResultCode {

    /** 成功 */
    SUCCESS(200, "操作成功"),

    /** 客户端请求参数错误（字段缺失/格式不对/越界） */
    BAD_REQUEST(400, "请求参数错误"),

    /** 未登录或登录态失效（token 缺失/过期/被登出） */
    UNAUTHORIZED(401, "未登录或登录已过期"),

    /** 已登录但权限不足（如普通用户访问管理接口） */
    FORBIDDEN(403, "没有操作权限"),

    /** 资源不存在（如文章 ID 非法） */
    NOT_FOUND(404, "资源不存在"),

    /** 请求冲突（如重复提交、状态不允许变更） */
    CONFLICT(409, "请求冲突"),

    /** 服务器内部错误（兜底，一般由未知异常触发） */
    SERVER_ERROR(500, "服务器内部错误"),

    /**
     * 服务不可用（P2 起：Redis 宕机时受保护接口返回 503，
     * 明确告知客户端是基础设施故障而非业务报错）。
     */
    SERVICE_UNAVAILABLE(503, "服务暂不可用，请稍后重试");

    /** 与 HTTP 状态码对齐的数值，便于前端 axios 拦截器直接判断 */
    private final int code;

    /** 默认错误消息，业务层可覆盖为更具体的描述 */
    private final String msg;

    ResultCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
