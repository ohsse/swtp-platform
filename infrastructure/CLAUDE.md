# infrastructure/ — 단일 서버 스택

```bash
./gradlew bootJar   # 사전 조건 — 이미지 안에서 Gradle을 돌리지 않는다
docker compose -f infrastructure/docker/compose.yaml up -d --build
docker compose -f infrastructure/docker/compose.yaml --profile full up -d --build
```

**개발서버는 master 푸시 시 `Jenkinsfile`이 위 명령을 대신 실행한다.** 파이프라인이 새 배포 방식을 만들지 않는다 — 빌드·이미지·기동 명령은 여기 적힌 것 그대로이고, 앞뒤에 통합 테스트·스모크 테스트·`:prev` 태그 롤백이 붙을 뿐이다. 배포 대상은 Core 8종이며 Optional은 프로파일이 있어 자동으로 빠진다.

수동 배포·롤백 진입점은 `infrastructure/jenkins/deploy-core.ps1`(backup/build/up/rollback/deploy)과 `infrastructure/jenkins/smoke-test.ps1`이다 — Jenkins가 멈춰도 배포 경로를 함께 잃지 않도록 스크립트로 분리했다.

상세: `infrastructure/docs/01-CICD-파이프라인-도입.md`(설계) · `infrastructure/docs/02-Jenkins-개발서버-구축절차.md`(구축 절차)

## 배포 구분

Optional 서비스만 compose profiles로 분리한다(아키텍처 19장 정수장별 선택 배포): `auth` / `autonomous` / `pms` / `ai`, 묶음은 `full`. 나머지는 Core라 프로파일이 없다.

**compose `--profile`과 `SWTP_PROFILES`는 별개다. 섞지 않는다.** 전자는 *어떤 컨테이너를 띄울지*, 후자(Spring)는 *어떤 설정을 읽을지*다. Spring 프로파일은 플랫폼 앱(config-server/gateway/discovery)은 환경 축(`docker`)만, 업무 마이크로서비스는 환경 축 + `micro` 축을 쓴다. `compose.yaml`은 전 정수장 동일하고, 정수장별 차이는 어떤 컨테이너를 띄우는가뿐이다.

```bash
docker compose -f infrastructure/docker/compose.yaml --profile auth up -d --build   # auth-service 배포
docker compose -f infrastructure/docker/compose.yaml up -d --build                  # auth-service 미배포
```

**인증 모드는 `x-app-env`가 전달하고 기본은 `none`(검증 안 함)이다.** 자체 auth-service로 검증하는 정수장만 `.env`에 `SWTP_AUTH_MODE=internal`을 적는다 — JWKS 기본값이 `--profile auth`로 함께 띄우는 auth-service를 가리키므로 그 구성이면 한 줄이면 된다.

**두 변수는 갈라진 앵커가 아니라 공통 베이스 `x-app-env`에 둔다.** 게이트웨이와 업무 서비스가 같은 값을 같은 뜻으로 읽어야 하는 값이라, `x-platform-app-env`/`x-micro-app-env`에 각각 적으면 그 순간 어긋날 수 있는 구조가 생긴다(`SWTP_DB_HOST`가 micro에만 있는 것과는 반대 경우다).

**`SWTP_AUTH_JWKS_URI`의 기본값을 비우지 않는다.** 빈 문자열도 '존재하는 값'이라 앱 쪽 `${...:기본값}`이 발동하지 않고, 파생되는 `jwk-set-uri`가 비어 `JwtDecoder` 빈 생성이 깨진다.

배경과 되돌리는 법: `infrastructure/docs/03-인증-기본값-none-전환.md`

앱 서비스를 추가할 때는 `x-app-build`/`x-app-env`/`x-platform-app-env` 또는 `x-micro-app-env`/`x-app-logs` YAML 앵커를 재사용한다. 업무 마이크로서비스는 `server.port=0`이라 compose가 직접 내부 앱 포트를 healthcheck하지 않고, 준비 확인은 gateway 경유 actuator(`/{서비스명}/actuator/health`)로 한다.

## 경계 — 여기 있는 것 / 서비스에 있는 것

- **스키마 생성은 `postgresql/init/01-schemas.sql`, 테이블 DDL은 각 서비스 Flyway.** 여기에 테이블 DDL을 넣지 않는다.
- **Kafka는 auto-create를 끄고 `kafka/create-topics.sh`가 토픽을 만든다.** 도메인 토픽 8종 + 설정 전파용 `springCloudBus`.
- **Kafka 브로커 포트는 전 경로 9092로 통일한다.** 앱 설정에서 달라지는 것은 호스트명뿐이다(`localhost:9092` / `kafka:9092`). 컨테이너 안에서는 한 포트에 두 리스너를 바인딩할 수 없어 HOST 리스너가 9094를 쓰지만, 그건 `ports: "9092:9094"` 매핑 뒤로 감춘다. 포트를 바꿀 때 `KAFKA_ADVERTISED_LISTENERS`를 함께 고친다 — **클라이언트가 실제로 재접속하는 주소는 그쪽**이라, 매핑만 바꾸면 조용히 옛 포트를 찾아간다.
- 이미지 태그는 전부 명시 고정한다(`latest` 금지). Bitnami 이미지는 정책 변경으로 쓰지 않는다.

## 볼륨과 로그 경로

데이터는 named volume, **로그는 bind mount**(`../../logs:/logs`) — 호스트에서 직접 열람·반출하는 것이 목적이다. 서비스별 하위 디렉토리는 앱의 logback 설정이 만든다.

**로그 수집기가 읽는 경로는 stdout 단독이다.** 수집기는 이 로그 디렉토리를 읽지 않는다 — 읽으면 이중 적재가 된다. 수집기가 무엇이든(현재는 미배치, ELK 전환 예정) 이 계약은 유지한다.

**콘솔 포맷은 `SWTP_LOG_FORMAT`이 정한다** — `plain`(기본) / `ecs`. 수집 파이프라인을 운용하는 정수장만 `.env`에 `ecs`를 적는다. `ecs`는 Elastic Common Schema JSON 1줄이라 수집기가 필드를 그대로 색인할 수 있고, `plain`이면 수집은 되되 필드 추출이 성립하지 않는다. 파일 로그는 어느 값이든 평문이다.

## 관측 백엔드 — ELK (`elk` 프로파일)

```bash
docker compose -f infrastructure/docker/compose.yaml --profile elk up -d   # Kibana http://<서버>:5601
```

**`elk`는 앱 프로파일과 다른 축이라 `full`에 포함되지 않는다.** `full`은 Optional *앱* 묶음이고 ELK는 앱이 아니라 관측 백엔드다 — `--profile full`로 스모크 테스트할 때 Elasticsearch까지 뜨면 단일 서버가 눌린다. 함께 띄우려면 둘 다 지정한다.

- 서비스 5종: `elasticsearch` / `kibana` / `elk-init` / `filebeat` / `metricbeat` (전부 9.4.5. `elk-init`은 elasticsearch 이미지를 재사용하므로 **이미지는 넷이고, 넷의 버전을 항상 같이 올린다.** 어긋나면 템플릿·대시보드 적재가 실패한다)
- **로그**: Filebeat가 docker discovery로 `swtp-` 컨테이너 stdout을 읽어 ECS JSON을 필드로 색인한다
- **메트릭**: Metricbeat의 `docker` 모듈(컨테이너 상태·healthcheck) + `prometheus` 모듈(`/actuator/prometheus`)
- **보존**: `elk-init`가 ILM 정책을 박는다(기본 30일/샤드 5GB). `kafka-init`와 같은 자리이고 **Beats보다 먼저 돌아야 한다** — Beats는 정책이 있으면 덮어쓰지 않는데, Beats 기본 정책에는 delete 단계가 없어 인덱스가 무한히 자란다.
- **보안**: `xpack.security` 비활성이다. 9200은 루프백에만 공개하지만 **Kibana 5601은 인증 없이 내부망에 열린다** — 외부망 노출은 방화벽에서 막는다.

**중앙 집계로 전환할 때는 `elk` 프로파일을 켜지 않고 `SWTP_ELASTIC_HOSTS`만 외부 주소로 바꾼다.** 코드 변경 0, `.env` 한 줄이다. 그때는 ES가 정수장 밖으로 나가므로 보안을 반드시 켠다.

설정 파일 마운트 경로는 **이미지가 실제로 읽는 경로와 정확히 같아야 한다**(`/usr/share/<beat>/<beat>.yml`). 다르면 마운트도 기동도 성공하고 설정만 무시된다 — 철거한 Prometheus가 그 상태로 오래 방치됐다(step-16 함정 #1).

상세: `docs/scaffold/steps/step-17-elk-observability.md`
