package com.digitalfitnessmp3.app.network;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** QA-only library adapter. Call on a worker thread with the current
 * authenticated user's access token. No tokens are stored by this class.
 * Origins must come from trusted build configuration, never user input.
 * Not connected to the release UI or a live database. */
public final class AlphaBeatLibraryClient {
    private final String apiOrigin;
    private final String storageOrigin;
    private final String publicKey;
    private static final int MAX_JSON = 2 * 1024 * 1024;

    public AlphaBeatLibraryClient(String apiOrigin, String storageOrigin, String publicKey) {
        this.apiOrigin = requireOrigin(apiOrigin);
        this.storageOrigin = requireOrigin(storageOrigin);
        if (!"https://zibsxuveoekuenppgkah.supabase.co".equals(this.apiOrigin)
                || !this.apiOrigin.equals(this.storageOrigin)
                || publicKey == null || !publicKey.matches("sb_publishable_[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Configurazione QA non valida");
        }
        this.publicKey = publicKey;
    }

    private static String requireOrigin(String origin) {
        if (origin == null || !origin.matches("https://[a-zA-Z0-9.-]+")) {
            throw new IllegalArgumentException("Origine HTTPS non valida");
        }
        return origin;
    }

    private String getJson(String path, String token) throws IOException {
        if (token == null || token.isEmpty() || token.contains("\r") || token.contains("\n")) {
            throw new IOException("Sessione AlphaBeat richiesta");
        }
        HttpURLConnection c = (HttpURLConnection) new URL(apiOrigin + path).openConnection();
        try {
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setUseCaches(false);
            c.setRequestProperty("Authorization", "Bearer " + token);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("apikey", publicKey);
            c.setRequestProperty("Cache-Control", "no-store");
            int status = c.getResponseCode();
            if (status != 200) throw new IOException("Libreria non disponibile: HTTP " + status);
            try (InputStream in = c.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    if (out.size() + n > MAX_JSON) throw new IOException("Risposta troppo grande");
                    out.write(buffer, 0, n);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }

    public JSONArray list(String accessToken, UUID ownerId) throws IOException {
        if (ownerId == null) throw new IOException("Identificativo mancante");
        try {
            return new JSONArray(getJson("/rest/v1/user_remixes?select=id,title,target_bpm,created_at&owner_id=eq."
                + ownerId + "&deletion_status=eq.active&order=created_at.desc&limit=100", accessToken));
        } catch (JSONException e) {
            throw new IOException("Risposta libreria non valida");
        }
    }

    /** Resolve a private storage path through owner-scoped PostgREST. */
    public URL audioDescriptor(UUID remixId, UUID ownerId, String accessToken) throws IOException {
        if (remixId == null || ownerId == null) throw new IOException("Identificativo mancante");
        try {
            JSONArray rows = new JSONArray(getJson("/rest/v1/user_remixes?select=id,owner_id,storage_path&id=eq."
                + remixId + "&owner_id=eq." + ownerId + "&deletion_status=eq.active&limit=1", accessToken));
            if (rows.length() != 1) throw new IOException("Remix non disponibile");
            JSONObject row = rows.getJSONObject(0);
            if (!remixId.toString().equals(row.getString("id")) || !ownerId.toString().equals(row.getString("owner_id")))
                throw new IOException("Remix non autorizzato");
            String path = row.getString("storage_path");
            String prefix = ownerId.toString() + "/";
            if (!path.startsWith(prefix)) throw new IOException("Percorso audio non autorizzato");
            String name = path.substring(prefix.length());
            if (!name.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(mp3|wav)"))
                throw new IOException("Nome audio non valido");
            return new URL(storageOrigin + "/storage/v1/object/authenticated/alphabeat-remixes/" + path);
        } catch (JSONException e) {
            throw new IOException("Risposta audio non valida");
        }
    }

    /** Download only to the app-private cache. The caller must recheck the
     * authenticated owner before importing into the timeline and delete the
     * temporary file when finished or on logout. No automatic playback. */
    public java.io.File downloadAudio(android.content.Context context,
            UUID remixId, UUID ownerId, String accessToken, String publicKey,
            java.util.function.BooleanSupplier sessionStillCurrent) throws IOException {
        if (context == null || sessionStillCurrent == null || !sessionStillCurrent.getAsBoolean()) {
            throw new IOException("Sessione non disponibile");
        }
        if (!this.publicKey.equals(publicKey) || publicKey.isEmpty() || publicKey.contains("\r")
                || publicKey.contains("\n") || publicKey.startsWith("sb_secret_")) {
            throw new IOException("Configurazione libreria non valida");
        }
        // Only allow publishable keys or legacy JWT keys explicitly marked anon.
        if (!publicKey.startsWith("sb_publishable_")) {
            try {
                String[] parts = publicKey.split("\\.");
                if (parts.length != 3) throw new IllegalArgumentException();
                byte[] claims = android.util.Base64.decode(parts[1],
                        android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP);
                if (!"anon".equals(new JSONObject(new String(claims, StandardCharsets.UTF_8)).optString("role"))) {
                    throw new IllegalArgumentException();
                }
            } catch (JSONException | IllegalArgumentException e) {
                throw new IOException("Chiave pubblica richiesta");
            }
        }
        URL audio = audioDescriptor(remixId, ownerId, accessToken);
        if (!sessionStillCurrent.getAsBoolean()) throw new IOException("Sessione cambiata");
        String suffix = audio.getPath().endsWith(".mp3") ? ".mp3" : ".wav";
        String mime = ".mp3".equals(suffix) ? "audio/mpeg" : "audio/wav";
        final long maxBytes = 50_000_000L;
        java.io.File dir = new java.io.File(context.getCacheDir(), "alphabeat-library");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cache non disponibile");
        java.io.File target = java.io.File.createTempFile("remix-", suffix, dir);
        HttpURLConnection c = null;
        boolean complete = false;
        try {
            c = (HttpURLConnection) audio.openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setUseCaches(false);
            c.setRequestProperty("Authorization", "Bearer " + accessToken);
            c.setRequestProperty("apikey", publicKey);
            c.setRequestProperty("Accept", mime);
            c.setRequestProperty("Accept-Encoding", "identity");
            c.setRequestProperty("Cache-Control", "no-store");
            int status = c.getResponseCode();
            if (status != 200) throw new IOException("Audio non disponibile: HTTP " + status);
            String actualType = c.getContentType();
            if (actualType == null || !mime.equalsIgnoreCase(actualType.split(";")[0].trim())) {
                throw new IOException("Formato audio non valido");
            }
            long expected = c.getContentLengthLong();
            if (expected == 0 || expected > maxBytes) throw new IOException("Dimensione audio non valida");
            long count = 0;
            try (InputStream in = c.getInputStream();
                 java.io.FileOutputStream out = new java.io.FileOutputStream(target)) {
                byte[] buffer = new byte[32768];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted() || !sessionStillCurrent.getAsBoolean()) {
                        throw new IOException("Download annullato");
                    }
                    count += n;
                    if (count > maxBytes) throw new IOException("Audio superiore a 50 MB");
                    out.write(buffer, 0, n);
                }
            }
            if (count == 0 || (expected >= 0 && count != expected)
                    || !sessionStillCurrent.getAsBoolean()) {
                throw new IOException("Download incompleto o sessione cambiata");
            }
            complete = true;
            return target;
        } finally {
            if (c != null) c.disconnect();
            if (!complete) target.delete();
        }
    }
}
