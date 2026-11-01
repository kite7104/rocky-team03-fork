import pytest
from fastapi.testclient import TestClient

from rocky_server.app import create_app
from rocky_server.jobs import JobStore

from fake_pipeline import FakeRunner


@pytest.fixture
def setup(tmp_path, monkeypatch):
    monkeypatch.delenv("ROCKY_API_KEY", raising=False)

    def make(runner=None, key=None):
        if key:
            monkeypatch.setenv("ROCKY_API_KEY", key)
        store = JobStore(tmp_path / "app", tmp_path / "runs", tmp_path / "input")
        runner = runner or FakeRunner()
        app = create_app(store, runner, start_worker=False)
        return TestClient(app), store, runner, app.state.worker
    return make


def upload(client, name="song.wav", requested="piano,guitar", headers=None, data=b"RIFF....WAVE"):
    return client.post("/jobs", files={"file": (name, data, "audio/wav")},
                       data={"requested": requested, "title": "Immigrant Song"}, headers=headers or {})


def test_upload_queue_run_and_download(setup):
    client, store, runner, worker = setup()
    r = upload(client)
    assert r.status_code == 201
    job_id = r.json()["id"]
    s = client.get(f"/jobs/{job_id}").json()
    assert (s["state"], s["queue_position"], s["stage"], s["eta_sec"]) == ("queued", 1, "SEPARATE", None)
    assert client.get(f"/jobs/{job_id}/result").status_code == 409

    worker.run(store.get(job_id))
    assert runner.calls == [("midi", job_id, f"{job_id}.wav", ("piano", "guitar")), ("pdf", job_id)]
    s = client.get(f"/jobs/{job_id}").json()
    assert (s["state"], s["fraction"]) == ("complete", 1.0)

    result = client.get(f"/jobs/{job_id}/result").json()
    assert result["title"] == "Immigrant Song"
    assert result["full_band"]["pdf"] == "scores/full_band.pdf"
    f = client.get(f"/jobs/{job_id}/files/scores/full_band.pdf")
    assert f.status_code == 200 and f.content == b"%PDF-full"
    assert f.headers["content-type"] == "application/pdf"
    assert client.get(f"/jobs/{job_id}/files/guitar/guitar.mid").status_code == 200


def test_only_listed_files_can_be_downloaded(setup):
    client, store, _, worker = setup()
    job_id = upload(client).json()["id"]
    worker.run(store.get(job_id))
    for rel in ("manifest.json", "../app/" + job_id + ".json", "stems/vocals.wav"):
        assert client.get(f"/jobs/{job_id}/files/{rel}").status_code == 404


def test_failure_reports_pipeline_error(setup):
    client, store, _, worker = setup(FakeRunner(fail_stage="midi"))
    job_id = upload(client).json()["id"]
    worker.run(store.get(job_id))
    s = client.get(f"/jobs/{job_id}").json()
    assert s["state"] == "failed"
    assert "muscriptor_drums" in s["error"]


def test_pdf_failure_is_a_failed_job(setup):
    client, store, _, worker = setup(FakeRunner(fail_stage="pdf"))
    job_id = upload(client).json()["id"]
    worker.run(store.get(job_id))
    assert client.get(f"/jobs/{job_id}").json()["state"] == "failed"


def test_rerun_skips_finished_midi_stage(setup):
    client, store, runner, worker = setup(FakeRunner(fail_stage="pdf"))
    job_id = upload(client).json()["id"]
    worker.run(store.get(job_id))
    runner.fail_stage = None
    runner.calls.clear()
    worker.run(store.get(job_id))
    assert runner.calls == [("pdf", job_id)]
    assert store.get(job_id).state == "complete"


@pytest.mark.parametrize("name,requested,code", [
    ("song.txt", "piano", 415),
    ("song.wav", "piano,keys", 422),
    ("song.wav", "piano,piano", 422),
])
def test_bad_uploads_are_rejected(setup, name, requested, code):
    client, store, *_ = setup()
    assert upload(client, name=name, requested=requested).status_code == code
    assert store.all() == []


def test_empty_requested_keeps_transcribed_lineup(setup):
    client, store, *_ = setup()
    job_id = upload(client, requested="").json()["id"]
    assert store.get(job_id).requested == []


def test_api_key(setup):
    client, *_ = setup(key="secret")
    assert upload(client).status_code == 401
    assert upload(client, headers={"X-Rocky-Key": "secret"}).status_code == 201
    assert client.get("/health").status_code == 200


def test_unknown_job_is_404(setup):
    client, *_ = setup()
    assert client.get("/jobs/000000000000").status_code == 404
    assert client.get("/jobs/../etc").status_code == 404


def test_restart_requeues_interrupted_jobs(setup):
    client, store, _, worker = setup()
    job_id = upload(client).json()["id"]
    store.update(store.get(job_id), state="running")
    worker.thread.run = lambda: None  # start() without a live loop
    worker.start()
    assert store.get(job_id).state == "queued"


def test_complete_job_frees_audio(setup):
    client, store, _, worker = setup()
    job_id = upload(client).json()["id"]
    (store.run_dir(job_id) / "stems").mkdir(parents=True)
    (store.run_dir(job_id) / "stems" / "drums.wav").write_bytes(b"x")
    worker.run(store.get(job_id))
    assert not (store.run_dir(job_id) / "stems").exists()
    assert list(store.inputs.iterdir()) == []
    assert client.get(f"/jobs/{job_id}/files/scores/full_band.pdf").status_code == 200
