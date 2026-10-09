# Step 00 — 리포지토리 부트스트랩

- 일자: 2026-08-11
- 상태: ✅ 완료
- 대응 계획: [docs/scaffold/plan.md](../plan.md) Phase 0

## 목표

Gradle이 정상 동작하는 빈 멀티모듈 루트를 만든다. 이후 모든 Phase의 기반.

## 실행 내용

### 1. 환경 확인

| 도구 | 버전 | 상태 |
|---|---|---|
| JDK | 21.0.10 (Oracle, LTS) | ✅ |
| Docker | 29.7.2 | ✅ |
| git | 2.45.1.windows.1 | ✅ |
| Gradle CLI | 미설치 | wrapper 직접 부트스트랩으로 해결 |

### 2. 생성 파일

```text
swtp-platform/
├─ .gitignore                          # Gradle/IDE/Python/OS 제외, wrapper jar는 포함
├─ .editorconfig                       # UTF-8, LF(bat만 CRLF), 4칸 들여쓰기
├─ README.md                           # 프로젝트 개요, 기술 스택, 저장소 구조
├─ settings.gradle                     # rootProject.name = 'swtp-platform'
├─ build.gradle                        # 루트는 비움 (규약은 build-logic, 버전은 카탈로그)
├─ gradle.properties                   # group/version, parallel/caching/configuration-cache
├─ gradle/
│  ├─ libs.versions.toml               # ★ 버전 단일 통제점 (Boot 4.1.0 / Cloud 2025.1.2)
│  └─ wrapper/                         # Gradle 9.7.0 wrapper
├─ gradlew / gradlew.bat
└─ docs/scaffold/                      # (최초 scaffold/ 생성 후 docs/ 하위로 이동)
   ├─ plan.md                          # 스캐폴딩 계획
   └─ steps/                           # 단계별 실행 기록 (본 문서)
```

### 3. 주요 결정

- **Gradle 9.7.0**: 확인 시점(2026-08) 최신 안정 버전. wrapper 파일은 gradle/gradle 저장소 v9.7.0 태그에서 직접 다운로드 (로컬 Gradle CLI가 없어 `gradle wrapper` 명령 대신 사용한 방법)
- **버전 확정**: Spring Boot **4.1.0**(최신 GA), Spring Cloud **2025.1.2**(GitHub Releases에서 확인, 2026-06-12 발행, Boot 4.1 호환 공식 선언)
- **group**: `com.mo.swtp` (변경 지점은 gradle.properties 한 곳 — 초기 `kr.co.mindone.smartwater`에서 프로젝트명 swtp 통일 시 변경)
- **configuration-cache 활성화**: Gradle 9.x 표준. 문제 발생 시 개별 태스크만 비활성화 예정
- settings.gradle의 `includeBuild 'build-logic'`은 디렉토리가 없으면 빌드가 깨지므로 Phase 1에서 추가

## 검증 결과

```text
> gradlew --version
Gradle 9.7.0 / Launcher JVM 21.0.10 ✅

> gradlew projects
Root project 'swtp-platform' — No sub-projects
BUILD SUCCESSFUL in 15s
Configuration cache entry stored. ✅
```

## 다음 단계

Phase 1 — build-logic Convention Plugin 4종 (`swtp.java-common`, `spring-library`, `spring-boot-app`, `spring-cloud-app`) 작성
