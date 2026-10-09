# Step 13 — 콘솔 로그 포맷을 정수장별 스위치로 외부화

- 일자: 2026-08-14
- 상태: ✅ 완료 (스타터 테스트 통과, 컨테이너 E2E는 아래 "검증" 참조)
- 관련: [step-09-observability-persistence.md](step-09-observability-persistence.md), [step-12-auth-mode-2values.md](step-12-auth-mode-2values.md)

## 발단

전 앱의 콘솔 로그가 ECS JSON 한 줄로만 나와 개발 중 눈으로 읽기 어려웠다.
확인해 보니 두 가지가 드러났다.

1. **포맷이 코드에 못 박혀 있었다.** `SwtpObservabilityEnvironmentPostProcessor`가
   `logging.structured.format.console=ecs`를 `DEFAULTS`에 하드코딩했다. `addLast` 주입이라
   재정의 자체는 가능했지만, 정수장별로 바꿀 스위치가 어디에도 없었다.
2. **로그 수집 파이프라인은 정수장별 선택 사항이다.** Alloy→Loki를 운용하지 않는 정수장에서는
   JSON일 이유가 없다. 사람이 `docker logs`로 직접 읽는 것이 유일한 소비 경로가 된다.

## 결정 (2026-08-14)

- **포맷 결정 주체를 `SWTP_LOG_FORMAT` 환경변수로 옮긴다** — `plain`(기본) / `ecs`.
  `step-12`가 `swtp.auth.mode`에 정착시킨 패턴 그대로다: 설정의 존재와 기본값은 `config-repo`가 소유하고,
  정수장별 실제 값은 배포 `.env` 한 줄이 정한다. 사이트 프로파일 축은 `step-12`에서 철회했으므로 되살리지 않는다.
- **기본값은 어디서든 `plain`이다.** 수집 파이프라인이 없는 정수장과 로컬 개발이 다수이고,
  JSON은 사람이 읽을 수 없는 대신 기계가 읽을 때만 이득이다. 수집하는 곳만 명시적으로 켠다.
- **스위치는 `logging.config`다 — 포맷 프로퍼티가 아니다.** (아래 함정 1)
  값이 `classpath:swtp-logback-${SWTP_LOG_FORMAT:plain}.xml`이라 환경변수가 곧 파일 선택이 된다.
- **파일 로그는 어느 포맷에서든 평문을 유지한다.** 감사·장애분석용으로 사람이 직접 열어 보는 백업이고,
  Alloy가 읽지 않으므로 구조화할 이유가 없다. `step-09`가 세운 "두 경로 분리"는 그대로다.

## 변경 내역

| 대상 | 변경 |
|---|---|
| `swtp-logback.xml` | **삭제** — 아래 셋으로 분리 |
| `swtp-logback-common.xml` (신규) | `springProperty`·파일 appender·`<root>` — 포맷과 무관한 공통부 |
| `swtp-logback-plain.xml` (신규) | Boot `console-appender.xml` + 공통부 |
| `swtp-logback-ecs.xml` (신규) | Boot `structured-console-appender.xml` + 공통부, 포맷 `ecs` 고정 |
| `SwtpObservabilityEnvironmentPostProcessor.java` | `logging.config`에 플레이스홀더 도입, `logging.structured.format.console` 제거 |
| `SwtpObservabilityEnvironmentPostProcessorTest.java` | 계약 갱신 + `SWTP_LOG_FORMAT`이 파일을 고르는지 검증 추가 |
| `SwtpLogbackConfigurationTest.java` | 콘솔 encoder 타입으로 포맷 전환을 실제 기동 경로에서 검증 (2건 추가) |
| `config-repo/application.yml` | `logging.config` SSOT 선언 |
| `infrastructure/docker/compose.yaml` | `x-app-env`에 전달 추가 + **config-server 블록에 직접 추가** (함정 2) |
| `infrastructure/docker/.env.example` | `SWTP_LOG_FORMAT` 항목 |
| `infrastructure/alloy/config.alloy` | 주석만 — `plain`일 때의 동작 명시. **코드는 바꾸지 않았다** |
| 문서 | `starters`·`infrastructure`·`config-repo` 세 CLAUDE.md |

## 함정 기록

1. **`logging.structured.format.console`을 비운다고 평문이 되지 않는다.**
   Boot 조각 `structured-console-appender.xml`은 `StructuredLogEncoder`를 하드코딩한다.
   포맷 프로퍼티는 그 인코더에 넘길 **값**일 뿐이라, 비우면 평문으로 돌아가는 대신 인코더가 기동에 실패한다.
   평문/구조화 전환은 **콘솔 appender 자체를 갈아끼우는 일**이다. 그래서 스위치가 `logging.config`가 됐다.
   — Boot 4.1.0의 `console-appender.xml`과 `structured-console-appender.xml`은 **appender 이름이 둘 다 `CONSOLE`**이라
   `<appender-ref>`를 건드리지 않고 조각만 바꿔 끼울 수 있다. 이 사실이 분리 구조를 성립시킨다.

2. **config-server는 프로파일 축 바깥에 있다.**
   `compose.yaml`의 config-server 블록만 `environment: *app-env` 앵커를 쓰지 않는다 —
   `SPRING_PROFILES_ACTIVE`를 받으면 `application.yml`의 `native`(백엔드 지정)가 밀려나 Config Server가 깨진다.
   게다가 `spring.config.import`가 없어 **자기가 서빙하는 config-repo를 자기는 읽지 않는다.**
   따라서 `config-repo`에만 설정하면 다른 앱이 전부 전환돼도 config-server 로그만 그대로 남는다.
   플레이스홀더를 스타터 기본값에 심고 compose에서 변수를 직접 전달해 해결했다.

3. **`swtp-logback-ecs.xml`의 `<property>`는 `defaults.xml` include 뒤에 와야 한다.**
   `defaults.xml`이 `CONSOLE_LOG_STRUCTURED_FORMAT`을 빈 문자열로 먼저 정의하므로,
   앞에 두면 덮어써져 포맷이 비고 인코더가 실패한다.

4. **`<springProperty>`는 include된 파일 안에서도 동작한다.** 공통부를 `<included>`로 뽑을 때의
   최대 리스크였다(해석 실패 시 `LOG_DIR`이 `_IS_UNDEFINED`가 되어 rollingPolicy가 기동에 실패한다).
   `SwtpLogbackConfigurationTest`의 파일 분리 테스트가 이를 계속 지켜준다.

5. **Config Server는 플레이스홀더를 해석하지 않고 원문 그대로 내려보낸다.** 실측으로 확인했다:

   ```
   "logging.config":"classpath:swtp-logback-${SWTP_LOG_FORMAT:plain}.xml"
   "swtp.auth.mode":"${SWTP_AUTH_MODE:internal}"
   ```

   해석은 각 앱이 **자기 환경**에서 한다. 따라서 config-server 한 대의 환경변수가 전 서비스를
   강제하지 않는다 — `config-repo`는 "어떤 변수가 무엇을 고르는가"라는 **메커니즘**을 소유하고,
   **값**은 각 배포 서버의 `.env`가 정한다. 정수장별 제어에 필요한 것이 정확히 이 분담이다.

6. **bootRun으로 띄운 config-server는 아무 설정도 서빙하지 않는다.** `search-locations`가
   상대경로(`optional:file:./config-repo`)인데 bootRun의 작업 디렉토리는 `apps/config-server`다.
   컨테이너는 `/config-repo`를 마운트하므로 정상이고, 앱들은 `optional:configserver:`라 조용히 폴백한다.
   로컬에서 서빙 내용을 확인하려면 `SPRING_CLOUD_CONFIG_SERVER_NATIVE_SEARCHLOCATIONS`에 절대경로를 준다.
   (이 변경으로 생긴 문제가 아니라 원래 그러했다.)

## 알려진 한계

1. **`plain`인 정수장에서는 Grafana 라벨 필터가 빈다.** Alloy의 `stage.json`이 파싱에 실패해
   `level`/`service_name` 승격을 건너뛴다. 로그 자체는 그대로 수집되므로 조용한 데이터 유실은 없지만,
   대시보드가 비어 보이는 것으로 드러난다. 수집을 쓰는 정수장은 반드시 `ecs`로 둔다.
2. **포맷 미지정을 기동 시점에 잡지 못한다.** 값을 빠뜨리면 `plain`으로 조용히 폴백한다
   (`step-12`의 인증 모드와 같은 성격의 한계다).
3. **런타임 전환은 불가능하다.** 로그 포맷은 기동 시점에 굳는다. `/actuator/refresh`로 바뀌지 않으므로
   전환에는 재기동이 필요하다.

## 검증

```bash
./gradlew :starters:swtp-observability-starter:test   # 통과
```

`SwtpLogbackConfigurationTest`가 실제 Boot 기동 경로로 다음을 확인한다 — 기본이 `PatternLayoutEncoder`,
`SWTP_LOG_FORMAT=ecs`면 콘솔이 `StructuredLogEncoder`이고 **그때도 파일은 `PatternLayoutEncoder`**.

실제 앱(config-server) 기동으로도 양방향을 확인했다 — 기본은 평문, `SWTP_LOG_FORMAT=ecs`는 ECS JSON,
그리고 **ecs 실행 중에도 `logs/config-server/config-server.log`는 평문**이었다.

컨테이너 검증:

```bash
./gradlew bootJar
docker compose -f infrastructure/docker/compose.yaml up -d --build
docker logs swtp-config-server --tail 5    # 평문
docker logs swtp-master-service --tail 5   # 평문

# .env에 SWTP_LOG_FORMAT=ecs 를 적고 재기동
docker logs swtp-config-server --tail 5    # ECS JSON — 프로파일 축 바깥인 앱도 전환되는지가 핵심
```

Grafana(`:3000`)에서 `{service_name="master-service"}`로 `ecs`일 때 라벨 승격이 살아 있는지 확인한다.
