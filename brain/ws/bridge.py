"""Device bridge - phone (Android app) ke saath WebSocket communication."""
import asyncio
import base64
import json
import mimetypes
import time
import uuid
from dataclasses import dataclass, field
from pathlib import Path


class DeviceError(Exception):
    """Phone se judne/kaam nahi hone ki galti."""


@dataclass
class DeviceLink:
    ws: object
    device_id: str
    info: dict = field(default_factory=dict)
    pending: dict = field(default_factory=dict)   # cmd_id -> Future
    connected_at: float = field(default_factory=time.time)


class Bridge:
    def __init__(self):
        self.devices: dict[str, DeviceLink] = {}

    # ---------------- device registry ----------------
    def add(self, link: DeviceLink):
        self.devices[link.device_id] = link

    def remove(self, device_id: str):
        self.devices.pop(device_id, None)

    @property
    def primary(self) -> DeviceLink | None:
        """Pehli connect hui device (abhi ek phone ka hisaab hai)."""
        return next(iter(self.devices.values()), None)

    # ---------------- server -> phone messages ----------------
    async def _send(self, msg: dict):
        link = self.primary
        if link is None:
            raise DeviceError("Phone connect nahi hai. Pehle Android app me connect karo.")
        await link.ws.send_text(json.dumps(msg, ensure_ascii=False))

    async def progress(self, text: str):
        await self._send({"type": "progress", "text": text})

    async def text(self, message: str):
        await self._send({"type": "chat", "message": message, "done": True})

    async def tts(self, text: str):
        await self._send({"type": "tts", "text": text})

    async def clipboard(self, text: str):
        await self._send({"type": "clipboard", "text": text})

    async def send_file(self, path: str | Path, name: str | None = None, mime: str | None = None):
        p = Path(path)
        data = p.read_bytes()
        mime = mime or mimetypes.guess_type(p.name)[0] or "application/octet-stream"
        await self._send({
            "type": "file",
            "name": name or p.name,
            "mime": mime,
            "b64": base64.b64encode(data).decode(),
        })

    # ---------------- commands (server -> phone, reply phone se) ----------------
    async def cmd(self, action: str, payload: dict | None = None, timeout: float = 25.0) -> dict:
        link = self.primary
        if link is None:
            raise DeviceError("Phone connect nahi hai. Pehle Android app me connect karo.")
        cid = uuid.uuid4().hex[:8]
        fut = asyncio.get_running_loop().create_future()
        link.pending[cid] = fut
        await link.ws.send_text(json.dumps(
            {"type": "cmd", "id": cid, "action": action, "payload": payload or {}},
            ensure_ascii=False))
        try:
            return await asyncio.wait_for(fut, timeout)
        except asyncio.TimeoutError:
            link.pending.pop(cid, None)
            raise DeviceError(f"Phone ne command '{action}' ke liye waqt me reply nahi kiya.")

    def resolve_ack(self, cid: str, ok: bool, data: dict) -> bool:
        for link in self.devices.values():
            fut = link.pending.pop(cid, None)
            if fut is not None and not fut.done():
                fut.set_result({"ok": ok, **(data or {})})
                return True
        return False
