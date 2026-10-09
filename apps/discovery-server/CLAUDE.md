# discovery-server — 서비스 레지스트리

Eureka 서버. 게이트웨이의 `uri: lb://<서비스명>`이 여기서 대상 인스턴스를 찾는다 — **이 서버가 없으면 라우트 정의는 살아 있는 채로 전부 503**이 된다(404가 아니라서 "라우트는 있는데 인스턴스가 없다"로 구분된다).

## 자기 자신은 등록도 조회도 하지 않는다

`register-with-eureka: false`, `fetch-registry: false`. 단일 노드라 자기를 자기에게 등록할 이유가 없다.

**이 두 값을 `config-repo`에 두지 않는다.** 원격 값이 앱 로컬 설정을 이기므로, 거기 쓰면 전 서비스에 퍼져서 다른 앱들이 등록을 멈춘다(`config-repo/CLAUDE.md`가 이 서비스를 예시로 든 이유다).

## 단일 서버 배포에 맞춘 3개 튜닝

| 설정 | 값 | 기본값이 안 맞는 이유 |
|---|---|---|
| `enable-self-preservation` | `false` | 하트비트가 끊겨도 등록을 붙잡아 두는 기능인데, 단일 서버(아키텍처 18장)에서는 컨테이너를 내려도 **유령 인스턴스가 남아 게이트웨이가 죽은 대상으로 라우팅**한다 |
| `response-cache-update-interval-ms` | `5000` | 기본 30초는 클라이언트 조회 주기(5초)를 무의미하게 만든다 |
| `eviction-interval-timer-in-ms` | `10000` | 만료 리스 회수 주기. 기본 60초 |

게이트웨이의 `spring.cloud.loadbalancer.cache.ttl: 5s`와 한 세트다 — 한쪽만 줄이면 다른 쪽 캐시가 병목이 된다.

## 테스트에서 규약을 되돌린다

`build.gradle`이 `systemProperty 'eureka.client.enabled', 'true'`를 건다. **지우면 컨텍스트 로드가 실패한다.**

`swtp.spring-cloud-app` 규약은 테스트에서 Eureka 클라이언트를 끄는데(등록 재시도로 느려지지 않게), Eureka **서버** 자동구성이 **클라이언트** 자동구성이 만드는 `ApplicationInfoManager`를 주입받는다. 여기서만 예외적으로 되살려야 한다. 네트워크 호출은 생기지 않는다 — 위의 `register-with-eureka: false`가 막는다.
