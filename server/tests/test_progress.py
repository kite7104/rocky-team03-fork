from rocky_server.progress import (allowed_paths, current_app_stage, overall_fraction, result_files,
                                  run_progress)

from fake_pipeline import STEMS, write_manifest, write_outputs, write_progress


def test_weights_match_the_app():
    # app/.../progress/JobProgress.kt: SEPARATE .10, TRANSCRIBE .75, BEATS .05, ARRANGE .10
    assert overall_fraction("SEPARATE", 0, 1) == 0
    assert overall_fraction("TRANSCRIBE", 0, 1) == 0.10
    assert abs(overall_fraction("BEATS", 0, 1) - 0.85) < 1e-9
    assert abs(overall_fraction("ARRANGE", 1, 1) - 1.0) < 1e-9


def test_stage_follows_first_unfinished_pipeline_stage():
    assert current_app_stage([]) == "SEPARATE"
    assert current_app_stage(["demucs", "muscriptor_drums"]) == "TRANSCRIBE"
    assert current_app_stage(["demucs", *[f"muscriptor_{s}" for s in STEMS]]) == "BEATS"
    assert current_app_stage(["demucs", *[f"muscriptor_{s}" for s in STEMS],
                              "merge_stems", "beat_this", "align"]) == "ARRANGE"


def test_no_manifest_yet_is_separation_without_eta(tmp_path):
    p = run_progress(tmp_path / "missing", elapsed_sec=10)
    assert p == {"stage": "SEPARATE", "completed": 0, "total": 1, "fraction": 0.0, "eta_sec": None}


def test_transcription_counts_chunks_of_all_four_stems(tmp_path):
    write_manifest(tmp_path, ["demucs", "muscriptor_drums"])
    write_progress(tmp_path, "drums", 30, 30)
    write_progress(tmp_path, "bass", 15, 30)
    p = run_progress(tmp_path, elapsed_sec=100)
    assert (p["stage"], p["completed"], p["total"]) == ("TRANSCRIBE", 45, 120)
    assert abs(p["fraction"] - (0.10 + 0.75 * 45 / 120)) < 1e-3
    assert p["eta_sec"] == round(100 * (1 - p["fraction"]) / p["fraction"])


def test_half_written_progress_line_is_ignored(tmp_path):
    write_manifest(tmp_path, ["demucs"])
    write_progress(tmp_path, "drums", 3, 30)
    with (tmp_path / "stem_midi" / "drums.progress.jsonl").open("a") as f:
        f.write('{"stage": "transc')
    assert run_progress(tmp_path, 5)["completed"] == 3


def test_eta_waits_for_first_chunk(tmp_path):
    write_manifest(tmp_path, ["demucs"])
    assert run_progress(tmp_path, elapsed_sec=40)["eta_sec"] is None


def test_result_lists_pdf_and_midi_per_part(tmp_path):
    write_outputs(tmp_path)
    r = result_files(tmp_path)
    assert r["full_band"] == {"pdf": "scores/full_band.pdf", "pages": 5, "midi": "full_score.mid"}
    assert r["instruments"]["guitar"] == {"pdf": "scores/guitar.pdf", "pages": 2, "midi": "guitar/guitar.mid"}
    assert r["instruments"]["piano"]["midi"] == "scores/piano.mid"
    assert r["generated_parts"] == ["piano"]
    assert "manifest.json" not in allowed_paths(r)


def test_arrange_counts_only_stages_this_song_runs(tmp_path):
    from rocky_server.progress import arrange_steps
    base = ["demucs", *[f"muscriptor_{s}" for s in STEMS], "merge_stems", "beat_this", "align"]
    # no bass in the song, user asked for a bass: bass TAB runs on the generated part only
    m = {"completed": base + ["guitar_tab", "midigpt", "normalize_generated"],
         "original_parts": ["drums", "guitar"], "generated_targets": ["bass"]}
    assert arrange_steps(m) == (3, 7)  # guitar_tab midigpt normalize combine gen_bass assemble + pdf
    m["completed"] += ["combine_generated_strings", "generated_bass_tab", "assemble"]
    m["score_publication"] = "complete"
    assert arrange_steps(m) == (7, 7)
