# =============================================================================
# 科研项目管理系统 — 后端镜像（多阶段构建）
# 构建：docker build -t rpm-backend ./backend
# 运行：见仓库根 docker-compose.yml / 本地运行指南（生产配置全部经环境变量注入）
# =============================================================================
#syntax=docker/dockerfile:1

# ---- 构建阶段：JDK 21 + Maven 依赖缓存 ----
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build

# 源码整体复制（target/ 等由 .dockerignore 排除）；
# 依赖走 BuildKit 缓存挂载，重复构建不重复下载
COPY . .
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -T 1C package -DskipTests

# ---- 运行阶段：仅 JRE，非 root ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN groupadd -r rpm && useradd -r -g rpm rpm
COPY --from=build --chown=rpm:rpm /build/rpm-boot/target/rpm-boot-*.jar app.jar
USER rpm

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
