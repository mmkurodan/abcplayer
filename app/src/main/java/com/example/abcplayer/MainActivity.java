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
                AbcParser parser = new AbcParser();
                Score score = parser.parseScore(abc);

                // ★ Score.voices のログ出力
                StringBuilder sbVoices = new StringBuilder();
                sbVoices.append("=== Score.voices ===\n");

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

                runOnUiThread(() -> txtStatus.setText(sbVoices.toString()));

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

                // ★ PCM 生成
                double tempo = score.header.tempoBpm;
                double defaultLen = score.header.defaultNoteLength;

                pcm = SineWaveSynth.generatePcm(
                        notes,
                        44100,
                        tempo,
                        defaultLen
                );

                if (isCancelled()) {
                    return "キャンセルされました";
                }

                int sampleRate = 44100;
                int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
                int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

                int minBufferSize = AudioTrack.getMinBufferSize(
                        sampleRate,
                        channelConfig,
                        audioFormat
                );

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
                        .setBufferSizeInBytes(Math.max(minBufferSize, pcm.length * 2))
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();

                audioTrack.write(pcm, 0, pcm.length);

                // ★ 再生終了位置は pcm.length（重要）
                audioTrack.setNotificationMarkerPosition(pcm.length);

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

                return "再生中";

            } catch (Exception e) {
                e.printStackTrace();
                return "エラー: " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            // ここでは何もしない
        }

        @Override
        protected void onCancelled(String result) {
            txtStatus.setText("キャンセル");
        }
    }
}
