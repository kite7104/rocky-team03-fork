"""Job records and the single worker that runs songs one at a time.

The SGS workspace has one GPU, so jobs run strictly in order. Records are
JSON files on the shared volume; after an API restart, unfinished jobs are
queued again and unified.py --resume continues from the last finished stage.
"""

from __future__ import annotations

import json
import shutil
import threading
import time
import uuid
from dataclasses import asdict, dataclass, field
from pathlib import Path

from .runners import RunnerError, to_wav

STATES = ("queued", "running", "publishing", "complete", "failed")


@dataclass
class Job:
    id: str
    title: str
    filename: str
    requested: list[str]
    state: str = "queued"
    created_at: float = field(default_factory=time.time)
    started_at: float | None = None
    finished_at: float | None = None
    error: str | None = None


class JobStore:
    def __init__(self, root: Path, runs: Path, inputs: Path) -> None:
        self.root, self.runs, self.inputs = root, runs, inputs
        for d in (root, runs, inputs):
            d.mkdir(parents=True, exist_ok=True)
        self.lock = threading.Lock()

    def path(self, job_id: str) -> Path:
        return self.root / f"{job_id}.json"

    def run_dir(self, job_id: str) -> Path:
        return self.runs / job_id

    def log(self, job_id: str) -> Path:
        return self.root / f"{job_id}.log"

    def save(self, job: Job) -> None:
        tmp = self.path(job.id).with_suffix(".tmp")
        tmp.write_text(json.dumps(asdict(job), ensure_ascii=False, indent=2), encoding="utf-8")
        tmp.replace(self.path(job.id))

    def get(self, job_id: str) -> Job | None:
        try:
            return Job(**json.loads(self.path(job_id).read_text(encoding="utf-8")))
        except (OSError, ValueError, TypeError):
            return None

    def all(self) -> list[Job]:
        jobs = [self.get(p.stem) for p in self.root.glob("*.json")]
        return sorted((j for j in jobs if j), key=lambda j: j.created_at)

    def create(self, title: str, filename: str, requested: list[str]) -> Job:
        job = Job(id=uuid.uuid4().hex[:12], title=title, filename=filename, requested=requested)
        self.save(job)
        return job

    def update(self, job: Job, **changes) -> Job:
        with self.lock:
            for key, value in changes.items():
                setattr(job, key, value)
            self.save(job)
        return job


class Worker:
    """Runs queued jobs in creation order on a background thread."""

    def __init__(self, store: JobStore, runner) -> None:
        self.store, self.runner = store, runner
        self.wake = threading.Event()
        self.thread = threading.Thread(target=self._loop, name="rocky-worker", daemon=True)

    def start(self) -> None:
        # Jobs cut off by a restart are run again; --resume skips finished stages.
        for job in self.store.all():
            if job.state in ("running", "publishing"):
                self.store.update(job, state="queued")
        self.thread.start()
        self.wake.set()

    def notify(self) -> None:
        self.wake.set()

    def queue_position(self, job_id: str) -> int | None:
        queued = [j.id for j in self.store.all() if j.state == "queued"]
        return queued.index(job_id) + 1 if job_id in queued else None

    def _next(self) -> Job | None:
        return next((j for j in self.store.all() if j.state == "queued"), None)

    def _loop(self) -> None:
        while True:
            job = self._next()
            if job is None:
                self.wake.wait(timeout=30)
                self.wake.clear()
                continue
            self.run(job)

    def run(self, job: Job) -> None:
        store = self.store
        log = store.log(job.id)
        run = store.run_dir(job.id)
        store.update(job, state="running", started_at=job.started_at or time.time(), error=None)
        try:
            audio = to_wav(store.inputs / job.filename)
            if not (run / "full_score.mid").is_file():
                self.runner.run_midi(job.id, audio, run, job.requested, log)
            store.update(job, state="publishing")
            if not (run / "scores" / "score_outputs.json").is_file():
                self.runner.run_pdf(job.id, run, log)
            if not (run / "scores" / "score_outputs.json").is_file():
                raise RunnerError("PDF stage finished without scores/score_outputs.json")
            store.update(job, state="complete", finished_at=time.time())
            free_space(run, store.inputs, job.filename)
        except Exception as exc:  # any failure ends this job, never the worker
            store.update(job, state="failed", finished_at=time.time(), error=failure_reason(run, exc))


def failure_reason(run: Path, exc: Exception) -> str:
    """Prefer the pipeline's own error from manifest.json; it names the stage."""
    try:
        manifest = json.loads((run / "manifest.json").read_text(encoding="utf-8"))
        if manifest.get("status") == "failed" and manifest.get("error"):
            return str(manifest["error"])[:500]
    except (OSError, ValueError):
        pass
    return f"{type(exc).__name__}: {exc}"[:500]


def free_space(run: Path, inputs: Path, filename: str) -> None:
    """The shared volume has only a few GiB free. After a job is complete,
    remove what the app never downloads: the separated audio stems (about
    150 MB per song) and the uploaded/decoded audio. MIDI, TAB and PDF stay."""
    shutil.rmtree(run / "stems", ignore_errors=True)
    stem = Path(filename).stem
    for f in inputs.glob(f"{stem}.*"):
        f.unlink(missing_ok=True)
