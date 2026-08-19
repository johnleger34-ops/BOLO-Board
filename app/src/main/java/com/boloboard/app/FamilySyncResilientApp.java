package com.boloboard.app;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Adds a local, human-readable change inbox on top of FamilySyncFixedApp.
 * Only changes actually downloaded from the other phone create notices.
 * Local edits do not create a notice on the phone where they were made.
 */
public class FamilySyncResilientApp extends FamilySyncFixedApp {
    public static final String KEY_CHANGE_LOG = "family_change_log_v1";
    private static final int MAX_LOG = 100;
    private final Handler retryHandler = new Handler(Looper.getMainLooper());

    @Override
    public void syncNow(boolean userInitiated, SyncCallback callback) {
        Map<String, Object> johnBefore = snapshot(JOHN);
        Map<String, Object> alexisBefore = snapshot(ALEXIS);
        syncAttempt(userInitiated, callback, johnBefore, alexisBefore, 0);
    }

    private void syncAttempt(boolean userInitiated, SyncCallback callback,
                             Map<String, Object> johnBefore,
                             Map<String, Object> alexisBefore,
                             int attempt) {
        super.syncNow(userInitiated, (ok, message) -> {
            if (ok) {
                recordRemoteChanges("John", johnBefore, snapshot(JOHN));
                recordRemoteChanges("Alexis", alexisBefore, snapshot(ALEXIS));
                if (callback != null) callback.done(true, message);
                return;
            }

            // A short retry handles temporary 502/edge failures without forcing the family
            // to press Sync repeatedly. Local data remains untouched while the retry waits.
            if (attempt < 2 && shouldRetry(message)) {
                retryHandler.postDelayed(() ->
                        syncAttempt(userInitiated, callback, johnBefore, alexisBefore, attempt + 1),
                        1200L * (attempt + 1));
            } else if (callback != null) {
                callback.done(false, message);
            }
        });
    }

    @Override
    public void createFamilySync(SyncCallback callback) {
        createAttempt(callback, 0);
    }

    private void createAttempt(SyncCallback callback, int attempt) {
        super.createFamilySync((ok, message) -> {
            if (!ok && attempt < 2 && shouldRetry(message)) {
                retryHandler.postDelayed(() -> createAttempt(callback, attempt + 1), 1200L * (attempt + 1));
            } else if (callback != null) {
                callback.done(ok, message);
            }
        });
    }

    @Override
    public void joinFamilySync(String pairingCode, SyncCallback callback) {
        joinAttempt(pairingCode, callback, 0);
    }

    private void joinAttempt(String pairingCode, SyncCallback callback, int attempt) {
        Map<String, Object> johnBefore = snapshot(JOHN);
        Map<String, Object> alexisBefore = snapshot(ALEXIS);
        super.joinFamilySync(pairingCode, (ok, message) -> {
            if (ok) {
                recordRemoteChanges("John", johnBefore, snapshot(JOHN));
                recordRemoteChanges("Alexis", alexisBefore, snapshot(ALEXIS));
                if (callback != null) callback.done(true, message);
            } else if (attempt < 2 && shouldRetry(message)) {
                retryHandler.postDelayed(() -> joinAttempt(pairingCode, callback, attempt + 1), 1200L * (attempt + 1));
            } else if (callback != null) {
                callback.done(false, message);
            }
        });
    }

    private boolean shouldRetry(String message) {
        if (message == null) return false;
        String m = message.toLowerCase(Locale.US);
        return m.contains("502") || m.contains("503") || m.contains("504") ||
                m.contains("timeout") || m.contains("timed out") ||
                m.contains("connection") || m.contains("temporarily") ||
                m.contains("cloud read failed") || m.contains("cloud update failed") ||
                m.contains("cloud create failed");
    }

    private Map<String, Object> snapshot(String prefsName) {
        return new HashMap<>(getSharedPreferences(prefsName, MODE_PRIVATE).getAll());
    }

    private void recordRemoteChanges(String profile, Map<String, Object> before, Map<String, Object> after) {
        if (before.equals(after)) return;

        // Prefer calendar/payroll changes because those are the changes the family needs to see.
        for (String key : union(before, after).keySet()) {
            Object oldValue = before.get(key);
            Object newValue = after.get(key);
            if (same(oldValue, newValue)) continue;
            String message = describe(profile, key, oldValue, newValue);
            if (message != null) addChange(message, profile);
        }
    }

    private Map<String, Boolean> union(Map<String, Object> a, Map<String, Object> b) {
        Map<String, Boolean> keys = new HashMap<>();
        for (String k : a.keySet()) keys.put(k, true);
        for (String k : b.keySet()) keys.put(k, true);
        return keys;
    }

    private boolean same(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    private String describe(String profile, String key, Object oldValue, Object newValue) {
        if (key.startsWith("ot_hours_")) {
            String date = prettyDate(key.substring("ot_hours_".length()));
            double oldHours = number(oldValue);
            double newHours = number(newValue);
            if (newHours > oldHours) return hours(newHours - oldHours) + " of overtime added to " + profile + " profile for " + date;
            if (newHours <= 0) return "Overtime cleared from " + profile + " profile for " + date;
            return profile + " overtime changed to " + hours(newHours) + " for " + date;
        }
        if (key.startsWith("comp_hours_")) {
            String date = prettyDate(key.substring("comp_hours_".length()));
            return profile + " comp-requested hours changed to " + hours(number(newValue)) + " for " + date;
        }
        if (key.startsWith("court_hours_")) {
            String date = prettyDate(key.substring("court_hours_".length()));
            return profile + " court time changed to " + hours(number(newValue)) + " for " + date;
        }
        if (key.startsWith("entry_")) {
            String date = prettyDate(key.substring("entry_".length()));
            String value = newValue == null ? "Use Schedule" : String.valueOf(newValue);
            return profile + " schedule changed to " + value + " for " + date;
        }
        if (key.contains("vacation") || key.contains("sick") || key.contains("comp_balance")) {
            return profile + " leave balance was updated";
        }
        if (key.equals("hourly_rate") || key.equals("overtime_rate") || key.equals("court_rate") || key.equals("supplement")) {
            return profile + " pay settings were updated";
        }
        if (key.startsWith("stub_") || key.equals("saved_stub_ids")) {
            return null; // Pay-stub revisions happen automatically and would otherwise duplicate notices.
        }
        return null;
    }

    private double number(Object value) {
        if (value == null) return 0;
        try { return Double.parseDouble(String.valueOf(value)); }
        catch (Exception ignored) { return 0; }
    }

    private String hours(double value) {
        if (Math.rint(value) == value) return String.format(Locale.US, "%.0f hours", value);
        return String.format(Locale.US, "%.1f hours", value);
    }

    private String prettyDate(String raw) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(raw);
            return new SimpleDateFormat("MMM d, yyyy", Locale.US).format(d);
        } catch (Exception ignored) {
            return raw;
        }
    }

    private synchronized void addChange(String text, String profile) {
        try {
            SharedPreferences global = getSharedPreferences(GLOBAL, MODE_PRIVATE);
            JSONArray old = new JSONArray(global.getString(KEY_CHANGE_LOG, "[]"));
            JSONArray next = new JSONArray();

            JSONObject item = new JSONObject();
            item.put("id", System.currentTimeMillis() + "_" + Math.abs(text.hashCode()));
            item.put("time", System.currentTimeMillis());
            item.put("profile", profile);
            item.put("text", text);
            next.put(item);

            for (int i = 0; i < old.length() && next.length() < MAX_LOG; i++) next.put(old.getJSONObject(i));
            global.edit().putString(KEY_CHANGE_LOG, next.toString()).apply();
        } catch (Exception ignored) { }
    }
}
