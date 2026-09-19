"""gui_task - kisi bhi app ke ANDAR kaam karwana.

Loop: PLAN banao → screen dekho (UI tree + screenshot + OCR) → AI decide kare → action lo → dobara dekho.
Yahi architecture hai jo "Astra" type agents me hota hai.
"""
import asyncio
import json
import re

from ..config import settings
from ..llm import llm_raw
from ..ws.bridge import DeviceError

PLAN_SYSTEM = """You are a phone-task planner. Break the user's task into 3-10 short, concrete steps that a phone GUI operator can follow (one tap/type/decision per step).
Reply with ONLY a numbered list, one step per line, in Hinglish. No intro, no outro."""

GUI_SYSTEM = """You are an expert Android GUI operator. You control a real phone by looking at the screen state. You are given a PLAN and you execute it step by step, verifying after every action.

INPUT each step:
- PLAN: the numbered step list
- TASK: the original task
- SCREEN: JSON array of on-screen elements. Each element:
    i = index, t = text/label, b = [x1,y1,x2,y2] in pixels,
    c = clickable, e = editable, s = scrollable, cl = class, r = id
- Sometimes also a photo of the screen and/or OCR text.

Reply with ONLY one JSON object, no other words:
{"action":"tap","target":<i>}                    - tap element i from SCREEN
{"action":"tap_xy","x":<px>,"y":<px>}            - tap a pixel (only if visible in photo but not in SCREEN)
{"action":"long_press","target":<i>}             - long press element i
{"action":"double_tap","target":<i>}             - double tap element i
{"action":"type","text":"..."}                   - type into the focused text field
{"action":"swipe","direction":"up|down|left|right"}
{"action":"back"}
{"action":"home"}
{"action":"wait"}
{"action":"done","result":"<what was accomplished, in Hinglish>"}
{"action":"fail","reason":"<why it failed, in Hinglish>"}

RULES:
- Only ONE action per step.
- Tap the element whose text best matches the current PLAN step. Never tap unrelated things.
- If you just opened an app and SCREEN is empty, do {"action":"wait"}.
- If the person/item you need is not visible, swipe up and look again (max 3 times), then fail if still not found.
- For typing: first tap the editable field (e=true), then type.
- After EVERY action the next SCREEN tells you the result. If the screen did NOT change as expected, try a different approach (different element, swipe, back, tap_xy from photo).
- When the TASK is verifiably done (message sent, search result visible, item selected, screen shows the result), return done.
- If you tried 2-3 alternatives for the same sub-goal and still cannot proceed, return fail.

APP CHEAT SHEET (known layouts - use as hints, trust the actual SCREEN over these):
- WhatsApp: magnifier/search icon at top. Below = chat list (each row: name + last message, t= shows name). Tap a row to open chat. In a chat: bottom input box is e=true with mic+emoji icons; Send button (paper plane) appears inside that row after text is typed.
- Instagram: top = search bar (e=true). Bottom bar = Home, Search, Reels, Shop, Profile (person icon, far right). Tap a profile row to open it.
- YouTube: magnifier icon top-right; type in search; tap a result.
- Gmail: compose button at bottom-right (+ or pencil). In compose: "To" field top, big body area e=true, Send at top.
- Google Maps: search bar at top (e=true); tap a result; navigation buttons at bottom.
- Chrome: omnibox/address bar at top (e=true).
- PowerPoint (mobile): "+" or "New" to create → pick a template (first one is fine) → tap a slide to edit; tap text placeholder then type; "+ Add slide" / plus icon to add slides.
- Settings: search bar at top; category list below; tap a category.
"""

_JSON_RE = re.compile(r"\{.*\}", re.S)
_VALID_ACTIONS = ("tap", "tap_xy", "type", "swipe", "back", "home", "wait",
                  "done", "fail", "long_press", "double_tap")


def _parse_action(raw: str) -> dict | None:
    m = _JSON_RE.search(raw or "")
    if not m:
        return None
    try:
        obj = json.loads(m.group(0))
    except json.JSONDecodeError:
        return None
    if not isinstance(obj, dict) or obj.get("action") not in _VALID_ACTIONS:
        return None
    return obj


def _el_center(tree_json: str, idx: int) -> tuple[int, int] | None:
    try:
        els = json.loads(tree_json)
    except Exception:
        return None
    for el in els:
        if el.get("i") == idx:
            b = el.get("b")
            if b and len(b) == 4:
                return ((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)
    return None


async def _make_plan(task: str) -> str:
    """Pehle task ka plan banate hain (success rate badhane ke liye)."""
    try:
        raw = await asyncio.to_thread(
            llm_raw,
            [
                {"role": "system", "content": PLAN_SYSTEM},
                {"role": "user", "content": f"TASK: {task}"},
            ],
        )
        plan = (raw or "").strip()
        return plan if plan else ""
    except Exception:
        return ""


async def gui_task(ctx, app: str, task: str, max_steps: int = 10) -> dict:
    if not (task or "").strip():
        raise ValueError("task chahiye - kya karna hai detail me likho")
    max_steps = max(3, min(20, int(max_steps or 12)))

    # 1) app kholo (agar bola hai)
    if app and app.strip():
        data = await ctx.bridge.cmd("open_app", {"app": app.strip()})
        if not data.get("ok", True):
            raise DeviceError(data.get("error", f"App '{app}' nahi khul payi"))
        await asyncio.sleep(2.5)

    # 2) plan banao
    plan = await _make_plan(task)
    await ctx.bridge.progress(f"📋 Plan: {(plan.splitlines() or ['kaam shuru'])[0][:80]}")

    messages = [
        {"role": "system", "content": GUI_SYSTEM},
        {"role": "user", "content": f"TASK: {task}\nPLAN:\n{plan or '(directly execute)'}\nShuru karo - pehle screen dekho."},
    ]

    for step in range(max_steps):
        n = step + 1

        # 3) screen lo (tree + screenshot)
        screen = await ctx.bridge.cmd("screen", timeout=35)
        if not screen.get("ok", True):
            raise DeviceError(screen.get("error", "Screen ka status nahi mil paya"))
        tree = str(screen.get("tree", "[]"))
        img = screen.get("image_b64")
        w, h = screen.get("width"), screen.get("height")
        ocr = screen.get("ocr")

        prompt = (f"TASK: {task}\nPLAN:\n{plan or '(none)'}\n"
                  f"STEP {n}/{max_steps}\nScreen size: {w or '?'}x{h or '?'} px\n"
                  f"SCREEN: {tree[:6000]}")
        if ocr:
            prompt += f"\nOCR (screen ka text): {ocr[:2000]}"

        msg: dict
        if img and settings.vision_enabled:
            msg = {"role": "user", "content": [
                {"type": "text", "text": prompt},
                {"type": "image_url", "image_url": {"url": f"data:image/png;base64,{img}"}},
            ]}
        else:
            msg = {"role": "user", "content": prompt}
        messages.append(msg)

        # 4) AI decide kare
        try:
            raw = await asyncio.to_thread(llm_raw, messages)
        except Exception as e:
            raise DeviceError(f"GUI AI call fail: {e}")
        action = _parse_action(raw)
        messages.append({"role": "assistant",
                         "content": json.dumps(action, ensure_ascii=False) if action else raw})
        if action is None:
            messages.append({"role": "user",
                             "content": "Invalid reply. Reply with ONLY the JSON action object."})
            continue

        a = action.get("action")

        # 5) done/fail?
        if a == "done":
            return {"summary": f"GUI kaam poora ({n} steps): {action.get('result', '')}",
                    "result": action.get("result", ""), "steps": n}
        if a == "fail":
            return {"summary": f"GUI kaam nahi ho paya: {action.get('reason', '')}",
                    "error": action.get("reason", ""), "steps": n}

        # 6) action execute karo
        try:
            await ctx.bridge.progress(f"📱 Step {n}: {a}")
            if a in ("tap", "long_press", "double_tap") and "target" in action:
                center = _el_center(tree, action["target"])
                if center is None:
                    raise DeviceError(f"Element {action['target']} screen me nahi mila")
                await ctx.bridge.cmd(a, {"x": center[0], "y": center[1]}, timeout=10)
            elif a == "tap_xy":
                await ctx.bridge.cmd("tap", {"x": int(action["x"]), "y": int(action["y"])}, timeout=10)
            elif a == "type":
                data = await ctx.bridge.cmd("input_text", {"text": str(action.get("text", ""))}, timeout=10)
                if not data.get("ok", True):
                    raise DeviceError(data.get("error", "Type nahi ho paya"))
            elif a == "swipe":
                await ctx.bridge.cmd("swipe", {"direction": str(action.get("direction", "up"))}, timeout=10)
            elif a == "back":
                await ctx.bridge.cmd("back", timeout=10)
            elif a == "home":
                await ctx.bridge.cmd("home", timeout=10)
            elif a == "wait":
                await asyncio.sleep(2.5)
        except DeviceError as e:
            messages.append({"role": "user",
                             "content": f"Action fail hua: {e}. Screen waise hi hai. Ab kya karna hai?"})
            continue

        await asyncio.sleep(1.5)

    return {"summary": f"GUI task {max_steps} steps me poora nahi hua",
            "error": "steps khatam ho gaye", "steps": max_steps}
