package com.boloboard.app;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

/**
 * BOLO Board V2 shell.
 *
 * Keeps the existing MainActivity UI intact while adding:
 *  - a clearable in-app family change inbox
 *  - automatic creation/revision of completed pay-period stubs
 */
public class MainActivityV2 extends MainActivity {
    private static final String PREFS = "bolo_board";
    private static final String BELL_TAG = "bolo_change_bell";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        autoSaveCompletedPayPeriods();
        installChangeBell();
    }

    @Override
    protected void onResume() {
        super.onResume();
        autoSaveCompletedPayPeriods();
        installChangeBell();
        FamilySyncResilientApp app = (getApplication() instanceof FamilySyncResilientApp)
                ? (FamilySyncResilientApp) getApplication() : null;
        if (app != null && app.isConfigured()) app.syncNow(false, null);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) installChangeBell();
    }

    private void installChangeBell() {
        View root = findViewById(android.R.id.content);
        if (!(root instanceof FrameLayout)) return;
        FrameLayout frame = (FrameLayout) root;
        View old = frame.findViewWithTag(BELL_TAG);
        if (old != null) frame.removeView(old);

        int count = changeCount();
        TextView bell = new TextView(this);
        bell.setTag(BELL_TAG);
        bell.setText(count > 0 ? "NOTICES  " + count : "NOTICES");
        bell.setTextColor(Color.WHITE);
        bell.setTextSize(11);
        bell.setTypeface(Typeface.DEFAULT_BOLD);
        bell.setGravity(Gravity.CENTER);
        bell.setPadding(dp2(12), dp2(7), dp2(12), dp2(7));
        bell.setBackgroundColor(count > 0 ? Color.rgb(177, 43, 52) : Color.rgb(22, 38, 55));
        bell.setOnClickListener(v -> showChangeInbox());

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp2(92), dp2(36));
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.topMargin = dp2(12);
        lp.rightMargin = dp2(10);
        frame.addView(bell, lp);
    }

    private int dp2(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private JSONArray changeLog() {
        try {
            String raw = getSharedPreferences(PREFS + "_global", MODE_PRIVATE)
                    .getString(FamilySyncResilientApp.KEY_CHANGE_LOG, "[]");
            return new JSONArray(raw);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private int changeCount() {
        return changeLog().length();
    }

    private void showChangeInbox() {
        JSONArray log = changeLog();
        if (log.length() == 0) {
            new AlertDialog.Builder(this)
                    .setTitle("Family Changes")
                    .setMessage("No new family changes.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        ArrayList<String> rows = new ArrayList<>();
        for (int i = 0; i < log.length(); i++) {
            JSONObject item = log.optJSONObject(i);
            if (item != null) rows.add(item.optString("text", "Family profile updated"));
        }

        new AlertDialog.Builder(this)
                .setTitle("Family Changes • " + rows.size())
                .setItems(rows.toArray(new String[0]), (dialog, which) -> {
                    removeChange(which);
                    installChangeBell();
                    showChangeInbox();
                })
                .setMessage("Tap any notice to clear it.")
                .setNeutralButton("CLEAR ALL", (d, w) -> {
                    getSharedPreferences(PREFS + "_global", MODE_PRIVATE).edit()
                            .putString(FamilySyncResilientApp.KEY_CHANGE_LOG, "[]").apply();
                    installChangeBell();
                })
                .setNegativeButton("CLOSE", null)
                .show();
    }

    private void removeChange(int index) {
        try {
            JSONArray old = changeLog();
            JSONArray next = new JSONArray();
            for (int i = 0; i < old.length(); i++) if (i != index) next.put(old.get(i));
            getSharedPreferences(PREFS + "_global", MODE_PRIVATE).edit()
                    .putString(FamilySyncResilientApp.KEY_CHANGE_LOG, next.toString()).apply();
        } catch (Exception ignored) { }
    }

    /**
     * Every time BOLO Board opens/resumes, make sure every completed 14-day period
     * has a stored stub. Re-running this intentionally replaces the same stub ID,
     * so a later correction (for example synced OT) revises the stub instead of
     * creating a duplicate.
     */
    private void autoSaveCompletedPayPeriods() {
        try {
            Field prefsField = MainActivity.class.getDeclaredField("prefs");
            Field profileField = MainActivity.class.getDeclaredField("activeProfile");
            prefsField.setAccessible(true);
            profileField.setAccessible(true);

            Object oldPrefs = prefsField.get(this);
            Object oldProfile = profileField.get(this);

            for (String profile : new String[]{"John", "Alexis"}) {
                SharedPreferences pp = getSharedPreferences(PREFS + "_" + profile.toLowerCase(Locale.US), MODE_PRIVATE);
                prefsField.set(this, pp);
                profileField.set(this, profile);
                saveCompletedForActiveProfile(pp);
            }

            prefsField.set(this, oldPrefs);
            profileField.set(this, oldProfile);
        } catch (Exception ignored) {
            // Payroll UI remains fully usable even if a future MainActivity refactor changes reflection targets.
        }
    }

    private void saveCompletedForActiveProfile(SharedPreferences pp) throws Exception {
        Method currentStartMethod = privateMethod("currentPayPeriodStart");
        Calendar currentStart = (Calendar) currentStartMethod.invoke(this);

        long anchorMillis = pp.getLong("pay_period_anchor", pp.getLong("anchor", defaultPayAnchor()));
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(anchorMillis);
        zero(start);

        // Do not manufacture future stubs. Only periods strictly before the current one are closed.
        int guard = 0;
        while (start.before(currentStart) && guard++ < 300) {
            Calendar end = (Calendar) start.clone();
            end.add(Calendar.DAY_OF_MONTH, 13);
            createOrReviseStub(pp, start, end);
            start.add(Calendar.DAY_OF_MONTH, 14);
        }
    }

    private void createOrReviseStub(SharedPreferences pp, Calendar start, Calendar end) throws Exception {
        Method entryFor = privateMethod("entryFor", Calendar.class);
        Method holiday = privateMethod("federalHoliday", Calendar.class);
        Method supplementEligible = privateMethod("isSupplementEligible", Calendar.class);

        double worked = 0;
        double paidLeave = 0;
        double otRequested = 0;
        double compRequested = 0;
        double courtHours = 0;
        double holidayHours = 0;

        Calendar cursor = (Calendar) start.clone();
        java.text.SimpleDateFormat keyFormat = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
        for (int i = 0; i < 14; i++) {
            String entry = String.valueOf(entryFor.invoke(this, cursor));
            if (entry.equals("Day Shift") || entry.equals("Night Shift")) {
                worked += 12;
                Object h = holiday.invoke(this, cursor);
                if (h != null) holidayHours += entry.equals("Night Shift") ? 8 : 12;
            } else if (entry.equals("Vacation") || entry.equals("Sick") || entry.equals("Comp Taken")) {
                paidLeave += 12;
            }

            String dk = keyFormat.format(cursor.getTime());
            otRequested += prefDouble(pp, "ot_hours_" + dk, 0);
            compRequested += prefDouble(pp, "comp_hours_" + dk, 0);
            courtHours += prefDouble(pp, "court_hours_" + dk, 0);
            cursor.add(Calendar.DAY_OF_MONTH, 1);
        }

        double actualWorked = worked + otRequested + compRequested;
        double overtimePool = Math.max(0, actualWorked - 84);
        double overtimeHours = Math.min(otRequested, overtimePool);
        double regularWorked = Math.min(84, actualWorked);
        double hourlyRate = prefDouble(pp, "hourly_rate", 0);
        double overtimeRate = prefDouble(pp, "overtime_rate", 0);
        if (overtimeRate <= 0 && hourlyRate > 0) overtimeRate = hourlyRate * 1.5;
        double courtRate = prefDouble(pp, "court_rate", 0);
        double supplementRate = prefDouble(pp, "supplement", 0);

        double regularPay = money((regularWorked + paidLeave) * hourlyRate);
        double holidayPay = money(holidayHours * hourlyRate);
        double overtimePay = money(overtimeHours * overtimeRate);
        double courtPay = money(courtHours * courtRate);
        boolean eligible = (Boolean) supplementEligible.invoke(this, start);
        double supplementalPay = money(eligible ? supplementRate : 0);
        double gross = money(regularPay + holidayPay + overtimePay + courtPay + supplementalPay);

        Method create = privateMethod("createPayStubSnapshot",
                Calendar.class, Calendar.class,
                double.class, double.class, double.class, double.class, double.class,
                double.class, double.class, double.class, double.class);
        JSONObject snapshot = (JSONObject) create.invoke(this,
                (Calendar) start.clone(), (Calendar) end.clone(),
                actualWorked, paidLeave, overtimeHours, regularPay, holidayPay,
                overtimePay, courtPay, supplementalPay, gross);

        Method save = privateMethod("savePayStubSnapshot", JSONObject.class);
        save.invoke(this, snapshot);
    }

    private Method privateMethod(String name, Class<?>... args) throws Exception {
        Method m = MainActivity.class.getDeclaredMethod(name, args);
        m.setAccessible(true);
        return m;
    }

    private double prefDouble(SharedPreferences pp, String key, double fallback) {
        Object raw = pp.getAll().get(key);
        if (raw == null) return fallback;
        try { return Double.parseDouble(String.valueOf(raw)); }
        catch (Exception ignored) { return fallback; }
    }

    private double money(double value) {
        return new BigDecimal(Double.toString(value)).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private long defaultPayAnchor() {
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.JULY, 13, 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private void zero(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }
}
