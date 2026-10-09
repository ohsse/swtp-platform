# pms-service — 설비 관리

설비 상태 관리·이상 감지·예지보전·센서 데이터 분석과 그 Dashboard API를 맡는다(아키텍처 4.4).

## Optional 배포

compose profile `pms`(또는 묶음 `full`)로만 뜬다. 이 서비스가 없는 정수장이 정상 구성이므로, **다른 서비스가 pms-service를 동기 호출하도록 만들면 그 정수장에서 기능이 깨진다.** 설비 이상 정보를 다른 서비스에 알려야 한다면 Kafka로 낸다.

## 발행할 토픽이 이미 준비돼 있다

`infrastructure/kafka/create-topics.sh`가 **`equipment.event`(PMS 설비 이벤트)** 를 이미 생성한다. 소비자가 먼저 붙어 있을 수 있으므로 발행을 시작할 때 `EventEnvelope` 규약(`starters/CLAUDE.md`)을 그대로 따른다 — 페이로드를 날것으로 보내지 않는다.

## 현재 상태 — 스캐폴드

`PmsServiceApplication` 한 개뿐이고 웹 스택·config·eureka만 배선돼 있다. **`pms` 스키마는 `01-schemas.sql`이 이미 만들어 두었다** — 모듈에 `swtp-persistence-starter`가 없어 아직 쓰지 않을 뿐이다.

이상 감지의 입력인 센서 시계열은 telemetry-service가, 설비·태그 기준정보는 master-service가 소유한다. 두 스키마에 직접 붙지 않는다(아키텍처 11.3).
