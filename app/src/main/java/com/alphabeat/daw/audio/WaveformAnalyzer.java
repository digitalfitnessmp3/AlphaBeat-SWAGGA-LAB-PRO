package com.alphabeat.daw.audio;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class WaveformAnalyzer {

    private WaveformAnalyzer() {}

    public static float[] analyze(
            Context context, Uri uri, int points) throws Exception {

        if (points <= 0 || points > 8192) {
            throw new IllegalArgumentException("Numero picchi non valido");
        }

        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;

        float[] peaks = new float[points];

        try {
            extractor.setDataSource(context, uri, null);

            int track = -1;
            MediaFormat format = null;

            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat candidate = extractor.getTrackFormat(i);
                String mime = candidate.getString(MediaFormat.KEY_MIME);

                if (mime != null && mime.startsWith("audio/")) {
                    track = i;
                    format = candidate;
                    break;
                }
            }

            if (track < 0 || format == null) {
                throw new Exception("Nessuna traccia audio");
            }

            extractor.selectTrack(track);

            String mime = format.getString(MediaFormat.KEY_MIME);
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(format, null, null, 0);
            codec.start();

            long durationUs = format.containsKey(
                    MediaFormat.KEY_DURATION)
                    ? format.getLong(MediaFormat.KEY_DURATION)
                    : -1L;

            if (durationUs <= 0) {
                throw new Exception("Durata audio non disponibile");
            }

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;
            int channels = format.getInteger(
                    MediaFormat.KEY_CHANNEL_COUNT);
            int sampleRate = format.getInteger(
                    MediaFormat.KEY_SAMPLE_RATE);

            long decodedFrames = 0;

            while (!outputDone) {
                if (!inputDone) {
                    int inputIndex = codec.dequeueInputBuffer(10000);

                    if (inputIndex >= 0) {
                        ByteBuffer input = codec.getInputBuffer(inputIndex);
                        if (input == null) {
                            throw new Exception("Buffer input non disponibile");
                        }
                        input.clear();

                        int size = extractor.readSampleData(input, 0);

                        if (size < 0) {
                            codec.queueInputBuffer(
                                    inputIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(
                                    inputIndex, 0, size,
                                    extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }

                int outputIndex = codec.dequeueOutputBuffer(info, 10000);

                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat decoded = codec.getOutputFormat();

                    channels = decoded.getInteger(
                            MediaFormat.KEY_CHANNEL_COUNT);
                    sampleRate = decoded.getInteger(
                            MediaFormat.KEY_SAMPLE_RATE);

                    int encoding = decoded.containsKey(
                            MediaFormat.KEY_PCM_ENCODING)
                            ? decoded.getInteger(
                                    MediaFormat.KEY_PCM_ENCODING)
                            : 2;

                    if (encoding != 2) {
                        throw new Exception(
                                "PCM non supportato: " + encoding);
                    }

                } else if (outputIndex >= 0) {
                    ByteBuffer buffer = codec.getOutputBuffer(outputIndex);

                    if (info.size > 0 && buffer != null) {
                        buffer.position(info.offset);
                        buffer.limit(info.offset + info.size);
                        ByteBuffer pcm = buffer.slice()
                                .order(ByteOrder.LITTLE_ENDIAN);

                        int frames = info.size / (2 * channels);

                        for (int frame = 0; frame < frames; frame++) {
                            float amplitude = 0f;

                            for (int ch = 0; ch < channels; ch++) {
                                float value =
                                        Math.abs(pcm.getShort() / 32768f);
                                amplitude = Math.max(amplitude, value);
                            }

                            long frameIndex = decodedFrames + frame;
                            long timeUs = frameIndex * 1000000L
                                    / sampleRate;

                            int bucket = (int)Math.min(
                                    points - 1,
                                    timeUs * points / durationUs);

                            peaks[bucket] = Math.max(
                                    peaks[bucket], amplitude);
                        }

                        decodedFrames += frames;
                    }

                    codec.releaseOutputBuffer(outputIndex, false);

                    if ((info.flags &
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                    }
                }
            }

            return peaks;

        } finally {
            if (codec != null) {
                try {
                    codec.stop();
                } finally {
                    codec.release();
                }
            }
            extractor.release();
        }
    }
}
