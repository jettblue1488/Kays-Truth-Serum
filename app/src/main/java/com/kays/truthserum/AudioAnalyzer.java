package com.kays.truthserum;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

/**
 * Experimental acoustic variability measurements. These are NOT indicators of
 * honesty, deception, psychological stress, or a medical condition.
 */
public final class AudioAnalyzer {
    private static final int ANALYSIS_RATE = 8000;
    private static final int MAX_SAMPLES = ANALYSIS_RATE * 120;
    private static final int FRAME_SAMPLES = 240; // 30 ms

    private AudioAnalyzer() {}

    public static final class Result {
        public boolean usable;
        public String message = "";
        public int variabilityScore;
        public double loudnessVariationDb;
        public double pitchVariationPercent;
        public boolean pitchAvailable;
        public double durationSeconds;
        public double speakingSeconds;
        public int pitchFrames;
    }

    private static final class Samples {
        short[] values = new short[32768];
        int count = 0;
        double sum = 0;
        int sampleCount = 0;
        long tick = 0;

        void add(double sample, int sampleRate) {
            sum += sample;
            sampleCount++;
            tick += ANALYSIS_RATE;
            if (tick >= sampleRate) {
                if (count < MAX_SAMPLES) {
                    if (count == values.length) {
                        short[] next = new short[Math.min(MAX_SAMPLES, values.length * 2)];
                        System.arraycopy(values, 0, next, 0, count);
                        values = next;
                    }
                    double averaged = sum / sampleCount;
                    values[count++] = (short) Math.max(Short.MIN_VALUE,
                            Math.min(Short.MAX_VALUE, Math.round(averaged * 32767.0)));
                }
                sum = 0;
                sampleCount = 0;
                tick -= sampleRate;
            }
        }
    }

    public static Result analyze(File audioFile) {
        Result result = new Result();
        MediaExtractor extractor = null;
        MediaCodec decoder = null;
        try {
            extractor = new MediaExtractor();
            extractor.setDataSource(audioFile.getAbsolutePath());
            int audioTrack = -1;
            MediaFormat inputFormat = null;
            String mime = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat candidate = extractor.getTrackFormat(i);
                String candidateMime = candidate.getString(MediaFormat.KEY_MIME);
                if (candidateMime != null && candidateMime.startsWith("audio/")) {
                    audioTrack = i;
                    inputFormat = candidate;
                    mime = candidateMime;
                    break;
                }
            }
            if (audioTrack < 0 || inputFormat == null || mime == null) {
                result.message = "No audio track was found.";
                return result;
            }
            extractor.selectTrack(audioTrack);
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(inputFormat, null, null, 0);
            decoder.start();

            int channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int pcmEncoding = AudioFormat.ENCODING_PCM_16BIT;
            Samples audio = new Samples();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;
            int idleCount = 0;

            while (!outputDone && idleCount < 400) {
                boolean progressed = false;
                if (!inputDone) {
                    int inputIndex = decoder.dequeueInputBuffer(10000);
                    if (inputIndex >= 0) {
                        ByteBuffer input = decoder.getInputBuffer(inputIndex);
                        if (input == null) throw new IllegalStateException("No decoder input buffer");
                        input.clear();
                        int length = extractor.readSampleData(input, 0);
                        if (length < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, length,
                                    extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                        progressed = true;
                    }
                }
                int outputIndex = decoder.dequeueOutputBuffer(info, 10000);
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat output = decoder.getOutputFormat();
                    channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    sampleRate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if (output.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        pcmEncoding = output.getInteger(MediaFormat.KEY_PCM_ENCODING);
                    }
                    progressed = true;
                } else if (outputIndex >= 0) {
                    ByteBuffer data = decoder.getOutputBuffer(outputIndex);
                    if (data != null && info.size > 0) {
                        decodePcm(data, info.offset, info.size, channels, sampleRate,
                                pcmEncoding, audio);
                    }
                    outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    decoder.releaseOutputBuffer(outputIndex, false);
                    progressed = true;
                }
                idleCount = progressed ? 0 : idleCount + 1;
            }
            if (!outputDone) {
                result.message = "The audio decoder did not finish.";
                return result;
            }
            return measure(audio);
        } catch (Exception error) {
            result.message = "Unable to analyze this recording: " + error.getClass().getSimpleName();
            return result;
        } finally {
            if (decoder != null) {
                try { decoder.stop(); } catch (Exception ignored) {}
                try { decoder.release(); } catch (Exception ignored) {}
            }
            if (extractor != null) {
                try { extractor.release(); } catch (Exception ignored) {}
            }
        }
    }

    private static void decodePcm(ByteBuffer data, int offset, int length,
                                  int channels, int sampleRate, int encoding, Samples audio) {
        if (channels < 1 || sampleRate < 1) throw new IllegalArgumentException("Invalid audio format");
        data.order(ByteOrder.LITTLE_ENDIAN);
        data.position(offset);
        data.limit(offset + length);
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            while (data.remaining() >= 4 * channels) {
                double mono = 0;
                for (int ch = 0; ch < channels; ch++) mono += data.getFloat();
                audio.add(mono / channels, sampleRate);
            }
        } else if (encoding == AudioFormat.ENCODING_PCM_16BIT) {
            while (data.remaining() >= 2 * channels) {
                double mono = 0;
                for (int ch = 0; ch < channels; ch++) mono += data.getShort() / 32768.0;
                audio.add(mono / channels, sampleRate);
            }
        } else {
            throw new IllegalArgumentException("Unsupported PCM encoding: " + encoding);
        }
    }

    private static Result measure(Samples audio) {
        Result result = new Result();
        result.durationSeconds = audio.count / (double) ANALYSIS_RATE;
        if (audio.count < FRAME_SAMPLES * 10) {
            result.message = "The answer is too short for a useful measurement. Record at least one second.";
            return result;
        }
        int frames = audio.count / FRAME_SAMPLES;
        double[] levels = new double[frames];
        double highest = -100;
        for (int frame = 0; frame < frames; frame++) {
            int start = frame * FRAME_SAMPLES;
            double energy = 0;
            for (int i = 0; i < FRAME_SAMPLES; i++) {
                double value = audio.values[start + i] / 32768.0;
                energy += value * value;
            }
            double rms = Math.sqrt(energy / FRAME_SAMPLES);
            levels[frame] = 20.0 * Math.log10(Math.max(0.000001, rms));
            highest = Math.max(highest, levels[frame]);
        }
        if (highest < -54) {
            result.message = "The recorded answer is too quiet to analyze.";
            return result;
        }

        double threshold = Math.max(-43, highest - 26);
        ArrayList<Double> speechLevels = new ArrayList<>();
        ArrayList<Double> pitches = new ArrayList<>();
        for (int frame = 0; frame < frames; frame++) {
            if (levels[frame] < threshold) continue;
            speechLevels.add(levels[frame]);
            double pitch = estimatePitch(audio.values, frame * FRAME_SAMPLES);
            if (pitch > 0) pitches.add(pitch);
        }
        result.speakingSeconds = speechLevels.size() * FRAME_SAMPLES / (double) ANALYSIS_RATE;
        result.pitchFrames = pitches.size();
        if (speechLevels.size() < 10) {
            result.message = "Not enough clear speech was detected. Try a quieter room.";
            return result;
        }

        result.loudnessVariationDb = standardDeviation(speechLevels);
        result.pitchAvailable = pitches.size() >= 5;
        if (result.pitchAvailable) {
            result.pitchVariationPercent = 100.0 * standardDeviation(pitches)
                    / Math.max(1, average(pitches));
        }
        // A 0–100 feature scale, NOT a probability or a validated stress classifier.
        double loudnessComponent = Math.min(1.0, result.loudnessVariationDb / 12.0);
        double pitchComponent = Math.min(1.0, result.pitchVariationPercent / 35.0);
        double combined = result.pitchAvailable
                ? 0.45 * loudnessComponent + 0.55 * pitchComponent
                : loudnessComponent;
        result.variabilityScore = (int) Math.round(100 * combined);
        result.usable = true;
        result.message = result.pitchAvailable
                ? "Experimental score from pitch and loudness changes."
                : "Low-detail estimate from loudness changes only; pitch tracking was insufficient.";
        return result;
    }

    private static double estimatePitch(short[] samples, int start) {
        double mean = 0;
        for (int i = 0; i < FRAME_SAMPLES; i++) mean += samples[start + i];
        mean /= FRAME_SAMPLES;
        double strongest = 0.66;
        int bestLag = -1;
        // 57–400 Hz, an approximate range for many speaking voices.
        for (int lag = 20; lag <= 140; lag++) {
            double correlation = 0, firstPower = 0, secondPower = 0;
            for (int i = 0; i < FRAME_SAMPLES - lag; i++) {
                double first = samples[start + i] - mean;
                double second = samples[start + i + lag] - mean;
                correlation += first * second;
                firstPower += first * first;
                secondPower += second * second;
            }
            double normalized = correlation / Math.sqrt(firstPower * secondPower + 0.000001);
            if (normalized > strongest) {
                strongest = normalized;
                bestLag = lag;
            }
        }
        return bestLag < 0 ? -1 : (double) ANALYSIS_RATE / bestLag;
    }

    private static double average(ArrayList<Double> values) {
        double sum = 0;
        for (double value : values) sum += value;
        return sum / values.size();
    }

    private static double standardDeviation(ArrayList<Double> values) {
        double avg = average(values);
        double squared = 0;
        for (double value : values) squared += (value - avg) * (value - avg);
        return Math.sqrt(squared / values.size());
    }
}
