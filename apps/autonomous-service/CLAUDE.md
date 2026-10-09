# autonomous-service — 자율운영

운전모드 관리, AI 추천 결과 기반 판단, 최적제어 로직, **제어 명령 생성**, 운전 결과 관리를 맡는다(아키텍처 4.2).

## Optional 배포

compose profile `autonomous`(또는 `full`)로만 뜬다. 이 서비스가 없는 정수장이 정상 구성이다 — 자율운영을 쓰지 않고 수동 운전만 하는 정수장이 대상이므로, **다른 서비스가 이 서비스의 존재를 전제하면 안 된다.**

## 쓰기는 하되 소유하지 않는 스키마가 있다

제어 이력은 `operation` 스키마에 남지만 **그 스키마의 DDL 소유는 master-service**다(아키텍처 11.3의 명시적 예외, `master-service`의 `spring.flyway.schemas: master,operation`). 즉 **테이블을 여기서 만들거나 바꾸지 않는다** — 컬럼이 필요하면 master-service의 Flyway 마이그레이션으로 요청한다. 여기에 `operation` 스키마용 마이그레이션이 생기면 두 서비스가 같은 스키마에 DDL을 걸어 Flyway history가 갈린다.

## 발행할 토픽이 이미 준비돼 있다

`infrastructure/kafka/create-topics.sh`가 **`control.event`(자율운영 제어 이벤트)** 를 생성해 둔다. 입력 쪽으로는 `prediction.generated`(AI 예측 생성)가 있다 — AI 서비스(`ai/ai-service`)를 HTTP로 직접 호출하는 대신 이 토픽을 소비하는 것이 설계 방향이다.

## 현재 상태 — 스캐폴드

`AutonomousServiceApplication` 한 개뿐이고 웹 스택·config·eureka만 배선돼 있다. `apps/CLAUDE.md` 포트 표에서 소유 스키마가 `—`인 것은 위의 이유다 — 자기 소유 스키마가 없고 `operation`에 쓰기만 한다.
