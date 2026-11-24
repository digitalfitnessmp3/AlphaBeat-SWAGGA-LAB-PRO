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

    // QA only: never enable the legacy public route for this mode.
    private static final String V6 = "https://digitalfitnessmp3-menu-staging-test.onrender.com/api/free-fitness/batida-extreme/jobs";
    private static final String EXTENDED = "https://digitalfitnessmp3-menu-staging-test.onrender.com/api/free-fitness/remix";
    private FitnessRemixClient() {}
    private static void pause(long ms) throws IOException {
        try { Thread.sleep(ms); } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IOException("Operazione interrotta", e);
        }
    }
    private static String newToken() {
        byte[] bytes = new byte[32]; new java.security.SecureRandom().nextBytes(bytes);
        return android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING | android.util.Base64.NO_WRAP);
    }
    private static boolean existingJob(String token) throws IOException {
        HttpURLConnection c = jobGet("/status", token);
        try {
            int code = c.getResponseCode();
            if (code == 200) return true;
            if (code == 404) return false;
            throw new IOException("HTTP " + code + ": impossibile verificare il lavoro precedente");
        } finally { c.disconnect(); }
    }
    private static void awaitFreeWorker() throws IOException {
        long deadline = android.os.SystemClock.elapsedRealtime() + 720000L;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            HttpURLConnection c = (HttpURLConnection) new URL(V6.replace("/jobs", "/status")).openConnection();
            c.setConnectTimeout(15000); c.setReadTimeout(20000); c.setUseCaches(false);
            try {
                if (c.getResponseCode() == 200) {
                    org.json.JSONObject j = json(c);
                    if (!j.optBoolean("enabled") || !j.optBoolean("async_jobs") || !j.has("busy"))
                        throw new V6Terminal("Server QA non aggiornato o non disponibile");
                    if (!j.optBoolean("busy")) return;
                }
            } catch (V6Terminal e) { throw e; }
            catch (IOException ignored) {} finally { c.disconnect(); }
            pause(15000);
        }
        throw new IOException("Motore ancora occupato: riprova senza cambiare brano");
    }

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
        return createRemix(context, source, sourceBpm, targetBpm, batida, phase -> {});
    }
    public static File createRemix(Context context, Uri source, int sourceBpm, int targetBpm, boolean batida, java.util.function.Consumer<String> progress) throws IOException {
        progress.accept("PREPARAZIONE AUDIO");
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

        android.content.SharedPreferences prefs = context.getSharedPreferences("v6_jobs", Context.MODE_PRIVATE);
        String key = source.toString() + "|" + sourceBpm + "|" + targetBpm;
        String token = batida ? prefs.getString(key, "") : "";
        if (batida && !token.isEmpty()) {
            if (existingJob(token)) {
                try { File result = followV6(context, token, progress); prefs.edit().remove(key).commit(); return result; }
                catch (V6Terminal e) { prefs.edit().remove(key).commit(); throw e; }
            }
        }
        if (batida && token.isEmpty()) {
            token = newToken();
            if (!prefs.edit().putString(key, token).commit()) throw new IOException("Impossibile salvare identificativo remix");
        }
        if (batida) { progress.accept("ATTESA MOTORE"); awaitFreeWorker(); }
        String boundary = "DFMP3-" + UUID.randomUUID();
        HttpURLConnection connection = null;
        File output = null;
        File requestBody = null;
        String phase = "Preparazione upload";

        try {
            requestBody = File.createTempFile("dfmp3-upload-", ".multipart", context.getCacheDir());
            connection = (HttpURLConnection)
                    new URL(batida ? V6 : (targetBpm > 180 ? EXTENDED : ENDPOINT)).openConnection();
            connection.setInstanceFollowRedirects(false);
            if (batida) connection.setRequestProperty("X-Batida-Job", token);

            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(batida ? 60000 : 180000);
            connection.setDoOutput(true);

            connection.setRequestProperty(
                    "Content-Type",
                    "multipart/form-data; boundary=" + boundary);

            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(requestBody))) {
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

            phase = "Invio audio";
            progress.accept("UPLOAD AUDIO");
            connection.setFixedLengthStreamingMode(requestBody.length());
            try (InputStream in = new FileInputStream(requestBody);
                 OutputStream out = connection.getOutputStream()) {
                byte[] buffer = new byte[65536];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            }
            phase = "Risposta server";
            progress.accept("ELABORAZIONE SERVER");
            int status = connection.getResponseCode();

            if (batida && status == 202) {
                String returnedToken = readJobToken(connection);
                if (!token.equals(returnedToken)) throw new IOException("Identificativo job non corrispondente");
                connection.disconnect();
                connection = null;
                phase = "Recupero V6 Club QA";
                try { File result = followV6(context, token, progress); prefs.edit().remove(key).commit(); return result; }
                catch (V6Terminal e) { prefs.edit().remove(key).commit(); throw e; }
            }
            if (status != 200 || batida) {
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

            phase = "Download MP3";
            progress.accept("DOWNLOAD MP3");
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
            if (error instanceof IOException && !(error.getMessage() != null && error.getMessage().startsWith("HTTP "))) {
                throw new IOException(phase + ": " + error.getMessage(), error);
            }
            throw error;
        } finally {
            if (connection != null) connection.disconnect();
            if (requestBody != null) requestBody.delete();
        }
    }

    private static org.json.JSONObject json(HttpURLConnection c) throws IOException {
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[4096]; int n;
            while ((n = in.read(bytes)) != -1) {
                if (buf.size() + n > 65536) throw new IOException("Risposta JSON troppo grande");
                buf.write(bytes, 0, n);
            }
            try { return new org.json.JSONObject(buf.toString("UTF-8")); }
            catch (org.json.JSONException e) { throw new IOException("Risposta JSON non valida", e); }
        }
    }

    private static String readJobToken(HttpURLConnection c) throws IOException {
        String token = json(c).optString("job", "");
        if (!token.matches("[A-Za-z0-9_-]{32,128}")) throw new IOException("Token job V6 non valido");
        return token;
    }

    private static HttpURLConnection jobGet(String suffix, String token) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(V6 + suffix).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setUseCaches(false);
        c.setRequestProperty("Cache-Control", "no-store");
        c.setRequestProperty("X-Batida-Job", token);
        return c;
    }

    private static boolean retryable(int status) {
        return status == 408 || status == 429 || status == 502 || status == 503 || status == 504;
    }

    private static File followV6(Context context, String token, java.util.function.Consumer<String> progress) throws IOException {
        progress.accept("RECUPERO REMIX");
        long deadline = android.os.SystemClock.elapsedRealtime() + 1320000L;
        long delay = 2500;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            HttpURLConnection c = null;
            boolean ready = false;
            try {
                c = jobGet("/status", token);
                int status = c.getResponseCode();
                if (status == 404) throw new V6Terminal("HTTP 404: job scaduto o server riavviato");
                if (status != 200) {
                    if (!retryable(status)) throw new V6Terminal("HTTP " + status + ": stato V6 non disponibile");
                    throw new IOException("Connessione temporaneamente non disponibile");
                }
                org.json.JSONObject state = json(c);
                String s = state.optString("state");
                if ("running".equals(s)) {
                    String phase = state.optString("phase", "starting");
                    switch (phase) {
                        case "analysis": progress.accept("ANALISI BPM"); break;
                        case "mix": progress.accept("REMIX CLUB"); break;
                        case "encoding": progress.accept("CODIFICA MP3"); break;
                        case "validation": progress.accept("VERIFICA AUDIO"); break;
                        default: progress.accept("AVVIO MOTORE");
                    }
                }
                if ("failed".equals(s)) throw new V6Terminal(state.optString("error", "Elaborazione V6 fallita"));
                ready = "ready".equals(s);
                if (!ready && !"running".equals(s)) throw new V6Terminal("Stato V6 non riconosciuto");
                delay = 2500;
            } catch (V6Terminal e) { throw e; }
            catch (IOException e) { delay = Math.min(15000L, Math.max(4000L, delay * 2)); }
            finally { if (c != null) c.disconnect(); }

            if (ready) {
                progress.accept("DOWNLOAD MP3");
                File output = null; boolean complete = false;
                try {
                    c = jobGet("/result", token);
                    int code = c.getResponseCode();
                    if (code == 404) throw new V6Terminal("HTTP 404: risultato V6 scaduto");
                    if (code != 200) {
                        if (!retryable(code)) throw new V6Terminal("HTTP " + code + ": download V6 non riuscito");
                        throw new IOException("Download temporaneamente non disponibile");
                    }
                    if (!"140".equals(c.getHeaderField("X-Target-BPM"))
                            || !"batida-extreme-qa".equals(c.getHeaderField("X-Remix-Mode"))
                            || c.getContentType() == null
                            || !c.getContentType().toLowerCase(java.util.Locale.ROOT).startsWith("audio/mpeg"))
                        throw new V6Terminal("Risposta V6 non conforme");
                    output = File.createTempFile("alphabeat-v6-", ".mp3", context.getCacheDir());
                    long expected = c.getContentLengthLong(), size = 0;
                    try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(output)) {
                        byte[] buf = new byte[65536]; int n;
                        while ((n = in.read(buf)) != -1) {
                            size += n;
                            if (size > 30L * 1024 * 1024) throw new V6Terminal("MP3 oltre il limite QA");
                            out.write(buf, 0, n);
                        }
                    }
                    if (size == 0 || (expected >= 0 && expected != size)) throw new IOException("Download incompleto");
                    complete = true;
                    return output;
                } catch (V6Terminal e) { throw e; }
                catch (IOException e) { delay = Math.min(15000L, Math.max(4000L, delay * 2)); }
                finally {
                    if (c != null) c.disconnect();
                    if (!complete && output != null) output.delete();
                }
            }
            try { Thread.sleep(delay); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Verifica V6 interrotta", e); }
        }
        throw new IOException("Tempo massimo recupero V6 raggiunto");
    }

    private static final class V6Terminal extends IOException {
        V6Terminal(String message) { super(message); }
    }
}
