package com.kays.truthserum;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.LinkedHashMap;

/** One durable JSON document per interview, containing all baseline and target questions. */
public final class InterviewStore {
    private static final String KEY = "interview_sessions_v4";
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

    private final SharedPreferences preferences;

    public InterviewStore(Context context) {
        preferences = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE);
        // getPreferences(0) on MainActivity uses "MainActivity" in Android.
        if (!preferences.contains(KEY)) migrateLegacy();
    }

    private synchronized JSONArray readAll() {
        try { return new JSONArray(preferences.getString(KEY, "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }

    private synchronized void persist(JSONArray all) {
        // commit() makes a saved question or answer durable before moving to the next screen.
        if (!preferences.edit().putString(KEY, all.toString()).commit())
            throw new IllegalStateException("Could not save this interview");
    }

    public synchronized JSONArray listSessions() { return readAll(); }

    public synchronized JSONObject get(String id) {
        JSONArray all = readAll();
        for (int i = 0; i < all.length(); i++) {
            JSONObject session = all.optJSONObject(i);
            if (session != null && id.equals(session.optString("id"))) return session;
        }
        return null;
    }

    public synchronized JSONObject create(String person, boolean withBaseline) throws Exception {
        JSONArray all = readAll();
        JSONObject session = new JSONObject();
        String id = String.valueOf(System.currentTimeMillis());
        session.put("id", id);
        session.put("person", person);
        session.put("date", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                java.util.Locale.US).format(new java.util.Date()));
        session.put("withBaseline", withBaseline);
        JSONArray items = new JSONArray();
        if (withBaseline) {
            for (int i = 0; i < BASELINE.length; i++) {
                String type = i == 8 ? "Assisted practice false answer"
                        : i == 5 ? "Known false (verify)"
                        : i == 4 ? "Known true (verify)" : "Neutral reference speech";
                items.put(newItem(BASELINE[i], "baseline", type));
            }
        }
        session.put("items", items);
        all.put(session);
        persist(all);
        return session;
    }

    private JSONObject newItem(String question, String category, String type) throws Exception {
        JSONObject item = new JSONObject();
        item.put("question", question);
        item.put("category", category);
        item.put("type", type);
        item.put("state", "pending");
        return item;
    }

    public synchronized void addTargets(String sessionId, java.util.List<String> questions) throws Exception {
        mutate(sessionId, session -> {
            JSONArray items = session.getJSONArray("items");
            for (String question : questions) {
                if (items.length() >= 110) break;
                items.put(newItem(question, "target", "Personal / target question"));
            }
        });
    }

    public synchronized void updateQuestion(String sessionId, int index, String question) throws Exception {
        mutate(sessionId, session -> session.getJSONArray("items")
                .getJSONObject(index).put("question", question));
    }

    public synchronized void skip(String sessionId, int index) throws Exception {
        mutate(sessionId, session -> session.getJSONArray("items")
                .getJSONObject(index).put("state", "skipped"));
    }

    public synchronized void saveAudio(String sessionId, int index, String file,
                                       AudioAnalyzer.Result result) throws Exception {
        mutate(sessionId, session -> {
            JSONObject item = session.getJSONArray("items").getJSONObject(index);
            item.put("state", "recorded");
            item.put("audio", file);
            item.put("recordedAt", System.currentTimeMillis());
            item.put("analyzed", result.usable);
            item.put("message", result.message);
            if (result.usable) {
                item.put("index", result.variabilityScore);
                item.put("loudnessDb", result.loudnessVariationDb);
                item.put("pitchVariability", result.pitchVariationPercent);
                item.put("pitchAvailable", result.pitchAvailable);
                item.put("pitchFluctuation", result.pitchFluctuationPercent);
                item.put("modulation", result.rapidModulationPercent);
                item.put("duration", result.durationSeconds);
                item.put("speechSeconds", result.speakingSeconds);
            }
        });
    }

    private interface Edit { void apply(JSONObject session) throws Exception; }

    private synchronized void mutate(String id, Edit edit) throws Exception {
        JSONArray all = readAll();
        for (int i = 0; i < all.length(); i++) {
            JSONObject session = all.getJSONObject(i);
            if (id.equals(session.optString("id"))) {
                edit.apply(session);
                persist(all);
                return;
            }
        }
        throw new IllegalStateException("Interview not found");
    }

    /** Preserve old histories by importing entries sharing their original session ID. */
    private synchronized void migrateLegacy() {
        try {
            String previous = preferences.getString("interviews", "[]");
            JSONArray old = new JSONArray(previous);
            LinkedHashMap<String, JSONObject> bySession = new LinkedHashMap<>();
            for (int i = 0; i < old.length(); i++) {
                JSONObject item = old.optJSONObject(i);
                if (item == null) continue;
                String sessionId = item.optString("session", "legacy-" + i);
                if (sessionId.isEmpty()) sessionId = "legacy-" + i;
                String person = item.optString("person", "Unknown speaker");
                String key = sessionId + "|" + person;
                JSONObject session = bySession.get(key);
                if (session == null) {
                    session = new JSONObject();
                    session.put("id", sessionId + "-legacy");
                    session.put("person", person);
                    session.put("date", item.optString("date", "Previous interview"));
                    session.put("withBaseline", true);
                    session.put("items", new JSONArray());
                    bySession.put(key, session);
                }
                JSONObject converted = newItem(item.optString("question", ""),
                        item.optString("category", item.optString("type", "").contains("question")
                                && !item.optString("type", "").contains("Detection") ? "baseline"
                                : item.optString("type", "").contains("Detection") ? "target" : "baseline"),
                        item.optString("type", "Previously recorded"));
                converted.put("audio", item.optString("file", ""));
                converted.put("state", "recorded");
                converted.put("analyzed", item.optBoolean("analyzed"));
                if (item.optBoolean("analyzed")) {
                    converted.put("index", item.optInt("variabilityScore"));
                    converted.put("loudnessDb", item.optDouble("loudnessVariationDb"));
                    converted.put("pitchVariability", item.optDouble("pitchVariationPercent"));
                    converted.put("pitchAvailable", item.optBoolean("pitchAvailable"));
                    converted.put("modulation", -1);
                    converted.put("pitchFluctuation", -1);
                    converted.put("duration", item.optDouble("durationSeconds"));
                    converted.put("speechSeconds", item.optDouble("speakingSeconds"));
                }
                session.getJSONArray("items").put(converted);
            }
            JSONArray migrated = new JSONArray();
            for (JSONObject session : bySession.values()) migrated.put(session);
            persist(migrated);
        } catch (Exception exception) {
            // Do not erase legacy data if migration fails. New sessions may still be created.
            preferences.edit().putString(KEY, "[]").apply();
        }
    }
}
