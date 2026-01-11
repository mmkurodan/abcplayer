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
            audioTrack.stop();
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

        @Override
        protected String doInBackground(String... params) {
            String abc = params[0];

            try {
                NoteEvent[] notes = SimpleAbcParser.parse(abc);

                if (notes.length == 0) {
                    return "解析結果が空です";
                }

                short[] pcm = SineWaveSynth.generatePcm(
                        notes,
                        44100,
                        0.5
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
                audioTrack.play();

                return "再生中";

            } catch (Exception e) {
                e.printStackTrace();
                return "エラー: " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            txtStatus.setText(result);
        }

        @Override
        protected void onCancelled(String result) {
            txtStatus.setText("キャンセル");
        }
    }
}
