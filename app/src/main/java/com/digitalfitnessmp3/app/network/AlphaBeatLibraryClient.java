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
    private static final int MAX_JSON = 2 * 1024 * 1024;

    public AlphaBeatLibraryClient(String apiOrigin, String storageOrigin) {
        this.apiOrigin = requireOrigin(apiOrigin);
        this.storageOrigin = requireOrigin(storageOrigin);
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

    public JSONArray list(String accessToken) throws IOException {
        try {
            return new JSONArray(getJson("/api/library/remixes", accessToken));
        } catch (JSONException e) {
            throw new IOException("Risposta libreria non valida");
        }
    }

    /** Validate the descriptor before any future authenticated audio download.
     * A descriptor is not a public URL and does not itself authorize access. */
    public URL audioDescriptor(UUID remixId, UUID ownerId, String accessToken) throws IOException {
        if (remixId == null || ownerId == null) throw new IOException("Identificativo mancante");
        try {
            JSONObject data = new JSONObject(getJson("/api/library/remixes/" + remixId + "/audio", accessToken));
            String prefix = storageOrigin + "/storage/v1/object/authenticated/alphabeat-remixes/" + ownerId + "/";
            String url = data.getString("url");
            if (!data.getBoolean("requires_authentication") || !url.startsWith(prefix)) {
                throw new IOException("Percorso audio non autorizzato");
            }
            String name = url.substring(prefix.length());
            if (!name.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(mp3|wav)")) {
                throw new IOException("Nome audio non valido");
            }
            String type = data.getString("content_type");
            if (!(name.endsWith(".mp3") ? "audio/mpeg" : "audio/wav").equals(type)) {
                throw new IOException("Formato audio non valido");
            }
            return new URL(url);
        } catch (JSONException e) {
            throw new IOException("Risposta audio non valida");
        }
    }
}
