# AI Service 컨테이너 이미지
FROM python:3.13-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 관측 모듈은 main.py와 같은 디렉토리에 둔다 — 패키지로 묶지 않는 이유는
# uvicorn이 `main:app`으로 모듈을 직접 import하기 때문이다(패키지화하면 경로가 바뀐다).
COPY main.py logging_ecs.py inference_status.py ./

# 포트 8000 — Phase 5 compose에서 내부 네트워크로 연결 (autonomous-service가 호출)
EXPOSE 8000
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
