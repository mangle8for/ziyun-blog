# ============================================================
# 紫云博客 · 后端镜像（构建产物为可执行 fat jar）
#
# 基础镜像 eclipse-temurin:17-jre-jammy：
#   - JRE 足够（编译在 GitHub Actions 上完成），镜像比 JDK 版小约 200MB
#   - jammy 对应 Ubuntu 22.04，与服务器发行版一致
#
# 构建方式：deploy.sh 先把 jar 复制为 backend/app.jar，
# 再 docker compose up -d --build（Dockerfile 位于 backend/ 目录）。
# ============================================================
FROM eclipse-temurin:17-jre-jammy

LABEL maintainer="ziyun-blog"

ENV TZ=Asia/Shanghai \
    # 2G 服务器内存专项调优：
    #   -Xms256m -Xmx512m：显式定死堆大小，防止 JVM 按物理内存 1/4 自动伸缩
    #   -XX:MaxMetaspaceSize=192m：限制类元数据，防泄漏时失控
    #   -XX:+ExitOnOutOfMemoryError：堆耗尽直接退出，交给 compose restart 拉起
    #   （JAVA_OPTS 可在 docker-compose.yml 中覆盖）
    JAVA_OPTS="-Xms256m -Xmx512m -XX:MaxMetaspaceSize=192m -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8"

WORKDIR /app

# jar 由部署脚本以固定文件名 app.jar 拷贝进来
COPY app.jar /app/app.jar

EXPOSE 8080

# SPRING_PROFILES_ACTIVE 由 compose 环境变量注入（prod）
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
