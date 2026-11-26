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
                android.media.MediaMetadataRetriever metadata = new android.media.MediaMetadataRetriever();
                try {
                    metadata.setDataSource(context, uri);
                    String duration = metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (duration != null) durationUs = Long.parseLong(duration) * 1000L;
                } finally { metadata.release(); }
            }
            if (durationUs <= 0) throw new Exception("Durata audio non disponibile");

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;
            int channels = format.getInteger(
                    MediaFormat.KEY_CHANNEL_COUNT);
            int sampleRate = format.getInteger(
                    MediaFormat.KEY_SAMPLE_RATE);

            long decodedFrames = 0;
            int encoding = android.media.AudioFormat.ENCODING_PCM_16BIT;
            long lastProgress = android.os.SystemClock.elapsedRealtime();

            while (!outputDone) {
                if (android.os.SystemClock.elapsedRealtime() - lastProgress > 15000L) {
                    throw new java.io.IOException("Decoder waveform senza avanzamento");
                }
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

                    encoding = decoded.containsKey(
                            MediaFormat.KEY_PCM_ENCODING)
                            ? decoded.getInteger(
                                    MediaFormat.KEY_PCM_ENCODING)
                            : 2;

                    if (encoding != android.media.AudioFormat.ENCODING_PCM_16BIT
                            && encoding != android.media.AudioFormat.ENCODING_PCM_FLOAT) {
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

                        lastProgress = android.os.SystemClock.elapsedRealtime();
                        boolean floating = encoding == android.media.AudioFormat.ENCODING_PCM_FLOAT;
                        int frames = info.size / ((floating ? 4 : 2) * channels);

                        for (int frame = 0; frame < frames; frame++) {
                            float amplitude = 0f;

                            for (int ch = 0; ch < channels; ch++) {
                                float sample = floating ? pcm.getFloat() : pcm.getShort() / 32768f;
                                float value = Float.isFinite(sample) ? Math.min(1f, Math.abs(sample)) : 0f;
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

            if (decodedFrames == 0) throw new java.io.IOException("Nessun campione audio decodificato");
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
