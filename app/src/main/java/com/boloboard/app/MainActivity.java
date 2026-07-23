package com.boloboard.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import org.json.JSONObject;

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
    private final SimpleDateFormat keyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat displayDate = new SimpleDateFormat("MMM d, yyyy", Locale.US);

    private int navy = Color.rgb(7,24,44);
    private int gold = Color.rgb(245,184,0);
    private int blue = Color.rgb(45,125,246);
    private int light = Color.rgb(238,244,251);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        applyAnnualAccrualIfNeeded();
        showCalendarScreen();
    }

    private void baseScreen(String title) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(light);

        TextView header = new TextView(this);
        header.setText("★  BOLO BOARD  ★\n" + title);
        header.setTextColor(Color.WHITE);
        header.setTextSize(20);
        header.setGravity(Gravity.CENTER);
        header.setPadding(18, 24, 18, 24);
        header.setBackgroundColor(navy);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(18,18,18,18);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(navy);
        String[] labels = {"Calendar","Payroll","Leave","Settings"};
        for (String label : labels) {
            Button b = new Button(this);
            b.setText(label);
            b.setTextSize(12);
            b.setOnClickListener(v -> {
                if (label.equals("Calendar")) showCalendarScreen();
                else if (label.equals("Payroll")) showPayrollScreen();
                else if (label.equals("Leave")) showLeaveScreen();
                else showSettingsScreen();
            });
            nav.addView(b, new LinearLayout.LayoutParams(0,-2,1));
        }
        root.addView(nav);
        setContentView(root);
    }

    private TextView section(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(navy);
        t.setTextSize(19);
        t.setPadding(0,18,0,10);
        return t;
    }

    private EditText input(String label, String value, boolean numeric) {
        EditText e = new EditText(this);
        e.setHint(label);
        e.setText(value);
        if (numeric) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setPadding(12,8,12,8);
        return e;
    }

    private Button action(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(listener);
        return b;
    }

    private void showCalendarScreen() {
        baseScreen("DISPATCH CALENDAR");

        LinearLayout monthNav = new LinearLayout(this);
        monthNav.setGravity(Gravity.CENTER);
        Button prev = action("◀", v -> { displayedMonth.add(Calendar.MONTH,-1); renderCalendar(); });
        Button next = action("▶", v -> { displayedMonth.add(Calendar.MONTH,1); renderCalendar(); });
        monthTitle = new TextView(this);
        monthTitle.setTextSize(20);
        monthTitle.setTextColor(navy);
        monthTitle.setGravity(Gravity.CENTER);
        monthNav.addView(prev);
        monthNav.addView(monthTitle, new LinearLayout.LayoutParams(0,-2,1));
        monthNav.addView(next);
        content.addView(monthNav);

        calendarGrid = new GridLayout(this);
        calendarGrid.setColumnCount(7);
        content.addView(calendarGrid);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.addView(action("Share This Month", v -> shareMonth()));
        actions.addView(action("Jump to Today", v -> {
            displayedMonth.setTime(new Date());
            renderCalendar();
        }));
        content.addView(actions);
        renderCalendar();
    }

    private void renderCalendar() {
        calendarGrid.removeAllViews();
        monthTitle.setText(new SimpleDateFormat("MMMM yyyy", Locale.US).format(displayedMonth.getTime()));
        String[] days = {"Sun","Mon","Tue","Wed","Thu","Fri","Sat"};
        for (String d : days) {
            TextView h = dayCell(d, Color.WHITE, navy);
            h.setTextSize(12);
            calendarGrid.addView(h);
        }

        Calendar first = (Calendar) displayedMonth.clone();
        first.set(Calendar.DAY_OF_MONTH,1);
        int start = first.get(Calendar.DAY_OF_WEEK)-1;
        int total = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int i=0;i<start;i++) calendarGrid.addView(dayCell("", light, light));

        for (int d=1; d<=total; d++) {
            Calendar date = (Calendar) first.clone();
            date.set(Calendar.DAY_OF_MONTH,d);
            String status = entryFor(date);
            String shortStatus = shortLabel(status);
            TextView cell = dayCell(d + "\n" + shortStatus, colorFor(status), Color.WHITE);
            cell.setOnClickListener(v -> editDate(date));
            calendarGrid.addView(cell);
        }
    }

    private TextView dayCell(String text, int background, int foreground) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(foreground);
        t.setBackgroundColor(background);
        t.setPadding(3,10,3,10);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED,1f);
        lp.setMargins(2,2,2,2);
        t.setLayoutParams(lp);
        return t;
    }

    private int colorFor(String status) {
        if (status.contains("Night")) return Color.rgb(72,61,139);
        if (status.contains("Day Shift")) return blue;
        if (status.contains("Vacation")) return Color.rgb(0,128,96);
        if (status.contains("Sick")) return Color.rgb(183,28,28);
        if (status.contains("Comp")) return Color.rgb(124,77,255);
        if (status.contains("Court")) return Color.rgb(255,140,0);
        if (status.contains("Extra")) return Color.rgb(0,137,123);
        if (status.contains("Custom")) return Color.DKGRAY;
        return Color.GRAY;
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
        if (override != null) return override;
        return scheduledFor(date);
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

        TextView period=section("Current Pay Period\n"+displayDate.format(start.getTime())+" – "+displayDate.format(end.getTime()));
        content.addView(period);

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

        double compEarned=0;
        double beforeComp=Math.max(0,84-(worked+straightExtra+otRequested));
        double straightComp=Math.min(compRequested,beforeComp);
        double overtimeComp=Math.max(0,compRequested-straightComp);
        compEarned=straightComp+(overtimeComp*1.5);

        content.addView(summaryLine("Scheduled/actual worked", actualWorked+" hrs"));
        content.addView(summaryLine("Paid leave", paidLeave+" hrs"));
        content.addView(summaryLine("Regular paid hours", (regularWorked+paidLeave)+" hrs"));
        content.addView(summaryLine("Overtime hours", overtime+" hrs"));
        content.addView(summaryLine("Comp earned", compEarned+" hrs"));
        content.addView(summaryLine("Court pay", money(courtPay)));
        content.addView(summaryLine("State supplement", money(supplement)));
        content.addView(summaryLine("Estimated gross", money(gross)));

        if(compEarned>0) {
            final double earned=compEarned;
            content.addView(action("Add Earned Comp to Bank",v->{
                addBalance("comp_balance",earned);
                Toast.makeText(this,"Comp bank updated",Toast.LENGTH_SHORT).show();
                showPayrollScreen();
            }));
        }

        content.addView(action("Share Paycheck Summary",v->{
            String text="BOLO Board Paycheck Estimate\n"+
                    displayDate.format(start.getTime())+" – "+displayDate.format(end.getTime())+"\n"+
                    "Actual worked: "+actualWorked+" hrs\nPaid leave: "+paidLeave+" hrs\n"+
                    "Overtime: "+overtime+" hrs\nCourt: "+money(courtPay)+"\n"+
                    "Supplement: "+money(supplement)+"\nEstimated gross: "+money(gross);
            Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT,text);startActivity(Intent.createChooser(send,"Share paycheck summary"));
        }));
    }

    private int paycheckIndexFor(Calendar periodStart) {
        Calendar pay=(Calendar)periodStart.clone(); pay.add(Calendar.DAY_OF_MONTH,15);
        int day=pay.get(Calendar.DAY_OF_MONTH);
        return day<=15?1:2;
    }

    private TextView summaryLine(String label, String value) {
        TextView t=new TextView(this);
        t.setText(label+":  "+value);
        t.setTextSize(17);t.setTextColor(navy);t.setPadding(8,10,8,10);
        return t;
    }

    private String money(double v) {
        return String.format(Locale.US,"$%,.2f",v);
    }

    private void showLeaveScreen() {
        baseScreen("LEAVE BANKS");
        content.addView(section("Current Balances"));
        content.addView(summaryLine("Vacation",formatHours(getDouble("vacation_balance",0))));
        content.addView(summaryLine("Sick",formatHours(getDouble("sick_balance",0))));
        content.addView(summaryLine("Comp",formatHours(getDouble("comp_balance",0))));

        content.addView(section("Manual Adjustment"));
        Spinner bank=new Spinner(this);
        bank.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Vacation","Sick","Comp"}));
        EditText amount=input("Hours to add (use negative to subtract)","",true);
        content.addView(bank); content.addView(amount);
        content.addView(action("Apply Adjustment",v->{
            double h=parse(amount.getText().toString());
            String key=bank.getSelectedItemPosition()==0?"vacation_balance":
                    bank.getSelectedItemPosition()==1?"sick_balance":"comp_balance";
            addBalance(key,h);
            showLeaveScreen();
        }));

        content.addView(section("Annual January 1 Accrual"));
        content.addView(summaryLine("Vacation accrual",formatHours(getDouble("vacation_accrual",0))));
        content.addView(summaryLine("Sick accrual",formatHours(getDouble("sick_accrual",0))));
    }

    private String formatHours(double h){return String.format(Locale.US,"%.1f hrs",h);}

    private void showSettingsScreen() {
        baseScreen("COMMAND SETTINGS");

        EditText name=input("Officer/profile name",prefs.getString("officer_name",""),false);
        Spinner pattern=new Spinner(this);
        pattern.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,PATTERNS));
        String current=prefs.getString("pattern","Alpha");
        for(int i=0;i<PATTERNS.length;i++)if(PATTERNS[i].equals(current))pattern.setSelection(i);

        Button anchor=action("Pattern Anchor: "+displayDate.format(new Date(prefs.getLong("anchor",defaultAnchor()))),null);
        final long[] selectedAnchor={prefs.getLong("anchor",defaultAnchor())};
        anchor.setOnClickListener(v->{
            Calendar c=Calendar.getInstance();c.setTimeInMillis(selectedAnchor[0]);
            new DatePickerDialog(this,(view,y,m,d)->{
                Calendar chosen=Calendar.getInstance();chosen.set(y,m,d,0,0,0);chosen.set(Calendar.MILLISECOND,0);
                selectedAnchor[0]=chosen.getTimeInMillis();
                anchor.setText("Pattern Anchor: "+displayDate.format(chosen.getTime()));
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

        content.addView(action("Save Settings",v->{
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

        content.addView(action("Clear All Manual Calendar Entries",v->
                new AlertDialog.Builder(this).setTitle("Clear overrides?")
                        .setMessage("Scheduled pattern settings and balances will remain.")
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
        if(applied<year && now.get(Calendar.DAY_OF_YEAR)>=1) {
            addBalance("vacation_balance",getDouble("vacation_accrual",0));
            addBalance("sick_balance",getDouble("sick_accrual",0));
            prefs.edit().putInt("accrual_applied_year",year).apply();
        }
    }
}
