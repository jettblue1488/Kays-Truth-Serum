package com.kays.truthserum;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private LinearLayout layout;
    private TextView status;
    private MediaRecorder recorder;
    private File currentFile;
    private String currentQuestion = "";
    private String currentType = "";
    private String currentCategory = "";
    private boolean recording = false;
    private boolean analyzing = false;

    private String interviewName = "";
    private String sessionId = "";
    private boolean baselinePhase = false;
    private int baselineIndex = 0;
    private int customIndex = 0;
    private final ArrayList<String> personalQuestions = new ArrayList<>();

    private static final String[] BASELINE = {
            "Please state your full name.",
            "What month is it currently?",
            "Describe what you can see in this room.",
            "What did you eat or drink most recently?",
            "Is your first name [insert correct name]?",
            "Is your first name [insert an incorrect name]?",
            "Please say 'yes' in your normal speaking voice.",
            "Please say 'no' in your normal speaking voice.",
            "For this practice only, knowingly give an incorrect answer: Is the sky normally green?",
            "Describe a familiar daily activity in two sentences."
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        showHome();
    }

    private TextView label(String value) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(18);
        text.setTextColor(Color.WHITE);
        text.setPadding(12, 15, 12, 15);
        layout.addView(text);
        return text;
    }

    private Button button(String value, Runnable action) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        layout.addView(b);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private EditText field(String hint) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setHintTextColor(Color.LTGRAY);
        edit.setTextColor(Color.WHITE);
        edit.setTextSize(18);
        layout.addView(edit);
        return edit;
    }

    private void page(String title) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(22, 24, 22, 24);
        layout.setBackgroundColor(Color.rgb(25, 15, 19));
        scroll.addView(layout);
        setContentView(scroll);
        label(title);
    }

    private void showHome() {
        page("KAY'S TRUTH SERUM");
        label("Record interview answers and review experimental voice-variation measurements. No recording can reliably give a truth or deception percentage.");
        button("New interview / Ask my questions", this::newInterview);
        button("Saved interviews and results", this::history);
        button("Voice Clone Studio", this::studio);
    }

    private void newInterview() {
        page("New interview");
        label("Enter the participant's name. With their knowledge, record individual answers to the questions you choose.");
        EditText name = field("Interviewed person's name");
        button("Ask my target questions now (skip baseline)", () -> beginInterview(name, false));
        button("Run optional 10-question baseline first", () -> beginInterview(name, true));
        label("The baseline supplies reference recordings for comparison only. It cannot calibrate a lie detector.");
        button("Home", this::showHome);
    }

    private void beginInterview(EditText name, boolean withBaseline) {
        String entered = name.getText().toString().trim();
        if (entered.isEmpty()) {
            Toast.makeText(this, "Enter a participant's name.", Toast.LENGTH_SHORT).show();
            return;
        }
        interviewName = entered;
        sessionId = String.valueOf(System.currentTimeMillis());
        baselinePhase = withBaseline;
        baselineIndex = 0;
        customIndex = 0;
        personalQuestions.clear();
        if (baselinePhase) showQuestion();
        else enterQuestions();
    }

    private void enterQuestions() {
        if (recording || analyzing) return;
        page("Your target questions • " + interviewName);
        label("Enter one question per line. You can add several at once, and the app will record and analyze each answer separately.");
        EditText entry = field("Question 1?\nQuestion 2?\nQuestion 3?");
        entry.setSingleLine(false);
        entry.setMinLines(4);
        entry.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        int waiting = personalQuestions.size() - customIndex;
        if (waiting > 0) label(waiting + " previously entered question(s) still waiting to be recorded.");
        button("Save questions and start recording", () -> {
            String[] lines = entry.getText().toString().split("\\r?\\n");
            int added = 0;
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    if (trimmed.length() > 500 || personalQuestions.size() >= 100) {
                        Toast.makeText(this, "Maximum 100 questions, 500 characters each.",
                                Toast.LENGTH_LONG).show();
                        break;
                    }
                    personalQuestions.add(trimmed);
                    added++;
                }
            }
            if (added == 0 && customIndex >= personalQuestions.size()) {
                Toast.makeText(this, "Enter at least one question.", Toast.LENGTH_SHORT).show();
                return;
            }
            showQuestion();
        });
        if (waiting > 0) {
            button("Continue with queued questions", this::showQuestion);
        }
        button("Finish and view interview results", this::sessionSummary);
    }

    private void showQuestion() {
        if (recording || analyzing) {
            Toast.makeText(this, "Finish the current recording first.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (baselinePhase && baselineIndex >= BASELINE.length) baselinePhase = false;
        if (!baselinePhase && customIndex >= personalQuestions.size()) {
            enterQuestions();
            return;
        }
        boolean baseline = baselinePhase;
        String prompt = baseline ? BASELINE[baselineIndex] : personalQuestions.get(customIndex);
        String type = baseline ? baselineType(baselineIndex) : "Target question";
        String title = baseline ? "Baseline " + (baselineIndex + 1) + " of 10"
                : "Target question " + (customIndex + 1) + " of " + personalQuestions.size();
        page(title + " • " + interviewName);
        label("Category: " + type);
        label(prompt);
        if (baseline && (baselineIndex == 4 || baselineIndex == 5)) {
            label("Replace the bracketed name with the appropriate name when asking the question.");
        }
        label("Record one answer at a time. The next screen will show its acoustic measurements.");
        status = label("Ready to record.");
        button("Start recording answer", () -> startRecording(prompt, type, baseline ? "baseline" : "target"));
        button("Stop, save, and analyze", this::stopRecording);
        if (!baseline) button("Edit this target question", this::editCurrentQuestion);
        button("Skip this question", () -> {
            if (recording || analyzing) {
                status.setText("Stop and save the recording first.");
                return;
            }
            advanceQuestion();
            showQuestion();
        });
        button("Finish interview", () -> {
            if (recording || analyzing) {
                status.setText("Stop and save the recording first.");
                return;
            }
            sessionSummary();
        });
    }

    private String baselineType(int index) {
        if (index == 8) return "Assisted practice false answer";
        if (index == 5) return "Known false statement (verify)";
        if (index == 4) return "Known true statement (verify)";
        return "Neutral / reference speech";
    }

    private void editCurrentQuestion() {
        if (baselinePhase || recording || analyzing) return;
        page("Edit target question");
        EditText edit = field("Question");
        edit.setText(personalQuestions.get(customIndex));
        button("Save and return to recording", () -> {
            String updated = edit.getText().toString().trim();
            if (updated.isEmpty()) {
                Toast.makeText(this, "A question cannot be blank.", Toast.LENGTH_SHORT).show();
                return;
            }
            personalQuestions.set(customIndex, updated);
            showQuestion();
        });
    }

    private void startRecording(String prompt, String type, String category) {
        if (recording || analyzing) {
            status.setText("Finish the current answer first.");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            status.setText("Allow microphone access, then tap Start again.");
            return;
        }
        try {
            currentQuestion = prompt;
            currentType = type;
            currentCategory = category;
            File folder = new File(getFilesDir(), "recordings");
            if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("Cannot create recordings folder");
            currentFile = new File(folder, "answer_" + System.currentTimeMillis() + ".m4a");
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioEncodingBitRate(96000);
            recorder.setOutputFile(currentFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            recording = true;
            status.setText("Recording... Speak into the phone. Tap Stop, save, and analyze when finished.");
        } catch (Exception error) {
            releaseRecorder();
            status.setText("Recording error: " + error.getMessage());
        }
    }

    private void stopRecording() {
        if (analyzing) {
            status.setText("Analyzing the previous answer...");
            return;
        }
        if (!recording) {
            status.setText("Tap Start recording answer first.");
            return;
        }
        try {
            recorder.stop();
        } catch (Exception error) {
            releaseRecorder();
            status.setText("Recording was too short or could not be saved. Record the answer again.");
            return;
        }
        releaseRecorder();
        final File savedFile = currentFile;
        final String savedQuestion = currentQuestion;
        final String savedType = currentType;
        final String savedCategory = currentCategory;
        final String savedSession = sessionId;
        final String savedPerson = interviewName;
        final int savedSequence = "baseline".equals(savedCategory)
                ? baselineIndex + 1 : customIndex + 1;
        advanceQuestion();
        analyzing = true;
        status.setText("Saved audio. Analyzing pitch and loudness variation...");
        new Thread(() -> {
            AudioAnalyzer.Result measurements = AudioAnalyzer.analyze(savedFile);
            runOnUiThread(() -> {
                try {
                    JSONObject item = new JSONObject();
                    item.put("person", savedPerson);
                    item.put("question", savedQuestion);
                    item.put("type", savedType);
                    item.put("category", savedCategory);
                    item.put("file", savedFile.getAbsolutePath());
                    item.put("session", savedSession);
                    item.put("sequence", savedSequence);
                    item.put("date", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                            Locale.US).format(new Date()));
                    item.put("analyzed", measurements.usable);
                    item.put("analysisMessage", measurements.message);
                    if (measurements.usable) {
                        item.put("variabilityScore", measurements.variabilityScore);
                        item.put("loudnessVariationDb", measurements.loudnessVariationDb);
                        item.put("pitchAvailable", measurements.pitchAvailable);
                        item.put("pitchVariationPercent", measurements.pitchVariationPercent);
                        item.put("durationSeconds", measurements.durationSeconds);
                        item.put("speakingSeconds", measurements.speakingSeconds);
                    }
                    JSONArray all = readHistory();
                    all.put(item);
                    getPreferences(0).edit().putString("interviews", all.toString()).apply();
                    analyzing = false;
                    showSavedResult(item, true);
                } catch (Exception error) {
                    analyzing = false;
                    status.setText("Audio saved, but the result could not be stored: " + error.getMessage());
                }
            });
        }).start();
    }

    private void advanceQuestion() {
        if (baselinePhase) {
            baselineIndex++;
            if (baselineIndex >= BASELINE.length) baselinePhase = false;
        } else {
            customIndex++;
        }
    }

    private void releaseRecorder() {
        recording = false;
        if (recorder != null) {
            try { recorder.reset(); } catch (Exception ignored) {}
            try { recorder.release(); } catch (Exception ignored) {}
            recorder = null;
        }
    }

    private JSONArray readHistory() {
        try { return new JSONArray(getPreferences(0).getString("interviews", "[]")); }
        catch (Exception error) { return new JSONArray(); }
    }

    private double baselineAverage(String session) {
        JSONArray all = readHistory();
        double total = 0;
        int count = 0;
        for (int i = 0; i < all.length(); i++) {
            JSONObject row = all.optJSONObject(i);
            if (row == null || !row.optBoolean("analyzed")) continue;
            if (!session.equals(row.optString("session"))) continue;
            if (!"baseline".equals(row.optString("category"))) continue;
            total += row.optDouble("variabilityScore", 0);
            count++;
        }
        return count == 0 ? -1 : total / count;
    }

    private void showSavedResult(JSONObject item, boolean fromRecording) {
        page("Answer analysis • " + item.optString("person"));
        label(item.optString("type") + ": " + item.optString("question"));
        label("Recorded: " + item.optString("date"));
        if (item.optBoolean("analyzed")) {
            int score = item.optInt("variabilityScore", 0);
            label("Voice-variation feature index: " + score + "% (0–100 experimental acoustic scale)");
            label(String.format(Locale.US, "Loudness variation: %.1f dB",
                    item.optDouble("loudnessVariationDb")));
            if (item.optBoolean("pitchAvailable")) {
                label(String.format(Locale.US, "Estimated pitch variation: %.1f%%",
                        item.optDouble("pitchVariationPercent")));
            } else {
                label("Pitch variation: insufficient voiced samples");
            }
            label(String.format(Locale.US, "Speech detected: %.1f seconds of %.1f seconds recorded",
                    item.optDouble("speakingSeconds"), item.optDouble("durationSeconds")));
            if ("target".equals(item.optString("category"))) {
                double baseline = baselineAverage(item.optString("session"));
                if (baseline > 0.01) {
                    double difference = 100.0 * (score - baseline) / baseline;
                    label(String.format(Locale.US,
                            "Difference from this session's baseline feature score: %+.1f%%", difference));
                } else {
                    label("No optional baseline comparison available.");
                }
            }
            label(item.optString("analysisMessage"));
        } else {
            label("Analysis unavailable for this recording: "
                    + item.optString("analysisMessage", "This older answer was not analyzed."));
        }
        label("Truth percentage: not measurable from voice. Deception percentage: not measurable from voice. These acoustic features also change with emotion, question wording, breathing, noise, and microphone distance.");
        button("Play this recorded answer", () -> play(item.optString("file")));
        if (fromRecording) {
            button("Continue to next question", this::showQuestion);
            button("Add more target questions", this::enterQuestions);
            button("Finish / Show this interview's results", this::sessionSummary);
        } else {
            button("Back to saved interviews", this::history);
        }
    }

    private void sessionSummary() {
        if (recording || analyzing) return;
        page("Interview results • " + interviewName);
        JSONArray all = readHistory();
        int count = 0;
        for (int i = 0; i < all.length(); i++) {
            JSONObject row = all.optJSONObject(i);
            if (row == null || !sessionId.equals(row.optString("session"))) continue;
            count++;
            final JSONObject item = row;
            String scoreText = item.optBoolean("analyzed")
                    ? " | Acoustic index " + item.optInt("variabilityScore") + "%"
                    : " | Not analyzed";
            button(item.optString("type") + " • " + item.optString("question") + scoreText,
                    () -> showSavedResult(item, false));
        }
        if (count == 0) label("No answers have been recorded in this interview yet.");
        else label(count + " answer(s) saved locally on this phone.");
        label("Results are voice-variation features, not truth or lie probabilities.");
        button("Add more target questions", this::enterQuestions);
        button("All saved interviews", this::history);
        button("Home", this::showHome);
    }

    private void history() {
        page("Saved interviews and results");
        EditText filter = field("Filter by person's name");
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        layout.addView(rows);
        Runnable render = () -> {
            rows.removeAllViews();
            JSONArray all = readHistory();
            for (int i = all.length() - 1; i >= 0; i--) {
                JSONObject item = all.optJSONObject(i);
                if (item == null) continue;
                String name = item.optString("person");
                if (!name.toLowerCase(Locale.US).contains(
                        filter.getText().toString().trim().toLowerCase(Locale.US))) continue;
                String metric = item.optBoolean("analyzed")
                        ? " | Acoustic index " + item.optInt("variabilityScore") + "%"
                        : " | No computed score";
                Button b = new Button(this);
                b.setAllCaps(false);
                b.setText(name + " | " + item.optString("date") + "\n"
                        + item.optString("question") + metric);
                rows.addView(b);
                b.setOnClickListener(v -> showSavedResult(item, false));
            }
        };
        filter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { render.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        render.run();
        button("Home", this::showHome);
    }

    private void play(String path) {
        try {
            File file = new File(path);
            if (!file.exists()) {
                Toast.makeText(this, "The audio file is missing.", Toast.LENGTH_SHORT).show();
                return;
            }
            MediaPlayer player = new MediaPlayer();
            player.setDataSource(file.getAbsolutePath());
            player.prepare();
            player.setOnCompletionListener(MediaPlayer::release);
            player.start();
        } catch (Exception error) {
            Toast.makeText(this, "Could not play the recording.", Toast.LENGTH_SHORT).show();
        }
    }

    private void studio() {
        page("Voice Clone Studio");
        label("Voice cloning is not enabled in this build. It requires an authorized speaker, a compatible model or configured provider, and clear synthetic-audio labeling.");
        button("Home", this::showHome);
    }

    @Override public void onBackPressed() {
        if (recording || analyzing) {
            Toast.makeText(this, "Stop or finish analyzing the current recording first.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        super.onBackPressed();
    }

    @Override protected void onDestroy() {
        releaseRecorder();
        super.onDestroy();
    }
}
