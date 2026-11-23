package com.kays.truthserum;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Single-session interviews with optional baseline, personal questions and per-answer acoustic analysis. */
public final class MainActivity extends Activity {
    private final ExecutorService analysisWorker = Executors.newSingleThreadExecutor();
    private InterviewStore store;
    private LinearLayout root;
    private TextView status;
    private MediaRecorder recorder;
    private File pendingFile;
    private boolean recording = false, analyzing = false;
    private String activeId = "";
    private String exportId = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new InterviewStore(this);
        home();
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextSize(18); t.setTextColor(Color.WHITE);
        t.setPadding(10, 13, 10, 13); root.addView(t);
        return t;
    }
    private EditText field(String hint) {
        EditText e = new EditText(this); e.setHint(hint);
        e.setTextColor(Color.WHITE);e.setHintTextColor(Color.LTGRAY);
        e.setTextSize(18);root.addView(e);
        return e;
    }
    private Button button(String name, Runnable action) {
        Button b = new Button(this);b.setText(name);b.setAllCaps(false);
        root.addView(b);b.setOnClickListener(v -> action.run());return b;
    }
    private void page(String title) {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        root = new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(23,14,21));root.setPadding(20,22,20,22);
        scroll.addView(root);setContentView(scroll);
        label(title);
    }
    private void alert(String message) {
        Toast.makeText(this,message,Toast.LENGTH_LONG).show();
    }
    private void home() {
        page("KAY'S TRUTH SERUM • STUDIO");
        label("One person, one interview file: optional baseline, custom questions, separate answer recordings and CVSA-style acoustic measurements.");
        button("Start a new interview", this::newInterview);
        button("Saved interviews / sessions", this::history);
        button("Native Voice Clone Studio", () -> {
            startActivity(new Intent(this, VoiceStudioActivity.class));
        });
        button("What do the percentages mean?", this::explain);
        label("Voice stress analyzers cannot scientifically determine whether an answer is truthful or deceptive. Recording and comparison features are for exploratory acoustic study.");
    }
    private void newInterview() {
        if (recording || analyzing) return;
        page("NEW INTERVIEW");
        label("Each session keeps every reference and personal question together, under this participant and one date.");
        EditText name = field("Person being interviewed");
        CheckBox baseline = new CheckBox(this);
        baseline.setText("Include the optional 10-question reference baseline");
        baseline.setTextColor(Color.WHITE);baseline.setChecked(true);
        baseline.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        root.addView(baseline);
        label("For meaningful within-session comparison, the person should give several clear normal-speech reference answers. Practice false answers are excluded from the reference average.");
        button("Create interview", () -> {
            String person = name.getText().toString().trim();
            if (person.isEmpty()) {alert("Enter the participant's name.");return;}
            try {
                JSONObject session = store.create(person,baseline.isChecked());
                activeId=session.getString("id");
                addTargets(activeId);
            } catch(Exception ex) {alert("Could not save interview: "+ex.getMessage());}
        });
        button("Back",this::home);
    }
    private void addTargets(String id) {
        if (recording || analyzing) return;
        activeId=id;
        JSONObject session=store.get(id);
        if(session==null){home();return;}
        page("PERSONAL QUESTIONS • "+session.optString("person"));
        label("Enter your target questions, one per line. They are saved inside this same dated interview, along with all baseline questions.");
        EditText questions=field("Did you...?\nWhere were you...?\nWhat happened?");
        questions.setMinLines(5);questions.setSingleLine(false);
        questions.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        button("Save custom questions into this interview", () -> {
            String[] lines = questions.getText().toString().split("\\r?\\n");
            List<String> cleaned = new ArrayList<>();
            for (String line : lines) {
                String value=line.trim();
                if(!value.isEmpty() && value.length()<=500)cleaned.add(value);
            }
            if(cleaned.isEmpty()){alert("Enter at least one question.");return;}
            try {
                store.addTargets(id,cleaned);
                showSession(id);
            } catch(Exception ex){alert("Could not add questions: "+ex.getMessage());}
        });
        button("Continue to already saved questions", () -> showSession(id));
        button("Home",this::home);
    }

    private void history() {
        if(recording || analyzing)return;
        page("SAVED INTERVIEWS");
        EditText filter=field("Search participant's name");
        LinearLayout resultBox=new LinearLayout(this);
        resultBox.setOrientation(LinearLayout.VERTICAL);root.addView(resultBox);
        Runnable render=()->{
            resultBox.removeAllViews();
            JSONArray sessions=store.listSessions();
            for(int i=sessions.length()-1;i>=0;i--){
                JSONObject session=sessions.optJSONObject(i);
                if(session==null)continue;
                String person=session.optString("person","Unknown");
                if(!person.toLowerCase(Locale.US).contains(
                    filter.getText().toString().trim().toLowerCase(Locale.US)))continue;
                JSONArray answers=session.optJSONArray("items");
                int total=answers==null?0:answers.length(), recorded=0;
                for(int j=0;j<total;j++){
                    JSONObject item=answers.optJSONObject(j);
                    if(item!=null && "recorded".equals(item.optString("state"))) recorded++;
                }
                final String sessionId=session.optString("id");
                Button b=new Button(this);b.setAllCaps(false);
                b.setText(person+"\n"+session.optString("date")+"\n"+recorded+"/"+total+" recorded • One session");
                resultBox.addView(b);b.setOnClickListener(v->showSession(sessionId));
            }
        };
        filter.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            @Override public void onTextChanged(CharSequence s,int a,int b,int c){render.run();}
            @Override public void afterTextChanged(Editable e){}
        });
        render.run();button("Home",this::home);
    }

    private void showSession(String id) {
        if(recording||analyzing)return;
        activeId=id;
        JSONObject session=store.get(id);
        if(session==null){alert("This interview was not found.");home();return;}
        JSONArray items=session.optJSONArray("items");
        if(items==null){alert("Session has no question list.");return;}
        page("INTERVIEW • "+session.optString("person"));
        label("Session: "+session.optString("date")+"\nInterview ID: "+session.optString("id"));
        label("All questions, recordings and acoustic scores below belong to this single interview.");
        int answered=0;
        for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i);
            if(item==null)continue;
            if("recorded".equals(item.optString("state"))) answered++;
        }
        label("Saved answers: "+answered+" of "+items.length()+" questions");
        button("Record next unanswered question", ()->nextPending(id,0));
        button("Add more personal questions", ()->addTargets(id));
        button("Explain CVSA-style scales",this::explain);
        button("Export session report",()->exportSession(id));
        int baselineCount=0,targetCount=0;
        for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i);
            if(item==null)continue;
            String group=item.optString("category");
            if("baseline".equals(group) && baselineCount++ == 0)label("BASELINE / REFERENCE QUESTIONS");
            if("target".equals(group) && targetCount++ == 0)label("PERSONAL TARGET QUESTIONS");
            final int questionIndex=i;
            String score=item.optBoolean("analyzed")
                ?" • Voice index "+item.optInt("index")+"%"
                : " • "+item.optString("state","Pending");
            button((i+1)+". "+item.optString("question")+score,
                ()->{ if("recorded".equals(item.optString("state"))) showResult(id,questionIndex);
                      else showQuestion(id,questionIndex); });
        }
        button("All saved interviews",this::history);
        button("Home",this::home);
    }
    private void nextPending(String id,int from) {
        JSONObject session=store.get(id);
        if(session==null){home();return;}
        JSONArray questions=session.optJSONArray("items");
        if(questions==null || questions.length()==0){addTargets(id);return;}
        for(int i=Math.max(0,from);i<questions.length();i++){
            JSONObject q=questions.optJSONObject(i);
            if(q!=null && !"recorded".equals(q.optString("state")) &&
                    !"skipped".equals(q.optString("state"))){showQuestion(id,i);return;}
        }
        showSession(id);
    }
    private void showQuestion(String id,int index) {
        if(recording||analyzing)return;
        activeId=id;
        JSONObject session=store.get(id);
        if(session==null){home();return;}
        JSONArray items=session.optJSONArray("items");
        JSONObject item=items==null?null:items.optJSONObject(index);
        if(item==null){showSession(id);return;}
        page("RECORD ANSWER • "+session.optString("person"));
        label("Question "+(index+1)+" of "+items.length()+" • "+item.optString("category").toUpperCase(Locale.US));
        label(item.optString("question"));
        label("Record one answer at a time; each remains linked to this interview.");
        status=label("Ready to record.");
        button("Start recording", ()->startRecording(id,index));
        button("Stop, save, analyze", ()->stopRecording(id,index));
        if("target".equals(item.optString("category")) &&
                !"recorded".equals(item.optString("state"))) {
            button("Edit this question",()->editQuestion(id,index));
        }
        button("Skip this question",()->{
            if(recording||analyzing){status.setText("Finish recording first.");return;}
            try{store.skip(id,index);nextPending(id,index+1);}
            catch(Exception ex){status.setText("Could not skip: "+ex.getMessage());}
        });
        button("Back to interview",()->{
            if(recording||analyzing){status.setText("Finish recording first.");return;}
            showSession(id);
        });
    }
    private void editQuestion(String id,int index) {
        JSONObject session=store.get(id);
        JSONObject item=session.getJSONArray("items").optJSONObject(index);
        if(item==null)return;
        page("EDIT PERSONAL QUESTION");
        EditText e=field("Question");e.setText(item.optString("question"));
        button("Save question",()->{
            String updated=e.getText().toString().trim();
            if(updated.isEmpty()||updated.length()>500){alert("Enter a question under 500 characters.");return;}
            try{store.updateQuestion(id,index,updated);showQuestion(id,index);}
            catch(Exception ex){alert("Could not update: "+ex.getMessage());}
        });
    }
    private void startRecording(String id,int index) {
        if(recording||analyzing){status.setText("Finish the current answer first.");return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1);
            status.setText("Allow microphone permission and tap Start again.");return;
        }
        try {
            File folder=new File(getFilesDir(),"recordings");
            if(!folder.exists()&&!folder.mkdirs())throw new IllegalStateException("Cannot create recordings folder");
            pendingFile=new File(folder,"answer_"+System.currentTimeMillis()+".m4a");
            recorder=new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioEncodingBitRate(96000);
            recorder.setOutputFile(pendingFile.getAbsolutePath());
            recorder.prepare();recorder.start();recording=true;
            status.setText("Recording answer... Tap Stop, save, analyze when finished.");
        } catch(Exception ex) {releaseRecorder();status.setText("Recording failed: "+ex.getMessage());}
    }
    private void stopRecording(String id,int index) {
        if(analyzing){status.setText("Analyzing previous answer.");return;}
        if(!recording){status.setText("Start recording before saving.");return;}
        try{recorder.stop();}
        catch(Exception ex){releaseRecorder();status.setText("Too short or invalid. Please try recording again.");return;}
        releaseRecorder();
        final File audio=pendingFile;
        analyzing=true;
        status.setText("Answer saved. Calculating CVSA-inspired acoustic features...");
        analysisWorker.execute(()->{
            AudioAnalyzer.Result measured=AudioAnalyzer.analyze(audio);
            runOnUiThread(()->{
                try {
                    store.saveAudio(id,index,audio.getAbsolutePath(),measured);
                    analyzing=false;
                    showResult(id,index);
                }catch(Exception ex){
                    analyzing=false;
                    status.setText("Could not save results: "+ex.getMessage());
                }
            });
        });
    }
    private void releaseRecorder() {
        recording=false;
        if(recorder!=null){try{recorder.reset();}catch(Exception ignored){}
            try{recorder.release();}catch(Exception ignored){}recorder=null;}
    }
    private void showResult(String id,int index) {
        if(recording||analyzing)return;
        JSONObject session=store.get(id);
        if(session==null){home();return;}
        JSONArray items=session.optJSONArray("items");
        JSONObject item=items==null?null:items.optJSONObject(index);
        if(item==null){showSession(id);return;}
        page("CVSA-STYLE VOICE ANALYSIS");
        label("Participant: "+session.optString("person")+"\nInterview: "+session.optString("date"));
        label("Question: "+item.optString("question"));
        if(!"recorded".equals(item.optString("state"))) {
            label("This answer has not been recorded.");
        }else if(!item.optBoolean("analyzed")){
            label("Audio saved; analysis unavailable. "+item.optString("message"));
        }else{
            int score=item.optInt("index");
            label("ACOUSTIC VARIATION INDEX: "+score+"% — "+scale(score));
            label("0% = very little measured feature variation; 100% = high measured variation by this experimental formula. 100% does NOT mean lying, and 0% does NOT mean truthful.");
            label(String.format(Locale.US,"Loudness variation: %.1f dB",
                    item.optDouble("loudnessDb")));
            if(item.optBoolean("pitchAvailable")){
                label(String.format(Locale.US,"Pitch variation: %.1f%% of average pitch",
                        item.optDouble("pitchVariability")));
                label(String.format(Locale.US,"Frame-to-frame pitch fluctuation: %.1f%%",
                        item.optDouble("pitchFluctuation")));
            }else label("Pitch measurement: insufficient clear voiced frames.");
            if(item.optDouble("modulation",-1)>=0){
                label(String.format(Locale.US,
                        "Rapid 8–12 Hz amplitude modulation index: %.1f%% of measured envelope variation",
                        item.optDouble("modulation")));
            } else label("Rapid amplitude modulation: insufficient usable audio.");
            label(String.format(Locale.US,"Clear-speech duration: %.1f s / recorded %.1f s",
                    item.optDouble("speechSeconds"),item.optDouble("duration")));
            if("target".equals(item.optString("category")))showComparison(session,item);
            label("What this means: the algorithm measures changes in speech acoustics. Those changes can arise from language, breathing, microphone position, noise, nervousness or normal expression; none prove deception.");
            label("Truth probability: NOT measurable. Deception probability: NOT measurable.");
        }
        button("Play recorded answer",()->play(item.optString("audio")));
        button("Next unanswered question",()->nextPending(id,index+1));
        button("View the whole interview",()->showSession(id));
    }
    private void showComparison(JSONObject session,JSONObject item) {
        JSONArray rows=session.optJSONArray("items");
        double score=0,loud=0,pitch=0,mod=0;
        int scoreN=0,loudN=0,pitchN=0,modN=0;
        if(rows!=null)for(int i=0;i<rows.length();i++){
            JSONObject ref=rows.optJSONObject(i);
            if(ref==null||!"baseline".equals(ref.optString("category"))||
                    !ref.optBoolean("analyzed"))continue;
            String type=ref.optString("type");
            if(type.contains("false")||type.contains("False"))continue;
            score+=ref.optDouble("index");scoreN++;
            loud+=ref.optDouble("loudnessDb");loudN++;
            if(ref.optBoolean("pitchAvailable")){
                pitch+=ref.optDouble("pitchVariability");pitchN++;
            }
            if(ref.optDouble("modulation",-1)>=0){mod+=ref.optDouble("modulation");modN++;}
        }
        if(scoreN==0) {
            label("BASELINE COMPARISON: unavailable. Record reference questions for this same session to enable it.");
            return;
        }
        label("WITHIN-SESSION BASELINE: "+scoreN+
                " reference answers from this participant and interview (intentional false-practice answers excluded).");
        label(String.format(Locale.US,
                "Reference average acoustic index: %.1f%%. This answer: %d%% (difference: %+.1f index points).",
                score/scoreN,item.optInt("index"),item.optInt("index")-score/scoreN));
        if(loudN>0)label(String.format(Locale.US,
                "Loudness variation difference: %+.1f dB vs this session's reference.",
                item.optDouble("loudnessDb")-loud/loudN));
        if(pitchN>0&&item.optBoolean("pitchAvailable"))
            label(String.format(Locale.US,
                    "Pitch variation difference: %+.1f percentage points.",
                    item.optDouble("pitchVariability")-pitch/pitchN));
        if(modN>0&&item.optDouble("modulation",-1)>=0)
            label(String.format(Locale.US,
                    "Rapid modulation difference: %+.1f points.",
                    item.optDouble("modulation")-mod/modN));
        if(scoreN<3)label("Warning: fewer than 3 reference answers; baseline comparisons are unstable.");
    }
    private String scale(int score) {
        if(score<25)return "LOW acoustic feature variation (0–24)";
        if(score<50)return "MILD acoustic feature variation (25–49)";
        if(score<75)return "MODERATE acoustic feature variation (50–74)";
        return "HIGH acoustic feature variation (75–100)";
    }
    private void explain() {
        page("HOW THE CVSA-STYLE SCALE WORKS");
        label("The 0–100% score is a custom acoustic variation index, NOT a probability of honesty, deception, stress, or fear.");
        label("0–24: low change in measured voice features. 25–49: mild. 50–74: moderate. 75–100: high. These ranges are descriptive choices, not scientifically validated thresholds.");
        label("Inputs: loudness variation, pitch variation, and rapid amplitude modulation in the 8–12 Hz band. Depending on audio quality, some features may not be available.");
        label("The 8–12 Hz feature measures quick changes in recorded amplitude. It does not establish vocal microtremors or authenticate proprietary CVSA technology.");
        label("A session baseline is calculated only from available reference answers from the SAME interview. Intentionally false practice answers are left out. Each target question displays differences from that session's reference average.");
        label("People can sound more variable when tired, excited, afraid, uncomfortable, speaking differently, or using a different microphone distance. Speech alone does not tell you whether a statement is true.");
        button("Back",()->{if(activeId!=null&&!activeId.isEmpty())showSession(activeId);else home();});
    }
    private void play(String path) {
        if(path==null||path.isEmpty()){alert("No audio saved for this question.");return;}
        File f=new File(path);
        if(!f.exists()){alert("Audio file missing.");return;}
        try {
            MediaPlayer player=new MediaPlayer();
            player.setDataSource(f.getAbsolutePath());
            player.setOnCompletionListener(MediaPlayer::release);
            player.prepare();player.start();
        }catch(Exception ex){alert("Playback error: "+ex.getMessage());}
    }
    private void exportSession(String id) {
        exportId=id;
        JSONObject session=store.get(id);
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE,"TruthSerum_Interview_"+id+".json");
        startActivityForResult(intent,77);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=77||result!=RESULT_OK||data==null||data.getData()==null)return;
        JSONObject session=store.get(exportId);
        if(session==null){alert("Interview unavailable.");return;}
        try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
            if(out==null)throw new IllegalStateException("Cannot open destination");
            out.write(session.toString(2).getBytes(StandardCharsets.UTF_8));
            alert("Interview report saved. Audio recordings stay in the app.");
        }catch(Exception ex){alert("Export failed: "+ex.getMessage());}
    }
    @Override public void onBackPressed() {
        if(recording||analyzing){alert("Save the current answer before leaving.");return;}
        super.onBackPressed();
    }
    @Override protected void onDestroy() {
        releaseRecorder();analysisWorker.shutdownNow();super.onDestroy();
    }
}
