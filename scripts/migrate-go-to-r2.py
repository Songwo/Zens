#!/usr/bin/env python3
"""
Migrate legacy go-media-service files and metadata into Cloudflare R2 + MySQL.

Required Python packages:
  pip install boto3 pymysql

Example:
  python scripts/migrate-go-to-r2.py \
    --sqlite ./go-media-service/data/media.db \
    --uploads-root ./go-media-service/uploads \
    --r2-endpoint https://<ACCOUNT_ID>.r2.cloudflarestorage.com \
    --r2-access-key-id xxx \
    --r2-secret-access-key xxx \
    --r2-bucket campus-pulse-media \
    --mysql-host 127.0.0.1 \
    --mysql-port 3306 \
    --mysql-user root \
    --mysql-password 123456 \
    --mysql-db campus_pulse \
    --public-base-url https://media.allinsong.top \
    --report ./migrate-report.json
"""

from __future__ import annotations

import argparse
import concurrent.futures
import json
import os
import sqlite3
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import boto3
import botocore.exceptions
import pymysql


@dataclass
class LegacyMediaRow:
    id: str
    storage_path: str
    media_type: str
    mime_type: str | None
    size_bytes: int
    sha256: str | None
    original_name: str | None
    biz_type: str | None
    biz_id: str | None
    uploader_id: str | None
    created_at: str | None


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Migrate go-media-service files to Cloudflare R2")
    parser.add_argument("--sqlite", required=True, help="Path to legacy SQLite media.db")
    parser.add_argument("--uploads-root", required=True, help="Path to legacy uploads root directory")
    parser.add_argument("--r2-endpoint", required=True, help="R2 endpoint, e.g. https://<ACCOUNT_ID>.r2.cloudflarestorage.com")
    parser.add_argument("--r2-access-key-id", required=True, help="R2 access key id")
    parser.add_argument("--r2-secret-access-key", required=True, help="R2 secret access key")
    parser.add_argument("--r2-bucket", required=True, help="R2 bucket name")
    parser.add_argument("--mysql-host", required=True, help="MySQL host")
    parser.add_argument("--mysql-port", type=int, default=3306, help="MySQL port")
    parser.add_argument("--mysql-user", required=True, help="MySQL user")
    parser.add_argument("--mysql-password", required=True, help="MySQL password")
    parser.add_argument("--mysql-db", required=True, help="MySQL database name")
    parser.add_argument("--public-base-url", default="https://media.allinsong.top", help="Public media base URL")
    parser.add_argument("--concurrency", type=int, default=8, help="Concurrent upload workers")
    parser.add_argument("--report", help="Optional JSON report path")
    parser.add_argument("--dry-run", action="store_true", help="Do not upload or write MySQL")
    return parser.parse_args()


def fetch_legacy_rows(sqlite_path: Path) -> list[LegacyMediaRow]:
    conn = sqlite3.connect(str(sqlite_path))
    conn.row_factory = sqlite3.Row
    try:
        rows = conn.execute(
            """
            SELECT id,
                   storage_path,
                   media_type,
                   mime_type,
                   size_bytes,
                   sha256,
                   original_name,
                   biz_type,
                   biz_id,
                   uploader_id,
                   created_at
            FROM media_files
            WHERE deleted_at IS NULL
            ORDER BY created_at ASC
            """
        ).fetchall()
    finally:
        conn.close()

    result: list[LegacyMediaRow] = []
    for row in rows:
        result.append(
            LegacyMediaRow(
                id=str(row["id"]),
                storage_path=str(row["storage_path"]).lstrip("/"),
                media_type=str(row["media_type"] or "image"),
                mime_type=row["mime_type"],
                size_bytes=int(row["size_bytes"] or 0),
                sha256=row["sha256"],
                original_name=row["original_name"],
                biz_type=row["biz_type"],
                biz_id=row["biz_id"],
                uploader_id=row["uploader_id"],
                created_at=row["created_at"],
            )
        )
    return result


def build_s3_client(args: argparse.Namespace):
    session = boto3.session.Session()
    return session.client(
        "s3",
        endpoint_url=args.r2_endpoint,
        aws_access_key_id=args.r2_access_key_id,
        aws_secret_access_key=args.r2_secret_access_key,
        region_name="auto",
    )


def object_exists_with_size(s3_client, bucket: str, key: str, expected_size: int) -> bool:
    try:
        resp = s3_client.head_object(Bucket=bucket, Key=key)
        return int(resp.get("ContentLength") or 0) == expected_size
    except botocore.exceptions.ClientError as exc:
        code = str(exc.response.get("Error", {}).get("Code", ""))
        if code in {"404", "NoSuchKey", "NotFound"}:
            return False
        raise


def upload_one(args: argparse.Namespace, s3_client, uploads_root: Path, row: LegacyMediaRow) -> dict[str, Any]:
    key = row.storage_path
    local_path = uploads_root / Path(key)
    if not local_path.exists():
        return {"ok": False, "id": row.id, "key": key, "reason": f"missing file: {local_path}"}
    if row.size_bytes <= 0:
        return {"ok": False, "id": row.id, "key": key, "reason": "invalid size_bytes"}

    if args.dry_run:
        return {"ok": True, "id": row.id, "key": key, "skipped": True, "reason": "dry-run"}

    try:
        if not object_exists_with_size(s3_client, args.r2_bucket, key, row.size_bytes):
            with local_path.open("rb") as fp:
                extra_args = {}
                if row.mime_type:
                    extra_args["ContentType"] = row.mime_type
                s3_client.upload_fileobj(fp, args.r2_bucket, key, ExtraArgs=extra_args or None)

        if not object_exists_with_size(s3_client, args.r2_bucket, key, row.size_bytes):
            return {"ok": False, "id": row.id, "key": key, "reason": "head size mismatch after upload"}
        return {"ok": True, "id": row.id, "key": key, "skipped": False}
    except Exception as exc:  # noqa: BLE001
        return {"ok": False, "id": row.id, "key": key, "reason": str(exc)}


def build_upsert_rows(rows: list[LegacyMediaRow], public_base_url: str) -> list[tuple[Any, ...]]:
    base = public_base_url.rstrip("/")
    values: list[tuple[Any, ...]] = []
    for row in rows:
        values.append(
            (
                row.id,
                row.storage_path,
                row.original_name,
                row.mime_type,
                row.media_type,
                row.size_bytes,
                row.sha256,
                None,
                None,
                None,
                f"{base}/{row.storage_path}",
                None,
                row.uploader_id,
                row.biz_type,
                row.biz_id,
                1,
                row.created_at,
                row.created_at,
            )
        )
    return values


def upsert_mysql(args: argparse.Namespace, rows: list[LegacyMediaRow]) -> None:
    if not rows or args.dry_run:
        return

    conn = pymysql.connect(
        host=args.mysql_host,
        port=args.mysql_port,
        user=args.mysql_user,
        password=args.mysql_password,
        database=args.mysql_db,
        charset="utf8mb4",
        autocommit=False,
    )
    sql = """
        INSERT INTO sys_media_file (
            id, file_key, original_name, mime_type, media_type, size_bytes, sha256,
            width, height, duration_seconds, access_url, cover_url, uploader_id,
            biz_type, biz_id, status, create_time, update_time
        ) VALUES (
            %s, %s, %s, %s, %s, %s, %s,
            %s, %s, %s, %s, %s, %s,
            %s, %s, %s, %s, %s
        )
        ON DUPLICATE KEY UPDATE
            original_name = VALUES(original_name),
            mime_type = VALUES(mime_type),
            media_type = VALUES(media_type),
            size_bytes = VALUES(size_bytes),
            sha256 = VALUES(sha256),
            access_url = VALUES(access_url),
            uploader_id = VALUES(uploader_id),
            biz_type = VALUES(biz_type),
            biz_id = VALUES(biz_id),
            status = VALUES(status),
            update_time = VALUES(update_time)
    """
    payload = build_upsert_rows(rows, args.public_base_url)
    try:
        with conn.cursor() as cursor:
            cursor.executemany(sql, payload)
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def main() -> int:
    args = parse_args()
    sqlite_path = Path(args.sqlite).resolve()
    uploads_root = Path(args.uploads_root).resolve()

    if not sqlite_path.exists():
        print(f"[ERROR] SQLite file not found: {sqlite_path}", file=sys.stderr)
        return 1
    if not uploads_root.exists():
        print(f"[ERROR] Uploads root not found: {uploads_root}", file=sys.stderr)
        return 1

    rows = fetch_legacy_rows(sqlite_path)
    print(f"[INFO] loaded {len(rows)} legacy rows from {sqlite_path}")

    s3_client = build_s3_client(args)
    successes: list[LegacyMediaRow] = []
    failures: list[dict[str, Any]] = []

    row_map = {row.id: row for row in rows}
    with concurrent.futures.ThreadPoolExecutor(max_workers=max(1, args.concurrency)) as executor:
        futures = [executor.submit(upload_one, args, s3_client, uploads_root, row) for row in rows]
        for future in concurrent.futures.as_completed(futures):
            result = future.result()
            if result["ok"]:
                successes.append(row_map[result["id"]])
                status = "skipped" if result.get("skipped") else "uploaded"
                print(f"[OK] {status}: {result['key']}")
            else:
                failures.append(result)
                print(f"[FAIL] {result['key']}: {result['reason']}", file=sys.stderr)

    try:
        upsert_mysql(args, successes)
        if not args.dry_run:
            print(f"[INFO] upserted {len(successes)} rows into sys_media_file")
    except Exception as exc:  # noqa: BLE001
        print(f"[ERROR] MySQL upsert failed: {exc}", file=sys.stderr)
        return 1

    report = {
        "total": len(rows),
        "success": len(successes),
        "failed": len(failures),
        "failures": failures,
        "dry_run": args.dry_run,
    }
    if args.report:
        report_path = Path(args.report).resolve()
        report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"[INFO] report written to {report_path}")

    if failures:
        print(f"[WARN] finished with {len(failures)} failures", file=sys.stderr)
        return 2

    print("[INFO] migration completed successfully")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
