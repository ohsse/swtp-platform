"""AI Service — FastAPI 골격.

아키텍처 문서 6장: 동일 리포지토리에 두되 Gradle 모듈로 포함하지 않는다.
OpenAPI 문서는 FastAPI 내장 기능으로 /docs(Swagger UI), /openapi.json 에서 제공된다.
Eureka 등록은 플랫폼 원칙(정적 라우팅 + 등록 optional)에 따라 미적용 —
다중 서버 확장 시 py-eureka-client 도입을 검토한다.

관측(step-17): Gradle 모듈이 아니라 swtp-observability-starter를 쓸 수 없으므로
ECS JSON 로그 계약을 logging_ecs.py가 직접 구현한다. Java 앱 11종과 같은
SWTP_LOG_FORMAT / SWTP_SITE_CODE 두 환경변수가 동작을 결정한다.
"""

import asyncio
import contextlib
import logging
import os
import time
import uuid
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request

from inference_status import status
from logging_ecs import request_id_var, setup_logging

#: 서블릿 쪽 SwtpHeaders.REQUEST_ID와 같은 값이어야 상관관계가 이어진다.
REQUEST_ID_HEADER = "X-Request-Id"

#: 하트비트 주기(초). 0 이하면 하트비트를 끈다.
HEARTBEAT_INTERVAL_SEC = int(os.getenv("SWTP_AI_HEARTBEAT_SEC", "60"))

log_format = setup_logging()
logger = logging.getLogger("swtp.ai")

#: 접근 로그 — 게이트웨이의 "swtp.gateway.access"와 같은 축의 이름이다.
#: 로거 이름으로 접근 로그만 따로 거를 수 있어야 Kibana에서 트래픽 조회가 성립한다.
access_log = logging.getLogger("swtp.ai.access")


async def _heartbeat() -> None:
    """SFR-016의 세 신호를 주기적으로 로그에 싣는다.

    ai-service에는 actuator가 없어 Metricbeat가 긁을 엔드포인트가 없다.
    자기 로그로 뱉으면 Filebeat가 이미 이 컨테이너를 수집하고 있으므로 배선이 0이다.
    """
    while True:
        await asyncio.sleep(HEARTBEAT_INTERVAL_SEC)
        logger.info("ai-service alive", extra=status.snapshot())


@asynccontextmanager
async def lifespan(_: FastAPI):
    logger.info("ai-service 기동", extra={"log.format": log_format})
    task = asyncio.create_task(_heartbeat()) if HEARTBEAT_INTERVAL_SEC > 0 else None
    try:
        yield
    finally:
        if task:
            task.cancel()
            # 종료 시 CancelledError가 남으면 컨테이너가 비정상 종료로 기록된다
            with contextlib.suppress(asyncio.CancelledError):
                await task
        logger.info("ai-service 종료")


app = FastAPI(
    title="swtp-ai-service",
    description="스마트정수장 AI 서비스 — 예측/판단 모델 서빙",
    version="0.1.0",
    lifespan=lifespan,
)


@app.middleware("http")
async def request_id_middleware(request: Request, call_next):
    """Java 쪽 RequestIdMdcFilter와 같은 규약 — 헤더가 있으면 전파, 없으면 자체 발급.

    게이트웨이를 거치지 않는 서비스 간 직접 호출(autonomous-service → ai-service)에도
    ID가 붙어야 "ID 없는 로그"가 생기지 않는다.
    """
    request_id = request.headers.get(REQUEST_ID_HEADER)
    if not request_id or not request_id.strip():
        request_id = str(uuid.uuid4())

    started_at = time.perf_counter()
    token = request_id_var.set(request_id)
    # 예외로 빠져나가도 접근 로그는 남아야 한다 — 그때 상태코드는 None이다.
    status_code = None
    try:
        response = await call_next(request)
        status_code = response.status_code
    finally:
        elapsed_ms = int((time.perf_counter() - started_at) * 1000)
        # 필드명은 게이트웨이 AccessLogGlobalFilter와 같아야 한다 —
        # 다르면 Kibana에서 한 쿼리로 전 구간 트래픽을 볼 수 없다.
        access_log.info(
            "%s %s -> %s (%sms)",
            request.method,
            request.url.path,
            status_code,
            elapsed_ms,
            extra={
                "http.method": request.method,
                "http.path": request.url.path,
                "http.status": status_code,
                "duration.ms": elapsed_ms,
            },
        )
        # ContextVar는 코루틴 단위지만 이벤트 루프가 컨텍스트를 재사용할 수 있어 명시 복원한다
        request_id_var.reset(token)
    response.headers[REQUEST_ID_HEADER] = request_id
    return response


@app.get("/health")
def health() -> dict:
    """Spring Actuator와 동일한 형태의 헬스 응답 (compose healthcheck 대상).

    SFR-016의 세 신호를 함께 싣는다 — 하트비트 로그를 기다리지 않고
    지금 상태를 즉시 확인하는 경로다.
    """
    return {"status": "UP", **status.snapshot()}
