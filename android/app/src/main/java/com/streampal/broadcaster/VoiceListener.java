package com.streampal.broadcaster;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Offline voice-command listener: "Peak, <command>".
 *
 * Fetches the station's active commands from the gatekeeper, listens with a
 * small offline Vosk model limited to exactly those phrases, and posts the
 * matched command to /api/trigger. Own AudioRecord (voice-recognition source),
 * so the encoder's microphone capture is not touched.
 */
public class VoiceListener {

    public interface Listener { void onStatus(String status); }

    private static final String API_BASE  = "https://live.xsen.fun:8443/api/trigger";
    private static final String MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip";
    private static final String[] WAKE    = {"peak", "peek", "pique"};
    private static final int SAMPLE_RATE  = 16000;
    private static final long COOLDOWN_MS = 6000;

    private final Context ctx;
    private final String streamKey;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean running = false;
    private Thread worker;

    public VoiceListener(Context ctx, String streamKey, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.streamKey = streamKey;
        this.listener = listener;
    }

    public boolean isRunning() { return running; }

    public void start() {
        if (running) return;
        running = true;
        worker = new Thread(this::run, "voice-listener");
        worker.start();
    }

    public void stop() {
        running = false;
        if (worker != null) worker.interrupt();
    }

    private void status(final String s) {
        main.post(() -> listener.onStatus(s));
    }

    private void run() {
        Model model = null;
        Recognizer rec = null;
        AudioRecord audio = null;
        try {
            status("Loading commands...");
            List<String> phrases = fetchPhrases();
            if (phrases.isEmpty()) { status("No commands (is the feed live?)"); running = false; return; }

            File modelDir = ensureModel();
            if (modelDir == null) { status("Voice model download failed"); running = false; return; }

            LibVosk.setLogLevel(LogLevel.WARNINGS);
            model = new Model(modelDir.getAbsolutePath());
            rec = new Recognizer(model, SAMPLE_RATE, buildGrammar(phrases));

            int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int bufSize = Math.max(minBuf, SAMPLE_RATE);
            audio = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize);
            if (audio.getState() != AudioRecord.STATE_INITIALIZED) {
                status("Microphone busy"); running = false; return;
            }
            audio.startRecording();
            status("Listening");

            byte[] buf = new byte[4096];
            long lastFire = 0;
            while (running) {
                int n = audio.read(buf, 0, buf.length);
                if (n <= 0) continue;
                if (rec.acceptWaveForm(buf, n)) {
                    String text = new JSONObject(rec.getResult()).optString("text", "").trim();
                    String cmd = matchCommand(text, phrases);
                    long now = System.currentTimeMillis();
                    if (cmd != null && now - lastFire > COOLDOWN_MS) {
                        lastFire = now;
                        boolean ok = postTrigger(cmd);
                        status(ok ? "Sent: " + cmd : "Not sent: " + cmd);
                    }
                }
            }
        } catch (Exception e) {
            status("Voice error: " + e.getMessage());
        } finally {
            running = false;
            try { if (audio != null) { audio.stop(); audio.release(); } } catch (Exception ignored) {}
            try { if (rec != null) rec.close(); } catch (Exception ignored) {}
            try { if (model != null) model.close(); } catch (Exception ignored) {}
            status("Voice off");
        }
    }

    // wake word + exact command, e.g. "peak sponsor one" -> "sponsor one"
    private String matchCommand(String text, List<String> phrases) {
        String t = text.toLowerCase();
        for (String w : WAKE) {
            if (t.startsWith(w + " ")) {
                String rest = t.substring(w.length() + 1).trim();
                for (String p : phrases) if (p.toLowerCase().equals(rest)) return p;
            }
        }
        return null;
    }

    private String buildGrammar(List<String> phrases) throws Exception {
        JSONArray g = new JSONArray();
        for (String p : phrases) for (String w : WAKE) g.put(w + " " + p.toLowerCase());
        g.put("[unk]");
        return g.toString();
    }

    private List<String> fetchPhrases() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(API_BASE + "/phrases?key=" + streamKey).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(8000);
        List<String> out = new ArrayList<>();
        if (c.getResponseCode() != 200) return out;
        JSONObject j = new JSONObject(readAll(c.getInputStream()));
        JSONArray a = j.optJSONArray("phrases");
        if (a != null) for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        return out;
    }

    private boolean postTrigger(String phrase) {
        try {
            JSONObject body = new JSONObject();
            body.put("key", streamKey);
            body.put("phrase", phrase);
            HttpURLConnection c = (HttpURLConnection) new URL(API_BASE).openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(8000); c.setReadTimeout(8000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            return c.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[2048]; int n;
        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        return bo.toString("UTF-8");
    }

    // One-time download of the small offline English model (~40 MB), then reused.
    private File ensureModel() {
        File dir = new File(ctx.getFilesDir(), "vosk-model");
        if (new File(dir, "am").isDirectory()) return dir;
        try {
            status("Downloading voice model (one time)...");
            File tmp = new File(ctx.getFilesDir(), "vosk-model.tmp");
            deleteRecursive(tmp);
            if (!tmp.mkdirs()) return null;
            HttpURLConnection c = (HttpURLConnection) new URL(MODEL_URL).openConnection();
            c.setConnectTimeout(15000); c.setReadTimeout(30000);
            try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(c.getInputStream()))) {
                ZipEntry e;
                String canonRoot = tmp.getCanonicalPath() + File.separator;
                while ((e = zin.getNextEntry()) != null) {
                    String name = e.getName();
                    int slash = name.indexOf('/');
                    if (slash < 0) continue;                 // skip the top-level folder entry itself
                    String rel = name.substring(slash + 1);   // strip the top folder
                    if (rel.isEmpty()) continue;
                    File out = new File(tmp, rel);
                    if (!out.getCanonicalPath().startsWith(canonRoot)) continue; // zip-slip guard
                    if (e.isDirectory()) { out.mkdirs(); continue; }
                    out.getParentFile().mkdirs();
                    try (FileOutputStream fo = new FileOutputStream(out)) {
                        byte[] b = new byte[8192]; int n;
                        while ((n = zin.read(b)) > 0) fo.write(b, 0, n);
                    }
                }
            }
            deleteRecursive(dir);
            if (!tmp.renameTo(dir)) return null;
            return new File(dir, "am").isDirectory() ? dir : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void deleteRecursive(File f) {
        if (!f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRecursive(k);
        f.delete();
    }
}
