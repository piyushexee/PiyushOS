"""Phone control tools - sab kuch bridge ke through Android app par jaata hai."""
from ..ws.bridge import DeviceError


async def open_app(ctx, app: str) -> dict:
    data = await ctx.bridge.cmd("open_app", {"app": app or ""})
    if not data.get("ok", True):
        raise DeviceError(data.get("error", "App nahi khul payi"))
    return {"app": app, "summary": f"App '{app}' khol di phone par"}


async def tap(ctx, x: int, y: int) -> dict:
    await ctx.bridge.cmd("tap", {"x": int(x), "y": int(y)}, timeout=10)
    return {"summary": f"({x}, {y}) par tap kar diya"}


async def double_tap(ctx, x: int, y: int) -> dict:
    await ctx.bridge.cmd("double_tap", {"x": int(x), "y": int(y)}, timeout=10)
    return {"summary": f"({x}, {y}) par double tap kar diya"}


async def long_press(ctx, x: int, y: int) -> dict:
    await ctx.bridge.cmd("long_press", {"x": int(x), "y": int(y)}, timeout=10)
    return {"summary": f"({x}, {y}) par long press kar diya"}


async def swipe(ctx, direction: str, amount: int = 50) -> dict:
    direction = (direction or "up").lower()
    if direction not in ("up", "down", "left", "right"):
        raise ValueError("direction up/down/left/right me se ek hona chahiye")
    amount = max(10, min(90, int(amount or 50)))
    await ctx.bridge.cmd("swipe", {"direction": direction, "amount": amount}, timeout=10)
    return {"summary": f"Screen {direction} {amount}% swipe kar diya"}


async def input_text(ctx, text: str) -> dict:
    data = await ctx.bridge.cmd("input_text", {"text": text or ""}, timeout=10)
    if not data.get("ok", True):
        raise DeviceError(data.get("error", "Typing nahi kar paya (text box focus nahi mila)"))
    return {"summary": f"Text type kar diya: {text[:60]}"}


async def back(ctx) -> dict:
    await ctx.bridge.cmd("back", timeout=10)
    return {"summary": "Back button dabaya"}


async def home(ctx) -> dict:
    await ctx.bridge.cmd("home", timeout=10)
    return {"summary": "Home screen par aa gaye"}


async def recent_apps(ctx) -> dict:
    await ctx.bridge.cmd("recent_apps", timeout=10)
    return {"summary": "Recent apps khol diye"}


async def open_notifications(ctx) -> dict:
    await ctx.bridge.cmd("open_notifications", timeout=10)
    return {"summary": "Notification shade khol di"}


async def screenshot(ctx) -> dict:
    data = await ctx.bridge.cmd("screenshot", timeout=30)
    if not data.get("ok", True) or not data.get("image_b64"):
        raise DeviceError("Screenshot nahi mil paya - app me 'Enable Screenshots' permission diya hai?")
    ctx.last_screenshot = data["image_b64"]
    return {
        "image_b64": data["image_b64"],
        "summary": f"Screenshot capture kiya (screen {data.get('width', '?')}x{data.get('height', '?')} px)",
    }


async def wait(ctx, seconds: float = 1.0) -> dict:
    import asyncio
    seconds = max(0.5, min(10.0, float(seconds or 1.0)))
    await asyncio.sleep(seconds)
    return {"summary": f"{seconds}s wait kiya"}
