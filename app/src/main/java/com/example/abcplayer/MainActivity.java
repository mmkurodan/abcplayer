package com.example.abcplayer;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int REQ_RECORD = 1001;

    private EditText editAbc;
    private Button btnPlay;
    private Button btnRecord;
    private TextView txtStatus;
    private ScrollView scrollStatus;

    private AudioTrack audioTrack;
    private PlayTask currentTask;
    private RecordTask recordTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editAbc = findViewById(R.id.editAbc);
        btnPlay = findViewById(R.id.btnPlay);
        btnRecord = findViewById(R.id.btnRecord);
        txtStatus = findViewById(R.id.txtStatus);
        scrollStatus = findViewById(R.id.scrollStatus);

        btnPlay.setOnClickListener(v -> onPlayClicked());
        btnRecord.setOnClickListener(v -> onRecordClicked());
    }

    private void onPlayClicked() {
        String abc = editAbc.getText().toString().trim();
        if (abc.isEmpty()) {
            txtStatus.setText("ABC notation が空です");
            return;
        }

        stopCurrentPlayback();

        txtStatus.setText("解析・生成中...");

        currentTask = new PlayTask();
        currentTask.execute(abc);
    }

    private void onRecordClicked() {
        if (recordTask != null) {
            recordTask.stopRecording();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD);
            return;
        }
        startRecording();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            } else {
                setStatusText("録音許可がありません");
            }
        }
    }

    private void startRecording() {
        recordTask = new RecordTask();
        recordTask.execute();
    }

    private void stopCurrentPlayback() {
        if (currentTask != null) {
            currentTask.cancel(true);
            currentTask = null;
        }
        if (audioTrack != null) {
            try {
                audioTrack.stop();
            } catch (Exception ignored) {}
            audioTrack.release();
            audioTrack = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCurrentPlayback();
        if (recordTask != null) recordTask.stopRecording();
    }

    private class PlayTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            String abc = params[0];

            try {
                AbcTokenizer tokenizer = new AbcTokenizer();
                List<AbcTokenizer.Token> toks = tokenizer.tokenize(abc);

                StringBuilder sbTok = new StringBuilder();
                sbTok.append("=== Parse Log ===\n");
                for (AbcTokenizer.Token t : toks) {
                    if (!isRecognizedToken(t.text)) {
                        sbTok.append("Unrecognized: ").append(t.text).append("\n");
                    }
                }
                runOnUiThread(() -> setStatusText(sbTok.toString()));

                AbcParser parser = new AbcParser();
                Score score = parser.parseScore(abc);

                StringBuilder sb = new StringBuilder();
                sb.append("=== Voices ===\n");
                for (Map.Entry<String, List<NoteEvent>> entry : score.voices.entrySet()) {
                    sb.append("Voice: ").append(entry.getKey())
                            .append(" count=").append(entry.getValue().size())
                            .append("\n");
                }

                NoteEvent[] notes = Renderer.renderToEvents(score);
                sb.append("\n=== Rendered ===\n");
                sb.append("events=").append(notes.length).append("\n");

                runOnUiThread(() -> appendStatus(sb.toString()));

                double tempo = score.header.tempoBpm;
                double defaultLen = score.header.defaultNoteLength;
                int sampleRate = 44100;
                double secPerBeat = 60.0 / tempo;
                int totalSamples = 0;
                for (NoteEvent n : notes) {
                    double seconds = n.beats * secPerBeat;
                    totalSamples += (int) (seconds * sampleRate);
                }

                if (isCancelled()) {
                    return "キャンセルされました";
                }

                int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
                int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
                int minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);
                int chunkSamples = Math.max(1024, minBufferSize / 2);

                audioTrack = new AudioTrack.Builder()
                        .setAudioAttributes(
                                new AudioAttributes.Builder()
                                        .setUsage(AudioAttributes.USAGE_MEDIA)
                                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                        .build()
                        )
                        .setAudioFormat(
                                new AudioFormat.Builder()
                                        .setEncoding(audioFormat)
                                        .setSampleRate(sampleRate)
                                        .setChannelMask(channelConfig)
                                        .build()
                        )
                        .setBufferSizeInBytes(Math.max(minBufferSize, chunkSamples * 2))
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build();

                audioTrack.setNotificationMarkerPosition(totalSamples);
                audioTrack.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
                    @Override
                    public void onMarkerReached(AudioTrack track) {
                        runOnUiThread(() -> appendStatus("\n再生終了"));
                    }
                    @Override
                    public void onPeriodicNotification(AudioTrack track) {}
                });

                audioTrack.play();

                int written = 0;
                while (written < totalSamples) {
                    if (isCancelled()) {
                        return "キャンセルされました";
                    }
                    int toWrite = Math.min(chunkSamples, totalSamples - written);
                    short[] chunk = SineWaveSynth.generatePcmChunk(notes, sampleRate, tempo, defaultLen, written, toWrite);
                    audioTrack.write(chunk, 0, toWrite);
                    written += toWrite;
                }

                return "再生中";

            } catch (Exception e) {
                e.printStackTrace();
cd aide: " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {}

        @Override
        protected void onCancelled(String result) {
            setStatusText("キャンセル");
        }
    }

    // MVP: 録音開始〜停止まで、ピークから簡易 ABC を組み立てる（BPM 仮固定 120）
    private class RecordTask extends AsyncTask<Void, String, String> {
        private static final int SAMPLE_RATE = 44100;
        private static final int FFT_SIZE = 2048;
        private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
        private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        private static final int PEAK_COUNT = 3; // 取得ピーク数（上から順）
        private static final double BPM = 120.0; // 仮固定テンポ推定

        private AudioRecord recorder;
        private boolean running = false;
        private long startTimeMs;
        private int bufferSize;

        @Override
        protected void onPreExecute() {
            bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            if (bufferSize < FFT_SIZE) bufferSize = FFT_SIZE;
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);
            running = true;
            startTimeMs = System.currentTimeMillis();
            btnRecord.setText("録音停止");
            setStatusText("録音開始...\n");
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                short[] buffer = new short[bufferSize];
                double[] fftInput = new double[FFT_SIZE * 2];
                double[] spectrum = new double[FFT_SIZE];
                long lastOnsetMs = startTimeMs;
                StringBuilder abcBuilder = new StringBuilder();
                abcBuilder.append("X:1\nT:Recorded\nM:4/4\nL:1/4\nQ:120\nK:C\n");

                recorder.startRecording();

                while (running && !isCancelled()) {
                    int read = recorder.read(buffer, 0, buffer.length);
                    if (read <= 0) continue;

                    int len = Math.min(read, FFT_SIZE);
                    for (int i = 0; i < len; i++) {
                        fftInput[2 * i] = buffer[i];
                        fftInput[2 * i + 1] = 0;
                    }
                    for (int i = len; i < FFT_SIZE; i++) {
                        fftInput[2 * i] = 0;
                        fftInput[2 * i + 1] = 0;
                    }

                    org.jtransforms.fft.DoubleFFT_1D fft = new org.jtransforms.fft.DoubleFFT_1D(FFT_SIZE);
                    fft.complexForward(fftInput);

                    for (int i = 0; i < FFT_SIZE / 2; i++) {
                        double re = fftInput[2 * i];
                        double im = fftInput[2 * i + 1];
                        spectrum[i] = Math.sqrt(re * re + im * im);
                    }

                    int[] peakBins = findTopPeaks(spectrum, PEAK_COUNT);
                    double[] freqs = new double[peakBins.length];
                    for (int i = 0; i < peakBins.length; i++) {
                        freqs[i] = (double) peakBins[i] * SAMPLE_RATE / FFT_SIZE;
                    }

                    String chord = binsToAbc(freqs);

                    long now = System.currentTimeMillis();
                    double seconds = (now - lastOnsetMs) / 1000.0;
                    lastOnsetMs = now;
                    double beats = secondsToBeats(seconds);

                    abcBuilder.append(chord).append(lengthToToken(beats)).append(" ");

                    publishProgress("Peak: " + chord + " beats=" + beats);
                }

                recorder.stop();
                recorder.release();

                return abcBuilder.toString();
            } catch (Exception e) {
                e.printStackTrace();
                return "エラー: " + e.getMessage();
            }
        }

        @Override
        protected void onProgressUpdate(String... values) {
            appendStatus(values[0] + "\n");
        }

        @Override
        protected void onPostExecute(String result) {
            btnRecord.setText("録音");
            recordTask = null;
            editAbc.setText(result.trim());
            appendStatus("録音完了\n");
        }

        @Override
        protected void onCancelled(String result) {
            btnRecord.setText("録音");
            recordTask = null;
            appendStatus("録音キャンセル\n");
        }

        void stopRecording() { running = false; }

        private int[] findTopPeaks(double[] spectrum, int count) {
            int[] bins = new int[count];
            double[] mags = new double[count];
            for (int i = 0; i < spectrum.length; i++) {
                double m = spectrum[i];
                for (int k = 0; k < count; k++) {
                    if (m > mags[k]) {
                        for (int s = count - 1; s > k; s--) {
                            mags[s] = mags[s - 1];
                            bins[s] = bins[s - 1];
                        }
                        mags[k] = m;
                        bins[k] = i;
                        break;
                    }
                }
            }
            return bins;
        }

        private String binsToAbc(double[] freqs) {
            Arrays.sort(freqs);
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < freqs.length; i++) {
                sb.append(freqToAbc(freqs[i]));
                if (i < freqs.length - 1) sb.append(" ");
            }
            sb.append("]");
            return sb.toString();
        }

        private String freqToAbc(double freq) {
            // 440Hz を A4 とし、最も近い 12-TET の音名（オクターブ簡易）
            int midi = (int) Math.round(69 + 12 * Math.log(freq / 440.0) / Math.log(2));
            int octave = midi / 12 - 1; // MIDI octave
            int pc = (midi % 12 + 12) % 12;
            String note;
            switch (pc) {
                case 0: note = "C"; break;
                case 1: note = "^C"; break;
                case 2: note = "D"; break;
                case 3: note = "^D"; break;
                case 4: note = "E"; break;
                case 5: note = "F"; break;
                case 6: note = "^F"; break;
                case 7: note = "G"; break;
                case 8: note = "^G"; break;
                case 9: note = "A"; break;
                case 10: note = "^A"; break;
                case 11: note = "B"; break;
                default: note = "C"; break;
            }
            // 簡易オクターブ: ABC では中音 C= C のまま、上は ' を付与、下は , を付与
            if (octave >= 5) {
                int ups = octave - 4;
                for (int i = 0; i < ups; i++) note += "'";
            } else if (octave <= 3) {
                int downs = 4 - octave;
                for (int i = 0; i < downs; i++) note += ",";
            }
            return note;
        }

        private double secondsToBeats(double seconds) {
            return seconds * (BPM / 60.0);
        }

        private String lengthToToken(double beats) {
#            // L=1/4 を前提。四捨五入し、
.git .github app build.gradle push.sh settings.gradle ");
            }
            sb.append("]");
            return sb.toString();
        }

        private String freqToAbc(double freq) {
            // 440Hz を A4 とし、最も近い 12-TET の音名（オクターブ簡易）
            int midi = (int) Math.round(69 + 12 * Math.log(freq / 440.0) / Math.log(2));
            int octave = midi / 12 - 1; // MIDI octave
            int pc = (midi % 12 + 12) % 12;
            String note;
            switch (pc) {
                case 0: note = "C"; break;
                case 1: note = "^C"; break;
                case 2: note = "D"; break;
                case 3: note = "^D"; break;
                case 4: note = "E"; break;
                case 5: note = "F"; break;
                case 6: note = "^F"; break;
                case 7: note = "G"; break;
                case 8: note = "^G"; break;
                case 9: note = "A"; break;
                case 10: note = "^A"; break;
                case 11: note = "B"; break;
                default: note = "C"; break;
            }
            // 簡易オクターブ: ABC では中音 C= C のまま、上は ' を付与、下は , を付与
            if (octave >= 5) {
                int ups = octave - 4;
                for (int i = 0; i < ups; i++) note += "'";
            } else if (octave <= 3) {
                int downs = 4 - octave;
                for (int i = 0; i < downs; i++) note += ",";
            }
            return note;
        }

        private double secondsToBeats(double seconds) {
            return seconds * (BPM / 60.0);
        }

        private String lengthToToken(double beats) {
#            // L=1/4 を前提。四捨五入し、

            double b = Math.round(beats * 4.0) / 4.0; // 0.25 単位に丸め
            if (Math.abs(b - 1.0) < 1e-3) return "";    // 四分
            if (Math.abs(b - 0.5) < 1e-3) return "/2";  // 八分
            if (Math.abs(b - 0.25) < 1e-3) return "/4"; // 16分
            if (Math.abs(b - 2.0) < 1e-3) return "2";   // 二分
            if (Math.abs(b - 4.0) < 1e-3) return "4";   // 全音符
            return ""; // その他は四分にフォールバック
        }
    }

    private boolean isRecognizedToken(String t) {
        if (t == null || t.isEmpty()) return false;
        if ("|:".equals(t) || ":|".equals(t) || "||".equals(t) || "|".equals(t)) return true;
        if (t.startsWith("V:") || t.startsWith("K:") || t.startsWith("M:") || t.startsWith("L:") || t.startsWith("Q:") || t.startsWith("X:") || t.startsWith("T:") || t.startsWith("C:") || t.startsWith("P:") || t.startsWith("N:") || t.startsWith("W:") || t.startsWith("U:") || t.startsWith("I:")) return true;
        if (t.equals("[") || t.equals("]") || t.equals("z") || t.equals("Z")) return true;
        if (t.equals("^") || t.equals("_") || t.equals("=") || t.equals("'") || t.equals(",") || t.equals("-")) return true;
        if (t.equals("(") || t.equals(")")) return true;
        if (t.equals("{") || t.equals("}")) return true;
        if (t.equals("~") || t.equals(".") || t.equals(">") || t.equals("<")) return true;
        if (t.startsWith("!")) return true;
        if (t.matches("[0-9/]+")) return true;
        return t.length() == 1 && "ABCDEFGabcdefg".indexOf(t.charAt(0)) >= 0;
    }

    private void setStatusText(String text) {
        txtStatus.setText(text);
        scrollToBottom();
    }

    private void appendStatus(String text) {
        txtStatus.append(text);
        scrollToBottom();
    }

    private void scrollToBottom() {
        scrollStatus.post(() -> scrollStatus.fullScroll(ScrollView.FOCUS_DOWN));
    }
}
