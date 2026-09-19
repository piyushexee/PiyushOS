"""LLM client - NVIDIA NIM (OpenAI-compatible API) + offline mock mode."""
import json

from openai import OpenAI

from .config import settings

_client: OpenAI | None = None


def _get_client() -> OpenAI:
    global _client
    if _client is None:
        _client = OpenAI(
            api_key=settings.llm_api_key or "not-set",
            base_url=settings.llm_base_url,
            timeout=180.0,
            default_headers={"User-Agent": "piyushos-agent/1.0"},
        )
    return _client


class LLMResult:
    def __init__(self, content: str, tool_calls: list):
        self.content = content or ""
        self.tool_calls = tool_calls or []


def llm_chat(messages: list, tools: list | None = None) -> LLMResult:
    """Ek LLM turn (tool-calling ke saath). Returns LLMResult(content, tool_calls)."""
    if settings.llm_mock:
        return _mock_chat(messages)

    kwargs: dict = {"model": settings.llm_model, "messages": messages}
    if tools:
        kwargs["tools"] = tools
        kwargs["tool_choice"] = "auto"

    resp = _get_client().chat.completions.create(**kwargs)
    msg = resp.choices[0].message
    tool_calls = []
    for tc in msg.tool_calls or []:
        try:
            args = json.loads(tc.function.arguments or "{}")
        except json.JSONDecodeError:
            args = {"raw": tc.function.arguments}
        tool_calls.append({"id": tc.id, "name": tc.function.name, "arguments": args})
    return LLMResult(msg.content, tool_calls)


def llm_raw(messages: list) -> str:
    """Simple text-in/text-out LLM call (GUI sub-loop ke liye)."""
    if settings.llm_mock:
        return _mock_gui_step(messages)
    resp = _get_client().chat.completions.create(
        model=settings.llm_model, messages=messages)
    return resp.choices[0].message.content or ""


# ----------------------------------------------------------------------
# MOCK MODE - bina API key ke pura pipeline test karne ke liye
# ----------------------------------------------------------------------
_MOCK_OUTLINE = json.dumps([
    {"title": "Artificial Intelligence", "subtitle": "Aaj ka sabse bada game-changer"},
    {"title": "AI kya hai?", "bullets": [
        "Machine jo seekhti hai aur khud decide karti hai",
        "ML, Deep Learning, NLP - teen bade pillars",
        "Har industry me badlaav la raha hai"]},
    {"title": "AI kahan-kahan hai?", "bullets": [
        "Phone me - keyboard, camera, voice assistant",
        "Healthcare - bimarī ki early detection",
        "Business - automation aur predictions"]},
    {"title": "Aage kya?", "bullets": [
        "Agentic AI - khud kaam karne wale systems",
        "Har ghar me personal AI butler",
        "Responsible AI - safety par dhyan"]},
])

_MOCK_SHEET = json.dumps({
    "title": "Mahinayana Budget",
    "sheets": [{
        "name": "Budget",
        "headers": ["Category", "Planned (₹)", "Actual (₹)", "Bachi (₹)"],
        "rows": [
            ["Ghar kiraya", 15000, 14200, 800],
            ["Dana-pena", 8000, 9300, -1300],
            ["Netflix + YouTube", 999, 999, 0],
            ["Savings", 10000, 5000, 5000],
        ],
    }],
})


def _mock_chat(messages: list) -> LLMResult:
    last = messages[-1] if messages else {}

    # Agar tool chal chuka hai to final reply do
    if last.get("role") == "tool":
        c = (last.get("content") or "").lower()
        if "gui kaam poora" in c:
            return LLMResult("Ho gaya bhai! ✅ Phone par kaam ho gaya. Aage kuch ho to bolo!", [])
        if "gui kaam nahi ho paya" in c:
            return LLMResult("Haa bhai, yeh baar kaam nahi ho paya 😅 Ek baar dobara bol do ya thoda aur detail me bata do kya karna hai.", [])
        if "app" in c and "khol di" in c:
            return LLMResult("App khol di hai ✅ Ab usme kya karna hai? Bol do.", [])
        if "pptx" in c:
            return LLMResult("PPT ban gayi bhai! ✅ Phone ke Downloads > PiyushOS folder me bhej di hai. Kuch edit karna ho to bolo (e.g. 'slide 2 ka title badlo').", [])
        if "xlsx" in c:
            return LLMResult("Excel sheet ban gayi! ✅ Phone par bhej di hai. Cell badalna ho to bolo (e.g. 'B3 me 5000 daalo').", [])
        if "glb" in c:
            return LLMResult("3D model ban gaya! ✅ GLB file (dekhne ke liye) aur STL file (3D printing ke liye) dono phone par bhej diye.", [])
        if "clipboard" in c:
            return LLMResult("Text bhej diya phone par, saath me clipboard par bhi copy kar diya ✅ Ab LinkedIn khol ke paste kar do.", [])
        return LLMResult("Ho gaya! ✅ Aur kuch karna ho to bol do.", [])

    text = ""
    for m in reversed(messages):
        if m.get("role") == "user":
            c = m.get("content")
            if isinstance(c, str):
                text = c.lower()
            break

    # App ke ANDAR kaam (message bhejna, search, post, etc.)
    if any(w in text for w in ("msg", "message", "bhejo", "bhej do", "send",
                               "like karo", "comment", "post karo", "search karo")):
        app = "whatsapp"
        for cand in ("whatsapp", "instagram", "youtube", "gmail", "maps"):
            if cand in text:
                app = cand
                break
        return LLMResult("", [{"id": "mock-6", "name": "gui_task",
                               "arguments": {"app": app, "task": text}}])
    if "ppt" in text or "presentation" in text or "slide" in text:
        return LLMResult("", [{"id": "mock-1", "name": "create_presentation",
                               "arguments": {"topic": "Artificial Intelligence", "outline_json": _MOCK_OUTLINE}}])
    if "excel" in text or "sheet" in text or "budget" in text:
        return LLMResult("", [{"id": "mock-2", "name": "create_spreadsheet",
                               "arguments": {"spec_json": _MOCK_SHEET}}])
    if "3d" in text or "3 d" in text or "model" in text:
        return LLMResult("", [{"id": "mock-3", "name": "create_3d_model",
                               "arguments": {"name": "rocket", "shape": "rocket", "color": "red"}}])
    if "linkedin" in text or "resume" in text or "profile" in text:
        content = ("Piyush Sharma\n"
                   "\n"
                   "Aspiring AI engineer from Sitapur, UP. Passionate about building agentic AI systems - "
                   "currently working on PiyushOS, a personal AI butler for Android that creates PPTs, "
                   "spreadsheets and 3D models from simple voice commands.\n"
                   "\n"
                   "Open to internships and projects in AI/ML, automation and mobile development.")
        return LLMResult("", [{"id": "mock-4", "name": "share_text",
                               "arguments": {"title": "LinkedIn Profile", "content": content}}])
    if "kholo" in text or "open" in text:
        app = "instagram"
        for cand in ("instagram", "whatsapp", "youtube", "gmail", "chrome",
                     "maps", "camera", "settings", "google"):
            if cand in text:
                app = cand
                break
        return LLMResult("", [{"id": "mock-5", "name": "open_app",
                               "arguments": {"app": app}}])
    return LLMResult("Bhai, main abhi MOCK mode me hu (LLM_MOCK=1). Real dimaag lagane ke liye .env me "
                     "apni NVIDIA NIM API key daalo aur LLM_MOCK=false karo. Is mode me main ye test kar "
                     "sakti hu: PPT/Excel/3D banana, LinkedIn likhna, app kholna, app ke andar kaam (gui_task). 🚀", [])


# ---------- GUI sub-loop ka mock ----------
_MOCK_GUI_SEQ = [
    '{"action": "tap", "target": 1}',
    '{"action": "type", "text": "Rahul"}',
    '{"action": "tap", "target": 2}',
    '{"action": "type", "text": "kal milte hain"}',
    '{"action": "tap", "target": 3}',
    '{"action": "done", "result": "Message bhej diya (mock mode)"}',
]


def _mock_gui_step(messages: list) -> str:
    sys_msg = (messages[0].get("content") or "") if messages else ""
    if "planner" in sys_msg.lower():
        return ("1. App kholo aur wait karo\n2. Search box par tap karo\n"
                "3. 'Rahul' type karo\n4. Rahul ka chat select karo\n"
                "5. Message type karo\n6. Send button dabao")
    step = sum(1 for m in messages if m.get("role") == "assistant")
    return _MOCK_GUI_SEQ[min(step, len(_MOCK_GUI_SEQ) - 1)]
