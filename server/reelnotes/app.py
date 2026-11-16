"""ReelNotes server: the phone sends a reel link (or video/screenshots), this returns a structured note."""
from __future__ import annotations

import asyncio
import logging
import re
import secrets
import shutil
import tempfile
from pathlib import Path

import httpx
from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, UploadFile
from fastapi.concurrency import run_in_threadpool

from . import config, providers
from .download import Media, classify_uploads, download
from .schema import DEFAULT_CATEGORIES

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("reelnotes")

app = FastAPI(title="ReelNotes server")
_jobs = asyncio.Semaphore(2)  # don't overload the PC/GPU


def check_token(x_api_key: str = Header(default="")) -> None:
    if config.API_TOKEN and not secrets.compare_digest(x_api_key, config.API_TOKEN):
        raise HTTPException(status_code=401, detail="Wrong API token (check app Settings)")


@app.get("/health")
def health() -> dict:
    return {
        "ok": True,
        "gemini": bool(config.GEMINI_API_KEY),
        "claude": bool(shutil.which(config.CLAUDE_CLI)),
        "ffmpeg": bool(shutil.which("ffmpeg")),
    }


URL_RE = re.compile(r"https?://\S+")


def first_url(text: str) -> str:
    m = URL_RE.search(text or "")
    return m.group(0).rstrip(").,") if m else ""


async def _check_links(result: dict) -> None:
    """Mark each link reachable or not so made-up/broken URLs are visible in the app."""
    items = [l for l in result.get("links", []) if isinstance(l, dict)]
    items += [t for t in result.get("tools", []) if isinstance(t, dict) and t.get("url")]

    async def check(item: dict, client: httpx.AsyncClient) -> None:
        url = (item.get("url") or "").strip()
        if url and not url.startswith("http"):
            url = "https://" + url
            item["url"] = url
        try:
            r = await client.get(url)
            item["reachable"] = r.status_code < 400 or r.status_code in (401, 403, 429)
        except Exception:
            item["reachable"] = False

    headers = {"User-Agent": "Mozilla/5.0 (ReelNotes link check)"}
    async with httpx.AsyncClient(timeout=8, follow_redirects=True, headers=headers) as client:
        await asyncio.gather(*(check(i, client) for i in items))


@app.post("/process", dependencies=[Depends(check_token)])
async def process(
    text: str = Form(default=""),
    provider: str = Form(default="gemini"),
    categories: str = Form(default=""),
    files: list[UploadFile] = File(default=[]),
) -> dict:
    cats = [c.strip() for c in categories.split("\n") if c.strip()] or DEFAULT_CATEGORIES
    url = first_url(text)
    if not url and not files:
        raise HTTPException(status_code=400, detail="Nothing to process: no link or files were shared")

    async with _jobs:
        workdir = Path(tempfile.mkdtemp(prefix="reelnotes_"))
        try:
            uploaded: list[Path] = []
            for i, f in enumerate(files[:10]):
                name = Path(f.filename or f"upload_{i}").name
                dest = workdir / f"up_{i:02d}_{name}"
                with dest.open("wb") as out:
                    shutil.copyfileobj(f.file, out)
                uploaded.append(dest)

            if uploaded:
                media = classify_uploads(uploaded)
                media.caption = text
            else:
                media = await run_in_threadpool(download, url, workdir)
                if not media.videos and not media.images and not media.caption:
                    raise HTTPException(
                        status_code=422,
                        detail="Could not download this post (Instagram may require login). "
                        "See server README 'Instagram login', or share a screen recording instead. "
                        f"Details: {media.download_error[:300]}",
                    )

            log.info("Processing %s with %s (%d videos, %d images)", url or "upload", provider,
                     len(media.videos), len(media.images))
            try:
                if provider == "claude":
                    result, transcript = await run_in_threadpool(providers.run_claude, media, cats, workdir)
                else:
                    result, transcript = await run_in_threadpool(providers.run_gemini, media, cats)
            except providers.ProviderError as e:
                raise HTTPException(status_code=502, detail=str(e)) from e

            await _check_links(result)
            return {
                "source_url": url,
                "creator": media.creator,
                "caption": media.caption,
                "transcript": transcript,
                "provider": provider,
                "note": result,
            }
        finally:
            shutil.rmtree(workdir, ignore_errors=True)


def main() -> None:
    import uvicorn

    if not config.API_TOKEN:
        log.warning("REELNOTES_TOKEN is empty: anyone on your network can use this server.")
    uvicorn.run(app, host=config.HOST, port=config.PORT)


if __name__ == "__main__":
    main()
