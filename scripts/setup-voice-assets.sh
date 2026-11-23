#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODEL_ID="sherpa-onnx-pocket-tts-int8-2026-01-26"
BASE="https://github.com/k2-fsa/sherpa-onnx/releases/download"
MODEL_URL="$BASE/tts-models/$MODEL_ID.tar.bz2"
AAR_URL="$BASE/v1.13.4/sherpa-onnx-1.13.4.aar"
MODEL_SHA="2f3b88823cbbb9bf0b2477ec8ae7b3fec417b3a87b6bb5f256dba66f2ad967cb"
AAR_SHA="03f9c4df965f21c71269365a7951a7f23b5696fddd093fa318c80d65550ab780"
TEMP="$(mktemp -d)"
trap 'rm -rf "$TEMP"' EXIT
mkdir -p "$ROOT/app/libs" "$ROOT/app/src/main/assets/tts"

curl -fL --retry 3 "$AAR_URL" -o "$TEMP/runtime.aar"
echo "$AAR_SHA  $TEMP/runtime.aar" | sha256sum -c -
cp "$TEMP/runtime.aar" "$ROOT/app/libs/sherpa-onnx-1.13.4.aar"

curl -fL --retry 3 "$MODEL_URL" -o "$TEMP/model.tar.bz2"
echo "$MODEL_SHA  $TEMP/model.tar.bz2" | sha256sum -c -
tar -xjf "$TEMP/model.tar.bz2" -C "$TEMP"

OUT="$ROOT/app/src/main/assets/tts/$MODEL_ID"
mkdir -p "$OUT"
for file in decoder.int8.onnx encoder.onnx lm_flow.int8.onnx lm_main.int8.onnx text_conditioner.onnx token_scores.json vocab.json LICENSE README.md; do
  cp "$TEMP/$MODEL_ID/$file" "$OUT/$file"
done
echo "PocketTTS files are ready for a single offline Android APK."
