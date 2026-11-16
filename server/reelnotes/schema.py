"""The structured result every AI provider must return."""

DEFAULT_CATEGORIES = [
    "AI & Engineering",
    "Programming",
    "Free Online Games",
    "Android & Emulators",
    "Tools & Websites",
    "Tech News",
    "Learning & Courses",
    "Productivity",
    "Other",
]

# JSON schema (OpenAPI subset, works with Gemini's response_schema and as a
# description for Claude).
RESULT_SCHEMA = {
    "type": "object",
    "properties": {
        "title": {"type": "string", "description": "Short descriptive title (max ~8 words)"},
        "category": {"type": "string", "description": "Exactly one of the allowed categories"},
        "tags": {"type": "array", "items": {"type": "string"}, "description": "3-8 short lowercase tags"},
        "summary": {"type": "string", "description": "2-4 sentence summary of what the reel teaches or shows"},
        "key_points": {"type": "array", "items": {"type": "string"}},
        "links": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "title": {"type": "string"},
                    "url": {"type": "string", "description": "Full URL starting with https:// . Only URLs/domains actually shown, said or written in the reel/caption"},
                    "description": {"type": "string"},
                },
                "required": ["title", "url", "description"],
            },
        },
        "steps": {"type": "array", "items": {"type": "string"}, "description": "Ordered how-to steps, if the reel has any"},
        "roadmap": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "stage": {"type": "string"},
                    "items": {"type": "array", "items": {"type": "string"}},
                },
                "required": ["stage", "items"],
            },
            "description": "Learning roadmap stages, if the reel presents a roadmap/path",
        },
        "facts": {"type": "array", "items": {"type": "string"}},
        "tools": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "name": {"type": "string"},
                    "description": {"type": "string"},
                    "url": {"type": "string"},
                },
                "required": ["name", "description", "url"],
            },
            "description": "Apps, websites, libraries or products mentioned",
        },
        "link_in_bio": {"type": "boolean", "description": "True if the creator says the link/resource is in their bio, comments or DMs"},
        "language": {"type": "string", "description": "Main spoken/written language of the reel"},
    },
    "required": [
        "title", "category", "tags", "summary", "key_points", "links", "steps",
        "roadmap", "facts", "tools", "link_in_bio", "language",
    ],
}


def build_instructions(categories: list[str], caption: str, creator: str) -> str:
    cats = "\n".join(f"- {c}" for c in categories)
    return f"""You turn short social-media videos (Instagram reels, etc.) into a saved knowledge note.

Analyse ALL available signals: the caption, the spoken audio/transcript, and any text shown on screen
(website names, URLs, tool names, steps, code, lists). On-screen text is often the most important part.

Rules:
- Write the note in English, even if the reel is in another language (keep names/URLs unchanged).
- category must be exactly one of:
{cats}
- links: include every website/URL/domain shown or mentioned. Turn bare domains like "poki.com" into
  "https://poki.com". Never invent URLs you did not see or hear; if a tool is named without a URL, put it in
  "tools" with url "".
- steps / roadmap / facts / tools: fill only when present; otherwise return an empty list.
- Be concrete and useful: someone should be able to act on the note without rewatching the reel.

Creator: {creator or "unknown"}
Caption:
\"\"\"
{caption or "(no caption)"}
\"\"\"
"""
