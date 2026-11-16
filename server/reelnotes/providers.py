"""AI providers: Gemini (free API tier) and Claude (via the Claude Code CLI on your Pro plan)."""
from __future__ import annotations

import json
import logging
import mimetypes
import re
import shutil
import subprocess
import time
from pathlib import Path

from . import config, media_tools
from .download import Media
from .schema import RESULT_SCHEMA, build_instructions

log = logging.getLogger(__name__)


class ProviderError(RuntimeError):
    pass


def _extract_json(text: str) -> dict:
    text = text.strip()
    fence = re.search(r"```(?:json)?\s*(\{.*\})\s*```", text, re.S)
    if fence:
        text = fence.group(1)
    else:
        start, end = text.find("{"), text.rfind("}")
        if start != -1 and end > start:
            text = text[start : end + 1]
    try:
        return json.loads(text)
    except json.JSONDecodeError as e:
        raise ProviderError(f"AI did not return valid JSON: {e}") from e


# ---------------------------------------------------------------- Gemini
def run_gemini(media: Media, categories: list[str]) -> tuple[dict, str]:
    if not config.GEMINI_API_KEY:
        raise ProviderError("GEMINI_API_KEY is not set in server/.env (get one free at aistudio.google.com/apikey)")
    from google import genai
    from google.genai import types

    client = genai.Client(api_key=config.GEMINI_API_KEY)
    uploaded = []
    try:
        for path in (media.videos + media.images)[:10]:
            mime = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
            f = client.files.upload(file=str(path), config={"mime_type": mime})
            uploaded.append(f)
        # Videos need server-side processing before use.
        deadline = time.time() + 180
        for i, f in enumerate(uploaded):
            while f.state and f.state.name == "PROCESSING":
                if time.time() > deadline:
                    raise ProviderError("Gemini took too long processing the video")
                time.sleep(2)
                f = client.files.get(name=f.name)
            if f.state and f.state.name == "FAILED":
                raise ProviderError("Gemini could not process the uploaded media")
            uploaded[i] = f

        prompt = build_instructions(categories, media.caption, media.creator)
        if not uploaded:
            prompt += "\n(No video could be downloaded; work from the caption only.)"
        resp = client.models.generate_content(
            model=config.GEMINI_MODEL,
            contents=[*uploaded, prompt],
            config=types.GenerateContentConfig(
                response_mime_type="application/json",
                response_schema=RESULT_SCHEMA,
                temperature=0.2,
            ),
        )
        if not resp.text:
            raise ProviderError("Gemini returned an empty response (possibly blocked)")
        return _extract_json(resp.text), ""
    except ProviderError:
        raise
    except Exception as e:
        raise ProviderError(f"Gemini error: {e}") from e
    finally:
        for f in uploaded:
            try:
                client.files.delete(name=f.name)
            except Exception:
                pass


# ---------------------------------------------------------------- Claude
def run_claude(media: Media, categories: list[str], workdir: Path) -> tuple[dict, str]:
    exe = shutil.which(config.CLAUDE_CLI)
    if not exe:
        raise ProviderError(
            "Claude Code CLI not found. Install it on this PC and run `claude` once to log in with your Pro account."
        )

    transcript = ""
    frames: list[Path] = []
    frame_dir = workdir / "frames"
    for v in media.videos[:3]:
        transcript += (media_tools.transcribe(v) + "\n").lstrip()
        per_video = max(4, config.FRAME_COUNT // max(1, len(media.videos[:3])))
        frames += media_tools.extract_frames(v, frame_dir, per_video)
    images = frames + media.images[:10]

    prompt = build_instructions(categories, media.caption, media.creator)
    prompt += f"\nSpoken audio transcript:\n\"\"\"\n{transcript.strip() or '(no speech / not available)'}\n\"\"\"\n"
    if images:
        rel = "\n".join(f"- {p.relative_to(workdir).as_posix()}" for p in images)
        prompt += (
            "\nUse the Read tool to look at EVERY one of these image files (video frames in time order, or "
            f"screenshots) and read any on-screen text:\n{rel}\n"
        )
    prompt += (
        "\nReply with ONLY a JSON object (no markdown, no commentary) matching this JSON schema:\n"
        + json.dumps(RESULT_SCHEMA)
    )

    cmd = [exe, "-p", "--output-format", "json", "--tools", "Read", "--allowedTools", "Read"]
    if config.CLAUDE_MODEL:
        cmd += ["--model", config.CLAUDE_MODEL]
    try:
        proc = subprocess.run(
            cmd, input=prompt, capture_output=True, text=True, encoding="utf-8",
            cwd=str(workdir), timeout=config.CLAUDE_TIMEOUT,
        )
    except subprocess.TimeoutExpired as e:
        raise ProviderError("Claude timed out") from e
    if proc.returncode != 0:
        raise ProviderError(f"Claude CLI failed: {(proc.stderr or proc.stdout)[-800:]}")

    try:
        envelope = json.loads(proc.stdout)
    except json.JSONDecodeError:
        envelope = {"result": proc.stdout}
    if envelope.get("is_error"):
        raise ProviderError(f"Claude error: {envelope.get('result')}")
    result = envelope.get("structured_output") or _extract_json(str(envelope.get("result", "")))
    return result, transcript.strip()


# ---------------------------------------------------------------- text-only (ask / quiz)
def run_text(provider: str, prompt: str, schema: dict) -> dict:
    """Send a plain-text prompt to the chosen AI and get JSON matching `schema`."""
    if provider == "claude":
        exe = shutil.which(config.CLAUDE_CLI)
        if not exe:
            raise ProviderError("Claude Code CLI not found on the PC.")
        full = prompt + "\n\nReply with ONLY a JSON object (no markdown) matching this JSON schema:\n" + json.dumps(schema)
        cmd = [exe, "-p", "--output-format", "json", "--tools", "Read", "--allowedTools", "Read"]
        if config.CLAUDE_MODEL:
            cmd += ["--model", config.CLAUDE_MODEL]
        import tempfile

        with tempfile.TemporaryDirectory(prefix="reelnotes_txt_") as tmp:
            try:
                proc = subprocess.run(cmd, input=full, capture_output=True, text=True, encoding="utf-8",
                                      cwd=tmp, timeout=config.CLAUDE_TIMEOUT)
            except subprocess.TimeoutExpired as e:
                raise ProviderError("Claude timed out") from e
        if proc.returncode != 0:
            raise ProviderError(f"Claude CLI failed: {(proc.stderr or proc.stdout)[-800:]}")
        try:
            envelope = json.loads(proc.stdout)
        except json.JSONDecodeError:
            envelope = {"result": proc.stdout}
        if envelope.get("is_error"):
            raise ProviderError(f"Claude error: {envelope.get('result')}")
        return envelope.get("structured_output") or _extract_json(str(envelope.get("result", "")))

    if not config.GEMINI_API_KEY:
        raise ProviderError("GEMINI_API_KEY is not set in server/.env")
    from google import genai
    from google.genai import types

    try:
        client = genai.Client(api_key=config.GEMINI_API_KEY)
        resp = client.models.generate_content(
            model=config.GEMINI_MODEL,
            contents=prompt,
            config=types.GenerateContentConfig(
                response_mime_type="application/json", response_schema=schema, temperature=0.3,
            ),
        )
    except Exception as e:
        raise ProviderError(f"Gemini error: {e}") from e
    if not resp.text:
        raise ProviderError("Gemini returned an empty response")
    return _extract_json(resp.text)
