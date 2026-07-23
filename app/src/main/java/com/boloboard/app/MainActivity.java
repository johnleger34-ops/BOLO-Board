package com.boloboard.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;

import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {

    private static final String PREFS = "bolo_board";
    private static final String[] PATTERNS = {"Alpha", "Bravo", "Charlie", "Delta"};
    private static final String[] ENTRY_TYPES = {
            "Use Schedule", "Day Shift", "Night Shift", "Day Off",
            "Extra Shift - Regular", "Extra Shift - OT Requested",
            "Extra Shift - Comp Requested", "Vacation", "Sick",
            "Comp Taken", "Court", "Custom Event"
    };

    private final Calendar displayedMonth = Calendar.getInstance();
    private SharedPreferences prefs;
    private LinearLayout content;
    private TextView monthTitle;
    private GridLayout calendarGrid;
    private LinearLayout bottomNav;
    private final SimpleDateFormat keyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat displayDate = new SimpleDateFormat("MMM d, yyyy", Locale.US);

    private final int navy = Color.rgb(3, 14, 27);
    private final int panel = Color.rgb(13, 24, 36);
    private final int panel2 = Color.rgb(21, 35, 50);
    private final int gold = Color.rgb(247, 190, 30);
    private final int blue = Color.rgb(24, 124, 255);
    private final int red = Color.rgb(238, 48, 58);
    private final int silver = Color.rgb(214, 220, 228);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(navy);
        getWindow().setNavigationBarColor(navy);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        applyAnnualAccrualIfNeeded();
        showCalendarScreen();
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
        root.setBackgroundColor(navy);

        PoliceHeaderView header = new PoliceHeaderView(this);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(180)));

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
        content.setPadding(dp(12), dp(10), dp(12), dp(18));
        content.setBackgroundColor(navy);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setGravity(Gravity.CENTER);
        bottomNav.setPadding(dp(6), dp(7), dp(6), dp(7));
        bottomNav.setBackground(rounded(Color.rgb(8,18,29), 0, Color.rgb(38,52,68), 1));

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
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(dp(4), dp(6), dp(4), dp(6));
        b.setBackground(rounded(panel2, dp(8), Color.rgb(49,66,84), 1));
        b.setOnClickListener(v -> {
            if (destination.equals("Calendar")) showCalendarScreen();
            else if (destination.equals("Payroll")) showPayrollScreen();
            else if (destination.equals("Leave")) showLeaveScreen();
            else showSettingsScreen();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(66), 1);
        lp.setMargins(dp(3),0,dp(3),0);
        bottomNav.addView(b, lp);
    }

    private TextView section(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(19);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(10),dp(16),dp(10),dp(10));
        return t;
    }

    private EditText input(String label, String value, boolean numeric) {
        EditText e = new EditText(this);
        e.setHint(label);
        e.setHintTextColor(Color.LTGRAY);
        e.setTextColor(Color.WHITE);
        e.setText(value);
        e.setSingleLine(true);
        if (numeric) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        e.setPadding(dp(12),dp(11),dp(12),dp(11));
        e.setBackground(rounded(panel2, dp(8), Color.rgb(62,82,104), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(5),0,dp(5));
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
        b.setPadding(dp(12),dp(15),dp(12),dp(15));
        b.setBackground(rounded(panel2, dp(9), Color.rgb(70,89,110), 1));
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(6),0,dp(6));
        b.setLayoutParams(lp);
        return b;
    }

    private void showCalendarScreen() {
        baseScreen("CALENDAR");

        LinearLayout scheduleRow = new LinearLayout(this);
        scheduleRow.setOrientation(LinearLayout.HORIZONTAL);
        scheduleRow.setGravity(Gravity.CENTER_VERTICAL);
        scheduleRow.setPadding(dp(10),dp(9),dp(10),dp(9));
        scheduleRow.setBackground(rounded(panel, dp(10), Color.rgb(76,91,106), 1));

        LinearLayout patternBox = new LinearLayout(this);
        patternBox.setOrientation(LinearLayout.VERTICAL);
        TextView pSmall = new TextView(this);
        pSmall.setText("YOUR SCHEDULE");
        pSmall.setTextColor(Color.LTGRAY);
        pSmall.setTextSize(10);
        TextView pBig = new TextView(this);
        pBig.setText(prefs.getString("pattern","Alpha").toUpperCase(Locale.US) + "  ▼");
        pBig.setTextColor(Color.WHITE);
        pBig.setTextSize(18);
        pBig.setTypeface(Typeface.DEFAULT_BOLD);
        patternBox.addView(pSmall);
        patternBox.addView(pBig);
        patternBox.setOnClickListener(v -> showSettingsScreen());

        TextView prev = arrowButton("◀", v -> { displayedMonth.add(Calendar.MONTH,-1); renderCalendar(); });
        TextView next = arrowButton("▶", v -> { displayedMonth.add(Calendar.MONTH,1); renderCalendar(); });

        monthTitle = new TextView(this);
        monthTitle.setTextSize(23);
        monthTitle.setTypeface(Typeface.DEFAULT_BOLD);
        monthTitle.setTextColor(Color.WHITE);
        monthTitle.setGravity(Gravity.CENTER);

        scheduleRow.addView(patternBox, new LinearLayout.LayoutParams(dp(112),-2));
        scheduleRow.addView(prev, new LinearLayout.LayoutParams(dp(52),dp(52)));
        scheduleRow.addView(monthTitle, new LinearLayout.LayoutParams(0,-2,1));
        scheduleRow.addView(next, new LinearLayout.LayoutParams(dp(52),dp(52)));
        content.addView(scheduleRow);

        calendarGrid = new GridLayout(this);
        calendarGrid.setColumnCount(7);
        calendarGrid.setBackgroundColor(Color.rgb(39,55,70));
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(-1,-2);
        gridLp.setMargins(0,dp(8),0,dp(8));
        calendarGrid.setLayoutParams(gridLp);
        content.addView(calendarGrid);

        TextView share = action("🚨   SHARE THIS MONTH   🚨", v -> shareMonth());
        share.setBackground(rounded(Color.rgb(15,31,49), dp(9), blue, 2));
        content.addView(share);
        content.addView(action("⌖   JUMP TO TODAY", v -> {
            displayedMonth.setTime(new Date());
            renderCalendar();
        }));

        renderCalendar();
    }

    private TextView arrowButton(String text, View.OnClickListener listener) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(22);
        b.setGravity(Gravity.CENTER);
        b.setBackground(rounded(panel2, dp(8), Color.rgb(92,106,122), 1));
        b.setOnClickListener(listener);
        return b;
    }

    private void renderCalendar() {
        calendarGrid.removeAllViews();
        monthTitle.setText(new SimpleDateFormat("MMMM yyyy", Locale.US).format(displayedMonth.getTime()));

        String[] days = {"SUN","MON","TUE","WED","THU","FRI","SAT"};
        for (String d : days) {
            TextView h = new TextView(this);
            h.setText(d);
            h.setGravity(Gravity.CENTER);
            h.setTextColor(Color.WHITE);
            h.setTextSize(11);
            h.setTypeface(Typeface.DEFAULT_BOLD);
            h.setBackgroundColor(panel);
            h.setPadding(0,dp(8),0,dp(8));
            GridLayout.LayoutParams lp = equalGridParams(dp(38));
            h.setLayoutParams(lp);
            calendarGrid.addView(h);
        }

        Calendar first = (Calendar) displayedMonth.clone();
        first.set(Calendar.DAY_OF_MONTH,1);
        int start = first.get(Calendar.DAY_OF_WEEK)-1;
        int total = first.getActualMaximum(Calendar.DAY_OF_MONTH);

        for (int i=0;i<start;i++) {
            View empty = new View(this);
            empty.setBackgroundColor(Color.rgb(5,13,22));
            empty.setLayoutParams(equalGridParams(dp(82)));
            calendarGrid.addView(empty);
        }

        for (int d=1; d<=total; d++) {
            Calendar date = (Calendar) first.clone();
            date.set(Calendar.DAY_OF_MONTH,d);
            String status = entryFor(date);

            FrameLayout cell = new FrameLayout(this);
            cell.setBackground(rounded(Color.rgb(7,17,27), 0, Color.rgb(56,73,90), 1));
            cell.setPadding(dp(2),dp(2),dp(2),dp(2));
            cell.setLayoutParams(equalGridParams(dp(82)));

            TextView number = new TextView(this);
            number.setText(String.valueOf(d));
            number.setTextColor(Color.WHITE);
            number.setTextSize(13);
            number.setTypeface(Typeface.DEFAULT_BOLD);
            number.setPadding(dp(4),dp(2),0,0);
            FrameLayout.LayoutParams numLp = new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);
            cell.addView(number,numLp);

            ShiftIconView icon = new ShiftIconView(this, status);
            FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(-1,-1);
            iconLp.setMargins(dp(3),dp(15),dp(3),dp(2));
            cell.addView(icon,iconLp);

            cell.setOnClickListener(v -> editDate(date));
            calendarGrid.addView(cell);
        }
    }

    private GridLayout.LayoutParams equalGridParams(int heightDp) {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = dp(heightDp);
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED,1f);
        lp.setMargins(1,1,1,1);
        return lp;
    }

    private void editDate(Calendar date) {
        String key = "entry_" + keyFormat.format(date.getTime());
        int checked = 0;
        String current = prefs.getString(key, "Use Schedule");
        for(int i=0;i<ENTRY_TYPES.length;i++) if(ENTRY_TYPES[i].equals(current)) checked=i;

        new AlertDialog.Builder(this)
                .setTitle(displayDate.format(date.getTime()))
                .setSingleChoiceItems(ENTRY_TYPES, checked, null)
                .setPositiveButton("Save", (dialog, which) -> {
                    AlertDialog ad=(AlertDialog)dialog;
                    int pos=ad.getListView().getCheckedItemPosition();
                    String selected=ENTRY_TYPES[pos];
                    SharedPreferences.Editor ed=prefs.edit();
                    if(selected.equals("Use Schedule")) ed.remove(key);
                    else ed.putString(key,selected);
                    ed.apply();
                    adjustLeaveForEntry(current, selected);
                    renderCalendar();
                })
                .setNegativeButton("Cancel",null)
                .show();
    }

    private void adjustLeaveForEntry(String oldValue, String newValue) {
        if (!oldValue.equals(newValue)) {
            if (oldValue.equals("Vacation")) addBalance("vacation_balance",12);
            if (oldValue.equals("Sick")) addBalance("sick_balance",12);
            if (oldValue.equals("Comp Taken")) addBalance("comp_balance",12);
            if (newValue.equals("Vacation")) addBalance("vacation_balance",-12);
            if (newValue.equals("Sick")) addBalance("sick_balance",-12);
            if (newValue.equals("Comp Taken")) addBalance("comp_balance",-12);
        }
    }

    private String entryFor(Calendar date) {
        String override = prefs.getString("entry_" + keyFormat.format(date.getTime()), null);
        return override != null ? override : scheduledFor(date);
    }

    private String scheduledFor(Calendar date) {
        String pattern = prefs.getString("pattern","Alpha");
        long anchor = prefs.getLong("anchor", defaultAnchor());
        long days = daysBetween(anchor, date.getTimeInMillis());
        int cycleDay = floorMod((int)days,14);
        boolean alphaWorks = cycleDay==0 || cycleDay==1 || cycleDay==4 || cycleDay==5 || cycleDay==6 ||
                cycleDay==9 || cycleDay==10;
        boolean works = (pattern.equals("Alpha") || pattern.equals("Charlie")) ? alphaWorks : !alphaWorks;
        if (!works) return "Day Off";
        if (pattern.equals("Charlie") || pattern.equals("Delta")) {
            int payPeriod = Math.floorDiv((int)days,14);
            return floorMod(payPeriod,2)==0 ? "Night Shift" : "Day Shift";
        }
        return "Day Shift";
    }

    private long defaultAnchor() {
        Calendar c=Calendar.getInstance();
        c.set(2026, Calendar.JULY,13,0,0,0);
        c.set(Calendar.MILLISECOND,0);
        return c.getTimeInMillis();
    }

    private long daysBetween(long start, long end) {
        Calendar a=Calendar.getInstance(); a.setTimeInMillis(start); zeroTime(a);
        Calendar b=Calendar.getInstance(); b.setTimeInMillis(end); zeroTime(b);
        return (b.getTimeInMillis()-a.getTimeInMillis())/86400000L;
    }

    private void zeroTime(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);
    }

    private int floorMod(int a, int b) {
        int r=a%b; return r<0?r+b:r;
    }

    private void shareMonth() {
        Calendar first=(Calendar)displayedMonth.clone(); first.set(Calendar.DAY_OF_MONTH,1);
        int total=first.getActualMaximum(Calendar.DAY_OF_MONTH);
        StringBuilder sb=new StringBuilder();
        sb.append(prefs.getString("officer_name","BOLO Board"))
                .append(" — ").append(new SimpleDateFormat("MMMM yyyy",Locale.US).format(first.getTime())).append("\n\n");
        for(int d=1;d<=total;d++){
            first.set(Calendar.DAY_OF_MONTH,d);
            sb.append(new SimpleDateFormat("EEE MMM d",Locale.US).format(first.getTime()))
                    .append(": ").append(entryFor(first)).append("\n");
        }
        Intent send=new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT,"BOLO Board Monthly Schedule");
        send.putExtra(Intent.EXTRA_TEXT,sb.toString());
        startActivity(Intent.createChooser(send,"Share monthly schedule"));
    }

    private void showPayrollScreen() {
        baseScreen("PAYROLL UNIT");

        Calendar start=Calendar.getInstance();
        start.setTimeInMillis(prefs.getLong("anchor",defaultAnchor()));
        Calendar now=Calendar.getInstance();
        long diff=daysBetween(start.getTimeInMillis(),now.getTimeInMillis());
        int periods=Math.floorDiv((int)diff,14);
        start.add(Calendar.DAY_OF_MONTH, periods*14);
        Calendar end=(Calendar)start.clone(); end.add(Calendar.DAY_OF_MONTH,13);

        content.addView(section("CURRENT PAY PERIOD\n"+displayDate.format(start.getTime())+" – "+displayDate.format(end.getTime())));

        double worked=0, paidLeave=0, straightExtra=0, otRequested=0, compRequested=0;
        int subpoenas=0;
        Calendar cursor=(Calendar)start.clone();
        for(int i=0;i<14;i++){
            String e=entryFor(cursor);
            if(e.equals("Day Shift")||e.equals("Night Shift")) worked+=12;
            else if(e.equals("Extra Shift - Regular")) straightExtra+=12;
            else if(e.equals("Extra Shift - OT Requested")) otRequested+=12;
            else if(e.equals("Extra Shift - Comp Requested")) compRequested+=12;
            else if(e.equals("Vacation")||e.equals("Sick")||e.equals("Comp Taken")) paidLeave+=12;
            else if(e.equals("Court")) subpoenas++;
            cursor.add(Calendar.DAY_OF_MONTH,1);
        }

        double actualWorked=worked+straightExtra+otRequested+compRequested;
        double overtime=Math.max(0,actualWorked-84);
        double regularWorked=actualWorked-overtime;
        double hourly=getDouble("hourly_rate",0);
        double regularPay=(regularWorked+paidLeave)*hourly;
        double overtimePay=overtime*hourly*1.5;
        double courtPay=Math.min(3,subpoenas)*50.0;
        int paycheckIndex=paycheckIndexFor(start);
        double supplement=paycheckIndex<=2?getDouble("supplement",0):0;
        double gross=regularPay+overtimePay+courtPay+supplement;

        double beforeComp=Math.max(0,84-(worked+straightExtra+otRequested));
        double straightComp=Math.min(compRequested,beforeComp);
        double overtimeComp=Math.max(0,compRequested-straightComp);
        final double compEarned=straightComp+(overtimeComp*1.5);

        content.addView(summaryCard("ACTUAL WORKED", actualWorked+" hrs", "⚡"));
        content.addView(summaryCard("PAID LEAVE", paidLeave+" hrs", "☂"));
        content.addView(summaryCard("OVERTIME", overtime+" hrs", "⏱"));
        content.addView(summaryCard("COMP EARNED", compEarned+" hrs", "★"));
        content.addView(summaryCard("COURT PAY", money(courtPay), "⚖"));
        content.addView(summaryCard("STATE SUPPLEMENT", money(supplement), "✦"));
        content.addView(summaryCard("ESTIMATED GROSS", money(gross), "$"));

        if(compEarned>0) {
            content.addView(action("ADD EARNED COMP TO BANK",v->{
                addBalance("comp_balance",compEarned);
                Toast.makeText(this,"Comp bank updated",Toast.LENGTH_SHORT).show();
                showPayrollScreen();
            }));
        }

        final double actualWorkedFinal = actualWorked;
        final double paidLeaveFinal = paidLeave;
        final double overtimeFinal = overtime;
        final double courtPayFinal = courtPay;
        final double supplementFinal = supplement;
        final double grossFinal = gross;
        final Calendar startFinal = (Calendar) start.clone();
        final Calendar endFinal = (Calendar) end.clone();

        content.addView(action("SHARE PAYCHECK SUMMARY",v->{
            String text="BOLO Board Paycheck Estimate\n"+
                    displayDate.format(startFinal.getTime())+" – "+displayDate.format(endFinal.getTime())+"\n"+
                    "Actual worked: "+actualWorkedFinal+" hrs\nPaid leave: "+paidLeaveFinal+" hrs\n"+
                    "Overtime: "+overtimeFinal+" hrs\nCourt: "+money(courtPayFinal)+"\n"+
                    "Supplement: "+money(supplementFinal)+"\nEstimated gross: "+money(grossFinal);
            Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT,text);startActivity(Intent.createChooser(send,"Share paycheck summary"));
        }));
    }

    private LinearLayout summaryCard(String label, String value, String icon) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14),dp(13),dp(14),dp(13));
        card.setBackground(rounded(panel, dp(9), Color.rgb(56,75,94), 1));
        TextView i = new TextView(this); i.setText(icon); i.setTextSize(28); i.setTextColor(gold); i.setGravity(Gravity.CENTER);
        LinearLayout words = new LinearLayout(this); words.setOrientation(LinearLayout.VERTICAL); words.setPadding(dp(12),0,0,0);
        TextView l = new TextView(this); l.setText(label); l.setTextColor(Color.LTGRAY); l.setTextSize(12);
        TextView v = new TextView(this); v.setText(value); v.setTextColor(Color.WHITE); v.setTextSize(21); v.setTypeface(Typeface.DEFAULT_BOLD);
        words.addView(l); words.addView(v);
        card.addView(i,new LinearLayout.LayoutParams(dp(46),-1));
        card.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,dp(5),0,dp(5)); card.setLayoutParams(lp);
        return card;
    }

    private int paycheckIndexFor(Calendar periodStart) {
        Calendar pay=(Calendar)periodStart.clone(); pay.add(Calendar.DAY_OF_MONTH,15);
        return pay.get(Calendar.DAY_OF_MONTH)<=15?1:2;
    }

    private String money(double v) {
        return String.format(Locale.US,"$%,.2f",v);
    }

    private void showLeaveScreen() {
        baseScreen("LEAVE BANKS");
        content.addView(summaryCard("VACATION",formatHours(getDouble("vacation_balance",0)),"☀"));
        content.addView(summaryCard("SICK",formatHours(getDouble("sick_balance",0)),"✚"));
        content.addView(summaryCard("COMP",formatHours(getDouble("comp_balance",0)),"★"));

        content.addView(section("MANUAL ADJUSTMENT"));
        Spinner bank=new Spinner(this);
        bank.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Vacation","Sick","Comp"}));
        EditText amount=input("Hours to add (negative subtracts)","",true);
        content.addView(bank); content.addView(amount);
        content.addView(action("APPLY ADJUSTMENT",v->{
            double h=parse(amount.getText().toString());
            String key=bank.getSelectedItemPosition()==0?"vacation_balance":
                    bank.getSelectedItemPosition()==1?"sick_balance":"comp_balance";
            addBalance(key,h);
            showLeaveScreen();
        }));

        content.addView(section("JANUARY 1 ACCRUAL"));
        content.addView(summaryCard("VACATION ACCRUAL",formatHours(getDouble("vacation_accrual",0)),"+"));
        content.addView(summaryCard("SICK ACCRUAL",formatHours(getDouble("sick_accrual",0)),"+"));
    }

    private String formatHours(double h){return String.format(Locale.US,"%.1f hrs",h);}

    private void showSettingsScreen() {
        baseScreen("COMMAND SETTINGS");

        EditText name=input("Officer/profile name",prefs.getString("officer_name",""),false);
        Spinner pattern=new Spinner(this);
        pattern.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,PATTERNS));
        String current=prefs.getString("pattern","Alpha");
        for(int i=0;i<PATTERNS.length;i++)if(PATTERNS[i].equals(current))pattern.setSelection(i);

        TextView anchor=action("PATTERN ANCHOR: "+displayDate.format(new Date(prefs.getLong("anchor",defaultAnchor()))),null);
        final long[] selectedAnchor={prefs.getLong("anchor",defaultAnchor())};
        anchor.setOnClickListener(v->{
            Calendar c=Calendar.getInstance();c.setTimeInMillis(selectedAnchor[0]);
            new DatePickerDialog(this,(view,y,m,d)->{
                Calendar chosen=Calendar.getInstance();chosen.set(y,m,d,0,0,0);chosen.set(Calendar.MILLISECOND,0);
                selectedAnchor[0]=chosen.getTimeInMillis();
                anchor.setText("PATTERN ANCHOR: "+displayDate.format(chosen.getTime()));
            },c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();
        });

        EditText hourly=input("Hourly rate",String.valueOf(getDouble("hourly_rate",0)),true);
        EditText supplement=input("State supplemental pay per eligible check",String.valueOf(getDouble("supplement",0)),true);
        EditText vacation=input("Vacation starting balance",String.valueOf(getDouble("vacation_balance",0)),true);
        EditText sick=input("Sick starting balance",String.valueOf(getDouble("sick_balance",0)),true);
        EditText comp=input("Comp starting balance",String.valueOf(getDouble("comp_balance",0)),true);
        EditText vacAcc=input("Vacation hours added each January 1",String.valueOf(getDouble("vacation_accrual",0)),true);
        EditText sickAcc=input("Sick hours added each January 1",String.valueOf(getDouble("sick_accrual",0)),true);

        content.addView(name);content.addView(pattern);content.addView(anchor);
        content.addView(hourly);content.addView(supplement);
        content.addView(vacation);content.addView(sick);content.addView(comp);
        content.addView(vacAcc);content.addView(sickAcc);

        content.addView(action("SAVE SETTINGS",v->{
            prefs.edit()
                    .putString("officer_name",name.getText().toString().trim())
                    .putString("pattern",PATTERNS[pattern.getSelectedItemPosition()])
                    .putLong("anchor",selectedAnchor[0])
                    .putString("hourly_rate",hourly.getText().toString())
                    .putString("supplement",supplement.getText().toString())
                    .putString("vacation_balance",vacation.getText().toString())
                    .putString("sick_balance",sick.getText().toString())
                    .putString("comp_balance",comp.getText().toString())
                    .putString("vacation_accrual",vacAcc.getText().toString())
                    .putString("sick_accrual",sickAcc.getText().toString())
                    .apply();
            Toast.makeText(this,"Settings saved",Toast.LENGTH_SHORT).show();
            showCalendarScreen();
        }));

        content.addView(action("CLEAR ALL MANUAL CALENDAR ENTRIES",v->
                new AlertDialog.Builder(this).setTitle("Clear overrides?")
                        .setMessage("Schedule settings and leave balances remain.")
                        .setPositiveButton("Clear",(d,w)->{
                            SharedPreferences.Editor ed=prefs.edit();
                            for(String key:prefs.getAll().keySet()) if(key.startsWith("entry_")) ed.remove(key);
                            ed.apply();Toast.makeText(this,"Calendar overrides cleared",Toast.LENGTH_SHORT).show();
                        }).setNegativeButton("Cancel",null).show()
        ));
    }

    private void addBalance(String key,double amount) {
        double current=getDouble(key,0);
        prefs.edit().putString(key,String.valueOf(current+amount)).apply();
    }

    private double getDouble(String key,double fallback) {
        try{return Double.parseDouble(prefs.getString(key,String.valueOf(fallback)));}
        catch(Exception e){return fallback;}
    }

    private double parse(String value) {
        try{return Double.parseDouble(value.trim());}catch(Exception e){return 0;}
    }

    private void applyAnnualAccrualIfNeeded() {
        Calendar now=Calendar.getInstance();
        int year=now.get(Calendar.YEAR);
        int applied=prefs.getInt("accrual_applied_year",0);
        if(applied<year) {
            addBalance("vacation_balance",getDouble("vacation_accrual",0));
            addBalance("sick_balance",getDouble("sick_accrual",0));
            prefs.edit().putInt("accrual_applied_year",year).apply();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private class PoliceHeaderView extends View {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        public PoliceHeaderView(Activity c) { super(c); }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            int w=getWidth(), h=getHeight();
            LinearGradient bg=new LinearGradient(0,0,0,h,Color.rgb(4,10,18),Color.rgb(7,24,42),Shader.TileMode.CLAMP);
            p.setShader(bg); c.drawRect(0,0,w,h,p); p.setShader(null);

            // city lights
            p.setColor(Color.rgb(18,32,49));
            for(int x=0;x<w;x+=dp(38)) {
                int bh=dp(45)+(x%dp(70));
                c.drawRect(x,h-bh,x+dp(28),h,p);
                p.setColor(Color.rgb(150,112,35));
                c.drawRect(x+dp(6),h-bh+dp(10),x+dp(10),h-bh+dp(14),p);
                p.setColor(Color.rgb(18,32,49));
            }

            // police car
            float cy=h-dp(30);
            p.setColor(Color.rgb(8,12,17));
            RectF body=new RectF(w*.18f,cy-dp(45),w*.82f,cy+dp(16));
            c.drawRoundRect(body,dp(16),dp(16),p);
            p.setColor(Color.rgb(16,25,34));
            Path roof=new Path(); roof.moveTo(w*.34f,cy-dp(45)); roof.lineTo(w*.43f,cy-dp(75)); roof.lineTo(w*.64f,cy-dp(75)); roof.lineTo(w*.72f,cy-dp(45)); roof.close(); c.drawPath(roof,p);
            p.setColor(Color.WHITE); c.drawCircle(w*.24f,cy,dp(7),p); c.drawCircle(w*.76f,cy,dp(7),p);
            p.setColor(blue); c.drawRect(w*.46f,cy-dp(82),w*.53f,cy-dp(72),p);
            p.setColor(red); c.drawRect(w*.54f,cy-dp(82),w*.61f,cy-dp(72),p);

            // title
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            p.setTextSize(dp(33));
            p.setColor(Color.rgb(35,39,44));
            c.drawText("BOLO BOARD",w/2f+dp(2),dp(54)+dp(2),p);
            p.setColor(Color.WHITE);
            c.drawText("BOLO BOARD",w/2f,dp(54),p);

            // light glow bars
            p.setColor(blue); c.drawRect(w*.35f,dp(72),w*.49f,dp(77),p);
            p.setColor(red); c.drawRect(w*.51f,dp(72),w*.65f,dp(77),p);
        }
    }

    private class ShiftIconView extends View {
        private final String status;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        ShiftIconView(Activity c, String status) { super(c); this.status=status; }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w=getWidth(), h=getHeight(), cx=w/2f;
            if (status.equals("Day Shift")) drawSunOfficer(c,cx,h*.42f,Math.min(w,h)*.27f);
            else if (status.equals("Night Shift")) drawMoonOfficer(c,cx,h*.42f,Math.min(w,h)*.27f);
            else if (status.equals("Vacation")) drawVacationCar(c,cx,h*.40f,Math.min(w,h)*.28f);
            else if (status.equals("Day Off")) drawOffBadge(c,cx,h*.42f,Math.min(w,h)*.24f);
            else drawSpecial(c,cx,h*.42f,Math.min(w,h)*.25f,status);

            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(dp(9));
            p.setColor(labelColor(status));
            c.drawText(shortLabel(status),cx,h-dp(3),p);
        }

        private void drawSunOfficer(Canvas c,float x,float y,float r) {
            p.setColor(gold);
            for(int i=0;i<12;i++){
                double a=i*Math.PI/6;
                float x1=(float)(x+Math.cos(a)*r*1.05), y1=(float)(y+Math.sin(a)*r*1.05);
                float x2=(float)(x+Math.cos(a)*r*1.35), y2=(float)(y+Math.sin(a)*r*1.35);
                p.setStrokeWidth(dp(3)); c.drawLine(x1,y1,x2,y2,p);
            }
            c.drawCircle(x,y,r,p);
            drawFace(c,x,y,r,true);
            drawHat(c,x,y-r*.78f,r);
        }

        private void drawMoonOfficer(Canvas c,float x,float y,float r) {
            p.setColor(Color.rgb(176,188,208)); c.drawCircle(x,y,r,p);
            p.setColor(Color.rgb(7,17,27)); c.drawCircle(x+r*.38f,y-r*.08f,r*.88f,p);
            p.setColor(Color.rgb(139,157,188)); c.drawCircle(x-r*.38f,y-r*.3f,r*.10f,p); c.drawCircle(x-r*.48f,y+r*.22f,r*.07f,p);
            drawFace(c,x-r*.18f,y,r*.78f,false);
            drawHat(c,x-r*.10f,y-r*.76f,r*.9f);
        }

        private void drawFace(Canvas c,float x,float y,float r,boolean shades) {
            if(shades){
                p.setColor(Color.BLACK);
                c.drawRoundRect(new RectF(x-r*.72f,y-r*.25f,x-r*.08f,y+r*.05f),r*.1f,r*.1f,p);
                c.drawRoundRect(new RectF(x+r*.08f,y-r*.25f,x+r*.72f,y+r*.05f),r*.1f,r*.1f,p);
                c.drawRect(x-r*.08f,y-r*.16f,x+r*.08f,y-r*.10f,p);
            } else {
                p.setColor(Color.WHITE); c.drawCircle(x-r*.28f,y-r*.08f,r*.13f,p); c.drawCircle(x+r*.28f,y-r*.08f,r*.13f,p);
                p.setColor(Color.BLACK); c.drawCircle(x-r*.25f,y-r*.06f,r*.06f,p); c.drawCircle(x+r*.25f,y-r*.06f,r*.06f,p);
            }
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2)); p.setColor(Color.BLACK);
            RectF smile=new RectF(x-r*.42f,y-r*.05f,x+r*.42f,y+r*.50f); c.drawArc(smile,20,140,false,p);
            p.setStyle(Paint.Style.FILL);
        }

        private void drawHat(Canvas c,float x,float y,float r) {
            p.setColor(Color.rgb(8,29,60));
            RectF crown=new RectF(x-r*.55f,y-r*.28f,x+r*.55f,y+r*.26f);
            c.drawRoundRect(crown,r*.18f,r*.18f,p);
            c.drawOval(new RectF(x-r*.76f,y+r*.14f,x+r*.76f,y+r*.42f),p);
            p.setColor(gold); c.drawCircle(x,y,r*.14f,p);
            p.setColor(Color.WHITE); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(r*.18f); p.setTypeface(Typeface.DEFAULT_BOLD); c.drawText("★",x,y+r*.07f,p);
        }

        private void drawVacationCar(Canvas c,float x,float y,float r) {
            p.setColor(Color.rgb(220,224,230));
            c.drawRoundRect(new RectF(x-r,y-r*.35f,x+r,y+r*.35f),r*.18f,r*.18f,p);
            p.setColor(Color.rgb(18,25,35));
            Path roof=new Path(); roof.moveTo(x-r*.45f,y-r*.35f); roof.lineTo(x-r*.2f,y-r*.75f); roof.lineTo(x+r*.45f,y-r*.75f); roof.lineTo(x+r*.7f,y-r*.35f); roof.close(); c.drawPath(roof,p);
            p.setColor(Color.BLACK); c.drawCircle(x-r*.58f,y+r*.34f,r*.22f,p); c.drawCircle(x+r*.58f,y+r*.34f,r*.22f,p);
            p.setColor(blue); c.drawRect(x-r*.12f,y-r*.88f,x,y-r*.73f,p);
            p.setColor(red); c.drawRect(x,y-r*.88f,x+r*.12f,y-r*.73f,p);
            p.setColor(Color.rgb(255,225,90));
            RectF note=new RectF(x-r*.30f,y-r*.10f,x+r*.70f,y+r*.72f);
            c.save(); c.rotate(-8,x+r*.2f,y+r*.3f); c.drawRect(note,p);
            p.setColor(Color.BLACK); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(r*.18f);
            c.drawText("ON",x+r*.2f,y+r*.17f,p); c.drawText("VACATION",x+r*.2f,y+r*.40f,p); c.restore();
        }

        private void drawOffBadge(Canvas c,float x,float y,float r) {
            p.setColor(Color.rgb(62,72,84));
            Path star=new Path();
            for(int i=0;i<16;i++){
                double a=-Math.PI/2+i*Math.PI/8;
                float rr=(i%2==0)?r:r*.68f;
                float px=(float)(x+Math.cos(a)*rr), py=(float)(y+Math.sin(a)*rr);
                if(i==0)star.moveTo(px,py); else star.lineTo(px,py);
            }
            star.close(); c.drawPath(star,p);
            p.setColor(Color.LTGRAY); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(r*.42f); c.drawText("OFF",x,y+r*.14f,p);
        }

        private void drawSpecial(Canvas c,float x,float y,float r,String s) {
            p.setColor(s.contains("Court")?Color.rgb(255,145,30):s.contains("Sick")?red:Color.rgb(122,81,255));
            c.drawCircle(x,y,r,p);
            p.setColor(Color.WHITE); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(r*.72f);
            String symbol=s.contains("Court")?"⚖":s.contains("Sick")?"+":s.contains("Extra")?"★":"C";
            c.drawText(symbol,x,y+r*.25f,p);
        }

        private int labelColor(String s) {
            if(s.equals("Day Shift")) return gold;
            if(s.equals("Night Shift")) return Color.rgb(55,155,255);
            if(s.equals("Vacation")) return Color.rgb(207,102,255);
            if(s.equals("Day Off")) return Color.LTGRAY;
            return Color.WHITE;
        }

        private String shortLabel(String s) {
            if (s.equals("Day Shift")) return "DAY";
            if (s.equals("Night Shift")) return "NIGHT";
            if (s.equals("Day Off")) return "OFF";
            if (s.contains("OT Requested")) return "OT";
            if (s.contains("Comp Requested")) return "COMP+";
            if (s.contains("Regular")) return "EXTRA";
            if (s.equals("Vacation")) return "VACATION";
            if (s.equals("Sick")) return "SICK";
            if (s.equals("Comp Taken")) return "COMP";
            if (s.equals("Court")) return "COURT";
            if (s.equals("Custom Event")) return "EVENT";
            return s;
        }
    }
}
