# 전 Java 앱 공통 이미지 템플릿 — 빌드 컨텍스트는 리포지토리 루트, APP 인자로 모듈을 지정한다.
# 사전 조건: 호스트에서 `gradlew bootJar` 실행 완료 (이미지 안에서는 Gradle을 돌리지 않는다)
#   docker build -f infrastructure/docker/app.Dockerfile --build-arg APP=master-service .

# ── 1단계: 실행 가능 jar를 레이어 단위로 추출 (의존성 레이어 캐시 재사용) ──
FROM eclipse-temurin:21-jre AS builder
WORKDIR /builder
ARG APP
COPY apps/${APP}/build/libs/*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ── 2단계: 런타임 이미지 — 변경 빈도 낮은 레이어부터 COPY ──
FROM eclipse-temurin:21-jre
# actuator healthcheck용 curl
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /application
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./
ENTRYPOINT ["java", "-jar", "application.jar"]
