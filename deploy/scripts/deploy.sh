#!/usr/bin/env bash
# ============================================================
# 紫云博客 · 服务器端部署脚本（由 GitHub Actions 远程调用）
#
# 用法：BASE64_ENV='<base64 编码的 .env 内容>' bash deploy.sh <GIT_SHA>
#
# 服务器目录约定（/opt/ziyun-blog）：
#   docker-compose.yml    compose 编排（固定位置，每次部署覆盖）
#   .env                  生产密钥（每次部署由 Actions 重新生成）
#   dist/                 当前前端产物（compose 挂载给 nginx）
#   backend/app.jar       当前后端 jar（Dockerfile COPY 打包进镜像）
#   nginx/ mysql/ redis/  各服务配置（随仓库更新）
#   logs/                 后端日志落盘
#   releases/<sha>/       每版产物历史归档（含 deploy/ 配置），回滚依据
#   dist.prev / backend/app.jar.prev   上一次版本备份（失败自动回滚）
#
# 回滚逻辑：新版本健康检查失败 -> 恢复 prev 版本 -> 重新拉起 -> 退出 1
# ============================================================

set -euo pipefail

GIT_SHA="${1:?用法: bash deploy.sh <GIT_SHA>}"
APP_DIR="/opt/ziyun-blog"
RELEASE_DIR="${APP_DIR}/releases/${GIT_SHA}"

if [[ ! -d "${RELEASE_DIR}" ]]; then
    echo "[deploy] 错误: 发布目录不存在 ${RELEASE_DIR}" >&2
    exit 1
fi
if [[ -z "${BASE64_ENV:-}" ]]; then
    echo "[deploy] 错误: 缺少 BASE64_ENV 环境变量" >&2
    exit 1
fi

cd "${APP_DIR}"

# ---------- 1. 写入 .env（base64 编码传输，避免特殊字符/换行问题） ----------
echo "[deploy] 写入 .env"
echo "${BASE64_ENV}" | base64 -d > .env
chmod 600 .env

DOMAIN="$(grep -E '^DOMAIN=' .env | cut -d= -f2-)"
if [[ -z "${DOMAIN}" ]]; then
    echo "[deploy] 错误: .env 缺少 DOMAIN" >&2
    exit 1
fi

# ---------- 2. 同步仓库内的配置与脚本 ----------
echo "[deploy] 同步配置 (${GIT_SHA})"
cp -f  "${RELEASE_DIR}/deploy/docker-compose.yml" "${APP_DIR}/docker-compose.yml"
cp -f  "${RELEASE_DIR}/deploy/backend/Dockerfile"  "${APP_DIR}/backend/Dockerfile"
cp -f  "${RELEASE_DIR}/deploy/nginx/blog.conf"     "${APP_DIR}/nginx/blog.conf"
cp -f  "${RELEASE_DIR}/deploy/mysql/my.cnf"        "${APP_DIR}/mysql/my.cnf"
cp -f  "${RELEASE_DIR}/deploy/mysql/init.sql"      "${APP_DIR}/mysql/init.sql"
cp -f  "${RELEASE_DIR}/deploy/redis/redis.conf"    "${APP_DIR}/redis/redis.conf"

# ---------- 3. 替换 nginx 占位符 __DOMAIN__ -> 真实域名 ----------
sed -i "s/__DOMAIN__/${DOMAIN}/g" "${APP_DIR}/nginx/blog.conf"

# ---------- 4. 备份当前版本（供失败回滚） ----------
if [[ -d "${APP_DIR}/dist" ]]; then
    rm -rf "${APP_DIR}/dist.prev"
    cp -rf "${APP_DIR}/dist" "${APP_DIR}/dist.prev"
fi
if [[ -f "${APP_DIR}/backend/app.jar" ]]; then
    cp -f "${APP_DIR}/backend/app.jar" "${APP_DIR}/backend/app.jar.prev"
fi

# ---------- 5. 应用新版本 ----------
# 注意：dist 必须用 rsync 原地同步（保留目录 inode）。
# 若 rm -rf 重建目录，nginx 容器的 bind mount 会指向已删除的旧 inode，
# 容器内挂载点变空目录 -> 前端 403（Docker 挂载在容器创建时绑定路径）。
mkdir -p "${APP_DIR}/dist"
rsync -a --delete "${RELEASE_DIR}/dist/" "${APP_DIR}/dist/"
cp -f "${RELEASE_DIR}/app.jar" "${APP_DIR}/backend/app.jar"

# ---------- 6. 启动 / 更新容器（后端 jar 重新打进镜像） ----------
echo "[deploy] docker compose up -d --build"
docker compose up -d --build --remove-orphans

# nginx 容器强制重建：Docker bind mount 在容器创建时固定宿主目录 inode，
# 若历史上 dist 曾被 rm -rf 重建过，旧容器挂载点会指向已删除的 inode
# （容器内看到空目录 -> 前端 403）。rsync 保证本次起 inode 稳定，
# 重建保证当前容器重新绑定新 dist 目录。重建仅 1 秒，成本可忽略。
echo "[deploy] docker compose up -d --force-recreate nginx"
docker compose up -d --force-recreate nginx

# ---------- 7. 健康检查（API 链路优先，最多等 180s） ----------
# 说明：只以 /api 链路（nginx 反代 -> backend -> MySQL）为成功标准；
# 前端静态文件 403/404 属于 dist 权限/时序问题，不阻断本次部署（回滚无意义）。
#
# 80/443 双栈均直接提供全量服务（HTTP 为无备案/证书异常时的兜底通道，
# 见 blog.conf），健康检查仍以 443 的完整 HTTPS 链路为准。
# --resolve 强制本机建连（不绕公网），SNI 与证书校验仍按真实域名进行。
echo "[deploy] 健康检查 https://${DOMAIN}/api/v1/categories（最多 180s）"
DEPLOY_OK=0
for i in $(seq 1 36); do
    # -w 打印 HTTP 状态码演进：502=后端未就绪 / 200=链路通 / 403=安全拦截或静态权限
    API_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
        --resolve "${DOMAIN}:443:127.0.0.1" \
        "https://${DOMAIN}/api/v1/categories" 2>/dev/null || echo "000")
    if [[ "${API_CODE}" == "200" ]]; then
        DEPLOY_OK=1
        break
    fi
    echo "[deploy] 等待后端就绪... (HTTP ${API_CODE})"
    sleep 5
done

if [[ "${DEPLOY_OK}" -eq 1 ]]; then
    if ! curl -fsS -o /dev/null --resolve "${DOMAIN}:443:127.0.0.1" "https://${DOMAIN}/"; then
        echo "[deploy] 警告: 前端静态页不可访问（不阻断），请检查 /opt/ziyun-blog/dist 内容与权限"
    fi
fi

if [[ "${DEPLOY_OK}" -ne 1 ]]; then
    echo "[deploy] 健康检查失败，自动回滚到上一版本" >&2
    mkdir -p "${APP_DIR}/dist"
    if [[ -d "${APP_DIR}/dist.prev" ]]; then
        rsync -a --delete "${APP_DIR}/dist.prev/" "${APP_DIR}/dist/"
    fi
    [[ -f "${APP_DIR}/backend/app.jar.prev" ]] && cp -f "${APP_DIR}/backend/app.jar.prev" "${APP_DIR}/backend/app.jar"
    docker compose up -d --build backend nginx
    exit 1
fi

echo "[deploy] 部署成功: ${GIT_SHA}"
