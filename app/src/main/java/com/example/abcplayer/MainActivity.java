package com.example.abcplayer;

import android.app.Activity;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    private EditText editAbc;
    private Button btnPlay;
    private TextView txtStatus;

    private AudioTrack audioTrack;
    private PlayTask currentTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editAbc = findViewById(R.id.editAbc);
        btnPlay = findViewById(R.id.btnPlay);
        txtStatus = findViewById(R.id.txtStatus);

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
                // ★ Tokenizer の結果を表示
                AbcTokenizer tokenizer = new AbcTokenizer();
                List<AbcTokenizer.Token> toks = tokenizer.tokenize(abc);

                StringBuilder sbTok = new StringBuilder();
                sbTok.append("=== Tokenizer ===\n");
                for (AbcTokenizer.Token t : toks) {
                    sbTok.append(t.text).append("\n");
                }

                runOnUiThread(() -> txtStatus.setText(sbTok.toString()));

                // ★ Score の解析
                AbcParser parser = new AbcParser();
                Score score = parser.parseScore(abc);

                // ★ Score.voices のログ出力
                StringBuilder sbVoices = new StringBuilder();
                sbVoices.append("\n=== Score.voices ===\n");

                for (Map.Entry<String, List<NoteEvent>> entry : score.voices.entrySet()) {
                    sbVoices.append("Voice: ").append(entry.getKey())
                            .append("  count=").append(entry.getValue().size())
                            .append("\n");

                    for (NoteEvent e : entry.getValue()) {
                        sbVoices.append("  beats=")
                                .append(e.beats)
                                .append(" midi=")
                                .append(Arrays.toString(e.midiNotes))
                                .append(" rest=")
                                .append(e.isRest)
                                .append("\n");
                    }
                }

                runOnUiThread(() -> txtStatus.append(sbVoices.toString()));

                // ★ Renderer の出力
                NoteEvent[] notes = Renderer.renderToEvents(score);

                StringBuilder sbRender = new StringBuilder();
                sbRender.append("\n=== Rendered Events ===\n");

                for (NoteEvent n : notes) {
                    sbRender.append("beats=")
                            .append(n.beats)
                            .append(" midi=")
                            .append(Arrays.toString(n.midiNotes))
                            .append(" rest=")
                            .append(n.isRest)
                            .append("\n");
                }

                runOnUiThread(() -> txtStatus.append(sbRender.toString()));

                // ★ PCM 生成（ストリーミング）
                double tempo = score.header.tempoBpm;
                double defaultLen = score.header.defaultNoteLength;
                int sampleRate = 44100;
                double secPerBeat = 60.0 / tempo;
                // 合計サンプル数を計算
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

                int minBufferSize = AudioTrack.getMinBufferSize(
                        sampleRate,
                        channelConfig,
                        audioFormat
                );

                // チャンクサイズ（フレーム数）
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

                // 再生終了位置（フレーム）
                audioTrack.setNotificationMarkerPosition(totalSamples);

                audioTrack.setPlaybackPositionUpdateListener(
                        new AudioTrack.OnPlaybackPositionUpdateListener() {
                            @Override
                            public void onMarkerReached(AudioTrack track) {
                                runOnUiThread(() -> txtStatus.append("\n再生終了"));
                            }

                            @Override
                            public void onPeriodicNotification(AudioTrack track) {}
                        }
                );

                audioTrack.play();

                // チャンク単位で合成・書き込み
                int written = 0;
                while (written < totalSamples) {
                    if (isCancelled()) {
                        return "キャンセルされました";
                    }
                    int toWrite = Math.min(chunkSamples, totalSamples - written);
                    short[] chunk = SineWaveSynth.generatePcmChunk(
                            notes,
                            sampleRate,
                            tempo,
                            defaultLen,
                            written,
                            toWrite
                    );
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
            txtStatus.setText("キャンセル");
        }
    }
}
