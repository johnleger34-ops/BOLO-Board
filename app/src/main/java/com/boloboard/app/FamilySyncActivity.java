package com.boloboard.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class FamilySyncActivity extends Activity {
    private final int navy = Color.rgb(3, 12, 23);
    private final int panel = Color.rgb(13, 25, 39);
    private final int panel2 = Color.rgb(22, 38, 55);
    private final int gold = Color.rgb(246, 189, 31);
    private final int blue = Color.rgb(31, 124, 255);
    private final int silver = Color.rgb(221, 227, 234);
    private final int muted = Color.rgb(155, 169, 184);

    private BoloBoardApp app;
    private SharedPreferences global;
    private LinearLayout content;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(navy);
        getWindow().setNavigationBarColor(navy);
        app = (BoloBoardApp) getApplication();
        global = getSharedPreferences(BoloBoardApp.GLOBAL, MODE_PRIVATE);
        render();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(navy);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(18), dp(16), dp(30));
        scroll.addView(content);

        TextView title = text("BOLO FAMILY SYNC", 27, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        content.addView(title);
        TextView subtitle = text("John + Alexis • one encrypted shared board", 14, gold, true);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(2), 0, dp(18));
        content.addView(subtitle);

        LinearLayout statusCard = card();
        statusCard.addView(text("CONNECTION STATUS", 12, muted, true));
        statusCard.addView(text(app.statusText(), 18, Color.WHITE, true));
        TextView safety = text("Your existing John and Alexis data stays on this phone. Before a join or remote replacement, BOLO Board saves a local safety snapshot you can restore.", 13, silver, false);
        safety.setPadding(0, dp(10), 0, 0);
        statusCard.addView(safety);
        content.addView(statusCard);

        if (!app.isConfigured()) {
            content.addView(section("START ON THE PHONE THAT HAS THE DATA YOU WANT TO KEEP"));
            TextView explanation = text("Create the family sync on the phone with the most complete BOLO Board first. Then share its pairing code with the other phone and choose Join there.", 14, silver, false);
            explanation.setPadding(dp(4), 0, dp(4), dp(8));
            content.addView(explanation);
            content.addView(button("CREATE FAMILY SYNC", blue, v -> confirmCreate()));
            content.addView(button("JOIN WITH PAIRING CODE", panel2, v -> showJoinDialog()));
        } else {
            content.addView(section("CONNECTED FAMILY BOARD"));
            EditText code = new EditText(this);
            code.setText(app.getPairingCode());
            code.setTextColor(Color.WHITE);
            code.setTextSize(12);
            code.setSelectAllOnFocus(true);
            code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            code.setPadding(dp(12), dp(12), dp(12), dp(12));
            code.setBackground(rounded(panel2, dp(10), Color.rgb(66, 88, 111), 2));
            content.addView(code, new LinearLayout.LayoutParams(-1, -2));
            TextView warning = text("Treat the pairing code like a password. It contains the key that decrypts your shared BOLO Board.", 12, muted, false);
            warning.setPadding(dp(3), dp(6), dp(3), dp(7));
            content.addView(warning);
            content.addView(button("SHARE PAIRING CODE", blue, v -> sharePairingCode()));
            content.addView(button("SYNC NOW", panel2, v -> syncNow()));
            content.addView(button("CREATE A NEW FAMILY SYNC", panel2, v -> confirmCreate()));
            content.addView(button("DISCONNECT THIS PHONE", panel2, v -> confirmDisconnect()));
        }

        if (app.hasSafetySnapshot()) {
            content.addView(section("DATA SAFETY"));
            content.addView(button("RESTORE LAST SAFETY SNAPSHOT", panel2, v -> confirmRestore()));
        }

        content.addView(section("HOW IT WORKS"));
        TextView how = text("Each phone keeps its normal BOLO Board data locally. When either phone is online and BOLO Board is open, John and Alexis profile changes are compared with the shared encrypted copy and the newer profile is synchronized. A cloud error never clears the local board.", 13, silver, false);
        how.setPadding(dp(4), 0, dp(4), dp(8));
        content.addView(how);

        if (getIntent().getBooleanExtra("first_run", false)) {
            content.addView(button("NOT NOW", panel2, v -> {
                global.edit().putBoolean(BoloBoardApp.KEY_PROMPT_DISMISSED, true).apply();
                finish();
            }));
        }
        content.addView(button("OPEN BOLO BOARD", panel2, v -> openMain()));
        setContentView(scroll);
    }

    private void confirmCreate() {
        String message = app.isConfigured()
                ? "This phone will start a new shared family board from its current John and Alexis data. The old pairing code will stop being used by this phone. Your local data is not deleted."
                : "This phone's current John and Alexis data will become the starting shared family board. A safety snapshot is saved first.";
        new AlertDialog.Builder(this)
                .setTitle("Create family sync?")
                .setMessage(message)
                .setPositiveButton("Create", (d, w) -> createSync())
                .setNegativeButton("Cancel", null).show();
    }

    private void createSync() {
        Toast.makeText(this, "Creating encrypted family board…", Toast.LENGTH_SHORT).show();
        app.createFamilySync((ok, message) -> {
            Toast.makeText(this, message == null ? (ok ? "Created" : "Unable to create") : message, Toast.LENGTH_LONG).show();
            if (ok) render();
        });
    }

    private void showJoinDialog() {
        EditText input = new EditText(this);
        input.setHint("BB1:board-id:encryption-key");
        input.setSingleLine(false);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setPadding(dp(18), dp(12), dp(18), dp(12));
        new AlertDialog.Builder(this)
                .setTitle("Join shared BOLO Board")
                .setMessage("Paste the pairing code from the phone that created the family sync. A safety snapshot of this phone is saved before shared data is downloaded.")
                .setView(input)
                .setPositiveButton("Join", (d, w) -> join(input.getText().toString()))
                .setNegativeButton("Cancel", null).show();
    }

    private void join(String code) {
        Toast.makeText(this, "Connecting and protecting local data…", Toast.LENGTH_SHORT).show();
        app.joinFamilySync(code, (ok, message) -> {
            Toast.makeText(this, message == null ? (ok ? "Connected" : "Unable to connect") : message, Toast.LENGTH_LONG).show();
            if (ok) render();
        });
    }

    private void sharePairingCode() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "BOLO Board family pairing code");
        send.putExtra(Intent.EXTRA_TEXT, "BOLO Board family pairing code:\n\n" + app.getPairingCode() + "\n\nKeep this code private. Install the same BOLO Board app, open BOLO Family Sync, and choose Join with Pairing Code.");
        startActivity(Intent.createChooser(send, "Share BOLO pairing code"));
    }

    private void syncNow() {
        Toast.makeText(this, "Syncing…", Toast.LENGTH_SHORT).show();
        app.syncNow(true, (ok, message) -> {
            if (message != null) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            render();
        });
    }

    private void confirmDisconnect() {
        new AlertDialog.Builder(this)
                .setTitle("Disconnect this phone?")
                .setMessage("Only the family-sync connection is removed. John and Alexis schedules, payroll settings, leave balances, and saved pay stubs remain on this phone.")
                .setPositiveButton("Disconnect", (d, w) -> {
                    app.disconnect();
                    Toast.makeText(this, "Family sync disconnected; local data kept", Toast.LENGTH_LONG).show();
                    render();
                }).setNegativeButton("Cancel", null).show();
    }

    private void confirmRestore() {
        new AlertDialog.Builder(this)
                .setTitle("Restore safety snapshot?")
                .setMessage("This restores the most recent John and Alexis safety copy saved before a family-sync replacement. If connected, the restored profiles become the newest version and will sync outward.")
                .setPositiveButton("Restore", (d, w) -> app.restoreSafetySnapshot((ok, message) -> {
                    Toast.makeText(this, message == null ? (ok ? "Restored" : "Restore failed") : message, Toast.LENGTH_LONG).show();
                    render();
                }))
                .setNegativeButton("Cancel", null).show();
    }

    private void openMain() {
        global.edit().putBoolean(BoloBoardApp.KEY_PROMPT_DISMISSED, true).apply();
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(rounded(panel, dp(12), Color.rgb(58, 78, 98), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(lp);
        return card;
    }

    private TextView section(String s) {
        TextView t = text(s, 15, gold, true);
        t.setPadding(dp(4), dp(16), dp(4), dp(8));
        return t;
    }

    private TextView button(String label, int color, View.OnClickListener listener) {
        TextView b = text(label, 15, Color.WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), dp(14), dp(12), dp(14));
        b.setBackground(rounded(color, dp(12), color == blue ? gold : Color.rgb(72, 96, 120), 1));
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(5), 0, dp(5));
        b.setLayoutParams(lp);
        return b;
    }

    private TextView text(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable rounded(int color, float radius, int stroke, int width) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (width > 0) d.setStroke(width, stroke);
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
