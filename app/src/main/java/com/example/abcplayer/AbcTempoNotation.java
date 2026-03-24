package com.example.abcplayer;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AbcTempoNotation {

    private static final double QUARTER_NOTE_LENGTH = 1.0 / 4.0;
    private static final Pattern NUMBER_PATTERN = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?");

    private AbcTempoNotation() {
    }

    static double normalizeTempoBpm(String tempoField, double fallbackBpm) {
        double safeFallbackBpm = isPositiveFinite(fallbackBpm) ? fallbackBpm : 120.0;
        if (tempoField == null) {
            return safeFallbackBpm;
        }

        String trimmed = tempoField.trim();
        if (trimmed.isEmpty()) {
            return safeFallbackBpm;
        }

        String value = trimmed.startsWith("Q:") ? trimmed.substring(2).trim() : trimmed;

        int equalsIndex = value.indexOf('=');
        if (equalsIndex < 0) {
            Double bpm = parseFirstNumber(value);
            return bpm != null && bpm > 0.0 ? bpm : safeFallbackBpm;
        }

        String left = value.substring(0, equalsIndex);
        String right = value.substring(equalsIndex + 1);

        Double bpm = parseFirstNumber(right);
        if (bpm == null || bpm <= 0.0) {
            return safeFallbackBpm;
        }

        Double noteLength = parseTempoNoteLength(left);
        if (noteLength == null || noteLength <= 0.0) {
            return bpm;
        }

        double normalizedBpm = bpm * (noteLength / QUARTER_NOTE_LENGTH);
        return Double.isFinite(normalizedBpm) && normalizedBpm > 0.0 ? normalizedBpm : bpm;
    }

    static String formatQuarterNoteTempo(double tempoBpm) {
        return "Q:1/4=" + formatTempoValue(tempoBpm);
    }

    static String formatTempoValue(double tempoBpm) {
        if (Math.abs(tempoBpm - Math.rint(tempoBpm)) < 1e-6) {
            return Integer.toString((int) Math.rint(tempoBpm));
        }
        return String.format(Locale.US, "%.2f", tempoBpm);
    }

    private static Double parseTempoNoteLength(String leftSide) {
        String cleaned = stripQuotedSegments(leftSide).trim();
        if (cleaned.isEmpty()) {
            return null;
        }

        String[] tokens = cleaned.split("\\s+");
        for (int i = tokens.length - 1; i >= 0; i--) {
            Double noteLength = parseTempoNoteLengthToken(tokens[i]);
            if (noteLength != null) {
                return noteLength;
            }
        }
        return null;
    }

    private static Double parseTempoNoteLengthToken(String token) {
        if (token == null) {
            return null;
        }

        String trimmed = token.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        if ("C".equals(trimmed)) {
            return QUARTER_NOTE_LENGTH;
        }
        if ("C|".equals(trimmed)) {
            return 1.0 / 2.0;
        }
        if ("/".equals(trimmed)) {
            return 1.0 / 2.0;
        }
        if (trimmed.startsWith("/")) {
            Double denominator = parsePositiveNumber(trimmed.substring(1));
            return denominator != null ? 1.0 / denominator : null;
        }

        int slashIndex = trimmed.indexOf('/');
        if (slashIndex >= 0) {
            Double numerator = parsePositiveNumber(trimmed.substring(0, slashIndex));
            if (numerator == null) {
                return null;
            }

            String denominatorText = trimmed.substring(slashIndex + 1);
            if (denominatorText.isEmpty()) {
                return numerator / 2.0;
            }

            Double denominator = parsePositiveNumber(denominatorText);
            return denominator != null ? numerator / denominator : null;
        }

        return parsePositiveNumber(trimmed);
    }

    private static String stripQuotedSegments(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        boolean inQuotes = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                if (!inQuotes) {
                    builder.append(' ');
                }
                continue;
            }
            if (!inQuotes) {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private static Double parseFirstNumber(String text) {
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return parsePositiveNumber(matcher.group());
    }

    private static Double parsePositiveNumber(String text) {
        try {
            double value = Double.parseDouble(text);
            return value > 0.0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean isPositiveFinite(double value) {
        return Double.isFinite(value) && value > 0.0;
    }
}
