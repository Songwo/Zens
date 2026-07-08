from __future__ import annotations

from collections import defaultdict
from html import unescape
import re
from typing import List, Sequence

import pymysql
from pymysql.cursors import DictCursor

from app.config import Settings
from app.repositories.base import CommentHit, PostHit

_QUESTION_STOP_TERMS = {
    "怎么",
    "如何",
    "请问",
    "一下",
    "一下子",
    "这个",
    "那个",
    "什么",
    "为啥",
    "为什么",
    "是否",
    "有没有",
    "问题",
}


class MysqlSearchRepository:
    backend_name = "mysql"

    def __init__(self, settings: Settings) -> None:
        self._connection_options = settings.mysql_connection_options

    def open(self) -> None:
        return None

    def close(self) -> None:
        return None

    def ping(self) -> bool:
        with self._connect() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT 1")
                row = cur.fetchone()
                return bool(row)

    def search_posts(self, question: str, section_id: int | None, limit: int) -> List[PostHit]:
        try:
            rows = self._search_posts_fulltext(question, section_id, limit)
            if rows:
                return rows
        except pymysql.MySQLError as exc:
            if not self._is_missing_fulltext_index(exc):
                raise
        return self._search_posts_like(question, section_id, limit)

    def _search_posts_fulltext(self, question: str, section_id: int | None, limit: int) -> List[PostHit]:
        normalized_question = question.strip()
        sql = """
        SELECT
            p.id AS post_id,
            p.title,
            COALESCE(p.summary, '') AS summary,
            LEFT(COALESCE(p.content, ''), 480) AS content_snippet,
            s.name AS section_name,
            COALESCE(p.tags, '') AS tags,
            (
                COALESCE(MATCH(p.title, p.content, p.summary, p.tags) AGAINST (%s IN NATURAL LANGUAGE MODE), 0) * 10
                + CASE WHEN p.title LIKE CONCAT('%%', %s, '%%') THEN 4 ELSE 0 END
                + CASE WHEN COALESCE(p.summary, '') LIKE CONCAT('%%', %s, '%%') THEN 2 ELSE 0 END
                + CASE WHEN COALESCE(p.tags, '') LIKE CONCAT('%%', %s, '%%') THEN 1.5 ELSE 0 END
                + CASE WHEN COALESCE(p.content, '') LIKE CONCAT('%%', %s, '%%') THEN 1 ELSE 0 END
            ) AS score
        FROM sys_post p
        LEFT JOIN sections s ON s.id = p.section_id
        WHERE p.status = 1
          AND (p.audit_status IS NULL OR p.audit_status = '' OR p.audit_status = 'APPROVED')
          AND (%s IS NULL OR p.section_id = %s)
          AND (
                MATCH(p.title, p.content, p.summary, p.tags) AGAINST (%s IN NATURAL LANGUAGE MODE)
                OR p.title LIKE CONCAT('%%', %s, '%%')
                OR COALESCE(p.summary, '') LIKE CONCAT('%%', %s, '%%')
                OR COALESCE(p.tags, '') LIKE CONCAT('%%', %s, '%%')
                OR COALESCE(p.content, '') LIKE CONCAT('%%', %s, '%%')
          )
        ORDER BY score DESC, COALESCE(p.heat_score, 0) DESC, COALESCE(p.last_activity_at, p.create_time) DESC
        LIMIT %s
        """
        params = [
            normalized_question,
            normalized_question,
            normalized_question,
            normalized_question,
            normalized_question,
            section_id,
            section_id,
            normalized_question,
            normalized_question,
            normalized_question,
            normalized_question,
            normalized_question,
            limit,
        ]
        with self._connect() as conn:
            with conn.cursor() as cur:
                cur.execute(sql, params)
                rows = cur.fetchall() or []
        return self._rows_to_post_hits(rows)

    def _search_posts_like(self, question: str, section_id: int | None, limit: int) -> List[PostHit]:
        normalized_question = question.strip()
        terms = self._build_search_terms(normalized_question)
        if not terms:
            return []

        score_parts: list[str] = []
        where_parts: list[str] = []
        score_params: list[object] = []
        where_params: list[object] = []

        exact_pattern = f"%{normalized_question}%"
        score_parts.extend([
            "CASE WHEN p.title LIKE %s THEN 6 ELSE 0 END",
            "CASE WHEN COALESCE(p.summary, '') LIKE %s THEN 3 ELSE 0 END",
            "CASE WHEN COALESCE(p.tags, '') LIKE %s THEN 2 ELSE 0 END",
            "CASE WHEN COALESCE(p.content, '') LIKE %s THEN 1.5 ELSE 0 END",
        ])
        score_params.extend([exact_pattern, exact_pattern, exact_pattern, exact_pattern])

        for term in terms:
            pattern = f"%{term}%"
            score_parts.extend([
                "CASE WHEN p.title LIKE %s THEN 4 ELSE 0 END",
                "CASE WHEN COALESCE(p.summary, '') LIKE %s THEN 2 ELSE 0 END",
                "CASE WHEN COALESCE(p.tags, '') LIKE %s THEN 1.5 ELSE 0 END",
                "CASE WHEN COALESCE(p.content, '') LIKE %s THEN 1 ELSE 0 END",
            ])
            score_params.extend([pattern, pattern, pattern, pattern])
            where_parts.append(
                "("
                "p.title LIKE %s "
                "OR COALESCE(p.summary, '') LIKE %s "
                "OR COALESCE(p.tags, '') LIKE %s "
                "OR COALESCE(p.content, '') LIKE %s"
                ")"
            )
            where_params.extend([pattern, pattern, pattern, pattern])

        score_expr = "\n                + ".join(score_parts)
        where_expr = "\n                OR ".join(where_parts)
        sql = """
        SELECT
            p.id AS post_id,
            p.title,
            COALESCE(p.summary, '') AS summary,
            LEFT(COALESCE(p.content, ''), 480) AS content_snippet,
            s.name AS section_name,
            COALESCE(p.tags, '') AS tags,
            (
                {score_expr}
            ) AS score
        FROM sys_post p
        LEFT JOIN sections s ON s.id = p.section_id
        WHERE p.status = 1
          AND (p.audit_status IS NULL OR p.audit_status = '' OR p.audit_status = 'APPROVED')
          AND (%s IS NULL OR p.section_id = %s)
          AND (
                {where_expr}
          )
        ORDER BY score DESC, COALESCE(p.heat_score, 0) DESC, COALESCE(p.last_activity_at, p.create_time) DESC
        LIMIT %s
        """.format(score_expr=score_expr, where_expr=where_expr)
        params = [*score_params, section_id, section_id, *where_params, limit]
        with self._connect() as conn:
            with conn.cursor() as cur:
                cur.execute(sql, params)
                rows = cur.fetchall() or []
        return self._rows_to_post_hits(rows)

    def _build_search_terms(self, question: str) -> list[str]:
        normalized = " ".join(question.strip().split())
        if not normalized:
            return []

        compact = re.sub(r"[\s\W_]+", "", normalized, flags=re.UNICODE)
        terms: list[str] = []

        if compact and compact != normalized and 2 <= len(compact) <= 12:
            terms.append(compact)

        for token in re.findall(r"[A-Za-z0-9_+#.-]{2,}", normalized.lower()):
            terms.append(token)

        chinese_sequences = re.findall(r"[\u4e00-\u9fff]{2,}", normalized)
        if not chinese_sequences:
            chinese_sequences = re.findall(r"[\u4e00-\u9fff]{2,}", compact)
        for sequence in chinese_sequences:
            if sequence not in _QUESTION_STOP_TERMS and len(sequence) <= 8:
                terms.append(sequence)
            if len(sequence) > 4:
                for size in (2, 3, 4):
                    for index in range(len(sequence) - size + 1):
                        token = sequence[index:index + size]
                        if token in _QUESTION_STOP_TERMS:
                            continue
                        terms.append(token)

        deduped: list[str] = []
        seen: set[str] = set()
        for term in terms:
            clean = term.strip()
            if len(clean) < 2 or clean in seen:
                continue
            seen.add(clean)
            deduped.append(clean)
            if len(deduped) >= 8:
                break
        return deduped

    def _is_missing_fulltext_index(self, exc: pymysql.MySQLError) -> bool:
        return bool(exc.args and exc.args[0] == 1191)

    def _rows_to_post_hits(self, rows: list[dict]) -> List[PostHit]:
        return [
            PostHit(
                post_id=str(row["post_id"]),
                title=unescape(str(row["title"] or "")),
                summary=unescape(str(row["summary"] or "")),
                content_snippet=unescape(str(row["content_snippet"] or "")),
                section_name=row["section_name"],
                tags=[item.strip() for item in str(row["tags"] or "").split(",") if item.strip()],
                score=float(row["score"] or 0.0),
                url=f"/t/{row['post_id']}",
            )
            for row in rows
        ]

    def fetch_top_comments(self, post_ids: Sequence[str], per_post: int) -> List[CommentHit]:
        if not post_ids or per_post <= 0:
            return []

        placeholders = ", ".join(["%s"] * len(post_ids))
        sql = f"""
        SELECT
            c.id AS comment_id,
            c.post_id,
            LEFT(COALESCE(c.content, ''), 240) AS content,
            COALESCE(c.like_count, 0) AS like_count
        FROM sys_comment c
        WHERE c.audit_status = 'APPROVED'
          AND c.post_id IN ({placeholders})
        ORDER BY c.post_id ASC, COALESCE(c.like_count, 0) DESC, c.create_time ASC
        """
        with self._connect() as conn:
            with conn.cursor() as cur:
                cur.execute(sql, list(post_ids))
                rows = cur.fetchall() or []

        grouped: dict[str, list[CommentHit]] = defaultdict(list)
        for row in rows:
            post_id = str(row["post_id"])
            if len(grouped[post_id]) >= per_post:
                continue
            grouped[post_id].append(
                CommentHit(
                    comment_id=str(row["comment_id"]),
                    post_id=post_id,
                    content=unescape(str(row["content"] or "")),
                    like_count=int(row["like_count"] or 0),
                )
            )

        hits: List[CommentHit] = []
        for post_id in post_ids:
            hits.extend(grouped.get(str(post_id), []))
        return hits

    def _connect(self) -> pymysql.Connection:
        return pymysql.connect(cursorclass=DictCursor, **self._connection_options)
