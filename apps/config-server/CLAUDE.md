# config-server — 설정 중앙화

`native`(파일시스템) 백엔드로 리포지토리 루트의 `config-repo/`를 서빙한다. 설정 내용의 규약은 `config-repo/CLAUDE.md`에 있고, 여기는 **서버 자신의 특수성**만 다룬다.

## 자기가 서빙하는 설정을 자기는 받지 못한다

`spring.config.import`가 없다 — 자기 자신을 config-server로 참조할 수 없기 때문이다. 그래서 **다른 앱이 `config-repo`에서 받는 값을 여기는 자기 `application.yml`에 따로 적어야 한다.** 이 비대칭이 이 서비스의 함정 대부분을 만든다.

- `spring.kafka.bootstrap-servers` — 설정 전파(Config Bus)가 쓴다. 환경 차이는 `SPRING_KAFKA_BOOTSTRAP_SERVERS` 환경변수로 흡수한다.
- `management.endpoints.web.exposure.include`에 **`prometheus`를 직접 나열한다.** 이 목록이 닫혀 있어 scrape 타깃에 들어 있으면서도 메트릭이 나온 적이 없었다(step-16 함정 #2).
- `management.health.binders.enabled: false` — **config-server의 health는 compose 기동 체인의 뿌리다.** Kafka가 늦게 뜬다는 이유로 여기가 DOWN이 되면 다른 앱이 하나도 뜨지 못한다.

`config-repo`에 값을 추가할 때 그 값을 config-server 자신도 써야 하는 것이면 **두 곳에 적어야 한다.** 한쪽만 고치면 조용히 갈라진다.

## compose에서 `x-app-env` 앵커를 쓰지 않는 유일한 앱

앵커가 전달하는 `SPRING_PROFILES_ACTIVE`를 받으면 **`native` 프로파일이 덮여 백엔드 자체가 깨진다.** 그래서 `compose.yaml`의 이 블록만 환경변수를 직접 나열한다 — 새 공통 환경변수를 앵커에 추가할 때 여기에도 손으로 더해야 한다.

## 레지스트리에 등록하지 않는다

다른 앱이 이 서버를 찾는 경로는 Eureka가 아니라 `CONFIG_SERVER_URI`(부트스트랩 시점에는 아직 레지스트리를 조회할 수 없다). `apps/CLAUDE.md` 포트 표의 "레지스트리에 등록하지 않음"이 그 뜻이다.
