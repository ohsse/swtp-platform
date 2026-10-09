# apps/ — 독립 실행 애플리케이션

각 앱은 자기 `@SpringBootApplication`을 가진 독립 실행체다. 앱끼리 Gradle 의존을 만들지 않는다(루트 불변식 1).

## 모듈 문서 — 앱마다 `CLAUDE.md`를 둔다

11개 앱이 각자 다른 역할을 가지므로 **앱마다 자기 `CLAUDE.md`를 갖는다**(step-19). 앱을 추가하면 이 파일도 함께 만든다.

층이 갈리는 기준은 **"여러 앱에 걸치는가"** 다.

| 이 파일(`apps/CLAUDE.md`) | 앱별 `CLAUDE.md` |
|---|---|
| 앱들을 **비교**해야 아는 것 — 포트·스키마 표, 스타터 조합 기준, 통합 테스트 표준형 | 그 앱 **하나만** 아는 것 — 역할과 경계, 자기만의 설정 이유, 자기 함정 |
| 새 앱을 만들 때 따르는 절차 | 이 앱을 고칠 때 밟는 지뢰 |

**포트·소유 스키마·배포 구분을 앱별 문서에 다시 적지 않는다.** 아래 표가 유일한 출처다 — 복제하면 갈라지고, 갈라진 것을 발견할 방법이 없다.

앱별 문서에는 **코드를 읽으면 알 수 있는 것을 쓰지 않는다.** 엔티티 목록·API 경로·클래스 구조는 코드가 답한다. 쓸 것은 "왜 이렇게 했는지"와 "무엇을 하면 안 되는지"다.

## 포트 · 소유 스키마 · 배포 구분

| 앱 | 포트 | 소유 스키마 | 배포 |
|---|---|---|---|
| gateway | 8080 | — | Core |
| config-server | 8888 | — | Core (레지스트리에 등록하지 않음) |
| discovery-server | 8761 | — | Core |
| master-service | 8081 | master, operation | Core |
| telemetry-service | 8082 | telemetry | Core |
| realtime-service | 8083 | — | Core |
| job-service | 8084 | job, batch, quartz | Core |
| ems-service | 8087 | ems | Core |
| auth-service | 8085 | auth | Optional (`auth`) |
| autonomous-service | 8086 | — | Optional (`autonomous`) |
| pms-service | 8088 | pms | Optional (`pms`) |

## application.yml 공통 골격

- `spring.config.import: "optional:configserver:${CONFIG_SERVER_URI:http://localhost:8888}"` — config-server 없이도 단독 기동한다.
- **DB 접속정보(url/username/password)를 앱에 두지 않는다.** `config-repo/application.yml`이 SSOT다. 앱은 자기 스키마만 지정한다 — JPA는 `hibernate.default_schema`, JdbcClient는 `hikari.connection-init-sql: SET search_path TO <스키마>, public, ext`.
  `hikari.schema`(=`setSchema()`)는 경로를 통째로 대체해 `ext` 확장 스키마가 조용히 사라지므로 쓰지 않는다.
- `spring.flyway.schemas`/`default-schema`에 자기 소유 스키마만 적는다. history 테이블도 자기 스키마 안에 둔다.

## 스타터 조합

`swtp.spring-cloud-app` 규약 위에 필요한 스타터만 고른다. **웹 스택(`spring-boot-starter-webmvc`)은 스타터가 전파하지 않으므로 앱이 직접 선언한다.**

| 필요 | 스타터 |
|---|---|
| API 노출 | `swtp-web-starter` (OpenAPI 경로를 앱 이름에서 자동 유도) |
| 토큰 검증 | `swtp-security-starter` (서블릿 전용. 게이트웨이 우회 직접 호출 차단) |
| DB | `swtp-persistence-starter` (JPA는 강제 전파 안 함 — master/auth/ems는 data-jpa, telemetry/job은 JdbcClient) |
| 이벤트 | `swtp-kafka-starter` |
| 로그·메트릭 | `swtp-observability-starter` (전 앱 공통) |

config/eureka 클라이언트는 둘 다 optional 성격이다 — 서버가 없어도 재시도만 할 뿐 기동을 막지 않는다.

## 통합 테스트 표준형

`apps/master-service/src/test/.../AbstractIntegrationTest.java`가 기준형이고, **`swtp-persistence-starter`나 `swtp-kafka-starter`를 의존하는 앱마다** 복제한다.

둘 다 쓰지 않는 앱(gateway·config-server·discovery-server와 아직 스캐폴드인 pms·autonomous)은 띄울 인프라가 없어 대상이 아니다 — `*ApplicationTest`(컨텍스트 로드)만 둔다. **스캐폴드에 그 스타터를 붙이는 커밋이 기준형을 함께 만드는 커밋이다.**

**그 커밋은 `*ApplicationTest`를 함께 지운다.** 둘은 공존하지 않는다 — persistence-starter가 붙으면 Flyway가 기동 시점에 DB를 찾으므로, DataSource가 없는 맨 `@SpringBootTest`는 컨텍스트 로드부터 실패한다. `verifyIntegrationTestBaseline`은 `AbstractIntegrationTest` 존재만 보므로 이 재발을 막지 못한다. ems가 이 경로를 밟았다(`apps/ems-service/docs/01`).

**컨테이너는 `@Testcontainers`/`@Container`가 아니라 싱글턴으로 띄운다** — static 필드 + static 초기화 블록의 `start()`. 그 확장은 컨테이너를 **테스트 클래스가 끝날 때 정지**시키는데, **같은 컨텍스트를 공유하는 다음 클래스는 죽은 컨테이너의 포트를 가리키는 DataSource를 물려받아 전부 `Connection refused`로 죽는다.**

발동 조건은 "클래스가 둘 이상"이 아니라 **"둘 이상이 같은 캐시된 컨텍스트를 공유"** 다 — job-service는 두 클래스가 서로 다른 `@TestPropertySource`를 가져 컨텍스트 캐시 키가 갈리므로 이 확장을 쓰고도 죽지 않는다. 그래서 오래 숨어 있었고, `verifyIntegrationTestBaseline`은 **파일 존재만 보므로** 잡지 못한다(`apps/ems-service/docs/02`).

**띄우는 컨테이너는 의존한 스타터와 일치시킨다.** 안 쓰는 컨테이너를 띄우면 테스트가 그만큼 느려지기만 한다 — auth-service는 PG만(kafka-starter 미의존), realtime-service는 Kafka만(persistence-starter 미의존) 띄운다.

`swtp.spring-boot-app` 규약이 이 대응을 `check` 시점에 검증한다. 규약을 어겨도 앱은 정상 동작하므로 컴파일러도 런타임도 알려주지 않는다.

- Testcontainers PG(`timescale/timescaledb-ha:pg17` — compose와 동일 이미지) + Kafka(`apache/kafka:4.3.1`) + `@ServiceConnection`
- `@SpringBootTest(properties = ...)`에 세 값을 **반드시** 넣는다 — `spring.config.import=`(개발 PC에 뜬 config-server가 결과를 좌우하는 것 차단), `spring.flyway.create-schemas=true`(Testcontainers에는 init SQL이 없음), `swtp.auth.mode=internal`(**운영 기본값이 `none`이라 명시하지 않으면 "토큰 없으면 401" 방어 테스트가 그냥 통과한다**)
- 토큰은 `testFixtures(project(':starters:swtp-security-starter'))`의 `SwtpTestJwt`로 만든다 — auth-service를 띄우지 않는다
- Boot 4 함정: `TestRestTemplate`은 `spring-boot-resttestclient` 별도 모듈 + `@AutoConfigureTestRestTemplate` opt-in

## 패키지 규약

앱 내부 자바 패키지는 **도메인 슬라이스 우선**이다. 계층(`web`·`service`…)을 최상위에 두지 않는다 —
변경은 거의 항상 도메인 단위로 오고, 도메인을 나중에 떼어낼 때 디렉토리째 옮길 수 있어야 한다.

```
com.mo.swtp.<service>
├── <Service>Application
├── <도메인>/                    ← 도메인이 2개 이상인 앱만 둔다. 1개면 이 단계를 생략한다
│   ├── web/         Controller
│   ├── service/     Service
│   ├── repository/  Repository (JPA 인터페이스 · JdbcClient 클래스 모두)
│   ├── dto/         Request / Response record
│   └── domain/      Entity, 도메인 이벤트, 도메인 enum
├── support/                     ← 도메인을 모르는 것: 공용 상수·enum, <Svc>ErrorCode, config
├── query/                       ← 도메인 2개 이상을 합치는 조회 (예약어 — 필요해질 때 만든다)
└── event/                       ← Kafka 릴레이·토픽 상수 (예약어 — 필요해질 때 만든다)
```

**도메인의 판정 기준은 "독립적인 CRUD API 표면과 자기 테이블을 갖는 소유권 단위"다.**
이 기준으로 auth의 `token`/`user`/`key`를 재보면 자기 REST 표면이 없고 인증 유스케이스 밖에서 의미가 없으므로 도메인 1개다.

### 형과 적용 현황

| 형 | 조건 | 앱 | 현재 |
|---|---|---|---|
| A형 (2단) | 도메인 2개 이상 | master-service | ✅ 적용됨 (기준형) |
| | | ems-service | ✅ 적용됨 (`ctrl`·`wnp` — `apps/ems-service/docs/02`) |
| B형 (1단) | 도메인 1개 — 도메인 세그먼트를 생략하고 최상위에 `web/service/repository/dto/domain` | auth-service | ⬜ 미적용 (`api,config,key,token,user`) |
| | | telemetry-service, job-service | ⬜ `sample/` 폐기 후 실도메인 착수 시 |
| 대상 외 | 도메인 상태가 없는 앱 | gateway, realtime-service, config-server, discovery-server | 현행 유지 |

**대상 외는 예외가 아니다.** 예외는 "대상인데 안 지킴"이고 이건 "대상이 아님"이다.
gateway(`filter`·`security`)와 realtime(`stream`)에 빈 `domain/repository/` 껍데기를 만들지 않는다.
B형의 ⬜는 **아직 안 끝난 상태**다 — 그 앱을 실도메인으로 손대는 커밋이 형을 맞추는 커밋이다.

**새 도메인 슬라이스는 `apps/master-service/.../tag/`를 복제해서 시작한다.**

### 의존 방향

```
  web  ──►  service  ──►  repository  ──►  domain
   │           │                             ▲
   │           └──► dto ──────────────────────┘   (Response.from(Entity))
   └──► dto

  누구나 ──► support          support는 잎(leaf) — 어떤 도메인 패키지도 모른다
  query  ──► 여러 도메인의 service
  event  ──► 여러 도메인의 domain

  <도메인A> ──✗──► <도메인B>    타입 직접 참조 금지. ID(String)로만 잇는다
```

- `domain`은 같은 앱의 `web`·`service`·`repository`·`dto`를 모른다.
- **`service`가 `dto`의 Response를 반환하는 것은 허용한다.** 다만 그 타입을 **Kafka 페이로드로 재사용하지 않는다** —
  하는 순간 REST 응답 스키마가 토픽의 와이어 계약이 되어 프론트 요청이 컨슈머를 깬다.
- **도메인 간 타입 직접 참조 금지.** `Eqp.fcltId`·`EqpTag.eqpId`·`PrcsFclt.prcsId`가 전부 `String` 스칼라이고
  `V2__master_domain.sql`이 "FK 제약은 걸지 않는다 — 참조 정합성은 애플리케이션 책임"이라 적어 뒀다.
  DB 설계가 이미 이 규칙대로다. `@ManyToOne`을 하나 들이면 되돌리는 것이 스키마 논쟁이 된다.

**이 방향들을 검사하는 자동 장치는 없다**(ArchUnit 미도입). 리뷰가 유일한 그물이다.

### 교차 도메인 엔티티의 소유

```
① 그 관계를 만들고 끊는 API를 소유하는 도메인에 둔다
② ①로 안 갈리면, PK가 한쪽 도메인의 키 단독인 쪽이 소유한다
```

`prcs_fclt_r`은 ①("공정에 시설을 편성한다")로 `prcs`, `eqp_tag_p`는 ①로 안 갈려 ②(PK가 `tag_sn` 단독)로 `tag`가 갖는다.

### 네이밍

| 종류 | 접미사 | 위치 |
|---|---|---|
| Entity | **없음** (`Tag`, `Eqp`) — 행위를 가진 도메인 객체다. 영속 역할로 이름 짓지 않는다 | `<도메인>/domain/` |
| Repository | `*Repository` | `<도메인>/repository/` |
| Service | `*Service` | `<도메인>/service/` |
| Controller | `*Controller` | `<도메인>/web/` |
| 요청/응답 | `<도메인>*Request` / `<도메인>Response` — **도메인 접두사 필수** | `<도메인>/dto/` |
| 배선 | `*Configuration` (`*Config` 쓰지 않는다) | `support/config/` |
| 설정 바인딩 | `*Properties` | `support/config/` |
| 에러 코드 | `<Svc>ErrorCode` — **앱당 enum 하나.** 코드 문자열이 프론트 i18n 키라 유일성 보장 단위가 서비스다 | `support/` |

**DTO를 `XxxDtos` 한 클래스에 중첩 record로 모으지 않는다.** springdoc은 중첩 record도 단순 클래스명으로
OpenAPI 스키마를 등록하므로(`use-fqn` 미설정), 도메인마다 `AddRequest`를 두면 한 스펙 안에서
`#/components/schemas/AddRequest` 한 칸을 여럿이 다툰다. 접두사 붙은 top-level record가 이 문제를 없앤다.
같은 이유로 **DTO 와일드카드 import(`import ....TagDtos.*`)를 쓰지 않는다.**

**자동생성 파일 헤더 주석 블록을 붙이지 않는다.** 패키지 경로를 적는 헤더는 파일을 옮기는 순간 거짓이 된다.

약어 도메인명(`prcs`·`eqp`·`fclt`)은 DDL·ERD와 맞춘 것이라 유지한다. 대신 **각 도메인에 `package-info.java`로
한글 한 줄**을 둔다.

### 일회용 `sample/` 패키지

`sample/` 패키지가 있는 앱(telemetry·job)의 그 패키지는 수직 슬라이스 검증용 일회용 코드다 — 실제 도메인 설계 시 패키지째 삭제한다.

**삭제할 때 세 곳을 함께 지운다** — main 패키지, 그 패키지를 쓰는 **테스트**, Flyway 마이그레이션. master-service가 main만 지우고 테스트를 남겨 `compileTestJava`가 깨진 적이 있다. 테스트는 다른 디렉토리에 있어 패키지를 지울 때 눈에 띄지 않는다.

## API 문서화

**swagger-ui가 프론트에게는 유일한 API 계약 문서다.** `swtp-web-starter`가 명세의 뼈대(Bearer 스킴·상대 서버 URL·공통 에러 응답)를 자동으로 얹으므로, 앱이 채우는 것은 **도메인만 아는 것**뿐이다. 근거와 기각한 대안은 `apps/docs/01-API-문서화-규약.md`에 있다.

### Javadoc과 애노테이션은 독자가 다르다

```
Javadoc     = 개발자용. 왜 이렇게 설계했는가, 무엇을 하면 안 되는가, 다른 코드와의 관계
애노테이션   = 소비자용. 이 API가 무엇을 하는가, 어떻게 부르는가, 무엇이 돌아오는가
```

둘은 중복이 아니라 **분리**다. 판별 기준은 *"게이트웨이 밖의 프론트 개발자에게 필요한 문장인가"* 하나다. `DrvmdChgController`의 "이력 생성 엔드포인트를 여기 두면 `iss_svc_cd`가 위조 가능해진다"는 Javadoc에 남고, `CtrlGrpController`의 "결과가 없으면 빈 배열 200이다"는 `@Operation`으로 간다.

**같은 문장을 양쪽에 적지 않는다.** 옮길 것이지 복사할 것이 아니다.

### 무엇을 어디에 다는가

| 애노테이션 | 위치 | 규칙 |
|---|---|---|
| `@Tag(name, description)` | 컨트롤러 **필수** | `name`은 한글 도메인명(`"제어그룹"`). **같은 도메인의 컨트롤러가 여럿이면 같은 `name`을 공유한다** — 소비자가 찾을 자리는 화면 단위이지 클래스 단위가 아니다. 두 번째 컨트롤러는 `description`을 반복하지 않는다 |
| `@Operation(summary, description)` | 핸들러 **필수** | `summary`는 목록에 뜨는 짧은 구. `description`에는 **계약만** 적는다 — 빈 배열인지 404인지, 부분 성공이 있는지, 응답 순서가 보장되는지 |
| `@Parameter(description)` | `@RequestParam`·`@PathVariable` | 설명만 |
| `@Schema(description, example)` | DTO record 컴포넌트 | 의미와 예시값만 |
| `@ApiResponse` | 핸들러 | **도메인 고유 응답만.** 공통 400·401·500은 스타터가 주입한다 |
| `@SecurityRequirements` | 공개 핸들러 | 스타터가 건 전역 bearer 요구를 해제한다 |

### 스프링·검증 애노테이션이 이미 말한 것을 다시 적지 않는다

springdoc이 자동으로 유도한다. 손으로 적으면 두 벌이 되고, 검증 규칙을 고칠 때 스펙이 뒤에 남는다.

| 이미 표현된 것 | 유도되는 스펙 | 적지 않을 것 |
|---|---|---|
| `@NotBlank`·`@NotNull` | `required` | `@Schema(requiredMode=)` |
| `@Size(max = N)` | `maxLength` | `@Schema(maxLength=)` |
| `@RequestParam(required=false)` | `required: false` | `@Parameter(required=)` |
| enum 타입 | `enum: [...]` | 값 목록 나열 |

**그래서 검증 애노테이션이 없는 도메인은 스펙도 부실하다.** master의 `tag`·`eqp`·`prcs`·`fclt`가 그 상태다 — 그 도메인을 문서화하는 커밋이 `@NotBlank`·`@Size`를 함께 넣는 커밋이다.

### 함정 둘

**swagger의 `@ApiResponse`는 FQN으로 쓴다.** 공용 봉투 `com.mo.swtp.common.api.ApiResponse`와 단순명이 충돌하는데, 봉투는 모든 핸들러의 반환 타입에 나오고 애노테이션은 도메인 고유 에러에만 붙는다 — **빈도가 낮은 쪽을 FQN으로 미룬다.**

**식별자 필드에 `example`을 넣지 않는다.** ID 체계는 정수장마다 다를 수 있어서 예시가 형식 규칙으로 읽힌다. 정렬순서·수량처럼 형태가 자명한 값에만 넣는다.
