"""ECS(Elastic Common Schema) JSON 로그 — Java 앱 11종과 필드 이름을 맞추기 위한 계약.

ai-service는 Gradle 모듈이 아니라 swtp-observability-starter를 쓸 수 없다.
Java 쪽은 Boot 4 내장 StructuredLogEncoder가 ECS JSON을 외부 라이브러리 0개로 만들어 주지만
Python 쪽에는 그에 해당하는 것이 없다 — 그래서 같은 모양의 출력을 여기서 직접 만든다.

**여기 필드명이 Java 쪽과 어긋나면 Kibana에서 서비스 간 상관관계가 AI 구간에서 끊긴다.**
필드를 바꿀 때는 반드시 Java 출력과 대조한다 (docs/scaffold/steps/step-17-elk-observability.md).

Java 쪽 실제 출력 (gateway 기준):
    {"@timestamp":"...","log":{"level":"WARN","logger":"..."},
     "process":{"pid":1,"thread":{"name":"main"}},
     "service":{"name":"gateway","version":"1.0.0","node":{"name":"GANGNEUNG-01"}},
     "message":"...","ecs":{"version":"8.11"},"requestId":"..."}
"""

from __future__ import annotations

import contextvars
import datetime as _dt
import json
import logging
import os
import sys

#: Java 쪽 Boot 4가 찍는 값과 같아야 한다. 다르면 같은 인덱스에 스키마 버전이 섞인다.
ECS_VERSION = "8.11"

#: Java 서비스의 spring.application.name과 같은 축의 값 (gateway / master-service / ...).
#: compose의 container_name(swtp-ai-service)이 아니라 서비스 이름을 쓴다.
SERVICE_NAME = "ai-service"

#: 요청 단위 상관관계 ID. Java 쪽 SwtpHeaders.REQUEST_ID_MDC_KEY와 같은 이름으로 나간다.
#: 서블릿의 MDC(스레드 로컬)에 대응하는 것이 asyncio에서는 ContextVar다 —
#: 스레드가 아니라 코루틴 단위로 값이 따라다녀야 하기 때문이다.
request_id_var: contextvars.ContextVar = contextvars.ContextVar("swtp_request_id", default=None)

#: Java(Logback)와 Python의 레벨 이름이 두 개 다르다. 맞추지 않으면 Kibana에서
#: `log.level: WARN` 필터가 AI 서비스 로그만 놓친다 — 조용히 빠지는 종류의 어긋남이다.
_LEVEL_ALIAS = {
    "WARNING": "WARN",     # Python WARNING == Logback WARN
    "CRITICAL": "ERROR",   # Logback에는 FATAL이 없다
}

#: logging.LogRecord의 표준 속성 — 이 목록에 없는 것이 사용자가 extra로 넣은 필드다.
_RESERVED = frozenset(vars(logging.LogRecord("", 0, "", 0, "", None, None)).keys()) | {
    "message", "asctime", "taskName",
}

_PLAIN_FORMAT = "%(asctime)s %(levelname)-5s [%(name)s] %(message)s"


def _iso_utc(epoch_seconds: float) -> str:
    """ECS @timestamp — UTC, 밀리초 3자리, Z 접미.

    나노초까지 찍지 않는 이유: Beats의 타임스탬프 파서가 기대하는 레이아웃이
    밀리초 3자리다. 정밀도를 더 주면 파싱에 실패해 수집 시각으로 대체되고,
    그러면 로그의 실제 발생 순서가 뒤섞인다.
    """
    return (
        _dt.datetime.fromtimestamp(epoch_seconds, _dt.timezone.utc)
        .strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3]
        + "Z"
    )


class EcsJsonFormatter(logging.Formatter):
    """LogRecord 하나를 ECS JSON 한 줄로 만든다."""

    def __init__(self, service_name: str, service_version: str, site_code: str = "") -> None:
        super().__init__()
        self._service = {"name": service_name, "version": service_version}
        # 값이 비면 필드를 아예 넣지 않는다 — 빈 문자열을 색인하면 Kibana에서
        # "값이 있는데 빈 정수장"처럼 보여 미설정 상태를 구분할 수 없다.
        if site_code:
            self._service["node"] = {"name": site_code}

    def format(self, record: logging.LogRecord) -> str:
        doc = {
            "@timestamp": _iso_utc(record.created),
            "log": {
                "level": _LEVEL_ALIAS.get(record.levelname, record.levelname),
                "logger": record.name,
            },
            "process": {
                "pid": record.process,
                "thread": {"name": record.threadName},
            },
            "service": self._service,
            "message": record.getMessage(),
            "ecs": {"version": ECS_VERSION},
        }

        request_id = request_id_var.get()
        if request_id:
            doc["requestId"] = request_id

        if record.exc_info:
            exc_type, exc_value, _ = record.exc_info
            doc["error"] = {
                "type": getattr(exc_type, "__name__", str(exc_type)),
                "message": str(exc_value),
                "stack_trace": self.formatException(record.exc_info),
            }

        # logger.info("...", extra={"http.status": 200}) 형태로 붙인 필드.
        # Java 쪽 SLF4J fluent addKeyValue()에 대응한다 — 게이트웨이 접근 로그가 쓰는 방식이다.
        for key, value in record.__dict__.items():
            if key not in _RESERVED and not key.startswith("_"):
                doc[key] = value

        return json.dumps(doc, ensure_ascii=False, default=str)


def setup_logging() -> str:
    """SWTP_LOG_FORMAT에 따라 루트 로거를 구성한다. 선택한 포맷을 돌려준다.

    Java 앱과 같은 두 환경변수가 결정한다 — plain(기본) | ecs.
    """
    log_format = os.getenv("SWTP_LOG_FORMAT", "plain").strip().lower()
    site_code = os.getenv("SWTP_SITE_CODE", "").strip()
    service_version = os.getenv("SWTP_SERVICE_VERSION", "0.1.0").strip()
    level = os.getenv("SWTP_LOG_LEVEL", "INFO").strip().upper()

    handler = logging.StreamHandler(sys.stdout)
    if log_format == "ecs":
        handler.setFormatter(EcsJsonFormatter(SERVICE_NAME, service_version, site_code))
    else:
        handler.setFormatter(logging.Formatter(_PLAIN_FORMAT))

    root = logging.getLogger()
    root.handlers.clear()
    root.addHandler(handler)
    root.setLevel(level)

    # uvicorn은 자기 로거 3개에 직접 핸들러를 달고 propagate=False로 둔다.
    # 떼어내지 않으면 접근 로그만 uvicorn 기본 평문으로 나가 한 컨테이너의 stdout에
    # ECS JSON과 평문이 섞이고, 수집기가 절반을 파싱하지 못한다.
    for name in ("uvicorn", "uvicorn.error", "uvicorn.access"):
        logger = logging.getLogger(name)
        logger.handlers.clear()
        logger.propagate = True

    # uvicorn의 접근 로그는 끈다. ASGI 앱 *바깥*의 프로토콜 계층에서 찍히기 때문에
    # 미들웨어가 세운 request_id_var 범위 밖이고, 그래서 requestId가 실리지 않는다
    # (서블릿에서 필터 바깥의 MDC가 비어 있는 것과 같은 구조다).
    # 대신 main.py의 미들웨어가 같은 정보를 게이트웨이와 같은 필드명으로 남긴다.
    logging.getLogger("uvicorn.access").disabled = True

    return log_format
