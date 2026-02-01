# Python 3.10 이상 권장
FROM python:3.10-slim

WORKDIR /app

# 시스템 의존성 설치 (Docling 및 PDF 처리에 필요한 라이브러리)
RUN apt-get update && apt-get install -y \
    build-essential \
    libgl1-mesa-glx \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# FastAPI 실행 (0.0.0.0으로 바인딩해야 외부 접속 가능)
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]