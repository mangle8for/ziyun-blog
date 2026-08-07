package fun.ziyun.blogserver.entity;

import lombok.Getter;

/**
 * 文章状态枚举。
 *
 * <p>设计说明（为什么用枚举而不是散落的 0/1 魔法数字）：</p>
 * <pre>
 * 若代码里直接写 status == 1 / status == 0，两个问题：
 *   1. 阅读者看到 1 不知道是发布还是禁用，上下文全靠猜；
 *   2. 数据库新增状态（如 2=私密）时，所有比较处都要人工排查。
 * 枚举把「数值」与「语义」绑定，switch/compare 处写枚举名，
 * 编译器还能在漏掉分支时报错提醒。
 * </pre>
 */
@Getter
public enum ArticleStatus {

    /** 草稿：仅管理端可见 */
    DRAFT(0),

    /** 已发布：前台公开可见 */
    PUBLISHED(1);

    private final int code;

    ArticleStatus(int code) {
        this.code = code;
    }

    /**
     * 按数值反查枚举，非法值返回 null（由调用方决定如何兜底）。
     * 相比直接比较，统一入口避免「=0 还是 ==0」的低级错误。
     */
    public static ArticleStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ArticleStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
