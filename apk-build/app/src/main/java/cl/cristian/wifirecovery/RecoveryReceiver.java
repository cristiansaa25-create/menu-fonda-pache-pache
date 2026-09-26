package cl.cristian.wifirecovery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.util.Log;

public class RecoveryReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        final PendingResult pending = goAsync();
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            SharedPreferences state = app.getSharedPreferences("state", Context.MODE_PRIVATE);
            state.edit().putLong("recovery_received", System.currentTimeMillis()).apply();
            try {
                WifiManager wifi = (WifiManager) app.getSystemService(Context.WIFI_SERVICE);
                if (wifi != null) {
                    boolean disabled = wifi.setWifiEnabled(false);
                    state.edit().putBoolean("recovery_disable_ok", disabled).apply();
                    Thread.sleep(5000L);
                    boolean enabled = wifi.setWifiEnabled(true);
                    state.edit().putBoolean("recovery_enable_ok", enabled)
                            .putLong("recovery_finished", System.currentTimeMillis()).apply();
                }
            } catch (Exception error) {
                Log.e("WifiRecovery", "Alarm recovery failed", error);
                state.edit().putString("recovery_error", error.toString()).apply();
            } finally {
                pending.finish();
            }
        }, "wifi-alarm-recovery").start();
    }
}
