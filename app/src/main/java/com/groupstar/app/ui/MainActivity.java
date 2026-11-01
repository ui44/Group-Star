package com.groupstar.app.ui;

import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.groupstar.app.NukerActivity;
import com.groupstar.app.R;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private LinearLayout cordPanel, tabButtons;
    private View btnCordTab, btnNukerTab;
    private Switch switchFakeDeafen, switchDesktopSpoof, switchVencord, switchArabic;

    private boolean cordPanelOpen   = false;
    private boolean fakeDeafenActive = false;
    private boolean vencordEnabled  = true;
    private boolean desktopSpoof    = true;
    private boolean arabicEnabled   = false;
    private String  currentTheme    = "default";

    private SharedPreferences prefs;
    private List<String[]>    accounts = new ArrayList<>(); // {name, token}
    private final Gson gson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs    = getSharedPreferences("gs_prefs", MODE_PRIVATE);
        webView  = findViewById(R.id.webView);
        cordPanel = findViewById(R.id.cordPanel);
        tabButtons = findViewById(R.id.tabButtons);
        btnCordTab  = findViewById(R.id.btnCordTab);
        btnNukerTab = findViewById(R.id.btnNukerTab);

        switchFakeDeafen  = findViewById(R.id.switchFakeDeafen);
        switchDesktopSpoof = findViewById(R.id.switchDesktopSpoof);
        switchVencord     = findViewById(R.id.switchVencord);
        switchArabic      = findViewById(R.id.switchArabic);

        loadAccounts();
        setupWebView();
        bindPanelControls();

        webView.loadUrl("https://discord.com/app");
    }

    // ──────────────────────────────────────────────────────────────────
    // WebView setup
    // ──────────────────────────────────────────────────────────────────

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setSupportZoom(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Desktop Chrome UA — no Mobile, no Android
        s.setUserAgentString(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/122.0.0.0 Safari/537.36"
        );

        WebView.setWebContentsDebuggingEnabled(false);
        webView.addJavascriptInterface(new GSBridge(), "GSBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                injectEarlyScripts();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                injectAfterLoad();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("https://discord.com") || url.startsWith("https://discordapp.com")) {
                    return false;
                }
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources());
            }
        });
    }

    // ──────────────────────────────────────────────────────────────────
    // JS injection
    // ──────────────────────────────────────────────────────────────────

    private void injectEarlyScripts() {
        // Navigator overrides — must happen before Discord JS runs
        String nav = "javascript:(function(){" +
            "try{" +
            "Object.defineProperty(navigator,'webdriver',{get:()=>false});" +
            "Object.defineProperty(navigator,'platform',{get:()=>'Win32'});" +
            "Object.defineProperty(navigator,'maxTouchPoints',{get:()=>0});" +
            "Object.defineProperty(navigator,'userAgent',{get:()=>" +
            "'Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36'});" +
            "Object.defineProperty(screen,'width',{get:()=>1920});" +
            "Object.defineProperty(screen,'height',{get:()=>1080});" +
            "try{Object.defineProperty(navigator,'userAgentData',{get:()=>({" +
            "brands:[{brand:'Chromium',version:'122'},{brand:'Google Chrome',version:'122'}]," +
            "mobile:false,platform:'Windows'," +
            "getHighEntropyValues:()=>Promise.resolve({platform:'Windows',architecture:'x86'})})});}" +
            "catch(e){}" +
            "}catch(e){}" +
            "})();";

        String token = prefs.getString("active_token", "");
        String tokenInject = "";
        if (!token.isEmpty()) {
            tokenInject = "javascript:(function(){" +
                "try{localStorage.setItem('token',JSON.stringify('" + token + "'));}catch(e){}" +
                "})();";
        }

        webView.evaluateJavascript(nav, null);
        if (!tokenInject.isEmpty()) webView.evaluateJavascript(tokenInject, null);
    }

    private void injectAfterLoad() {
        injectDiscordInternalSpoof();
        if (vencordEnabled) webView.evaluateJavascript(buildVencordLoader(), null);
        if (fakeDeafenActive) injectFakeDeafen(true);
        if (arabicEnabled) injectArabic(true);
        applyTheme(currentTheme);
    }

    private void injectDiscordInternalSpoof() {
        String js = "javascript:(function(){" +
            "try{" +
            "var req=webpackChunkdiscord_app&&webpackChunkdiscord_app.push([[Math.random()]," +
            "{},function(e){var r={};for(var t in e.c){try{var n=e.c[t].exports;" +
            "if(n&&n.default&&typeof n.default.getPlatformType==='function'){" +
            "n.default.getPlatformType=function(){return 'desktop';};" +
            "n.default.isDesktop=function(){return true;};" +
            "n.default.isMobile=function(){return false;};}" +
            "}catch(err){}}}]);" +
            "}catch(e){}" +
            "})();";
        webView.evaluateJavascript(js, null);
    }

    private void injectFakeDeafen(boolean enable) {
        String js;
        if (enable) {
            js = "javascript:(function(){" +
                "if(window._fakeDeafenPatch)return;" +
                "window._fakeDeafenPatch=true;" +
                // Intercept WebSocket send — force self_deaf:true but self_mute:false
                "var origSend=WebSocket.prototype.send;" +
                "WebSocket.prototype.send=function(data){" +
                "  try{" +
                "    var parsed=JSON.parse(data);" +
                "    if(parsed.op===4&&parsed.d){" +
                "      parsed.d.self_deaf=true;" +
                "      parsed.d.self_mute=false;" +
                "      data=JSON.stringify(parsed);" +
                "    }" +
                "  }catch(e){}" +
                "  origSend.call(this,data);" +
                "};" +
                // Block Discord from muting audio via gain=0
                "var origCreateGain=AudioContext.prototype.createGain;" +
                "AudioContext.prototype.createGain=function(){" +
                "  var g=origCreateGain.call(this);" +
                "  var origSetter=Object.getOwnPropertyDescriptor(GainNode.prototype,'gain');" +
                "  Object.defineProperty(g,'gain',{" +
                "    get:function(){return origSetter.get.call(g);}," +
                "    set:function(v){if(v>0.01)origSetter.set&&origSetter.set.call(g,v);}," +
                "    configurable:true" +
                "  });" +
                "  return g;" +
                "};" +
                "})();";
        } else {
            js = "javascript:(function(){window._fakeDeafenPatch=false;" +
                "delete window._fakeDeafenPatch;})();";
        }
        webView.evaluateJavascript(js, null);
    }

    private String buildVencordLoader() {
        return "(function(){" +
            "if(window._vencordLoaded)return;" +
            "window._vencordLoaded=true;" +
            "var s=document.createElement('script');" +
            "s.src='https://raw.githack.com/Vendicated/Vencord/main/browser/browser.js';" +
            "s.onload=function(){" +
            "  try{" +
            "    var plugins=Object.keys(Vencord.Plugins.plugins);" +
            "    plugins.forEach(function(name){" +
            "      try{Vencord.Settings.plugins[name].enabled=true;}catch(e){}" +
            "    });" +
            "    GSBridge.onVencordReady(plugins.join(','));" +
            "  }catch(e){}" +
            "};" +
            "document.head.appendChild(s);" +
            "})();";
    }

    private void injectArabic(boolean enable) {
        if (enable) {
            String js = "javascript:(function(){" +
                "try{" +
                "document.documentElement.setAttribute('lang','ar');" +
                "document.documentElement.setAttribute('dir','rtl');" +
                // Try Vencord locale if available
                "try{Vencord.Webpack.findByProps('setLocale').setLocale('ar');}catch(e){}" +
                "}catch(e){}" +
                "})();";
            webView.evaluateJavascript(js, null);
        } else {
            String js = "javascript:(function(){" +
                "document.documentElement.setAttribute('lang','en-US');" +
                "document.documentElement.setAttribute('dir','ltr');" +
                "try{Vencord.Webpack.findByProps('setLocale').setLocale('en-US');}catch(e){}" +
                "})();";
            webView.evaluateJavascript(js, null);
        }
    }

    private void applyTheme(String theme) {
        String css;
        switch (theme) {
            case "onedark":
                css = ":root{--background-primary:#282c34!important;--background-secondary:#21252b!important;" +
                      "--background-tertiary:#1d2026!important;--text-normal:#abb2bf!important;}";
                break;
            case "midnight":
                css = ":root{--background-primary:#0d0d0d!important;--background-secondary:#111111!important;" +
                      "--background-tertiary:#0a0a0a!important;--text-normal:#ffffff!important;}";
                break;
            case "purple":
                css = ":root{--background-primary:#1a0a2e!important;--background-secondary:#12001f!important;" +
                      "--background-tertiary:#0d0015!important;--text-normal:#d8b4fe!important;}";
                break;
            default: // discord default
                css = "";
                break;
        }
        if (!css.isEmpty()) {
            String js = "javascript:(function(){" +
                "var id='_gsTheme';" +
                "var el=document.getElementById(id);" +
                "if(!el){el=document.createElement('style');el.id=id;document.head.appendChild(el);}" +
                "el.textContent=" + escapeJs(css) + ";" +
                "})();";
            webView.evaluateJavascript(js, null);
        } else {
            webView.evaluateJavascript(
                "javascript:(function(){var el=document.getElementById('_gsTheme');if(el)el.remove();})();", null);
        }
    }

    private String escapeJs(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    // ──────────────────────────────────────────────────────────────────
    // Panel controls
    // ──────────────────────────────────────────────────────────────────

    private void bindPanelControls() {
        btnCordTab.setOnClickListener(v -> toggleCordPanel());
        btnNukerTab.setOnClickListener(v -> {
            // Close cord panel if open, then open nuker
            if (cordPanelOpen) closeCordPanel();
            openNuker();
        });

        switchFakeDeafen.setOnCheckedChangeListener((b, checked) -> {
            fakeDeafenActive = checked;
            injectFakeDeafen(checked);
        });

        switchDesktopSpoof.setOnCheckedChangeListener((b, checked) -> {
            desktopSpoof = checked;
            if (checked) injectDiscordInternalSpoof();
        });

        switchVencord.setOnCheckedChangeListener((b, checked) -> {
            vencordEnabled = checked;
            if (checked) webView.evaluateJavascript(buildVencordLoader(), null);
        });

        switchArabic.setOnCheckedChangeListener((b, checked) -> {
            arabicEnabled = checked;
            injectArabic(checked);
        });

        // Theme buttons
        bindTheme(R.id.themeDefault,  "default");
        bindTheme(R.id.themeOneDark,  "onedark");
        bindTheme(R.id.themeMidnight, "midnight");
        bindTheme(R.id.themePurple,   "purple");

        findViewById(R.id.btnManageAccounts).setOnClickListener(v -> showAccountsMenu());
    }

    private void bindTheme(int viewId, String theme) {
        View v = findViewById(viewId);
        if (v != null) v.setOnClickListener(x -> {
            currentTheme = theme;
            applyTheme(theme);
        });
    }

    private void toggleCordPanel() {
        if (cordPanelOpen) closeCordPanel();
        else openCordPanel();
    }

    private void openCordPanel() {
        cordPanelOpen = true;
        cordPanel.setVisibility(View.VISIBLE);
        ObjectAnimator.ofFloat(cordPanel, "translationX", 240f * getResources().getDisplayMetrics().density / 160f, 0f)
            .setDuration(250).start();
        // Rotate arrow
        ImageView arrow = btnCordTab.findViewById(R.id.arrowCord);
        if (arrow != null) arrow.setRotation(180f);
    }

    private void closeCordPanel() {
        cordPanelOpen = false;
        float width = 240f * getResources().getDisplayMetrics().density / 160f;
        ObjectAnimator anim = ObjectAnimator.ofFloat(cordPanel, "translationX", 0f, width);
        anim.setDuration(220);
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                cordPanel.setVisibility(View.GONE);
            }
        });
        anim.start();
        ImageView arrow = btnCordTab.findViewById(R.id.arrowCord);
        if (arrow != null) arrow.setRotation(0f);
    }

    private void openNuker() {
        startActivity(new Intent(this, NukerActivity.class));
    }

    // ──────────────────────────────────────────────────────────────────
    // Accounts
    // ──────────────────────────────────────────────────────────────────

    private void loadAccounts() {
        String json = prefs.getString("accounts", "[]");
        Type type = new TypeToken<List<String[]>>(){}.getType();
        accounts = gson.fromJson(json, type);
        if (accounts == null) accounts = new ArrayList<>();
    }

    private void saveAccounts() {
        prefs.edit().putString("accounts", gson.toJson(accounts)).apply();
    }

    private void showAccountsMenu() {
        List<String> names = new ArrayList<>();
        for (String[] acc : accounts) names.add(acc[0]);
        names.add("+ Add Account");

        new AlertDialog.Builder(this)
            .setTitle("Accounts")
            .setItems(names.toArray(new String[0]), (dialog, which) -> {
                if (which == accounts.size()) {
                    addAccountDialog();
                } else {
                    switchAccount(accounts.get(which)[1]);
                }
            }).show();
    }

    private void addAccountDialog() {
        android.widget.EditText etName  = new android.widget.EditText(this);
        etName.setHint("Account name");
        android.widget.EditText etToken = new android.widget.EditText(this);
        etToken.setHint("Discord token");
        etToken.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            | android.text.InputType.TYPE_CLASS_TEXT);

        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(48, 16, 48, 0);
        ll.addView(etName);
        ll.addView(etToken);

        new AlertDialog.Builder(this)
            .setTitle("Add Account")
            .setView(ll)
            .setPositiveButton("Add", (d, w) -> {
                String name  = etName.getText().toString().trim();
                String token = etToken.getText().toString().trim();
                if (!name.isEmpty() && !token.isEmpty()) {
                    accounts.add(new String[]{name, token});
                    saveAccounts();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void switchAccount(String token) {
        prefs.edit().putString("active_token", token).apply();
        webView.evaluateJavascript(
            "javascript:(function(){" +
            "localStorage.setItem('token',JSON.stringify('" + token + "'));" +
            "window.location.reload();" +
            "})();", null);
    }

    // ──────────────────────────────────────────────────────────────────
    // JS Bridge
    // ──────────────────────────────────────────────────────────────────

    class GSBridge {
        @android.webkit.JavascriptInterface
        public void onVencordReady(String plugins) {
            // Vencord loaded — plugins list passed back
        }

        @android.webkit.JavascriptInterface
        public String getToken() {
            return prefs.getString("active_token", "");
        }

        @android.webkit.JavascriptInterface
        public boolean isFakeDeafenActive() {
            return fakeDeafenActive;
        }
    }

    @Override
    public void onBackPressed() {
        if (cordPanelOpen) {
            closeCordPanel();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
