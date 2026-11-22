package com.digitalfitnessmp3.app.audio;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class NativeAudioDecoder {
    private NativeAudioDecoder() {}

    public static PcmAudio decode(Context context, Uri uri)
            throws Exception {

        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;

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

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            boolean inputDone = false;
            boolean outputDone = false;

            int sampleRate = format.getInteger(
                    MediaFormat.KEY_SAMPLE_RATE);
            int channels = format.getInteger(
                    MediaFormat.KEY_CHANNEL_COUNT);

            while (!outputDone) {
                if (!inputDone) {
                    int inputIndex = codec.dequeueInputBuffer(10000);

                    if (inputIndex >= 0) {
                        ByteBuffer input = codec.getInputBuffer(inputIndex);
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
                    MediaFormat decodedFormat = codec.getOutputFormat();

                    sampleRate = decodedFormat.getInteger(
                            MediaFormat.KEY_SAMPLE_RATE);
                    channels = decodedFormat.getInteger(
                            MediaFormat.KEY_CHANNEL_COUNT);

                    int encoding = decodedFormat.containsKey(
                            MediaFormat.KEY_PCM_ENCODING)
                            ? decodedFormat.getInteger(
                                    MediaFormat.KEY_PCM_ENCODING)
                            : 2;

                    if (encoding != 2) {
                        throw new Exception(
                                "Formato PCM non ancora supportato: "
                                + encoding);
                    }

                } else if (outputIndex >= 0) {
                    ByteBuffer buffer = codec.getOutputBuffer(outputIndex);

                    if (info.size > 0 && buffer != null) {
                        buffer.position(info.offset);
                        buffer.limit(info.offset + info.size);

                        byte[] chunk = new byte[info.size];
                        buffer.get(chunk);
                        output.write(chunk);
                    }

                    codec.releaseOutputBuffer(outputIndex, false);

                    if ((info.flags &
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                    }
                }
            }

            byte[] bytes = output.toByteArray();
            ByteBuffer pcm = ByteBuffer.wrap(bytes)
                    .order(ByteOrder.LITTLE_ENDIAN);

            int frames = bytes.length / (2 * channels);
            float[] mono = new float[frames];

            for (int frame = 0; frame < frames; frame++) {
                float sum = 0;

                for (int channel = 0; channel < channels; channel++) {
                    sum += pcm.getShort() / 32768f;
                }

                mono[frame] = sum / channels;
            }

            return new PcmAudio(mono, sampleRate);

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
