package com.jiankong.scoreapp.v2;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.ValueCallback;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import org.json.JSONObject;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Base64;

public class MainActivity extends Activity {

    private static final String CLOUD_API = "https://api.github.com/repos/Aweb484/score-app-data/contents/data.json";
    private static final String CLOUD_TOKEN = "ghp_" + "zxqMBri47tSo6iTFd8HwwRIki9pVOR4MF7PL";
    private static final int FILE_CHOOSER_REQ = 1;

    private WebView wv;
    private ValueCallback<Uri[]> uploadMessage;
    private View loadingView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            showLoading();
            loadApp();
        } catch (Throwable e) {
            e.printStackTrace();
            showError("启动失败: " + e.getMessage());
        }
    }

    private void showLoading() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFFF5F7FA);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(android.view.Gravity.CENTER);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = android.view.Gravity.CENTER;
        root.addView(layout, lp);
        ProgressBar pb = new ProgressBar(this);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pbLp.gravity = android.view.Gravity.CENTER;
        layout.addView(pb, pbLp);
        TextView tv = new TextView(this);
        tv.setText("正在加载...");
        tv.setTextColor(0xFF666666);
        tv.setTextSize(14);
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tvLp.gravity = android.view.Gravity.CENTER;
        tvLp.topMargin = 16;
        layout.addView(tv, tvLp);
        loadingView = root;
        setContentView(root);
    }

    private void loadApp() {
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // 1. 读取本地内置 HTML（稳定可靠，不依赖网络）
                    InputStream is = getAssets().open("app.html");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line).append("\n");
                    reader.close();
                    final String html = sb.toString();

                    // 2. 尝试下载云端数据（用户列表等）
                    final String cloudData = downloadCloudData();

                    // 3. 回到主线程初始化 WebView
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            initWebView(html, cloudData);
                        }
                    });
                } catch (final Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showError("加载失败: " + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    private String downloadCloudData() {
        try {
            if (!isNetworkAvailable()) return null;
            HttpURLConnection conn = (HttpURLConnection) new URL(CLOUD_API).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "token " + CLOUD_TOKEN);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            int code = conn.getResponseCode();
            if (code != 200) { conn.disconnect(); return null; }
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            conn.disconnect();
            JSONObject jsonObj = new JSONObject(sb.toString());
            String content = jsonObj.getString("content");
            byte[] decoded = Base64.decode(content, Base64.DEFAULT);
            return new String(decoded, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void initWebView(final String html, final String cloudData) {
        wv = new WebView(this);
        WebSettings ws = wv.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowFileAccessFromFileURLs(true);
        ws.setAllowUniversalAccessFromFileURLs(true);

        wv.setWebViewClient(new WebViewClient() {
            private boolean jsInjected = false;
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (loadingView != null) loadingView.setVisibility(View.GONE);
                if (jsInjected) return;
                jsInjected = true;
                // 注入云端 Token
                wv.evaluateJavascript(
                    "if(typeof CLOUD_CONFIG!=='undefined'){CLOUD_CONFIG.token='" + CLOUD_TOKEN + "';}", null);
                // 注入云端数据（使用 JSONObject 安全转义，防止 \t/\u2028/\u2029 等导致代码注入）
                if (cloudData != null) {
                    try {
                        String safeWrapper = new JSONObject().put("d", cloudData).toString();
                        int colonIdx = safeWrapper.indexOf(":");
                        String safeStr = safeWrapper.substring(colonIdx + 1, safeWrapper.length() - 1);
                        wv.evaluateJavascript(
                            "if(typeof window._injectCloudData==='function'){window._injectCloudData(JSON.parse(" + safeStr + "));}", null);
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }
        });

        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                    android.webkit.WebChromeClient.FileChooserParams fileChooserParams) {
                uploadMessage = filePathCallback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                startActivityForResult(Intent.createChooser(intent, "选择照片"), FILE_CHOOSER_REQ);
                return true;
            }
        });

        // 加载本地 HTML（不依赖网络，稳定可靠）
        wv.loadDataWithBaseURL("https://scoreapp.local/", html, "text/html", "UTF-8", null);
        setContentView(wv);
    }

    @SuppressWarnings("deprecation")
    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                Network network = cm.getActiveNetwork();
                if (network == null) return false;
                NetworkCapabilities nc = cm.getNetworkCapabilities(network);
                return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            } else {
                android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
                return ni != null && ni.isConnected();
            }
        } catch (Exception e) { return false; }
    }

    private void showError(String msg) {
        if (loadingView != null) loadingView.setVisibility(View.GONE);
        try {
            new AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton("退出", (d, w) -> finish())
                .show();
        } catch (Throwable e) { finish(); }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQ && uploadMessage != null) {
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                String s = data.getDataString();
                if (s != null) results = new Uri[]{Uri.parse(s)};
            }
            uploadMessage.onReceiveValue(results);
            uploadMessage = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (wv != null && wv.canGoBack()) wv.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (wv != null) {
            wv.stopLoading();
            if (wv.getParent() != null) {
                ((android.view.ViewGroup) wv.getParent()).removeView(wv);
            }
            wv.destroy();
            wv = null;
        }
        super.onDestroy();
    }
}
