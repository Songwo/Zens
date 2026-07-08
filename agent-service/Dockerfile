FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

WORKDIR /app

COPY requirements.txt /app/requirements.txt
ARG PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple
RUN pip install --no-cache-dir -i "$PIP_INDEX_URL" -r /app/requirements.txt

COPY app /app/app

EXPOSE 7810

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "7810"]
