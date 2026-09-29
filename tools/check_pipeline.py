#!/usr/bin/env python3
"""End-to-end check: a photo in, a plausible calorie total out.

The two other scripts check the recognition call and the nutrition sources separately. This one
runs the whole chain the way the app does -- Gemini identifies and portions the food, the matcher
picks a database row for each item, and the per-100g values are scaled by the estimated grams --
and asserts the result is sensible.

This is the check that would have caught "steamed broccoli" resolving to steamed corn at 386
kcal/100g, because the failure only shows up once the pieces are joined.

    GEMINI_API_KEY=... USDA_API_KEY=... python3 tools/check_pipeline.py
"""

from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from check_gemini import fallback_chain, response_schema, kotlin_triple_quoted, test_image, SCHEMA_KT  # noqa: E402

UA = "FoodTracker/0.1 (github.com/oliverinhalo/Food-tracker-app)"
KJ_PER_KCAL = 4.184

# Mirrors FoodMatcher. Kept in step with it by the unit tests on the Kotlin side; this script's job
# is to prove the joined-up behaviour against live data, not to re-specify the rules.
COOKED = {"cooked", "boiled", "steamed", "grilled", "fried", "baked", "roasted", "poached",
          "sauteed", "braised", "toasted", "barbecued", "smoked", "stewed"}
RAW = {"raw", "fresh", "uncooked", "dried", "dry"}
DESCRIPTORS = {"hot", "cold", "warm", "large", "small", "medium", "homemade", "mixed"}
FORMS = {"flour", "powder", "dried", "dehydrated", "concentrate", "extract", "oil", "syrup", "juice"}
STOP = {"a", "an", "and", "of", "with", "in", "on", "the", "fresh", "raw", "plain", "cooked",
        "prepared", "style", "homemade", "serving", "portion", "piece", "pieces"}
MIN_SCORE = 0.42

# Physically plausible kcal/100g per category, mirroring FoodCategory and isPlausibleEnergy.
# Order matters exactly as it does in the Kotlin enum: the first keyword hit wins, so a dish word
# like "soup" has to outrank the ingredient it names.
CATEGORY_BOUNDS = [
    (("oil", "butter", "margarine", "ghee", "lard", "mayonnaise", "mayo"), (250, 950)),
    (("sauce", "dressing", "ketchup", "mustard", "gravy", "salsa", "hummus", "dip", "syrup", "jam", "honey"), (0, 750)),
    (("soup", "stew", "broth", "curry", "chowder", "casserole"), (0, 250)),
    (("juice", "soda", "cola", "coffee", "tea", "beer", "wine", "smoothie", "water", "milkshake", "drink"), (0, 300)),
    (("milk", "cream", "buttermilk", "kefir"), (5, 400)),
    (("yoghurt", "yogurt", "quark", "skyr", "cottage cheese", "creme fraiche"), (15, 300)),
    (("cheese", "cheddar", "mozzarella", "parmesan", "feta", "brie", "gouda"), (30, 500)),
    (("bread", "toast", "bagel", "roll", "bun", "croissant", "muffin", "pancake", "waffle", "tortilla", "pita", "naan"), (100, 550)),
    (("cereal", "granola", "muesli", "cornflakes", "oats", "porridge", "oatmeal"), (250, 550)),
    (("rice", "pasta", "spaghetti", "noodle", "couscous", "quinoa", "barley", "bulgur", "risotto", "macaroni"), (40, 220)),
    (("potato", "fries", "chips", "sweet potato", "yam", "cassava", "carrot", "beetroot", "parsnip"), (10, 400)),
    (("bean", "lentil", "chickpea", "pea", "tofu", "tempeh", "edamame"), (20, 420)),
    (("chicken", "beef", "pork", "lamb", "turkey", "duck", "steak", "mince", "bacon", "sausage",
      "ham", "fish", "salmon", "tuna", "cod", "prawn", "shrimp", "meat", "burger", "patty"), (30, 650)),
    (("egg", "omelette", "omelet", "frittata"), (40, 420)),
    (("nut", "almond", "cashew", "walnut", "peanut", "pistachio", "seed", "sesame"), (300, 800)),
    (("apple", "banana", "orange", "berry", "berries", "grape", "melon", "peach", "pear", "mango", "pineapple", "fruit", "avocado"), (10, 400)),
    (("lettuce", "spinach", "kale", "rocket", "arugula", "salad", "cabbage", "greens"), (0, 90)),
    (("broccoli", "cauliflower", "courgette", "zucchini", "pepper", "tomato", "onion", "mushroom",
      "cucumber", "aubergine", "eggplant", "vegetable", "sweetcorn", "corn"), (0, 160)),
    (("chocolate", "biscuit", "cookie", "cake", "crisps", "candy", "sweet", "ice cream", "doughnut", "donut", "brownie"), (100, 700)),
]


def tokens(text):
    words = ["".join(c if c.isalnum() else " " for c in text.lower())][0].split()
    out = []
    for w in words:
        if w in STOP:
            continue
        if len(w) > 3 and w.endswith("ies"):
            w = w[:-3] + "y"
        elif len(w) > 3 and w.endswith("es") and not w.endswith("ses"):
            w = w[:-2]
        elif len(w) > 3 and w.endswith("s") and not w.endswith("ss"):
            w = w[:-1]
        out.append(w)
    return out


def levenshtein(a, b):
    prev = list(range(len(b) + 1))
    for i in range(1, len(a) + 1):
        cur = [i] + [0] * len(b)
        for j in range(1, len(b) + 1):
            cur[j] = min(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + (a[i - 1] != b[j - 1]))
        prev = cur
    return prev[len(b)]


def coverage(query, candidate):
    """Asymmetric, weighted toward covering the query -- mirrors TextSimilarity.coverage."""
    a, b = tokens(query), tokens(candidate)
    if not a or not b:
        return 0.0

    def match(x, y):
        return x == y or (abs(len(x) - len(y)) <= 1 and min(len(x), len(y)) >= 4 and levenshtein(x, y) <= 1)

    fwd = sum(1 for x in a if any(match(x, y) for y in b)) / len(a)
    rev = sum(1 for y in b if any(match(x, y) for x in a)) / len(b)
    return 0.75 * fwd + 0.25 * rev


def plausible(name, kcal):
    lowered = name.lower()
    for keywords, (lo, hi) in CATEGORY_BOUNDS:
        if any(k in lowered for k in keywords):
            return lo <= kcal <= hi
    # No category matched, so there is nothing to judge against; PortionProfile.GENERIC behaves the
    # same way by declining to constrain an unknown food.
    return 0 <= kcal <= 900


def score(query, cooking_method, candidate_name, kcal):
    identity = {t for t in tokens(query) if t not in COOKED | RAW | DESCRIPTORS}
    cand_tokens = set(tokens(candidate_name))
    cand_words = set("".join(c if c.isalpha() else " " for c in candidate_name.lower()).split())

    if identity and not (identity & cand_tokens):
        return 0.0
    if not plausible(query, kcal):
        return 0.0

    s = coverage(query, candidate_name)
    if (cand_words & FORMS) and not (identity & FORMS):
        s -= 0.35
    if cooking_method:
        method = cooking_method.lower()
        wants_cooked = method not in RAW
        if method in cand_words:
            s += 0.20
        elif wants_cooked and (cand_words & COOKED):
            s += 0.25
        elif wants_cooked and (cand_words & RAW):
            s -= 0.25
        elif not wants_cooked and (cand_words & COOKED):
            s -= 0.25
    # FoodMatcher ADDS this when the branded-ness matches what was asked for, which for these
    # generic queries means the unbranded USDA rows. Subtracting it scored every candidate 0.10
    # below the app and turned real matches into "no plausible match".
    return max(0.0, s + 0.05)


def recognise(api_key):
    schema_source = SCHEMA_KT.read_text()
    payload = json.dumps({
        "systemInstruction": {"parts": [{"text": kotlin_triple_quoted(schema_source, "SYSTEM_INSTRUCTION")}]},
        "contents": [{"role": "user", "parts": [
            {"inline_data": {"mime_type": "image/jpeg",
                             "data": __import__("base64").b64encode(test_image()).decode()}},
            {"text": kotlin_triple_quoted(schema_source, "PROMPT")}]}],
        "generationConfig": {"responseMimeType": "application/json", "responseSchema": response_schema(),
                             "temperature": 0.2, "thinkingConfig": {"thinkingBudget": 0}},
    }).encode()

    for model in fallback_chain():
        request = urllib.request.Request(
            f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent",
            data=payload,
            headers={"Content-Type": "application/json", "x-goog-api-key": api_key},
        )
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                body = json.loads(response.read())
        except urllib.error.HTTPError as e:
            print(f"  {model}: HTTP {e.code}, trying the next model")
            continue
        text = body["candidates"][0]["content"]["parts"][0]["text"]
        return model, json.loads(text)
    return None, []


def usda_candidates(query, api_key, limit=25):
    if not api_key:
        return []
    url = ("https://api.nal.usda.gov/fdc/v1/foods/search?"
           + urllib.parse.urlencode({"api_key": api_key, "query": query, "pageSize": limit,
                                     "dataType": "Foundation,SR Legacy"}))
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=60) as r:
            data = json.loads(r.read())
    except (urllib.error.HTTPError, urllib.error.URLError):
        return []

    out = []
    for food in data.get("foods", []):
        values = {n.get("nutrientId"): n.get("value") for n in food.get("foodNutrients", [])}
        kcal = values.get(1008) or (values.get(1062) / KJ_PER_KCAL if values.get(1062) else None)
        if kcal is not None:
            out.append((food["description"], kcal))
    return out


def main() -> int:
    gemini_key = os.environ.get("GEMINI_API_KEY")
    usda_key = os.environ.get("USDA_API_KEY", "")
    if not gemini_key:
        sys.exit("set GEMINI_API_KEY")
    # Without USDA every item finds no candidates, which would otherwise be reported as an
    # implausible calorie total rather than as the configuration problem it is.
    if not usda_key:
        sys.exit("set USDA_API_KEY: without it there are no candidates to match against")

    print("Recognising the test plate...")
    model, items = recognise(gemini_key)
    if not items:
        print("every model in the fallback chain was unavailable", file=sys.stderr)
        return 1

    print(f"  {model} found {len(items)} item(s)\n")
    print(f"  {'food':<28} {'grams':>6}  {'matched database row':<44} {'kcal':>7}")
    print(f"  {'-' * 28} {'-' * 6}  {'-' * 44} {'-' * 7}")

    total = 0.0
    unmatched = []

    for entry in items:
        name = entry["name"]
        grams = float(entry["estimatedGrams"])
        method = entry.get("cookingMethod")

        candidates = usda_candidates(name, usda_key)
        ranked = sorted(
            ((score(name, method, cand, kcal), cand, kcal) for cand, kcal in candidates),
            key=lambda x: -x[0],
        )
        best = next((r for r in ranked if r[0] >= MIN_SCORE), None)

        if best is None:
            unmatched.append(name)
            print(f"  {name[:28]:<28} {grams:>6.0f}  {'(no plausible match -- logs without calories)':<44} {'--':>7}")
            continue

        _, row, per100g = best
        kcal = per100g * grams / 100
        total += kcal
        print(f"  {name[:28]:<28} {grams:>6.0f}  {row[:44]:<44} {kcal:>7.0f}")

    print(f"\n  {'MEAL TOTAL':<28} {'':>6}  {'':<44} {total:>7.0f} kcal")

    # A plate of chicken, rice and broccoli is roughly 400-900 kcal. Outside that, something in the
    # chain is wrong -- which is exactly how the steamed-corn and rice-flour failures showed up.
    if unmatched:
        print(f"\n  note: {len(unmatched)} item(s) had no plausible match: {', '.join(unmatched)}")
    if not 250 <= total <= 1200:
        print(f"\nFAIL: {total:.0f} kcal is not a plausible total for this plate", file=sys.stderr)
        return 1

    print("\npipeline produces a plausible meal total")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
