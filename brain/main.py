"""PiyushOS Brain - FastAPI + WebSocket server.

Chalane ka tarika (piyushos folder se):
    python -m brain.main
"""
import asyncio
import json
import uuid

import uvicorn
from fastapi import FastAPI, WebSocket, WebSocketDisconnect

from .agent import Agent
from .config import settings
from .ws.bridge import Bridge, DeviceLink

app = FastAPI(title="PiyushOS Brain")
bridge = Bridge()
agent = Agent(bridge)


@app.get("/health")
def health():
    return {
        "ok": True,
        "name": "PiyushOS Brain",
        "model": settings.llm_model,
        "mock": settings.llm_mock,
        "devices": len(bridge.devices),
    }


@app.websocket("/ws")
async def ws_endpoint(websocket: WebSocket):
    await websocket.accept()

    token = websocket.query_params.get("token", "")
    if token != settings.device_token:
        await websocket.send_text(json.dumps(
            {"type": "error", "message": "Ghalat token - .env ka DEVICE_TOKEN match nahi karta"}))
        await websocket.close(code=4401)
        return

    device_id = f"dev-{uuid.uuid4().hex[:6]}"
    link = DeviceLink(ws=websocket, device_id=device_id)
    bridge.add(link)
    print(f"📱 Phone connect hua: {device_id}")
    await websocket.send_text(json.dumps({"type": "hello_ack", "device_id": device_id}))

    try:
        async for raw in websocket.iter_text():
            try:
                msg = json.loads(raw)
            except json.JSONDecodeError:
                continue
            t = msg.get("type")
            if t == "hello":
                link.info = msg
                print(f"   ↳ device: {msg.get('device_name')} "
                      f"({msg.get('screen_w')}x{msg.get('screen_h')})")
            elif t == "chat":
                text = (msg.get("text") or "").strip()
                if text:
                    print(f"🗣  {device_id}: {text}")
                    asyncio.create_task(agent.handle_user(device_id, text))
            elif t == "cmd_ack":
                bridge.resolve_ack(msg.get("id", ""), msg.get("ok", True), msg.get("data", {}))
            elif t == "ping":
                await websocket.send_text(json.dumps({"type": "pong"}))
    except WebSocketDisconnect:
        pass
    finally:
        bridge.remove(device_id)
        print(f"📴 Phone disconnect: {device_id}")


def main():
    print("=" * 62)
    print("  🧠 PiyushOS Brain - aapka personal AI butler server")
    print("=" * 62)
    print(f"  Model : {settings.llm_model}")
    print(f"  Base  : {settings.llm_base_url}")
    print(f"  Mode  : {'MOCK (API key nahi chahiye)' if settings.llm_mock else 'LIVE (NVIDIA NIM)'}")
    print(f"  Port  : {settings.port}")
    print(f"  Token : {settings.device_token}")
    print("-" * 62)
    print("  Android app me yahi IP : Port : Token daal ke connect karo.")
    print("  PC ka IP dekhne ke liye:  ipconfig (Windows) / ifconfig (Linux-Mac)")
    print("=" * 62)
    uvicorn.run(app, host=settings.host, port=settings.port, log_level="warning")


if __name__ == "__main__":
    main()
