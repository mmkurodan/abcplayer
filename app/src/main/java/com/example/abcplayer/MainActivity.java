package com.example.abcplayer;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.abcplayer.audio.AnalysisConfig;
import com.example.abcplayer.audio.AudioAnalysisEngine;
import com.example.abcplayer.audio.AudioAnalysisResult;
import com.example.abcplayer.audio.DetectedPitch;
import com.example.abcplayer.audio.PolyphonyMode;
import com.example.abcplayer.audio.tempo_calibration.PlaybackTempoPlan;
import com.example.abcplayer.audio.tempo_calibration.TempoCalibrator;
import com.example.abcplayer.audio.tempo_calibration.TempoMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int REQ_RECORD = 1001;
    private static final int DEFAULT_TEMPO_BPM = 120;
    private static final int DEFAULT_SAMPLE_RATE = 44100;
    private static final int MIN_THRESHOLD_MULTIPLIER = 1;
    private static final int DEFAULT_THRESHOLD_MULTIPLIER = 200;
    private static final int MAX_THRESHOLD_MULTIPLIER = 400;
    private static final double MIN_RECORDABLE_BEATS = 1.0 / 8.0;
    private static final int[] INPUT_SAMPLE_RATE_CANDIDATES = {48000, 44100, 32000, 22050, 16000};
    private static final int MIN_POLYPHONY_COUNT = 1;
    private static final int MAX_POLYPHONY_COUNT = 8;
    private static final int DEFAULT_POLYPHONY_COUNT = 4;

    private EditText editAbc;
    private EditText editTempo;
    private Button btnPlay;
    private Button btnRecord;
    private SeekBar seekThreshold;
    private TextView txtThresholdValue;
    private Button btnPolyphonyAuto;
    private Button btnPolyphonyFixed;
    private SeekBar seekPolyphonyCount;
    private TextView txtPolyphonyCount;
    private TextView txtStatus;
    private ScrollView scrollStatus;
    private SpectrumView spectrumView;

    private AudioTrack audioTrack;
    private PlayTask currentTask;
    private AudioMonitorTask audioMonitorTask;

    private volatile int thresholdMultiplier = DEFAULT_THRESHOLD_MULTIPLIER;
    private volatile PolyphonyMode polyphonyMode = PolyphonyMode.AUTOMATIC;
    private volatile int polyphonyCount = DEFAULT_POLYPHONY_COUNT;
    private boolean shouldStartRecordingAfterPermission;
    private boolean monitoringWanted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editAbc = findViewById(R.id.editAbc);
        editTempo = findViewById(R.id.editTempo);
        btnPlay = findViewById(R.id.btnPlay);
        btnRecord = findViewById(R.id.btnRecord);
        seekThreshold = findViewById(R.id.seekThreshold);
        txtThresholdValue = findViewById(R.id.txtThresholdValue);
        btnPolyphonyAuto = findViewById(R.id.btnPolyphonyAuto);
        btnPolyphonyFixed = findViewById(R.id.btnPolyphonyFixed);
        seekPolyphonyCount = findViewById(R.id.seekPolyphonyCount);
        txtPolyphonyCount = findViewById(R.id.txtPolyphonyCount);
        txtStatus = findViewById(R.id.txtStatus);
        scrollStatus = findViewById(R.id.scrollStatus);
        spectrumView = findViewById(R.id.spectrumView);

        configureThresholdSlider();
        configurePolyphonyControls();
        spectrumView.updateSpectrum(new float[SpectrumView.NOTE_COUNT], 0f, "待機中");

        btnPlay.setOnClickListener(v -> onPlayClicked());
        btnRecord.setOnClickListener(v -> onRecordClicked());

        monitoringWanted = true;
        if (hasRecordPermission()) {
            startAudioMonitoringIfNeeded();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD);
            appendStatus("音声グラフ表示にはマイク権限が必要です\n");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        monitoringWanted = true;
        if (hasRecordPermission()) {
            startAudioMonitoringIfNeeded();
        }
    }

    @Override
    protected void onPause() {
        monitoringWanted = false;
        stopAudioMonitoring();
        super.onPause();
    }

    private void configureThresholdSlider() {
        seekThreshold.setMax(MAX_THRESHOLD_MULTIPLIER - MIN_THRESHOLD_MULTIPLIER);
        seekThreshold.setProgress(DEFAULT_THRESHOLD_MULTIPLIER - MIN_THRESHOLD_MULTIPLIER);
        updateThresholdLabel(DEFAULT_THRESHOLD_MULTIPLIER);
        seekThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                thresholdMultiplier = MIN_THRESHOLD_MULTIPLIER + progress;
                updateThresholdLabel(thresholdMultiplier);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void updateThresholdLabel(int value) {
        txtThresholdValue.setText(String.format(Locale.US, "閾値倍率: %d", value));
    }

    private void configurePolyphonyControls() {
        seekPolyphonyCount.setMax(MAX_POLYPHONY_COUNT - MIN_POLYPHONY_COUNT);
        seekPolyphonyCount.setProgress(DEFAULT_POLYPHONY_COUNT - MIN_POLYPHONY_COUNT);
        updatePolyphonyLabel(DEFAULT_POLYPHONY_COUNT);

        btnPolyphonyAuto.setOnClickListener(v -> setPolyphonyMode(PolyphonyMode.AUTOMATIC));
        btnPolyphonyFixed.setOnClickListener(v -> setPolyphonyMode(PolyphonyMode.FIXED));

        seekPolyphonyCount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                polyphonyCount = MIN_POLYPHONY_COUNT + progress;
                updatePolyphonyLabel(polyphonyCount);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        updatePolyphonyModeButtons();
    }

    private void setPolyphonyMode(PolyphonyMode mode) {
        polyphonyMode = mode;
        updatePolyphonyModeButtons();
    }

    private void updatePolyphonyModeButtons() {
        if (polyphonyMode == PolyphonyMode.AUTOMATIC) {
            btnPolyphonyAuto.setEnabled(false);
            btnPolyphonyFixed.setEnabled(true);
            seekPolyphonyCount.setEnabled(false);
        } else {
            btnPolyphonyAuto.setEnabled(true);
            btnPolyphonyFixed.setEnabled(false);
            seekPolyphonyCount.setEnabled(true);
        }
    }

    private void updatePolyphonyLabel(int count) {
        txtPolyphonyCount.setText(String.valueOf(count));
    }

    private void onPlayClicked() {
        String abc = editAbc.getText().toString().trim();
        if (abc.isEmpty()) {
            txtStatus.setText("ABC notation が空です");
            return;
        }

        stopCurrentPlayback();
        txtStatus.setText("解析・生成中...\n");

        currentTask = new PlayTask();
        currentTask.execute(new PlaybackRequest(abc, editTempo.getText().toString().trim()));
    }

    private void onRecordClicked() {
        if (!hasRecordPermission()) {
            shouldStartRecordingAfterPermission = true;
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD);
            return;
        }

        startAudioMonitoringIfNeeded();
        if (audioMonitorTask == null) {
            appendStatus("音声監視を開始できませんでした\n");
            return;
        }

        if (audioMonitorTask.isRecording()) {
            audioMonitorTask.stopRecordingSession(false);
        } else {
            audioMonitorTask.startRecordingSession(getInputTempoBpm());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startAudioMonitoringIfNeeded();
                if (shouldStartRecordingAfterPermission && audioMonitorTask != null) {
                    audioMonitorTask.startRecordingSession(getInputTempoBpm());
                }
            } else {
                setStatusText("録音権限がありません\n音声グラフは表示できません\n");
            }
            shouldStartRecordingAfterPermission = false;
        }
    }

    private void startAudioMonitoringIfNeeded() {
        if (!hasRecordPermission() || audioMonitorTask != null) {
            return;
        }
        audioMonitorTask = new AudioMonitorTask();
        audioMonitorTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void stopAudioMonitoring() {
        if (audioMonitorTask != null) {
            audioMonitorTask.stopMonitoring();
            audioMonitorTask.cancel(true);
        }
    }

    private boolean hasRecordPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private double getInputTempoBpm() {
        String tempoText = editTempo.getText().toString().trim();
        if (tempoText.isEmpty()) {
            return DEFAULT_TEMPO_BPM;
        }
        try {
            double value = Double.parseDouble(tempoText);
            return value > 0 ? value : DEFAULT_TEMPO_BPM;
        } catch (NumberFormatException e) {
            appendStatus("テンポ入力が不正なため 120 BPM を使います\n");
            return DEFAULT_TEMPO_BPM;
        }
    }

    private double getPlaybackTempoBpm(String tempoText, double fallbackBpm) {
        double safeFallback = fallbackBpm > 0.0 ? fallbackBpm : DEFAULT_TEMPO_BPM;
        if (tempoText == null || tempoText.trim().isEmpty()) {
            return safeFallback;
        }
        try {
            double value = Double.parseDouble(tempoText.trim());
            return value > 0.0 ? value : safeFallback;
        } catch (NumberFormatException e) {
            runOnUiThread(() -> appendStatus(String.format(Locale.US, "テンポ入力が不正なため %.2f BPM を使います\n", safeFallback)));
            return safeFallback;
        }
    }

    private int resolveInputSampleRate() {
        for (int candidate : INPUT_SAMPLE_RATE_CANDIDATES) {
            int minBufferSize = AudioRecord.getMinBufferSize(candidate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minBufferSize > 0) {
                return candidate;
            }
        }
        return DEFAULT_SAMPLE_RATE;
    }

    private int resolvePlaybackSampleRate() {
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager != null) {
            String property = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
            if (property != null) {
                try {
                    int sampleRate = Integer.parseInt(property);
                    if (sampleRate > 0) {
                        return sampleRate;
                    }
                } catch (NumberFormatException ignored) {
                    // Fall back to the app default sample rate below.
                }
            }
        }
        return DEFAULT_SAMPLE_RATE;
    }

    private String stablePitchesToAbc(List<DetectedPitch> stablePitches) {
        if (stablePitches == null || stablePitches.isEmpty()) {
            return "z";
        }
        if (stablePitches.size() == 1) {
            return stablePitches.get(0).toAbcNote();
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < stablePitches.size(); i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(stablePitches.get(i).toAbcNote());
        }
        builder.append(']');
        return builder.toString();
    }

    private void stopCurrentPlayback() {
        if (currentTask != null) {
            currentTask.cancel(true);
            currentTask = null;
        }
        if (audioTrack != null) {
            if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack.stop();
            }
            audioTrack.release();
            audioTrack = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCurrentPlayback();
        stopAudioMonitoring();
    }

    private class PlayTask extends AsyncTask<PlaybackRequest, Void, String> {
        @Override
        protected String doInBackground(PlaybackRequest... params) {
            PlaybackRequest request = params[0];
            String abc = request.abcText;
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

                TempoMetadata tempoMetadata = TempoMetadata.fromAbc(abc);
                double requestedTempo = getPlaybackTempoBpm(request.tempoText, score.header.tempoBpm);
                PlaybackTempoPlan playbackPlan = PlaybackTempoPlan.fromMetadata(tempoMetadata, requestedTempo);
                double tempo = playbackPlan.correctedTempoBpm;
                double defaultLen = score.header.defaultNoteLength;
                int sampleRate = resolvePlaybackSampleRate();
                int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
                int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
                int minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);
                if (minBufferSize <= 0 && sampleRate != DEFAULT_SAMPLE_RATE) {
                    sampleRate = DEFAULT_SAMPLE_RATE;
                    minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);
                }
                if (minBufferSize <= 0) {
                    return "エラー: 再生バッファを確保できません";
                }
                int chunkSamples = Math.max(1024, minBufferSize / 2);

                double secPerBeat = 60.0 / tempo;
                int totalSamples = 0;
                for (NoteEvent n : notes) {
                    double seconds = n.beats * secPerBeat;
                    totalSamples += (int) (seconds * sampleRate);
                }

                if (isCancelled()) {
                    return "キャンセル";
                }

                int playbackSampleRate = sampleRate;
                runOnUiThread(() -> appendStatus(String.format(
                        Locale.US,
                        "再生テンポ 指定=%.2f 実測=%.2f 補正後=%.2f 補正係数=%.5f sampleRate=%d\n",
                        playbackPlan.requestedBpm,
                        playbackPlan.actualBpm,
                        playbackPlan.correctedTempoBpm,
                        playbackPlan.correctionFactor,
                        playbackSampleRate
                )));

                audioTrack = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(audioFormat)
                                .setSampleRate(sampleRate)
                                .setChannelMask(channelConfig)
                        .build())
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
                        return "キャンセル";
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
        protected void onCancelled(String result) {
            setStatusText("キャンセル");
        }
    }

    private class AudioMonitorTask extends AsyncTask<Void, MonitorUpdate, String> {
        private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
        private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

        private final Object recordingLock = new Object();
        private final int sampleRate;
        private final AnalysisConfig analysisConfig;
        private final AudioAnalysisEngine analysisEngine;
        private final List<RecordedSegment> recordedSegments = new ArrayList<>();

        private volatile boolean running = true;
        private volatile boolean recording;

        private AudioRecord recorder;
        private String currentChord;
        private double currentDurationSec;
        private double recordingTempoBpm = DEFAULT_TEMPO_BPM;
        private TempoCalibrator tempoCalibrator;

        AudioMonitorTask() {
            sampleRate = resolveInputSampleRate();
            analysisConfig = AnalysisConfig.realtimeDefaults(sampleRate);
            analysisConfig.polyphonyMode = polyphonyMode;
            analysisConfig.fixedPolyphonyCount = polyphonyCount;
            analysisEngine = new AudioAnalysisEngine(analysisConfig);
        }

        @Override
        protected void onPreExecute() {
            appendStatus(String.format(
                    Locale.US,
                    "音声監視を開始します sampleRate=%dHz frame=%d\n",
                    sampleRate,
                    analysisConfig.fftSize
            ));
        }

        @Override
        protected String doInBackground(Void... voids) {
            int minBufferBytes = AudioRecord.getMinBufferSize(sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT);
            if (minBufferBytes <= 0) {
                return "エラー: 録音バッファを確保できません";
            }
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                return "録音権限がありません";
            }

            int frameSize = analysisConfig.fftSize;
            int bufferSizeBytes = Math.max(minBufferBytes, frameSize * 2);
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSizeBytes);
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                recorder.release();
                recorder = null;
                return "エラー: AudioRecord の初期化に失敗しました";
            }

            short[] buffer = new short[frameSize];

            try {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    return "録音権限がありません";
                }
                recorder.startRecording();

                while (running && !isCancelled()) {
                    long readStartNanos = SystemClock.elapsedRealtimeNanos();
                    int read = recorder.read(buffer, 0, buffer.length);
                    long readEndNanos = SystemClock.elapsedRealtimeNanos();
                    if (read <= 0) {
                        continue;
                    }

                    double measuredFrameDurationSec = Math.max(
                            read / (double) sampleRate,
                            (readEndNanos - readStartNanos) / 1_000_000_000.0
                    );
                    AudioAnalysisResult frame = analysisEngine.analyze(buffer, read, measuredFrameDurationSec, thresholdMultiplier);
                    updateRecording(frame, measuredFrameDurationSec, readEndNanos);
                    publishProgress(new MonitorUpdate(frame.noteMagnitudes, (float) frame.threshold, frame.noteSummary));
                }
                return null;
            } catch (SecurityException e) {
                e.printStackTrace();
                return "録音権限がありません";
            } catch (IllegalStateException e) {
                e.printStackTrace();
                return "エラー: 録音を開始できません";
            } catch (Exception e) {
                e.printStackTrace();
                return "エラー: " + e.getMessage();
            } finally {
                analysisEngine.reset();
                releaseRecorder();
            }
        }

        @Override
        protected void onProgressUpdate(MonitorUpdate... values) {
            MonitorUpdate update = values[0];
            spectrumView.updateSpectrum(update.noteMagnitudes, update.threshold, update.summary);
        }

        @Override
        protected void onPostExecute(String result) {
            if (audioMonitorTask == this) {
                audioMonitorTask = null;
            }
            btnRecord.setText("録音");
            if (result != null && !result.isEmpty()) {
                appendStatus(result + "\n");
            }
        }

        @Override
        protected void onCancelled(String result) {
            if (audioMonitorTask == this) {
                audioMonitorTask = null;
            }
            btnRecord.setText("録音");
            if (result != null && !result.isEmpty()) {
                appendStatus(result + "\n");
            }
            if (monitoringWanted && hasRecordPermission()) {
                startAudioMonitoringIfNeeded();
            }
        }

        boolean isRecording() {
            return recording;
        }

        void startRecordingSession(double tempoBpm) {
            synchronized (recordingLock) {
                if (recording) {
                    return;
                }
                recording = true;
                recordingTempoBpm = tempoBpm;
                recordedSegments.clear();
                currentChord = null;
                currentDurationSec = 0.0;
                tempoCalibrator = new TempoCalibrator(tempoBpm, analysisConfig.minimumOnsetIntervalMs);
                tempoCalibrator.start(SystemClock.elapsedRealtimeNanos());
            }
            btnRecord.setText("録音停止");
            appendStatus(String.format(
                    Locale.US,
                    "録音開始 BPM=%.2f 閾値倍率=%d frame=%d\n",
                    tempoBpm,
                    thresholdMultiplier,
                    analysisConfig.fftSize
            ));
        }

        void stopRecordingSession(boolean dueToMonitoringStop) {
            final String abcText;
            final TempoMetadata tempoMetadata;
            synchronized (recordingLock) {
                if (!recording) {
                    return;
                }
                flushCurrentSegmentLocked();
                tempoMetadata = tempoCalibrator != null
                        ? tempoCalibrator.finish(SystemClock.elapsedRealtimeNanos())
                        : TempoMetadata.identity(recordingTempoBpm);
                abcText = finishRecordingLocked(tempoMetadata);
                recording = false;
                tempoCalibrator = null;
            }

            btnRecord.setText("録音");
            editAbc.setText(abcText.trim());
            appendStatus(String.format(
                    Locale.US,
                    "%s 実測BPM=%.2f 補正係数=%.5f\n",
                    dueToMonitoringStop ? "音声監視停止に伴い録音を終了しました" : "録音完了",
                    tempoMetadata.actualBpm,
                    tempoMetadata.correctionFactor
            ));
        }

        void stopMonitoring() {
            if (recording) {
                stopRecordingSession(true);
            }
            running = false;
        }

        private void updateRecording(AudioAnalysisResult frame, double frameSeconds, long timestampNanos) {
            synchronized (recordingLock) {
                if (!recording) {
                    return;
                }
                if (tempoCalibrator != null) {
                    tempoCalibrator.observeDetectedPitches(frame.stablePitches, timestampNanos);
                }

                String chord = stablePitchesToAbc(frame.stablePitches);
                if (currentChord == null) {
                    currentChord = chord;
                    currentDurationSec = frameSeconds;
                    return;
                }
                if (currentChord.equals(chord)) {
                    currentDurationSec += frameSeconds;
                    return;
                }
                flushCurrentSegmentLocked();
                currentChord = chord;
                currentDurationSec = frameSeconds;
            }
        }

        private void flushCurrentSegmentLocked() {
            if (currentChord == null || currentDurationSec <= 0.0) {
                return;
            }
            double beats = secondsToBeats(currentDurationSec, recordingTempoBpm);
            if (beats >= MIN_RECORDABLE_BEATS) {
                double quantizedBeats = Math.max(MIN_RECORDABLE_BEATS, Math.round(beats * 8.0) / 8.0);
                appendOrMergeSegmentLocked(currentChord, quantizedBeats);
                if (tempoCalibrator != null) {
                    tempoCalibrator.recordQuantizedBeats(quantizedBeats);
                }
            }
            currentChord = null;
            currentDurationSec = 0.0;
        }

        private void appendOrMergeSegmentLocked(String chord, double beats) {
            if (!recordedSegments.isEmpty()) {
                RecordedSegment last = recordedSegments.get(recordedSegments.size() - 1);
                if (last.chord.equals(chord)) {
                    last.beats += beats;
                    return;
                }
            }
            recordedSegments.add(new RecordedSegment(chord, beats));
        }

        private String finishRecordingLocked(TempoMetadata tempoMetadata) {
            String abcText = buildRecordedAbc(recordedSegments, recordingTempoBpm, tempoMetadata);
            recordedSegments.clear();
            currentChord = null;
            currentDurationSec = 0.0;
            return abcText;
        }

        private void releaseRecorder() {
            if (recorder == null) {
                return;
            }
            if (recorder.getState() == AudioRecord.STATE_INITIALIZED
                    && recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop();
            }
            recorder.release();
            recorder = null;
        }
    }

    private String buildRecordedAbc(List<RecordedSegment> segments, double tempoBpm, TempoMetadata tempoMetadata) {
        StringBuilder abcBuilder = new StringBuilder();
        abcBuilder.append("X:1\nT:Recorded\nM:4/4\nL:1/4\n")
                .append(AbcTempoNotation.formatQuarterNoteTempo(tempoBpm))
                .append("\nK:C\n");
        if (tempoMetadata != null) {
            abcBuilder.append(tempoMetadata.toCommentLine()).append('\n');
        }
        for (RecordedSegment segment : segments) {
            abcBuilder.append(segment.chord)
                    .append(lengthToToken(segment.beats))
                    .append(' ');
        }
        return abcBuilder.toString();
    }

    private double secondsToBeats(double seconds, double bpm) {
        return seconds * (bpm / 60.0);
    }

    private String lengthToToken(double beats) {
        int numerator = (int) Math.max(1, Math.round(beats * 8.0));
        int denominator = 8;
        int gcd = gcd(numerator, denominator);
        numerator /= gcd;
        denominator /= gcd;
        if (denominator == 1) {
            return numerator == 1 ? "" : Integer.toString(numerator);
        }
        return numerator == 1 ? "/" + denominator : numerator + "/" + denominator;
    }

    private int gcd(int a, int b) {
        while (b != 0) {
            int t = a % b;
            a = b;
            b = t;
        }
        return Math.max(1, a);
    }

    private boolean isRecognizedToken(String t) {
        if (t == null || t.isEmpty()) {
            return false;
        }
        if ("|:".equals(t) || ":|".equals(t) || "||".equals(t) || "|".equals(t)) {
            return true;
        }
        if (t.startsWith("V:") || t.startsWith("K:") || t.startsWith("M:") || t.startsWith("L:")
                || t.startsWith("Q:") || t.startsWith("X:") || t.startsWith("T:") || t.startsWith("C:")
                || t.startsWith("P:") || t.startsWith("N:") || t.startsWith("W:") || t.startsWith("U:")
                || t.startsWith("I:")) {
            return true;
        }
        if (t.equals("[") || t.equals("]") || t.equals("z") || t.equals("Z")) {
            return true;
        }
        if (t.equals("^") || t.equals("_") || t.equals("=") || t.equals("'") || t.equals(",") || t.equals("-")) {
            return true;
        }
        if (t.equals("(") || t.equals(")")) {
            return true;
        }
        if (t.equals("{") || t.equals("}")) {
            return true;
        }
        if (t.equals("~") || t.equals(".") || t.equals(">") || t.equals("<")) {
            return true;
        }
        if (t.startsWith("!")) {
            return true;
        }
        if (t.matches("[0-9/]+")) {
            return true;
        }
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

    private static final class MonitorUpdate {
        final float[] noteMagnitudes;
        final float threshold;
        final String summary;

        MonitorUpdate(float[] noteMagnitudes, float threshold, String summary) {
            this.noteMagnitudes = noteMagnitudes;
            this.threshold = threshold;
            this.summary = summary;
        }
    }

    private static final class PlaybackRequest {
        final String abcText;
        final String tempoText;

        PlaybackRequest(String abcText, String tempoText) {
            this.abcText = abcText;
            this.tempoText = tempoText;
        }
    }

    private static final class RecordedSegment {
        final String chord;
        double beats;

        RecordedSegment(String chord, double beats) {
            this.chord = chord;
            this.beats = beats;
        }
    }
}
