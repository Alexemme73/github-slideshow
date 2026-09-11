package com.batterylens.client;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.DhcpInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.app.Activity;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int DISCOVERY_PORT = 50042;
    private static final String DISCOVERY_MAGIC = "BATTERYLENS_DISCOVER_V1";
    private static final String PREFS = "batterylens_client";
    private static final String KEY_LAST_URL = "last_server_url";
    private WebView webView;
    private View connectPanel;
    private TextView statusText;
    private ProgressBar progress;
    private Button retryButton;
    private Button manualButton;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private String currentServerUrl = "";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        webView = findViewById(R.id.webView);
        connectPanel = findViewById(R.id.connectPanel);
        statusText = findViewById(R.id.statusText);
        progress = findViewById(R.id.progress);
        retryButton = findViewById(R.id.retryButton);
        manualButton = findViewById(R.id.manualButton);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setLoadWithOverviewMode(false);
        ws.setUseWideViewPort(true);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String scheme = u.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) return false;
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                connectPanel.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showConnectionFailure("Server non raggiungibile. Verifico nuovamente la rete…");
            }
        });
        retryButton.setOnClickListener(v -> startConnectionFlow(true));
        manualButton.setOnClickListener(v -> showManualDialog());
        startConnectionFlow(false);
    }

    private void startConnectionFlow(boolean forceDiscovery) {
        webView.setVisibility(View.GONE);
        connectPanel.setVisibility(View.VISIBLE);
        progress.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.GONE);
        manualButton.setVisibility(View.GONE);
        statusText.setText("Ricerca automatica del server BatteryLens…");
        final String last = forceDiscovery ? "" : prefs.getString(KEY_LAST_URL, "");
        io.execute(() -> {
            if (!last.isEmpty() && verifyServer(last)) { connectToServer(last); return; }
            String discovered = discoverServer();
            if (discovered != null && verifyServer(discovered)) {
                prefs.edit().putString(KEY_LAST_URL, discovered).apply();
                connectToServer(discovered);
                return;
            }
            main.post(() -> showConnectionFailure("Server BatteryLens non trovato automaticamente. Controlla che telefono e server siano sulla stessa rete e che nel server sia configurato correttamente Indirizzo server / Porta di connessione."));
        });
    }

    private void connectToServer(String url) {
        currentServerUrl = normalizeUrl(url);
        main.post(() -> {
            statusText.setText("Connessione a " + currentServerUrl + "…");
            progress.setVisibility(View.VISIBLE);
            retryButton.setVisibility(View.GONE);
            manualButton.setVisibility(View.GONE);
            webView.loadUrl(currentServerUrl + "/");
        });
    }

    private void showConnectionFailure(String message) {
        main.post(() -> {
            webView.setVisibility(View.GONE);
            connectPanel.setVisibility(View.VISIBLE);
            progress.setVisibility(View.GONE);
            statusText.setText(message);
            retryButton.setVisibility(View.VISIBLE);
            manualButton.setVisibility(View.VISIBLE);
        });
    }

    private String discoverServer() {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            socket.setBroadcast(true);
            socket.setSoTimeout(3500);
            byte[] request = DISCOVERY_MAGIC.getBytes(StandardCharsets.UTF_8);
            Set<InetAddress> targets = new LinkedHashSet<>();
            targets.add(InetAddress.getByName("255.255.255.255"));
            InetAddress localBroadcast = wifiBroadcastAddress();
            if (localBroadcast != null) targets.add(localBroadcast);
            for (InetAddress target : targets) {
                DatagramPacket packet = new DatagramPacket(request, request.length, target, DISCOVERY_PORT);
                socket.send(packet);
            }
            long deadline = System.currentTimeMillis() + 3500;
            byte[] buf = new byte[4096];
            while (System.currentTimeMillis() < deadline) {
                DatagramPacket response = new DatagramPacket(buf, buf.length);
                socket.receive(response);
                String s = new String(response.getData(), response.getOffset(), response.getLength(), StandardCharsets.UTF_8);
                JSONObject obj = new JSONObject(s);
                if (!"BatteryLens".equals(obj.optString("service"))) continue;
                String url = obj.optString("url", "");
                if (url.isEmpty()) {
                    String address = obj.optString("address", response.getAddress().getHostAddress());
                    int port = obj.optInt("port", 5000);
                    url = "http://" + address + ":" + port;
                }
                return normalizeUrl(url);
            }
        } catch (Exception ignored) {
        } finally {
            if (socket != null) socket.close();
        }
        return null;
    }

    private InetAddress wifiBroadcastAddress() {
        try {
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi == null) return null;
            DhcpInfo dhcp = wifi.getDhcpInfo();
            if (dhcp == null || dhcp.netmask == 0) return null;
            int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
            byte[] quads = new byte[4];
            for (int k = 0; k < 4; k++) quads[k] = (byte) ((broadcast >> (k * 8)) & 0xFF);
            return InetAddress.getByAddress(quads);
        } catch (Exception e) { return null; }
    }

    private boolean verifyServer(String baseUrl) {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(normalizeUrl(baseUrl) + "/api/server/ping");
            conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(2200);
            conn.setReadTimeout(2200);
            conn.setUseCaches(false);
            conn.setRequestProperty("User-Agent", "BatteryLens-Android/1.0");
            if (conn.getResponseCode() != 200) return false;
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            JSONObject obj = new JSONObject(sb.toString());
            return obj.optBoolean("ok", false) && "BatteryLens".equals(obj.optString("service"));
        } catch (Exception e) { return false; }
        finally { if (conn != null) conn.disconnect(); }
    }

    private String normalizeUrl(String value) {
        String v = value == null ? "" : value.trim();
        if (!v.startsWith("http://") && !v.startsWith("https://")) v = "http://" + v;
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        return v;
    }

    private void showManualDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        EditText host = new EditText(this);
        host.setHint("Indirizzo server (es. 192.168.1.50)");
        host.setSingleLine(true);
        EditText port = new EditText(this);
        port.setHint("Porta (es. 5000)");
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        port.setSingleLine(true);
        port.setText("5000");
        String last = prefs.getString(KEY_LAST_URL, "");
        try {
            Uri u = Uri.parse(last);
            if (u.getHost() != null) host.setText(u.getHost());
            if (u.getPort() > 0) port.setText(String.valueOf(u.getPort()));
        } catch (Exception ignored) {}
        box.addView(host);
        box.addView(port);
        new AlertDialog.Builder(this)
                .setTitle("Connessione manuale")
                .setMessage("Usala solo se la ricerca automatica è bloccata dalla rete. L'indirizzo corretto è quello configurato sul server in Impostazioni.")
                .setView(box)
                .setNegativeButton("Annulla", null)
                .setPositiveButton("Connetti", (dialog, which) -> {
                    String h = host.getText().toString().trim();
                    String p = port.getText().toString().trim();
                    if (h.isEmpty() || p.isEmpty()) return;
                    String url = normalizeUrl(h + ":" + p);
                    prefs.edit().putString(KEY_LAST_URL, url).apply();
                    io.execute(() -> {
                        if (verifyServer(url)) connectToServer(url);
                        else showConnectionFailure("Nessun server BatteryLens risponde su " + url);
                    });
                }).show();
    }

    @Override public void onBackPressed() {
        if (webView.getVisibility() == View.VISIBLE && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        try { webView.destroy(); } catch (Exception ignored) {}
        io.shutdownNow();
        super.onDestroy();
    }
}
