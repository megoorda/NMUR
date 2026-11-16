# ReelNotes (NMUR)

Share an Instagram reel (or a Facebook/TikTok/YouTube post, screen recording or screenshots) to **ReelNotes**.
The app turns it into a searchable note: summary, category, links, steps, roadmap, facts and tools.

```
Instagram --Share--> ReelNotes app (phone) --Tailscale--> server on your Windows PC
                                                           ├─ yt-dlp downloads the reel + caption
                                                           ├─ Gemini (free): watches the whole video
                                                           └─ Claude (your Pro plan): GPU speech-to-text + video frames
```

## 1. Set up the PC server (Windows, once)

1. Download this repo on the PC (green **Code** button → **Download ZIP**, then unzip it), or `git clone` it.
2. Open **PowerShell** in the `server` folder and run:
   ```
   powershell -ExecutionPolicy Bypass -File setup.ps1
   ```
   This installs Python, ffmpeg and the libraries, then creates `server\.env` with a random **token**.
3. Get a free Gemini key at <https://aistudio.google.com/apikey> and paste it into `server\.env` after `GEMINI_API_KEY=`.
4. Optional, for the **Claude** option: install Claude Code (<https://claude.com/claude-code>), run `claude` once in a terminal, and log in with your Pro account.
5. Double-click **`server\start.bat`**. Leave the window open; the server listens on port 8787.

## 2. Connect the phone with Tailscale (once)

1. Install **Tailscale** on the PC (<https://tailscale.com/download>) and on the S23 Ultra (Play Store). Sign in with the same account on both.
2. In the Tailscale app on the PC, copy the PC's address (looks like `100.x.y.z`).

## 3. Install the app

1. On the phone, open this repo's **Releases** page on GitHub and download the newest `ReelNotes-N.apk`.
2. Open the file and allow "install unknown apps" for your browser when asked.
3. Open ReelNotes → ⚙️ **Settings**:
   - Server address: `http://100.x.y.z:8787`
   - Token: the `REELNOTES_TOKEN` value from `server\.env`
   - Choose **Gemini** or **Claude**, then tap **Test connection** and **Save**.

Each new build installs as an update, and your notes are kept.

## Using it

- Instagram → **Share** on a reel → **ReelNotes: summarize**. You return to Instagram right away and get a notification when the note is ready.
- If the PC is off, the item waits and processes automatically later.
- In a note you can tick off steps and roadmap items, open links, star it, export it as Markdown, or re-run it with the other AI.
- Links that didn't respond when checked are shown with a ⚠️.

## Instagram login (if downloads fail)

Instagram sometimes blocks anonymous downloads. Fixes, easiest first:

1. `start.bat` already updates yt-dlp on every start. Try restarting it.
2. Log in to Instagram in **Firefox** on the PC, then add `YTDLP_COOKIES_BROWSER=firefox` to `server\.env`.
3. Or export cookies to a `cookies.txt` file with a browser extension and set `YTDLP_COOKIES_FILE=C:\path\cookies.txt`.
4. Fallback that always works: screen-record the reel on the phone and share the recording to ReelNotes.

Note: downloading Instagram content is against Instagram's terms. This is fine for personal use, but the download step must be replaced (for example, screen recordings only) before publishing on the Play Store.

## Settings in `server\.env`

| Setting | Meaning |
|---|---|
| `REELNOTES_TOKEN` | Password the app must send |
| `GEMINI_API_KEY`, `GEMINI_MODEL` | Gemini key and model (default `gemini-flash-latest`) |
| `CLAUDE_MODEL` | Optional model for the Claude CLI (empty = your default) |
| `WHISPER_MODEL`, `WHISPER_DEVICE` | Speech-to-text model; `auto` uses the NVIDIA GPU, falls back to CPU |

## Project layout

- `android/`: Kotlin + Jetpack Compose app (share target, Room database, WorkManager)
- `server/`: Python FastAPI server
- `.github/workflows/android.yml`: builds the APK and publishes a release on every push to `main`
