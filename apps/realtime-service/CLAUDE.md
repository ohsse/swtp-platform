# realtime-service — 실시간 브로드캐스트

Kafka 이벤트를 SSE/WebSocket으로 Frontend에 전달한다(아키텍처 7장). **Frontend가 Kafka에 직접 붙지 않게 하는 것이 이 서비스의 존재 이유다** — 브로커를 외부에 노출하지 않고, 인증도 여기(정확히는 게이트웨이)에서 한 번에 건다.

## 소유하지 않는 것

**DB를 쓰지 않는다.** `swtp-persistence-starter`가 없고 소유 스키마도 없다. 구독자 목록은 인메모리(`CopyOnWriteArrayList<SseEmitter>`)다.

**`swtp-security-starter`를 쓰지 않는다.** 다른 서비스는 게이트웨이를 우회한 직접 호출을 막으려고 토큰을 한 번 더 검증하지만, 여기는 브라우저 `EventSource`/`WebSocket`이 **핸드셰이크에 헤더를 붙일 수 없어서**(아키텍처 7.3) 그 방식이 성립하지 않는다. 대신 게이트웨이가 쿼리 파라미터의 단기 ws-ticket(30초, `typ=ws-ticket`)을 검증한다.

그래서 **게이트웨이의 `swtp.gateway.security.ticket-auth-paths`가 이 서비스의 인증 경계**다. 그 값이 낡으면 여기만 401이 되고 다른 서비스는 멀쩡하다 — 실제로 그렇게 된 적이 있다(`apps/gateway/docs/02`). 게이트웨이 경유 주소는 경로 규약(`/{서비스명}/**`)에 따라 `/realtime-service/api/realtime/...`이고, `ticket-auth-paths`도 그 형태여야 한다.

## 소비 설정

`@KafkaListener(topics = "telemetry.raw")`, `auto-offset-reset: latest`.

**스타터 기본값(`earliest`)을 일부러 재정의했다.** 브로드캐스트는 과거 재생이 무의미하고, `earliest`로 두면 재기동할 때마다 보존 기간만큼의 과거 데이터가 화면에 쏟아진다. telemetry-service는 반대로 `earliest`가 맞다 — 같은 토픽을 소비하지만 목적이 달라 오프셋 정책도 갈린다.

## 알려진 한계 — 다중 인스턴스

구독자를 인스턴스 메모리에 들고 있으므로 **인스턴스를 2대 이상 띄우면 구독자가 인스턴스별로 갈린다.** 게이트웨이가 라운드로빈하면 어떤 클라이언트는 A에, 어떤 클라이언트는 B에 붙고 각자 자기 인스턴스가 받은 것만 본다. 현재 배포는 정수장별 단일 서버(아키텍처 18장)라 문제되지 않는다 — 다중 노드로 가면 공유 브로드캐스트 경로가 필요하다.

WebSocket은 아직 없다. 현재는 SSE만 구현돼 있다.
