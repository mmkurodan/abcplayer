package com.example.abcplayer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class SpectrumView extends View {
    public static final int MIN_MIDI = 36;
    public static final int MAX_MIDI = 96;
    public static final int NOTE_COUNT = MAX_MIDI - MIN_MIDI + 1;

    private final Paint backgroundPaint = new Paint();
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thresholdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thresholdTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float[] noteMagnitudes = new float[NOTE_COUNT];
    private float threshold;
    private String detectedChord = "待機中";
    private int polyphonyCount = 8;
    private int maxDisplayNotes = NOTE_COUNT;

    public SpectrumView(Context context) {
        super(context);
        init();
    }

    public SpectrumView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SpectrumView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        backgroundPaint.setColor(Color.WHITE);

        axisPaint.setColor(Color.DKGRAY);
        axisPaint.setStrokeWidth(dp(1f));

        gridPaint.setColor(Color.LTGRAY);
        gridPaint.setStrokeWidth(dp(1f));

        textPaint.setColor(Color.DKGRAY);
        textPaint.setTextSize(sp(12f));

        barPaint.setColor(Color.rgb(66, 133, 244));
        activeBarPaint.setColor(Color.rgb(234, 67, 53));

        thresholdPaint.setColor(Color.rgb(251, 188, 5));
        thresholdPaint.setStrokeWidth(dp(2f));

        thresholdTextPaint.setColor(thresholdPaint.getColor());
        thresholdTextPaint.setTextSize(sp(12f));
        thresholdTextPaint.setAntiAlias(true);
    }

    public void updateSpectrum(float[] magnitudes, float threshold, String detectedChord) {
        updateSpectrum(magnitudes, threshold, detectedChord, NOTE_COUNT);
    }

    public void updateSpectrum(float[] magnitudes, float threshold, String detectedChord, int maxDisplayNotes) {
        int length = Math.min(magnitudes.length, noteMagnitudes.length);
        System.arraycopy(magnitudes, 0, noteMagnitudes, 0, length);
        for (int i = length; i < noteMagnitudes.length; i++) {
            noteMagnitudes[i] = 0f;
        }
        this.threshold = threshold;
        this.detectedChord = detectedChord == null ? "z" : detectedChord;
        this.maxDisplayNotes = Math.max(1, Math.min(maxDisplayNotes, NOTE_COUNT));
        postInvalidateOnAnimation();
    }

    public void setPolyphonyCount(int count) {
        this.polyphonyCount = Math.max(1, Math.min(count, 8));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        canvas.drawRect(0, 0, width, height, backgroundPaint);

        float left = dp(12f);
        float top = dp(28f);
        float right = width - dp(12f);
        float bottom = height - dp(28f);
        if (right <= left || bottom <= top) {
            return;
        }

        float plotWidth = right - left;
        float plotHeight = bottom - top;
        float maxMagnitude = 1f;
        for (int i = 0; i < maxDisplayNotes; i++) {
            if (noteMagnitudes[i] > maxMagnitude) {
                maxMagnitude = noteMagnitudes[i];
            }
        }
        if (threshold > 0f) {
            maxMagnitude = Math.max(maxMagnitude, threshold * 1.1f);
        }

        drawGrid(canvas, left, top, right, bottom, plotHeight, maxMagnitude);
        drawBars(canvas, left, top, bottom, plotWidth, plotHeight, maxMagnitude);
        drawThreshold(canvas, left, right, top, bottom, plotHeight, maxMagnitude);
        drawAxes(canvas, left, top, right, bottom);
        drawLabels(canvas, left, top, right, bottom, plotWidth);
    }

    private void drawGrid(Canvas canvas, float left, float top, float right, float bottom, float plotHeight, float maxMagnitude) {
        for (int i = 0; i <= 4; i++) {
            float fraction = i / 4f;
            float y = bottom - fraction * plotHeight;
            canvas.drawLine(left, y, right, y, gridPaint);
        }
    }

    private void drawBars(Canvas canvas, float left, float top, float bottom, float plotWidth, float plotHeight, float maxMagnitude) {
        float barWidth = plotWidth / maxDisplayNotes;
        for (int i = 0; i < maxDisplayNotes; i++) {
            float magnitude = noteMagnitudes[i];
            float normalized = magnitude / maxMagnitude;
            float barTop = bottom - normalized * plotHeight;
            float x0 = left + i * barWidth + dp(0.5f);
            float x1 = left + (i + 1) * barWidth - dp(0.5f);
            Paint paint = magnitude >= threshold && magnitude > 0f ? activeBarPaint : barPaint;
            canvas.drawRect(x0, Math.max(top, barTop), x1, bottom, paint);
        }
    }

    private void drawThreshold(Canvas canvas, float left, float right, float top, float bottom, float plotHeight, float maxMagnitude) {
        if (threshold <= 0f) {
            return;
        }
        float thresholdY = bottom - (threshold / maxMagnitude) * plotHeight;
        thresholdY = Math.max(top, Math.min(bottom, thresholdY));
        canvas.drawLine(left, thresholdY, right, thresholdY, thresholdPaint);
        canvas.drawText("閾値", right - dp(36f), thresholdY - dp(4f), thresholdTextPaint);
    }

    private void drawAxes(Canvas canvas, float left, float top, float right, float bottom) {
        canvas.drawLine(left, top, left, bottom, axisPaint);
        canvas.drawLine(left, bottom, right, bottom, axisPaint);
    }

    private void drawLabels(Canvas canvas, float left, float top, float right, float bottom, float plotWidth) {
        canvas.drawText("強さ", dp(2f), top, textPaint);
        canvas.drawText("音階", right - dp(24f), getHeight() - dp(6f), textPaint);
        canvas.drawText("検出: " + detectedChord, left, top - dp(8f), textPaint);

        float barWidth = plotWidth / maxDisplayNotes;
        for (int midi = MIN_MIDI; midi <= MIN_MIDI + maxDisplayNotes - 1; midi += 12) {
            int index = midi - MIN_MIDI;
            if (index < 0 || index >= maxDisplayNotes) continue;
            float x = left + index * barWidth;
            canvas.drawLine(x, top, x, bottom, gridPaint);
            canvas.drawText(midiToLabel(midi), x + dp(2f), bottom + dp(16f), textPaint);
        }
    }

    private String midiToLabel(int midi) {
        String[] names = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        int octave = midi / 12 - 1;
        return names[midi % 12] + octave;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
