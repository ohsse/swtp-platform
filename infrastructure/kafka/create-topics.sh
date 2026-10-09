#!/bin/bash
# Kafka 토픽 생성 init 스크립트 — auto-create off 정책에 따라 명시 생성한다.
# 도메인 토픽 8종 + 설정 전파 토픽 1종.
# 도메인 토픽 목록의 근거: docs/architecture/스마트정수장_리빌드_아키텍처.md 9.3 초기 Topic 구성안
set -euo pipefail

BOOTSTRAP=kafka:9092

TOPICS=(
  telemetry.raw        # 원천 수집 데이터
  telemetry.minute     # 1분 시계열 데이터
  telemetry.aggregate  # 집계 데이터
  master.changed       # Master 변경 이벤트
  prediction.generated # AI 예측 생성 이벤트
  equipment.event      # PMS 설비 이벤트
  control.event        # 자율운영 제어 이벤트
  job.event            # Job 실행 이벤트
)

for topic in "${TOPICS[@]}"; do
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP" --create --if-not-exists \
    --topic "$topic" --partitions 3 --replication-factor 1
done

# 설정 전파(Spring Cloud Bus) 기본 토픽 — /actuator/busrefresh 이벤트가 흐른다.
# 도메인 토픽과 달리 파티션 1개다: 브로드캐스트라 처리량이 아니라 순서가 중요하고,
# 파티션을 나누면 리프레시 이벤트 순서가 인스턴스마다 뒤집힐 수 있다.
# 이름은 Spring 기본값이다 — spring.cloud.bus.destination을 어디에도 지정하지 않는다.
/opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP" --create --if-not-exists \
  --topic springCloudBus --partitions 1 --replication-factor 1

echo "=== 생성된 토픽 목록 ==="
/opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP" --list
