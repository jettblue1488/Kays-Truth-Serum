# Kay's Truth Serum (Android)

Version 0.2 includes a **direct target-question interview mode** and an **optional 10-prompt reference baseline**.

## Use the interview
1. Choose **New interview / Ask my questions** and enter the participant's name.
2. Tap **Ask my target questions now** to skip the baseline, or use the optional 10 baseline prompts.
3. Enter **one target question per line**, then tap **Save questions and start recording**.
4. Record a separate answer for each question, then tap **Stop, save, and analyze**.
5. Review each answer's **voice-variation feature index (0–100%)**, loudness/pitch variation, and optional baseline comparison. Results and the recorded audio appear under **Saved interviews and results**.

### Important limitations
The percentage is a **custom, experimental measure of acoustic feature variability**, not a validated measure of psychological stress, confidence, honesty, or deception. There is no scientifically reliable voice-only truth or lie percentage. Changes in pitch and loudness can reflect many factors including ordinary speech patterns, noise, illness, emotion, and microphone distance. Short or poor-quality clips may not receive a score.

The analyzer runs **offline on the phone** and processes up to the first 120 seconds of a recording. Record only with the speaker's knowledge and applicable consent.

## Build APK
In GitHub, open **Actions → Build Kay's Truth Serum APK**, run the workflow on main (or use the most recent completed main build), and download its `Kays-Truth-Serum-debug-apk` artifact. Unzip it to obtain the Android APK.

**Note for updates:** Debug APKs generated on different GitHub Actions runners can have different signing certificates. If Android reports **App not installed** or **package conflicts**, an installed older build may have an incompatible signature. Uninstalling it can delete recordings and saved interview history. Back up important recordings before removing an older build. For reliable updates without data loss, the project needs a consistent release-signing certificate kept in GitHub Secrets.
