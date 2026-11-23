# Kay's Truth Serum Studio (Android v0.3.0)

A **native Android** interview recorder and **offline, zero-shot neural voice cloning** studio.

## The updated interview flow

1. Open **Start a new interview**, enter a participant's name, and choose whether to include the optional 10-question reference baseline.
2. Add multiple target questions (one question per line).
3. Select **Record next unanswered question**. Record and save each answer separately.
4. Select **Saved interviews** to open one session containing **every baseline question, target question, audio recording, timestamp, and analysis result** from that person and that particular interview.
5. Review results for each question with an optional comparison against the **reference answers from the same session**. Deliberately false practice questions are excluded from the neutral reference average.
6. Export a human-readable JSON report for the session using **Export session report** (the export includes metadata and measurements but not the private audio bytes).

## CVSA-inspired acoustic measurements

The app computes a custom acoustic feature index from:
- Loudness variation measured in decibels.
- Pitch variation measured as a percentage of the average estimated pitch.
- Rapid 8–12 Hz amplitude modulation in the short-time energy envelope.

The displayed **0–100% feature index** is a deliberately defined experimental scale, **not a calibrated or validated probability**. Its descriptive bands are 0–24 low, 25–49 mild, 50–74 moderate, 75–100 high *measured acoustic variation*. There is **no evidence that a value like 70% means a person is 70% likely to be deceptive, stressed, anxious, or dishonest**. It is not the proprietary CVSA software or a verified replication of its algorithm. Short, unclear, or noisy answers may have incomplete results.

Comparison is **within the same interview only**, based on the average of recorded and analyzable neutral/known-true reference answers. If no reference answers exist, no baseline comparison is shown. Three or more reference recordings are preferable. Speech analysis is not a substitute for verifying facts.

## Own native Voice Clone Studio

Open **Native Voice Clone Studio**. Confirm that you own the voice or have the speaker's explicit permission. Record about **8–15 seconds** of clear English speech. Enter custom text and tap **Generate AI voice**. The app uses **PocketTTS INT8** with a speaker reference **fully on the phone**, without an API key or account. Play or export the generated audio as a WAV file. Generated files are clearly labeled **SYNTHETIC**.

PocketTTS and sherpa-onnx are upstream open-source components. The Android build downloads their officially published model/runtime archives and checks them against pinned SHA-256 digests. See the model's bundled LICENSE and README for licensing conditions. This model version is English-only.

### Performance / installation

- The voice model **substantially enlarges the APK** (roughly 200+ MB) and uses considerable RAM. It is built for **64-bit ARM Android devices**; very old devices or low-RAM phones may be unsupported.
- This build uses a **different package ID** (`com.kays.truthserum.v3`) and appears as **Kay's Truth Serum Studio**. It can install alongside an older, differently signed debug APK. Older private app data stays in the old app and **cannot automatically be imported across packages**.
- The build is a debug APK signed by GitHub Actions. Future upgrades need a **stable release signing key** or Android may reject them. Do not uninstall an old app with valuable private recordings before backing up those recordings.
- The **Export session report** command exports JSON measurements and question text, but audio files are retained in private app storage and **not** included in the exported JSON.

## Build

GitHub **Actions → Build Kay's Truth Serum APK** builds and uploads `Kays-Truth-Serum-Studio-v0.3.0`. Download and unzip the artifact; the file inside is `app-debug.apk`. The GitHub workflow may take longer because it fetches and verifies the voice model.

Record and synthesize audio only with applicable consent. Do not use synthetic audio to mislead people or to impersonate others.
