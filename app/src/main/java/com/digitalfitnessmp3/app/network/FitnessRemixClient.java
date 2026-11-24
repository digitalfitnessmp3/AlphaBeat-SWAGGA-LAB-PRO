package com.digitalfitnessmp3.app.network;

import android.content.Context;
import android.net.Uri;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class FitnessRemixClient {
    private static final String ENDPOINT =
            "https://digitalfitnessmp3.com/api/free-fitness/remix";

    private FitnessRemixClient() {}

    public static File createRemix(Context context, Uri source, int targetBpm)
            throws IOException {
        return createRemix(context, source, 0, targetBpm);
    }

    public static File createRemix(
            Context context, Uri source, int sourceBpm, int targetBpm)
            throws IOException {
        return createRemix(context, source, sourceBpm, targetBpm, false);
    }

    public static File createRemix(Context context, Uri source, int sourceBpm, int targetBpm, boolean batida) throws IOException {
        if (batida && (targetBpm != 140 || sourceBpm > 180)) {
            throw new IOException("Batida Extreme: target 140 BPM, sorgente 70-180 BPM musicali");
        }
        if (sourceBpm != 0 && (sourceBpm < 70 || sourceBpm > 200)) {
            throw new IOException("BPM originali fuori intervallo");
        }

        if (android.os.Looper.myLooper()
                == android.os.Looper.getMainLooper()) {
            throw new IOException("Eseguire fuori dal thread UI");
        }

        try (android.database.Cursor cursor =
                     context.getContentResolver().query(
                             source,
                             new String[]{
                                     android.provider.OpenableColumns.SIZE
                             },
                             null, null, null)) {

            if (cursor != null && cursor.moveToFirst()
                    && !cursor.isNull(0)) {

                long size = cursor.getLong(0);

                if (size > 30L * 1024 * 1024) {
                    throw new IOException(
                            "File troppo grande: massimo 30 MB");
                }
            }
        }

        String mime = context.getContentResolver().getType(source);
        String extension;

        if (mime == null) {
            throw new IOException("Formato audio non riconosciuto");
        }

        switch (mime.toLowerCase(java.util.Locale.ROOT)) {
            case "audio/mpeg":
            case "audio/mp3":
                extension = ".mp3";
                break;
            case "audio/wav":
            case "audio/x-wav":
            case "audio/wave":
                extension = ".wav";
                break;
            case "audio/mp4":
            case "audio/x-m4a":
                extension = ".m4a";
                break;
            case "audio/flac":
                extension = ".flac";
                break;
            case "audio/ogg":
                extension = ".ogg";
                break;
            default:
                throw new IOException("Formato non supportato: " + mime);
        }

        if (targetBpm != 0 && targetBpm != 128
                && targetBpm != 140 && targetBpm != 150
                && targetBpm != 160 && targetBpm != 180
                && targetBpm != 200 && targetBpm != 220
                && targetBpm != 240 && targetBpm != 260) {
            throw new IOException("Target BPM non supportato");
        }

        String boundary = "DFMP3-" + UUID.randomUUID();
        HttpURLConnection connection = null;
        File output = null;

        try {
            connection = (HttpURLConnection)
                    new URL(batida ? ENDPOINT.replace("/remix", "/batida-extreme") : ENDPOINT).openConnection();

            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(batida ? 260000 : 180000);
            connection.setDoOutput(true);

            connection.setChunkedStreamingMode(65536);
            connection.setRequestProperty(
                    "Content-Type",
                    "multipart/form-data; boundary=" + boundary);

            try (OutputStream out = connection.getOutputStream()) {
                String header = "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; "
                        + "name=\"file\"; filename=\"audio" + extension + "\"\r\n"
                        + "Content-Type: " + mime + "\r\n\r\n";

                String bpmField = "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"manual_target_bpm\"\r\n\r\n"
                        + targetBpm + "\r\n";

                if (sourceBpm != 0) {
                    String sourceField = "--" + boundary + "\r\n"
                            + "Content-Disposition: form-data; name=\"manual_source_bpm\"\r\n\r\n"
                            + sourceBpm + "\r\n";
                    out.write(sourceField.getBytes(StandardCharsets.UTF_8));
                }

                if (targetBpm != 0) {
                    out.write(bpmField.getBytes(StandardCharsets.UTF_8));
                }
                out.write(header.getBytes(StandardCharsets.UTF_8));

                try (InputStream in = context.getContentResolver()
                        .openInputStream(source)) {
                    if (in == null) {
                        throw new IOException("File audio non accessibile");
                    }

                    byte[] buffer = new byte[65536];
                    int count;
                    long totalBytes = 0;
                    final long maxBytes = 30L * 1024 * 1024;

                    while ((count = in.read(buffer)) != -1) {
                        totalBytes += count;

                        if (totalBytes > maxBytes) {
                            throw new IOException(
                                    "File troppo grande: massimo 30 MB");
                        }

                        out.write(buffer, 0, count);
                    }
                }

                out.write(("\r\n--" + boundary + "--\r\n")
                        .getBytes(StandardCharsets.UTF_8));
            }

            int status = connection.getResponseCode();

            if (status != 200) {
                String detail = "";
                try (InputStream err = connection.getErrorStream()) {
                    if (err != null) {
                        ByteArrayOutputStream buffer =
                                new ByteArrayOutputStream();
                        byte[] chunk = new byte[4096];
                        int n;
                        while ((n = err.read(chunk)) != -1
                                && buffer.size() < 4096) {
                            buffer.write(chunk, 0,
                                    Math.min(n, 4096 - buffer.size()));
                        }
                        detail = buffer.toString("UTF-8");
                    }
                }
                throw new IOException(
                        "HTTP " + status + ": " + detail);
            }

            if (batida && !"batida-extreme-qa".equals(connection.getHeaderField("X-Remix-Mode"))) {
                throw new IOException("Risposta Batida non riconosciuta");
            }
            String returnedBpm = connection.getHeaderField("X-Target-BPM");
            if (returnedBpm == null) {
                throw new IOException("Risposta Fitness 32 senza BPM verificabili");
            }

            try {
                double actualBpm = Double.parseDouble(returnedBpm);
                if (!Double.isFinite(actualBpm)
                        || (targetBpm != 0 && Math.abs(actualBpm - targetBpm) > 0.1)) {
                    throw new IOException("BPM restituiti diversi da quelli richiesti");
                }
            } catch (NumberFormatException error) {
                throw new IOException("BPM restituiti non validi", error);
            }

            String contentType = connection.getContentType();
            if (contentType == null ||
                    !contentType.toLowerCase(java.util.Locale.ROOT)
                            .startsWith("audio/mpeg")) {
                throw new IOException("Risposta non MP3");
            }

            output = File.createTempFile(
                    "dfmp3-remix-", ".mp3", context.getCacheDir());

            try (InputStream in = connection.getInputStream();
                 OutputStream out = new FileOutputStream(output)) {

                byte[] buffer = new byte[65536];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                }
            }

            if (output.length() == 0) {
                throw new IOException("MP3 ricevuto vuoto");
            }

            return output;

        } catch (IOException | RuntimeException error) {
            if (output != null) output.delete();
            throw error;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
