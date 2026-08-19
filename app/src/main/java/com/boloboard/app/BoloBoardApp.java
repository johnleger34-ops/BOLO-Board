package com.boloboard.app;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
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
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class BoloBoardApp extends Application implements Application.ActivityLifecycleCallbacks {
    public static final String PREFS = "bolo_board";
    public static final String GLOBAL = PREFS + "_global";
    public static final String JOHN = PREFS + "_john";
    public static final String ALEXIS = PREFS + "_alexis";
    public static final String KEY_BLOB = "family_sync_blob_id";
    public static final String KEY_SECRET = "family_sync_key";
    public static final String KEY_LAST_SYNC = "family_sync_last_sync";
    public static final String KEY_LAST_ERROR = "family_sync_last_error";
    public static final String KEY_JOHN_MOD = "family_sync_john_modified";
    public static final String KEY_ALEXIS_MOD = "family_sync_alexis_modified";
    public static final String KEY_PROMPT_DISMISSED = "family_sync_prompt_dismissed";
    public static final String KEY_SAFETY = "family_sync_safety_snapshot";

    private static final String LEGACY_API_BASE = "https://jsonblob.com/api/jsonBlob";
    private static final String FAMILY_API_BASE = "https://jsonblob.io";
    private static final String FAMILY_PREFIX = "io_";
    private static final long POLL_MS = 15000L;
    private static final long PUSH_DEBOUNCE_MS = 1400L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicBoolean syncRunning = new AtomicBoolean(false);
    private final SecureRandom random = new SecureRandom();
    private SharedPreferences global;
    private SharedPreferences john;
    private SharedPreferences alexis;
    private boolean applyingRemote;
    private WeakReference<Activity> mainActivity = new WeakReference<>(null);
    private int startedActivities;

    private final SharedPreferences.OnSharedPreferenceChangeListener johnListener = (sp, key) -> localChanged("john");
    private final SharedPreferences.OnSharedPreferenceChangeListener alexisListener = (sp, key) -> localChanged("alexis");

    private final Runnable debouncedSync = () -> syncNow(false, null);
    private final Runnable poller = new Runnable() {
        @Override public void run() {
            if (startedActivities > 0 && isConfigured()) {
                syncNow(false, null);
                main.postDelayed(this, POLL_MS);
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        global = getSharedPreferences(GLOBAL, MODE_PRIVATE);
        john = getSharedPreferences(JOHN, MODE_PRIVATE);
        alexis = getSharedPreferences(ALEXIS, MODE_PRIVATE);
        if (!global.contains(KEY_JOHN_MOD)) global.edit().putLong(KEY_JOHN_MOD, 1L).apply();
        if (!global.contains(KEY_ALEXIS_MOD)) global.edit().putLong(KEY_ALEXIS_MOD, 1L).apply();
        john.registerOnSharedPreferenceChangeListener(johnListener);
        alexis.registerOnSharedPreferenceChangeListener(alexisListener);
        registerActivityLifecycleCallbacks(this);
    }

    public boolean isConfigured() {
        return !global.getString(KEY_BLOB, "").isEmpty() && !global.getString(KEY_SECRET, "").isEmpty();
    }

    public String getPairingCode() {
        if (!isConfigured()) return "";
        String blob = global.getString(KEY_BLOB, "");
        String secret = global.getString(KEY_SECRET, "");
        if (blob.startsWith(FAMILY_PREFIX)) {
            return "BB2:" + blob.substring(FAMILY_PREFIX.length()) + ":" + secret;
        }
        return "BB1:" + blob + ":" + secret;
    }

    public String statusText() {
        if (!isConfigured()) return "Not connected";
        String err = global.getString(KEY_LAST_ERROR, "");
        long last = global.getLong(KEY_LAST_SYNC, 0L);
        if (!err.isEmpty()) return "Connected • last sync error: " + err;
        if (last <= 0) return "Connected • waiting for first sync";
        return "Connected • last sync " + android.text.format.DateFormat.format("MMM d, h:mm a", last);
    }

    private void localChanged(String profile) {
        if (applyingRemote) return;
        long now = System.currentTimeMillis();
        if (profile.equals("john")) global.edit().putLong(KEY_JOHN_MOD, now).apply();
        else global.edit().putLong(KEY_ALEXIS_MOD, now).apply();
        if (isConfigured()) {
            main.removeCallbacks(debouncedSync);
            main.postDelayed(debouncedSync, PUSH_DEBOUNCE_MS);
        }
    }

    public void createFamilySync(SyncCallback callback) {
        if (syncRunning.getAndSet(true)) {
            if (callback != null) callback.done(false, "A sync is already running");
            return;
        }
        io.execute(() -> {
            try {
                saveSafetySnapshot();
                byte[] key = new byte[32];
                random.nextBytes(key);
                String encodedKey = Base64.encodeToString(key, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
                long now = System.currentTimeMillis();
                JSONObject doc = buildLocalDocument(now, now);
                String encrypted = encrypt(doc.toString(), key);
                String blobId = createBlob(encrypted);
                global.edit()
                        .putString(KEY_BLOB, blobId)
                        .putString(KEY_SECRET, encodedKey)
                        .putLong(KEY_JOHN_MOD, now)
                        .putLong(KEY_ALEXIS_MOD, now)
                        .putLong(KEY_LAST_SYNC, now)
                        .remove(KEY_LAST_ERROR)
                        .putBoolean(KEY_PROMPT_DISMISSED, true)
                        .apply();
                complete(callback, true, "Family sync created • local data kept safely on this phone");
            } catch (Exception e) {
                complete(callback, false, cleanError(e));
            } finally {
                syncRunning.set(false);
            }
        });
    }

    public void joinFamilySync(String pairingCode, SyncCallback callback) {
        if (syncRunning.getAndSet(true)) {
            if (callback != null) callback.done(false, "A sync is already running");
            return;
        }
        io.execute(() -> {
            try {
                String[] parts = pairingCode == null ? new String[0] : pairingCode.trim().split(":", 3);
                boolean legacy = parts.length == 3 && "BB1".equals(parts[0]);
                boolean current = parts.length == 3 && "BB2".equals(parts[0]);
                if ((!legacy && !current) || parts[1].trim().isEmpty() || parts[2].trim().isEmpty()) {
                    throw new Exception("Invalid BOLO pairing code");
                }
                byte[] key = decodeKey(parts[2].trim());
                String storedBlobId = current ? FAMILY_PREFIX + parts[1].trim() : parts[1].trim();
                String payload = getBlob(storedBlobId);
                JSONObject remote = new JSONObject(decrypt(payload, key));
                validateRemote(remote);

                // Never replace this phone's local board without first keeping a full local recovery copy.
                saveSafetySnapshot();
                global.edit()
                        .putString(KEY_BLOB, storedBlobId)
                        .putString(KEY_SECRET, parts[2].trim())
                        .putBoolean(KEY_PROMPT_DISMISSED, true)
                        .apply();
                applyRemoteProfile("john", remote.getJSONObject("john"));
                applyRemoteProfile("alexis", remote.getJSONObject("alexis"));
                long now = System.currentTimeMillis();
                global.edit().putLong(KEY_LAST_SYNC, now).remove(KEY_LAST_ERROR).apply();
                refreshMainActivity();
                complete(callback, true, "Connected • shared board downloaded • previous local data saved as a safety snapshot");
            } catch (Exception e) {
                complete(callback, false, cleanError(e));
            } finally {
                syncRunning.set(false);
            }
        });
    }

    public void syncNow(boolean userInitiated, SyncCallback callback) {
        if (!isConfigured()) {
            if (callback != null) callback.done(false, "Family sync is not connected");
            return;
        }
        if (syncRunning.getAndSet(true)) {
            if (callback != null) callback.done(false, "A sync is already running");
            return;
        }
        io.execute(() -> {
            boolean pulled = false;
            try {
                String blobId = global.getString(KEY_BLOB, "");
                byte[] key = decodeKey(global.getString(KEY_SECRET, ""));
                JSONObject remote = new JSONObject(decrypt(getBlob(blobId), key));
                validateRemote(remote);

                JSONObject rJohn = remote.getJSONObject("john");
                JSONObject rAlexis = remote.getJSONObject("alexis");
                long rJohnMod = rJohn.optLong("modifiedAt", 0L);
                long rAlexisMod = rAlexis.optLong("modifiedAt", 0L);
                long lJohnMod = global.getLong(KEY_JOHN_MOD, 1L);
                long lAlexisMod = global.getLong(KEY_ALEXIS_MOD, 1L);
                boolean writeRemote = false;

                if (rJohnMod > lJohnMod) {
                    saveSafetySnapshot();
                    applyRemoteProfile("john", rJohn);
                    pulled = true;
                } else if (lJohnMod > rJohnMod) {
                    remote.put("john", profileBlock(john, lJohnMod));
                    writeRemote = true;
                }

                if (rAlexisMod > lAlexisMod) {
                    saveSafetySnapshot();
                    applyRemoteProfile("alexis", rAlexis);
                    pulled = true;
                } else if (lAlexisMod > rAlexisMod) {
                    remote.put("alexis", profileBlock(alexis, lAlexisMod));
                    writeRemote = true;
                }

                if (writeRemote) {
                    remote.put("updatedAt", System.currentTimeMillis());
                    putBlob(blobId, encrypt(remote.toString(), key));
                }
                long now = System.currentTimeMillis();
                global.edit().putLong(KEY_LAST_SYNC, now).remove(KEY_LAST_ERROR).apply();
                if (pulled) refreshMainActivity();
                complete(callback, true, pulled ? "Synced • new family changes downloaded" : "Synced • up to date");
            } catch (Exception e) {
                String message = cleanError(e);
                global.edit().putString(KEY_LAST_ERROR, message).apply();
                if (userInitiated) complete(callback, false, message);
                else complete(callback, false, null);
            } finally {
                syncRunning.set(false);
            }
        });
    }

    public void disconnect() {
        global.edit()
                .remove(KEY_BLOB)
                .remove(KEY_SECRET)
                .remove(KEY_LAST_SYNC)
                .remove(KEY_LAST_ERROR)
                .putBoolean(KEY_PROMPT_DISMISSED, true)
                .apply();
        main.removeCallbacks(debouncedSync);
        main.removeCallbacks(poller);
    }

    public boolean hasSafetySnapshot() {
        return !global.getString(KEY_SAFETY, "").isEmpty();
    }

    public void restoreSafetySnapshot(SyncCallback callback) {
        io.execute(() -> {
            try {
                String raw = global.getString(KEY_SAFETY, "");
                if (raw.isEmpty()) throw new Exception("No safety snapshot is available");
                JSONObject root = new JSONObject(raw);
                applyingRemote = true;
                restorePreferences(john, root.getJSONObject("john"));
                restorePreferences(alexis, root.getJSONObject("alexis"));
                applyingRemote = false;
                long now = System.currentTimeMillis();
                global.edit().putLong(KEY_JOHN_MOD, now).putLong(KEY_ALEXIS_MOD, now).apply();
                refreshMainActivity();
                complete(callback, true, "Safety snapshot restored");
                if (isConfigured()) syncNow(false, null);
            } catch (Exception e) {
                applyingRemote = false;
                complete(callback, false, cleanError(e));
            }
        });
    }

    private JSONObject buildLocalDocument(long johnMod, long alexisMod) throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", "BOLO_BOARD_FAMILY_SYNC");
        root.put("version", 2);
        root.put("updatedAt", System.currentTimeMillis());
        root.put("john", profileBlock(john, johnMod));
        root.put("alexis", profileBlock(alexis, alexisMod));
        return root;
    }

    private JSONObject profileBlock(SharedPreferences sp, long modifiedAt) throws Exception {
        JSONObject block = new JSONObject();
        block.put("modifiedAt", modifiedAt);
        block.put("data", preferencesToJson(sp));
        return block;
    }

    private void validateRemote(JSONObject root) throws Exception {
        if (!"BOLO_BOARD_FAMILY_SYNC".equals(root.optString("format"))) {
            throw new Exception("The shared data is not a BOLO Board family sync");
        }
        if (!root.has("john") || !root.has("alexis")) {
            throw new Exception("The shared BOLO Board is incomplete");
        }
    }

    private void applyRemoteProfile(String profile, JSONObject block) throws Exception {
        long modified = block.optLong("modifiedAt", System.currentTimeMillis());
        applyingRemote = true;
        restorePreferences(profile.equals("john") ? john : alexis, block.getJSONObject("data"));
        applyingRemote = false;
        global.edit().putLong(profile.equals("john") ? KEY_JOHN_MOD : KEY_ALEXIS_MOD, modified).apply();
    }

    public synchronized void saveSafetySnapshot() throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", "BOLO_BOARD_LOCAL_SAFETY");
        root.put("createdAt", System.currentTimeMillis());
        root.put("john", preferencesToJson(john));
        root.put("alexis", preferencesToJson(alexis));
        global.edit().putString(KEY_SAFETY, root.toString()).commit();
    }

    private JSONObject preferencesToJson(SharedPreferences source) throws Exception {
        JSONObject result = new JSONObject();
        for (Map.Entry<String, ?> entry : source.getAll().entrySet()) {
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

    private void restorePreferences(SharedPreferences target, JSONObject data) throws Exception {
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
                JSONArray array = (JSONArray) value;
                for (int i = 0; i < array.length(); i++) set.add(array.getString(i));
                editor.putStringSet(key, set);
            } else editor.putString(key, String.valueOf(value));
        }
        if (!editor.commit()) throw new Exception("Unable to save synchronized profile locally");
    }

    private String encrypt(String plaintext, byte[] key) throws Exception {
        byte[] iv = new byte[12];
        random.nextBytes(iv);
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
        if (!"BOLO_BOARD_SYNC_ENCRYPTED".equals(envelope.optString("format"))) {
            throw new Exception("Shared data is not encrypted BOLO Board data");
        }
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

    private String createBlob(String json) throws Exception {
        // jsonblob.com began returning HTTP 403 to Android POST requests. New family boards use
        // jsonblob.io instead. The board UUID is generated on-device so the pairing code never
        // depends on a response header, and the payload is already AES-GCM encrypted before upload.
        String uuid = UUID.randomUUID().toString();
        HttpURLConnection conn = open(FAMILY_API_BASE + "/" + uuid, "POST");
        writeJson(conn, json);
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            String detail = readError(conn);
            throw new Exception("Cloud create failed (" + code + ")" + detail);
        }
        consume(conn);
        return FAMILY_PREFIX + uuid;
    }

    private String getBlob(String id) throws Exception {
        if (id.startsWith(FAMILY_PREFIX)) {
            String uuid = id.substring(FAMILY_PREFIX.length());
            HttpURLConnection conn = open(FAMILY_API_BASE + "/" + uuid, "GET");
            int code = conn.getResponseCode();
            if (code == 404) throw new Exception("Shared board not found. Your local data was not changed");
            if (code < 200 || code >= 300) throw new Exception("Cloud read failed (" + code + ")" + readError(conn));
            return readAll(conn.getInputStream());
        }

        // BB1 pairing codes from v1.7.0 remain readable so an existing shared board is not stranded.
        HttpURLConnection conn = open(LEGACY_API_BASE + "/" + id, "GET");
        int code = conn.getResponseCode();
        if (code == 404) throw new Exception("Shared board not found. Your local data was not changed");
        if (code < 200 || code >= 300) throw new Exception("Cloud read failed (" + code + ")" + readError(conn));
        return readAll(conn.getInputStream());
    }

    private void putBlob(String id, String json) throws Exception {
        if (id.startsWith(FAMILY_PREFIX)) {
            String uuid = id.substring(FAMILY_PREFIX.length());
            HttpURLConnection conn = open(FAMILY_API_BASE + "/" + uuid, "POST");
            writeJson(conn, json);
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("Cloud update failed (" + code + ")" + readError(conn));
            consume(conn);
            return;
        }

        HttpURLConnection conn = open(LEGACY_API_BASE + "/" + id, "PUT");
        writeJson(conn, json);
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) throw new Exception("Cloud update failed (" + code + ")" + readError(conn));
        consume(conn);
    }

    private HttpURLConnection open(String address, String method) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("User-Agent", "BOLO-Board/1.7.1 Android FamilySync");
        conn.setInstanceFollowRedirects(true);
        conn.setUseCaches(false);
        return conn;
    }

    private void writeJson(HttpURLConnection conn, String json) throws Exception {
        conn.setDoOutput(true);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(bytes);
            out.flush();
        }
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
            if (body.length() > 80) body = body.substring(0, 80);
            return body.isEmpty() ? "" : " • " + body;
        } catch (Exception ignored) {
            return "";
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

    private String cleanError(Exception e) {
        String m = e.getMessage();
        if (m == null || m.trim().isEmpty()) m = e.getClass().getSimpleName();
        if (m.length() > 160) m = m.substring(0, 160);
        return m;
    }

    private void complete(SyncCallback callback, boolean ok, String message) {
        main.post(() -> {
            if (callback != null) callback.done(ok, message);
        });
    }

    private void refreshMainActivity() {
        main.post(() -> {
            Activity a = mainActivity.get();
            if (a != null && !a.isFinishing() && !a.isDestroyed()) a.recreate();
        });
    }

    public interface SyncCallback {
        void done(boolean ok, String message);
    }

    @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }

    @Override public void onActivityStarted(Activity activity) {
        startedActivities++;
        if (activity instanceof MainActivity) mainActivity = new WeakReference<>(activity);
        if (startedActivities == 1) {
            main.removeCallbacks(poller);
            if (isConfigured()) {
                syncNow(false, null);
                main.postDelayed(poller, POLL_MS);
            }
        }
    }

    @Override public void onActivityResumed(Activity activity) {
        if (activity instanceof MainActivity && !isConfigured() && !global.getBoolean(KEY_PROMPT_DISMISSED, false)) {
            main.postDelayed(() -> {
                Activity current = mainActivity.get();
                if (current != null && !current.isFinishing() && !isConfigured() && !global.getBoolean(KEY_PROMPT_DISMISSED, false)) {
                    Intent i = new Intent(current, FamilySyncActivity.class);
                    i.putExtra("first_run", true);
                    current.startActivity(i);
                }
            }, 900L);
        }
    }

    @Override public void onActivityPaused(Activity activity) { }

    @Override public void onActivityStopped(Activity activity) {
        startedActivities = Math.max(0, startedActivities - 1);
        if (startedActivities == 0) main.removeCallbacks(poller);
    }

    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }
    @Override public void onActivityDestroyed(Activity activity) { }
}
