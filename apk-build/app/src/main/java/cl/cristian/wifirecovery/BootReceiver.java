package cl.cristian.wifirecovery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.os.SystemClock;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        final Context app = context.getApplicationContext();
        SharedPreferences state = app.getSharedPreferences("state", Context.MODE_PRIVATE);
        state.edit().putLong("boot_received", System.currentTimeMillis()).apply();
        Intent service = new Intent(app, WifiRecoveryService.class).putExtra("delay", 45000L);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) app.startForegroundService(service);
            else app.startService(service);
            state.edit().putBoolean("boot_service_started", true).apply();
        } catch (Exception e) {
            state.edit().putString("boot_service_error", e.toString()).apply();
        }
        AlarmManager alarms = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        Intent recovery = new Intent(app, RecoveryReceiver.class);
        PendingIntent operation = PendingIntent.getBroadcast(app, 42, recovery,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (alarms != null) {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 60000L, operation);
            state.edit().putBoolean("alarm_scheduled", true).apply();
        }
    }
}
