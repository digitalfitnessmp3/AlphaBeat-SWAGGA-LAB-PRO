package com.digitalfitnessmp3.app.audio;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.content.Context;

public final class AudioDecoder {

    private AudioDecoder() {}

    public static MediaFormat inspectAudio(
            Context context, Uri audioUri) throws Exception {

        MediaExtractor extractor = new MediaExtractor();

        try {
            extractor.setDataSource(context, audioUri, null);

            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);

                if (mime != null && mime.startsWith("audio/")) {
                    return format;
                }
            }

            throw new Exception("Nessuna traccia audio trovata");
        } finally {
            extractor.release();
        }
    }
}
