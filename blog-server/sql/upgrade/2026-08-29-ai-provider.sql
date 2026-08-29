-- ============================================================
-- 增量迁移：AI 模型供应商设置表（2026-08-29）
-- 用途：AI 写作助手（润色/续写/摘要等）的模型供应商配置。
-- 手工执行：mysql -u<user> -p ziyun_blog < 2026-08-29-ai-provider.sql
-- ============================================================
USE ziyun_blog;

CREATE TABLE IF NOT EXISTS `ai_provider` (
    `id`          BIGINT       NOT NULL COMMENT '主键ID（雪花）',
    `name`        VARCHAR(50)  NOT NULL COMMENT '供应商名称（如 智谱GLM）',
    `base_url`    VARCHAR(255) NOT NULL COMMENT 'OpenAI 兼容 Base URL（如 https://open.bigmodel.cn/api/paas/v4）',
    `api_key`     VARCHAR(512) NOT NULL COMMENT 'API Key（AES/GCM 加密，Base64[iv+密文]，库中永不存明文）',
    `models`      VARCHAR(1024) NOT NULL DEFAULT '[]' COMMENT '可选模型 ID 的 JSON 数组（如 ["glm-4.6"]）',
    `enabled`     TINYINT      NOT NULL DEFAULT 1 COMMENT '启用：0-停用 1-启用（停用后不可被选为写作模型）',
    `is_default`  TINYINT      NOT NULL DEFAULT 0 COMMENT '写作默认：0-普通 1-默认（全局仅一个）',
    `remark`      VARCHAR(255) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删 1-已删',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB COMMENT = 'AI 模型供应商设置';
