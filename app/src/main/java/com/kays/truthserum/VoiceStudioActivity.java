package com.kays.truthserum;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.widget.*;
import com.k2fsa.sherpa.onnx.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;

/** Own-voice/authorized-speaker offline zero-shot synthesis using PocketTTS. */
public final class VoiceStudioActivity extends Activity {
    private static final String MODEL = "tts/sherpa-onnx-pocket-tts-int8-2026-01-26";
    private static final int RATE = 24000;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean recording = false, busy = false;
    private AudioRecord microphone;
    private OfflineTts tts;
    private File sample, resultFile;
    private MediaPlayer player;
    private TextView status;
    private EditText text;
    private CheckBox consent;
    private Button generate, preview;
    private int steps = 2;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        show();
        worker.execute(this::loadModel);
    }
    private TextView label(LinearLayout root, String value) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(17);
        t.setTextColor(Color.WHITE); t.setPadding(8, 12, 8, 12); root.addView(t); return t;
    }
    private Button button(LinearLayout root, String name, Runnable action) {
        Button b = new Button(this); b.setText(name); b.setAllCaps(false);
        root.addView(b); b.setOnClickListener(v -> action.run()); return b;
    }
    private void show() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24,24,24,24); root.setBackgroundColor(Color.rgb(25,15,20));
        scroll.addView(root); setContentView(scroll);
        label(root, "KAY'S NATIVE VOICE STUDIO");
        label(root, "Record an 8–15 second clear voice sample. Then type text to generate new synthetic audio in that voice. Works offline after installation, using the bundled PocketTTS AI model.");
        consent = new CheckBox(this);
        consent.setText("I own this voice or have explicit consent from the speaker for synthetic voice creation.");
        consent.setTextColor(Color.WHITE);
        consent.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        root.addView(consent);
        button(root, "Start recording sample", this::startReference);
        button(root, "Stop recording", () -> recording = false);
        preview = button(root, "Play voice sample", () -> play(sample)); preview.setEnabled(false);
        text = new EditText(this); text.setTextColor(Color.WHITE);
        text.setHintTextColor(Color.LTGRAY); text.setHint("Text for the synthetic voice");
        text.setMinLines(3); text.setSingleLine(false); root.addView(text);
        button(root, "Change quality (fast / balanced / high)", () -> {
            steps = steps == 2 ? 4 : steps == 4 ? 8 : 2;
            status.setText("Selected synthesis quality: " + steps + " steps.");
        });
        generate = button(root, "Generate AI voice", this::generateAudio); generate.setEnabled(false);
        button(root, "Play generated voice", () -> play(resultFile));
        button(root, "Export generated WAV", this::export);
        status = label(root, "Loading offline AI model. You can record your sample.");
        label(root, "Generated speech is synthetic. Do not present it as a real recording of another person.");
        button(root, "Back to interview", this::finishStudio);
    }
    private void loadModel() {
        try {
            OfflineTtsPocketModelConfig pocket = new OfflineTtsPocketModelConfig();
            pocket.setLmFlow(MODEL + "/lm_flow.int8.onnx");
            pocket.setLmMain(MODEL + "/lm_main.int8.onnx");
            pocket.setEncoder(MODEL + "/encoder.onnx");
            pocket.setDecoder(MODEL + "/decoder.int8.onnx");
            pocket.setTextConditioner(MODEL + "/text_conditioner.onnx");
            pocket.setVocabJson(MODEL + "/vocab.json");
            pocket.setTokenScoresJson(MODEL + "/token_scores.json");
            pocket.setVoiceEmbeddingCacheCapacity(2);
            OfflineTtsModelConfig model = new OfflineTtsModelConfig();
            model.setPocket(pocket); model.setNumThreads(2); model.setDebug(false); model.setProvider("cpu");
            OfflineTtsConfig conf = new OfflineTtsConfig(); conf.setModel(model);
            tts = new OfflineTts(getAssets(), conf);
            runOnUiThread(() -> { generate.setEnabled(true); status.setText("PocketTTS model ready. Record a sample and type your text."); });
        } catch (Throwable error) {
            runOnUiThread(() -> status.setText("The model could not load: " + error.getClass().getSimpleName()
                + ". This device may not have enough memory or a compatible 64-bit processor."));
        }
    }
    private void startReference() {
        if (!consent.isChecked()) { status.setText("Confirm speaker consent first."); return; }
        if (recording || busy) { status.setText("Another recording or generation is active."); return; }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 9);
            status.setText("Allow microphone access and tap Start again."); return;
        }
        int minimum = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minimum < 1) { status.setText("24 kHz microphone capture unavailable."); return; }
        try {
            microphone = new AudioRecord(MediaRecorder.AudioSource.MIC, RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(minimum * 2, 9600));
            if (microphone.getState() != AudioRecord.STATE_INITIALIZED) {
                microphone.release(); microphone=null;
                status.setText("Microphone could not be initialized."); return;
            }
            microphone.startRecording(); recording=true;
            status.setText("Recording... Speak normally. Tap Stop after 8–15 seconds.");
            Thread capture = new Thread(this::captureReference, "sample-capture"); capture.start();
        } catch (Exception e) {
            recording=false;
            if (microphone!=null) {microphone.release();microphone=null;}
            status.setText("Recording error: "+e.getMessage());
        }
    }
    private void captureReference() {
        AudioRecord mic = microphone;
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        short[] buffer = new short[2400];
        int total = 0, max = RATE*15;
        try {
            while (recording && total < max) {
                int count = mic.read(buffer,0,Math.min(buffer.length,max-total));
                if (count < 0) throw new IOException("Microphone read error "+count);
                for(int i=0;i<count;i++) {stream.write(buffer[i]&255);stream.write((buffer[i]>>>8)&255);}
                total += count;
            }
            if (total < RATE*3) {
                runOnUiThread(()->status.setText("Recording too short. Use 8–15 seconds for better quality."));
            } else {
                File dir=new File(getFilesDir(),"voice_studio");
                if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create voice folder");
                File output=new File(dir,"voice_reference.wav");
                byte[] raw=stream.toByteArray();
                writeWave(output,raw,RATE);
                sample=output;
                final int seconds=total/RATE;
                runOnUiThread(()->{preview.setEnabled(true);status.setText("Voice sample saved locally ("+seconds+" seconds).");});
            }
        } catch(Exception e) {
            runOnUiThread(()->status.setText("Recording failed: "+e.getMessage()));
        } finally {
            recording=false;
            try{mic.stop();}catch(Exception ignored){}
            mic.release();
            if(microphone==mic)microphone=null;
        }
    }
    private void generateAudio() {
        String words=text.getText().toString().trim();
        if (words.isEmpty()) {status.setText("Enter words for the synthesized voice.");return;}
        if (words.length()>700) {status.setText("Please use 700 characters or fewer.");return;}
        if (!consent.isChecked()) {status.setText("Confirm the speaker's consent first.");return;}
        if (recording||busy||tts==null||sample==null||!sample.exists()) {
            status.setText("Wait for the model and record a voice sample first.");return;
        }
        busy=true;generate.setEnabled(false);status.setText("Generating speech locally; this can require substantial RAM.");
        final File reference=sample;final int quality=steps;
        worker.execute(()->{
            try {
                Wave wav=readWave(reference);
                GenerationConfig config=new GenerationConfig();
                config.setReferenceAudio(wav.samples);
                config.setReferenceSampleRate(wav.rate);
                config.setNumSteps(quality);config.setSpeed(1f);
                GeneratedAudio voice=tts.generateWithConfig(words,config);
                float[] samples=voice.getSamples();
                if(samples.length==0)throw new IOException("No audio was generated");
                byte[] pcm=new byte[samples.length*2];
                for(int i=0;i<samples.length;i++) {
                    short value=(short)Math.round(Math.max(-1,Math.min(1,samples[i]))*32767);
                    pcm[2*i]=(byte)value;pcm[2*i+1]=(byte)(value>>>8);
                }
                File output=new File(getFilesDir(),"voice_studio/SYNTHETIC_"+System.currentTimeMillis()+".wav");
                writeWave(output,pcm,voice.getSampleRate());
                resultFile=output;
                runOnUiThread(()->{
                    busy=false;generate.setEnabled(true);
                    status.setText(String.format(Locale.US,"Synthetic voice ready: %.1f seconds. Tap Play or Export.", (double)samples.length/voice.getSampleRate()));
                    play(output);
                });
            } catch(Throwable e) {
                runOnUiThread(()->{busy=false;generate.setEnabled(true);
                    status.setText("Synthesis failed: "+e.getClass().getSimpleName()+" ("+e.getMessage()+"). Try shorter text.");});
            }
        });
    }
    private static final class Wave {final float[] samples;final int rate;Wave(float[] a,int r){samples=a;rate=r;}}
    private static Wave readWave(File file)throws Exception {
        byte[] bytes;
        try(FileInputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[8192];int n;
            while((n=in.read(buf))!=-1)out.write(buf,0,n);
            bytes=out.toByteArray();
        }
        if(bytes.length<44)throw new IOException("Invalid WAV");
        ByteBuffer b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if(b.getInt(0)!=0x46464952||b.getInt(8)!=0x45564157)throw new IOException("Invalid WAV header");
        int offset=12,rate=0,channels=0,bits=0,position=-1,size=0;
        while(offset+8<=bytes.length) {
            int id=b.getInt(offset),length=b.getInt(offset+4),start=offset+8;
            if(length<0||length>bytes.length-start)throw new IOException("Invalid WAV chunk");
            if(id==0x20746D66&&length>=16){
                if(b.getShort(start)!=1)throw new IOException("WAV is not PCM");
                channels=b.getShort(start+2)&65535;rate=b.getInt(start+4);bits=b.getShort(start+14)&65535;
            }
            if(id==0x61746164){position=start;size=length;break;}
            offset=start+length+(length&1);
        }
        if(position<0||channels!=1||bits!=16||rate<8000)throw new IOException("Expected mono 16-bit WAV");
        float[] audio=new float[size/2];
        for(int i=0;i<audio.length;i++)audio[i]=b.getShort(position+i*2)/32768f;
        return new Wave(audio,rate);
    }
    private static void writeWave(File file,byte[] pcm,int rate)throws IOException {
        ByteBuffer header=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(0x46464952).putInt(36+pcm.length).putInt(0x45564157);
        header.putInt(0x20746D66).putInt(16).putShort((short)1).putShort((short)1);
        header.putInt(rate).putInt(rate*2).putShort((short)2).putShort((short)16);
        header.putInt(0x61746164).putInt(pcm.length);
        try(FileOutputStream out=new FileOutputStream(file)){out.write(header.array());out.write(pcm);}
    }
    private void play(File file) {
        if(file==null||!file.exists()){status.setText("Nothing recorded yet.");return;}
        try {
            if(player!=null){player.release();player=null;}
            player=new MediaPlayer();player.setDataSource(file.getAbsolutePath());
            player.prepare();player.start();
            status.setText("Playing "+file.getName());
        }catch(Exception e){status.setText("Playback failed: "+e.getMessage());}
    }
    private void export() {
        if(resultFile==null||!resultFile.exists()){status.setText("Generate a synthetic WAV first.");return;}
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/wav");intent.putExtra(Intent.EXTRA_TITLE,resultFile.getName());
        startActivityForResult(intent,41);
    }
    @Override protected void onActivityResult(int request,int code,Intent data){
        super.onActivityResult(request,code,data);
        if(request!=41||code!=RESULT_OK||data==null||data.getData()==null||resultFile==null)return;
        try(FileInputStream in=new FileInputStream(resultFile);
            OutputStream out=getContentResolver().openOutputStream(data.getData())){
            if(out==null)throw new IOException("No destination");
            byte[] buffer=new byte[8192];int count;
            while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
            status.setText("AI-generated speech exported.");
        }catch(Exception e){status.setText("Export failed: "+e.getMessage());}
    }
    private void finishStudio() {
        if(busy||recording){status.setText("Stop recording or finish generation before leaving.");return;}
        finish();
    }
    @Override protected void onDestroy(){
        recording=false;worker.shutdownNow();
        if(player!=null){player.release();player=null;}
        if(tts!=null){tts.release();tts=null;}
        super.onDestroy();
    }
}
