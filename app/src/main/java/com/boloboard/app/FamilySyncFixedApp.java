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

/** Dedicated BOLO Board family sync. No pairing or anonymous blob service. */
public class FamilySyncFixedApp extends BoloBoardApp {
    private static final String API = "https://ykqyonrbwktrsjohajff.supabase.co/functions/v1/bolo-sync";
    private static final String ANON = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InlrcXlvbnJid2t0cnNqb2hhamZmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODcxODIxOTksImV4cCI6MjEwMjc1ODE5OX0.Hj01mpshRcTq8JsgWwc-tYfD_N8Vvvlut8WdIOSpwAA";
    private static final String FAMILY_KEY = "ETnH0JswF-D_d1lgzBmgSBhQW7DtRM2TYqltpxvTpEw";
    private static final String CONNECTED = "supabase_family_v1";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private WeakReference<Activity> currentMain = new WeakReference<>(null);

    @Override public void onCreate() {
        super.onCreate();
        getSharedPreferences(GLOBAL, MODE_PRIVATE).edit()
                .putString(KEY_BLOB, CONNECTED)
                .putString(KEY_SECRET, FAMILY_KEY)
                .putBoolean(KEY_PROMPT_DISMISSED, true)
                .apply();
    }

    @Override public void onActivityStarted(Activity activity) {
        super.onActivityStarted(activity);
        if (activity instanceof MainActivity) currentMain = new WeakReference<>(activity);
    }

    @Override public void onActivityDestroyed(Activity activity) {
        super.onActivityDestroyed(activity);
        if (currentMain.get() == activity) currentMain = new WeakReference<>(null);
    }

    @Override public String getPairingCode() { return "BOLO Board is automatically connected"; }

    @Override public void createFamilySync(SyncCallback callback) {
        bootstrap(callback);
    }

    @Override public void joinFamilySync(String ignored, SyncCallback callback) {
        bootstrap(callback);
    }

    private void bootstrap(SyncCallback callback) {
        SharedPreferences g = getSharedPreferences(GLOBAL, MODE_PRIVATE);
        g.edit().putString(KEY_BLOB, CONNECTED).putString(KEY_SECRET, FAMILY_KEY).putBoolean(KEY_PROMPT_DISMISSED, true).apply();
        syncNow(true, callback);
    }

    @Override public void syncNow(boolean userInitiated, SyncCallback callback) {
        if (busy.getAndSet(true)) {
            if (callback != null) complete(callback, false, "Sync already running");
            return;
        }
        ioExecutor.execute(() -> {
            boolean pulled = false;
            try {
                SharedPreferences g = getSharedPreferences(GLOBAL, MODE_PRIVATE);
                byte[] key = decodeKey(FAMILY_KEY);
                pulled |= syncProfile("john", JOHN, KEY_JOHN_MOD, key);
                pulled |= syncProfile("alexis", ALEXIS, KEY_ALEXIS_MOD, key);
                g.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).remove(KEY_LAST_ERROR).apply();
                if (pulled) refreshMain();
                complete(callback, true, pulled ? "Synced • family changes downloaded" : "Synced • up to date");
            } catch (Exception e) {
                String msg = clean(e);
                getSharedPreferences(GLOBAL, MODE_PRIVATE).edit().putString(KEY_LAST_ERROR, msg).apply();
                if (userInitiated) complete(callback, false, msg);
                else complete(callback, false, null);
            } finally { busy.set(false); }
        });
    }

    private boolean syncProfile(String profile, String prefsName, String modKey, byte[] key) throws Exception {
        SharedPreferences g = getSharedPreferences(GLOBAL, MODE_PRIVATE);
        long localMod = g.getLong(modKey, 1L);
        JSONObject remote = api("get", profile, null, 0L);
        JSONObject record = remote.optJSONObject("record");
        if (record == null) {
            putProfile(profile, prefsName, modKey, Math.max(localMod, System.currentTimeMillis()), key);
            return false;
        }
        long remoteMod = record.optLong("modified_at", 0L);
        if (remoteMod > localMod) {
            saveSafetySnapshot();
            JSONObject doc = decrypt(record.getString("payload"), key);
            applyProfile(prefsName, doc, modKey);
            return true;
        }
        if (localMod > remoteMod) putProfile(profile, prefsName, modKey, localMod, key);
        return false;
    }

    private void putProfile(String profile, String prefsName, String modKey, long modifiedAt, byte[] key) throws Exception {
        String payload = encrypt(profileDocument(profile, prefsName, modifiedAt), key);
        try {
            api("put", profile, payload, modifiedAt);
            getSharedPreferences(GLOBAL, MODE_PRIVATE).edit().putLong(modKey, modifiedAt).apply();
        } catch (RemoteNewerException newer) {
            JSONObject r = api("get", profile, null, 0L).getJSONObject("record");
            applyProfile(prefsName, decrypt(r.getString("payload"), key), modKey);
        }
    }

    private JSONObject api(String action, String profile, String payload, long modifiedAt) throws Exception {
        JSONObject req = new JSONObject().put("action", action).put("profile", profile);
        if (payload != null) req.put("payload", payload).put("modifiedAt", modifiedAt);
        HttpURLConnection c = (HttpURLConnection)new URL(API).openConnection();
        c.setRequestMethod("POST"); c.setConnectTimeout(15000); c.setReadTimeout(15000); c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Authorization", "Bearer " + ANON);
        c.setRequestProperty("apikey", ANON);
        byte[] bytes = req.toString().getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(bytes.length);
        try(OutputStream out=c.getOutputStream()){out.write(bytes);}
        int code=c.getResponseCode(); String body=readBody(c,code);
        if(code==409) throw new RemoteNewerException();
        if(code<200||code>=300) throw new Exception("BOLO sync failed ("+code+")"+shortBody(body));
        return new JSONObject(body);
    }

    private JSONObject profileDocument(String profile,String prefsName,long modifiedAt)throws Exception{
        return new JSONObject().put("format","BOLO_BOARD_PROFILE_SYNC").put("version",5).put("profile",profile).put("modifiedAt",modifiedAt).put("data",prefsToJson(getSharedPreferences(prefsName,MODE_PRIVATE)));
    }

    private JSONObject prefsToJson(SharedPreferences prefs)throws Exception{
        JSONObject r=new JSONObject();
        for(Map.Entry<String,?> e:prefs.getAll().entrySet()){
            Object v=e.getValue();
            if(v instanceof String||v instanceof Boolean||v instanceof Integer||v instanceof Long||v instanceof Double||v instanceof Float) r.put(e.getKey(),v);
            else if(v instanceof Set){JSONArray a=new JSONArray();for(Object x:(Set<?>)v)a.put(String.valueOf(x));r.put(e.getKey(),a);}
        }return r;
    }

    private void applyProfile(String prefsName,JSONObject root,String modKey)throws Exception{
        JSONObject data=root.getJSONObject("data"); SharedPreferences.Editor ed=getSharedPreferences(prefsName,MODE_PRIVATE).edit().clear();
        Iterator<String> it=data.keys(); while(it.hasNext()){String k=it.next();Object v=data.get(k);
            if(v instanceof Boolean)ed.putBoolean(k,(Boolean)v);else if(v instanceof Integer)ed.putInt(k,(Integer)v);else if(v instanceof Long)ed.putLong(k,(Long)v);
            else if(v instanceof JSONArray){Set<String>s=new HashSet<>();JSONArray a=(JSONArray)v;for(int i=0;i<a.length();i++)s.add(a.getString(i));ed.putStringSet(k,s);}else ed.putString(k,String.valueOf(v));}
        if(!ed.commit())throw new Exception("Unable to save synchronized profile");
        getSharedPreferences(GLOBAL,MODE_PRIVATE).edit().putLong(modKey,root.optLong("modifiedAt",System.currentTimeMillis())).apply();
    }

    private String encrypt(JSONObject plain,byte[] key)throws Exception{byte[]iv=new byte[12];new java.security.SecureRandom().nextBytes(iv);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));byte[]ct=c.doFinal(plain.toString().getBytes(StandardCharsets.UTF_8));return new JSONObject().put("format","BOLO_BOARD_SYNC_ENCRYPTED").put("iv",Base64.encodeToString(iv,Base64.NO_WRAP)).put("data",Base64.encodeToString(ct,Base64.NO_WRAP)).toString();}
    private JSONObject decrypt(String text,byte[]key)throws Exception{JSONObject e=new JSONObject(text);byte[]iv=Base64.decode(e.getString("iv"),Base64.DEFAULT),ct=Base64.decode(e.getString("data"),Base64.DEFAULT);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));return new JSONObject(new String(c.doFinal(ct),StandardCharsets.UTF_8));}
    private byte[] decodeKey(String s)throws Exception{byte[]k=Base64.decode(s,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);if(k.length!=32)throw new Exception("Invalid sync key");return k;}
    private String readBody(HttpURLConnection c,int code)throws Exception{InputStream in=code>=200&&code<400?c.getInputStream():c.getErrorStream();if(in==null)return"";StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String l;while((l=r.readLine())!=null)b.append(l);}return b.toString();}
    private String shortBody(String b){if(b==null)return"";b=b.replace('\n',' ').trim();if(b.length()>120)b=b.substring(0,120);return b.isEmpty()?"":" • "+b;}
    private String clean(Exception e){String m=e.getMessage();return m==null||m.isEmpty()?e.getClass().getSimpleName():m;}
    private void complete(SyncCallback cb,boolean ok,String msg){mainHandler.post(()->{if(cb!=null)cb.done(ok,msg);});}
    private void refreshMain(){mainHandler.post(()->{Activity a=currentMain.get();if(a!=null&&!a.isFinishing()&&!a.isDestroyed())a.recreate();});}
    private static class RemoteNewerException extends Exception{}
}
