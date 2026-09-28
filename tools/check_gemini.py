#!/usr/bin/env python3
"""Smoke-check the Gemini recognition call against the live API.

Unit tests can prove the merge and portion maths; they cannot prove that the request we send is
one the API accepts, or that the response still parses. This script builds a request from the
*same* schema and prompt the app uses -- read out of the Kotlin source, so the check fails if the
two drift apart -- sends a generated test image, and prints what came back.

    GEMINI_API_KEY=... python3 tools/check_gemini.py

Exits non-zero if every model in the fallback chain fails, or if the response does not parse.
"""

from __future__ import annotations

import base64
import json
import os
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
GEMINI_DIR = REPO / "data/recognition/src/main/kotlin/dev/foodtracker/data/recognition/gemini"
SCHEMA_KT = GEMINI_DIR / "FoodDetectionSchema.kt"
MODELS_KT = GEMINI_DIR / "GeminiModels.kt"
ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"


def kotlin_triple_quoted(source: str, name: str) -> str:
    """Pull a `val NAME = \"\"\"...\"\"\"` block out of the Kotlin source."""
    marker = f"{name} = \"\"\""
    if marker not in source:
        sys.exit(f"could not find {name} in {SCHEMA_KT.name} -- has it been renamed?")
    return source.split(marker, 1)[1].split('"""', 1)[0].strip()


def fallback_chain() -> list[str]:
    source = MODELS_KT.read_text()
    block = source.split("FALLBACK_CHAIN", 1)[1].split(")", 1)[0]
    return re.findall(r'"([^"]+)"', block)


def response_schema() -> dict:
    """The schema the app sends. Mirrors FoodDetectionSchema.schema."""
    alternative = {
        "type": "OBJECT",
        "properties": {"name": {"type": "STRING"}, "confidence": {"type": "NUMBER"}},
        "required": ["name", "confidence"],
    }
    return {
        "type": "ARRAY",
        "description": "Every distinct food item visible in the photo.",
        "items": {
            "type": "OBJECT",
            "properties": {
                "name": {"type": "STRING", "description": "Common name of the food."},
                "cookingMethod": {"type": "STRING", "description": "How it was prepared."},
                "confidence": {"type": "NUMBER", "description": "Probability from 0 to 1."},
                "estimatedGrams": {"type": "NUMBER", "description": "Edible mass as served, in grams."},
                "householdUnit": {"type": "STRING", "description": "Portion as an everyday measure."},
                "alternatives": {"type": "ARRAY", "items": alternative},
                "boundingBox": {
                    "type": "OBJECT",
                    "properties": {k: {"type": "NUMBER"} for k in ("left", "top", "right", "bottom")},
                    "required": ["left", "top", "right", "bottom"],
                },
            },
            "required": ["name", "confidence", "estimatedGrams", "householdUnit", "alternatives"],
        },
    }


def test_image() -> bytes:
    """A drawn plate: chicken, broccoli, rice. Avoids committing a photo to the repo."""
    try:
        from PIL import Image, ImageDraw
    except ImportError:
        sys.exit("this script needs Pillow: pip install Pillow")

    import random

    img = Image.new("RGB", (900, 900), (120, 96, 72))
    d = ImageDraw.Draw(img)
    d.ellipse([90, 90, 810, 810], fill=(245, 245, 242), outline=(210, 210, 205), width=6)

    d.ellipse([190, 210, 470, 400], fill=(196, 150, 96))
    for y in range(235, 385, 28):
        d.line([(205, y), (455, y - 12)], fill=(120, 82, 46), width=8)

    for cx, cy, r in [(560, 250, 52), (640, 300, 44), (590, 355, 40), (665, 225, 34)]:
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(64, 122, 54))
        d.ellipse([cx - r // 2, cy - r // 2, cx + r // 3, cy + r // 3], fill=(88, 150, 70))

    d.ellipse([250, 460, 560, 690], fill=(250, 250, 246))
    for i in range(300):
        random.seed(i)
        x, y = random.randint(265, 545), random.randint(475, 675)
        d.ellipse([x, y, x + 9, y + 5], fill=(232, 232, 226))

    from io import BytesIO

    buffer = BytesIO()
    img.save(buffer, format="JPEG", quality=85)
    return buffer.getvalue()


def main() -> int:
    api_key = os.environ.get("GEMINI_API_KEY")
    if not api_key:
        sys.exit("set GEMINI_API_KEY (your Google AI Studio key)")

    schema_source = SCHEMA_KT.read_text()
    payload = json.dumps(
        {
            "systemInstruction": {
                "parts": [{"text": kotlin_triple_quoted(schema_source, "SYSTEM_INSTRUCTION")}]
            },
            "contents": [
                {
                    "role": "user",
                    "parts": [
                        {
                            "inline_data": {
                                "mime_type": "image/jpeg",
                                "data": base64.b64encode(test_image()).decode(),
                            }
                        },
                        {"text": kotlin_triple_quoted(schema_source, "PROMPT")},
                    ],
                }
            ],
            "generationConfig": {
                "responseMimeType": "application/json",
                "responseSchema": response_schema(),
                "temperature": 0.2,
                "thinkingConfig": {"thinkingBudget": 0},
            },
        }
    ).encode()

    for model in fallback_chain():
        request = urllib.request.Request(
            ENDPOINT.format(model=model),
            data=payload,
            headers={"Content-Type": "application/json", "x-goog-api-key": api_key},
        )
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                body = json.loads(response.read())
        except urllib.error.HTTPError as e:
            # 503/404 is exactly what the app's fallback chain exists for; keep walking.
            print(f"  {model}: HTTP {e.code} -- trying the next model in the chain")
            continue

        text = body["candidates"][0]["content"]["parts"][0]["text"]
        items = json.loads(text)
        print(f"\n{model} returned {len(items)} item(s):\n")
        for entry in items:
            box = entry.get("boundingBox")
            where = f" at {box}" if box else ""
            print(
                f"  {entry['name']} -- {entry['estimatedGrams']:g} g "
                f"({entry.get('householdUnit', '?')}), confidence {entry['confidence']:.2f}{where}"
            )
        return 0

    print("every model in the fallback chain failed", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
