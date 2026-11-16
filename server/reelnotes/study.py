"""Prompts and schemas for 'Ask AI about my notes' and quiz generation."""
from __future__ import annotations

ASK_SCHEMA = {
    "type": "object",
    "properties": {
        "answer": {"type": "string", "description": "Helpful answer in plain text; use short lists where useful"},
        "used_note_ids": {"type": "array", "items": {"type": "integer"}, "description": "ids of the notes the answer relies on"},
    },
    "required": ["answer", "used_note_ids"],
}

QUIZ_SCHEMA = {
    "type": "object",
    "properties": {
        "questions": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "question": {"type": "string"},
                    "options": {"type": "array", "items": {"type": "string"}, "description": "exactly 4 options"},
                    "answer_index": {"type": "integer", "description": "0-3, index of the correct option"},
                    "explanation": {"type": "string"},
                },
                "required": ["question", "options", "answer_index", "explanation"],
            },
        }
    },
    "required": ["questions"],
}


def ask_prompt(question: str, notes: str, history: list[dict]) -> str:
    convo = "\n".join(f"{m.get('role', 'user').upper()}: {m.get('text', '')}" for m in history[-8:])
    return f"""You are the user's personal knowledge assistant. Their library holds notes made from social-media
reels they saved (summaries, links, steps, roadmaps, tools). Answer the question using ONLY these notes.
- Mention concrete names, links and steps from the notes.
- If the notes don't contain the answer, say so plainly and suggest what to search for.
- Put the ids of every note you used in used_note_ids.

NOTES:
{notes}

{('EARLIER CONVERSATION:' + chr(10) + convo) if convo else ''}

QUESTION: {question}
"""


def quiz_prompt(note: str, count: int) -> str:
    return f"""Write {count} multiple-choice questions that help someone remember the most useful, practical
information in this saved note (tools, links/sites, steps, key facts). Each question has exactly 4 options and
one correct answer; wrong options must be plausible. Keep questions short. Write in English.

NOTE:
{note}
"""
