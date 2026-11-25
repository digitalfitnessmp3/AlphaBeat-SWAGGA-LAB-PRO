package com.digitalfitnessmp3.app.network;

import android.os.SystemClock;
import android.util.Base64;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** QA-only password login for existing AlphaBeat accounts.
 * Supply a trusted QA Supabase project origin and PUBLIC key from build config.
 * No password, refresh token or access token is written to persistent storage.
 * OAuth providers and refresh-token rotation are deliberately not implemented yet. */
public final class AlphaBeatAccountClient {
    private final String origin;
    private final String publicKey;

    public AlphaBeatAccountClient(String origin, String publicKey) {
        if (origin == null || !origin.matches("https://[a-z0-9]+\\.supabase\\.co"))
            throw new IllegalArgumentException("Trusted project origin required");
        if (publicKey == null || publicKey.contains("\r") || publicKey.contains("\n"))
            throw new IllegalArgumentException("Public key required");
        if (!publicKey.startsWith("sb_publishable_")) {
            try {
                String[] p = publicKey.split("\\.");
                if (p.length != 3 || !"anon".equals(new JSONObject(new String(
                        Base64.decode(p[1], Base64.URL_SAFE | Base64.NO_WRAP),
                        StandardCharsets.UTF_8)).optString("role"))) throw new IllegalArgumentException();
            } catch (Exception e) { throw new IllegalArgumentException("Public key required"); }
        }
        this.origin = origin;
        this.publicKey = publicKey;
    }

    public static final class Session {
        public final UUID owner;
        private final String token;
        private final long deadline;
        private volatile boolean invalidated;
        private Session(UUID owner, String token, long lifetimeSeconds) {
            this.owner = owner;
            this.token = token;
            this.deadline = SystemClock.elapsedRealtime() + Math.min(lifetimeSeconds - 30, 3600) * 1000L;
        }
        public boolean isCurrent() {
            return !invalidated && SystemClock.elapsedRealtime() < deadline;
        }
        public String accessToken() throws IOException {
            if (!isCurrent()) throw new IOException("Sessione scaduta. Accedi nuovamente.");
            return token;
        }
        public void invalidate() { invalidated = true; }
    }

    /** Worker thread only. Password goes directly to the configured Auth origin. */
    public Session signIn(String email, String password) throws IOException {
        if (email == null || email.trim().isEmpty() || password == null || password.isEmpty())
            throw new IOException("Inserisci email e password.");
        try {
            JSONObject payload = new JSONObject().put("email", email.trim()).put("password", password);
            JSONObject data = request("POST", "/token?grant_type=password", payload, null);
            String token = data.getString("access_token");
            long lifetime = data.getLong("expires_in");
            if (token.isEmpty() || token.contains("\r") || token.contains("\n") || lifetime <= 30)
                throw new IOException("Sessione non valida.");
            JSONObject user = request("GET", "/user", null, token);
            UUID owner = UUID.fromString(user.getString("id"));
            if (user.optBoolean("is_anonymous", false)) throw new IOException("Account permanente richiesto.");
            if (!owner.equals(UUID.fromString(data.getJSONObject("user").getString("id"))))
                throw new IOException("Identità non corrispondente.");
            return new Session(owner, token, lifetime);
        } catch (org.json.JSONException | IllegalArgumentException e) {
            throw new IOException("Risposta account non valida.");
        }
    }

    /** Invalidate locally immediately, then revoke this device session only.
     * Call on a worker thread. The UI must also clear imported private files. */
    public void signOut(Session session) throws IOException {
        if (session == null) return;
        session.invalidate();
        try { request("POST", "/logout?scope=local", null, session.token); }
        catch (org.json.JSONException e) { throw new IOException("Logout non confermato."); }
    }

    private JSONObject request(String method, String path, JSONObject payload, String token)
            throws IOException, org.json.JSONException {
        HttpURLConnection c = (HttpURLConnection) new URL(origin + "/auth/v1" + path).openConnection();
        try {
            c.setRequestMethod(method);
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setUseCaches(false);
            c.setRequestProperty("apikey", publicKey);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("Cache-Control", "no-store");
            if (token != null) c.setRequestProperty("Authorization", "Bearer " + token);
            if (payload != null) {
                byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                c.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
                finally { java.util.Arrays.fill(bytes, (byte) 0); }
            }
            int code = c.getResponseCode();
            if (path.startsWith("/logout") && (code == 204 || code == 401 || code == 403 || code == 404))
                return new JSONObject();
            if (code != 200) throw new IOException("Account non disponibile: HTTP " + code);
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) {
                    if (out.size() + n > 256 * 1024) throw new IOException("Risposta account troppo grande");
                    out.write(buf, 0, n);
                }
                if (out.size() == 0 && path.startsWith("/logout")) return new JSONObject();
                return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally { c.disconnect(); }
    }
}
