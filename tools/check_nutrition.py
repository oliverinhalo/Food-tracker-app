#!/usr/bin/env python3
"""Smoke-check the USDA and Open Food Facts integrations against the live APIs.

Mirrors what UsdaClient and OpenFoodFactsClient do -- same endpoints, same fields, same
normalisation -- so it catches an upstream change that unit tests cannot see: an endpoint going
away, a renamed field, or energy arriving in kJ where kcal was expected.

    USDA_API_KEY=... python3 tools/check_nutrition.py
"""

from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

UA = "FoodTracker/0.1 (github.com/oliverinhalo/Food-tracker-app)"
KJ_PER_KCAL = 4.184
SALT_TO_SODIUM = 2.5


def fetch(url: str, timeout: int = 45) -> dict | None:
    request = urllib.request.Request(url, headers={"User-Agent": UA})
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return json.loads(response.read())
    except (urllib.error.HTTPError, urllib.error.URLError, json.JSONDecodeError) as e:
        print(f"    request failed: {e}")
        return None


def atwater(protein: float, carbs: float, fat: float) -> float:
    return protein * 4 + carbs * 4 + fat * 9


def check_usda(api_key: str) -> bool:
    print("USDA FoodData Central")
    if not api_key:
        print("  skipped: no USDA_API_KEY set")
        return True

    query = urllib.parse.urlencode(
        {
            "api_key": api_key,
            "query": "grilled chicken breast",
            "pageSize": 3,
            "dataType": "Foundation,SR Legacy",
        }
    )
    data = fetch(f"https://api.nal.usda.gov/fdc/v1/foods/search?{query}")
    if not data or not data.get("foods"):
        print("  FAIL: no results")
        return False

    ids = {"protein": 1003, "fat": 1004, "carbs": 1005, "kcal": 1008, "kj": 1062}
    ok = False
    for food in data["foods"][:3]:
        values = {n.get("nutrientId"): n.get("value") for n in food.get("foodNutrients", [])}
        kcal = values.get(ids["kcal"])
        source = "kcal"
        if kcal is None and values.get(ids["kj"]) is not None:
            kcal = values[ids["kj"]] / KJ_PER_KCAL
            source = "kJ->kcal"
        if kcal is None:
            continue
        protein = values.get(ids["protein"]) or 0
        carbs = values.get(ids["carbs"]) or 0
        fat = values.get(ids["fat"]) or 0
        print(
            f"  {food['description'][:46]:<48} {kcal:6.1f} kcal/100g ({source})  "
            f"P{protein:.1f} C{carbs:.1f} F{fat:.1f}  atwater={atwater(protein, carbs, fat):.0f}"
        )
        ok = True

    if not ok:
        print("  FAIL: no row carried usable energy")
    return ok


def check_off_barcode() -> bool:
    print("\nOpen Food Facts - barcode lookup")
    fields = "code,product_name,brands,serving_size,serving_quantity,nutriments"
    data = fetch(f"https://world.openfoodfacts.org/api/v2/product/3017624010701.json?fields={fields}")
    if not data or data.get("status") != 1:
        print("  FAIL: barcode lookup did not return a product")
        return False

    product = data["product"]
    n = product.get("nutriments", {})
    kcal = n.get("energy-kcal_100g")
    if kcal is None and n.get("energy-kj_100g") is not None:
        kcal = n["energy-kj_100g"] / KJ_PER_KCAL
    protein = n.get("proteins_100g") or 0
    carbs = n.get("carbohydrates_100g") or 0
    fat = n.get("fat_100g") or 0
    sodium_mg = (n["sodium_100g"] * 1000) if n.get("sodium_100g") is not None else None

    print(f"  {product.get('product_name')} ({product.get('brands')})")
    print(f"    {kcal:.0f} kcal/100g  P{protein} C{carbs} F{fat}  atwater={atwater(protein, carbs, fat):.0f}")
    print(f"    sodium {sodium_mg:.0f} mg/100g" if sodium_mg is not None else "    sodium: absent")
    return kcal is not None


def check_off_search() -> bool:
    """The main host's text search returns 503, which is why the app uses the search service."""
    print("\nOpen Food Facts - text search")

    main_host = "https://world.openfoodfacts.org/api/v2/search?categories_tags_en=yogurts&page_size=1"
    request = urllib.request.Request(main_host, headers={"User-Agent": UA})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            print(f"  note: main host search now returns {response.status} -- it was 503 when this was written")
    except urllib.error.HTTPError as e:
        print(f"  main host search returns {e.code} as expected; using the search service instead")
    except urllib.error.URLError as e:
        print(f"  main host search unreachable ({e}); using the search service instead")

    fields = "code,product_name,brands,nutriments"
    query = urllib.parse.urlencode({"q": "greek yoghurt", "page_size": 5, "fields": fields})
    data = fetch(f"https://search.openfoodfacts.org/search?{query}")
    if not data:
        print("  FAIL: search service unreachable")
        return False

    usable = 0
    for hit in data.get("hits", []):
        n = hit.get("nutriments") or {}
        kcal = n.get("energy-kcal_100g")
        if kcal is None:
            continue
        usable += 1
        print(f"  {hit.get('code')}  {(hit.get('product_name') or '?')[:34]:<36} {kcal:.0f} kcal/100g")

    print(f"  {usable} of {len(data.get('hits', []))} hits carried nutrition (the rest are dropped)")
    return usable > 0


def main() -> int:
    results = [
        check_usda(os.environ.get("USDA_API_KEY", "")),
        check_off_barcode(),
        check_off_search(),
    ]
    if all(results):
        print("\nall nutrition sources reachable and parseable")
        return 0
    print("\nat least one nutrition source failed", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
