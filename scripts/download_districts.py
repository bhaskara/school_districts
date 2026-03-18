#!/usr/bin/env python3

import json
import pathlib
import time
import urllib.parse
import urllib.request
from typing import Any

BASE_URL = (
    "https://nces.ed.gov/opengis/rest/services/"
    "K12_School_Locations/SABS_1516/MapServer/0/query"
)

DISTRICTS = {
    # Elementary / K-8 districts
    "alum_rock_union_elementary": "0602310",
    "berryessa_union_elementary": "0604800",
    "cambrian": "0607140",
    "campbell_union": "0607200",
    "cupertino_union": "0610290",
    "evergreen_elementary": "0613140",
    "franklin_mckinley_elementary": "0614370",
    "lakeside_joint": "0620700",
    "los_altos_elementary": "0622650",
    "los_gatos_union_elementary": "0622830",
    "luther_burbank": "0623130",
    "moreland": "0625770",
    "mount_pleasant_elementary": "0626400",
    "mountain_view_whisman": "0626280",
    "oak_grove_elementary": "0627810",
    "orchard_elementary": "0628680",
    "saratoga_union_elementary": "0635910",
    "sunnyvale": "0638460",
    "union_elementary": "0640320",

    # High school districts
    "campbell_union_high": "0607230",
    "east_side_union_high": "0611820",
    "fremont_union_high": "0614430",
    "los_gatos_saratoga_union_high": "0622800",
    "mountain_view_los_altos_union_high": "0626310",

    # Unified districts
    "gilroy_unified": "0615180",
    "milpitas_unified": "0624500",
    "morgan_hill_unified": "0625830",
    "palo_alto_unified": "0629610",
    "san_jose_unified": "0634590",
    "santa_clara_unified": "0635430",
}

OUT_DIR = pathlib.Path("attendance_boundaries")
DISTRICT_DIR = OUT_DIR / "districts"

MERGED_ALL = OUT_DIR / "merged_all.geojson"
MERGED_ELEMENTARY = OUT_DIR / "merged_elementary.geojson"
MERGED_SECONDARY = OUT_DIR / "merged_secondary.geojson"
MANIFEST = OUT_DIR / "manifest.json"

REQUEST_DELAY_SEC = 0.25
TIMEOUT_SEC = 60


def make_query_url(leaid: str) -> str:
    params = {
        "where": f"leaid='{leaid}'",
        "outFields": "*",
        "returnGeometry": "true",
        "f": "geojson",
    }
    return BASE_URL + "?" + urllib.parse.urlencode(params)


def fetch_geojson(leaid: str) -> dict[str, Any]:
    url = make_query_url(leaid)
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": "attendance-boundary-downloader/1.0"
        },
    )
    with urllib.request.urlopen(req, timeout=TIMEOUT_SEC) as resp:
        return json.load(resp)


def level_name(level: Any) -> str:
    # NCES SABS commonly uses:
    # 1 = primary, 2 = middle, 3 = high, 4 = other
    mapping = {
        "1": "primary",
        "2": "middle",
        "3": "high",
        "4": "other",
        1: "primary",
        2: "middle",
        3: "high",
        4: "other",
    }
    return mapping.get(level, str(level) if level is not None else "unknown")


def normalize_feature(feature: dict[str, Any], district_slug: str, leaid: str) -> dict[str, Any]:
    props = feature.setdefault("properties", {})

    props["district_slug"] = district_slug
    props["district_leaid"] = leaid

    # Keep original NCES fields, but also add app-friendly names.
    props["school_name"] = props.get("schnam")
    props["school_id"] = props.get("ncessch")
    props["district_name_raw"] = props.get("SrcName")
    props["school_level_code"] = props.get("level")
    props["school_level"] = level_name(props.get("level"))
    props["source"] = "NCES SABS_1516"

    return feature


def bucket_feature(feature: dict[str, Any]) -> str:
    props = feature.get("properties", {})
    level = str(props.get("school_level_code"))

    if level == "1":
        return "elementary"
    if level in {"2", "3"}:
        return "secondary"
    return "other"


def dedupe_features(features: list[dict[str, Any]]) -> list[dict[str, Any]]:
    seen = set()
    out = []

    for f in features:
        props = f.get("properties", {})
        key = (
            props.get("district_leaid"),
            props.get("school_id"),
        )

        # Fall back in case school_id is missing.
        if key[1] in (None, ""):
            key = (
                props.get("district_leaid"),
                props.get("school_name"),
                json.dumps(f.get("geometry", {}), sort_keys=True),
            )

        if key in seen:
            continue
        seen.add(key)
        out.append(f)

    return out


def write_geojson(path: pathlib.Path, features: list[dict[str, Any]]) -> None:
    fc = {
        "type": "FeatureCollection",
        "features": features,
    }
    with path.open("w", encoding="utf-8") as f:
        json.dump(fc, f, ensure_ascii=False)


def main() -> None:
    DISTRICT_DIR.mkdir(parents=True, exist_ok=True)

    merged_all: list[dict[str, Any]] = []
    merged_elementary: list[dict[str, Any]] = []
    merged_secondary: list[dict[str, Any]] = []

    manifest: dict[str, Any] = {
        "base_url": BASE_URL,
        "districts_total": len(DISTRICTS),
        "district_results": [],
    }

    for district_slug, leaid in DISTRICTS.items():
        print(f"Downloading {district_slug} ({leaid})")
        result = {
            "district_slug": district_slug,
            "leaid": leaid,
            "url": make_query_url(leaid),
            "status": "unknown",
            "feature_count": 0,
            "output_file": str(DISTRICT_DIR / f"{district_slug}.geojson"),
            "error": None,
        }

        try:
            fc = fetch_geojson(leaid)
            features = fc.get("features", [])

            normalized = [
                normalize_feature(f, district_slug, leaid)
                for f in features
            ]

            if not normalized:
                result["status"] = "empty"
                manifest["district_results"].append(result)
                print("  -> empty response")
                time.sleep(REQUEST_DELAY_SEC)
                continue

            write_geojson(DISTRICT_DIR / f"{district_slug}.geojson", normalized)

            merged_all.extend(normalized)
            for feature in normalized:
                bucket = bucket_feature(feature)
                if bucket == "elementary":
                    merged_elementary.append(feature)
                elif bucket == "secondary":
                    merged_secondary.append(feature)

            result["status"] = "ok"
            result["feature_count"] = len(normalized)
            manifest["district_results"].append(result)
            print(f"  -> saved {len(normalized)} features")

        except Exception as e:
            result["status"] = "error"
            result["error"] = str(e)
            manifest["district_results"].append(result)
            print(f"  -> ERROR: {e}")

        time.sleep(REQUEST_DELAY_SEC)

    merged_all = dedupe_features(merged_all)
    merged_elementary = dedupe_features(merged_elementary)
    merged_secondary = dedupe_features(merged_secondary)

    write_geojson(MERGED_ALL, merged_all)
    write_geojson(MERGED_ELEMENTARY, merged_elementary)
    write_geojson(MERGED_SECONDARY, merged_secondary)

    manifest["merged_outputs"] = {
        "merged_all": str(MERGED_ALL),
        "merged_elementary": str(MERGED_ELEMENTARY),
        "merged_secondary": str(MERGED_SECONDARY),
        "merged_all_count": len(merged_all),
        "merged_elementary_count": len(merged_elementary),
        "merged_secondary_count": len(merged_secondary),
    }

    with MANIFEST.open("w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2, ensure_ascii=False)

    print("\nDone.")
    print(f"  wrote {MERGED_ALL}")
    print(f"  wrote {MERGED_ELEMENTARY}")
    print(f"  wrote {MERGED_SECONDARY}")
    print(f"  wrote {MANIFEST}")


if __name__ == "__main__":
    main()