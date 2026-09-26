package cl.cristian.wifirecovery;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.*;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class WifiRecoveryService extends Service {
    private static final String CHANNEL = "wifi_recovery";
    private static final long CHECK_MS = 15000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WifiManager wifi;
    private SharedPreferences state;
    private int failedChecks;

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "Recuperación Wi-Fi", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        startForeground(7, b.setContentTitle("Wi-Fi protegido").setContentText("Vigilancia permanente activada").setOngoing(true).setSmallIcon(android.R.drawable.stat_notify_sync).build());
        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        state = getSharedPreferences("state", MODE_PRIVATE);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        handler.removeCallbacks(monitor);
        long delay = intent == null ? 0 : intent.getLongExtra("delay", 0);
        handler.postDelayed(monitor, delay);
        state.edit().putBoolean("guardian_enabled", true).apply();
        return START_STICKY;
    }

    private boolean connected() {
        ConnectivityManager cm = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo n = cm == null ? null : cm.getActiveNetworkInfo();
        return n != null && n.isConnected() && n.getType() == ConnectivityManager.TYPE_WIFI;
    }

    private final Runnable monitor = new Runnable() {
        @Override public void run() {
            try {
                boolean enabled = wifi != null && wifi.isWifiEnabled();
                boolean online = connected();
                if (!enabled && wifi != null) {
                    boolean accepted = wifi.setWifiEnabled(true);
                    log("Wi-Fi apagado; solicitud de encendido=" + accepted);
                    failedChecks++;
                } else if (!online && wifi != null) {
                    wifi.startScan();
                    boolean reconnect = wifi.reconnect();
                    failedChecks++;
                    log("Sin conexión; reconexión=" + reconnect + ", intento=" + failedChecks);
                    if (failedChecks % 8 == 0) wifi.setWifiEnabled(true);
                } else {
                    if (failedChecks > 0) log("Conexión Wi-Fi recuperada");
                    failedChecks = 0;
                }
                state.edit().putLong("last_check", System.currentTimeMillis())
                        .putBoolean("last_wifi_enabled", enabled).putBoolean("last_connected", online)
                        .putInt("failed_checks", failedChecks).apply();
            } catch (Throwable error) { log("ERROR: " + error); }
            handler.postDelayed(this, CHECK_MS);
        }
    };

    private void log(String message) {
        String stamp = new SimpleDateFormat("dd-MM HH:mm:ss", Locale.US).format(new Date());
        String old = state.getString("guardian_log", "");
        String next = stamp + "  " + message + "\n" + old;
        if (next.length() > 6000) next = next.substring(0, 6000);
        state.edit().putString("guardian_log", next).apply();
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (state != null) state.edit().putBoolean("guardian_enabled", false).apply();
        super.onDestroy();
    }

    @Override public android.os.IBinder onBind(Intent intent) { return null; }
}
