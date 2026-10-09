# Step 01 — build-logic Convention Plugin

- 일자: 2026-08-11
- 상태: ✅ 완료
- 대응 계획: [plan.md](../plan.md) Phase 1

## 목표

모든 모듈이 공유할 빌드 규약을 build-logic(포함 빌드)의 precompiled Groovy script plugin으로 확립한다.
이후 각 모듈의 build.gradle은 `plugins { id 'swtp.…' }` 한 줄 + 자기 의존성 선언만 가진다.

## 실행 내용

### 1. 생성 파일

```text
build-logic/
├─ settings.gradle        # 루트 gradle/libs.versions.toml 카탈로그 공유
├─ build.gradle           # groovy-gradle-plugin + spring-boot-gradle-plugin 구현체
└─ src/main/groovy/
   ├─ swtp.java-common.gradle       # java + toolchain 21 + UTF-8 + JUnit Platform
   ├─ swtp.spring-library.gradle    # java-common + java-library + Boot BOM + configuration-processor
   ├─ swtp.spring-boot-app.gradle   # java-common + Boot 플러그인 + BOM + actuator/test 기본 의존
   └─ swtp.spring-cloud-app.gradle  # spring-boot-app + Spring Cloud BOM
```

루트 `settings.gradle`의 `pluginManagement`에 `includeBuild 'build-logic'` 추가.

### 2. 플러그인 적용 대상 (이후 Phase에서 사용)

| Plugin | 적용 대상 |
|---|---|
| `swtp.java-common` | (직접 적용하지 않음 — 아래 플러그인들의 베이스) |
| `swtp.spring-library` | libs/swtp-common, starters/* |
| `swtp.spring-boot-app` | apps/* (일반 서비스) |
| `swtp.spring-cloud-app` | apps/config-server, discovery-server, gateway 등 Cloud 사용 앱 |

### 3. 주요 결정

- **BOM 정렬은 `platform(SpringBootPlugin.BOM_COORDINATES)`** — `io.spring.dependency-management` 플러그인은 maintenance mode라 사용하지 않음. Boot 버전을 올리면 BOM 좌표가 플러그인 버전을 따라가므로 카탈로그의 `spring-boot` 한 줄만 바꾸면 됨
- **Java toolchain 버전도 카탈로그에서 읽음** (`libs.findVersion('java')`) — 버전 통제점 단일화. Groovy precompiled plugin에서는 Kotlin DSL과 달리 `libs.x.y` 타입세이프 접근이 안 되므로 `VersionCatalogsExtension` API 사용
- **라이브러리(spring-library)는 Boot 플러그인을 적용하지 않음** — 실행 가능 jar가 아니므로 BOM으로 버전만 정렬
- 문서 저장 경로를 `scaffold/` → **`docs/scaffold/`** 로 변경 (사용자 요청)

## 검증 결과

```text
> gradlew :build-logic:build
:build-logic:compileGroovyPlugins / :build-logic:validatePlugins 포함
BUILD SUCCESSFUL in 39s (9 tasks) ✅

> gradlew build
BUILD SUCCESSFUL — configuration cache 정상 ✅
```

플러그인이 실제 모듈에 적용된 상태의 검증은 Phase 2(swtp-common, starters)에서 수행한다.

## 다음 단계

Phase 2 — `libs/swtp-common` + `starters/swtp-{web,kafka,observability,security}-starter` 골격 생성, ApplicationContextRunner로 자동구성 로드 테스트
