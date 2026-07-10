FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV AGENT_SERVICE_HOST=0.0.0.0
ENV AGENT_SERVICE_PORT=7810

WORKDIR /app

COPY requirements.txt /app/requirements.txt
ARG PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple
RUN pip install --no-cache-dir -i "$PIP_INDEX_URL" -r /app/requirements.txt

COPY app /app/app

EXPOSE 7810

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD python -c "import os, urllib.request; urllib.request.urlopen('http://127.0.0.1:' + os.getenv('AGENT_SERVICE_PORT', '7810') + '/ready', timeout=3).read()"

CMD ["sh", "-c", "exec uvicorn app.main:app --host \"${AGENT_SERVICE_HOST}\" --port \"${AGENT_SERVICE_PORT}\""]
