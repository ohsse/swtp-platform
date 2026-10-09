# config-repo/ — Config Server(native) 서빙 설정

전 서비스가 여기서 공통 설정을 받는다. 앱은 `optional:configserver:`로 참조하므로 서버가 없어도 기동한다.

- `application.yml` — 전 환경 공통 + 로컬 기본값
- `application-docker.yml` — **환경 축**. 컨테이너 프로파일 재정의 (host/port 계열만)
- `application-dev.yml` — 로컬 개발 재정의
- `application-micro.yml` — 업무 마이크로서비스 전용 공통 설정(DB/JPA/Flyway/랜덤 포트)
- 서비스별 재정의가 필요하면 `<spring.application.name>.yml`을 추가한다

## 규칙

- **DB 접속정보는 `application-micro.yml`, `swtp.auth.mode`와 `logging.config`(콘솔 로그 포맷)는 config-repo가 SSOT다.** 앱 yml에 복제하지 않는다. 앱 yml의 같은 키는 config-server 없이 단독 기동할 때의 기본값일 뿐이다.
- **원격 값이 앱 로컬 설정을 이긴다.** 따라서 앱마다 달라야 하는 값은 여기 쓰면 안 된다 — `eureka.client.register-with-eureka`가 그 예로, 여기 쓰면 discovery-server가 자기 자신에게 등록을 시도한다.
- **프로파일은 여러 개를 둘 수 있다.** 환경 축(`docker`·`dev`), 앱 유형 축(`micro`), 정수장(테넌트) 축을 필요한 만큼 만든다 — 축의 개수를 제한하지 않는다. 다만 **한 키를 두 축이 함께 건드리지 않는다** — 그러면 누가 이기는지가 활성 프로파일 순서에 달리고, 어느 파일이 이겼는지는 로그에 남지 않는다. 키를 축마다 갈라 둔다.
- **config-server/gateway/discovery는 `micro` 프로파일을 활성화하지 않는다.** DB/JPA/Flyway와 업무 서비스 랜덤 포트 설정이 들어 있으므로 플랫폼 앱에 내려가면 안 된다.
- **`swtp.auth.mode`와 `swtp.auth.jwks-uri`는 환경변수(`SWTP_AUTH_MODE`/`SWTP_AUTH_JWKS_URI`)가 결정한다.** 한 쌍으로만 의미가 있으므로 함께 지정한다 — 나누면 "검증은 켜져 있는데 공개키는 없는 곳을 가리키는" 상태가 성립한다. yml 파일로 되돌려 놓지 않는다. **기본값은 `none`(검증하지 않음)이고**, 자체 auth-service로 검증하는 정수장이 `internal`을 명시한다 — 그래서 설정을 빠뜨린 실수는 401이 아니라 '조용히 열린 상태'로 나타난다. 배경: `infrastructure/docs/03-인증-기본값-none-전환.md`
- 환경 차이는 **host/port로만** 흡수한다. 계정·비밀번호·URL 형식은 전 환경 동일하게 둔다.
- **라우팅 URI를 여기에 복제하지 않는다.** 게이트웨이가 `lb://`를 쓰는 이유가 그 복제본(`gateway-docker.yml`) 제거였다.
- **`spring.cloud.bus.destination`을 지정하지 않는다.** config-server는 이 파일을 읽지 못해 자기 yml에 같은 값을 따로 둬야 하는데, 두 값이 갈라지면 발행 토픽과 수신 토픽이 달라져 **에러 없이 리프레시가 사라진다.** 기본 토픽명(`springCloudBus`)을 쓰면 선언이 0개라 갈라질 여지가 없다 — `infrastructure/kafka/create-topics.sh`가 그 토픽을 명시 생성한다.
- **`spring.kafka.bootstrap-servers`를 프로파일마다 재정의하지 않는다.** 설정 전파(Config Bus)가 이 주소를 쓰므로, 프로파일별로 갈리면 발행자와 수신자가 다른 브로커를 본다. 공통 `localhost:9092`와 `docker` 축의 `kafka:9092` 둘뿐이다 — 환경 차이는 host/port로만 흡수한다는 위 규칙대로 **호스트명만** 다르다.
