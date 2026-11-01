"""Ways to run the ROCKY pipeline for one job.

KubernetesRunner (used on SGS): the API pod has no GPU. For each song it
creates the same two Jobs as ml/pipeline/sgs/*.example.yaml: a GPU Job that
runs unified.py, then a CPU Job that runs publish_scores.py. The GPU is held
only while the GPU Job runs, so the 30-minute idle rule never applies to the
API pod.

SubprocessRunner: runs the same two commands directly. For a machine that
has the pipeline installed locally (or for trying the server by hand).
"""

from __future__ import annotations

import os
import shlex
import shutil
import subprocess
import time
from pathlib import Path


class RunnerError(RuntimeError):
    pass


def to_wav(source: Path) -> Path:
    """Decode mp3/m4a/flac to 44.1 kHz stereo WAV when ffmpeg is available.

    unified.py reads the song length with soundfile, which cannot open every
    compressed format. WAV input avoids that. Without ffmpeg the file is
    passed through unchanged.
    """
    if source.suffix.lower() == ".wav" or shutil.which("ffmpeg") is None:
        return source
    target = source.with_suffix(".wav")
    result = subprocess.run(["ffmpeg", "-nostdin", "-loglevel", "error", "-y", "-i", str(source),
                             "-ar", "44100", "-ac", "2", str(target)], capture_output=True, text=True)
    if result.returncode or not target.is_file():
        raise RunnerError(f"Could not decode the audio file: {result.stderr.strip()[-300:]}")
    return target


class SubprocessRunner:
    def __init__(self) -> None:
        self.midi_cmd = os.environ.get(
            "ROCKY_MIDI_CMD",
            "/data/rocky-unified/.venv/bin/python /data/pipeline/unified.py {audio} {run} --requested {requested} --resume")
        self.pdf_cmd = os.environ.get(
            "ROCKY_PDF_CMD",
            "/data/rocky-unified/.venv/bin/python /data/pipeline/publish_scores.py {run}")

    def _run(self, template: str, audio: Path, run: Path, requested: list[str], log: Path) -> None:
        cmd = template.format(audio=shlex.quote(str(audio)), run=shlex.quote(str(run)),
                              requested=shlex.quote(",".join(requested)))
        with log.open("a", encoding="utf-8") as out:
            code = subprocess.run(cmd, shell=True, stdout=out, stderr=subprocess.STDOUT).returncode
        if code:
            raise RunnerError(f"command exited {code}; see {log.name}")

    def run_midi(self, job_id: str, audio: Path, run: Path, requested: list[str], log: Path) -> None:
        self._run(self.midi_cmd, audio, run, requested, log)

    def run_pdf(self, job_id: str, run: Path, log: Path) -> None:
        self._run(self.pdf_cmd, run, run, [], log)


class KubernetesRunner:
    """Create one GPU Job and one CPU Job per song in the workspace namespace."""

    POLL_SEC = 5

    def __init__(self) -> None:
        from kubernetes import client, config  # imported here so tests need no cluster
        try:
            config.load_incluster_config()
        except config.ConfigException:
            config.load_kube_config()
        self.batch = client.BatchV1Api()
        self.core = client.CoreV1Api()
        self.client = client
        self.namespace = os.environ.get("ROCKY_NAMESPACE") or Path(
            "/var/run/secrets/kubernetes.io/serviceaccount/namespace").read_text().strip()
        self.claim = os.environ.get("ROCKY_PVC", "muscriptor-data")
        self.pipeline_image = os.environ.get("ROCKY_PIPELINE_IMAGE", "ghcr.io/astral-sh/uv:python3.13-bookworm-slim")
        self.pdf_image = os.environ.get("ROCKY_PDF_IMAGE", "mcr.microsoft.com/playwright:v1.62.1-noble")
        self.timeout_sec = int(os.environ.get("ROCKY_JOB_TIMEOUT_SEC", "3600"))

    # ---- Job specs (same as ml/pipeline/sgs/*.example.yaml) ----
    def _volume(self):
        c = self.client
        return [c.V1Volume(name="data", persistent_volume_claim=c.V1PersistentVolumeClaimVolumeSource(
            claim_name=self.claim))], [c.V1VolumeMount(name="data", mount_path="/data")]

    def _job(self, name: str, image: str, script: str, env: dict[str, str], limits: dict[str, str],
             working_dir: str | None = None):
        c = self.client
        volumes, mounts = self._volume()
        container = c.V1Container(
            name="main", image=image, command=["/bin/bash", "-lc"], args=[script],
            working_dir=working_dir,
            env=[c.V1EnvVar(name=k, value=v) for k, v in env.items()],
            resources=c.V1ResourceRequirements(requests={"cpu": "0", "memory": "0"}, limits=limits),
            volume_mounts=mounts)
        pod = c.V1PodSpec(restart_policy="Never", containers=[container], volumes=volumes,
                          dns_config=c.V1PodDNSConfig(options=[c.V1PodDNSConfigOption(name="ndots", value="1")]))
        return c.V1Job(
            api_version="batch/v1", kind="Job",
            metadata=c.V1ObjectMeta(name=name, labels={"app": "rocky", "rocky-job": name}),
            spec=c.V1JobSpec(backoff_limit=0, ttl_seconds_after_finished=600,
                             template=c.V1PodTemplateSpec(metadata=c.V1ObjectMeta(labels={"rocky-job": name}),
                                                          spec=pod)))

    def midi_job(self, job_id: str, audio: Path, run: Path, requested: list[str]):
        script = (
            "set -euo pipefail; "
            "export HF_HOME=/data/cache/hf TORCH_HOME=/data/cache/torch "
            "UV_CACHE_DIR=/data/cache/uv UV_TOOL_DIR=/data/cache/uv-tools "
            "UV_PYTHON_INSTALL_DIR=/data/cache/python XDG_CACHE_HOME=/data/cache/xdg; "
            'exec /data/rocky-unified/.venv/bin/python /data/pipeline/unified.py '
            '"$ROCKY_INPUT_AUDIO" "$ROCKY_OUTPUT_DIR" --requested "$ROCKY_REQUESTED" --resume')
        env = {"ROCKY_INPUT_AUDIO": str(audio), "ROCKY_OUTPUT_DIR": str(run),
               "ROCKY_REQUESTED": ",".join(requested), "NVIDIA_DRIVER_CAPABILITIES": "compute,utility"}
        return self._job(f"rocky-midi-{job_id}", self.pipeline_image, script, env,
                         {"cpu": "8", "memory": "32Gi", "nvidia.com/gpu": "1"})

    def pdf_job(self, job_id: str, run: Path):
        script = "\n".join([
            "set -euo pipefail",
            "export DEBIAN_FRONTEND=noninteractive QT_QPA_PLATFORM=offscreen",
            "export XDG_RUNTIME_DIR=/tmp/rocky-qt-runtime",
            'mkdir -p "$XDG_RUNTIME_DIR"; chmod 700 "$XDG_RUNTIME_DIR"',
            "sed -i 's|http://azure.archive.ubuntu.com/ubuntu|https://archive.ubuntu.com/ubuntu|g' "
            "/etc/apt/sources.list.d/ubuntu.sources",
            "apt-get -o Acquire::Retries=5 update -qq",
            "apt-get -o Acquire::Retries=5 install -y -qq --no-install-recommends musescore3 poppler-utils",
            '/data/rocky-unified/.venv/bin/python /data/pipeline/publish_scores.py "$ROCKY_OUTPUT_DIR" '
            "--musescore musescore3 --gp5-converter /data/rocky-unified/ml/gp5_to_pdf/convert.js",
        ])
        return self._job(f"rocky-pdf-{job_id}", self.pdf_image, script, {"ROCKY_OUTPUT_DIR": str(run)},
                         {"cpu": "4", "memory": "8Gi"}, working_dir="/data/rocky-unified/ml/gp5_to_pdf")

    # ---- running ----
    def _run_job(self, job, log: Path) -> None:
        name = job.metadata.name
        try:
            self.batch.create_namespaced_job(self.namespace, job)
        except self.client.exceptions.ApiException as exc:
            if exc.status != 409:  # 409: the Job already exists after an API restart; wait for it
                raise RunnerError(f"could not create Job {name}: {exc.reason}") from exc
        deadline = time.monotonic() + self.timeout_sec
        while time.monotonic() < deadline:
            status = self.batch.read_namespaced_job_status(name, self.namespace).status
            if status.succeeded:
                self._save_log(name, log)
                return
            if status.failed:
                self._save_log(name, log)
                raise RunnerError(f"Job {name} failed; see {log.name}")
            time.sleep(self.POLL_SEC)
        raise RunnerError(f"Job {name} did not finish within {self.timeout_sec} s")

    def _save_log(self, name: str, log: Path) -> None:
        try:
            pods = self.core.list_namespaced_pod(self.namespace, label_selector=f"rocky-job={name}").items
            if pods:
                text = self.core.read_namespaced_pod_log(pods[0].metadata.name, self.namespace, tail_lines=400)
                with log.open("a", encoding="utf-8") as out:
                    out.write(f"===== {name} =====\n{text}\n")
        except Exception as exc:  # the log is only for people; never fail the job over it
            with log.open("a", encoding="utf-8") as out:
                out.write(f"(could not read log of {name}: {exc})\n")

    def run_midi(self, job_id: str, audio: Path, run: Path, requested: list[str], log: Path) -> None:
        self._run_job(self.midi_job(job_id, audio, run, requested), log)

    def run_pdf(self, job_id: str, run: Path, log: Path) -> None:
        self._run_job(self.pdf_job(job_id, run), log)


def make_runner(kind: str):
    if kind == "kubernetes":
        return KubernetesRunner()
    if kind == "subprocess":
        return SubprocessRunner()
    raise ValueError(f"unknown ROCKY_RUNNER {kind!r} (kubernetes or subprocess)")
