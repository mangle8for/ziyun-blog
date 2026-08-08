-- ============================================================
-- 紫云博客 · 数据库初始化脚本
-- 目标版本：MySQL 8.0+
-- 字符集：utf8mb4（支持 emoji 等四字节字符，须配合 utf8mb4_unicode_ci 排序规则）
--
-- 执行方式（二选一）：
--   1. 命令行：mysql -uroot -p < init.sql
--   2. Navicat / Workbench：直接打开本文件运行
--
-- 设计说明（整体约定）：
--   1. 主键统一 BIGINT 且【非自增】：ID 由 MyBatis-Plus 的 assign_id 策略
--      在应用层用雪花算法生成。相比数据库自增：
--        a. 自增 ID 可被遍历抓取（文章 id=1,2,3... 一路爬），雪花 ID 无规律；
--        b. 自增依赖数据库单点计数器，分布式/分表场景是瓶颈；
--        c. 应用层生成 ID 后即可先组装关联数据（如文章与标签的关联表）
--           再一次性入库，无需插入后回查 ID。
--   2. 所有表统一带 deleted 逻辑删除字段（0 未删 / 1 已删）：
--      配合 MyBatis-Plus 全局配置，业务查询自动追加 AND deleted=0，
--      误删可恢复，留审计痕迹。代价是每个查询多一个条件（可接受）。
--   3. 不建物理外键（FOREIGN KEY）：文章与分类/标签的关联是「逻辑外键」，
--      靠业务代码保证一致性（删除前先查引用）。理由：
--        a. 物理外键强制写操作顺序、加锁开销大，高并发下影响性能；
--        b. 分库分表后外键直接失效；主流互联网项目几乎不用物理外键。
--   4. DROP TABLE IF EXISTS 保证脚本可重复执行（幂等）。
--      ⚠️ 生产环境严禁执行 DROP，请用增量迁移脚本（如 Flyway/Liquibase）。
-- ============================================================

-- ⚠️ 首行 SET NAMES 必须保留：官方 MySQL 镜像用 mysql CLI（默认 latin1 会话）
--    执行本脚本，不强制 utf8mb4 会导致中文「双重编码」入库（应用读取乱码）
SET NAMES utf8mb4;

-- ---------- 1. 建库 ----------
CREATE DATABASE IF NOT EXISTS `ziyun_blog`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE `ziyun_blog`;

-- ============================================================
-- 用户表
-- ============================================================
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    `id`          BIGINT       NOT NULL COMMENT '主键ID（雪花算法，应用层生成）',
    `username`    VARCHAR(50)  NOT NULL COMMENT '用户名（登录用）',
    `password`    VARCHAR(100) NOT NULL COMMENT '密码（BCrypt 加密后的 60 位哈希，绝不存明文）',
    `nickname`    VARCHAR(50)  DEFAULT NULL COMMENT '昵称（显示用，为空则回退用户名）',
    `avatar`      VARCHAR(255) DEFAULT NULL COMMENT '头像URL（存 MinIO 地址）',
    `email`       VARCHAR(100) DEFAULT NULL COMMENT '邮箱（预留，未来评论通知用）',
    `role`        TINYINT      NOT NULL DEFAULT 0 COMMENT '角色：0-普通用户 1-管理员（控制管理端接口权限）',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-正常',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删 1-已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB COMMENT = '用户表';
-- 设计说明：
--   - username 唯一索引：登录查询按 username 走索引，杜绝重复用户名。
--     注意：逻辑删除与唯一索引存在经典矛盾 —— 删除用户后其 username 仍被
--     唯一索引占用，无法再次注册。个人博客用户量小可忽略，复杂方案
--     是加 username_deleted 冗余字段进唯一索引（此处不展开）。
--   - user 加反引号：user 是 MySQL 函数名（USER()），虽然非保留字，
--     但建表时统一反引号避免歧义（其他表无歧义字段则省略）。

-- ============================================================
-- 分类表
-- ============================================================
DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
    `id`          BIGINT       NOT NULL COMMENT '主键ID（雪花）',
    `name`        VARCHAR(50)  NOT NULL COMMENT '分类名',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '分类简介',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删 1-已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE = InnoDB COMMENT = '文章分类表';

-- ============================================================
-- 标签表
-- ============================================================
DROP TABLE IF EXISTS `tag`;
CREATE TABLE `tag` (
    `id`          BIGINT       NOT NULL COMMENT '主键ID（雪花）',
    `name`        VARCHAR(50)  NOT NULL COMMENT '标签名',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删 1-已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE = InnoDB COMMENT = '文章标签表';

-- ============================================================
-- 文章表
-- ============================================================
DROP TABLE IF EXISTS `article`;
CREATE TABLE `article` (
    `id`          BIGINT       NOT NULL COMMENT '主键ID（雪花）',
    `title`       VARCHAR(200) NOT NULL COMMENT '标题',
    `summary`     VARCHAR(500) DEFAULT NULL COMMENT '摘要（列表页展示，空则截取正文前 100 字）',
    `content`     LONGTEXT     COMMENT '正文（Markdown 原文，LONGTEXT 上限 4GB 足够）',
    `cover`       VARCHAR(255) DEFAULT NULL COMMENT '封面图URL（MinIO）',
    `category_id` BIGINT       DEFAULT NULL COMMENT '分类ID（逻辑外键 -> category.id，可为空表示未分类）',
    `author_id`   BIGINT       NOT NULL COMMENT '作者ID（逻辑外键 -> user.id）',
    `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0-草稿 1-发布（发布后才对外可见）',
    `view_count`  INT          NOT NULL DEFAULT 0 COMMENT '浏览量（P2 起异步累加）',
    `like_count`  INT          NOT NULL DEFAULT 0 COMMENT '点赞数（预留字段，v1 未实现点赞功能）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删 1-已删',
    PRIMARY KEY (`id`),
    KEY `idx_category` (`category_id`),
    KEY `idx_author` (`author_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB COMMENT = '文章表';
-- 设计说明（索引策略）：
--   - 列表页高频查询是「按状态 + 按分类/作者过滤 + 按时间倒序」，
--     单列索引 idx_status / idx_category / idx_author 可分别命中；
--     若未来列表数据量大，可再建联合索引 (status, create_time)。
--   - 不建 (status, category_id) 联合索引：当前数据量（个人博客千篇级）
--     单列索引已够用，避免过度索引拖慢写入。

-- ============================================================
-- 文章-标签关联表
-- ============================================================
DROP TABLE IF EXISTS `article_tag`;
CREATE TABLE `article_tag` (
    `article_id` BIGINT NOT NULL COMMENT '文章ID（逻辑外键 -> article.id）',
    `tag_id`     BIGINT NOT NULL COMMENT '标签ID（逻辑外键 -> tag.id）',
    PRIMARY KEY (`article_id`, `tag_id`)
) ENGINE = InnoDB COMMENT = '文章-标签多对多关联表';
-- 设计说明：
--   - 复合主键天然防重复：同一篇文章重复打同一标签会被主键拒绝。
--   - 文章与标签是多对多关系，必须引入中间表；
--     若只有一对多（一篇文章一个分类），加外键列即可，无需中间表。
--   - 无 id 主键、无时间字段：本表只承载关联，不参与业务展示。

-- ============================================================
-- 预置数据（开发/联调用）
-- ============================================================
-- ⚠️ 以下均为开发环境初始数据，密码为 admin123 的 BCrypt 哈希。
--    登录后请立即修改密码；生产环境由运维手工初始化。

-- 管理员账号（id 固定为 1，雪花 ID 与之共存无冲突）
INSERT INTO `user` (`id`, `username`, `password`, `nickname`, `role`)
VALUES (1, 'admin', '$2b$10$yq3y6umtjNkUXU9yhyzO4OFoitSomEHUDO8DMzxhVwfSn9yxqBVbS', '紫云管理员', 1);

-- 示例分类
INSERT INTO `category` (`id`, `name`, `description`) VALUES
    (1, 'Java', 'Java 后端技术分享'),
    (2, '前端', 'Vue / 前端工程化'),
    (3, '随笔', '生活与思考');

-- 示例标签
INSERT INTO `tag` (`id`, `name`) VALUES
    (1, 'Spring Boot'),
    (2, 'MyBatis-Plus'),
    (3, 'Redis'),
    (4, 'Vue3');

-- 示例文章：1 篇已发布（前台可见）+ 1 篇草稿（管理端可见）
INSERT INTO `article` (`id`, `title`, `summary`, `content`, `category_id`, `author_id`, `status`) VALUES
    (1, '欢迎来到紫云博客', '本站基于 Spring Boot 3 + Vue 3 从零构建，本文介绍项目背景与技术选型。', '# 欢迎来到紫云博客

本项目是一个前后端分离的个人博客系统，用于学习主流 Java 与前端技术栈。

## 技术栈

- 后端：Spring Boot 3.5 + MyBatis-Plus + MySQL + Redis + MinIO
- 前端：Vue 3 + Vite + TypeScript + Element Plus
- 部署：Nginx 托管静态资源并反向代理 /api

## 内容规划

后续将陆续更新 Spring Security、JWT 认证、异步编程等系列文章。', 1, 1, 1),
    (2, '未发布的草稿示例', '这是一篇草稿，发布前前台不可见。', '# 草稿

只有登录管理端才能看到我。', 2, 1, 0);

-- 示例文章-标签关联
INSERT INTO `article_tag` (`article_id`, `tag_id`) VALUES
    (1, 1),
    (1, 2),
    (1, 3),
    (2, 4);
