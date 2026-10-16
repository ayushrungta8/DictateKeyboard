# Current target: Roman Hinglish

The user means Hindi/English mixed in one sentence, written in English letters: “jaise ki YouTube, Banana, and Apple”. Generic Hindi recognition and Devanagari output do not meet this requirement.

The updated build separates **HING** (Roman Hinglish) from **EN** (ordinary English). HING uses Oriserve Hindi2Hinglish-Apex by default; Swift is a smaller, faster alternative selectable in local-model settings. EN retains the original multilingual Whisper Small engine. These models directly emit Roman Hinglish; there is no word-replacement dictionary pretending to fix misrecognition.

## Evidence from the connected Samsung SM-S928B

| Fixture | New output | Apex time | Swift time |
|---|---|---|---|
| Public short Hindi recording | Main kal call karoon kya? | 8.7 s cold | 1.8 s cold |
| Synthetic mixed speech | Kal morning mein meeting hai aur uske baad please send the report. | 4.9 s warm | 0.7 s warm |

The original English fixture still produced “Please send the meeting notes tomorrow morning.” using the original engine. The larger Apex also handled more of the public noisy fixtures correctly in a small desktop comparison.

**Limit:** both models still made errors on a robotic eSpeak version of the user's YouTube/Banana/Apple example. Swift lost the fruit words; Apex retained “banaana and apple” but mangled the opening/brand. This is not a verified fix for the user's real voice or proof of Wispr parity. The app did not retain the user's previous failed recordings, so those could not be replayed. Further evaluation needs natural mixed-language speech, especially names and brands. No synthetic test is labelled as human speech.

Apex download is about 1.04 GB; Swift about 161 MB. Models were exported from pinned upstream safetensors with the sherpa-onnx Whisper exporter, quantized to int8, and verified in the Android runtime. Source revisions and SHA256 hashes accompany the model assets. See `tools/models/export-hinglish.py` and the model's Apache-2.0 attribution in the app.

Sources: [Oriserve Apex](https://huggingface.co/Oriserve/Whisper-Hindi2Hinglish-Apex), [Oriserve Swift](https://huggingface.co/Oriserve/Whisper-Hindi2Hinglish-Swift).

---

The following records the initial generic-Hindi investigation and baseline results; it is retained as history, not the current model decision.

# Offline English/Hindi dictation — model decision

Checked 16 October 2026. These are research-based candidates, not a benchmark on Ayush’s phone. We have no matched recordings of Ayush’s speech in this repository. Separate pure English, pure Hindi and within-sentence English/Hindi switching in evaluation; a language list does not establish the last one.

## What the first build uses

**Multilingual Whisper Small (int8)** is the initial balanced choice because Dictate already supports its Android inference path and verified model downloads. It supports English and Hindi; the `.en` variant does not. Download is about 376 MB including the speech detector. Whisper Base (~161 MB) is the smaller alternative, with lower expected recognition quality. Automatic language detection stays selected by default, with explicit English/Hindi modes available. This is an integration choice, not a claim of best accuracy.

Sources: [OpenAI Whisper](https://github.com/openai/whisper), and the pinned model files, byte counts, hashes and Android configurations in `LocalModelCatalog.kt` and `LocalTranscriptionProvider.kt`.

## Candidates to benchmark next

| Candidate | Why it matters | What remains unproven |
|---|---|---|
| **Nemotron 3.5 ASR 0.6B** | Native streaming, punctuation/capitalization, English and Hindi in NVIDIA’s transcription-ready tier. FluidVoice offers this family on Mac. | Android latency, memory and accuracy on natural mixed English/Hindi; requires a compatible Android runtime/export integration, not merely adding its name to the picker. |
| **Qwen3-ASR 0.6B** | Explicit English/Hindi support. sherpa-onnx documents Android support for Qwen3-ASR, giving it a plausible deployment path. | Needs an engine integration with the vendored runtime, compatible weights and actual phone measurements. |
| **Whisper Small multilingual** | Existing Android implementation; widest practical baseline in this fork. | Script confusion, language switches, long rambling dictation and inference speed on the target device. |
| **Dolphin Base** | Existing small (~105 MB) Hindi-capable model. | English is not in its documented supported list. It must not be presented as the general mixed English/Hindi engine. |

Primary sources:

- [NVIDIA Nemotron 3.5 model card and evaluation](https://huggingface.co/nvidia/nemotron-3.5-asr-streaming-0.6b)
- [Qwen3-ASR 0.6B model card](https://huggingface.co/Qwen/Qwen3-ASR-0.6B)
- [sherpa-onnx Qwen3-ASR deployment documentation](https://github.com/k2-fsa/sherpa/blob/master/docs/source/onnx/qwen3-asr/index.rst)
- [Dolphin upstream](https://github.com/DataoceanAI/Dolphin)

Do not compare numerical error rates from different datasets as though they were a head-to-head test. Vendor GPU throughput does not predict Android latency. Experimental third-party Hinglish fine-tunes are candidates only after reviewing their data, license and evaluation.

## What FluidVoice does well

Its public app offers multiple recognition engines (Nemotron, Parakeet, Whisper and others), plus optional local **Fluid Intelligence** cleanup. Recognition and cleanup are different stages. The cleanup runtime is separately and privately maintained and macOS-specific; the app repository’s open license does not grant a portable Android cleanup implementation.

[Official FluidVoice source and explanation](https://github.com/altic-dev/FluidVoice#fluid-intelligence). Its README explicitly says `fluidvoice.org` is unaffiliated. Use `altic.dev/fluid`, `fluidvoice.com`, `fluidvoice.app`, and the official GitHub releases.

**Parakeet TDT v3 is not our bilingual default:** its 25 documented languages exclude Hindi. FluidVoice’s Mac performance is not evidence that a particular model will run equally well on Android.

## Acceptance test before calling a model “best”

Use the same 20–30 local recordings across all engines: Indian English, Hindi, mixed speech, names, numbers, corrections and pauses. Include at least five clips that switch languages inside a sentence. Decide Devanagari versus Romanized Hindi separately; accurate recognition and transliteration are different tasks.

Record raw transcript, missing/incorrect words, unwanted translation or rewriting, script errors, time after speech ends, real-time factor (processing seconds divided by recording seconds), peak memory and thermal/battery behavior. Evaluate cleanup separately so it cannot hide recognition errors by inventing plausible sentences. Keep all recordings and results local.

## Emulator smoke results

The Android 16 arm64 emulator ran Whisper Small with Wi-Fi and mobile data disabled. This verifies local execution; it is not a phone benchmark or a model ranking.

| Fixture | Result | Time after starting inference |
|---|---|---|
| eSpeak Indian English: “Please send the meeting notes tomorrow morning.” | Exact transcript | 2.77 s |
| Public Hindi sample: “मैं कल कॉल करूँ क्या?” | “मैं कर कोल कर क्या” | 2.44 s |
| eSpeak mixed Hindi/English | Inaccurate Romanized Hindi mixed with English | 2.27 s |

The first purely synthetic Hindi fixture produced repeated “र”; replacing it with a public dataset sample improved the result but still left recognition errors. Therefore **do not describe this build as Wispr-quality Hindi/Hinglish**. The initial engine is a functioning baseline, and recognition quality is the main remaining product limitation.

Hindi fixture: `a_hindi/54.wav` inside [this public dataset’s archive](https://huggingface.co/datasets/datasetsANDmodels/hindi-stt-dataset/blob/main/a_hindi.zip), with reference text from [its transcription file](https://huggingface.co/datasets/datasetsANDmodels/hindi-stt-dataset/blob/main/a_transcription.txt). The fixture audio is not redistributed in the fork or APK. Public dataset provenance is not independently verified. The Android smoke test checks that the runtime returns text and Hindi script; it does not certify accuracy.

## Physical phone check

On the connected Samsung SM-S928B (Android 16), the same local recognition smoke test took approximately 2.9 s for English, 3.6 s for Hindi and 4.0 s for mixed speech. Output quality was similar to the emulator: exact English in this fixture, Hindi recognition errors, and inaccurate Romanized Hindi in the mixed synthetic fixture. The phone's radios were left unchanged; this run exercised the local provider directly. Offline capability was separately verified with emulator radios off.
