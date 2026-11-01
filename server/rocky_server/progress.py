"""Turn a unified.py run directory into the progress the Android app shows.

The app has four stages (see app/.../progress/JobProgress.kt). The weights
below must match the app's Stage weights so the server's percent and the
app's percent agree.
"""

from __future__ import annotations

import json
from pathlib import Path

APP_STAGES = ("SEPARATE", "TRANSCRIBE", "BEATS", "ARRANGE")
WEIGHTS = {"SEPARATE": 0.10, "TRANSCRIBE": 0.75, "BEATS": 0.05, "ARRANGE": 0.10}

TRANSCRIBE_STEMS = ("drums", "bass", "guitar", "piano")

# unified.py stage name -> app stage. Anything not listed (TAB, MIDI-GPT,
# assembly, PDF publication) counts as ARRANGE.
PIPELINE_TO_APP = {
    "demucs": "SEPARATE",
    **{f"muscriptor_{s}": "TRANSCRIBE" for s in TRANSCRIBE_STEMS},
    "merge_stems": "BEATS",
    "beat_this": "BEATS",
    "align": "BEATS",
}


def read_json(path: Path) -> dict | None:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None


def last_progress_line(path: Path) -> dict | None:
    """Last complete JSON line of a MuScriptor progress.jsonl file."""
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError:
        return None
    for line in reversed(lines):
        try:
            return json.loads(line)
        except ValueError:
            continue  # a line being written right now
    return None


def transcription_chunks(run: Path, completed_stages: list[str]) -> tuple[int, int]:
    """(chunks done, chunks total) summed over the four transcribed stems."""
    per_stem_total = 0
    done = 0
    for stem in TRANSCRIBE_STEMS:
        line = last_progress_line(run / "stem_midi" / f"{stem}.progress.jsonl")
        if line:
            per_stem_total = max(per_stem_total, int(line.get("total") or 0))
        if f"muscriptor_{stem}" in completed_stages:
            done += int(line.get("total") or 0) if line else 0
        elif line:
            done += int(line.get("completed") or 0)
    total = per_stem_total * len(TRANSCRIBE_STEMS)
    return min(done, total), total


def current_app_stage(completed: list[str]) -> str:
    """The app stage of the first unfinished pipeline stage."""
    done = set(completed)
    if "demucs" not in done:
        return "SEPARATE"
    if not all(f"muscriptor_{s}" in done for s in TRANSCRIBE_STEMS):
        return "TRANSCRIBE"
    if not {"merge_stems", "beat_this", "align"} <= done:
        return "BEATS"
    return "ARRANGE"


def arrange_steps(manifest: dict) -> tuple[int, int]:
    """Rough step count for ARRANGE: TAB, generation, assembly and PDF."""
    completed = manifest.get("completed", [])
    names = [n for n in completed if n not in PIPELINE_TO_APP]
    total = 5  # guitar_tab, bass_tab, midigpt, normalize_generated, assemble
    total += 2 * len([p for p in manifest.get("generated_targets", []) if p in ("guitar", "bass")])
    total += 1  # PDF publication
    done = len(names) + (1 if manifest.get("score_publication") == "complete" else 0)
    return min(done, total), total


def overall_fraction(stage: str, completed: int, total: int) -> float:
    before = 0.0
    for s in APP_STAGES:
        if s == stage:
            break
        before += WEIGHTS[s]
    frac = 0.0 if total <= 0 else max(0.0, min(1.0, completed / total))
    return before + WEIGHTS[stage] * frac


def run_progress(run: Path, elapsed_sec: float | None) -> dict:
    """Progress fields for GET /jobs/{id} while the pipeline is running."""
    manifest = read_json(run / "manifest.json") or {"completed": []}
    completed = manifest.get("completed", [])
    stage = current_app_stage(completed)
    if stage == "TRANSCRIBE":
        done, total = transcription_chunks(run, completed)
    elif stage == "ARRANGE":
        done, total = arrange_steps(manifest)
    else:
        done, total = 0, 1
    fraction = overall_fraction(stage, done, total)
    eta = None
    # Like MuScriptor, give no estimate until the first chunk has finished.
    transcribe_started = stage != "SEPARATE" and not (stage == "TRANSCRIBE" and done == 0)
    if elapsed_sec is not None and transcribe_started and fraction >= 0.05:
        eta = int(round(elapsed_sec * (1 - fraction) / fraction))
    return {"stage": stage, "completed": done, "total": total,
            "fraction": round(fraction, 4), "eta_sec": eta}


def result_files(run: Path) -> dict:
    """Downloadable files of a finished run, as paths relative to the run.

    Reads scores/score_outputs.json (written by publish_scores.py). MIDI for
    each part: piano/drums from scores/, guitar/bass from their TAB folders.
    """
    contract = read_json(run / "scores" / "score_outputs.json")
    if contract is None:
        raise FileNotFoundError("scores/score_outputs.json is missing")
    manifest = read_json(run / "manifest.json") or {}
    midi_for = {"piano": "scores/piano.mid", "drums": "scores/drums.mid",
                "guitar": "guitar/guitar.mid", "bass": "bass/bass.mid"}
    parts = {}
    for part, info in contract.get("instruments", {}).items():
        entry = {"pdf": info["pdf"], "pages": info.get("pages")}
        midi = midi_for.get(part)
        if part == "guitar" and not (run / midi).is_file() and (run / "guitar/skyline_lead_rhythm.mid").is_file():
            midi = "guitar/skyline_lead_rhythm.mid"
        if midi and (run / midi).is_file():
            entry["midi"] = midi
        parts[part] = entry
    return {
        "full_band": {"pdf": contract["full_band"]["pdf"], "pages": contract["full_band"].get("pages"),
                      "midi": contract.get("full_score_midi", "full_score.mid")},
        "instruments": parts,
        "original_parts": manifest.get("original_parts", []),
        "generated_parts": manifest.get("generated_targets", []),
    }


def allowed_paths(result: dict) -> set[str]:
    paths = {result["full_band"]["pdf"], result["full_band"]["midi"]}
    for entry in result["instruments"].values():
        paths.add(entry["pdf"])
        if "midi" in entry:
            paths.add(entry["midi"])
    return paths
