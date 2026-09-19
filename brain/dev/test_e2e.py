"""E2E test - fake phone (Android app ki jagah yeh script phone ban jaati hai).

Terminal 1 me:   LLM_MOCK=1 .venv/bin/python -m brain.main
Terminal 2 me:   .venv/bin/python -m brain.dev.test_e2e
"""
import asyncio
import base64
import json
import pathlib

import websockets

URI = "ws://127.0.0.1:8787/ws?token=piyush123"

PROMPTS = [
    "Ek 4 slide ki PPT banao artificial intelligence par",
    "Excel me mera mahinayana budget banao",
    "Ek rocket ka 3D model banao red color ka",
    "LinkedIn profile likh do mere liye",
    "Instagram kholo",
    "WhatsApp me Rahul ko message bhejo: kal milte hain",
]

# 1x1 transparent PNG (base64)
_TINY_PNG_B64 = (
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="
)

# Fake WhatsApp-like screen tree (GUI agent test ke liye)
FAKE_TREE = json.dumps([
    {"i": 0, "t": "WhatsApp", "b": [0, 0, 1080, 2400]},
    {"i": 1, "t": "Search", "b": [600, 180, 1000, 260], "c": True, "e": True, "cl": "EditText"},
    {"i": 2, "t": "Rahul", "b": [40, 300, 1000, 420], "c": True},
    {"i": 3, "t": "Send", "b": [900, 2100, 1040, 2240], "c": True},
])


async def main():
    pathlib.Path("out").mkdir(exist_ok=True)
    async with websockets.connect(URI, max_size=None) as ws:
        await ws.send(json.dumps({"type": "hello", "device_name": "FakePhone",
                                  "screen_w": 1080, "screen_h": 2400}))
        print("SERVER:", await ws.recv())

        for prompt in PROMPTS:
            print(f"\n🗣  PIYUSH: {prompt}")
            await ws.send(json.dumps({"type": "chat", "text": prompt}))
            while True:
                raw = await asyncio.wait_for(ws.recv(), 120)
                msg = json.loads(raw)
                t = msg.get("type")
                if t == "cmd":
                    act = msg["action"]
                    payload = msg.get("payload", {})
                    print(f"   ⚙️  CMD: {act} {json.dumps(payload)[:100]}")
                    if act == "screen":
                        data = {"ok": True, "tree": FAKE_TREE,
                                "image_b64": _TINY_PNG_B64, "width": 1080, "height": 2400}
                    elif act == "screenshot":
                        data = {"ok": True, "image_b64": _TINY_PNG_B64,
                                "width": 1080, "height": 2400}
                    else:
                        data = {"ok": True}
                    await ws.send(json.dumps({"type": "cmd_ack", "id": msg["id"],
                                              "ok": True, "data": data}))
                elif t == "file":
                    out = f"out/{msg['name']}"
                    pathlib.Path(out).write_bytes(base64.b64decode(msg["b64"]))
                    print(f"   📄 FILE: {msg['name']} -> {out} ({msg['mime']})")
                elif t == "clipboard":
                    print(f"   📋 CLIPBOARD: {msg['text'][:70].replace(chr(10), ' / ')}...")
                elif t == "progress":
                    print(f"   … {msg['text']}")
                elif t == "tts":
                    print(f"   🔊 TTS: {msg['text'][:60]}")
                elif t == "chat" and msg.get("done"):
                    print(f"   🧠 BUTLER: {msg['message']}")
                    break
    print("\n🎉 E2E test khatam!")


if __name__ == "__main__":
    asyncio.run(main())
