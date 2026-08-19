package com.boloboard.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * BOLO Family Sync transport.
 *
 * New boards use jsonblob.io's documented anonymous create endpoint. The cloud
 * payload is AES-GCM encrypted before upload and this transport never clears the
 * local John/Alexis data when a network request fails.
 *
 * Important: jsonblob.io documents its API with curl-style POST requests. A
 * browser-identifying Android request can be rejected with HTTP 403 by the edge
 * layer, so this version intentionally uses a minimal curl-compatible request
 * profile rather than pretending to be Chrome.
 */
public class FamilySyncFixedApp extends BoloBoardApp {
    private static final String API_BASE = "https://jsonblob.io";
    private static final String PREFIX = "io_";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final SecureRandom secureRandom = new SecureRandom();
    private WeakReference<Activity> currentMain = new WeakReference<>(null);

    @Override public void onCreate() {
        super.onCreate();
    }

    @Override public void onActivityStarted(Activity activity) {
        super.onActivityStarted(activity);
        if (activity instanceof MainActivity) currentMain = new WeakReference<>(activity);
    }

    @Override public void onActivityDestroyed(Activity activity) {
        super.onActivityDestroyed(activity);
        Activity a = currentMain.get();
        if (a == activity) currentMain = new WeakReference<>(null);
    }

    @Override public void createFamilySync(SyncCallback callback) {
        if (busy.getAndSet(true)) {
            complete(callback, false, "A sync is already running");
            return;
        }
        ioExecutor.execute(() -> {
            try {
                saveSafetySnapshot();
                byte[] key = new byte[32];
                secureRandom.nextBytes(key);
                String encodedKey = Base64.encodeToString(key, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
                long now = System.currentTimeMillis();
                JSONObject doc = buildDocument(now, now);
                String encrypted = encrypt(doc.toString(), key);
                String uuid = createRemote(encrypted);

                getSharedPreferences(GLOBAL, MODE_PRIVATE).edit()
                        .putString(KEY_BLOB, PREFIX + uuid)
                        .putString(KEY_SECRET, encodedKey)
                        .putLong(KEY_JOHN_MOD, now)
                        .putLong(KEY_ALEXIS_MOD, now)
                        .putLong(KEY_LAST_SYNC, now)
                        .remove(KEY_LAST_ERROR)
                        .putBoolean(KEY_PROMPT_DISMISSED, true)
                        .apply();

                complete(callback, true, "Family sync created • ready to pair the second phone");
            } catch (Exception e) {
                complete(callback, false, clean(e));
            } finally {
                busy.set(false);
            }
        });
    }

    @Override public void joinFamilySync(String pairingCode, SyncCallback callback) {
        if (busy.getAndSet(true)) {
            complete(callback, false, "A sync is already running");
            return;
        }
        ioExecutor.execute(() -> {
            try {
                String[] parts = pairingCode == null ? new String[0] : pairingCode.trim().split(":", 3);
                if (parts.length != 3 || (!"BB2".equals(parts[0]) && !"BB1".equals(parts[0]))) {
                    throw new Exception("Invalid BOLO pairing code");
                }
                if (parts[1].trim().isEmpty() || parts[2].trim().isEmpty()) throw new Exception("Invalid BOLO pairing code");

                if ("BB1".equals(parts[0])) {
                    busy.set(false);
                    super.joinFamilySync(pairingCode, callback);
                    return;
                }

                byte[] key = decodeKey(parts[2].trim());
                String uuid = parts[1].trim();
                JSONObject remote = new JSONObject(decrypt(readRemote(uuid), key));
                validate(remote);

                saveSafetySnapshot();
                SharedPreferences global = getSharedPreferences(GLOBAL, MODE_PRIVATE);
                global.edit()
                        .putString(KEY_BLOB, PREFIX + uuid)
                        .putString(KEY_SECRET, parts[2].trim())
                        .putBoolean(KEY_PROMPT_DISMISSED, true)
                        .apply();

                applyProfile(JOHN, remote.getJSONObject("john"), KEY_JOHN_MOD);
                applyProfile(ALEXIS, remote.getJSONObject("alexis"), KEY_ALEXIS_MOD);
                global.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).remove(KEY_LAST_ERROR).apply();
                refreshMain();
                complete(callback, true, "Connected • shared family board downloaded");
            } catch (Exception e) {
                complete(callback, false, clean(e));
            } finally {
                busy.set(false);
            }
        });
    }

    @Override public void syncNow(boolean userInitiated, SyncCallback callback) {
        SharedPreferences global = getSharedPreferences(GLOBAL, MODE_PRIVATE);
        String stored = global.getString(KEY_BLOB, "");
        if (stored.isEmpty() || global.getString(KEY_SECRET, "").isEmpty()) {
            if (callback != null) complete(callback, false, "Family sync is not connected");
            return;
        }
        if (!stored.startsWith(PREFIX)) {
            super.syncNow(userInitiated, callback);
            return;
        }
        if (busy.getAndSet(true)) {
            if (callback != null) complete(callback, false, "A sync is already running");
            return;
        }

        ioExecutor.execute(() -> {
            boolean pulled = false;
            try {
                String uuid = stored.substring(PREFIX.length());
                byte[] key = decodeKey(global.getString(KEY_SECRET, ""));
                JSONObject remote = new JSONObject(decrypt(readRemote(uuid), key));
                validate(remote);

                JSONObject remoteJohn = remote.getJSONObject("john");
                JSONObject remoteAlexis = remote.getJSONObject("alexis");
                long rJohn = remoteJohn.optLong("modifiedAt", 0L);
                long rAlexis = remoteAlexis.optLong("modifiedAt", 0L);
                long lJohn = global.getLong(KEY_JOHN_MOD, 1L);
                long lAlexis = global.getLong(KEY_ALEXIS_MOD, 1L);
                boolean push = false;

                if (rJohn > lJohn) {
                    saveSafetySnapshot();
                    applyProfile(JOHN, remoteJohn, KEY_JOHN_MOD);
                    pulled = true;
                } else if (lJohn > rJohn) {
                    remote.put("john", profileBlock(getSharedPreferences(JOHN, MODE_PRIVATE), lJohn));
                    push = true;
                }

                if (rAlexis > lAlexis) {
                    saveSafetySnapshot();
                    applyProfile(ALEXIS, remoteAlexis, KEY_ALEXIS_MOD);
                    pulled = true;
                } else if (lAlexis > rAlexis) {
                    remote.put("alexis", profileBlock(getSharedPreferences(ALEXIS, MODE_PRIVATE), lAlexis));
                    push = true;
                }

                if (push) {
                    remote.put("updatedAt", System.currentTimeMillis());
                    updateRemote(uuid, encrypt(remote.toString(), key));
                }

                global.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).remove(KEY_LAST_ERROR).apply();
                if (pulled) refreshMain();
                complete(callback, true, pulled ? "Synced • new family changes downloaded" : "Synced • up to date");
            } catch (Exception e) {
                String message = clean(e);
                global.edit().putString(KEY_LAST_ERROR, message).apply();
                if (userInitiated) complete(callback, false, message);
                else complete(callback, false, null);
            } finally {
                busy.set(false);
            }
        });
    }

    private JSONObject buildDocument(long johnMod, long alexisMod) throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", "BOLO_BOARD_FAMILY_SYNC");
        root.put("version", 2);
        root.put("updatedAt", System.currentTimeMillis());
        root.put("john", profileBlock(getSharedPreferences(JOHN, MODE_PRIVATE), johnMod));
        root.put("alexis", profileBlock(getSharedPreferences(ALEXIS, MODE_PRIVATE), alexisMod));
        return root;
    }

    private JSONObject profileBlock(SharedPreferences prefs, long modifiedAt) throws Exception {
        JSONObject block = new JSONObject();
        block.put("modifiedAt", modifiedAt);
        block.put("data", prefsToJson(prefs));
        return block;
    }

    private JSONObject prefsToJson(SharedPreferences prefs) throws Exception {
        JSONObject result = new JSONObject();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String || value instanceof Boolean || value instanceof Integer || value instanceof Long || value instanceof Double || value instanceof Float) {
                result.put(entry.getKey(), value);
            } else if (value instanceof Set) {
                JSONArray array = new JSONArray();
                for (Object item : (Set<?>) value) array.put(String.valueOf(item));
                result.put(entry.getKey(), array);
            }
        }
        return result;
    }

    private void applyProfile(String prefsName, JSONObject block, String modifiedKey) throws Exception {
        SharedPreferences target = getSharedPreferences(prefsName, MODE_PRIVATE);
        JSONObject data = block.getJSONObject("data");
        SharedPreferences.Editor editor = target.edit().clear();
        Iterator<String> keys = data.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = data.get(key);
            if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Double || value instanceof Float) editor.putString(key, String.valueOf(value));
            else if (value instanceof JSONArray) {
                Set<String> set = new HashSet<>();
                JSONArray arr = (JSONArray) value;
                for (int i = 0; i < arr.length(); i++) set.add(arr.getString(i));
                editor.putStringSet(key, set);
            } else editor.putString(key, String.valueOf(value));
        }
        if (!editor.commit()) throw new Exception("Unable to save synchronized profile locally");
        getSharedPreferences(GLOBAL, MODE_PRIVATE).edit()
                .putLong(modifiedKey, block.optLong("modifiedAt", System.currentTimeMillis()))
                .apply();
    }

    private void validate(JSONObject root) throws Exception {
        if (!"BOLO_BOARD_FAMILY_SYNC".equals(root.optString("format")) || !root.has("john") || !root.has("alexis")) {
            throw new Exception("The shared BOLO Board is incomplete or invalid");
        }
    }

    private String encrypt(String plaintext, byte[] key) throws Exception {
        byte[] iv = new byte[12];
        secureRandom.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        JSONObject envelope = new JSONObject();
        envelope.put("format", "BOLO_BOARD_SYNC_ENCRYPTED");
        envelope.put("version", 1);
        envelope.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP));
        envelope.put("data", Base64.encodeToString(ciphertext, Base64.NO_WRAP));
        return envelope.toString();
    }

    private String decrypt(String envelopeText, byte[] key) throws Exception {
        JSONObject envelope = new JSONObject(envelopeText);
        if (!"BOLO_BOARD_SYNC_ENCRYPTED".equals(envelope.optString("format"))) throw new Exception("Shared data is not encrypted BOLO Board data");
        byte[] iv = Base64.decode(envelope.getString("iv"), Base64.DEFAULT);
        byte[] ciphertext = Base64.decode(envelope.getString("data"), Base64.DEFAULT);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    private byte[] decodeKey(String encoded) throws Exception {
        try {
            byte[] key = Base64.decode(encoded, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            if (key.length != 32) throw new Exception("wrong length");
            return key;
        } catch (Exception e) {
            throw new Exception("Invalid family sync encryption key");
        }
    }

    private String createRemote(String json) throws Exception {
        HttpURLConnection conn = open(API_BASE + "/", "POST");
        write(conn, json);
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) throw new Exception("Cloud create failed (" + code + ")" + readError(conn));
        String uuid = conn.getHeaderField("x-blob-uuid");
        if (uuid == null || uuid.trim().isEmpty()) uuid = conn.getHeaderField("X-Blob-Uuid");
        consume(conn);
        if (uuid == null || uuid.trim().isEmpty()) throw new Exception("Cloud created the board but did not return its pairing ID");
        return uuid.trim();
    }

    private String readRemote(String uuid) throws Exception {
        HttpURLConnection conn = open(API_BASE + "/" + uuid, "GET");
        int code = conn.getResponseCode();
        if (code == 404) throw new Exception("Shared board not found");
        if (code < 200 || code >= 300) throw new Exception("Cloud read failed (" + code + ")" + readError(conn));
        return readAll(conn.getInputStream());
    }

    private void updateRemote(String uuid, String json) throws Exception {
        HttpURLConnection conn = open(API_BASE + "/" + uuid, "POST");
        write(conn, json);
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) throw new Exception("Cloud update failed (" + code + ")" + readError(conn));
        consume(conn);
    }

    private HttpURLConnection open(String address, String method) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Accept", "*/*");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("User-Agent", "curl/8.5.0");
        conn.setRequestProperty("Cache-Control", "no-cache");
        conn.setRequestProperty("Connection", "close");
        conn.setUseCaches(false);
        conn.setInstanceFollowRedirects(true);
        return conn;
    }

    private void write(HttpURLConnection conn, String json) throws Exception {
        conn.setDoOutput(true);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(bytes);
            out.flush();
        }
    }

    private String readAll(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private void consume(HttpURLConnection conn) {
        try {
            InputStream in = conn.getInputStream();
            if (in != null) while (in.read() != -1) { }
            if (in != null) in.close();
        } catch (Exception ignored) { }
    }

    private String readError(HttpURLConnection conn) {
        try {
            InputStream in = conn.getErrorStream();
            if (in == null) return "";
            String body = readAll(in).replace('\n', ' ').replace('\r', ' ').trim();
            if (body.length() > 100) body = body.substring(0, 100);
            return body.isEmpty() ? "" : " • " + body;
        } catch (Exception ignored) {
            return "";
        }
    }

    private String clean(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) message = e.getClass().getSimpleName();
        if (message.length() > 180) message = message.substring(0, 180);
        return message;
    }

    private void complete(SyncCallback callback, boolean ok, String message) {
        mainHandler.post(() -> {
            if (callback != null) callback.done(ok, message);
        });
    }

    private void refreshMain() {
        mainHandler.post(() -> {
            Activity a = currentMain.get();
            if (a != null && !a.isFinishing() && !a.isDestroyed()) a.recreate();
        });
    }
}
