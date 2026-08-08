-- ============================================================
-- 紫云博客 · 修复初始化示例数据的中文乱码
--
-- 背景：首次部署时 MySQL 官方镜像用 mysql CLI（latin1 会话）执行
-- init.sql，中文被「双重编码」入库（如「前端」变成 Ã¥‰«¯ 的字节）。
-- 应用以 utf8mb4 读取时显示乱码；而后台新增的数据（应用层 utf8mb4
-- 写入）正常。
--
-- 修复原理：utf8mb4 字节先 CAST latin1 还原原始字节，再转回 utf8mb4。
-- 安全机制：WHERE 仅命中包含 latin-1 supplement 字符（[À-ÿ]，即
-- 双重编码特征）的行——正常中文（CJK 区）与纯 ASCII 行绝不命中，
-- 不会误伤你后台新增/编辑过的数据。
--
-- 执行方式（服务器上）：
--   docker exec -i ziyun-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" \
--     --default-character-set=utf8mb4 < fix_charset.sql
-- ============================================================

USE ziyun_blog;

-- ---------- 用户昵称 ----------
UPDATE `user` SET
    nickname = CONVERT(CAST(CONVERT(nickname USING latin1) AS BINARY) USING utf8mb4)
WHERE nickname REGEXP '[À-ÿ]';

-- ---------- 分类 ----------
UPDATE `category` SET
    name        = CONVERT(CAST(CONVERT(name USING latin1) AS BINARY) USING utf8mb4),
    description = CONVERT(CAST(CONVERT(description USING latin1) AS BINARY) USING utf8mb4)
WHERE name REGEXP '[À-ÿ]' OR description REGEXP '[À-ÿ]';

-- ---------- 标签 ----------
UPDATE `tag` SET
    name = CONVERT(CAST(CONVERT(name USING latin1) AS BINARY) USING utf8mb4)
WHERE name REGEXP '[À-ÿ]';

-- ---------- 文章 ----------
UPDATE `article` SET
    title   = CONVERT(CAST(CONVERT(title USING latin1) AS BINARY) USING utf8mb4),
    summary = CONVERT(CAST(CONVERT(summary USING latin1) AS BINARY) USING utf8mb4),
    content = CONVERT(CAST(CONVERT(content USING latin1) AS BINARY) USING utf8mb4)
WHERE title REGEXP '[À-ÿ]' OR summary REGEXP '[À-ÿ]' OR content REGEXP '[À-ÿ]';

-- ---------- 残留检查（应全部为 0，非 0 请把输出发我） ----------
SELECT 'user.nickname'      AS tbl, COUNT(*) AS remaining FROM `user`     WHERE nickname    REGEXP '[À-ÿ]'
UNION ALL
SELECT 'category.name'      AS tbl, COUNT(*) AS remaining FROM `category` WHERE name        REGEXP '[À-ÿ]'
UNION ALL
SELECT 'category.desc'      AS tbl, COUNT(*) AS remaining FROM `category` WHERE description REGEXP '[À-ÿ]'
UNION ALL
SELECT 'tag.name'           AS tbl, COUNT(*) AS remaining FROM `tag`      WHERE name        REGEXP '[À-ÿ]'
UNION ALL
SELECT 'article.title'      AS tbl, COUNT(*) AS remaining FROM `article`  WHERE title       REGEXP '[À-ÿ]'
UNION ALL
SELECT 'article.summary'    AS tbl, COUNT(*) AS remaining FROM `article`  WHERE summary     REGEXP '[À-ÿ]'
UNION ALL
SELECT 'article.content'    AS tbl, COUNT(*) AS remaining FROM `article`  WHERE content     REGEXP '[À-ÿ]';

-- ---------- 修复后抽查（应为正常中文） ----------
SELECT id, name FROM `category`;
SELECT id, title, summary FROM `article`;
