package com.boloboard.app;

import android.app.Activity;
import android.content.SharedPreferences;
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
 * BB4 stores John and Alexis in two separate encrypted JSON records. Splitting
 * the profiles keeps each request comfortably below the service item limit and
 * also lets one profile update without rewriting the other profile.
 */
public class FamilySyncFixedApp extends BoloBoardApp {
    private static final String API_BASE = "https://api.jsonstorage.net/v1/json";
    private static final String PREFIX = "js4_";
    private static final String PAIR_VERSION = "BB4";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final SecureRandom secureRandom = new SecureRandom();
    private WeakReference<Activity> currentMain = new WeakReference<>(null);

    @Override public void onActivityStarted(Activity activity) {
        super.onActivityStarted(activity);
        if (activity instanceof MainActivity) currentMain = new WeakReference<>(activity);
    }

    @Override public void onActivityDestroyed(Activity activity) {
        super.onActivityDestroyed(activity);
        Activity a = currentMain.get();
        if (a == activity) currentMain = new WeakReference<>(null);
    }

    @Override public String getPairingCode() {
        SharedPreferences global = getSharedPreferences(GLOBAL, MODE_PRIVATE);
        String stored = global.getString(KEY_BLOB, "");
        String secret = global.getString(KEY_SECRET, "");
        if (stored.startsWith(PREFIX) && !secret.isEmpty()) {
            return PAIR_VERSION + ":" + stored.substring(PREFIX.length()) + ":" + secret;
        }
        return super.getPairingCode();
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

                String johnId = createRemote(encrypt(profileDocument("john", JOHN, now), key));
                String alexisId = createRemote(encrypt(profileDocument("alexis", ALEXIS, now), key));
                String remotePair = johnId + "," + alexisId;

                getSharedPreferences(GLOBAL, MODE_PRIVATE).edit()
                        .putString(KEY_BLOB, PREFIX + remotePair)
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
                if (parts.length != 3) throw new Exception("Invalid BOLO pairing code");
                if (!PAIR_VERSION.equals(parts[0])) {
                    busy.set(false);
                    super.joinFamilySync(pairingCode, callback);
                    return;
                }

                String[] ids = parts[1].split(",", 2);
                if (ids.length != 2 || ids[0].trim().isEmpty() || ids[1].trim().isEmpty() || parts[2].trim().isEmpty()) {
                    throw new Exception("Invalid BOLO pairing code");
                }

                byte[] key = decodeKey(parts[2].trim());
                JSONObject johnRemote = decrypt(readRemote(ids[0].trim()), key);
                JSONObject alexisRemote = decrypt(readRemote(ids[1].trim()), key);
                validateProfile(johnRemote, "john");
                validateProfile(alexisRemote, "alexis");

                saveSafetySnapshot();
                applyProfile(JOHN, johnRemote, KEY_JOHN_MOD);
                applyProfile(ALEXIS, alexisRemote, KEY_ALEXIS_MOD);

                SharedPreferences global = getSharedPreferences(GLOBAL, MODE_PRIVATE);
                global.edit()
                        .putString(KEY_BLOB, PREFIX + ids[0].trim() + "," + ids[1].trim())
                        .putString(KEY_SECRET, parts[2].trim())
                        .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                        .remove(KEY_LAST_ERROR)
                        .putBoolean(KEY_PROMPT_DISMISSED, true)
                        .apply();

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
        String secret = global.getString(KEY_SECRET, "");
        if (stored.isEmpty() || secret.isEmpty()) {
            if (callback != null) complete(callback, false, "Family sync is not connected");
            return;
        }

        if (!stored.startsWith(PREFIX)) {
            if (stored.startsWith("io_") || stored.startsWith("js_")) {
                String msg = "Older Family Sync detected • create a new Family Sync once, then pair the other phone";
                global.edit().putString(KEY_LAST_ERROR, msg).apply();
                if (callback != null) complete(callback, false, msg);
                return;
            }
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
                String[] ids = stored.substring(PREFIX.length()).split(",", 2);
                if (ids.length != 2) throw new Exception("Family sync connection is incomplete");
                byte[] key = decodeKey(secret);

                JSONObject rJohn = decrypt(readRemote(ids[0]), key);
                JSONObject rAlexis = decrypt(readRemote(ids[1]), key);
                validateProfile(rJohn, "john");
                validateProfile(rAlexis, "alexis");

                long remoteJohnMod = rJohn.optLong("modifiedAt", 0L);
                long remoteAlexisMod = rAlexis.optLong("modifiedAt", 0L);
                long localJohnMod = global.getLong(KEY_JOHN_MOD, 1L);
                long localAlexisMod = global.getLong(KEY_ALEXIS_MOD, 1L);

                if (remoteJohnMod > localJohnMod) {
                    saveSafetySnapshot();
                    applyProfile(JOHN, rJohn, KEY_JOHN_MOD);
                    pulled = true;
                } else if (localJohnMod > remoteJohnMod) {
                    updateRemote(ids[0], encrypt(profileDocument("john", JOHN, localJohnMod), key));
                }

                if (remoteAlexisMod > localAlexisMod) {
                    saveSafetySnapshot();
                    applyProfile(ALEXIS, rAlexis, KEY_ALEXIS_MOD);
                    pulled = true;
                } else if (localAlexisMod > remoteAlexisMod) {
                    updateRemote(ids[1], encrypt(profileDocument("alexis", ALEXIS, localAlexisMod), key));
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

    private JSONObject profileDocument(String profile, String prefsName, long modifiedAt) throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", "BOLO_BOARD_PROFILE_SYNC");
        root.put("version", 4);
        root.put("profile", profile);
        root.put("modifiedAt", modifiedAt);
        root.put("data", prefsToJson(getSharedPreferences(prefsName, MODE_PRIVATE)));
        return root;
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

    private void validateProfile(JSONObject root, String expected) throws Exception {
        if (!"BOLO_BOARD_PROFILE_SYNC".equals(root.optString("format")) || !expected.equals(root.optString("profile")) || !root.has("data")) {
            throw new Exception("The shared " + expected + " profile is incomplete or invalid");
        }
    }

    private void applyProfile(String prefsName, JSONObject root, String modifiedKey) throws Exception {
        JSONObject data = root.getJSONObject("data");
        SharedPreferences.Editor editor = getSharedPreferences(prefsName, MODE_PRIVATE).edit().clear();
        Iterator<String> keys = data.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            Object value = data.get(k);
            if (value instanceof Boolean) editor.putBoolean(k, (Boolean) value);
            else if (value instanceof Integer) editor.putInt(k, (Integer) value);
            else if (value instanceof Long) editor.putLong(k, (Long) value);
            else if (value instanceof Double || value instanceof Float) editor.putString(k, String.valueOf(value));
            else if (value instanceof JSONArray) {
                Set<String> set = new HashSet<>();
                JSONArray arr = (JSONArray) value;
                for (int i = 0; i < arr.length(); i++) set.add(arr.getString(i));
                editor.putStringSet(k, set);
            } else editor.putString(k, String.valueOf(value));
        }
        if (!editor.commit()) throw new Exception("Unable to save synchronized profile locally");
        getSharedPreferences(GLOBAL, MODE_PRIVATE).edit()
                .putLong(modifiedKey, root.optLong("modifiedAt", System.currentTimeMillis()))
                .apply();
    }

    private String encrypt(JSONObject plaintext, byte[] key) throws Exception {
        byte[] iv = new byte[12];
        secureRandom.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] ciphertext = cipher.doFinal(plaintext.toString().getBytes(StandardCharsets.UTF_8));
        JSONObject envelope = new JSONObject();
        envelope.put("format", "BOLO_BOARD_SYNC_ENCRYPTED");
        envelope.put("version", 1);
        envelope.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP));
        envelope.put("data", Base64.encodeToString(ciphertext, Base64.NO_WRAP));
        return envelope.toString();
    }

    private JSONObject decrypt(String envelopeText, byte[] key) throws Exception {
        JSONObject envelope = new JSONObject(envelopeText);
        if (!"BOLO_BOARD_SYNC_ENCRYPTED".equals(envelope.optString("format"))) {
            throw new Exception("Shared data is not encrypted BOLO Board data");
        }
        byte[] iv = Base64.decode(envelope.getString("iv"), Base64.DEFAULT);
        byte[] ciphertext = Base64.decode(envelope.getString("data"), Base64.DEFAULT);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        return new JSONObject(new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8));
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

    private String createRemote(String encryptedJson) throws Exception {
        HttpURLConnection conn = request(API_BASE, "POST", encryptedJson);
        int code = conn.getResponseCode();
        String body = readBody(conn, code);
        if (code < 200 || code >= 300) throw new Exception("Family cloud create failed (" + code + ")" + shortBody(body));
        String uri = new JSONObject(body).optString("uri", "").trim();
        String marker = "/v1/json/";
        int at = uri.indexOf(marker);
        if (at < 0 || at + marker.length() >= uri.length()) throw new Exception("Family cloud did not return a usable board ID");
        return uri.substring(at + marker.length());
    }

    private String readRemote(String remoteId) throws Exception {
        HttpURLConnection conn = request(API_BASE + "/" + remoteId, "GET", null);
        int code = conn.getResponseCode();
        String body = readBody(conn, code);
        if (code == 404) throw new Exception("Shared board not found");
        if (code < 200 || code >= 300) throw new Exception("Family cloud read failed (" + code + ")" + shortBody(body));
        return body;
    }

    private void updateRemote(String remoteId, String encryptedJson) throws Exception {
        HttpURLConnection conn = request(API_BASE + "/" + remoteId, "PUT", encryptedJson);
        int code = conn.getResponseCode();
        String body = readBody(conn, code);
        if (code < 200 || code >= 300) throw new Exception("Family cloud update failed (" + code + ")" + shortBody(body));
    }

    private HttpURLConnection request(String address, String method, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("User-Agent", "BOLO-Board/1.8.1");
        conn.setRequestProperty("Cache-Control", "no-cache");
        conn.setUseCaches(false);
        conn.setInstanceFollowRedirects(true);
        if (body != null) {
            conn.setDoOutput(true);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(bytes);
                out.flush();
            }
        }
        return conn;
    }

    private String readBody(HttpURLConnection conn, int code) throws Exception {
        InputStream in = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
        if (in == null) return "";
        return readAll(in);
    }

    private String readAll(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private String shortBody(String body) {
        if (body == null) return "";
        String s = body.replace('\n', ' ').replace('\r', ' ').trim();
        if (s.length() > 160) s = s.substring(0, 160);
        return s.isEmpty() ? "" : " • " + s;
    }

    private String clean(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) message = e.getClass().getSimpleName();
        if (message.length() > 220) message = message.substring(0, 220);
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
