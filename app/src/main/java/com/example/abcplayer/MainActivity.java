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
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.jtransforms.fft.DoubleFFT_1D;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int REQ_RECORD = 1001;
    private static final int DEFAULT_TEMPO_BPM = 120;
    private static final int MIN_THRESHOLD_MULTIPLIER = 1;
    private static final int DEFAULT_THRESHOLD_MULTIPLIER = 200;
    private static final int MAX_THRESHOLD_MULTIPLIER = 400;
    private static final double MIN_RECORDABLE_BEATS = 1.0 / 8.0;

    private EditText editAbc;
    private EditText editTempo;
    private Button btnPlay;
    private Button btnRecord;
    private SeekBar seekThreshold;
    private TextView txtThresholdValue;
    private TextView txtStatus;
    private ScrollView scrollStatus;
    private SpectrumView spectrumView;

    private AudioTrack audioTrack;
    private PlayTask currentTask;
    private AudioMonitorTask audioMonitorTask;

    private volatile int thresholdMultiplier = DEFAULT_THRESHOLD_MULTIPLIER;
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
        txtStatus = findViewById(R.id.txtStatus);
        scrollStatus = findViewById(R.id.scrollStatus);
        spectrumView = findViewById(R.id.spectrumView);

        configureThresholdSlider();
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

    private void onPlayClicked() {
        String abc = editAbc.getText().toString().trim();
        if (abc.isEmpty()) {
            txtStatus.setText("ABC notation が空です");
            return;
        }

        stopCurrentPlayback();
        txtStatus.setText("解析・生成中...\n");

        currentTask = new PlayTask();
        currentTask.execute(abc);
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
                    return "キャンセル";
                }

                int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
                int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
                int minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);
                int chunkSamples = Math.max(1024, minBufferSize / 2);

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
        private static final int SAMPLE_RATE = 44100;
        private static final int FFT_SIZE = 2048;
        private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
        private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        private static final int MAX_PEAKS = 4;

        private final Object recordingLock = new Object();
        private final DoubleFFT_1D fft = new DoubleFFT_1D(FFT_SIZE);
        private final double[] window = new double[FFT_SIZE];
        private final List<RecordedSegment> recordedSegments = new ArrayList<>();

        private volatile boolean running = true;
        private volatile boolean recording;

        private AudioRecord recorder;
        private String currentChord;
        private double currentDurationSec;
        private double recordingTempoBpm = DEFAULT_TEMPO_BPM;

        AudioMonitorTask() {
            for (int i = 0; i < FFT_SIZE; i++) {
                window[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (FFT_SIZE - 1));
            }
        }

        @Override
        protected void onPreExecute() {
            appendStatus("音声監視を開始します\n");
        }

        @Override
        protected String doInBackground(Void... voids) {
            int minBufferBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            if (minBufferBytes <= 0) {
                return "エラー: 録音バッファを確保できません";
            }
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                return "録音権限がありません";
            }

            int bufferSizeBytes = Math.max(minBufferBytes, FFT_SIZE * 2);
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSizeBytes);
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                recorder.release();
                recorder = null;
                return "エラー: AudioRecord の初期化に失敗しました";
            }

            short[] buffer = new short[FFT_SIZE];
            double[] fftInput = new double[FFT_SIZE * 2];
            double[] spectrum = new double[FFT_SIZE / 2];

            try {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    return "録音権限がありません";
                }
                recorder.startRecording();

                while (running && !isCancelled()) {
                    int read = recorder.read(buffer, 0, buffer.length);
                    if (read <= 0) {
                        continue;
                    }

                    AnalysisFrame frame = analyzeFrame(buffer, read, fftInput, spectrum);
                    updateRecording(frame.chord, frame.frameSeconds);
                    publishProgress(new MonitorUpdate(frame.noteMagnitudes, (float) frame.threshold, frame.chord));
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
                releaseRecorder();
            }
        }

        @Override
        protected void onProgressUpdate(MonitorUpdate... values) {
            MonitorUpdate update = values[0];
            spectrumView.updateSpectrum(update.noteMagnitudes, update.threshold, update.chord);
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
            }
            btnRecord.setText("録音停止");
            appendStatus(String.format(Locale.US, "録音開始 BPM=%.2f 閾値倍率=%d\n", tempoBpm, thresholdMultiplier));
        }

        void stopRecordingSession(boolean dueToMonitoringStop) {
            final String abcText;
            synchronized (recordingLock) {
                if (!recording) {
                    return;
                }
                abcText = finishRecordingLocked();
                recording = false;
            }

            btnRecord.setText("録音");
            editAbc.setText(abcText.trim());
            appendStatus(dueToMonitoringStop ? "音声監視停止に伴い録音を終了しました\n" : "録音完了\n");
        }

        void stopMonitoring() {
            if (recording) {
                stopRecordingSession(true);
            }
            running = false;
        }

        private AnalysisFrame analyzeFrame(short[] buffer, int read, double[] fftInput, double[] spectrum) {
            int len = Math.min(read, FFT_SIZE);
            double sumSq = 0.0;
            for (int i = 0; i < len; i++) {
                double normalized = buffer[i] / 32768.0;
                sumSq += normalized * normalized;
                fftInput[2 * i] = buffer[i] * window[i];
                fftInput[2 * i + 1] = 0.0;
            }
            for (int i = len; i < FFT_SIZE; i++) {
                fftInput[2 * i] = 0.0;
                fftInput[2 * i + 1] = 0.0;
            }

            fft.complexForward(fftInput);

            int specLen = FFT_SIZE / 2;
            for (int i = 0; i < specLen; i++) {
                double re = fftInput[2 * i];
                double im = fftInput[2 * i + 1];
                spectrum[i] = Math.sqrt(re * re + im * im);
            }

            double noiseFloor = percentile(spectrum, specLen, 20.0);
            double magThreshold = Math.max(1e-6, noiseFloor * thresholdMultiplier);
            int[] peakBins = findPeaks(spectrum, specLen, MAX_PEAKS, magThreshold);
            String chord;
            if (peakBins.length == 0) {
                chord = "z";
            } else {
                peakBins = suppressHarmonics(peakBins, SAMPLE_RATE / (double) FFT_SIZE);
                double[] freqs = new double[peakBins.length];
                for (int i = 0; i < peakBins.length; i++) {
                    freqs[i] = peakBins[i] * SAMPLE_RATE / (double) FFT_SIZE;
                }
                chord = binsToAbc(freqs);
            }

            float[] noteMagnitudes = buildNoteMagnitudes(spectrum, specLen);
            double rms = Math.sqrt(sumSq / Math.max(1, len));
            double frameSeconds = len / (double) SAMPLE_RATE;
            return new AnalysisFrame(chord, noteMagnitudes, magThreshold, rms, frameSeconds);
        }

        private float[] buildNoteMagnitudes(double[] spectrum, int specLen) {
            float[] noteMagnitudes = new float[SpectrumView.NOTE_COUNT];
            for (int i = 1; i < specLen; i++) {
                double freq = i * SAMPLE_RATE / (double) FFT_SIZE;
                int midi = freqToMidi(freq);
                if (midi < SpectrumView.MIN_MIDI || midi > SpectrumView.MAX_MIDI) {
                    continue;
                }
                int index = midi - SpectrumView.MIN_MIDI;
                noteMagnitudes[index] = Math.max(noteMagnitudes[index], (float) spectrum[i]);
            }
            return noteMagnitudes;
        }

        private void updateRecording(String chord, double frameSeconds) {
            synchronized (recordingLock) {
                if (!recording) {
                    return;
                }
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

        private String finishRecordingLocked() {
            flushCurrentSegmentLocked();
            String abcText = buildRecordedAbc(recordedSegments, recordingTempoBpm);
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

        private int[] findPeaks(double[] spectrum, int len, int maxCount, double threshold) {
            int[] bins = new int[maxCount];
            double[] mags = new double[maxCount];
            int found = 0;
            for (int i = 1; i < len - 1; i++) {
                double magnitude = spectrum[i];
                if (magnitude < threshold || magnitude < spectrum[i - 1] || magnitude < spectrum[i + 1]) {
                    continue;
                }
                for (int k = 0; k < maxCount; k++) {
                    if (magnitude > mags[k]) {
                        for (int s = maxCount - 1; s > k; s--) {
                            mags[s] = mags[s - 1];
                            bins[s] = bins[s - 1];
                        }
                        mags[k] = magnitude;
                        bins[k] = i;
                        if (found < maxCount) {
                            found++;
                        }
                        break;
                    }
                }
            }
            return Arrays.copyOf(bins, found);
        }

        private int[] suppressHarmonics(int[] bins, double binResHz) {
            List<Integer> kept = new ArrayList<>();
            for (int bin : bins) {
                double freq = bin * binResHz;
                boolean skip = false;
                for (int base : kept) {
                    double baseFreq = base * binResHz;
                    double ratio = freq / baseFreq;
                    double nearest = Math.rint(ratio);
                    if (nearest >= 2 && nearest <= 4 && Math.abs(ratio - nearest) < 0.05) {
                        skip = true;
                        break;
                    }
                    if (Math.abs(bin - base) <= 1) {
                        skip = true;
                        break;
                    }
                }
                if (!skip) {
                    kept.add(bin);
                }
            }
            int[] result = new int[kept.size()];
            for (int i = 0; i < kept.size(); i++) {
                result[i] = kept.get(i);
            }
            return result;
        }

        private double percentile(double[] arr, int len, double p) {
            double[] copy = Arrays.copyOf(arr, len);
            Arrays.sort(copy);
            if (len == 0) {
                return 0.0;
            }
            double rank = (p / 100.0) * (len - 1);
            int low = (int) Math.floor(rank);
            int high = (int) Math.ceil(rank);
            if (low == high) {
                return copy[low];
            }
            double weight = rank - low;
            return copy[low] * (1 - weight) + copy[high] * weight;
        }

        private String binsToAbc(double[] freqs) {
            Arrays.sort(freqs);
            if (freqs.length == 1) {
                return freqToAbc(freqs[0]);
            }
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < freqs.length; i++) {
                sb.append(freqToAbc(freqs[i]));
                if (i < freqs.length - 1) {
                    sb.append(' ');
                }
            }
            sb.append(']');
            return sb.toString();
        }

        private String freqToAbc(double freq) {
            int midi = freqToMidi(freq);
            int octave = midi / 12 - 1;
            int pc = (midi % 12 + 12) % 12;
            String note;
            switch (pc) {
                case 0:
                    note = "C";
                    break;
                case 1:
                    note = "^C";
                    break;
                case 2:
                    note = "D";
                    break;
                case 3:
                    note = "^D";
                    break;
                case 4:
                    note = "E";
                    break;
                case 5:
                    note = "F";
                    break;
                case 6:
                    note = "^F";
                    break;
                case 7:
                    note = "G";
                    break;
                case 8:
                    note = "^G";
                    break;
                case 9:
                    note = "A";
                    break;
                case 10:
                    note = "^A";
                    break;
                case 11:
                    note = "B";
                    break;
                default:
                    note = "C";
                    break;
            }
            if (octave >= 5) {
                int ups = octave - 4;
                for (int i = 0; i < ups; i++) {
                    note += "'";
                }
            } else if (octave <= 3) {
                int downs = 4 - octave;
                for (int i = 0; i < downs; i++) {
                    note += ",";
                }
            }
            return note;
        }

        private int freqToMidi(double freq) {
            return (int) Math.round(69 + 12 * Math.log(freq / 440.0) / Math.log(2));
        }
    }

    private String buildRecordedAbc(List<RecordedSegment> segments, double tempoBpm) {
        StringBuilder abcBuilder = new StringBuilder();
        abcBuilder.append("X:1\nT:Recorded\nM:4/4\nL:1/4\nQ:")
                .append(formatTempo(tempoBpm))
                .append("\nK:C\n");
        for (RecordedSegment segment : segments) {
            abcBuilder.append(segment.chord)
                    .append(lengthToToken(segment.beats))
                    .append(' ');
        }
        return abcBuilder.toString();
    }

    private String formatTempo(double tempoBpm) {
        if (Math.abs(tempoBpm - Math.rint(tempoBpm)) < 1e-6) {
            return Integer.toString((int) Math.rint(tempoBpm));
        }
        return String.format(Locale.US, "%.2f", tempoBpm);
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

    private static final class AnalysisFrame {
        final String chord;
        final float[] noteMagnitudes;
        final double threshold;
        final double rms;
        final double frameSeconds;

        AnalysisFrame(String chord, float[] noteMagnitudes, double threshold, double rms, double frameSeconds) {
            this.chord = chord;
            this.noteMagnitudes = noteMagnitudes;
            this.threshold = threshold;
            this.rms = rms;
            this.frameSeconds = frameSeconds;
        }
    }

    private static final class MonitorUpdate {
        final float[] noteMagnitudes;
        final float threshold;
        final String chord;

        MonitorUpdate(float[] noteMagnitudes, float threshold, String chord) {
            this.noteMagnitudes = noteMagnitudes;
            this.threshold = threshold;
            this.chord = chord;
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
