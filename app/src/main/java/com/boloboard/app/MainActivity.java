package com.boloboard.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.json.JSONObject;
import org.json.JSONArray;

public class MainActivity extends Activity {

    private static final String PREFS = "bolo_board";
    private static final String[] PATTERNS = {"Alpha", "Bravo", "Charlie", "Delta"};
    private static final String[] FEDERAL_STATUSES = {"Single or Married Filing Separately", "Married Filing Jointly", "Head of Household"};
    private static final String[] LOUISIANA_STATUSES = {"Single / Married Filing Separately", "Married Filing Jointly", "Head of Household"};
    private static final String[] RANKS = {"Officer", "Senior Officer", "Corporal", "Sergeant", "Lieutenant", "Captain", "Major", "Chief"};
    private static final String[] ENTRY_TYPES = {
            "Use Schedule", "Day Shift", "Night Shift", "Day Off",
            "Extra Shift - Regular", "Extra Shift - OT Requested",
            "Extra Shift - Comp Requested", "Vacation", "Sick",
            "Comp Taken", "Court", "Custom Event"
    };

    private final Calendar displayedMonth = Calendar.getInstance();
    private SharedPreferences prefs;
    private SharedPreferences globalPrefs;
    private String activeProfile;
    private LinearLayout content;
    private TextView monthTitle;
    private GridLayout calendarGrid;
    private LinearLayout bottomNav;

    private final SimpleDateFormat keyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat displayDate = new SimpleDateFormat("MMM d, yyyy", Locale.US);

    private final int navy = Color.rgb(3, 12, 23);
    private final int panel = Color.rgb(13, 25, 39);
    private final int panel2 = Color.rgb(22, 38, 55);
    private final int gold = Color.rgb(246, 189, 31);
    private final int blue = Color.rgb(31, 124, 255);
    private final int red = Color.rgb(235, 51, 63);
    private final int silver = Color.rgb(221, 227, 234);
    private final int muted = Color.rgb(155, 169, 184);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(navy);
        getWindow().setNavigationBarColor(navy);
        globalPrefs = getSharedPreferences(PREFS + "_global", MODE_PRIVATE);
        activeProfile = globalPrefs.getString("active_profile", "John");
        loadProfilePrefs();
        seedProfileNames();
        applyAnnualAccrualIfNeeded();
        showCalendarScreen();
    }


    private void loadProfilePrefs() {
        prefs = getSharedPreferences(PREFS + "_" + activeProfile.toLowerCase(Locale.US), MODE_PRIVATE);
    }

    private void seedProfileNames() {
        SharedPreferences john = getSharedPreferences(PREFS + "_john", MODE_PRIVATE);
        SharedPreferences alexis = getSharedPreferences(PREFS + "_alexis", MODE_PRIVATE);
        if (!john.contains("officer_name")) john.edit().putString("officer_name", "J. Leger").putString("rank", "Lieutenant").apply();
        if (!alexis.contains("officer_name")) alexis.edit().putString("officer_name", "A. Leger").putString("rank", "Corporal").apply();
        // Preserve editable rates, but migrate the rounded Alexis rates used by older builds
        // to the exact values proven by the official 84-hour + 12-hour OT pay stub.
        String alexisHourly = alexis.getString("hourly_rate", "");
        String alexisOt = alexis.getString("overtime_rate", "");
        SharedPreferences.Editor alexisRates = alexis.edit();
        boolean updateAlexisRates = false;
        if (alexisHourly.isEmpty() || alexisHourly.equals("20.26") || alexisHourly.equals("20.2600")) {
            alexisRates.putString("hourly_rate", "20.260952380952");
            updateAlexisRates = true;
        }
        if (alexisOt.isEmpty() || alexisOt.equals("30.39") || alexisOt.equals("30.3900")) {
            alexisRates.putString("overtime_rate", "30.391666666667");
            updateAlexisRates = true;
        }
        if (updateAlexisRates) alexisRates.apply();
    }

    private void switchProfile(String profile) {
        activeProfile = profile;
        globalPrefs.edit().putString("active_profile", profile).apply();
        loadProfilePrefs();
        applyAnnualAccrualIfNeeded();
        showCalendarScreen();
    }

    private LinearLayout profileSwitcher() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setGravity(Gravity.CENTER);
        String[] profiles = {"John", "Alexis"};
        for (String profile : profiles) {
            SharedPreferences pp = getSharedPreferences(PREFS + "_" + profile.toLowerCase(Locale.US), MODE_PRIVATE);
            String display = pp.getString("officer_name", profile.equals("John") ? "J. Leger" : "A. Leger");
            String rank = pp.getString("rank", profile.equals("John") ? "Lieutenant" : "Corporal");
            TextView chip = new TextView(this);
            chip.setText(display.toUpperCase(Locale.US) + "  ▼\n" + rankInsignia(rank) + "  " + rank.toUpperCase(Locale.US));
            chip.setGravity(Gravity.CENTER);
            chip.setTextSize(12);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            boolean selected = profile.equals(activeProfile);
            chip.setTextColor(selected ? navy : silver);
            chip.setBackground(rounded(selected ? gold : panel2, dp(18), selected ? gold : Color.rgb(65,84,104), 1));
            chip.setOnClickListener(v -> {
                if (!profile.equals(activeProfile)) switchProfile(profile);
                else showRankPicker();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(54), 1);
            lp.setMargins(dp(5), 0, dp(5), 0);
            row.addView(chip, lp);
        }
        return row;
    }

    private String rankInsignia(String rank) {
        if (rank.equals("Corporal")) return "⌃⌃";
        if (rank.equals("Sergeant")) return "≪≪";
        if (rank.equals("Lieutenant")) return "▮ ▮";
        if (rank.equals("Captain")) return "▮▮";
        if (rank.equals("Major")) return "★";
        if (rank.equals("Chief")) return "★★★★";
        if (rank.equals("Senior Officer")) return "★";
        return "●";
    }

    private void showRankPicker() {
        String current = prefs.getString("rank", activeProfile.equals("John") ? "Lieutenant" : "Corporal");
        int checked = 0;
        for (int i=0;i<RANKS.length;i++) if (RANKS[i].equals(current)) checked=i;
        new AlertDialog.Builder(this).setTitle("Select rank for " + prefs.getString("officer_name", activeProfile))
                .setSingleChoiceItems(RANKS, checked, (d, which) -> {
                    prefs.edit().putString("rank", RANKS[which]).apply();
                    d.dismiss(); showCalendarScreen();
                }).setNegativeButton("Cancel", null).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable rounded(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    private void baseScreen(String title) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        Bitmap backgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.police_explorer_banner);
        BitmapDrawable background = new BitmapDrawable(getResources(), backgroundBitmap);
        background.setGravity(Gravity.CENTER);
        background.setAlpha(42);
        root.setBackground(background);

        PoliceHeaderView header = new PoliceHeaderView(this);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(58)));
        root.addView(profileSwitcher(), new LinearLayout.LayoutParams(-1, dp(50)));

        if (title != null && !title.isEmpty() && !title.equals("CALENDAR")) {
            TextView sectionTitle = new TextView(this);
            sectionTitle.setText(title);
            sectionTitle.setTextColor(Color.WHITE);
            sectionTitle.setTextSize(22);
            sectionTitle.setTypeface(Typeface.DEFAULT_BOLD);
            sectionTitle.setGravity(Gravity.CENTER);
            sectionTitle.setPadding(dp(12), dp(12), dp(12), dp(12));
            sectionTitle.setBackgroundColor(panel);
            root.addView(sectionTitle);
        }

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(6), dp(4), dp(6), dp(6));
        content.setBackgroundColor(Color.argb(205, 3, 12, 23));

        if (title != null && title.equals("CALENDAR")) {
            root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        } else {
            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            scroll.addView(content);
            root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        }

        bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setGravity(Gravity.CENTER);
        bottomNav.setBackground(rounded(Color.rgb(7, 18, 31), 0, Color.rgb(45, 62, 80), 1));

        addNavButton("▣\nCALENDAR", "Calendar");
        addNavButton("$\nPAYROLL", "Payroll");
        addNavButton("♟\nLEAVE", "Leave");
        addNavButton("⚙\nSETTINGS", "Settings");

        root.addView(bottomNav, new LinearLayout.LayoutParams(-1, -2));

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottom = insets.getSystemWindowInsetBottom();
            int left = insets.getSystemWindowInsetLeft();
            int right = insets.getSystemWindowInsetRight();
            bottomNav.setPadding(dp(6) + left, dp(7), dp(6) + right, dp(7) + bottom);
            return insets;
        });

        setContentView(root);
        root.requestApplyInsets();
    }

    private void addNavButton(String label, String destination) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(silver);
        b.setTextSize(10);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(dp(3), dp(3), dp(3), dp(3));
        b.setBackground(rounded(panel2, dp(8), Color.rgb(55, 75, 96), 1));
        b.setOnClickListener(v -> {
            if (destination.equals("Calendar")) showCalendarScreen();
            else if (destination.equals("Payroll")) showPayrollScreen();
            else if (destination.equals("Leave")) showLeaveScreen();
            else showSettingsScreen();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1);
        lp.setMargins(dp(3), 0, dp(3), 0);
        bottomNav.addView(b, lp);
    }

    private TextView section(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(19);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(10), dp(16), dp(10), dp(10));
        return t;
    }

    private EditText input(String label, String value, boolean numeric) {
        EditText e = new EditText(this);
        e.setHint(label);
        e.setHintTextColor(muted);
        e.setTextColor(Color.WHITE);
        e.setText(value);
        e.setSingleLine(true);
        if (numeric) {
            e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        }
        e.setPadding(dp(12), dp(11), dp(12), dp(11));
        e.setBackground(rounded(panel2, dp(14), Color.rgb(76, 101, 128), 2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(5), 0, dp(5));
        e.setLayoutParams(lp);
        return e;
    }

    private TextView action(String text, View.OnClickListener listener) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), dp(15), dp(12), dp(15));
        b.setBackground(rounded(panel2, dp(14), Color.rgb(79, 105, 132), 2));
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, dp(6));
        b.setLayoutParams(lp);
        return b;
    }

    private LinearLayout labeledField(String label, EditText field) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        TextView lab = new TextView(this);
        lab.setText(label);
        lab.setTextColor(muted);
        lab.setTextSize(12);
        lab.setTypeface(Typeface.DEFAULT_BOLD);
        lab.setPadding(dp(3), dp(8), dp(3), 0);
        wrap.addView(lab);
        wrap.addView(field);
        return wrap;
    }

    private void showCalendarScreen() {
        baseScreen("CALENDAR");

        LinearLayout scheduleRow = new LinearLayout(this);
        scheduleRow.setOrientation(LinearLayout.HORIZONTAL);
        scheduleRow.setGravity(Gravity.CENTER_VERTICAL);
        scheduleRow.setPadding(dp(6), dp(4), dp(6), dp(4));
        scheduleRow.setBackground(rounded(panel, dp(10), Color.rgb(76, 94, 113), 1));

        LinearLayout patternBox = new LinearLayout(this);
        patternBox.setOrientation(LinearLayout.VERTICAL);
        TextView pSmall = new TextView(this);
        pSmall.setText("YOUR SCHEDULE");
        pSmall.setTextColor(muted);
        pSmall.setTextSize(10);
        TextView pBig = new TextView(this);
        pBig.setText(prefs.getString("pattern", "Alpha").toUpperCase(Locale.US) + "  ▼");
        pBig.setTextColor(Color.WHITE);
        pBig.setTextSize(15);
        pBig.setTypeface(Typeface.DEFAULT_BOLD);
        patternBox.addView(pSmall);
        patternBox.addView(pBig);
        patternBox.setOnClickListener(v -> showSettingsScreen());

        TextView prev = arrowButton("◀", v -> {
            displayedMonth.add(Calendar.MONTH, -1);
            renderCalendar();
        });
        TextView next = arrowButton("▶", v -> {
            displayedMonth.add(Calendar.MONTH, 1);
            renderCalendar();
        });

        monthTitle = new TextView(this);
        monthTitle.setTextSize(18);
        monthTitle.setTypeface(Typeface.DEFAULT_BOLD);
        monthTitle.setTextColor(Color.WHITE);
        monthTitle.setGravity(Gravity.CENTER);

        scheduleRow.addView(patternBox, new LinearLayout.LayoutParams(dp(92), -2));
        scheduleRow.addView(prev, new LinearLayout.LayoutParams(dp(38), dp(38)));
        scheduleRow.addView(monthTitle, new LinearLayout.LayoutParams(0, -2, 1));
        scheduleRow.addView(next, new LinearLayout.LayoutParams(dp(38), dp(38)));
        content.addView(scheduleRow);

        calendarGrid = new GridLayout(this);
        calendarGrid.setColumnCount(7);
        calendarGrid.setBackgroundColor(Color.rgb(39, 55, 70));
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(-1, -2);
        gridLp.setMargins(0, dp(4), 0, dp(4));
        calendarGrid.setLayoutParams(gridLp);
        content.addView(calendarGrid);

        LinearLayout calendarActions = new LinearLayout(this);
        calendarActions.setOrientation(LinearLayout.HORIZONTAL);
        TextView share = action("SHARE MONTH", v -> shareMonth());
        share.setTextSize(11);
        share.setPadding(dp(4), dp(7), dp(4), dp(7));
        share.setBackground(rounded(Color.rgb(15, 31, 49), dp(8), blue, 1));
        TextView todayButton = action("JUMP TO TODAY", v -> {
            Calendar today = Calendar.getInstance();
            displayedMonth.setTimeInMillis(today.getTimeInMillis());
            displayedMonth.set(Calendar.DAY_OF_MONTH, 1);
            renderCalendar();
            Toast.makeText(this, "Showing today: " + displayDate.format(today.getTime()), Toast.LENGTH_SHORT).show();
        });
        todayButton.setTextSize(11);
        todayButton.setPadding(dp(4), dp(7), dp(4), dp(7));
        LinearLayout.LayoutParams actionLeft = new LinearLayout.LayoutParams(0, dp(38), 1);
        actionLeft.setMargins(0, 0, dp(3), 0);
        LinearLayout.LayoutParams actionRight = new LinearLayout.LayoutParams(0, dp(38), 1);
        actionRight.setMargins(dp(3), 0, 0, 0);
        calendarActions.addView(share, actionLeft);
        calendarActions.addView(todayButton, actionRight);
        content.addView(calendarActions);

        renderCalendar();
    }

    private TextView arrowButton(String text, View.OnClickListener listener) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(21);
        b.setGravity(Gravity.CENTER);
        b.setBackground(rounded(panel2, dp(8), Color.rgb(92, 106, 122), 1));
        b.setOnClickListener(listener);
        return b;
    }

    private void renderCalendar() {
        calendarGrid.removeAllViews();
        monthTitle.setText(new SimpleDateFormat("MMMM yyyy", Locale.US).format(displayedMonth.getTime()));

        String[] days = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};
        for (String d : days) {
            TextView h = new TextView(this);
            h.setText(d);
            h.setGravity(Gravity.CENTER);
            h.setTextColor(Color.WHITE);
            h.setTextSize(9);
            h.setTypeface(Typeface.DEFAULT_BOLD);
            h.setBackgroundColor(Color.argb(235, 13, 25, 39));
            h.setPadding(0, dp(3), 0, dp(3));
            h.setLayoutParams(equalGridParams(dp(23)));
            calendarGrid.addView(h);
        }

        Calendar firstVisible = (Calendar) displayedMonth.clone();
        firstVisible.set(Calendar.DAY_OF_MONTH, 1);
        int mondayOffset = (firstVisible.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        firstVisible.add(Calendar.DAY_OF_MONTH, -mondayOffset);

        Calendar today = Calendar.getInstance();
        int shownMonth = displayedMonth.get(Calendar.MONTH);
        int shownYear = displayedMonth.get(Calendar.YEAR);
        int squareSize = calendarCellSizePx();

        for (int i = 0; i < 35; i++) {
            Calendar date = (Calendar) firstVisible.clone();
            date.add(Calendar.DAY_OF_MONTH, i);
            String status = entryFor(date);
            String holiday = federalHoliday(date);
            boolean adjacentMonth = date.get(Calendar.MONTH) != shownMonth || date.get(Calendar.YEAR) != shownYear;
            boolean isToday = date.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                    && date.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);

            FrameLayout cell = new FrameLayout(this);
            int fill = adjacentMonth ? Color.argb(180, 5, 13, 22) : Color.argb(225, 7, 17, 27);
            cell.setBackground(rounded(isToday ? Color.argb(235, 22, 48, 72) : fill,
                    0, isToday ? gold : Color.rgb(56, 73, 90), isToday ? 3 : 1));
            cell.setPadding(dp(1), dp(1), dp(1), dp(1));
            cell.setLayoutParams(equalGridParamsPx(squareSize));

            TextView number = new TextView(this);
            number.setText(String.valueOf(date.get(Calendar.DAY_OF_MONTH)));
            number.setTextColor(adjacentMonth ? muted : Color.WHITE);
            number.setTextSize(10);
            number.setTypeface(Typeface.DEFAULT_BOLD);
            number.setPadding(dp(2), 0, 0, 0);
            cell.addView(number, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT));

            ShiftIconView icon = new ShiftIconView(this, status);
            FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(-1, -1);
            iconLp.setMargins(dp(1), dp(5), dp(1), dp(11));
            cell.addView(icon, iconLp);

            String bottomText = extrasLabel(date);
            if (holiday != null) bottomText = (bottomText.isEmpty() ? "" : bottomText + " • ") + holiday;
            if (!bottomText.isEmpty()) {
                TextView detail = new TextView(this);
                detail.setText(bottomText);
                detail.setTextColor(gold);
                detail.setTextSize(5.2f);
                detail.setTypeface(Typeface.DEFAULT_BOLD);
                detail.setGravity(Gravity.CENTER);
                detail.setSingleLine(true);
                FrameLayout.LayoutParams detailLp = new FrameLayout.LayoutParams(-1, dp(10), Gravity.BOTTOM);
                detailLp.setMargins(dp(1), 0, dp(1), 0);
                cell.addView(detail, detailLp);
            }

            final Calendar selectedDate = (Calendar) date.clone();
            cell.setOnClickListener(v -> editDate(selectedDate));
            calendarGrid.addView(cell);
        }
    }

    private int calendarCellSizePx() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int available = screenWidth - dp(12) - 14;
        return Math.max(dp(42), available / 7);
    }

    private GridLayout.LayoutParams equalGridParamsPx(int heightPx) {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = heightPx;
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(1, 1, 1, 1);
        return lp;
    }

    private GridLayout.LayoutParams equalGridParams(int heightDp) {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = dp(heightDp);
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(1, 1, 1, 1);
        return lp;
    }

    private void editDate(Calendar date) {
        String dateKey = keyFormat.format(date.getTime());
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(6), dp(18), 0);

        TextView baseLabel = new TextView(this);
        baseLabel.setText("BASE SCHEDULE FOR THIS DATE");
        baseLabel.setTextColor(muted);
        baseLabel.setTypeface(Typeface.DEFAULT_BOLD);
        form.addView(baseLabel);

        Spinner baseSpinner = new Spinner(this);
        String[] baseChoices = {"Use Schedule", "Day Shift", "Night Shift", "Day Off", "Vacation", "Sick", "Comp Taken", "Custom Event"};
        baseSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, baseChoices));
        String currentBase = prefs.getString("entry_" + dateKey, "Use Schedule");
        for (int i=0;i<baseChoices.length;i++) if (baseChoices[i].equals(currentBase)) baseSpinner.setSelection(i);
        form.addView(baseSpinner);

        EditText otHours = input("Overtime hours added", formatRate(getDouble("ot_hours_" + dateKey, 0)), true);
        EditText compHours = input("Comp-requested hours added", formatRate(getDouble("comp_hours_" + dateKey, 0)), true);
        EditText courtHours = input("Court hours added", formatRate(getDouble("court_hours_" + dateKey, 0)), true);
        form.addView(labeledField("OVERTIME HOURS", otHours));
        form.addView(labeledField("COMP-REQUESTED HOURS", compHours));
        form.addView(labeledField("COURT HOURS", courtHours));

        new AlertDialog.Builder(this)
                .setTitle(displayDate.format(date.getTime()))
                .setView(form)
                .setPositiveButton("Save", (dialog, which) -> {
                    String oldBase = prefs.getString("entry_" + dateKey, "Use Schedule");
                    String selectedBase = baseChoices[baseSpinner.getSelectedItemPosition()];
                    saveMultiDateEntry(dateKey, oldBase, selectedBase,
                            Math.max(0, parse(otHours.getText().toString())),
                            Math.max(0, parse(compHours.getText().toString())),
                            Math.max(0, parse(courtHours.getText().toString())));
                })
                .setNeutralButton("Clear extras", (dialog, which) -> {
                    prefs.edit().remove("ot_hours_" + dateKey).remove("comp_hours_" + dateKey).remove("court_hours_" + dateKey).apply();
                    renderCalendar();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveMultiDateEntry(String dateKey, String oldBase, String selectedBase, double ot, double comp, double court) {
        SharedPreferences.Editor ed = prefs.edit();
        if (selectedBase.equals("Use Schedule")) ed.remove("entry_" + dateKey);
        else ed.putString("entry_" + dateKey, selectedBase);
        putOrRemove(ed, "ot_hours_" + dateKey, ot);
        putOrRemove(ed, "comp_hours_" + dateKey, comp);
        putOrRemove(ed, "court_hours_" + dateKey, court);
        ed.apply();
        adjustLeaveForEntry(oldBase, selectedBase);
        renderCalendar();
    }

    private void putOrRemove(SharedPreferences.Editor ed, String key, double value) {
        if (value > 0) ed.putString(key, String.valueOf(value)); else ed.remove(key);
    }

    private void adjustLeaveForEntry(String oldValue, String newValue) {
        if (!oldValue.equals(newValue)) {
            if (oldValue.equals("Vacation")) addBalance("vacation_balance", 12);
            if (oldValue.equals("Sick")) addBalance("sick_balance", 12);
            if (oldValue.equals("Comp Taken")) addBalance("comp_balance", 12);
            if (newValue.equals("Vacation")) addBalance("vacation_balance", -12);
            if (newValue.equals("Sick")) addBalance("sick_balance", -12);
            if (newValue.equals("Comp Taken")) addBalance("comp_balance", -12);
        }
    }

    private String extrasLabel(Calendar date) {
        String k = keyFormat.format(date.getTime());
        ArrayList<String> parts = new ArrayList<>();
        double ot = getDouble("ot_hours_" + k, 0);
        double comp = getDouble("comp_hours_" + k, 0);
        double court = getDouble("court_hours_" + k, 0);
        if (ot > 0) parts.add("OT " + compactHours(ot));
        if (comp > 0) parts.add("COMP " + compactHours(comp));
        if (court > 0) parts.add("COURT " + compactHours(court));
        if (isPayday(date)) parts.add("Payday");
        return android.text.TextUtils.join(" • ", parts);
    }

    private String compactHours(double h) {
        return (Math.rint(h)==h ? String.format(Locale.US,"%.0f",h) : String.format(Locale.US,"%.1f",h)) + "h";
    }

    private String entryFor(Calendar date) {
        String override = prefs.getString("entry_" + keyFormat.format(date.getTime()), null);
        return override != null ? override : scheduledFor(date);
    }

    private String scheduledFor(Calendar date) {
        String pattern = prefs.getString("pattern", "Alpha");
        long anchor = prefs.getLong("anchor", defaultAnchor());
        long days = daysBetween(anchor, date.getTimeInMillis());
        int cycleDay = floorMod((int) days, 14);
        boolean alphaWorks = cycleDay == 0 || cycleDay == 1 || cycleDay == 4 || cycleDay == 5 || cycleDay == 6 ||
                cycleDay == 9 || cycleDay == 10;
        boolean works = (pattern.equals("Alpha") || pattern.equals("Charlie")) ? alphaWorks : !alphaWorks;
        if (!works) return "Day Off";
        if (pattern.equals("Charlie") || pattern.equals("Delta")) {
            int payPeriod = Math.floorDiv((int) days, 14);
            return floorMod(payPeriod, 2) == 0 ? "Night Shift" : "Day Shift";
        }
        return "Day Shift";
    }

    private long defaultAnchor() {
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.JULY, 13, 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private long daysBetween(long start, long end) {
        Calendar a = Calendar.getInstance();
        a.setTimeInMillis(start);
        zeroTime(a);
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(end);
        zeroTime(b);
        return (b.getTimeInMillis() - a.getTimeInMillis()) / 86400000L;
    }

    private void zeroTime(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }

    private int floorMod(int a, int b) {
        int r = a % b;
        return r < 0 ? r + b : r;
    }

    private void shareMonth() {
        Calendar first = (Calendar) displayedMonth.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        int total = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        StringBuilder sb = new StringBuilder();
        sb.append(prefs.getString("officer_name", "BOLO Board"))
                .append(" — ")
                .append(new SimpleDateFormat("MMMM yyyy", Locale.US).format(first.getTime()))
                .append("\n\n");
        for (int d = 1; d <= total; d++) {
            first.set(Calendar.DAY_OF_MONTH, d);
            sb.append(new SimpleDateFormat("EEE MMM d", Locale.US).format(first.getTime()))
                    .append(": ")
                    .append(entryFor(first))
                    .append("\n");
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "BOLO Board Monthly Schedule");
        send.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(send, "Share monthly schedule"));
    }

    private void showPayrollScreen() {
        baseScreen("PAYROLL");

        content.addView(section("PAY CONFIGURATION"));

        EditText hourly = input("Hourly rate", formatPayRate(getDouble("hourly_rate", 0)), true);
        EditText overtime = input("Overtime hourly rate", formatPayRate(getDouble("overtime_rate", 0)), true);
        EditText court = input("Court pay per subpoena", formatRate(getDouble("court_rate", 50)), true);
        EditText supplement = input("Supplemental pay per eligible check", formatRate(getDouble("supplement", 0)), true);

        content.addView(labeledField("HOURLY RATE", hourly));
        content.addView(labeledField("OVERTIME RATE", overtime));
        content.addView(labeledField("COURT PAY PER SUBPOENA", court));
        content.addView(labeledField("SUPPLEMENTAL PAY", supplement));

        TextView saveRates = action("SAVE PAY RATES & RECALCULATE", v -> {
            prefs.edit()
                    .putString("hourly_rate", hourly.getText().toString())
                    .putString("overtime_rate", overtime.getText().toString())
                    .putString("court_rate", court.getText().toString())
                    .putString("supplement", supplement.getText().toString())
                    .apply();
            Toast.makeText(this, "Pay rates saved", Toast.LENGTH_SHORT).show();
            showPayrollScreen();
        });
        saveRates.setBackground(rounded(Color.rgb(17, 71, 122), dp(9), blue, 2));
        content.addView(saveRates);

        Calendar start = currentPayPeriodStart();
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_MONTH, 13);

        content.addView(section("CURRENT PAY PERIOD\n" + displayDate.format(start.getTime()) + " – " + displayDate.format(end.getTime())));

        double worked = 0;
        double paidLeave = 0;
        double otRequested = 0;
        double compRequested = 0;
        double courtHours = 0;
        double holidayHours = 0;

        Calendar cursor = (Calendar) start.clone();
        for (int i = 0; i < 14; i++) {
            String e = entryFor(cursor);
            if (e.equals("Day Shift") || e.equals("Night Shift")) {
                worked += 12;
                if (federalHoliday(cursor) != null) {
                    // Holiday premium is a separate regular-rate earning. It does not add worked hours.
                    holidayHours += e.equals("Night Shift") ? 8 : 12;
                }
            } else if (e.equals("Vacation") || e.equals("Sick") || e.equals("Comp Taken")) paidLeave += 12;
            String dk = keyFormat.format(cursor.getTime());
            otRequested += getDouble("ot_hours_" + dk, 0);
            compRequested += getDouble("comp_hours_" + dk, 0);
            courtHours += getDouble("court_hours_" + dk, 0);
            cursor.add(Calendar.DAY_OF_MONTH, 1);
        }

        double actualWorked = worked + otRequested + compRequested;
        double overtimePool = Math.max(0, actualWorked - 84);
        double overtimeHours = Math.min(otRequested, overtimePool);
        double compOtHours = Math.min(compRequested, Math.max(0, overtimePool - overtimeHours));
        double regularWorked = Math.min(84, actualWorked);
        double hourlyRate = getDouble("hourly_rate", 0);
        double overtimeRate = getDouble("overtime_rate", 0);
        if (overtimeRate <= 0 && hourlyRate > 0) overtimeRate = hourlyRate * 1.5;
        double courtRate = getDouble("court_rate", 0);
        double supplementRate = getDouble("supplement", 0);

        // Calculate each payroll earning with decimal arithmetic and round each pay code
        // to cents before totaling, matching the agency pay-stub method.
        double regularPay = moneyValue(bd(regularWorked + paidLeave).multiply(bd(hourlyRate)));
        double holidayPay = moneyValue(bd(holidayHours).multiply(bd(hourlyRate)));
        double overtimePay = moneyValue(bd(overtimeHours).multiply(bd(overtimeRate)));
        double courtPay = moneyValue(bd(courtHours).multiply(bd(courtRate)));
        double supplementalPay = moneyValue(bd(isSupplementEligible(start) ? supplementRate : 0));
        double gross = moneyValue(bd(regularPay).add(bd(holidayPay)).add(bd(overtimePay)).add(bd(courtPay)).add(bd(supplementalPay)));
        final double compEarned = compOtHours * 1.5;

        Calendar paydayForPeriod = paydayForPeriod(start);
        boolean healthApplies = paycheckOccurrenceInMonth(paydayForPeriod) <= 2;
        double health = healthApplies ? getDouble("health_deduction", 0) : 0;
        double taxableGross = Math.max(0, gross - health);
        double retirementBase = regularPay + holidayPay + supplementalPay;
        double retirement = moneyValue(bd(retirementBase).multiply(bd(getDouble("retirement_percent", 0))).divide(new BigDecimal("100"), 12, RoundingMode.HALF_UP));
        double federalTax = moneyValue(bd(estimateFederalWithholding(gross, getString("federal_status", FEDERAL_STATUSES[0]),
                (int)getDouble("federal_children", 0), (int)getDouble("federal_other_dependents", 0))).add(bd(getDouble("federal_extra", 0))));
        double stateTax = moneyValue(bd(estimateLouisianaWithholding(gross, getString("state_status", LOUISIANA_STATUSES[0]),
                (int)getDouble("state_dependents", 0))).add(bd(getDouble("state_extra", 0))));
        double medicare = moneyValue(bd(gross).multiply(new BigDecimal("0.0145")));
        double other = moneyValue(bd(getDouble("other_deductions", 0)));
        double net = moneyValue(bd(gross).subtract(bd(health)).subtract(bd(retirement)).subtract(bd(federalTax))
                .subtract(bd(stateTax)).subtract(bd(medicare)).subtract(bd(other)));

        content.addView(summaryCard("ACTUAL WORKED", actualWorked + " hrs", "⚡"));
        content.addView(summaryCard("PAID LEAVE", paidLeave + " hrs", "☂"));
        content.addView(summaryCard("REGULAR PAY", money(regularPay), "$"));
        content.addView(summaryCard("OVERTIME", overtimeHours + " hrs  •  " + money(overtimePay), "⏱"));
        content.addView(summaryCard("HOLIDAY PREMIUM", holidayHours + " hrs  •  " + money(holidayPay), "★"));
        content.addView(summaryCard("COMP EARNED", compEarned + " hrs", "★"));
        content.addView(summaryCard("COURT", courtHours + " hrs  •  " + money(courtPay), "⚖"));
        content.addView(summaryCard("SUPPLEMENTAL PAY", money(supplementalPay), "✦"));
        content.addView(summaryCard("ESTIMATED GROSS CHECK", money(gross), "$"));
        content.addView(summaryCard("PRETAX INSURANCE & BENEFITS", healthApplies ? money(health) : "$0.00 • third check", "✚"));
        content.addView(summaryCard("RETIREMENT", money(retirement), "★"));
        content.addView(summaryCard("FEDERAL WITHHOLDING", money(federalTax), "F"));
        content.addView(summaryCard("LOUISIANA WITHHOLDING", money(stateTax), "LA"));
        content.addView(summaryCard("MEDICARE", money(medicare), "MC"));
        content.addView(summaryCard("ESTIMATED NET CHECK", money(net), "$"));

        if (compEarned > 0) {
            content.addView(action("ADD EARNED COMP TO BANK", v -> {
                addBalance("comp_balance", compEarned);
                Toast.makeText(this, "Comp bank updated", Toast.LENGTH_SHORT).show();
                showPayrollScreen();
            }));
        }

        final double actualWorkedFinal = actualWorked;
        final double paidLeaveFinal = paidLeave;
        final double overtimeHoursFinal = overtimeHours;
        final double regularPayFinal = regularPay;
        final double holidayPayFinal = holidayPay;
        final double overtimePayFinal = overtimePay;
        final double courtPayFinal = courtPay;
        final double supplementalPayFinal = supplementalPay;
        final double grossFinal = gross;
        final Calendar startFinal = (Calendar) start.clone();
        final Calendar endFinal = (Calendar) end.clone();

        content.addView(action("SAVE CURRENT PAY STUB", v -> {
            JSONObject snapshot = createPayStubSnapshot(startFinal, endFinal, actualWorkedFinal, paidLeaveFinal,
                    overtimeHoursFinal, regularPayFinal, holidayPayFinal, overtimePayFinal, courtPayFinal,
                    supplementalPayFinal, grossFinal);
            savePayStubSnapshot(snapshot);
            Toast.makeText(this, "Pay stub saved", Toast.LENGTH_SHORT).show();
            showPayrollScreen();
        }));
        content.addView(action("SHARE CURRENT PAYCHECK IMAGE", v -> {
            JSONObject snapshot = createPayStubSnapshot(startFinal, endFinal, actualWorkedFinal, paidLeaveFinal,
                    overtimeHoursFinal, regularPayFinal, holidayPayFinal, overtimePayFinal, courtPayFinal,
                    supplementalPayFinal, grossFinal);
            sharePayStubSnapshotImage(snapshot);
        }));

        addPreviousPayStubsSection();
    }

    private Calendar currentPayPeriodStart() {
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(prefs.getLong("pay_period_anchor", prefs.getLong("anchor", defaultAnchor())));
        Calendar now = Calendar.getInstance();
        long diff = daysBetween(start.getTimeInMillis(), now.getTimeInMillis());
        int periods = Math.floorDiv((int) diff, 14);
        start.add(Calendar.DAY_OF_MONTH, periods * 14);
        return start;
    }

    private boolean isSupplementEligible(Calendar periodStart) {
        Calendar payday = (Calendar) periodStart.clone();
        payday.add(Calendar.DAY_OF_MONTH, 15);
        zeroTime(payday);

        Calendar firstKnownPayday = Calendar.getInstance();
        firstKnownPayday.set(2026, Calendar.JULY, 28, 0, 0, 0);
        firstKnownPayday.set(Calendar.MILLISECOND, 0);

        long difference = daysBetween(firstKnownPayday.getTimeInMillis(), payday.getTimeInMillis());
        int sequence = Math.floorDiv((int) difference, 14);

        Calendar candidate = (Calendar) firstKnownPayday.clone();
        candidate.add(Calendar.DAY_OF_MONTH, sequence * 14);

        while (candidate.after(payday)) candidate.add(Calendar.DAY_OF_MONTH, -14);
        while (candidate.before(payday)) candidate.add(Calendar.DAY_OF_MONTH, 14);

        int occurrence = 1;
        Calendar previous = (Calendar) candidate.clone();
        previous.add(Calendar.DAY_OF_MONTH, -14);
        while (previous.get(Calendar.YEAR) == candidate.get(Calendar.YEAR) &&
                previous.get(Calendar.MONTH) == candidate.get(Calendar.MONTH)) {
            occurrence++;
            previous.add(Calendar.DAY_OF_MONTH, -14);
        }

        return occurrence <= 2;
    }


    private double extraHoursFor(Calendar date) {
        return getDouble("hours_" + keyFormat.format(date.getTime()), 12);
    }

    private long defaultPayday() {
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.JULY, 24, 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private boolean isPayday(Calendar date) {
        long anchor = prefs.getLong("payday_anchor", defaultPayday());
        long diff = daysBetween(anchor, date.getTimeInMillis());
        return floorMod((int)diff, 14) == 0;
    }

    private void pickDate(long[] holder, TextView button, String prefix) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(holder[0]);
        new DatePickerDialog(this, (view,y,m,d) -> {
            Calendar chosen = Calendar.getInstance(); chosen.set(y,m,d,0,0,0); chosen.set(Calendar.MILLISECOND,0);
            holder[0]=chosen.getTimeInMillis(); button.setText(prefix + displayDate.format(chosen.getTime()));
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private String federalHoliday(Calendar c) {
        int y=c.get(Calendar.YEAR), m=c.get(Calendar.MONTH), d=c.get(Calendar.DAY_OF_MONTH);
        if (m==Calendar.JANUARY && d==1) return "NEW YEAR'S";
        if (m==Calendar.JANUARY && isNthWeekday(c, Calendar.MONDAY,3)) return "MLK DAY";
        if (m==Calendar.FEBRUARY && isNthWeekday(c, Calendar.MONDAY,3)) return "PRESIDENTS";
        if (m==Calendar.MAY && isLastWeekday(c, Calendar.MONDAY)) return "MEMORIAL";
        if (m==Calendar.JUNE && d==19) return "JUNETEENTH";
        if (m==Calendar.JULY && d==4) return "INDEPENDENCE";
        if (m==Calendar.SEPTEMBER && isNthWeekday(c, Calendar.MONDAY,1)) return "LABOR DAY";
        if (m==Calendar.OCTOBER && isNthWeekday(c, Calendar.MONDAY,2)) return "COLUMBUS";
        if (m==Calendar.NOVEMBER && d==11) return "VETERANS";
        if (m==Calendar.NOVEMBER && isNthWeekday(c, Calendar.THURSDAY,4)) return "THANKSGIVING";
        if (m==Calendar.DECEMBER && d==25) return "CHRISTMAS";
        return null;
    }
    private boolean isNthWeekday(Calendar c,int weekday,int nth){ return c.get(Calendar.DAY_OF_WEEK)==weekday && ((c.get(Calendar.DAY_OF_MONTH)-1)/7+1)==nth; }
    private boolean isLastWeekday(Calendar c,int weekday){ if(c.get(Calendar.DAY_OF_WEEK)!=weekday)return false; Calendar n=(Calendar)c.clone(); n.add(Calendar.DAY_OF_MONTH,7); return n.get(Calendar.MONTH)!=c.get(Calendar.MONTH); }

    private Calendar paydayForPeriod(Calendar periodStart) {
        Calendar anchorPeriod = Calendar.getInstance(); anchorPeriod.setTimeInMillis(prefs.getLong("pay_period_anchor", defaultAnchor())); zeroTime(anchorPeriod);
        Calendar anchorPayday = Calendar.getInstance(); anchorPayday.setTimeInMillis(prefs.getLong("payday_anchor", defaultPayday())); zeroTime(anchorPayday);
        int periods = Math.floorDiv((int)daysBetween(anchorPeriod.getTimeInMillis(), periodStart.getTimeInMillis()), 14);
        anchorPayday.add(Calendar.DAY_OF_MONTH, periods * 14); return anchorPayday;
    }

    private int paycheckOccurrenceInMonth(Calendar payday) {
        int count=1; Calendar c=(Calendar)payday.clone(); c.add(Calendar.DAY_OF_MONTH,-14);
        while(c.get(Calendar.YEAR)==payday.get(Calendar.YEAR) && c.get(Calendar.MONTH)==payday.get(Calendar.MONTH)){ count++; c.add(Calendar.DAY_OF_MONTH,-14); }
        return count;
    }

    private double estimateFederalWithholding(double grossPay, String status, int qualifyingChildren, int otherDependents) {
        // Employer-pattern estimate validated against five actual Alexis pay stubs.
        // For Married Filing Jointly with one dependent, interpolate between observed gross/tax pairs.
        double[] grossPoints = {1615.38, 1644.23, 2066.62, 2307.69, 2461.54};
        double[] taxPoints   = {  10.19,   13.65,   57.75,   93.17,  109.83};
        double tax = interpolateTax(grossPay, grossPoints, taxPoints);
        int dependents = Math.max(0, qualifyingChildren) + Math.max(0, otherDependents);
        if (!status.equals(FEDERAL_STATUSES[1])) tax *= status.equals(FEDERAL_STATUSES[2]) ? 1.12 : 1.28;
        // The observed baseline is one dependent. Adjust gently for different elections.
        tax -= (dependents - 1) * 18.0;
        return roundMoney(Math.max(0, tax));
    }

    private double interpolateTax(double value, double[] x, double[] y) {
        if (value <= x[0]) return Math.max(0, y[0] + (value - x[0]) * ((y[1]-y[0])/(x[1]-x[0])));
        for (int i=1;i<x.length;i++) {
            if (value <= x[i]) {
                double ratio=(value-x[i-1])/(x[i]-x[i-1]);
                return y[i-1] + ratio*(y[i]-y[i-1]);
            }
        }
        int n=x.length;
        return y[n-1] + (value-x[n-1]) * ((y[n-1]-y[n-2])/(x[n-1]-x[n-2]));
    }

    private double bracketTax(double income, double[] limits, double[] rates) {
        double tax=0, prev=0;
        for(int i=0;i<limits.length;i++){ double slice=Math.min(income,limits[i])-prev; if(slice>0) tax+=slice*rates[i]; if(income<=limits[i]) return tax; prev=limits[i]; }
        return tax + Math.max(0,income-prev)*rates[rates.length-1];
    }

    private double estimateLouisianaWithholding(double grossPay, String status, int dependents) {
        // Employer-pattern estimate validated against five actual Alexis pay stubs.
        double[] grossPoints = {1615.38, 1644.23, 2066.62, 2307.69, 2461.54};
        double[] taxPoints   = {  27.77,   28.66,   40.97,   49.14,   53.43};
        double tax = interpolateTax(grossPay, grossPoints, taxPoints);
        if (!status.equals(LOUISIANA_STATUSES[1])) tax *= 1.12;
        tax -= (Math.max(0, dependents) - 1) * 2.0;
        return roundMoney(Math.max(0, tax));
    }

    private BigDecimal bd(double value) { return new BigDecimal(Double.toString(value)); }
    private double moneyValue(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP).doubleValue(); }
    private double roundMoney(double value) { return moneyValue(bd(value)); }

    private JSONObject createPayStubSnapshot(Calendar start, Calendar end, double worked, double leave,
                                             double otHours, double regularPay, double holidayPay, double otPay,
                                             double courtPay, double supplement, double gross) {
        JSONObject o = new JSONObject();
        try {
            Calendar payday = paydayForPeriod(start);
            boolean healthApplies = paycheckOccurrenceInMonth(payday) <= 2;
            double health = healthApplies ? moneyValue(bd(getDouble("health_deduction", 0))) : 0;
            double retirement = moneyValue(bd(regularPay + holidayPay + supplement)
                    .multiply(bd(getDouble("retirement_percent", 0)))
                    .divide(new BigDecimal("100"), 12, RoundingMode.HALF_UP));
            double federal = moneyValue(bd(estimateFederalWithholding(gross,
                    getString("federal_status", FEDERAL_STATUSES[0]),
                    (int)getDouble("federal_children", 0),
                    (int)getDouble("federal_other_dependents", 0)))
                    .add(bd(getDouble("federal_extra", 0))));
            double state = moneyValue(bd(estimateLouisianaWithholding(gross,
                    getString("state_status", LOUISIANA_STATUSES[0]),
                    (int)getDouble("state_dependents", 0)))
                    .add(bd(getDouble("state_extra", 0))));
            double medicare = moneyValue(bd(gross).multiply(new BigDecimal("0.0145")));
            double other = moneyValue(bd(getDouble("other_deductions", 0)));
            double net = moneyValue(bd(gross).subtract(bd(health)).subtract(bd(retirement))
                    .subtract(bd(federal)).subtract(bd(state)).subtract(bd(medicare)).subtract(bd(other)));

            o.put("id", payday.getTimeInMillis());
            o.put("payday", payday.getTimeInMillis());
            o.put("start", start.getTimeInMillis());
            o.put("end", end.getTimeInMillis());
            o.put("name", prefs.getString("officer_name", activeProfile));
            o.put("worked", worked); o.put("leave", leave); o.put("otHours", otHours);
            o.put("regularPay", regularPay); o.put("holidayPay", holidayPay); o.put("otPay", otPay);
            o.put("courtPay", courtPay); o.put("supplement", supplement); o.put("gross", gross);
            o.put("health", health); o.put("retirement", retirement); o.put("federal", federal);
            o.put("state", state); o.put("medicare", medicare); o.put("other", other); o.put("net", net);
            o.put("vacation", getDouble("vacation_balance", 0));
            o.put("sick", getDouble("sick_balance", 0));
            o.put("comp", getDouble("comp_balance", 0));
        } catch (Exception ignored) { }
        return o;
    }

    private void savePayStubSnapshot(JSONObject o) {
        try {
            long id = o.optLong("id", o.optLong("payday"));
            prefs.edit().putString("stub_" + id, o.toString()).apply();
            ArrayList<Long> ids = getSavedStubIds();
            if (!ids.contains(id)) ids.add(id);
            Collections.sort(ids, Collections.reverseOrder());
            JSONArray arr = new JSONArray();
            for (Long savedId : ids) arr.put(savedId);
            prefs.edit().putString("stub_index", arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    private ArrayList<Long> getSavedStubIds() {
        ArrayList<Long> ids=new ArrayList<>();
        try { JSONArray arr=new JSONArray(prefs.getString("stub_index","[]")); for(int i=0;i<arr.length();i++) ids.add(arr.getLong(i)); } catch(Exception ignored) {}
        Collections.sort(ids, Collections.reverseOrder()); return ids;
    }

    private JSONObject getSavedStub(long id) {
        try { return new JSONObject(prefs.getString("stub_"+id,"{}")); } catch(Exception e) { return new JSONObject(); }
    }

    private void addPreviousPayStubsSection() {
        ArrayList<Long> ids=getSavedStubIds();
        content.addView(section("PREVIOUS PAY STUBS"));
        if(ids.isEmpty()) { content.addView(summaryCard("SAVED STUBS","None saved yet","▤")); return; }
        Spinner spinner=new Spinner(this);
        ArrayList<String> labels=new ArrayList<>();
        ArrayList<JSONObject> snapshots=new ArrayList<>();
        for(Long id:ids) {
            JSONObject o=getSavedStub(id);
            snapshots.add(o);
            labels.add(displayDate.format(new Date(o.optLong("payday",id)))+" • "+money(o.optDouble("net",0)));
        }
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        content.addView(labeledSpinner("NEWEST TO OLDEST",spinner));
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView view=action("VIEW",v->showSavedStubDialog(snapshots.get(spinner.getSelectedItemPosition())));
        TextView share=action("SHARE",v->sharePayStubSnapshotImage(snapshots.get(spinner.getSelectedItemPosition())));
        actions.addView(view,new LinearLayout.LayoutParams(0,dp(52),1)); actions.addView(share,new LinearLayout.LayoutParams(0,dp(52),1)); content.addView(actions);
    }

    private void showSavedStubDialog(JSONObject o) {
        String text="Pay date: "+displayDate.format(new Date(o.optLong("payday")))+"\n"+
                "Pay period: "+displayDate.format(new Date(o.optLong("start")))+" – "+displayDate.format(new Date(o.optLong("end")))+"\n\n"+
                "Gross: "+money(o.optDouble("gross"))+"\n"+
                "Holiday pay: "+money(o.optDouble("holidayPay"))+"\n"+
                "Federal: "+money(o.optDouble("federal"))+"\n"+
                "Louisiana: "+money(o.optDouble("state"))+"\n"+
                "Medicare: "+money(o.optDouble("medicare"))+"\n"+
                "Retirement: "+money(o.optDouble("retirement"))+"\n"+
                "Health: "+money(o.optDouble("health"))+"\n"+
                "Net: "+money(o.optDouble("net"));
        new AlertDialog.Builder(this).setTitle(o.optString("name",activeProfile)+" Pay Stub").setMessage(text).setPositiveButton("Close",null).show();
    }

    private void sharePayStubSnapshotImage(JSONObject o) {
        shareThemedPayStub(o, "Share pay stub");
    }

    private void sharePayStubSnapshotImage(JSONObject o, Calendar start, Calendar end) {
        shareThemedPayStub(o, "Share saved pay stub");
    }

    private void shareThemedPayStub(JSONObject o, String chooserTitle) {
        try {
            Calendar start = Calendar.getInstance(); start.setTimeInMillis(o.optLong("start"));
            Calendar end = Calendar.getInstance(); end.setTimeInMillis(o.optLong("end"));
            Bitmap bmp = Bitmap.createBitmap(1080, 1820, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp); Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            LinearGradient bg = new LinearGradient(0,0,0,1820,Color.rgb(3,12,24),Color.rgb(12,34,58),Shader.TileMode.CLAMP); p.setShader(bg); c.drawRect(0,0,1080,1820,p); p.setShader(null);
            p.setColor(Color.rgb(18,39,64)); c.drawRoundRect(new RectF(36,32,1044,1788),38,38,p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(5); p.setColor(gold); c.drawRoundRect(new RectF(36,32,1044,1788),38,38,p); p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD); p.setColor(gold); p.setTextSize(42); c.drawText("POLICE INCIDENT & COMPENSATION REPORT",540,102,p);
            p.setColor(Color.WHITE); p.setTextSize(54); c.drawText(o.optString("name",activeProfile).toUpperCase(Locale.US),540,168,p);
            p.setTextSize(24); p.setColor(silver); c.drawText("PAY DATE: "+displayDate.format(new Date(o.optLong("payday"))),540,212,p); c.drawText("PAY PERIOD: "+displayDate.format(start.getTime())+" – "+displayDate.format(end.getTime()),540,248,p);
            drawMoneyOfficer(c,p,540,390);
            p.setColor(Color.rgb(9,25,44)); c.drawRoundRect(new RectF(70,520,1010,1530),28,28,p); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(Color.rgb(57,91,128)); c.drawRoundRect(new RectF(70,520,1010,1530),28,28,p); p.setStyle(Paint.Style.FILL);
            int y=595;
            y=funStubLine(c,p,"Hours spent protecting & eating donuts",String.format(Locale.US,"%.1f",o.optDouble("worked")),y);
            y=funStubLine(c,p,"Paid leave on official donut assignment",String.format(Locale.US,"%.1f",o.optDouble("leave")),y);
            y=funStubLine(c,p,"Regular patrol earnings",money(o.optDouble("regularPay")),y);
            y=funStubLine(c,p,"Holiday bonus: serving with extra sprinkles",money(o.optDouble("holidayPay")),y);
            y=funStubLine(c,p,"Overtime donut surveillance ("+String.format(Locale.US,"%.1f",o.optDouble("otHours"))+" hrs)",money(o.optDouble("otPay")),y);
            y=funStubLine(c,p,"Courtroom testimony & dramatic objections",money(o.optDouble("courtPay")),y);
            y=funStubLine(c,p,"Supplemental badge-polish allowance",money(o.optDouble("supplement")),y);
            y=funStubLine(c,p,"TOTAL EARNINGS ON RECORD",money(o.optDouble("gross")),y);
            y=funStubLine(c,p,"Insurance, benefits & coffee protection",money(o.optDouble("health")),y);
            y=funStubLine(c,p,"Retirement: future fishing fund",money(o.optDouble("retirement")),y);
            y=funStubLine(c,p,"Federal donut tax",money(o.optDouble("federal")),y);
            y=funStubLine(c,p,"Louisiana fun tax",money(o.optDouble("state")),y);
            y=funStubLine(c,p,"Medicare contribution",money(o.optDouble("medicare")),y);
            y=funStubLine(c,p,"Other deductions: cuffs, keys & snacks",money(o.optDouble("other")),y);
            p.setColor(gold); p.setTextSize(46); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextAlign(Paint.Align.LEFT); c.drawText("GRAND NET TOTAL",86,1610,p); p.setTextAlign(Paint.Align.RIGHT); c.drawText(money(o.optDouble("net")),994,1610,p);
            p.setTextAlign(Paint.Align.CENTER); p.setTextSize(25); p.setColor(silver); c.drawText("LEAVE BANKS  •  Vacation "+formatHours(o.optDouble("vacation"))+"   Sick "+formatHours(o.optDouble("sick"))+"   Comp "+formatHours(o.optDouble("comp")),540,1678,p);
            p.setColor(Color.rgb(118,143,168)); p.setTextSize(22); c.drawText("Accurate numbers. Questionable donut accounting.",540,1735,p);
            File dir=new File(getCacheDir(),"shared"); dir.mkdirs(); File f=new File(dir,"bolo-fun-paystub-"+o.optLong("payday")+"-"+System.currentTimeMillis()+".png"); FileOutputStream out=new FileOutputStream(f); bmp.compress(Bitmap.CompressFormat.PNG,100,out); out.close();
            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f); Intent send=new Intent(Intent.ACTION_SEND); send.setType("image/png"); send.putExtra(Intent.EXTRA_STREAM,uri); send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(Intent.createChooser(send,chooserTitle));
        } catch(Exception e) { Toast.makeText(this,"Unable to create pay stub image",Toast.LENGTH_LONG).show(); }
    }

    private int funStubLine(Canvas c, Paint p, String left, String right, int y) {
        p.setColor((y/70)%2==0 ? Color.rgb(24,49,78) : Color.rgb(18,40,66)); c.drawRoundRect(new RectF(86,y-37,994,y+22),12,12,p);
        p.setTextAlign(Paint.Align.LEFT); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(25); p.setColor(silver); c.drawText(left,104,y,p);
        p.setTextAlign(Paint.Align.RIGHT); p.setTextSize(28); p.setColor(Color.WHITE); c.drawText(right,974,y,p); return y+68;
    }

    private int stubLine(Canvas c,Paint p,String left,String right,int y){ p.setTextAlign(Paint.Align.LEFT); p.setTypeface(Typeface.DEFAULT); p.setTextSize(31); p.setColor(silver); c.drawText(left,85,y,p); p.setTextAlign(Paint.Align.RIGHT); p.setTypeface(Typeface.DEFAULT_BOLD); p.setColor(Color.WHITE); c.drawText(right,995,y,p); p.setColor(Color.rgb(65,84,104)); c.drawRect(85,y+15,995,y+17,p); return y+60; }
    private void drawMoneyOfficer(Canvas c,Paint p,float x,float y){
        p.setColor(gold); c.drawCircle(x,y,92,p); p.setColor(navy); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(120); c.drawText("$",x,y+42,p);
        p.setColor(Color.rgb(20,40,65)); c.drawRect(x-110,y-130,x+110,y-95,p); c.drawRect(x-70,y-170,x+70,y-125,p);
        p.setColor(Color.WHITE); p.setTextSize(34); c.drawText("POLICE",x,y-105,p);
        p.setColor(Color.rgb(80,190,105)); for(int i=-3;i<=3;i++){ float dx=i*115; c.save(); c.rotate(i*12,x+dx,y-5-Math.abs(i)*22); c.drawRect(x+dx-30,y-20-Math.abs(i)*25,x+dx+30,y+15-Math.abs(i)*25,p); c.restore(); }
    }

    private void sharePayStubSnapshotImage(JSONObject o, Calendar start, Calendar end) {
        try {
            Bitmap bmp=Bitmap.createBitmap(1080,1350,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(bmp); Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.rgb(5,14,25)); c.drawRect(0,0,1080,1350,p); p.setColor(Color.rgb(19,38,58)); c.drawRoundRect(new RectF(45,45,1035,1305),34,34,p);
            p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD); p.setColor(Color.WHITE); p.setTextSize(70); c.drawText("BOLO BOARD PAY STUB",540,135,p);
            p.setTextSize(40); p.setColor(gold); c.drawText(o.optString("name",activeProfile),540,195,p);
            p.setTextSize(25); p.setColor(silver); c.drawText("Pay date: "+displayDate.format(new Date(o.optLong("payday"))),540,238,p);
            c.drawText("Pay period: "+displayDate.format(start.getTime())+" – "+displayDate.format(end.getTime()),540,275,p);
            drawMoneyOfficer(c,p,540,415); p.setTextAlign(Paint.Align.LEFT); int y=595;
            y=stubLine(c,p,"Actual hours worked",String.format(Locale.US,"%.1f",o.optDouble("worked")),y);
            y=stubLine(c,p,"Paid leave hours",String.format(Locale.US,"%.1f",o.optDouble("leave")),y);
            y=stubLine(c,p,"Regular wages",money(o.optDouble("regularPay")),y); y=stubLine(c,p,"Overtime ("+String.format(Locale.US,"%.1f",o.optDouble("otHours"))+" hrs)",money(o.optDouble("otPay")),y);
            y=stubLine(c,p,"Court pay",money(o.optDouble("courtPay")),y); y=stubLine(c,p,"Supplemental pay",money(o.optDouble("supplement")),y); y=stubLine(c,p,"Gross pay",money(o.optDouble("gross")),y);
            y=stubLine(c,p,"Health insurance",money(o.optDouble("health")),y); y=stubLine(c,p,"Retirement",money(o.optDouble("retirement")),y); y=stubLine(c,p,"Federal withholding",money(o.optDouble("federal")),y); y=stubLine(c,p,"Louisiana withholding",money(o.optDouble("state")),y); y=stubLine(c,p,"Other deductions",money(o.optDouble("other")),y);
            p.setColor(gold); p.setTextSize(46); p.setTypeface(Typeface.DEFAULT_BOLD); c.drawText("NET",85,y+40,p); p.setTextAlign(Paint.Align.RIGHT); c.drawText(money(o.optDouble("net")),995,y+40,p);
            p.setTextAlign(Paint.Align.LEFT); p.setTextSize(28); p.setColor(silver); c.drawText("BANKS  •  Vacation "+formatHours(o.optDouble("vacation"))+"   Sick "+formatHours(o.optDouble("sick"))+"   Comp "+formatHours(o.optDouble("comp")),85,y+105,p);
            File dir=new File(getCacheDir(),"shared"); dir.mkdirs(); File f=new File(dir,"bolo-paystub-"+o.optLong("payday")+"-"+System.currentTimeMillis()+".png"); FileOutputStream out=new FileOutputStream(f); bmp.compress(Bitmap.CompressFormat.PNG,100,out); out.close();
            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f); Intent send=new Intent(Intent.ACTION_SEND); send.setType("image/png"); send.putExtra(Intent.EXTRA_STREAM,uri); send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(Intent.createChooser(send,"Share saved pay stub"));
        } catch(Exception e){ Toast.makeText(this,"Unable to create saved pay stub image",Toast.LENGTH_LONG).show(); }
    }

    private String formatRate(double value) {
        if (value == 0) return "";
        if (Math.rint(value) == value) return String.format(Locale.US, "%.0f", value);
        return String.format(Locale.US, "%.2f", value);
    }

    private String formatPayRate(double value) {
        if (value == 0) return "";
        String text = String.format(Locale.US, "%.12f", value);
        while (text.contains(".") && text.endsWith("0")) text = text.substring(0, text.length() - 1);
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    private LinearLayout summaryCard(String label, String value, String icon) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.setBackground(rounded(panel, dp(9), Color.rgb(56, 75, 94), 1));

        TextView i = new TextView(this);
        i.setText(icon);
        i.setTextSize(28);
        i.setTextColor(gold);
        i.setGravity(Gravity.CENTER);

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(12), 0, 0, 0);

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(muted);
        l.setTextSize(12);

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(Color.WHITE);
        v.setTextSize(21);
        v.setTypeface(Typeface.DEFAULT_BOLD);

        words.addView(l);
        words.addView(v);
        card.addView(i, new LinearLayout.LayoutParams(dp(46), -1));
        card.addView(words, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(5), 0, dp(5));
        card.setLayoutParams(lp);
        return card;
    }

    private String money(double v) {
        return String.format(Locale.US, "$%,.2f", v);
    }

    private void showLeaveScreen() {
        baseScreen("LEAVE BANKS");
        content.addView(summaryCard("VACATION", formatHours(getDouble("vacation_balance", 0)), "☀"));
        content.addView(summaryCard("SICK", formatHours(getDouble("sick_balance", 0)), "✚"));
        content.addView(summaryCard("COMP", formatHours(getDouble("comp_balance", 0)), "★"));

        content.addView(section("MANUAL ADJUSTMENT"));

        Spinner bank = new Spinner(this);
        bank.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Vacation", "Sick", "Comp"}));

        EditText amount = input("Hours to add (negative subtracts)", "", true);
        content.addView(bank);
        content.addView(amount);

        content.addView(action("APPLY ADJUSTMENT", v -> {
            double h = parse(amount.getText().toString());
            String key = bank.getSelectedItemPosition() == 0 ? "vacation_balance" :
                    bank.getSelectedItemPosition() == 1 ? "sick_balance" : "comp_balance";
            addBalance(key, h);
            showLeaveScreen();
        }));

        content.addView(section("JANUARY 1 ACCRUAL"));
        content.addView(summaryCard("VACATION ACCRUAL", formatHours(getDouble("vacation_accrual", 0)), "+"));
        content.addView(summaryCard("SICK ACCRUAL", formatHours(getDouble("sick_accrual", 0)), "+"));
    }

    private String formatHours(double h) {
        return String.format(Locale.US, "%.1f hrs", h);
    }

    private void showSettingsScreen() {
        baseScreen("SETTINGS");
        content.addView(section("PROFILE & SCHEDULE"));

        EditText name = input("Officer/profile name", prefs.getString("officer_name", activeProfile.equals("John") ? "J. Leger" : "A. Leger"), false);
        Spinner rankSpinner = new Spinner(this);
        rankSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, RANKS));
        selectSpinner(rankSpinner, RANKS, prefs.getString("rank", activeProfile.equals("John") ? "Lieutenant" : "Corporal"));
        Spinner pattern = new Spinner(this);
        pattern.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, PATTERNS));
        String current = prefs.getString("pattern", "Alpha");
        for (int i = 0; i < PATTERNS.length; i++) if (PATTERNS[i].equals(current)) pattern.setSelection(i);

        final long[] selectedAnchor = {prefs.getLong("anchor", defaultAnchor())};
        TextView anchor = action("PATTERN ANCHOR: " + displayDate.format(new Date(selectedAnchor[0])), null);
        anchor.setOnClickListener(v -> pickDate(selectedAnchor, anchor, "PATTERN ANCHOR: "));
        final long[] payPeriodAnchor = {prefs.getLong("pay_period_anchor", selectedAnchor[0])};
        final long[] paydayAnchor = {prefs.getLong("payday_anchor", defaultPayday())};
        TextView payPeriodButton = action("INITIAL PAY PERIOD START: " + displayDate.format(new Date(payPeriodAnchor[0])), null);
        payPeriodButton.setOnClickListener(v -> pickDate(payPeriodAnchor, payPeriodButton, "INITIAL PAY PERIOD START: "));
        TextView paydayButton = action("INITIAL PAYDAY: " + displayDate.format(new Date(paydayAnchor[0])), null);
        paydayButton.setOnClickListener(v -> pickDate(paydayAnchor, paydayButton, "INITIAL PAYDAY: "));

        content.addView(labeledField("PROFILE NAME", name));
        content.addView(labeledSpinner("RANK & INSIGNIA", rankSpinner));
        content.addView(labeledSpinner("SHIFT PATTERN", pattern));
        content.addView(anchor); content.addView(payPeriodButton); content.addView(paydayButton);

        content.addView(section("TAXES, RETIREMENT & DEDUCTIONS"));
        Spinner federalStatus = new Spinner(this);
        federalStatus.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, FEDERAL_STATUSES));
        selectSpinner(federalStatus, FEDERAL_STATUSES, getString("federal_status", FEDERAL_STATUSES[0]));
        Spinner stateStatus = new Spinner(this);
        stateStatus.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, LOUISIANA_STATUSES));
        selectSpinner(stateStatus, LOUISIANA_STATUSES, getString("state_status", LOUISIANA_STATUSES[0]));

        EditText federalChildren = input("Number of qualifying children under 17", formatRate(getDouble("federal_children", 0)), true);
        EditText federalOtherDependents = input("Number of other dependents", formatRate(getDouble("federal_other_dependents", 0)), true);
        EditText stateDependents = input("Louisiana dependents / exemptions", formatRate(getDouble("state_dependents", 0)), true);
        EditText retirement = input("Retirement percentage", formatRate(getDouble("retirement_percent", 0)), true);
        EditText federalExtra = input("Extra federal dollars per check", formatRate(getDouble("federal_extra", 0)), true);
        EditText stateExtra = input("Extra Louisiana dollars per check", formatRate(getDouble("state_extra", 0)), true);
        EditText health = input("Pretax insurance & benefits per check", formatRate(getDouble("health_deduction", 0)), true);
        EditText deductions = input("Other deductions per check", formatRate(getDouble("other_deductions", 0)), true);

        content.addView(labeledSpinner("FEDERAL FILING STATUS", federalStatus));
        content.addView(labeledSpinner("LOUISIANA FILING STATUS", stateStatus));
        content.addView(labeledField("FEDERAL QUALIFYING CHILDREN UNDER 17", federalChildren));
        content.addView(labeledField("FEDERAL OTHER DEPENDENTS", federalOtherDependents));
        content.addView(labeledField("LOUISIANA DEPENDENTS / EXEMPTIONS", stateDependents));
        content.addView(labeledField("RETIREMENT %", retirement));
        content.addView(labeledField("ADDITIONAL FEDERAL WITHHOLDING", federalExtra));
        content.addView(labeledField("ADDITIONAL LOUISIANA WITHHOLDING", stateExtra));
        content.addView(labeledField("PRETAX INSURANCE & BENEFITS — FIRST TWO CHECKS EACH MONTH", health));
        content.addView(labeledField("OTHER DEDUCTIONS PER CHECK", deductions));

        content.addView(section("LEAVE BANKS"));
        EditText vacation = input("Vacation starting balance", String.valueOf(getDouble("vacation_balance", 0)), true);
        EditText sick = input("Sick starting balance", String.valueOf(getDouble("sick_balance", 0)), true);
        EditText comp = input("Comp starting balance", String.valueOf(getDouble("comp_balance", 0)), true);
        EditText vacAcc = input("Vacation hours added each January 1", String.valueOf(getDouble("vacation_accrual", 0)), true);
        EditText sickAcc = input("Sick hours added each January 1", String.valueOf(getDouble("sick_accrual", 0)), true);
        content.addView(labeledField("VACATION STARTING BALANCE", vacation));
        content.addView(labeledField("SICK STARTING BALANCE", sick));
        content.addView(labeledField("COMP STARTING BALANCE", comp));
        content.addView(labeledField("VACATION JANUARY ACCRUAL", vacAcc));
        content.addView(labeledField("SICK JANUARY ACCRUAL", sickAcc));

        content.addView(action("SAVE SETTINGS", v -> {
            prefs.edit()
                    .putString("officer_name", name.getText().toString().trim())
                    .putString("rank", RANKS[rankSpinner.getSelectedItemPosition()])
                    .putString("pattern", PATTERNS[pattern.getSelectedItemPosition()])
                    .putLong("anchor", selectedAnchor[0]).putLong("pay_period_anchor", payPeriodAnchor[0]).putLong("payday_anchor", paydayAnchor[0])
                    .putString("federal_status", FEDERAL_STATUSES[federalStatus.getSelectedItemPosition()])
                    .putString("state_status", LOUISIANA_STATUSES[stateStatus.getSelectedItemPosition()])
                    .putString("federal_children", federalChildren.getText().toString())
                    .putString("federal_other_dependents", federalOtherDependents.getText().toString())
                    .putString("state_dependents", stateDependents.getText().toString())
                    .putString("retirement_percent", retirement.getText().toString())
                    .putString("federal_extra", federalExtra.getText().toString()).putString("state_extra", stateExtra.getText().toString())
                    .putString("health_deduction", health.getText().toString()).putString("other_deductions", deductions.getText().toString())
                    .putString("vacation_balance", vacation.getText().toString()).putString("sick_balance", sick.getText().toString())
                    .putString("comp_balance", comp.getText().toString()).putString("vacation_accrual", vacAcc.getText().toString())
                    .putString("sick_accrual", sickAcc.getText().toString()).apply();
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
            showCalendarScreen();
        }));

        content.addView(action("CLEAR ALL MANUAL CALENDAR ENTRIES", v -> new AlertDialog.Builder(this)
                .setTitle("Clear overrides?").setMessage("Schedule settings and leave balances remain.")
                .setPositiveButton("Clear", (d, w) -> {
                    SharedPreferences.Editor ed = prefs.edit();
                    for (String key : prefs.getAll().keySet()) if (key.startsWith("entry_") || key.startsWith("ot_hours_") || key.startsWith("comp_hours_") || key.startsWith("court_hours_")) ed.remove(key);
                    ed.apply(); Toast.makeText(this, "Calendar entries cleared", Toast.LENGTH_SHORT).show();
                }).setNegativeButton("Cancel", null).show()));
    }

    private LinearLayout labeledSpinner(String label, Spinner spinner) {
        LinearLayout wrap = new LinearLayout(this); wrap.setOrientation(LinearLayout.VERTICAL);
        TextView lab = new TextView(this); lab.setText(label); lab.setTextColor(muted); lab.setTextSize(12); lab.setTypeface(Typeface.DEFAULT_BOLD); lab.setPadding(dp(3), dp(8), dp(3), dp(4));
        spinner.setBackground(rounded(panel2, dp(8), Color.rgb(63,84,106), 1)); spinner.setPadding(dp(8),dp(5),dp(8),dp(5));
        wrap.addView(lab); wrap.addView(spinner, new LinearLayout.LayoutParams(-1, dp(52))); return wrap;
    }

    private void selectSpinner(Spinner spinner, String[] options, String value) { for (int i=0;i<options.length;i++) if (options[i].equals(value)) spinner.setSelection(i); }
    private String getString(String key, String fallback) { return prefs.getString(key, fallback); }

    private void addBalance(String key, double amount) {
        double current = getDouble(key, 0);
        prefs.edit().putString(key, String.valueOf(current + amount)).apply();
    }

    private double getDouble(String key, double fallback) {
        try {
            return Double.parseDouble(prefs.getString(key, String.valueOf(fallback)));
        } catch (Exception e) {
            return fallback;
        }
    }

    private double parse(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private void applyAnnualAccrualIfNeeded() {
        Calendar now = Calendar.getInstance();
        int year = now.get(Calendar.YEAR);
        int applied = prefs.getInt("accrual_applied_year", 0);
        if (applied < year) {
            addBalance("vacation_balance", getDouble("vacation_accrual", 0));
            addBalance("sick_balance", getDouble("sick_accrual", 0));
            prefs.edit().putInt("accrual_applied_year", year).apply();
        }
    }

    private class PoliceHeaderView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        PoliceHeaderView(Activity c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            int w = getWidth();
            int h = getHeight();
            p.setColor(Color.argb(225, 2, 8, 15));
            c.drawRect(0, 0, w, h, p);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            p.setTextSize(dp(27));
            p.setShadowLayer(dp(3), 0, dp(1), Color.BLACK);
            p.setColor(Color.WHITE);
            c.drawText("BOLO BOARD", w / 2f, dp(39), p);
            p.clearShadowLayer();
        }
    }

    private class ShiftIconView extends View {
        private final String status;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        ShiftIconView(Activity c, String status) {
            super(c);
            this.status = status;
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float pulse = 1.0f;
            float r = Math.min(w, h) * .31f * pulse;

            if (status.equals("Day Shift")) drawSunOfficer(c, cx, h * .39f, r);
            else if (status.equals("Night Shift")) drawMoonOfficer(c, cx, h * .39f, r);
            else if (status.equals("Vacation")) drawVacationCar(c, cx, h * .39f, r);
            else if (status.equals("Day Off")) drawOffBadge(c, cx, h * .39f, r);
            else if (status.equals("Sick")) drawSickCar(c, cx, h * .39f, r);
            else drawSpecial(c, cx, h * .39f, r, status);

            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(dp(8));
            p.setColor(labelColor(status));
            c.drawText(shortLabel(status), cx, h - dp(2), p);
        }

        private void drawSunOfficer(Canvas c, float x, float y, float r) {
            p.setColor(gold);
            p.setStrokeWidth(dp(2));
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 6;
                float x1 = (float) (x + Math.cos(a) * r * 1.02);
                float y1 = (float) (y + Math.sin(a) * r * 1.02);
                float x2 = (float) (x + Math.cos(a) * r * 1.28);
                float y2 = (float) (y + Math.sin(a) * r * 1.28);
                c.drawLine(x1, y1, x2, y2, p);
            }
            c.drawCircle(x, y, r, p);
            drawOfficerHat(c, x, y - r * .72f, r);
            p.setColor(Color.rgb(28, 28, 28));
            c.drawCircle(x - r * .28f, y - r * .08f, r * .08f, p);
            c.drawCircle(x + r * .28f, y - r * .08f, r * .08f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            c.drawArc(new RectF(x - r * .35f, y - r * .02f, x + r * .35f, y + r * .42f), 20, 140, false, p);
            p.setStyle(Paint.Style.FILL);
        }

        private void drawMoonOfficer(Canvas c, float x, float y, float r) {
            p.setColor(Color.rgb(218, 224, 235));
            c.drawCircle(x, y, r, p);
            p.setColor(Color.rgb(176, 185, 201));
            c.drawCircle(x-r*.35f, y-r*.30f, r*.12f, p);
            c.drawCircle(x+r*.28f, y-r*.18f, r*.09f, p);
            c.drawCircle(x-r*.18f, y+r*.28f, r*.08f, p);
            drawOfficerHat(c, x, y-r*.78f, r);
            p.setColor(Color.rgb(28,35,45));
            c.drawCircle(x-r*.27f, y-r*.06f, r*.07f, p);
            c.drawCircle(x+r*.27f, y-r*.06f, r*.07f, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2));
            c.drawArc(new RectF(x-r*.34f,y+r*.02f,x+r*.34f,y+r*.38f),20,140,false,p);
            p.setStyle(Paint.Style.FILL);
        }

        private void drawOfficerHat(Canvas c, float x, float y, float r) {
            p.setColor(Color.rgb(6, 32, 65));
            c.drawRoundRect(new RectF(x - r * .52f, y - r * .18f, x + r * .52f, y + r * .22f),
                    r * .15f, r * .15f, p);
            c.drawOval(new RectF(x - r * .73f, y + r * .10f, x + r * .73f, y + r * .31f), p);
            p.setColor(gold);
            c.drawCircle(x, y + r * .01f, r * .12f, p);
            p.setColor(Color.WHITE);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(r * .16f);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText("★", x, y + r * .06f, p);
        }

        private void drawVacationCar(Canvas c, float x, float y, float r) {
            p.setColor(Color.rgb(226, 230, 235));
            c.drawRoundRect(new RectF(x - r, y - r * .30f, x + r, y + r * .28f), r * .18f, r * .18f, p);

            p.setColor(Color.rgb(16, 24, 34));
            Path roof = new Path();
            roof.moveTo(x - r * .46f, y - r * .30f);
            roof.lineTo(x - r * .22f, y - r * .68f);
            roof.lineTo(x + r * .42f, y - r * .68f);
            roof.lineTo(x + r * .67f, y - r * .30f);
            roof.close();
            c.drawPath(roof, p);

            p.setColor(Color.BLACK);
            c.drawCircle(x - r * .58f, y + r * .30f, r * .20f, p);
            c.drawCircle(x + r * .58f, y + r * .30f, r * .20f, p);

            p.setColor(blue);
            c.drawRect(x - r * .12f, y - r * .81f, x, y - r * .68f, p);
            p.setColor(red);
            c.drawRect(x, y - r * .81f, x + r * .12f, y - r * .68f, p);

            p.setColor(Color.rgb(255, 225, 92));
            c.save();
            c.rotate(-8, x + r * .20f, y + r * .24f);
            c.drawRect(new RectF(x - r * .25f, y - r * .02f, x + r * .70f, y + r * .65f), p);
            p.setColor(Color.BLACK);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(r * .17f);
            c.drawText("ON", x + r * .22f, y + r * .20f, p);
            c.drawText("VACATION", x + r * .22f, y + r * .42f, p);
            c.restore();
        }

        private void drawSickCar(Canvas c, float x, float y, float r) {
            p.setColor(Color.rgb(205, 216, 226));
            c.drawRoundRect(new RectF(x-r, y-r*.28f, x+r, y+r*.30f), r*.18f, r*.18f, p);
            p.setColor(Color.rgb(18, 31, 46));
            Path roof = new Path(); roof.moveTo(x-r*.48f,y-r*.28f); roof.lineTo(x-r*.20f,y-r*.67f); roof.lineTo(x+r*.42f,y-r*.67f); roof.lineTo(x+r*.68f,y-r*.28f); roof.close(); c.drawPath(roof,p);
            p.setColor(Color.BLACK); c.drawCircle(x-r*.58f,y+r*.31f,r*.20f,p); c.drawCircle(x+r*.58f,y+r*.31f,r*.20f,p);
            p.setColor(blue); c.drawRect(x-r*.14f,y-r*.80f,x,y-r*.67f,p); p.setColor(red); c.drawRect(x,y-r*.80f,x+r*.14f,y-r*.67f,p);
            p.setColor(Color.rgb(22, 34, 48)); c.drawRect(x-r*.22f,y-r*.03f,x+r*.22f,y+r*.20f,p);
            p.setStrokeWidth(Math.max(3f,r*.10f)); p.setStrokeCap(Paint.Cap.ROUND); p.setColor(Color.WHITE); c.drawLine(x+r*.10f,y+r*.05f,x+r*.56f,y-r*.48f,p);
            p.setColor(red); c.drawCircle(x+r*.62f,y-r*.56f,r*.14f,p); p.setStrokeCap(Paint.Cap.BUTT);
            p.setColor(Color.rgb(130, 205, 255)); c.drawCircle(x-r*.25f,y-r*.02f,r*.07f,p); c.drawCircle(x+r*.30f,y-r*.02f,r*.07f,p);
        }

        private void drawOffBadge(Canvas c, float x, float y, float r) {
            p.setColor(Color.rgb(64, 75, 88));
            Path star = new Path();
            for (int i = 0; i < 16; i++) {
                double a = -Math.PI / 2 + i * Math.PI / 8;
                float rr = (i % 2 == 0) ? r : r * .68f;
                float px = (float) (x + Math.cos(a) * rr);
                float py = (float) (y + Math.sin(a) * rr);
                if (i == 0) star.moveTo(px, py);
                else star.lineTo(px, py);
            }
            star.close();
            c.drawPath(star, p);

            p.setColor(Color.LTGRAY);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(r * .40f);
            c.drawText("OFF", x, y + r * .14f, p);
        }

        private void drawSpecial(Canvas c, float x, float y, float r, String s) {
            int color = s.contains("Court") ? Color.rgb(255, 145, 30) :
                    s.contains("Sick") ? red :
                    s.contains("Extra") ? Color.rgb(32, 150, 136) :
                    Color.rgb(122, 81, 255);
            p.setColor(color);
            c.drawCircle(x, y, r, p);

            p.setColor(Color.WHITE);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(r * .68f);
            String symbol = s.contains("Court") ? "⚖" :
                    s.contains("Sick") ? "+" :
                    s.contains("Extra") ? "★" : "C";
            c.drawText(symbol, x, y + r * .24f, p);
        }

        private int labelColor(String s) {
            if (s.equals("Day Shift")) return gold;
            if (s.equals("Night Shift")) return Color.rgb(65, 160, 255);
            if (s.equals("Vacation")) return Color.rgb(210, 106, 255);
            if (s.equals("Day Off")) return Color.LTGRAY;
            return Color.WHITE;
        }

        private String shortLabel(String s) {
            if (s.equals("Day Shift")) return "DAY";
            if (s.equals("Night Shift")) return "NIGHT";
            if (s.equals("Day Off")) return "OFF";
            if (s.contains("OT Requested")) return "OT";
            if (s.contains("Comp Requested")) return "COMP+";
            if (s.contains("Regular")) return "EXTRA";
            if (s.equals("Vacation")) return "VAC";
            if (s.equals("Sick")) return "SICK";
            if (s.equals("Comp Taken")) return "COMP";
            if (s.equals("Court")) return "COURT";
            if (s.equals("Custom Event")) return "EVENT";
            return s;
        }
    }
}
