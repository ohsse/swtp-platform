"""알고리즘 모델 프로세스 동작 상태 — SFR-016의 세 신호.

step-16에서 고객사 요구 "알고리즘 모델 프로세스 동작 상태 정보 제공"의 구현 한계를
**세 신호**로 못박았다. 모델 정확도·드리프트·재학습 상태는 범위 밖이다.

    1. 프로세스 생존      — 이 모듈이 응답한다는 것 자체 + 주기 하트비트 로그
    2. 마지막 추론 시각    — last_inference_at
    3. 추론 에러율        — 누적 / 최근 창

**여기는 계약 골격이다.** 추론 엔드포인트가 생기면 성공/실패마다 record()를 부르면 되고,
집계 방식과 노출 경로는 바꾸지 않는다.

ELK로 나가는 경로는 별도 수집기가 아니라 **자기 로그**다 — ai-service에는 actuator가
없어서 Metricbeat가 긁을 OpenMetrics 엔드포인트가 없고, compose profile로 선택 배포되는
서비스를 Metricbeat 정적 타깃에 넣으면 미배포 정수장에서 30초마다 접속 실패가 쌓인다.
자기가 로그로 뱉으면 Filebeat가 이미 그 컨테이너를 수집하고 있으므로 추가 배선이 0이다.
"""

from __future__ import annotations

import collections
import datetime as _dt
import threading

#: 에러율 산정 창 크기. 누적 비율만 보면 기동 직후의 실패가 영원히 희석되지 않아
#: "지금 정상인가"를 판단할 수 없다. 그래서 누적과 최근 창을 함께 낸다.
_WINDOW = 100


class InferenceStatus:
    """추론 성공/실패를 집계한다. 프로세스 생명주기 동안만 유지되는 인메모리 상태다."""

    def __init__(self, window: int = _WINDOW) -> None:
        self._lock = threading.Lock()
        self._total = 0
        self._errors = 0
        self._last_at: _dt.datetime | None = None
        self._recent: collections.deque = collections.deque(maxlen=window)

    def record(self, ok: bool) -> None:
        """추론 1건의 결과를 기록한다. 추론 엔드포인트가 생기면 여기를 부른다."""
        with self._lock:
            self._total += 1
            if not ok:
                self._errors += 1
            self._last_at = _dt.datetime.now(_dt.timezone.utc)
            self._recent.append(ok)

    def snapshot(self) -> dict:
        """현재 상태를 ECS 로그와 /health가 함께 쓰는 평면 dict로 낸다.

        키 이름을 점 표기(inference.*)로 두는 이유: ECS 로그에 extra로 그대로 실으면
        Elasticsearch가 중첩 필드로 색인해 Kibana에서 inference.error_rate로 잡힌다.
        """
        with self._lock:
            recent_total = len(self._recent)
            recent_errors = recent_total - sum(1 for ok in self._recent if ok)
            return {
                "inference.total": self._total,
                "inference.errors": self._errors,
                "inference.error_rate": round(self._errors / self._total, 4) if self._total else 0.0,
                "inference.recent_error_rate": (
                    round(recent_errors / recent_total, 4) if recent_total else 0.0
                ),
                # 한 번도 추론하지 않았으면 None이다 — 0이나 기동 시각으로 채우지 않는다.
                # "아직 안 돌았다"와 "옛날에 돌고 멈췄다"는 운영상 완전히 다른 상태다.
                "inference.last_at": self._last_at.isoformat().replace("+00:00", "Z")
                if self._last_at
                else None,
            }


#: 프로세스 전역 인스턴스 — 추론 엔드포인트와 하트비트가 같은 것을 본다.
status = InferenceStatus()
