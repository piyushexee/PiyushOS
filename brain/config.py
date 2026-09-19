"""PiyushOS - configuration (.env se values load hoti hain)."""
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

ROOT = Path(__file__).resolve().parent.parent


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=str(ROOT / ".env"),
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # ---------- LLM (default: NVIDIA NIM) ----------
    llm_api_key: str = ""
    llm_base_url: str = "https://integrate.api.nvidia.com/v1"
    llm_model: str = "nvidia/nemotron-3-ultra-550b-a55b"
    llm_mock: bool = False          # True = bina API key ke test mode
    vision_enabled: bool = True     # screenshot ko LLM ko dikhana?
    max_steps: int = 12             # ek command me max tool steps

    # ---------- Server ----------
    host: str = "0.0.0.0"
    port: int = 8787
    device_token: str = "piyush123"  # Android app me yahi token dena hai

    # ---------- Files ----------
    out_dir: Path = ROOT / "brain" / "generated"


settings = Settings()
settings.out_dir.mkdir(parents=True, exist_ok=True)
