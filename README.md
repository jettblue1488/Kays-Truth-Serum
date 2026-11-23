# Kay's Truth Serum (Android)

Version 0.2.1 is a **side-by-side-installable build** for people who have an earlier APK installed with a different debug signing certificate. It uses Android application ID `com.kays.truthserum.v2` and appears as **Kay's Truth Serum 2** on the launcher. **Do not uninstall your previous version if you need its recordings.** This new installation does not automatically import previous interview histories; they remain in the older app.

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
In GitHub, open **Actions → Build Kay's Truth Serum APK**, choose a successful main build, and download the `Kays-Truth-Serum-debug-apk` artifact. Unzip it to obtain `app-debug.apk`.

**Future updates:** GitHub Actions builds use a fresh debug signing certificate on clean runners. Even this side-by-side install cannot be updated seamlessly with another debug build signed by a different certificate. To preserve interview data across future app updates, configure consistent release signing using a securely stored signing key; never commit a private signing key to a public repository.
