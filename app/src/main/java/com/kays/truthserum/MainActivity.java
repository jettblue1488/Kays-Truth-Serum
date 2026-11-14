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
    static final String[] BASELINE = {
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
    String interviewName = "";
    String sessionId = "";
    int step = 0;
    java.util.ArrayList<String> detectionQuestions = new java.util.ArrayList<>();
    void newInterview() {
        page("Start guided interview");
        EditText name = field("Interviewed person's name");
        label("The first 10 prompts establish speech samples, not a scientifically validated lie-detection baseline. The speaker should know when a practice answer is intentionally false.");
        button("Begin 10-question baseline", () -> {
            if(name.getText().toString().trim().isEmpty()) { Toast.makeText(this,"Enter a name",Toast.LENGTH_SHORT).show();return; }
            interviewName=name.getText().toString().trim();
            sessionId=String.valueOf(System.currentTimeMillis());
            step=0; detectionQuestions.clear(); showGuidedQuestion();
        });
        button("Home",this::showHome);
    }
    void showGuidedQuestion() {
        if(recording) { Toast.makeText(this,"Stop recording first",Toast.LENGTH_SHORT).show();return; }
        boolean baseline=step<BASELINE.length;
        int customIndex=step-BASELINE.length;
        if(!baseline && customIndex>=detectionQuestions.size()) { addDetectionQuestion();return; }
        String prompt=baseline?BASELINE[step]:detectionQuestions.get(customIndex);
        String type=baseline?(step==8?"Assisted practice false answer":step==5?"Known false / verify":step==4?"Known true / verify":"Neutral / speech sample"):"Detection question";
        page((baseline?"Baseline "+(step+1)+"/10":"Personal question "+(customIndex+1))+" • "+interviewName);
        label("Type: "+type);
        label(prompt);
        if(baseline && (step==4 || step==5)) label("Confirm the statement's truth or falsity with the participant before recording.");
        status=label("Record the answer, then save to continue.");
        button("Start recording",()->startGuidedRecording(prompt,type));
        button("Stop, save and next",()->stopGuidedRecording());
        if(!baseline) button("Add another personal question",()->{if(recording){status.setText("Stop recording first.");return;}addDetectionQuestion();});
        button("Finish interview",()->{if(recording){status.setText("Stop recording first.");return;}showHome();});
    }
    void addDetectionQuestion() {
        page("Individual questions • "+interviewName);
        label("Baseline complete. Enter a question specific to this person.");
        EditText q=field("Question");
        button("Add and record question",()->{
            String value=q.getText().toString().trim();
            if(value.isEmpty()){Toast.makeText(this,"Enter a question",Toast.LENGTH_SHORT).show();return;}
            detectionQuestions.add(value);
            step=BASELINE.length+detectionQuestions.size()-1;
            showGuidedQuestion();
        });
        button("Finish interview",this::showHome);
    }
    void startGuidedRecording(String prompt,String type) {
        if(recording){status.setText("Already recording.");return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1);
            status.setText("Grant microphone permission, then tap Start again.");return;
        }
        try {
            currentName=interviewName;currentQuestion=prompt;currentType=type;
            File dir=new File(getFilesDir(),"recordings");dir.mkdirs();
            currentFile=new File(dir,"answer_"+System.currentTimeMillis()+".m4a");
            recorder=new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setOutputFile(currentFile.getAbsolutePath());
            recorder.prepare();recorder.start();recording=true;
            status.setText("Recording answer...");
        } catch(Exception e){releaseRecorder();status.setText("Recording error: "+e.getMessage());}
    }
    void stopGuidedRecording() {
        if(!recording){status.setText("Start a recording first.");return;}
        try {
            recorder.stop();releaseRecorder();
            JSONObject item=new JSONObject();
            item.put("person",currentName);item.put("question",currentQuestion);
            item.put("type",currentType);item.put("file",currentFile.getAbsolutePath());
            item.put("session",sessionId);item.put("sequence",step+1);
            item.put("date",new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date()));
            JSONArray all=readHistory();all.put(item);
            getPreferences(0).edit().putString("interviews",all.toString()).apply();
            step++;
            if(step==BASELINE.length || step>BASELINE.length+detectionQuestions.size()-1) addDetectionQuestion();
            else showGuidedQuestion();
        } catch(Exception e){releaseRecorder();status.setText("Unable to save answer: "+e.getMessage());}
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
