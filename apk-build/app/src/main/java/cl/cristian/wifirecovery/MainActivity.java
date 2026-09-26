package cl.cristian.wifirecovery;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.*;

public class MainActivity extends Activity {
    private WifiManager wifi;
    private TextView status;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean retries;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 5);
        }
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(30, 24, 30, 30);
        box.setBackgroundColor(Color.rgb(16,16,16));
        TextView title = new TextView(this);
        title.setText("REPARACIÓN WI-FI DEL DECO\nPROPIEDAD DE CRISTIAN SEPÚLVEDA");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 18, 0, 18);
        box.addView(title);
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(16);
        status.setBackgroundColor(Color.rgb(45,45,45));
        status.setPadding(18, 18, 18, 18);
        box.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(button("1. DIAGNOSTICAR", v -> diagnose()));
        box.addView(button("2. REPARACIÓN SUAVE", v -> softRepair()));
        box.addView(button("3. REPARACIÓN COMPLETA", v -> fullRepair()));
        box.addView(button("4. REINTENTOS AUTOMÁTICOS", v -> toggleRetries()));
        box.addView(button("5. ACTIVAR PROTECTOR PERMANENTE", v -> startGuardian()));
        box.addView(button("6. VER REGISTRO DE CAÍDAS", v -> showLog()));
        scroll.addView(box);
        setContentView(scroll);
        diagnose();
    }

    private Button button(String text, android.view.View.OnClickListener action) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(17);
        b.setAllCaps(false);
        b.setOnClickListener(action);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = 12;
        b.setLayoutParams(p);
        return b;
    }

    private boolean connected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo n = cm == null ? null : cm.getActiveNetworkInfo();
        return n != null && n.isConnected() && n.getType() == ConnectivityManager.TYPE_WIFI;
    }

    private void diagnose() {
        if (wifi == null) { status.setText("ERROR: no se encontró el adaptador Wi-Fi."); return; }
        WifiInfo info = wifi.getConnectionInfo();
        String ssid = info == null ? "sin información" : info.getSSID();
        SupplicantState supplicant = info == null ? SupplicantState.INVALID : info.getSupplicantState();
        int networkId = info == null ? -1 : info.getNetworkId();
        int rssi = info == null ? 0 : info.getRssi();
        status.setText("Wi-Fi encendido: " + wifi.isWifiEnabled() + "\nConectado por Wi-Fi: " + connected()
                + "\nSSID: " + ssid + "\nEstado: " + supplicant + "\nRed guardada activa: " + networkId
                + "\nSeñal: " + rssi + " dBm");
    }

    private void softRepair() {
        if (wifi == null) return;
        status.setText("Reparación suave: solicitando escaneo y reconexión…");
        boolean scan = wifi.startScan();
        boolean reconnect = wifi.reconnect();
        handler.postDelayed(() -> { diagnose(); Toast.makeText(this, "Escaneo: " + scan + " / Reconexión: " + reconnect, Toast.LENGTH_LONG).show(); }, 8000L);
    }

    private void fullRepair() {
        if (wifi == null) return;
        status.setText("Reparación completa: reiniciando adaptador…");
        wifi.setWifiEnabled(false);
        handler.postDelayed(() -> {
            wifi.setWifiEnabled(true);
            status.setText("Adaptador encendido. Esperando servicio Wi-Fi…");
            handler.postDelayed(() -> {
                wifi.startScan();
                wifi.reconnect();
                handler.postDelayed(this::diagnose, 8000L);
            }, 7000L);
        }, 5000L);
    }

    private void toggleRetries() {
        retries = !retries;
        if (retries) { status.setText("Reintentos activados mientras esta pantalla permanezca abierta."); retryLoop.run(); }
        else { handler.removeCallbacks(retryLoop); status.setText("Reintentos detenidos."); }
    }

    private void startGuardian() {
        Intent i = new Intent(this, WifiRecoveryService.class);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            status.setText("Protector permanente activado. Revisará el Wi-Fi cada 15 segundos y se iniciará al encender el deco.");
        } catch (Exception e) { status.setText("Android bloqueó el servicio: " + e); }
    }

    private void showLog() {
        String log = getSharedPreferences("state", MODE_PRIVATE).getString("guardian_log", "Sin eventos registrados.");
        status.setText("REGISTRO DEL PROTECTOR\n\n" + log);
    }

    private final Runnable retryLoop = new Runnable() {
        @Override public void run() {
            if (!retries) return;
            if (connected()) { diagnose(); status.append("\nRESULTADO: conexión recuperada."); retries = false; return; }
            if (wifi != null) { if (!wifi.isWifiEnabled()) wifi.setWifiEnabled(true); wifi.startScan(); wifi.reconnect(); }
            status.setText("Sin conexión. Nuevo intento de escaneo y reconexión…");
            handler.postDelayed(this, 15000L);
        }
    };

    @Override protected void onDestroy() {
        retries = false;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
