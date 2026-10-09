<#
.SYNOPSIS
    Core 스택 8종을 이미지 빌드하고 기동한다. 실패하면 직전 이미지(:prev)로 되돌린다.

.DESCRIPTION
    Jenkins 파이프라인의 4~6단계를 담는다. Jenkins 없이 손으로도 그대로 돌아가야 하므로
    스크립트로 분리했다 — 파이프라인이 고장났을 때 배포 경로까지 함께 잃지 않기 위해서다.

    설계 근거: infrastructure/docs/01-CICD-파이프라인-도입.md
    구축 절차: infrastructure/docs/02-Jenkins-개발서버-구축절차.md

.PARAMETER Action
    backup   현재 <프로젝트>-<서비스>:latest 이미지에 :prev 태그를 붙인다. 롤백 대상을 만든다.
    build    Core 8종 이미지를 빌드한다. :latest를 덮어쓰므로 반드시 backup 뒤에 온다.
    up       스택을 기동하고 전 컨테이너가 healthy가 될 때까지 기다린다.
    rollback :prev를 :latest로 되돌리고 Core 8종만 재생성한다. 미들웨어는 건드리지 않는다.
    deploy   backup -> build -> up 을 연달아 실행한다 (손으로 배포할 때 쓴다).

.EXAMPLE
    # 개발서버에서 손으로 전체 배포
    .\deploy-core.ps1 -Action deploy

.EXAMPLE
    # 배포가 틀어졌을 때 직전 이미지로 복귀
    .\deploy-core.ps1 -Action rollback
#>
[CmdletBinding()]
param(
    [ValidateSet('backup', 'build', 'up', 'rollback', 'deploy')]
    [string]$Action = 'deploy',

    # 지정하지 않으면 스크립트 위치 기준으로 찾는다 — 호출 위치에 좌우되지 않게 한다.
    [string]$ComposeFile,

    # 첫 기동은 Flyway 마이그레이션 + Kafka 토픽 생성 + 헬스체크 start_period(20s)가 겹쳐 길다.
    [int]$WaitTimeoutSec = 600
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# ── 상수 ─────────────────────────────────────────────────────
# compose 프로젝트 이름은 compose.yaml의 `name:` 키가 정한다. 빌드 산출 이미지는
# <프로젝트>-<서비스>:latest 규칙으로 이름이 붙으므로 여기서 그대로 유도한다.
# compose.yaml의 name을 바꾸면 이 값도 같이 바꿔야 한다 — 안 바꾸면 백업·롤백이 조용히 빈손이 된다.
$ProjectName = 'swtp-platform'

# Core 8종 — compose.yaml에서 `profiles`가 없는 앱 서비스와 정확히 일치한다.
# Optional(auth/autonomous/pms/ai)과 미들웨어(timescaledb/kafka)는 대상이 아니다.
# 서비스를 Core로 승격하면 여기에 추가한다.
$CoreServices = @(
    'config-server',
    'discovery-server',
    'gateway',
    'master-service',
    'telemetry-service',
    'realtime-service',
    'job-service',
    'ems-service'
)

# ── 경로 확정 ────────────────────────────────────────────────
if (-not $ComposeFile) {
    $ComposeFile = Join-Path $PSScriptRoot '..\docker\compose.yaml'
}
if (-not (Test-Path $ComposeFile)) {
    throw "compose 파일을 찾지 못했다: $ComposeFile"
}
$ComposeFile = (Resolve-Path $ComposeFile).Path

# ── 보조 함수 ────────────────────────────────────────────────

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "=== $Message ===" -ForegroundColor Cyan
}

# 네이티브 실행 파일의 종료 코드를 직접 본다.
# PowerShell은 exe가 0이 아닌 코드로 끝나도 예외를 던지지 않으므로, 여기서 막지 않으면
# 이미지 빌드가 실패해도 파이프라인이 다음 단계로 넘어가 버린다.
function Invoke-Native {
    param(
        [string]$File,
        [string[]]$Arguments,
        [string]$What
    )
    Write-Host "  > $File $($Arguments -join ' ')" -ForegroundColor DarkGray
    & $File @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$What 실패 (종료코드 $LASTEXITCODE): $File $($Arguments -join ' ')"
    }
}

function Invoke-Compose {
    param(
        [string[]]$Arguments,
        [string]$What
    )
    Invoke-Native -File 'docker' -Arguments (@('compose', '-f', $ComposeFile) + $Arguments) -What $What
}

# 이미지 존재 확인에 `docker image inspect`를 쓰지 않는다 — 없을 때 stderr로 실패하고,
# Windows PowerShell에서 네이티브 stderr는 ErrorRecord로 감싸져 $ErrorActionPreference='Stop'과
# 충돌한다. `docker images -q`는 없으면 빈 문자열 + 종료코드 0이라 분기가 깔끔하다.
function Test-ImageExists {
    param([string]$Image)
    $id = & docker images -q $Image
    return -not [string]::IsNullOrWhiteSpace($id)
}

# ── 단계별 동작 ──────────────────────────────────────────────

function Invoke-Backup {
    Write-Step '4단계 — 직전 이미지 백업 (:latest -> :prev)'
    $backed = 0
    foreach ($svc in $CoreServices) {
        $image = "$ProjectName-$svc"
        if (Test-ImageExists "${image}:latest") {
            Invoke-Native -File 'docker' -Arguments @('tag', "${image}:latest", "${image}:prev") -What "이미지 백업($svc)"
            $backed++
        }
        else {
            # 최초 배포이거나 이미지를 지운 상태다. 이 서비스는 롤백 대상이 아니다.
            Write-Host "  - ${image}:latest 없음 — 최초 배포로 간주하고 건너뛴다" -ForegroundColor Yellow
        }
    }
    Write-Host "  백업 $backed/$($CoreServices.Count) 종" -ForegroundColor Green
}

function Invoke-Build {
    Write-Step '5단계 — Core 8종 이미지 빌드'
    # 서비스를 명시한다. 인자를 비우면 프로파일 설정에 따라 대상이 달라져
    # "무엇을 빌드했는가"가 실행 환경에 좌우된다.
    Invoke-Compose -Arguments (@('build') + $CoreServices) -What 'compose build'
}

function Invoke-Up {
    Write-Step '6단계 — 스택 기동 및 healthy 대기'
    # 서비스를 지정하지 않는다 — profiles가 없는 서비스, 즉 Core 8종 + 미들웨어가 정확히 대상이다.
    # --remove-orphans는 일부러 쓰지 않는다: 프로파일로 띄운 Optional 서비스(auth 등)가
    # 여기서는 고아로 보여 함께 제거된다.
    # --wait는 compose.yaml의 actuator healthcheck 계약을 그대로 대기 조건으로 쓴다.
    Invoke-Compose -Arguments @('up', '-d', '--wait', '--wait-timeout', "$WaitTimeoutSec") -What 'compose up'
    Write-Step '기동 결과'
    & docker compose -f $ComposeFile ps
}

function Invoke-Rollback {
    Write-Step '롤백 — 직전 이미지(:prev)로 복귀'
    $restored = @()
    foreach ($svc in $CoreServices) {
        $image = "$ProjectName-$svc"
        if (Test-ImageExists "${image}:prev") {
            Invoke-Native -File 'docker' -Arguments @('tag', "${image}:prev", "${image}:latest") -What "이미지 복원($svc)"
            $restored += $svc
        }
        else {
            Write-Host "  - ${image}:prev 없음 — 복원할 이전 버전이 없다" -ForegroundColor Yellow
        }
    }

    if ($restored.Count -eq 0) {
        # 최초 배포가 실패한 경우가 여기다. 되돌릴 곳이 없으므로 사실을 그대로 알린다.
        throw '복원할 :prev 이미지가 하나도 없다. 최초 배포 실패이므로 롤백이 성립하지 않는다 — 원인을 고치고 다시 배포할 것.'
    }

    # --no-deps로 미들웨어(DB/Kafka)를 건드리지 않는다. 롤백은 앱 문제를 되돌리는 것이지
    # 데이터 평면을 재기동하는 일이 아니다.
    Invoke-Compose -Arguments (@('up', '-d', '--force-recreate', '--no-deps', '--wait', '--wait-timeout', "$WaitTimeoutSec") + $restored) -What '롤백 재기동'
    Write-Host "  롤백 완료: $($restored -join ', ')" -ForegroundColor Green
}

# ── 진입점 ───────────────────────────────────────────────────

Write-Host "compose 파일 : $ComposeFile"
Write-Host "동작         : $Action"

switch ($Action) {
    'backup' { Invoke-Backup }
    'build' { Invoke-Build }
    'up' { Invoke-Up }
    'rollback' { Invoke-Rollback }
    'deploy' {
        Invoke-Backup
        Invoke-Build
        Invoke-Up
    }
}

Write-Host ""
Write-Host "완료: $Action" -ForegroundColor Green
