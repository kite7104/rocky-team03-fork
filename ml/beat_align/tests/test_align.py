"""align.py 단위 테스트. Beat This! 모델 없이 가짜 박 배열로 검증한다.

Written with Claude (AI); reviewed by Soobin Jo.
"""
import sys
from pathlib import Path

import numpy as np
import pretty_midi
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from align import align_midi, align_pretty_midi, compute_beat_offset, estimate_bpm  # noqa: E402


def make_midi(notes):
    """notes: [(pitch, start, end)] → 피아노 한 트랙짜리 PrettyMIDI"""
    pm = pretty_midi.PrettyMIDI()
    inst = pretty_midi.Instrument(program=0)
    for pitch, s, e in notes:
        inst.notes.append(pretty_midi.Note(velocity=100, pitch=pitch, start=s, end=e))
    pm.instruments.append(inst)
    return pm


def test_bpm_from_even_beats():
    beats = np.arange(0, 10, 0.5)  # 0.5 s 간격 = 120 BPM
    bpm, _ = estimate_bpm(beats)
    assert bpm == pytest.approx(120.0)


def test_bpm_ignores_outlier_intervals():
    beats = np.array([0.0, 0.5, 1.0, 1.5, 1.6, 2.1, 2.6, 5.0])  # 0.1 s, 2.4 s 간격은 제외
    bpm, _ = estimate_bpm(beats)
    assert bpm == pytest.approx(120.0)


def test_no_valid_interval_raises():
    with pytest.raises(ValueError):
        estimate_bpm(np.array([0.0, 0.05, 0.1]))


def test_first_downbeat_becomes_bar_start():
    beats = np.arange(0, 10, 0.5)
    downbeats = beats[1::4]  # 두 번째 박부터 다운비트
    assert compute_beat_offset(beats, downbeats) == 3
    assert compute_beat_offset(beats, []) == 0


def test_notes_snap_to_sixteenth_grid():
    beats = np.arange(0, 10, 0.5)
    pm = make_midi([(60, 0.53, 0.98)])  # 박에서 살짝 어긋난 음
    out, r = align_pretty_midi(pm, beats, beats[::4])
    note = out.instruments[0].notes[0]
    sixteenth = 60.0 / r.bpm / 4
    assert note.start / sixteenth == pytest.approx(round(note.start / sixteenth))
    assert note.start == pytest.approx(0.5)


def test_tempo_rewritten_to_detected_bpm():
    beats = np.arange(0, 20, 60 / 91)  # 91 BPM
    out, r = align_pretty_midi(make_midi([(60, 1.0, 2.0)]), beats, beats[::4])
    assert r.bpm == pytest.approx(91.0, rel=1e-3)
    _, tempi = out.get_tempo_changes()
    assert tempi[0] == pytest.approx(91.0, rel=1e-3)
    ts = out.time_signature_changes[0]
    assert (ts.numerator, ts.denominator) == (4, 4)


def test_zero_length_note_gets_sixteenth():
    beats = np.arange(0, 10, 0.5)
    out, r = align_pretty_midi(make_midi([(60, 1.0, 1.01)]), beats, beats[::4])
    note = out.instruments[0].notes[0]
    assert note.end - note.start == pytest.approx(60.0 / r.bpm / 4)


def test_instruments_and_note_count_kept():
    pm = make_midi([(60, 0.5, 1.0), (64, 1.0, 1.5)])
    drums = pretty_midi.Instrument(program=0, is_drum=True, name="Drums")
    drums.notes.append(pretty_midi.Note(velocity=90, pitch=36, start=0.5, end=0.6))
    pm.instruments.append(drums)
    out, r = align_pretty_midi(pm, np.arange(0, 10, 0.5), [])
    assert r.note_count == 3
    assert [i.is_drum for i in out.instruments] == [False, True]


def test_too_few_beats_raises():
    with pytest.raises(ValueError):
        align_pretty_midi(make_midi([(60, 0, 1)]), [1.0], [])


def test_align_midi_file_roundtrip(tmp_path):
    src = tmp_path / "in.mid"
    make_midi([(60, 0.52, 1.0)]).write(str(src))
    dst = tmp_path / "out" / "aligned.mid"
    beats = list(np.arange(0, 10, 0.5))
    r = align_midi(src, dst, beats=beats, downbeats=beats[::4])
    assert dst.exists() and r.note_count == 1


def test_align_midi_needs_beats_or_audio(tmp_path):
    src = tmp_path / "in.mid"
    make_midi([(60, 0, 1)]).write(str(src))
    with pytest.raises(ValueError):
        align_midi(src, tmp_path / "o.mid")


def test_notes_outside_beat_range():
    beats = np.arange(1.0, 5.0, 0.5)  # 박은 1~4.5 s 구간에만 있음
    pm = make_midi([(60, 0.0, 0.5), (62, 6.0, 6.5)])
    out, _ = align_pretty_midi(pm, beats, beats[::4])
    starts = [n.start for n in out.instruments[0].notes]
    assert starts[0] == 0.0           # 첫 박 이전 → 0초로 고정
    assert starts[1] > max(beats) - 1  # 마지막 박 이후 → 같은 템포로 연장


def test_beats_json_roundtrip(tmp_path):
    from align import load_beats, save_beats
    path = tmp_path / "b.json"
    save_beats(path, [0.5, 1.0], [0.5])
    assert load_beats(path) == ([0.5, 1.0], [0.5])


def test_cli_with_saved_beats(tmp_path, capsys):
    from align import main, save_beats
    src, beats_json, dst = tmp_path / "in.mid", tmp_path / "b.json", tmp_path / "out.mid"
    make_midi([(60, 0.52, 1.0)]).write(str(src))
    beats = list(np.arange(0, 10, 0.5))
    save_beats(beats_json, beats, beats[::4])
    main(["--midi", str(src), "--beats", str(beats_json), "--out", str(dst)])
    assert dst.exists()
    assert "BPM 120.00" in capsys.readouterr().out


# ----------------------------------------------------------------------
# 일괄 처리
# ----------------------------------------------------------------------
def _write_song(root, name, nested=True):
    """MuScriptor 구조(곡/muscriptor_large.mid) 또는 평평한 구조(곡.mid)로 MIDI 생성"""
    path = root / name / "muscriptor_large.mid" if nested else root / f"{name}.mid"
    path.parent.mkdir(parents=True, exist_ok=True)
    make_midi([(60, 0.52, 1.0)]).write(str(path))
    return path


def test_song_key_normalizes_names():
    import unicodedata
    from align import song_key
    nfd = unicodedata.normalize("NFD", "흰수염고래 - YB__no_vocals")
    assert song_key(nfd) == "흰수염고래 - YB"


def test_collect_midis_both_layouts(tmp_path):
    from align import collect_midis
    _write_song(tmp_path, "A__no_vocals", nested=True)
    _write_song(tmp_path, "B", nested=False)
    assert set(collect_midis(tmp_path)) == {"A", "B"}


def test_batch_with_beats_dir(tmp_path):
    from align import align_batch, save_beats
    midi_dir, beats_dir, out_dir = tmp_path / "midi", tmp_path / "beats", tmp_path / "out"
    beats = list(np.arange(0, 10, 0.5))
    for name in ["Song A__no_vocals", "Song B__no_vocals", "Only MIDI"]:
        _write_song(midi_dir, name)
    beats_dir.mkdir()
    for name in ["Song A__no_vocals", "Song B", "Only Beats"]:
        save_beats(beats_dir / f"{name}_beats.json", beats, beats[::4])

    report = align_batch(midi_dir, out_dir, beats_dir=beats_dir, log=lambda *_: None)
    assert report.succeeded == ["Song A", "Song B"]
    assert report.midi_only == ["Only MIDI"] and report.source_only == ["Only Beats"]
    assert (out_dir / "Song A_aligned.mid").exists()


def test_batch_continues_after_failure(tmp_path):
    from align import align_batch, save_beats
    midi_dir, beats_dir = tmp_path / "midi", tmp_path / "beats"
    _write_song(midi_dir, "Good"); _write_song(midi_dir, "Bad")
    beats_dir.mkdir()
    save_beats(beats_dir / "Good_beats.json", list(np.arange(0, 10, 0.5)), [0.0])
    save_beats(beats_dir / "Bad_beats.json", [1.0], [])  # 박 1개 → 실패
    report = align_batch(midi_dir, tmp_path / "out", beats_dir=beats_dir, log=lambda *_: None)
    assert report.succeeded == ["Good"] and set(report.failed) == {"Bad"}


def test_batch_with_audio_dir_uses_detector(tmp_path, monkeypatch):
    import align
    midi_dir, audio_dir, out_dir = tmp_path / "midi", tmp_path / "audio", tmp_path / "out"
    _write_song(midi_dir, "Song__no_vocals")
    audio_dir.mkdir(); (audio_dir / "Song.wav").write_bytes(b"")  # 원곡 이름 ↔ __no_vocals MIDI
    beats = list(np.arange(0, 10, 0.5))
    calls = []
    monkeypatch.setattr(align, "detect_beats", lambda p, device="auto": (calls.append(p) or (beats, beats[::4])))
    report = align.align_batch(midi_dir, out_dir, audio_dir=audio_dir, log=lambda *_: None)
    assert report.succeeded == ["Song"] and len(calls) == 1
    assert (out_dir / "Song_beats.json").exists()


def test_batch_needs_exactly_one_source(tmp_path):
    from align import align_batch
    with pytest.raises(ValueError):
        align_batch(tmp_path, tmp_path / "out")


def test_cli_batch_and_mode_errors(tmp_path):
    from align import main, save_beats
    midi_dir, beats_dir, out_dir = tmp_path / "midi", tmp_path / "beats", tmp_path / "out"
    _write_song(midi_dir, "X")
    beats_dir.mkdir()
    beats = list(np.arange(0, 10, 0.5))
    save_beats(beats_dir / "X_beats.json", beats, beats[::4])
    assert main(["--midi-dir", str(midi_dir), "--beats-dir", str(beats_dir), "--out-dir", str(out_dir)]) == 0
    assert (out_dir / "X_aligned.mid").exists()
    with pytest.raises(SystemExit):  # 한 곡/여러 곡 옵션을 섞으면 오류
        main(["--midi", "a.mid", "--midi-dir", str(midi_dir), "--out-dir", str(out_dir), "--beats-dir", str(beats_dir)])
