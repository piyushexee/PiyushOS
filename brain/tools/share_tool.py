"""Text share karo - phone par file + clipboard par copy (LinkedIn/resume/email ke liye)."""
import time

from ..config import settings


def _safe_name(s: str, limit: int = 40) -> str:
    s = "".join(ch for ch in (s or "") if ch.isalnum() or ch in " -_").strip()
    return s.replace(" ", "_")[:limit] or "text"


async def share_text(ctx, title: str, content: str) -> dict:
    if not (content or "").strip():
        raise ValueError("content khaali hai")
    stamp = time.strftime("%Y%m%d_%H%M%S")
    path = settings.out_dir / f"{_safe_name(title)}_{stamp}.txt"
    path.write_text(content, encoding="utf-8")

    # NOTE: file bhejna aur clipboard karna agent loop karta hai (result["files"] /
    # result["clipboard"] ke through) - yahan duplicate bhejne se bachne ke liye
    # kuch nahi bhejte.
    if ctx.bridge.primary is None:
        summary = f"Phone connect nahi hai; text server par save hai: {path}"
    else:
        summary = f"Text '{title}' tayyaar hai - phone par file bhi bhejungi aur clipboard par copy karungi"
    return {
        "path": str(path),
        "name": path.name,
        "files": [str(path)],
        "clipboard": content,
        "summary": summary,
    }
