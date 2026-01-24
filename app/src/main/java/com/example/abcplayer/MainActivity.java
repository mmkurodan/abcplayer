package com.example.abcplayer;

import android.app.Activity;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    private EditText editAbc;
    private Button btnPlay;
    private TextView txtStatus;
    private ScrollView scrollStatus;

    private AudioTrack audioTrack;
    private PlayTask currentTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editAbc = findViewById(R.id.editAbc);
        btnPlay = findViewById(R.id.btnPlay);
        txtStatus = findViewById(R.id.txtStatus);
        scrollStatus = findViewById(R.id.scrollStatus);

        btnPlay.setOnClickListener(v -> onPlayClicked());
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
    }

    private class PlayTask extends AsyncTask<String, Void, String> {

        private short[] pcm;

        @Override
        protected String doInBackground(String... params) {
            String abc = params[0];

            try {
                AbcTokenizer tokenizer = new AbcTokenizer();
                List<AbcTokenizer.Token> toks = tokenizer.tokenize(abc);

                // トークン表示は中止: 代わりに解釈できないトークンを抽出
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

                // Score/Render ログ
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

                // PCM 生成
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
                return "エラー: " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {}

        @Override
        protected void onCancelled(String result) {
            setStatusText("キャンセル");
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
