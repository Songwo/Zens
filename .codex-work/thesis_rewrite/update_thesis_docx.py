# -*- coding: utf-8 -*-
from __future__ import annotations

import shutil
import sys
import tempfile
import zipfile
from pathlib import Path
from typing import Dict, List, Tuple
import xml.etree.ElementTree as ET


WORK_DIR = Path(__file__).resolve().parent
REPO_ROOT = WORK_DIR.parents[1]
REPLACEMENTS_FILE = WORK_DIR / "replacements.tsv"
REGISTER_SNIPPET_FILE = WORK_DIR / "appendix_register.txt"
LOGIN_SNIPPET_FILE = WORK_DIR / "appendix_login.txt"
SOURCE_PARAGRAPH_REPORT = REPO_ROOT / "tmp_thesis7" / "paragraphs.txt"

INPUT_DOCX = Path(r"D:\2026毕业设计\DaiMa\campus-pulse(back)\campus-pulse\docs\赵青松论文7.3.docx")
OUTPUT_DOCX = Path(r"D:\2026毕业设计\DaiMa\campus-pulse(back)\campus-pulse\docs\赵青松论文7.3_项目修订版.docx")
PARAGRAPH_REPORT = WORK_DIR / "updated_paragraphs.txt"
CHANGE_REPORT = WORK_DIR / "changed_paragraphs.txt"


EXPECTED_REPORT_PREFIXES = {
    38: "随着开源生态、在线学习与技术分享平台的快速发展",
    86: "随着开源生态、在线学习与技术分享平台的快速发展",
    305: "普通用户是 Campus Pulse 的主要使用主体",
    423: "王嘉伟.",
}

XML_SPACE = "{http://www.w3.org/XML/1998/namespace}space"


def load_replacements() -> Dict[int, str]:
    replacements: Dict[int, str] = {}
    for raw in REPLACEMENTS_FILE.read_text(encoding="utf-8").splitlines():
        if not raw.strip():
            continue
        index_str, text = raw.split("\t", 1)
        index = int(index_str.strip())
        replacements[index] = "" if text == "__BLANK__" else text
    return replacements


def load_snippet_lines(path: Path) -> List[str]:
    return path.read_text(encoding="utf-8").splitlines()


def load_source_paragraphs() -> Dict[int, str]:
    if not SOURCE_PARAGRAPH_REPORT.exists():
        raise FileNotFoundError(f"Source paragraph report not found: {SOURCE_PARAGRAPH_REPORT}")

    paragraphs: Dict[int, str] = {}
    for raw in SOURCE_PARAGRAPH_REPORT.read_text(encoding="utf-8").splitlines():
        if not raw.strip():
            continue
        parts = raw.split("\t", 2)
        if len(parts) < 3:
            continue
        idx_str, _, text = parts
        paragraphs[int(idx_str)] = text

    for idx, expected_prefix in EXPECTED_REPORT_PREFIXES.items():
        actual = paragraphs.get(idx, "")
        if not actual.startswith(expected_prefix):
            raise ValueError(
                f"Paragraph report mismatch at logical index {idx}: "
                f"expected prefix {expected_prefix!r}, got {actual[:40]!r}"
            )

    return paragraphs


def load_namespaces(xml_path: Path) -> Dict[str, str]:
    namespaces: Dict[str, str] = {}
    for _, ns in ET.iterparse(xml_path, events=("start-ns",)):
        prefix, uri = ns
        if prefix not in namespaces:
            namespaces[prefix] = uri
    for prefix, uri in namespaces.items():
        ET.register_namespace(prefix, uri)
    return namespaces


def paragraph_text(paragraph: ET.Element, t_tag: str) -> str:
    return "".join(node.text or "" for node in paragraph.iter(t_tag))


def normalize_text(text: str) -> str:
    return "".join(text.split())


def set_paragraph_text(paragraph: ET.Element, t_tag: str, text: str) -> None:
    text_nodes = list(paragraph.iter(t_tag))
    if not text_nodes:
        return
    first = text_nodes[0]
    first.text = text
    if text.startswith(" ") or text.endswith(" ") or "  " in text:
        first.set(XML_SPACE, "preserve")
    elif XML_SPACE in first.attrib:
        del first.attrib[XML_SPACE]
    for node in text_nodes[1:]:
        node.text = ""


def build_logical_to_actual_map(
    source_paragraphs: Dict[int, str], paragraphs: List[ET.Element], t_tag: str
) -> Dict[int, int]:
    actual_texts = [normalize_text(paragraph_text(paragraph, t_tag)) for paragraph in paragraphs]
    mapping: Dict[int, int] = {}
    cursor = 0

    for logical_idx in sorted(source_paragraphs):
        target = normalize_text(source_paragraphs[logical_idx])
        if not target:
            continue
        while cursor < len(actual_texts) and actual_texts[cursor] != target:
            cursor += 1
        if cursor >= len(actual_texts):
            raise ValueError(
                f"Unable to map logical paragraph {logical_idx}: "
                f"{source_paragraphs[logical_idx][:60]!r}"
            )
        mapping[logical_idx] = cursor
        cursor += 1

    return mapping


def apply_range(paragraphs: List[ET.Element], t_tag: str, start_idx: int, end_idx: int, lines: List[str]) -> List[Tuple[int, str, str]]:
    changes: List[Tuple[int, str, str]] = []
    for offset, idx in enumerate(range(start_idx, end_idx + 1)):
        before = paragraph_text(paragraphs[idx], t_tag)
        after = lines[offset] if offset < len(lines) else ""
        set_paragraph_text(paragraphs[idx], t_tag, after)
        changes.append((idx, before, after))
    return changes


def export_paragraphs(paragraphs: List[ET.Element], t_tag: str, output_path: Path) -> None:
    lines = []
    for idx, paragraph in enumerate(paragraphs):
        lines.append(f"{idx}\t{paragraph_text(paragraph, t_tag)}")
    output_path.write_text("\n".join(lines), encoding="utf-8")


def find_paragraph_index(paragraphs: List[ET.Element], t_tag: str, expected_text: str) -> int:
    for idx, paragraph in enumerate(paragraphs):
        if paragraph_text(paragraph, t_tag) == expected_text:
            return idx
    raise ValueError(f"Paragraph not found: {expected_text!r}")


def main() -> int:
    if not INPUT_DOCX.exists():
        print(f"Input DOCX not found: {INPUT_DOCX}", file=sys.stderr)
        return 1

    replacements = load_replacements()
    register_lines = load_snippet_lines(REGISTER_SNIPPET_FILE)
    login_lines = load_snippet_lines(LOGIN_SNIPPET_FILE)
    source_paragraphs = load_source_paragraphs()

    with tempfile.TemporaryDirectory(prefix="thesis-docx-") as temp_dir:
        temp_dir_path = Path(temp_dir)
        unzip_dir = temp_dir_path / "unzipped"
        unzip_dir.mkdir(parents=True, exist_ok=True)

        with zipfile.ZipFile(INPUT_DOCX, "r") as zf:
            zf.extractall(unzip_dir)

        document_xml = unzip_dir / "word" / "document.xml"
        namespaces = load_namespaces(document_xml)
        w_uri = namespaces["w"]
        p_tag = f"{{{w_uri}}}p"
        t_tag = f"{{{w_uri}}}t"

        tree = ET.parse(document_xml)
        root = tree.getroot()
        paragraphs = list(root.iter(p_tag))
        logical_to_actual = build_logical_to_actual_map(source_paragraphs, paragraphs, t_tag)

        changed: List[Tuple[int, str, str]] = []
        for logical_idx, new_text in sorted(replacements.items()):
            actual_idx = logical_to_actual[logical_idx]
            before = paragraph_text(paragraphs[actual_idx], t_tag)
            set_paragraph_text(paragraphs[actual_idx], t_tag, new_text)
            changed.append((actual_idx, before, new_text))

        appendix_title_idx = find_paragraph_index(paragraphs, t_tag, "附录 部分源代码")
        register_heading_idx = find_paragraph_index(paragraphs, t_tag, "1．用户注册代码")
        login_heading_idx = find_paragraph_index(paragraphs, t_tag, "2．用户登录代码")

        if not (appendix_title_idx < register_heading_idx < login_heading_idx):
            raise ValueError("Appendix heading order mismatch.")

        register_start = register_heading_idx + 1
        register_end = login_heading_idx - 1
        login_start = login_heading_idx + 1
        login_end = len(paragraphs) - 1

        changed.extend(apply_range(paragraphs, t_tag, register_start, register_end, register_lines))
        changed.extend(apply_range(paragraphs, t_tag, login_start, login_end, login_lines))

        tree.write(document_xml, encoding="utf-8", xml_declaration=True)

        if OUTPUT_DOCX.exists():
            OUTPUT_DOCX.unlink()

        with zipfile.ZipFile(OUTPUT_DOCX, "w", zipfile.ZIP_DEFLATED) as out_zip:
            for path in sorted(unzip_dir.rglob("*")):
                if path.is_file():
                    out_zip.write(path, path.relative_to(unzip_dir))

        export_paragraphs(paragraphs, t_tag, PARAGRAPH_REPORT)

        report_lines = []
        for idx, before, after in changed:
            report_lines.append(f"[{idx}] BEFORE: {before}")
            report_lines.append(f"[{idx}] AFTER : {after}")
            report_lines.append("")
        CHANGE_REPORT.write_text("\n".join(report_lines), encoding="utf-8")

        main_text = "".join(
            paragraph_text(paragraphs[logical_to_actual[idx]], t_tag).replace(" ", "")
            for idx in range(84, 417)
            if idx in logical_to_actual
        )
        main_char_count = len("".join(ch for ch in main_text if not ch.isspace()))

        print(f"Output DOCX: {OUTPUT_DOCX}")
        print(f"Paragraph count: {len(paragraphs)}")
        print(f"Main chapter char count (84-416): {main_char_count}")
        print(f"Paragraph report: {PARAGRAPH_REPORT}")
        print(f"Change report: {CHANGE_REPORT}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
