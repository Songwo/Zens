from __future__ import annotations

import json
import re
import time
from collections import defaultdict
from dataclasses import dataclass
from typing import AsyncIterator, Iterable, List, Sequence

from app.config import Settings
from app.models import AskRequest, AskResponse, AskTrace, Citation, RelatedPost, SearchResponse
from app.repositories.base import CommentHit, PostHit, SearchRepository
from app.services.answering import build_fallback_answer, estimate_confidence
from app.services.llm_client import LlmClient


def _clean_excerpt(text: str) -> str:
    cleaned = " ".join((text or "").replace("\r", " ").replace("\n", " ").split())
    return cleaned[:220]


_MIN_WEAK_RELATED_SCORE = 4.0
_MIN_SINGLE_TERM_CITATION_SCORE = 2.0

_QUERY_STOP_WORDS = {
    "怎么",
    "如何",
    "一下",
    "一个",
    "这个",
    "那个",
    "是否",
    "有没有",
    "什么",
    "为啥",
    "为什么",
    "需要",
    "应该",
    "可以",
    "帮我",
    "给我",
    "继续",
    "展开",
    "一下子",
    "排查",
    "原因",
    "常见",
    "问题",
    "帖子",
    "评论",
    "社区",
}

_QUERY_ALIAS_GROUPS: tuple[tuple[tuple[str, ...], tuple[str, ...]], ...] = (
    (("登录态", "登录", "鉴权", "认证"), ("登录", "登录态", "会话", "session", "token", "cookie", "鉴权", "认证")),
    (("首屏", "性能", "卡顿"), ("首屏", "性能", "加载", "渲染", "优化")),
    (("缓存穿透", "缓存击穿", "缓存雪崩", "redis"), ("缓存", "穿透", "击穿", "雪崩", "redis")),
    (("部署", "上线", "发布"), ("部署", "上线", "发布", "docker", "nginx")),
    (("数据库", "mysql", "索引", "慢查询"), ("数据库", "mysql", "索引", "sql", "慢查询")),
    (("agent", "ai", "openai", "大模型"), ("agent", "ai", "openai", "模型", "问答", "检索")),
)


def _build_sse_event(event: str, payload: dict) -> str:
    return f"event: {event}\ndata: {json.dumps(payload, ensure_ascii=False)}\n\n"


def _model_to_dict(model) -> dict:
    if hasattr(model, "model_dump"):
        return model.model_dump()
    return model.dict()


def _extract_query_terms(text: str, *, limit: int = 10) -> list[str]:
    normalized = (
        str(text or "")
        .lower()
        .replace("\r", " ")
        .replace("\n", " ")
    )
    normalized = re.sub(r"[，。！？、,.!?:：;；()[\]{}【】<>《》\"“”'‘’`~@#$%^&*=+|\\/]+", " ", normalized)
    compact = re.sub(r"\s+", "", normalized)
    raw_terms = re.findall(r"[a-z0-9._#+-]{2,}|[\u4e00-\u9fff]{2,}", normalized)

    terms: list[str] = []
    for item in raw_terms:
        clean = item.strip()
        if not clean:
            continue
        terms.append(clean)
        if re.fullmatch(r"[\u4e00-\u9fff]{4,}", clean):
            for size in (4, 3, 2):
                for index in range(len(clean) - size + 1):
                    terms.append(clean[index:index + size])

    if compact and 2 <= len(compact) <= 12:
        terms.append(compact)

    deduped: list[str] = []
    seen: set[str] = set()
    for term in terms:
        clean = term.strip()
        if (
            len(clean) < 2
            or clean in seen
            or clean in _QUERY_STOP_WORDS
            or clean.isdigit()
        ):
            continue
        seen.add(clean)
        deduped.append(clean)
        if len(deduped) >= limit:
            break
    return deduped


def _expand_query_terms(question: str, base_terms: Sequence[str], *, limit: int = 12) -> list[str]:
    merged: list[str] = []
    seen: set[str] = set()

    def add(term: str) -> None:
        clean = term.strip().lower()
        if (
            len(clean) < 2
            or clean in seen
            or clean in _QUERY_STOP_WORDS
            or clean.isdigit()
        ):
            return
        seen.add(clean)
        merged.append(clean)

    for term in base_terms:
        add(term)

    question_lower = question.lower()
    for triggers, aliases in _QUERY_ALIAS_GROUPS:
        if any(trigger in question_lower for trigger in triggers):
            for alias in aliases:
                add(alias)

    return merged[:limit]


def _build_retrieval_candidates(question: str, retrieval_query: str) -> list[str]:
    texts = [retrieval_query.strip(), question.strip()]
    base_terms = _extract_query_terms(retrieval_query or question, limit=8)
    expanded_terms = _expand_query_terms(question, base_terms, limit=10)

    candidates: list[str] = []
    seen: set[str] = set()

    def add(candidate: str) -> None:
        value = " ".join(candidate.split()).strip()
        if not value:
            return
        key = value.lower()
        if key in seen:
            return
        seen.add(key)
        candidates.append(value[:500])

    for text in texts:
        add(text)
    if base_terms:
        add(" ".join(base_terms))
    if expanded_terms and expanded_terms != list(base_terms):
        add(" ".join(expanded_terms))
    if base_terms and expanded_terms:
        blended = [*base_terms[:4], *[item for item in expanded_terms if item not in base_terms][:4]]
        add(" ".join(blended))
    return candidates


def _contains_term(source: str, term: str) -> bool:
    if not term:
        return False
    if re.fullmatch(r"[a-z0-9._#+-]+", term):
        pattern = rf"(?<![a-z0-9]){re.escape(term)}(?![a-z0-9])"
        return re.search(pattern, source) is not None
    return term in source


def _is_strong_technical_term(term: str) -> bool:
    return bool(re.fullmatch(r"[a-z0-9][a-z0-9._#+-]{1,}", term or ""))


def _match_terms(post: PostHit, terms: Sequence[str]) -> list[str]:
    source = " ".join(
        [
            post.title or "",
            post.summary or "",
            post.content_snippet or "",
            " ".join(post.tags or []),
            post.section_name or "",
        ]
    ).lower()
    matched: list[str] = []
    for term in terms:
        if _contains_term(source, term):
            matched.append(term)
    return matched


@dataclass
class RankedPost:
    post: PostHit
    rank_score: float
    overlap_count: int
    matched_terms: list[str]


@dataclass
class SearchBundle:
    question: str
    retrieval_query: str
    conversation_context: str
    posts: List[PostHit]
    hit_count: int
    citations: List[Citation]
    related_posts: List[RelatedPost]
    retrieval_ms: int
    confidence: str


class CommunityQaService:
    def __init__(
        self,
        settings: Settings,
        repository: SearchRepository,
        llm_client: LlmClient,
    ) -> None:
        self._settings = settings
        self._repository = repository
        self._llm_client = llm_client

    async def ask(self, request: AskRequest) -> AskResponse:
        total_started_at = time.perf_counter()
        bundle = self._retrieve_bundle(request)

        llm_ms = 0
        if self._llm_client.enabled and bundle.citations:
            try:
                answer, llm_ms = await self._llm_client.answer(
                    bundle.question,
                    bundle.citations[:6],
                    bundle.conversation_context,
                )
            except Exception:
                answer = build_fallback_answer(bundle.question, bundle.citations, bundle.related_posts)
                llm_ms = 0
        else:
            answer = build_fallback_answer(bundle.question, bundle.citations, bundle.related_posts)

        total_ms = int((time.perf_counter() - total_started_at) * 1000)
        return AskResponse(
            answer=answer,
            confidence=bundle.confidence,
            citations=bundle.citations,
            related_posts=bundle.related_posts,
            trace=AskTrace(
                backend=self._repository.backend_name,
                retrieval_ms=bundle.retrieval_ms,
                llm_ms=llm_ms,
                total_ms=total_ms,
                hit_count=bundle.hit_count,
            ),
        )

    def search_only(self, request: AskRequest) -> SearchResponse:
        bundle = self._retrieve_bundle(request)
        return SearchResponse(
            citations=bundle.citations,
            related_posts=bundle.related_posts,
            hit_count=bundle.hit_count,
            backend=self._repository.backend_name,
        )

    async def ask_stream(self, request: AskRequest) -> AsyncIterator[str]:
        total_started_at = time.perf_counter()
        try:
            bundle = self._retrieve_bundle(request)
        except Exception as exc:
            yield _build_sse_event("error", {"message": str(exc)})
            return

        yield _build_sse_event(
            "context",
            {
                "question": bundle.question,
                "confidence": bundle.confidence,
                "citations": [_model_to_dict(item) for item in bundle.citations],
                "related_posts": [_model_to_dict(item) for item in bundle.related_posts],
                "trace": {
                    "backend": self._repository.backend_name,
                    "retrieval_ms": bundle.retrieval_ms,
                    "llm_ms": 0,
                    "total_ms": 0,
                    "hit_count": bundle.hit_count,
                },
            },
        )

        answer_parts: list[str] = []
        llm_ms = 0
        used_fallback = False
        if self._llm_client.enabled and bundle.citations:
            llm_started_at = time.perf_counter()
            try:
                async for chunk in self._llm_client.stream_answer(
                    bundle.question,
                    bundle.citations[:6],
                    bundle.conversation_context,
                ):
                    if not chunk:
                        continue
                    answer_parts.append(chunk)
                    yield _build_sse_event("delta", {"delta": chunk})
                llm_ms = int((time.perf_counter() - llm_started_at) * 1000)
            except Exception:
                if not answer_parts:
                    fallback = build_fallback_answer(bundle.question, bundle.citations, bundle.related_posts)
                    answer_parts.append(fallback)
                    yield _build_sse_event("delta", {"delta": fallback})
                    used_fallback = True
                llm_ms = 0
        else:
            fallback = build_fallback_answer(bundle.question, bundle.citations, bundle.related_posts)
            answer_parts.append(fallback)
            yield _build_sse_event("delta", {"delta": fallback})
            used_fallback = True

        answer = "".join(answer_parts).strip()
        if not answer:
            answer = build_fallback_answer(bundle.question, bundle.citations, bundle.related_posts)
            used_fallback = True

        if used_fallback:
            llm_ms = 0

        total_ms = int((time.perf_counter() - total_started_at) * 1000)
        yield _build_sse_event(
            "done",
            {
                "answer": answer,
                "confidence": bundle.confidence,
                "trace": {
                    "backend": self._repository.backend_name,
                    "retrieval_ms": bundle.retrieval_ms,
                    "llm_ms": llm_ms,
                    "total_ms": total_ms,
                    "hit_count": bundle.hit_count,
                },
            },
        )

    def _retrieve_bundle(self, request: AskRequest) -> SearchBundle:
        normalized_question = request.question.strip()
        if len(normalized_question) < self._settings.min_question_length:
            raise ValueError(f"问题至少需要 {self._settings.min_question_length} 个字符")
        retrieval_query = (request.retrieval_query or normalized_question).strip()
        if len(retrieval_query) < self._settings.min_question_length:
            retrieval_query = normalized_question
        conversation_context = (request.conversation_context or "").strip()

        limit = min(request.limit or self._settings.default_search_limit, self._settings.max_search_limit)
        comments_per_post = min(
            request.comments_per_post or self._settings.default_comments_per_post,
            self._settings.max_comments_per_post,
        )

        analysis_terms = _expand_query_terms(
            normalized_question,
            _extract_query_terms(retrieval_query or normalized_question, limit=8),
            limit=10,
        )
        retrieval_candidates = _build_retrieval_candidates(normalized_question, retrieval_query)
        retrieval_started_at = time.perf_counter()
        posts = self._retrieve_ranked_posts(retrieval_candidates, analysis_terms, request.section_id, limit)
        citation_posts = self._select_citation_posts(posts, analysis_terms, limit)
        related_posts = self._build_related_posts(posts[:5])
        comments = self._repository.fetch_top_comments(
            [item.post_id for item in citation_posts],
            comments_per_post if request.include_comments else 0,
        )
        retrieval_ms = int((time.perf_counter() - retrieval_started_at) * 1000)

        citations = self._build_citations(citation_posts, comments)
        hit_count = len({item.post_id for item in citation_posts}) or len({item.post_id for item in related_posts})
        return SearchBundle(
            question=normalized_question,
            retrieval_query=retrieval_query,
            conversation_context=conversation_context,
            posts=posts,
            hit_count=hit_count,
            citations=citations,
            related_posts=related_posts,
            retrieval_ms=retrieval_ms,
            confidence=estimate_confidence(citations),
        )

    def _retrieve_ranked_posts(
        self,
        retrieval_candidates: Sequence[str],
        analysis_terms: Sequence[str],
        section_id: int | None,
        limit: int,
    ) -> List[PostHit]:
        ranked: dict[str, RankedPost] = {}
        fallback_posts: list[PostHit] = []

        for query_index, candidate in enumerate(retrieval_candidates):
            rows = self._repository.search_posts(candidate, section_id, limit)
            if not fallback_posts and rows:
                fallback_posts = rows
            for position, item in enumerate(rows):
                matched_terms = _match_terms(item, analysis_terms)
                overlap_count = len(matched_terms)
                rank_score = (
                    overlap_count * 10
                    + float(item.score)
                    - (query_index * 0.2)
                    - (position * 0.01)
                )
                current = ranked.get(item.post_id)
                if current and current.rank_score >= rank_score:
                    continue
                ranked[item.post_id] = RankedPost(
                    post=item,
                    rank_score=rank_score,
                    overlap_count=overlap_count,
                    matched_terms=matched_terms,
                )

        if not ranked:
            return fallback_posts[:limit]

        ordered = sorted(
            ranked.values(),
            key=lambda item: (item.overlap_count, item.rank_score, item.post.score),
            reverse=True,
        )
        return [item.post for item in ordered[:limit]]

    def _select_citation_posts(self, posts: Sequence[PostHit], analysis_terms: Sequence[str], limit: int) -> List[PostHit]:
        if not posts:
            return []

        required_overlap = 1 if len(analysis_terms) <= 3 else 2
        selected: list[PostHit] = []
        for post in posts:
            matched_terms = _match_terms(post, analysis_terms)
            has_strong_term = any(_is_strong_technical_term(term) for term in matched_terms)
            enough_overlap = len(matched_terms) >= required_overlap
            enough_single_term_signal = (
                has_strong_term
                and len(matched_terms) >= 1
                and float(post.score or 0) >= _MIN_SINGLE_TERM_CITATION_SCORE
            )
            if enough_overlap or enough_single_term_signal:
                selected.append(post)
            if len(selected) >= limit:
                break
        return selected

    def _build_citations(self, posts: List[PostHit], comments: List[CommentHit]) -> List[Citation]:
        comment_map: dict[str, list[CommentHit]] = defaultdict(list)
        for comment in comments:
            comment_map[comment.post_id].append(comment)

        citations: List[Citation] = []
        citation_index = 1
        for post in posts:
            primary_excerpt = _clean_excerpt(post.summary or post.content_snippet)
            if primary_excerpt:
                citations.append(
                    Citation(
                        index=citation_index,
                        post_id=post.post_id,
                        title=post.title,
                        section_name=post.section_name,
                        excerpt=primary_excerpt,
                        source_type="post",
                        url=post.url,
                    )
                )
                citation_index += 1

            for comment in comment_map.get(post.post_id, []):
                excerpt = _clean_excerpt(comment.content)
                if not excerpt:
                    continue
                citations.append(
                    Citation(
                        index=citation_index,
                        post_id=post.post_id,
                        title=post.title,
                        section_name=post.section_name,
                        excerpt=excerpt,
                        source_type="comment",
                        comment_id=comment.comment_id,
                        url=f"{post.url}#comment-{comment.comment_id}",
                    )
                )
                citation_index += 1
        return citations[:8]

    def _build_related_posts(self, posts: List[PostHit]) -> List[RelatedPost]:
        return [
            RelatedPost(
                post_id=item.post_id,
                title=item.title,
                section_name=item.section_name,
                summary=item.summary or item.content_snippet,
                tags=item.tags,
                score=item.score,
                url=item.url,
            )
            for item in posts[:5]
            if float(item.score or 0) >= _MIN_WEAK_RELATED_SCORE
        ]
