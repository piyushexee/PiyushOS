"""PiyushOS Agent - LLM ko tools ke saath loop me chalata hai (socho -> kaam karo -> dekho -> aage badho)."""
import json
from pathlib import Path

from .config import settings
from .llm import llm_chat
from .tools import Ctx, TOOL_SPECS, execute
from .ws.bridge import DeviceError

SYSTEM_PROMPT = """Tu PiyushOS hai - Piyush ka personal AI butler jo uske Android phone ko PURA control karta hai, apps ke andar tak.

RULES:
1. Hamesha Hinglish me reply karo - simple, friendly, chhote sentences.
2. Jab bhi user se file ka kaam ho (PPT, Excel, 3D model, LinkedIn, resume, email, bio, kahani) - uska tool use karo, file phone par bhej do. Khud sirf chat me text mat do.
3. PPT ke liye outline_json me solid, informative bullet points likho (user ki bhasha ke hisaab se).

GUI KAAM (apps ke andar) - SABSE ZARURI:
4. KAI BAAR USER KOI BHI TASK KISI BHI APP ME DEGA. Rule: agar kaam kisi app ke SCREEN par hona hai (koi bhi app - WhatsApp, Instagram, YouTube, Gmail, Maps, Settings, Chrome, koi bhi), to HAMESHA gui_task use karo. Agent khud screen dekh kar samjhega kya karna hai - tujhe bas app ka naam + task dena hai. open_app sirf tab jab user ne sirf "kholo" bola ho.
5. gui_task ka TASK specific likho lekin HAMESHA complete sentence me: kaunsi app, kiska naam / kya cheez, exact text kya, kaunsa option chunein. E.g. "Rahul ko msg karo" -> "WhatsApp me 'Rahul' naam ka chat kholo aur message bhejo: <text>". User ne app nahi batayi ho to context se socho (msg bhejna -> WhatsApp, search -> YouTube/Maps, etc.) aur task me app ka naam khud likho.
6. Task mushkil ya lamba ho to chinta mat karo - gui_task ka plan bana kar step-by-step karta hai, screenshot dekh kar verify karta hai, aur galti par dobara try karta hai. Bas task saaf likhna.
6. PPT ka do tarika: (a) default - create_presentation se clean PPTX file banao (fast, professional); (b) agar user NE clearly kaha "phone ke PowerPoint app me banao" / "live banao" - tab gui_task(app="powerpoint", task="...") use karo jo app ke andar slides banayega. Dono options user ko bata do agar wo confuse ho.
7. PowerPoint app me live banate waqt task me har slide ka title + bullets explicitly likhna, warna app me galti se ban jayega.

EDITING / CUSTOMIZATION:
8. User bole "slide 2 ka title badlo", "ye bullet remove karo", "nayi slide add karo" - edit_presentation tool use karo (file "auto" = pichli PPT).
9. User bole "B3 me 5000 daalo", "naya row add karo" - edit_spreadsheet use karo (file "auto" = pichli Excel).
10. Edit ke baad user ko file ka naya naam bata do aur bata do ki phone par bhej di hai.

PHONE CONTROL (gui_task ke alawa):
11. Simple screen cheezein (screenshot dekhna, back, home, scroll) ke liye screenshot/tap/swipe tools use karo.
12. LinkedIn/resume jaisa content professional English me likho, lekin reply Hinglish me.
13. Agar phone connect nahi hai: device wale kaam par user ko bata do ki phone app connect karni hogi. File/text wale kaam (PPT, Excel, 3D, LinkedIn) aaj bhi kar sakte ho.
14. Kabhi bhi user ki galti na maaro; kaam chhota sa bhi ho to poora karo aur bata do.
15. gui_task ke dauraan alag-alag updates mat bhejo - wo khud progress bhejta hai.
"""


class Agent:
    def __init__(self, bridge):
        self.bridge = bridge
        self._sessions: dict[str, list] = {}

    async def handle_user(self, device_id: str, text: str):
        link = self.bridge.devices.get(device_id) or self.bridge.primary
        if link is None:
            return
        screen = {
            "width": link.info.get("screen_w"),
            "height": link.info.get("screen_h"),
        } if link.info else {}
        ctx = Ctx(bridge=self.bridge, screen=screen)
        history = self._sessions.setdefault(device_id, [])
        messages: list = [{"role": "system", "content": SYSTEM_PROMPT}]
        messages += history
        messages.append({"role": "user", "content": text})

        vision_off = not settings.vision_enabled

        for step in range(settings.max_steps):
            # ---------- LLM call ----------
            try:
                out = llm_chat(messages, tools=TOOL_SPECS)
            except Exception as e:
                # Agar vision wala message wajah hai to bina image ke retry
                has_image = any(
                    isinstance(m.get("content"), list) for m in messages
                )
                if not vision_off and has_image:
                    messages = [m for m in messages if not isinstance(m.get("content"), list)]
                    vision_off = True
                    continue
                try:
                    await self.bridge.text(f"😵 AI se baat karne me problem aayi: {e}")
                except Exception:
                    pass
                return

            # ---------- Final answer? ----------
            if not out.tool_calls:
                final = (out.content or "").strip() or "Ho gaya! ✅"
                history.append({"role": "user", "content": text})
                history.append({"role": "assistant", "content": final})
                del history[:-12]
                await self.bridge.text(final)
                if len(final) < 600:
                    try:
                        await self.bridge.tts(final)
                    except Exception:
                        pass
                return

            # ---------- Tools chalo ----------
            messages.append({
                "role": "assistant",
                "content": out.content or "",
                "tool_calls": [
                    {
                        "id": tc["id"],
                        "type": "function",
                        "function": {
                            "name": tc["name"],
                            "arguments": json.dumps(tc["arguments"], ensure_ascii=False),
                        },
                    }
                    for tc in out.tool_calls
                ],
            })

            for tc in out.tool_calls:
                name, args = tc["name"], tc["arguments"]
                try:
                    await self.bridge.progress(f"⚙️ {name}...")
                except Exception:
                    pass
                try:
                    result = await execute(name, args, ctx)
                except DeviceError as e:
                    result = {"error": str(e)}
                except Exception as e:
                    result = {"error": f"Tool me galti: {e}"}

                summary = json.dumps(result, ensure_ascii=False, default=str)
                if len(summary) > 4000:
                    summary = summary[:4000]
                messages.append({
                    "role": "tool",
                    "tool_call_id": tc["id"],
                    "content": summary,
                })

                # bane hue files phone par bhejo
                files = result.get("files") if isinstance(result, dict) else None
                for f in files or []:
                    try:
                        await self.bridge.progress(f"📤 {Path(f).name} phone par bhej raha hu...")
                        await self.bridge.send_file(f)
                    except Exception:
                        pass
                if isinstance(result, dict) and result.get("clipboard"):
                    try:
                        await self.bridge.clipboard(result["clipboard"])
                    except Exception:
                        pass

                # screenshot -> LLM ko dikhao (agar vision chalu hai)
                if name == "screenshot" and not vision_off and isinstance(result, dict) \
                        and result.get("image_b64"):
                    w = ctx.screen.get("width")
                    h = ctx.screen.get("height")
                    messages.append({"role": "user", "content": [
                        {"type": "text",
                         "text": (f"Yeh abhi ka phone screen ka screenshot hai "
                                  f"(screen {w or '?'}x{h or '?'} px). Isse dekh kar agla action socho.")},
                        {"type": "image_url",
                         "image_url": {"url": f"data:image/png;base64,{result['image_b64']}"}},
                    ]})
