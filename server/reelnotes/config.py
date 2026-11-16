import os
from pathlib import Path

try:
    from dotenv import load_dotenv

    load_dotenv(Path(__file__).resolve().parent.parent / ".env")
except ImportError:  # optional
    pass


def _get(name: str, default: str = "") -> str:
    return os.environ.get(name, default).strip()


API_TOKEN = _get("REELNOTES_TOKEN")
HOST = _get("REELNOTES_HOST", "0.0.0.0")
PORT = int(_get("REELNOTES_PORT", "8787"))

GEMINI_API_KEY = _get("GEMINI_API_KEY")
GEMINI_MODEL = _get("GEMINI_MODEL", "gemini-flash-latest")

CLAUDE_CLI = _get("CLAUDE_CLI", "claude")
CLAUDE_MODEL = _get("CLAUDE_MODEL")  # empty = your Claude Code default
CLAUDE_TIMEOUT = int(_get("CLAUDE_TIMEOUT", "300"))

WHISPER_MODEL = _get("WHISPER_MODEL", "large-v3-turbo")
WHISPER_DEVICE = _get("WHISPER_DEVICE", "auto")  # auto | cuda | cpu

# yt-dlp login helpers for Instagram (optional, needed if downloads get blocked)
YTDLP_COOKIES_FILE = _get("YTDLP_COOKIES_FILE")
YTDLP_COOKIES_BROWSER = _get("YTDLP_COOKIES_BROWSER")  # e.g. firefox

FRAME_COUNT = int(_get("FRAME_COUNT", "10"))
