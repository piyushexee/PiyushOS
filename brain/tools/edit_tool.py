"""Editing tools - PPTX aur XLSX me customization (user bole to file edit ho jati hai)."""
import json
import time
from pathlib import Path

from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.util import Inches, Pt
from openpyxl import load_workbook
from openpyxl.styles import Font

from ..config import settings
from ..registry import LAST_FILES
from .pptx_tool import add_content_slide, DARK


def _safe_name(s: str, limit: int = 40) -> str:
    s = "".join(ch for ch in (s or "") if ch.isalnum() or ch in " -_").strip()
    return s.replace(" ", "_")[:limit] or "file"


def _resolve(path_hint: str, kind: str) -> Path:
    p = (path_hint or "auto").strip()
    if p in ("", "auto"):
        last = LAST_FILES.get(kind)
        if not last or not Path(last).exists():
            raise ValueError(f"Koi {kind} file nahi mili - pehle banvao ya file ka naam batao")
        return Path(last)
    pth = Path(p)
    if not pth.is_absolute():
        pth = settings.out_dir / p
    if not pth.exists():
        raise ValueError(f"File nahi mili: {pth}")
    return pth


def _find_title_shape(slide):
    for shp in slide.shapes:
        if shp.has_text_frame and shp.top is not None and shp.top < Inches(1.5):
            return shp
    return None


def _find_body_shape(slide):
    best = None
    for shp in slide.shapes:
        if shp.has_text_frame and shp.top is not None and shp.top >= Inches(1.5):
            if best is None or (shp.height or 0) > (best.height or 0):
                best = shp
    return best


def _find_title_slide_shapes(slide):
    """Slide 1: (title, subtitle) - top se 2in se neeche wale text boxes."""
    boxes = []
    for shp in slide.shapes:
        if shp.has_text_frame and shp.text_frame.text.strip() and (shp.top or 0) >= Inches(1.5):
            boxes.append(shp)
    boxes.sort(key=lambda s: s.top or 0)
    title = boxes[0] if boxes else None
    subtitle = boxes[1] if len(boxes) > 1 else None
    return title, subtitle


def _set_single_text(shape, text: str):
    tf = shape.text_frame
    for p in list(tf.paragraphs[1:]):
        p._p.getparent().remove(p._p)
    p0 = tf.paragraphs[0]
    if p0.runs:
        p0.runs[0].text = text
        for r in list(p0.runs[1:]):
            r._r.getparent().remove(r._r)
    else:
        p0.text = text


def _set_bullets(shape, bullets: list):
    tf = shape.text_frame
    for p in list(tf.paragraphs[1:]):
        p._p.getparent().remove(p._p)
    p0 = tf.paragraphs[0]
    for r in list(p0.runs):
        r._r.getparent().remove(r._r)
    for i, b in enumerate(bullets):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.space_after = Pt(14)
        r = p.add_run()
        r.text = f"▪  {b}"
        r.font.size = Pt(22)
        r.font.color.rgb = DARK


def _move_slide_to_end_position(prs, from_idx: int, to_idx: int):
    sldIdLst = prs.slides._sldIdLst
    slides = list(sldIdLst)
    el = slides[from_idx]
    sldIdLst.remove(el)
    slides = list(sldIdLst)
    if to_idx >= len(slides):
        sldIdLst.append(el)
    else:
        slides[to_idx].addprevious(el)


def edit_presentation(spec_json: str) -> dict:
    try:
        spec = json.loads(spec_json) if isinstance(spec_json, str) else spec_json
        edits = spec.get("edits", [])
        if not edits:
            raise ValueError("'edits' list khaali hai")
    except Exception as e:
        raise ValueError(f"spec_json valid nahi: {e}")

    src = _resolve(spec.get("file", "auto"), "pptx")
    prs = Presentation(str(src))
    blank = prs.slide_layouts[6]
    done = []

    for e in edits[:12]:
        op = e.get("op")
        try:
            if op == "new_title":
                s = prs.slides[0]
                t, sub = _find_title_slide_shapes(s)
                if t is not None and e.get("text"):
                    _set_single_text(t, e["text"])
                if sub is not None and e.get("subtitle"):
                    _set_single_text(sub, e["subtitle"])
                done.append("title slide")
            elif op in ("set_title", "set_bullets"):
                idx = max(0, int(e.get("slide", 2)) - 1)
                if idx >= len(prs.slides._sldIdLst):
                    raise ValueError(f"Slide {idx + 1} nahi hai")
                s = prs.slides[idx]
                if op == "set_title":
                    shp = _find_title_shape(s)
                    if shp is None:
                        raise ValueError("Title shape nahi mila")
                    _set_single_text(shp, str(e.get("text", "")))
                else:
                    shp = _find_body_shape(s)
                    if shp is None:
                        raise ValueError("Bullets box nahi mila")
                    _set_bullets(shp, [str(b) for b in e.get("bullets", [])])
                done.append(f"slide {idx + 1}")
            elif op == "add_slide":
                title = str(e.get("title", ""))
                bullets = [str(b) for b in e.get("bullets", [])]
                before = len(prs.slides._sldIdLst)
                add_content_slide(prs, blank, title, bullets)
                after = int(e.get("after", before))  # insert position (1-based)
                _move_slide_to_end_position(prs, len(prs.slides._sldIdLst) - 1, after)
                done.append(f"new slide '{title}'")
            elif op == "delete_slide":
                idx = max(0, int(e.get("slide", 1)) - 1)
                slides = list(prs.slides._sldIdLst)
                if idx >= len(slides):
                    raise ValueError(f"Slide {idx + 1} nahi hai")
                prs.slides._sldIdLst.remove(slides[idx])
                done.append(f"slide {idx + 1} delete")
            else:
                raise ValueError(f"Unknown op: {op}")
        except Exception as ex:
            raise ValueError(f"Edit '{op}' me galti: {ex}")

    stem = _safe_name(src.stem, 40)
    stamp = time.strftime("%Y%m%d_%H%M%S")
    out = settings.out_dir / f"{stem}_edited_{stamp}.pptx"
    prs.save(str(out))
    LAST_FILES["pptx"] = str(out)
    return {
        "path": str(out),
        "name": out.name,
        "files": [str(out)],
        "edits": done,
        "summary": f"PPT edit ho gayi: {', '.join(done)} → {out.name}",
    }


def edit_spreadsheet(spec_json: str) -> dict:
    try:
        spec = json.loads(spec_json) if isinstance(spec_json, str) else spec_json
        edits = spec.get("edits", [])
        if not edits:
            raise ValueError("'edits' list khaali hai")
    except Exception as e:
        raise ValueError(f"spec_json valid nahi: {e}")

    src = _resolve(spec.get("file", "auto"), "xlsx")
    wb = load_workbook(str(src))
    done = []

    for e in edits[:50]:
        op = e.get("op")
        sheet = e.get("sheet") or wb.sheetnames[0]
        if sheet not in wb.sheetnames:
            raise ValueError(f"Sheet '{sheet}' nahi mili: {wb.sheetnames}")
        ws = wb[sheet]
        try:
            if op == "set_cell":
                ws[str(e.get("cell", ""))] = e.get("value")
                done.append(f"{sheet}!{e.get('cell')}")
            elif op == "set_header":
                ws[str(e.get("cell", ""))] = e.get("value")
                ws[str(e.get("cell", ""))].font = Font(bold=True)
                done.append(f"{sheet}!{e.get('cell')} (header)")
            elif op == "add_row":
                ws.append(list(e.get("row", [])))
                done.append(f"{sheet} naya row")
            elif op == "delete_row":
                ws.delete_rows(int(e.get("row", 2)))
                done.append(f"{sheet} row {e.get('row')} delete")
            else:
                raise ValueError(f"Unknown op: {op}")
        except Exception as ex:
            raise ValueError(f"Edit '{op}' me galti: {ex}")

    stem = _safe_name(src.stem, 40)
    stamp = time.strftime("%Y%m%d_%H%M%S")
    out = settings.out_dir / f"{stem}_edited_{stamp}.xlsx"
    wb.save(str(out))
    LAST_FILES["xlsx"] = str(out)
    return {
        "path": str(out),
        "name": out.name,
        "files": [str(out)],
        "edits": done,
        "summary": f"Excel edit ho gaya: {len(done)} changes → {out.name}",
    }
