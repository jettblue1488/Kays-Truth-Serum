package com.kays.truthserum;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    LinearLayout layout;
    EditText person, question;
    Spinner category;
    TextView status;
    MediaRecorder recorder;
    File currentFile;
    String currentName, currentQuestion, currentType;
    boolean recording = false;
    final File[] selected = new File[1];

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        showHome();
    }
    TextView label(String text) {
        TextView t = new TextView(this); t.setText(text); t.setTextSize(19); t.setTextColor(Color.WHITE);
        t.setPadding(12,16,12,16); layout.addView(t); return t;
    }
    Button button(String text, Runnable action) {
        Button b = new Button(this); b.setText(text); layout.addView(b); b.setOnClickListener(v -> action.run()); return b;
    }
    void page(String title) {
        ScrollView scroll = new ScrollView(this);
        layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(22,24,22,24); layout.setBackgroundColor(Color.rgb(25,15,19));
        scroll.addView(layout); setContentView(scroll); label(title);
    }
    EditText field(String hint) {
        EditText e = new EditText(this); e.setHint(hint); e.setHintTextColor(Color.LTGRAY);
        e.setTextColor(Color.WHITE); layout.addView(e); return e;
    }
    void showHome() {
        page("KAY'S TRUTH SERUM");
        label("Interview recording and history. Vocal stress does not establish truth or deception.");
        button("New interview", this::newInterview);
        button("Saved interviews", this::history);
        button("Voice Clone Studio", this::studio);
    }
    void newInterview() {
        page("New interview");
        person = field("Interviewed person's name");
        question = field("Question to ask");
        category = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            new String[]{"Control question","Detection question"});
        category.setAdapter(a); layout.addView(category);
        status = label("Ready to record. Obtain the speaker's consent.");
        button("Start recording", () -> {
            if (recording) { status.setText("Stop the current recording first."); return; }
            if (person.getText().toString().trim().isEmpty() || question.getText().toString().trim().isEmpty()) {
                status.setText("Enter a name and question."); return;
            }
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1);
                status.setText("Allow microphone access, then tap Start again."); return;
            }
            try {
                currentName = person.getText().toString().trim();
                currentQuestion = question.getText().toString().trim();
                currentType = category.getSelectedItem().toString();
                File dir = new File(getFilesDir(),"recordings"); dir.mkdirs();
                currentFile = new File(dir, "answer_"+System.currentTimeMillis()+".m4a");
                recorder = new MediaRecorder();
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                recorder.setOutputFile(currentFile.getAbsolutePath());
                recorder.prepare(); recorder.start(); recording = true;
                status.setText("Recording... tap Stop when finished.");
            } catch (Exception e) { status.setText("Recording error: "+e.getMessage()); releaseRecorder(); }
        });
        button("Stop and save answer", () -> {
            if (!recording) { status.setText("No recording in progress."); return; }
            try {
                recorder.stop(); releaseRecorder();
                JSONObject item = new JSONObject();
                item.put("person",currentName); item.put("question",currentQuestion);
                item.put("type",currentType); item.put("file",currentFile.getAbsolutePath());
                item.put("date",new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date()));
                JSONArray all = readHistory(); all.put(item);
                getPreferences(0).edit().putString("interviews",all.toString()).apply();
                status.setText("Saved answer for "+currentName);
                question.setText("");
            } catch (Exception e) { releaseRecorder(); status.setText("Could not save recording: "+e.getMessage()); }
        });
        button("View history", this::history);
        button("Home", this::showHome);
    }
    void releaseRecorder() {
        recording = false;
        if(recorder != null) { try { recorder.reset(); recorder.release(); } catch(Exception ignored) {} recorder = null; }
    }
    JSONArray readHistory() {
        try { return new JSONArray(getPreferences(0).getString("interviews","[]")); }
        catch(Exception e) { return new JSONArray(); }
    }
    void history() {
        page("Saved interview answers");
        EditText filter = field("Search by person's name");
        LinearLayout results = new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL); layout.addView(results);
        Runnable render = () -> {
            results.removeAllViews(); JSONArray all=readHistory();
            for(int i=all.length()-1;i>=0;i--) {
                JSONObject item=all.optJSONObject(i); if(item==null)continue;
                String name=item.optString("person");
                if(!name.toLowerCase(Locale.US).contains(filter.getText().toString().toLowerCase(Locale.US)))continue;
                Button b=new Button(this);
                b.setAllCaps(false);
                b.setText(name+" | "+item.optString("date")+"\n"+item.optString("type")+": "+item.optString("question"));
                results.addView(b);
                b.setOnClickListener(v -> {
                    try {
                        File f=new File(item.optString("file"));
                        if(!f.exists()){Toast.makeText(this,"Audio file missing",Toast.LENGTH_SHORT).show();return;}
                        MediaPlayer player=new MediaPlayer(); player.setDataSource(f.getAbsolutePath());
                        player.prepare(); player.setOnCompletionListener(MediaPlayer::release); player.start();
                        Toast.makeText(this,"Playing recorded answer",Toast.LENGTH_SHORT).show();
                    } catch(Exception e){Toast.makeText(this,"Playback failed",Toast.LENGTH_SHORT).show();}
                });
            }
        };
        filter.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){render.run();}
            public void afterTextChanged(android.text.Editable e){}
        });
        render.run(); button("Home",this::showHome);
    }
    void studio() {
        page("Voice Clone Studio");
        label("Voice cloning is not enabled in this build. It requires an authorized speaker, a compatible local model or configured online provider, and clear synthetic-audio labeling.");
        button("Home",this::showHome);
    }
    @Override protected void onDestroy(){releaseRecorder();super.onDestroy();}
}
