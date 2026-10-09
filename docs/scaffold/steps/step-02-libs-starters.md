# Step 02 — libs/swtp-common + starters 4종 골격

- 일자: 2026-08-11
- 상태: ✅ 완료
- 대응 계획: [docs/scaffold/plan.md](../plan.md) Phase 2

## 목표

앱보다 먼저 공유 라이브러리 계층(libs, starters)을 만들어 **의존 방향(apps → starters → libs)을 강제**한다.
이 단계에서 Phase 1의 convention plugin이 실제 모듈에 적용된 상태로 처음 검증된다.

## 실행 내용

### 1. 생성 모듈 (settings.gradle에 5개 include)

```text
libs/
└─ swtp-common/                        # 순수 공통 라이브러리 (swtp.spring-library)
   └─ ApiResponse<T>                   # API 공통 응답 record + 단위 테스트

starters/                              # 모두 swtp.spring-library 적용
├─ swtp-web-starter/                   # api project(':libs:swtp-common') — 의존 방향 시연
├─ swtp-kafka-starter/
├─ swtp-observability-starter/
└─ swtp-security-starter/
```

각 starter 구성 (동일 패턴):

- `Swtp{Web,Kafka,Observability,Security}AutoConfiguration` — `@AutoConfiguration` 골격
- `Swtp*StarterMarker` — 자동구성 로드 확인용 마커 빈
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- 테스트 2개: ApplicationContextRunner로 마커 빈 로드 확인 + imports 파일 오타 검증

### 2. 진행 중 발견/해결한 문제

| 문제 | 원인 | 해결 |
|---|---|---|
| `package org.springframework.boot.autoconfigure does not exist` | Boot 4에서 `spring-boot` 코어 jar에는 autoconfigure 패키지가 없음 | starter 의존을 `spring-boot` → **`spring-boot-autoconfigure`** 로 변경 |
| `Failed to load JUnit Platform` (테스트 실행 불가) | **Gradle 9는 `junit-platform-launcher`를 자동 주입하지 않음** (8.x까지의 암묵 주입 제거) | convention plugin(`swtp.spring-library`, `swtp.spring-boot-app`)에 `testRuntimeOnly 'org.junit.platform:junit-platform-launcher'` 추가 — 버전은 Boot BOM이 정렬 |

### 3. 주요 결정

- **마커 빈 패턴**: 실구현 전 골격 단계에서도 "자동구성이 imports 파일을 통해 로드되는가"를 테스트 가능하게 만드는 최소 장치. 실구현(Phase 6~) 시 마커는 실제 빈으로 대체/유지 판단
- **web-starter만 swtp-common에 `api` 의존**: Phase 6에서 ApiResponse를 공통 응답 처리에 사용할 예정이라 미리 연결. 나머지 starter는 필요해질 때 추가
- 직전 커밋에서 프로젝트 명칭이 smartwater → **swtp** 로 일괄 변경됨 (group `com.mo.swtp`, 패키지 `com.mo.swtp.*`)

### 4. 후속 결정 (Phase 2 완료 후 추가)

- **ApiResponse 간소화**: `code + data` 2필드 구조로 변경 — RestControllerAdvice에서도 동일 봉투로 리턴하기 위함. 에러 상세는 data 슬롯에 담고, 코드 체계는 Phase 6 ErrorCode에서 확정 (성공 코드 임시값 `SUCCESS`)
- **Lombok 전 모듈 기본 사용** → 컴파일 시점 도구이므로 convention plugin(`swtp.java-common`)에 배치. 버전(1.18.46)은 카탈로그에서 관리
- **OpenAPI 명세(springdoc 3.1.0, Boot 4 지원 세대)** → API를 노출하는 앱의 런타임 기능이므로 `swtp-web-starter`에 `api` 의존으로 배치. 공통 OpenAPI 설정 자동구성은 Phase 6에서 실구현
- 배치 기준: **컴파일/빌드 관심사는 build-logic, 런타임 기능·규약은 starter**

## 검증 결과

```text
> gradlew build
:libs:swtp-common:test / :starters:swtp-*-starter:test (4종) 모두 실행
BUILD SUCCESSFUL — 테스트 10개 (모듈당 2개) 전부 통과 ✅
Configuration cache entry stored.
```

알려진 경고: "Deprecated Gradle features ... incompatible with Gradle 10" — Boot Gradle 플러그인 쪽 경고로 추정, 빌드에는 영향 없음. Gradle 10 이전에 재확인 예정.

## 다음 단계

Phase 3 — apps 11개 모듈 골격 (`@SpringBootApplication` + application.yml 포트 고정표 + actuator health), config-server(native)/eureka/gateway(정적 라우팅), ai/ai-service(FastAPI), 앱간 의존 금지 검증 태스크
