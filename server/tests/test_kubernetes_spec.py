from pathlib import Path

from kubernetes import client

from rocky_server.runners import KubernetesRunner


def runner():
    r = KubernetesRunner.__new__(KubernetesRunner)  # no cluster needed to build specs
    r.client, r.claim = client, "muscriptor-data"
    r.pipeline_image, r.pdf_image = "uv-image", "pw-image"
    return r


def test_midi_job_matches_example_yaml():
    job = runner().midi_job("abc123abc123", Path("/data/input/app/x.wav"), Path("/data/rocky-unified/runs/x"),
                            ["piano", "drums"])
    c = job.spec.template.spec.containers[0]
    env = {e.name: e.value for e in c.env}
    assert job.metadata.name == "rocky-midi-abc123abc123"
    assert job.spec.backoff_limit == 0 and job.spec.template.spec.restart_policy == "Never"
    assert c.resources.limits["nvidia.com/gpu"] == "1"
    assert env["ROCKY_REQUESTED"] == "piano,drums"
    assert "/data/pipeline/unified.py" in c.args[0] and "--resume" in c.args[0]
    assert job.spec.template.spec.volumes[0].persistent_volume_claim.claim_name == "muscriptor-data"


def test_pdf_job_has_no_gpu():
    job = runner().pdf_job("abc123abc123", Path("/data/rocky-unified/runs/x"))
    c = job.spec.template.spec.containers[0]
    assert "nvidia.com/gpu" not in c.resources.limits
    assert "publish_scores.py" in c.args[0]
