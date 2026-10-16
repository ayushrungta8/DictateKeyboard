#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12,<3.13"
# dependencies = ["torch==2.14.1", "onnx==1.23.2", "onnxruntime==1.31.0", "openai-whisper==20250625", "safetensors==0.8.0"]
# ///
"""Export licensed Hindi2Hinglish weights for the existing Android sherpa-onnx runtime.

Usage: uv run tools/models/export-hinglish.py swift --work-dir /tmp/hinglish-swift
Use apex for the larger model. Requires several GB of working disk/memory.
Only public model weights are downloaded; no audio is sent anywhere.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import urllib.request

import torch
from safetensors.torch import load_file

EXPORTER_REV = "7be9a38e275ece9ee9a9277cc78295f2cbfd6673"
REVISIONS = {"swift": "cd0d4d013062d542569135460a79025337df1982", "apex": "f3214eed20b4e4d4144e739982d911f87b9cb223"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("variant", choices=REVISIONS)
    parser.add_argument("--work-dir", type=Path, required=True)
    args = parser.parse_args()
    work = args.work_dir.resolve()
    work.mkdir(parents=True, exist_ok=True)
    repo = f"Oriserve/Whisper-Hindi2Hinglish-{args.variant.title()}"
    revision = REVISIONS[args.variant]

    def fetch(url, target):
        if not target.exists():
            temporary = target.with_suffix(target.suffix + ".part")
            urllib.request.urlretrieve(url, temporary)
            temporary.replace(target)

    for name in ("config.json", "model.safetensors", "convert_hf2openai.json"):
        fetch(f"https://huggingface.co/{repo}/resolve/{revision}/{name}", work / name)
    config = json.loads((work / "config.json").read_text())
    mapping = json.loads((work / "convert_hf2openai.json").read_text())
    state = {}
    for key, value in load_file(work / "model.safetensors").items():
        key = key.removeprefix("model.")
        for pattern, replacement in mapping.items():
            if re.match(pattern, key):
                state[re.sub(pattern, replacement, key)] = value
                break
    dimensions = {"n_mels": config["num_mel_bins"], "n_vocab": config["vocab_size"],
        "n_audio_ctx": config["max_source_positions"], "n_audio_state": config["d_model"],
        "n_audio_head": config["encoder_attention_heads"], "n_audio_layer": config["encoder_layers"],
        "n_text_ctx": config["max_target_positions"], "n_text_state": config["d_model"],
        "n_text_head": config["decoder_attention_heads"], "n_text_layer": config["decoder_layers"]}
    checkpoint = work / "hinglish.pt"
    torch.save({"dims": dimensions, "model_state_dict": state}, checkpoint)
    del state
    name = "base" if args.variant == "swift" else "turbo"
    exporter_url = f"https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/{EXPORTER_REV}/scripts/whisper/export-onnx.py"
    fetch(exporter_url, work / "upstream-export.py")
    source = (work / "upstream-export.py").read_text()
    needle = "def load_model(name: str):"
    if needle not in source:
        raise RuntimeError("Pinned exporter API changed")
    source = source.replace(needle, needle + '\n    return whisper.load_model("./hinglish.pt", device="cpu")', 1)
    source = source.replace("        opset_version=opset_version,", "        dynamo=False,\n        opset_version=opset_version,")
    (work / "export.py").write_text(source)
    subprocess.run([sys.executable, str(work / "export.py"), "--model", name], cwd=work, check=True)
    output = work / "release"
    output.mkdir(exist_ok=True)
    for original, target in [(f"{name}-encoder.int8.onnx", "encoder.onnx"),
                             (f"{name}-decoder.int8.onnx", "decoder.onnx"),
                             (f"{name}-tokens.txt", "tokens.txt")]:
        shutil.copyfile(work / original, output / target)
    manifest = {"source_model": repo, "source_revision": revision, "license": "Apache-2.0",
                "exporter": exporter_url, "files": {}}
    for path in output.iterdir():
        manifest["files"][path.name] = {"bytes": path.stat().st_size, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
    (output / "MODEL_MANIFEST.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"Exported to {output}. Review checksums before changing the app catalog.")


if __name__ == "__main__":
    main()
