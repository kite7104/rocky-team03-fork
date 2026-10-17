# Iteration 2 M2M GPU arranger

These are the two self-contained GPU-pod notebooks used for the Iteration 2 M2M arrangements. They are inference workflows, not model-training code and not part of the web app. Each notebook reads every `.mid`/`.midi` file staged in `/workspace/m2m/inputs`, uses the upstream `BandArranger` followed by `DrumArranger`, and writes validated MIDI files plus a SHA-256 manifest and ZIP under `/workspace/m2m/outputs`.

| Notebook | Model conditioning programs | Saved GM programs (zero-based; 128 = drums) |
| --- | --- | --- |
| [Synth + guitar band](notebooks/M2M_Synth_Guitar_Band_GPU_Server.ipynb) | 80 synth lead, 26 electric guitar, 33 electric bass | 80 synth lead, 27 clean electric guitar, 33 electric bass, 128 drums |
| [Electric piano + clean guitar band](notebooks/M2M_EPiano_Clean_Guitar_Bass_Drum_GPU_Server.ipynb) | 2 electric piano, 26 electric guitar, 33 electric bass | 4 electric piano, 27 clean electric guitar, 33 electric bass, 128 drums |

The post-generation program remapping is intentional: the published model's conditioning IDs are used for inference, then the saved MIDI is relabeled to the requested output programs. The notebooks reject an output if any requested instrument has no notes, re-open each saved MIDI for validation, and retry failed generations with recorded seeds.

## Run on an ephemeral GPU pod

1. Check out this branch on the local machine and place the chosen input MIDI files in a local `corrected_midi` directory at the repository root. Input files are **not** in Git.
2. Open the selected notebook and follow its first Markdown cell from PowerShell at the repository root. Set your own Kubernetes namespace and GPU pod name. The instructions install the minimal runtime if needed, copy the inputs and notebook into `/workspace/m2m`, then execute with `jupyter nbconvert`. Run the second notebook against the same inputs if both arrangements are wanted.
3. Copy each resulting ZIP (and, if useful, the executed notebook) back to local storage with `kubectl cp` **before** deleting or losing the pod. Inspect each ZIP's manifest and MIDI files locally.

The commands use relative Windows paths for local `kubectl cp` sources. An absolute `C:\...` path can be misinterpreted by `kubectl cp` as a remote target. Do not put a kubeconfig, credentials, input MIDI, model cache, checkpoints, executed notebooks, or generated outputs in this branch.

## Provenance

The successful 2026-10-17 GPU run processed 10 staged MIDI inputs with each notebook (10/10 outputs per configuration). At that run, the upstream code checkouts were [`Sonata165/music2music_code` at `8359bbdb53e4463b85f3d887af510d19952da767`](https://github.com/Sonata165/music2music_code/tree/8359bbdb53e4463b85f3d887af510d19952da767) and [`Sonata165/REMI-z` at `dbededa3c3bdf58c4d6af7e43e4dbd8563f26a3e`](https://github.com/Sonata165/REMI-z/tree/dbededa3c3bdf58c4d6af7e43e4dbd8563f26a3e). The notebooks load Hugging Face checkpoints `LongshenOu/m2m_arranger` and `LongshenOu/m2m_drummer`, with PyTorch `2.2.2+cu121`, Transformers `4.40.0`, and NumPy `<2`. The notebooks currently clone upstream default branches when run; check the printed commit IDs against these tested revisions before relying on a future run as an exact reproduction.
