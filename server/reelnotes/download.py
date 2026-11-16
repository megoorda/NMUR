"""Download a reel (video + caption) with yt-dlp."""
from __future__ import annotations

import logging
from dataclasses import dataclass, field
from pathlib import Path

from . import config

log = logging.getLogger(__name__)

VIDEO_EXT = {".mp4", ".mov", ".mkv", ".webm", ".m4v", ".3gp"}
IMAGE_EXT = {".jpg", ".jpeg", ".png", ".webp", ".heic"}


@dataclass
class Media:
    caption: str = ""
    creator: str = ""
    title: str = ""
    videos: list[Path] = field(default_factory=list)
    images: list[Path] = field(default_factory=list)
    download_error: str = ""


def download(url: str, workdir: Path) -> Media:
    import yt_dlp

    media = Media()
    opts = {
        "outtmpl": str(workdir / "dl_%(autonumber)s.%(ext)s"),
        "format": "best[ext=mp4][height<=1080]/best[height<=1080]/best",
        "quiet": True,
        "no_warnings": True,
        "noplaylist": False,  # carousels come as playlists
        "playlist_items": "1-10",
        "writethumbnail": False,
        "socket_timeout": 30,
        "retries": 2,
    }
    if config.YTDLP_COOKIES_FILE:
        opts["cookiefile"] = config.YTDLP_COOKIES_FILE
    if config.YTDLP_COOKIES_BROWSER:
        opts["cookiesfrombrowser"] = (config.YTDLP_COOKIES_BROWSER,)

    try:
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=True)
    except Exception as e:  # yt-dlp raises many types
        log.warning("yt-dlp failed for %s: %s", url, e)
        media.download_error = str(e)
        return media

    entries = info.get("entries") or [info]
    first = next((e for e in entries if e), info)
    media.caption = info.get("description") or first.get("description") or ""
    media.creator = (
        info.get("uploader") or info.get("channel") or first.get("uploader") or first.get("channel") or ""
    )
    media.title = info.get("title") or ""

    for p in sorted(workdir.glob("dl_*")):
        ext = p.suffix.lower()
        if ext in VIDEO_EXT:
            media.videos.append(p)
        elif ext in IMAGE_EXT:
            media.images.append(p)
    return media


def classify_uploads(paths: list[Path]) -> Media:
    media = Media()
    for p in paths:
        ext = p.suffix.lower()
        if ext in VIDEO_EXT:
            media.videos.append(p)
        elif ext in IMAGE_EXT:
            media.images.append(p)
    return media
