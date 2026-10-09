<#
.SYNOPSIS
    배포된 Core 스택 8종이 실제로 응답하는지 확인한다.

.DESCRIPTION
    두 축으로 본다.
      1) 직접 확인 — 각 앱의 published 포트로 /actuator/health 를 찌른다.
      2) 관통 확인 — 게이트웨이를 경유해 업스트림 health를 부른다.

    2번 한 발이 네 가지를 동시에 단언한다: 게이트웨이 라우트 매칭, RewritePath 접두사 제거,
    Eureka 레지스트리 조회(lb://), 업스트림 앱의 실제 응답. 그래서 1번보다 정보량이 크다.

    토큰이 필요 없는 이유는 apps/gateway/src/main/resources/application.yml 의
    permit-all-paths에 /*-service/actuator/** 가 있기 때문이다. 이 화이트리스트를 좁히면
    이 스크립트가 401로 깨진다 — 그때는 여기도 함께 고친다.

    설계 근거: infrastructure/docs/01-CICD-파이프라인-도입.md

.EXAMPLE
    .\smoke-test.ps1

.EXAMPLE
    # 게이트웨이 포트가 다른 환경
    .\smoke-test.ps1 -GatewayBaseUrl http://localhost:9080
#>
[CmdletBinding()]
param(
    [string]$HostName = 'localhost',
    [string]$GatewayBaseUrl,

    # 레지스트리 전파 대기용. Eureka 조회 주기 5초 + 게이트웨이 LB 캐시 TTL 5초라
    # 컨테이너가 healthy가 된 직후에도 lb:// 라우팅은 잠시 503일 수 있다.
    [int]$RetryCount = 12,
    [int]$RetryDelaySec = 5,
    [int]$TimeoutSec = 10
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if (-not $GatewayBaseUrl) {
    $GatewayBaseUrl = "http://${HostName}:8080"
}

# ── 검사 대상 ────────────────────────────────────────────────
# 포트는 apps/CLAUDE.md 「포트 · 소유 스키마 · 배포 구분」 표가 유일 출처다.
# 앱을 추가하거나 포트를 바꾸면 그 표와 여기를 함께 고친다.
$DirectChecks = @(
    @{ Name = 'config-server';     Url = "http://${HostName}:8888/actuator/health" }
    @{ Name = 'discovery-server';  Url = "http://${HostName}:8761/actuator/health" }
    @{ Name = 'gateway';           Url = "http://${HostName}:8080/actuator/health" }
    @{ Name = 'master-service';    Url = "http://${HostName}:8081/actuator/health" }
    @{ Name = 'telemetry-service'; Url = "http://${HostName}:8082/actuator/health" }
    @{ Name = 'realtime-service';  Url = "http://${HostName}:8083/actuator/health" }
    @{ Name = 'job-service';       Url = "http://${HostName}:8084/actuator/health" }
    @{ Name = 'ems-service';       Url = "http://${HostName}:8087/actuator/health" }
)

# 게이트웨이 관통. 전 서비스를 다 찌를 필요는 없다 — 라우팅 기계는 하나이므로
# 두 개면 "규약이 동작한다"와 "한 건이 우연히 된 게 아니다"를 모두 말한다.
$RoutedChecks = @(
    @{ Name = 'gateway -> master-service'; Url = "$GatewayBaseUrl/master-service/actuator/health" }
    @{ Name = 'gateway -> ems-service';    Url = "$GatewayBaseUrl/ems-service/actuator/health" }
)

# ── 보조 함수 ────────────────────────────────────────────────

# actuator health는 상태가 UP이 아니어도 HTTP 200으로 답할 수 있고(구성에 따라 다르다),
# DOWN일 때 503을 낸다. 두 경우를 모두 실패로 잡으려면 본문의 status를 직접 본다.
function Test-Health {
    param(
        [string]$Name,
        [string]$Url,
        [int]$Retries,
        [int]$DelaySec
    )

    for ($attempt = 1; $attempt -le $Retries; $attempt++) {
        try {
            $response = Invoke-RestMethod -Uri $Url -Method Get -TimeoutSec $TimeoutSec
            $status = $response.status
            if ($status -eq 'UP') {
                Write-Host ("  [OK]   {0,-28} {1}" -f $Name, $Url) -ForegroundColor Green
                return $true
            }
            $lastError = "status=$status"
        }
        catch {
            $lastError = $_.Exception.Message
        }

        if ($attempt -lt $Retries) {
            Write-Host ("  [..]   {0,-28} 재시도 {1}/{2} — {3}" -f $Name, $attempt, $Retries, $lastError) -ForegroundColor DarkGray
            Start-Sleep -Seconds $DelaySec
        }
    }

    Write-Host ("  [FAIL] {0,-28} {1}" -f $Name, $Url) -ForegroundColor Red
    Write-Host ("         마지막 오류: {0}" -f $lastError) -ForegroundColor Red
    return $false
}

# ── 실행 ─────────────────────────────────────────────────────

Write-Host ""
Write-Host "=== 스모크 테스트 시작 (대상 $HostName) ===" -ForegroundColor Cyan

$failed = @()

Write-Host ""
Write-Host "[1/2] 직접 확인 — 각 앱의 published 포트" -ForegroundColor Cyan
foreach ($check in $DirectChecks) {
    # 이 시점에 compose --wait가 이미 healthy를 보장했으므로 재시도를 짧게 잡는다.
    if (-not (Test-Health -Name $check.Name -Url $check.Url -Retries 3 -DelaySec 3)) {
        $failed += $check.Name
    }
}

Write-Host ""
Write-Host "[2/2] 관통 확인 — 게이트웨이 경유 (라우팅 + 레지스트리)" -ForegroundColor Cyan
foreach ($check in $RoutedChecks) {
    # 레지스트리 전파를 기다려야 하므로 여기만 재시도를 길게 잡는다.
    if (-not (Test-Health -Name $check.Name -Url $check.Url -Retries $RetryCount -DelaySec $RetryDelaySec)) {
        $failed += $check.Name
    }
}

Write-Host ""
if ($failed.Count -gt 0) {
    Write-Host "=== 스모크 테스트 실패 ($($failed.Count)건) ===" -ForegroundColor Red
    foreach ($name in $failed) {
        Write-Host "  - $name" -ForegroundColor Red
    }
    # 종료코드를 남긴다 — Jenkins가 이 값으로 롤백 여부를 정한다.
    exit 1
}

Write-Host "=== 스모크 테스트 통과 ($($DirectChecks.Count + $RoutedChecks.Count)건) ===" -ForegroundColor Green
exit 0
