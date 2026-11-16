"""ffmpeg frame extraction and local Whisper transcription (used by the Claude provider)."""
from __future__ import annotations

import logging
import os
import shutil
import subprocess
import sys
from pathlib import Path

from . import config

log = logging.getLogger(__name__)
_whisper_model = None


def _ffmpeg() -> str:
    exe = shutil.which("ffmpeg")
    if not exe:
        raise RuntimeError("ffmpeg not found. Install it (Windows: winget install Gyan.FFmpeg) and restart.")
    return exe


def _duration(video: Path) -> float:
    probe = shutil.which("ffprobe")
    if not probe:
        return 0.0
    out = subprocess.run(
        [probe, "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(video)],
        capture_output=True, text=True,
    )
    try:
        return float(out.stdout.strip())
    except ValueError:
        return 0.0


def extract_frames(video: Path, outdir: Path, count: int) -> list[Path]:
    """Evenly spaced frames (scaled to 768px wide) so on-screen text can be read."""
    outdir.mkdir(parents=True, exist_ok=True)
    dur = _duration(video) or 60.0
    frames: list[Path] = []
    for i in range(count):
        t = dur * (i + 0.5) / count
        out = outdir / f"frame_{video.stem}_{i:02d}.jpg"
        subprocess.run(
            [_ffmpeg(), "-y", "-loglevel", "error", "-ss", f"{t:.2f}", "-i", str(video),
             "-frames:v", "1", "-vf", "scale=768:-2", "-q:v", "3", str(out)],
            capture_output=True,
        )
        if out.exists() and out.stat().st_size > 0:
            frames.append(out)
    return frames


def _add_nvidia_dll_dirs() -> None:
    """On Windows, pip-installed CUDA libs (nvidia-cublas-cu12 / nvidia-cudnn-cu12) must be on the DLL path."""
    if sys.platform != "win32":
        return
    try:
        import nvidia  # type: ignore
    except ImportError:
        return
    for base in nvidia.__path__:
        for sub in ("cublas", "cudnn", "cuda_nvrtc"):
            d = Path(base) / sub / "bin"
            if d.is_dir():
                os.add_dll_directory(str(d))
                os.environ["PATH"] = str(d) + os.pathsep + os.environ.get("PATH", "")


def _load_whisper():
    global _whisper_model
    if _whisper_model is not None:
        return _whisper_model
    from faster_whisper import WhisperModel

    _add_nvidia_dll_dirs()
    attempts = []
    if config.WHISPER_DEVICE in ("auto", "cuda"):
        attempts.append(("cuda", "float16", config.WHISPER_MODEL))
    if config.WHISPER_DEVICE in ("auto", "cpu"):
        attempts.append(("cpu", "int8", "small"))
    last = None
    for device, compute, name in attempts:
        try:
            _whisper_model = WhisperModel(name, device=device, compute_type=compute)
            log.info("Whisper loaded: %s on %s", name, device)
            return _whisper_model
        except Exception as e:
            log.warning("Whisper %s/%s failed: %s", name, device, e)
            last = e
    raise RuntimeError(f"Could not load Whisper: {last}")


def transcribe(video: Path) -> str:
    try:
        model = _load_whisper()
        segments, _info = model.transcribe(str(video), vad_filter=True)
        return " ".join(s.text.strip() for s in segments).strip()
    except Exception as e:
        # Retry once on CPU if the GPU libraries are missing at run time.
        global _whisper_model
        msg = str(e).lower()
        if config.WHISPER_DEVICE == "auto" and any(k in msg for k in ("cuda", "cublas", "cudnn")):
            from faster_whisper import WhisperModel

            log.warning("GPU transcription failed (%s); falling back to CPU", e)
            _whisper_model = WhisperModel("small", device="cpu", compute_type="int8")
            segments, _info = _whisper_model.transcribe(str(video), vad_filter=True)
            return " ".join(s.text.strip() for s in segments).strip()
        log.warning("Transcription failed: %s", e)
        return ""
