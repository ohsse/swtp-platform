#!/bin/bash
# Beats 인덱스 보존 정책(ILM) — kafka/create-topics.sh와 같은 자리의 초기화 스크립트다.
#
# 왜 필요한가: Beats가 기동 시 자동으로 만드는 기본 ILM 정책에는 **delete 단계가 없다.**
# rollover만 하고 지우지 않으므로 인덱스가 무한히 자란다. 정수장 온프레미스 단일 서버에서는
# 이것이 결국 디스크를 채우고, 디스크가 차면 Elasticsearch가 읽기 전용으로 잠기면서
# 수집이 멈춘다 — 관측 스택이 스스로 관측을 못 하게 되는 상태다.
#
# Beats는 정책이 이미 있으면 덮어쓰지 않는다("lifecycle policy exists already").
# 그래서 이 스크립트가 Beats보다 먼저 돌아야 한다 — compose의 depends_on이 순서를 보장한다.
set -euo pipefail

ES="${SWTP_ELASTIC_HOSTS:-http://elasticsearch:9200}"
RETENTION_DAYS="${SWTP_LOG_RETENTION_DAYS:-30}"
# 정수장 서버 디스크 규모에 맞춘 값이다. Beats 기본값 50gb는 단일 노드에서 너무 크다 —
# 롤오버 전에 디스크가 먼저 찬다.
MAX_SHARD_SIZE="${SWTP_LOG_MAX_SHARD_SIZE:-5gb}"

apply_policy() {
  local name="$1"
  echo "ILM 정책 적용: ${name} (보존 ${RETENTION_DAYS}일, 샤드 ${MAX_SHARD_SIZE})"
  curl -sS -f -X PUT "${ES}/_ilm/policy/${name}"     -H 'Content-Type: application/json'     -d "{
      \"policy\": {
        \"phases\": {
          \"hot\": {
            \"min_age\": \"0ms\",
            \"actions\": {
              \"rollover\": {
                \"max_age\": \"1d\",
                \"max_primary_shard_size\": \"${MAX_SHARD_SIZE}\"
              }
            }
          },
          \"delete\": {
            \"min_age\": \"${RETENTION_DAYS}d\",
            \"actions\": { \"delete\": {} }
          }
        }
      }
    }" > /dev/null
  echo "  → ${name} 적용 완료"
}

apply_policy filebeat
apply_policy metricbeat
echo "ILM 초기화 완료"
