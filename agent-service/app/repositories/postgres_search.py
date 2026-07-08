from __future__ import annotations

from typing import List, Sequence

from psycopg.rows import dict_row
from psycopg_pool import ConnectionPool

from app.config import Settings
from app.repositories.base import CommentHit, PostHit


class PostgresSearchRepository:
    backend_name = "postgres"

    def __init__(self, settings: Settings) -> None:
        self._pool = ConnectionPool(
            conninfo=settings.postgres_dsn,
            min_size=settings.postgres_min_pool_size,
            max_size=settings.postgres_max_pool_size,
            kwargs={
                "autocommit": True,
                "row_factory": dict_row,
                "connect_timeout": settings.postgres_connect_timeout_seconds,
            },
        )

    def open(self) -> None:
        self._pool.open(wait=True)

    def close(self) -> None:
        self._pool.close()

    def ping(self) -> bool:
        with self._pool.connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT 1")
                row = cur.fetchone()
                return bool(row)

    def search_posts(self, question: str, section_id: int | None, limit: int) -> List[PostHit]:
        sql = """
        WITH query_input AS (
            SELECT
                %(question)s::text AS raw_query,
                websearch_to_tsquery('simple', %(question)s::text) AS ts_query
        )
        SELECT
            p.id AS post_id,
            p.title,
            COALESCE(p.summary, '') AS summary,
            LEFT(COALESCE(p.content, ''), 480) AS content_snippet,
            s.name AS section_name,
            COALESCE(p.tags, '') AS tags,
            (
                ts_rank_cd(
                    to_tsvector(
                        'simple',
                        COALESCE(p.title, '') || ' ' ||
                        COALESCE(p.summary, '') || ' ' ||
                        COALESCE(p.tags, '') || ' ' ||
                        COALESCE(p.content, '')
                    ),
                    qi.ts_query
                ) * 0.8
                +
                GREATEST(
                    similarity(COALESCE(p.title, ''), qi.raw_query),
                    similarity(COALESCE(p.summary, ''), qi.raw_query),
                    similarity(COALESCE(p.tags, ''), qi.raw_query)
                ) * 0.2
            ) AS score
        FROM sys_post p
        CROSS JOIN query_input qi
        LEFT JOIN sections s ON s.id = p.section_id
        WHERE p.status = 1
          AND (p.audit_status IS NULL OR p.audit_status = '' OR p.audit_status = 'APPROVED')
          AND (%(section_id)s IS NULL OR p.section_id = %(section_id)s)
          AND (
                to_tsvector(
                    'simple',
                    COALESCE(p.title, '') || ' ' ||
                    COALESCE(p.summary, '') || ' ' ||
                    COALESCE(p.tags, '') || ' ' ||
                    COALESCE(p.content, '')
                ) @@ qi.ts_query
                OR COALESCE(p.title, '') %% qi.raw_query
                OR COALESCE(p.summary, '') %% qi.raw_query
                OR COALESCE(p.tags, '') %% qi.raw_query
          )
        ORDER BY score DESC, p.heat_score DESC, p.last_activity_at DESC
        LIMIT %(limit)s
        """
        with self._pool.connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    sql,
                    {
                        "question": question.strip(),
                        "section_id": section_id,
                        "limit": limit,
                    },
                )
                rows = cur.fetchall() or []
        hits: List[PostHit] = []
        for row in rows:
            hits.append(
                PostHit(
                    post_id=str(row["post_id"]),
                    title=str(row["title"] or ""),
                    summary=str(row["summary"] or ""),
                    content_snippet=str(row["content_snippet"] or ""),
                    section_name=row["section_name"],
                    tags=[item.strip() for item in str(row["tags"] or "").split(",") if item.strip()],
                    score=float(row["score"] or 0.0),
                    url=f"/t/{row['post_id']}",
                )
            )
        return hits

    def fetch_top_comments(self, post_ids: Sequence[str], per_post: int) -> List[CommentHit]:
        if not post_ids or per_post <= 0:
            return []
        sql = """
        WITH ranked_comments AS (
            SELECT
                c.id AS comment_id,
                c.post_id,
                LEFT(COALESCE(c.content, ''), 240) AS content,
                COALESCE(c.like_count, 0) AS like_count,
                ROW_NUMBER() OVER (
                    PARTITION BY c.post_id
                    ORDER BY COALESCE(c.like_count, 0) DESC, c.create_time ASC
                ) AS rn
            FROM sys_comment c
            WHERE c.audit_status = 'APPROVED'
              AND c.post_id = ANY(%(post_ids)s)
        )
        SELECT comment_id, post_id, content, like_count
        FROM ranked_comments
        WHERE rn <= %(per_post)s
        ORDER BY post_id, rn ASC
        """
        with self._pool.connection() as conn:
            with conn.cursor() as cur:
                cur.execute(sql, {"post_ids": list(post_ids), "per_post": per_post})
                rows = cur.fetchall() or []
        return [
            CommentHit(
                comment_id=str(row["comment_id"]),
                post_id=str(row["post_id"]),
                content=str(row["content"] or ""),
                like_count=int(row["like_count"] or 0),
            )
            for row in rows
        ]
