"""Tool registry - LLM ko batata hai kaunse tools hain aur unhe execute karta hai."""
import asyncio
import inspect
from dataclasses import dataclass

from ..ws.bridge import Bridge
from . import device_tools, edit_tool, gui_tool, model3d_tool, pptx_tool, share_tool, xlsx_tool


@dataclass
class Ctx:
    bridge: Bridge
    screen: dict
    last_screenshot: str | None = None


# ---------------- OpenAI function calling specs ----------------
TOOL_SPECS = [
    {
        "type": "function",
        "function": {
            "name": "create_presentation",
            "description": "PowerPoint PPT banata hai aur phone par bhejta hai",
            "parameters": {
                "type": "object",
                "properties": {
                    "topic": {"type": "string", "description": "PPT ka topic"},
                    "outline_json": {
                        "type": "string",
                        "description": ("JSON array. Pehla slide: {\"title\": str, \"subtitle\": str}. "
                                        "Baaki slides: {\"title\": str, \"bullets\": [str]}. "
                                        "4 se 12 slides, informative bullet points."),
                    },
                },
                "required": ["topic", "outline_json"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "create_spreadsheet",
            "description": "Excel file banata hai aur phone par bhejta hai",
            "parameters": {
                "type": "object",
                "properties": {
                    "spec_json": {
                        "type": "string",
                        "description": ('JSON: {"title": str, "sheets": [{"name": str, '
                                        '"headers": [str], "rows": [[...]]}]}'),
                    },
                },
                "required": ["spec_json"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "create_3d_model",
            "description": "3D model banata hai (GLB dekhne ke liye + STL 3D printing ke liye) aur phone par bhejta hai",
            "parameters": {
                "type": "object",
                "properties": {
                    "name": {"type": "string", "description": "Model ka naam (file name ke liye)"},
                    "shape": {
                        "type": "string",
                        "enum": ["rocket", "car", "house", "chair", "cube",
                                 "sphere", "cylinder", "cone", "torus", "combo"],
                        "description": "Model ki shape",
                    },
                    "color": {"type": "string", "description": "e.g. red, blue, green ya hex #ff5252"},
                },
                "required": ["shape"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "share_text",
            "description": ("Koi bhi likha hua text (LinkedIn profile, resume, email, bio, project description) "
                            "phone par file ki tarah bhejta hai + clipboard par copy karta hai taaki user paste kare"),
            "parameters": {
                "type": "object",
                "properties": {
                    "title": {"type": "string", "description": "Text ka naam, e.g. 'LinkedIn About Section'"},
                    "content": {"type": "string", "description": "Pura text jo phone par bhejna hai"},
                },
                "required": ["title", "content"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "gui_task",
            "description": ("Kisi bhi app ke ANDAR kaam karwana (screen dekh ke navigate karta hai). "
                            "Jaise: WhatsApp/Instagram me message bhejna, search karna, post banana, "
                            "PowerPoint app me live slides banana, settings badalna. "
                            "TASK bahut specific likho - kisko, kya text, kaunsa option."),
            "parameters": {
                "type": "object",
                "properties": {
                    "app": {"type": "string", "description": "Kaunsi app (whatsapp, instagram, powerpoint, youtube, gmail...). Agar app already open hai to khaali chhod do"},
                    "task": {"type": "string", "description": "Poora kaam detail me. E.g. 'Rahul naam ka chat kholo aur unhe message bhejo: kal milte hain'"},
                    "max_steps": {"type": "integer", "description": "Max steps (3-12, default 10)"},
                },
                "required": ["task"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "edit_presentation",
            "description": ("Pichli PPT me customization/edit: title badalna, bullets badalna, "
                            "slide add/delete karna. User bole 'slide 2 badlo' to yeh tool use karo."),
            "parameters": {
                "type": "object",
                "properties": {
                    "file": {"type": "string", "description": "'auto' = pichli PPT (default), ya file ka naam"},
                    "edits_json": {
                        "type": "string",
                        "description": ('JSON: {"edits": [{"op":"set_title","slide":2,"text":"..."}, '
                                        '{"op":"set_bullets","slide":3,"bullets":["a","b"]}, '
                                        '{"op":"add_slide","after":4,"title":"...","bullets":["a"]}, '
                                        '{"op":"delete_slide","slide":5}, '
                                        '{"op":"new_title","text":"...","subtitle":"..."}]}'),
                    },
                },
                "required": ["edits_json"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "edit_spreadsheet",
            "description": ("Pichli Excel me edit: cell ka value badalna, row add/delete karna. "
                            "User bole 'B3 me 5000 daalo' to yeh tool use karo."),
            "parameters": {
                "type": "object",
                "properties": {
                    "file": {"type": "string", "description": "'auto' = pichli Excel (default), ya file ka naam"},
                    "edits_json": {
                        "type": "string",
                        "description": ('JSON: {"edits": [{"op":"set_cell","sheet":"Budget","cell":"B4","value":5000}, '
                                        '{"op":"add_row","sheet":"Budget","row":["New","100","90","10"]}, '
                                        '{"op":"delete_row","sheet":"Budget","row":3}]}'),
                    },
                },
                "required": ["edits_json"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "open_app",
            "description": "Phone par koi app sirf kholta hai (naam se, e.g. instagram, whatsapp, chrome). App ke ANDAR kaam ke liye gui_task use karo",
            "parameters": {
                "type": "object",
                "properties": {"app": {"type": "string"}},
                "required": ["app"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "tap",
            "description": "Screen par pixels (x, y) par tap karta hai. Pehle screenshot dekho.",
            "parameters": {
                "type": "object",
                "properties": {"x": {"type": "integer"}, "y": {"type": "integer"}},
                "required": ["x", "y"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "double_tap",
            "description": "Pixels (x, y) par double tap",
            "parameters": {
                "type": "object",
                "properties": {"x": {"type": "integer"}, "y": {"type": "integer"}},
                "required": ["x", "y"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "long_press",
            "description": "Pixels (x, y) par long press",
            "parameters": {
                "type": "object",
                "properties": {"x": {"type": "integer"}, "y": {"type": "integer"}},
                "required": ["x", "y"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "swipe",
            "description": "Screen ko swipe karta hai",
            "parameters": {
                "type": "object",
                "properties": {
                    "direction": {"type": "string", "enum": ["up", "down", "left", "right"]},
                    "amount": {"type": "integer", "description": "10-90 (% of screen)"},
                },
                "required": ["direction"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "input_text",
            "description": "Jo bhi text box focus me hai wahan text type karta hai",
            "parameters": {
                "type": "object",
                "properties": {"text": {"type": "string"}},
                "required": ["text"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "back",
            "description": "Phone ka Back button dabata hai",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "home",
            "description": "Home screen par le jata hai",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "recent_apps",
            "description": "Recent apps (multitasking) kholta hai",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "open_notifications",
            "description": "Notification shade niche kheenchta hai",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "screenshot",
            "description": "Screen ka screenshot leta hai taaki dekh sako ki abhi kya dikh raha hai",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "wait",
            "description": "Kuch seconds wait karta hai (app load hone ke liye)",
            "parameters": {
                "type": "object",
                "properties": {"seconds": {"type": "number"}},
            },
        },
    },
]

HANDLERS = {
    "gui_task": lambda a, ctx: gui_tool.gui_task(
        ctx, a.get("app", ""), a.get("task", ""), a.get("max_steps", 10)),
    "edit_presentation": lambda a, ctx: edit_tool.edit_presentation(a.get("edits_json", "")),
    "edit_spreadsheet": lambda a, ctx: edit_tool.edit_spreadsheet(a.get("edits_json", "")),
    "create_presentation": lambda a, ctx: pptx_tool.build_pptx(a.get("topic", ""), a.get("outline_json", "")),
    "create_spreadsheet": lambda a, ctx: xlsx_tool.build_xlsx(a.get("spec_json", "")),
    "create_3d_model": lambda a, ctx: model3d_tool.build_model3d(
        a.get("name", "model"), a.get("shape", "rocket"), a.get("color", "#4fc3f7")),
    "share_text": lambda a, ctx: share_tool.share_text(ctx, a.get("title", "text"), a.get("content", "")),
    "open_app": lambda a, ctx: device_tools.open_app(ctx, a.get("app", "")),
    "tap": lambda a, ctx: device_tools.tap(ctx, int(a.get("x", 0)), int(a.get("y", 0))),
    "double_tap": lambda a, ctx: device_tools.double_tap(ctx, int(a.get("x", 0)), int(a.get("y", 0))),
    "long_press": lambda a, ctx: device_tools.long_press(ctx, int(a.get("x", 0)), int(a.get("y", 0))),
    "swipe": lambda a, ctx: device_tools.swipe(ctx, a.get("direction", "up"), a.get("amount", 50)),
    "input_text": lambda a, ctx: device_tools.input_text(ctx, a.get("text", "")),
    "back": lambda a, ctx: device_tools.back(ctx),
    "home": lambda a, ctx: device_tools.home(ctx),
    "recent_apps": lambda a, ctx: device_tools.recent_apps(ctx),
    "open_notifications": lambda a, ctx: device_tools.open_notifications(ctx),
    "screenshot": lambda a, ctx: device_tools.screenshot(ctx),
    "wait": lambda a, ctx: device_tools.wait(ctx, a.get("seconds", 1)),
}


async def execute(name: str, args: dict, ctx: Ctx) -> dict:
    handler = HANDLERS.get(name)
    if handler is None:
        raise ValueError(f"Unknown tool: {name}")
    result = handler(args or {}, ctx)
    if inspect.isawaitable(result):
        result = await result
    if not isinstance(result, dict):
        result = {"summary": str(result)}
    return result
