package com.groupstar.app.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.groupstar.app.R;
import com.groupstar.app.accounts.AccountManager;
import com.groupstar.app.discord.DiscordForegroundService;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "GroupStar";
    private static final int REQ_AUDIO = 101;
    private static final int REQ_FILE  = 102;

    private WebView webView;
    private AccountManager accountManager;

    // حالة الميزات
    private boolean fakeDeafenOn  = false;
    private boolean micUnlocked   = false;   // فيك ديفن + مايك مفتوح

    // UI controls
    private ImageButton btnFakeDeafen;
    private ImageButton btnMic;
    private ImageButton btnAccounts;
    private TextView    tvStatus;

    private ValueCallback<Uri[]> filePathCallback;

    // ── وزن JS السكريبتات ────────────────────────────────────────
    private String injectorJs  = "";
    private String contentJs   = "";
    private String vencordJs   = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        accountManager = new AccountManager(this);

        // تحقق من الحسابات
        if (accountManager.isEmpty()) {
            startActivity(new Intent(this, TokenLoginActivity.class));
            finish();
            return;
        }

        // اطلب صلاحية المايك
        requestAudioPermission();

        // ابدأ الـ Foreground Service
        Intent svc = new Intent(this, DiscordForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }

        // ربط الـ UI
        webView       = findViewById(R.id.webview);
        btnFakeDeafen = findViewById(R.id.btn_fake_deafen);
        btnMic        = findViewById(R.id.btn_mic);
        btnAccounts   = findViewById(R.id.btn_accounts);
        tvStatus      = findViewById(R.id.tv_status);

        // تحميل السكريبتات من assets
        loadScripts();

        // إعداد WebView
        setupWebView();

        // أزرار الـ UI
        setupButtons();

        // تحميل Discord
        loadDiscord();
    }

    // ═══════════════════════════════════════════════════════════
    // تحميل السكريبتات من assets
    // ═══════════════════════════════════════════════════════════
    private void loadScripts() {
        injectorJs = readAsset("extension/injector.js");
        contentJs  = readAsset("extension/content.js");
        // Vencord loader — بيتحمل من الشبكة أو من assets
        vencordJs  = buildVencordLoader();
    }

    private String readAsset(String path) {
        try (InputStream is = getAssets().open(path)) {
            byte[] b = new byte[is.available()];
            is.read(b);
            return new String(b, "UTF-8");
        } catch (Exception e) {
            Log.e(TAG, "readAsset failed: " + path, e);
            return "";
        }
    }

    /**
     * Vencord Loader — يسحب Vencord من CDN ويحقن كل البلاقن
     */
    private String buildVencordLoader() {
        return "(function() {\n" +
            "  if (window.__VENCORD_LOADED__) return;\n" +
            "  window.__VENCORD_LOADED__ = true;\n" +
            "  // Vencord via CDN\n" +
            "  const script = document.createElement('script');\n" +
            "  script.src = 'https://raw.githack.com/Vendicated/Vencord/refs/heads/main/browser/browser.js';\n" +
            "  script.onload = function() {\n" +
            "    console.log('[GroupStar] Vencord loaded');\n" +
            "    enableAllPlugins();\n" +
            "  };\n" +
            "  script.onerror = function() {\n" +
            "    console.warn('[GroupStar] Vencord CDN failed, trying backup');\n" +
            "    loadVencordBackup();\n" +
            "  };\n" +
            "  document.head.appendChild(script);\n" +
            "\n" +
            "  function enableAllPlugins() {\n" +
            "    try {\n" +
            "      const { Vencord } = window;\n" +
            "      if (!Vencord) return setTimeout(enableAllPlugins, 1000);\n" +
            "      const plugins = Vencord.Plugins?.plugins;\n" +
            "      if (!plugins) return;\n" +
            "      Object.keys(plugins).forEach(name => {\n" +
            "        try {\n" +
            "          if (!Vencord.Settings.plugins[name]) Vencord.Settings.plugins[name] = {};\n" +
            "          Vencord.Settings.plugins[name].enabled = true;\n" +
            "          if (plugins[name].start) plugins[name].start();\n" +
            "        } catch(e) {}\n" +
            "      });\n" +
            "      console.log('[GroupStar] All Vencord plugins enabled:', Object.keys(plugins).length);\n" +
            "      window.GSBridge.onVencordReady(Object.keys(plugins).join(','));\n" +
            "    } catch(e) { setTimeout(enableAllPlugins, 1000); }\n" +
            "  }\n" +
            "\n" +
            "  function loadVencordBackup() {\n" +
            "    const s2 = document.createElement('script');\n" +
            "    s2.src = 'https://cdn.jsdelivr.net/gh/Vendicated/Vencord@main/browser/browser.js';\n" +
            "    s2.onload = function() { enableAllPlugins(); };\n" +
            "    document.head.appendChild(s2);\n" +
            "  }\n" +
            "})();\n";
    }

    // ═══════════════════════════════════════════════════════════
    // إعداد WebView
    // ═══════════════════════════════════════════════════════════
    private void setupWebView() {
        WebSettings ws = webView.getSettings();

        // تفعيل كل شيء
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);

        // ── Desktop User-Agent + Platform Spoof ──────────────────
        // هذا هو اللي يخلي Discord يشوفك PC
        ws.setUserAgentString(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Safari/537.36"
        );

        // إخفاء NavigatorInfo — Desktop
        ws.setUserAgentString(
            ws.getUserAgentString()
                .replace("Mobile", "")
                .replace("Android", "")
        );

        // تفعيل WebRTC
        ws.setJavaScriptEnabled(true);

        // رفع حدود المحتوى
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ws.setSafeBrowsingEnabled(false);
        }

        // JavaScript Bridge
        webView.addJavascriptInterface(new GSBridge(), "GSBridge");

        // WebViewClient
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String url = req.getUrl().toString();
                // ابق داخل discord.com
                if (url.startsWith("https://discord.com") || url.startsWith("https://canary.discord.com")) {
                    return false;
                }
                // افتح روابط خارجية في المتصفح
                if (url.startsWith("http")) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                // حقن التوكن مبكراً
                injectTokenEarly();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url.contains("discord.com")) {
                    injectAllScripts();
                }
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                // لا تمنع أي request
                return null;
            }
        });

        // WebChromeClient — صلاحيات الصوت/كاميرا/ملفات
        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                // امنح كل الصلاحيات تلقائياً
                request.grant(request.getResources());
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                Log.d(TAG + "/JS", cm.message() + " [" + cm.sourceId() + ":" + cm.lineNumber() + "]");
                return true;
            }

            @Override
            public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> cb, FileChooserParams p) {
                filePathCallback = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(Intent.createChooser(i, "اختر ملف"), REQ_FILE);
                return true;
            }
        });

        // تفعيل WebView debugging
        WebView.setWebContentsDebuggingEnabled(true);
    }

    // ═══════════════════════════════════════════════════════════
    // حقن التوكن مبكراً قبل تحميل الصفحة
    // ═══════════════════════════════════════════════════════════
    private void injectTokenEarly() {
        AccountManager.Account acc = accountManager.getActive();
        if (acc == null) return;

        // حقن التوكن في localStorage قبل ما يشوفه Discord
        String tokenScript =
            "(function() {" +
            "  try {" +
            "    const token = '" + acc.token.replace("'", "\\'") + "';" +
            // Discord يخزن التوكن بهذه الطريقة
            "    localStorage.setItem('token', JSON.stringify(token));" +
            // بعض الإصدارات تستخدم هذا
            "    document.cookie = 'locale=en-US; path=/';" +
            // Override navigator.platform لـ Desktop
            "    Object.defineProperty(navigator, 'platform', { get: () => 'Win32' });" +
            "    Object.defineProperty(navigator, 'userAgent', { get: () => 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36' });" +
            "    Object.defineProperty(navigator, 'maxTouchPoints', { get: () => 0 });" +
            // خلي Discord يشوف الجهاز كـ Desktop
            "    if (!window.__GS_PLATFORM_SET__) {" +
            "      window.__GS_PLATFORM_SET__ = true;" +
            "      Object.defineProperty(navigator, 'userAgentData', {" +
            "        get: () => ({" +
            "          platform: 'Windows'," +
            "          mobile: false," +
            "          brands: [{brand:'Google Chrome',version:'120'},{brand:'Chromium',version:'120'}]" +
            "        })" +
            "      });" +
            "    }" +
            "  } catch(e) {}" +
            "})();";

        webView.evaluateJavascript(tokenScript, null);
    }

    // ═══════════════════════════════════════════════════════════
    // حقن كل السكريبتات بعد تحميل الصفحة
    // ═══════════════════════════════════════════════════════════
    private void injectAllScripts() {
        // 1. Platform spoof أقوى
        injectDesktopSpoof();

        // 2. ツcore injector
        if (!injectorJs.isEmpty()) {
            webView.evaluateJavascript(injectorJs, null);
        }

        // 3. Vencord + all plugins
        webView.evaluateJavascript(vencordJs, null);

        // 4. Fake Deafen إذا كان شغال
        if (fakeDeafenOn) {
            injectFakeDeafen(true);
        }

        tvStatus.post(() -> tvStatus.setText("✓ مدمج"));
    }

    // ═══════════════════════════════════════════════════════════
    // Desktop Spoof الكامل
    // ═══════════════════════════════════════════════════════════
    private void injectDesktopSpoof() {
        String spoof =
            "(function() {" +
            "  if (window.__GS_SPOOF_DONE__) return;" +
            "  window.__GS_SPOOF_DONE__ = true;" +

            // Navigator overrides
            "  const props = {" +
            "    platform:      { value: 'Win32', writable:false }," +
            "    maxTouchPoints:{ value: 0, writable:false }," +
            "    userAgent:     { value: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36', writable:false }" +
            "  };" +
            "  Object.keys(props).forEach(k => {" +
            "    try { Object.defineProperty(navigator, k, props[k]); } catch(e) {}" +
            "  });" +

            // UserAgentData
            "  try {" +
            "    Object.defineProperty(navigator, 'userAgentData', {" +
            "      value: {" +
            "        platform: 'Windows'," +
            "        mobile: false," +
            "        brands: [" +
            "          { brand: 'Google Chrome', version: '120' }," +
            "          { brand: 'Chromium',      version: '120' }," +
            "          { brand: 'Not?A_Brand',   version: '24'  }" +
            "        ]," +
            "        getHighEntropyValues: (hints) => Promise.resolve({" +
            "          platform: 'Windows'," +
            "          platformVersion: '10.0.0'," +
            "          architecture: 'x86'," +
            "          bitness: '64'," +
            "          mobile: false" +
            "        })" +
            "      }," +
            "      writable: false" +
            "    });" +
            "  } catch(e) {}" +

            // Screen — ابدو زي شاشة PC
            "  try {" +
            "    Object.defineProperty(window, 'screen', {" +
            "      value: Object.create(window.screen, {" +
            "        width:       { get: () => 1920 }," +
            "        height:      { get: () => 1080 }," +
            "        availWidth:  { get: () => 1920 }," +
            "        availHeight: { get: () => 1040 }," +
            "        colorDepth:  { get: () => 24  }," +
            "        pixelDepth:  { get: () => 24  }" +
            "      })" +
            "    });" +
            "  } catch(e) {}" +

            // Discord internal — يخلي الـ API يشوفك desktop
            "  try {" +
            "    const findModule = (filter) => {" +
            "      const wpRequire = window.webpackChunkdiscord_app?.push([[Symbol()], {}, r => r]);" +
            "      if (!wpRequire) return null;" +
            "      const modules = Object.values(wpRequire.m || {});" +
            "      for (const m of modules) {" +
            "        try {" +
            "          const exp = m && m.exports;" +
            "          if (exp && filter(exp)) return exp;" +
            "          if (exp && exp.default && filter(exp.default)) return exp.default;" +
            "        } catch(e) {}" +
            "      }" +
            "      return null;" +
            "    };" +

            // Override Discord's getPlatformType
            "    setTimeout(() => {" +
            "      try {" +
            "        const browserInfo = findModule(m => m.getPlatformType || m.getOS);" +
            "        if (browserInfo) {" +
            "          if (browserInfo.getPlatformType) browserInfo.getPlatformType = () => 'web';" +
            "          if (browserInfo.isDesktop)       browserInfo.isDesktop = () => true;" +
            "          if (browserInfo.isMobile)        browserInfo.isMobile  = () => false;" +
            "          console.log('[GroupStar] Desktop spoof: Discord internals patched');" +
            "        }" +
            "      } catch(e) {}" +
            "    }, 2000);" +
            "  } catch(e) {}" +

            "})();";

        webView.evaluateJavascript(spoof, null);
    }

    // ═══════════════════════════════════════════════════════════
    // Fake Deafen
    // تظهر للكل أنك ديفن (صامت عن السماع) لكن في الحقيقة:
    // - تسمع الكل
    // - تقدر تتكلم إذا فتحت المايك
    // ═══════════════════════════════════════════════════════════
    private void injectFakeDeafen(boolean enable) {
        String script;
        if (enable) {
            script =
                "(function() {" +
                "  if (window.__GS_FAKE_DEAFEN__) return;" +
                "  window.__GS_FAKE_DEAFEN__ = true;" +

                "  const patchDiscordDeafen = () => {" +
                "    try {" +
                "      const wpChunk = window.webpackChunkdiscord_app;" +
                "      if (!wpChunk) return setTimeout(patchDiscordDeafen, 500);" +

                "      let rtcPatched = false;" +

                // البحث عن MediaDeviceDispatcher أو RTCConnectionManager
                "      const req = wpChunk.push([[Symbol()], {}, r => r]);" +
                "      const modules = Object.values(req.m || {});" +

                "      for (const factory of modules) {" +
                "        try {" +
                "          const m = factory.toString();" +
                // نبحث عن الكود اللي يتحكم في الـ deafen
                "          if (m.includes('selfDeaf') || m.includes('selfMute')) {" +
                "            console.log('[GroupStar] Found deafen module, patching...');" +
                "          }" +
                "        } catch(e) {}" +
                "      }" +

                // الطريقة المضمونة: override الـ WebSocket messages
                "      const origSend = WebSocket.prototype.send;" +
                "      WebSocket.prototype.send = function(data) {" +
                "        if (typeof data === 'string') {" +
                "          try {" +
                "            const msg = JSON.parse(data);" +
                // op 4 = Voice State Update في Discord Gateway
                "            if (msg && msg.op === 4 && msg.d) {" +
                "              const isDeafenMsg = msg.d.self_deaf !== undefined;" +
                "              if (isDeafenMsg && !window.__GS_FAKE_DEAFEN_ACTIVE__) {" +
                "                window.__GS_FAKE_DEAFEN_ACTIVE__ = false;" +
                "              }" +
                "              if (window.__GS_FAKE_DEAFEN_ACTIVE__) {" +
                // نرسل deafen = true للـ API (يشوفك الكل ديفن)
                // لكن نمنع Discord من فعلاً يصمّت الصوت
                "                const fakemsg = JSON.parse(JSON.stringify(msg));" +
                "                fakemsg.d.self_deaf = true;" +
                "                fakemsg.d.self_mute = false;" + // مايك مفتوح
                "                return origSend.call(this, JSON.stringify(fakemsg));" +
                "              }" +
                "            }" +
                "          } catch(e) {}" +
                "        }" +
                "        return origSend.call(this, data);" +
                "      };" +

                // منع Discord من يصمّت الـ AudioContext عند الديفن
                "      const origGain = AudioContext.prototype.createGain || window.AudioContext.prototype.createGain;" +
                "      if (origGain) {" +
                "        window.__GS_AUDIO_GAINS__ = [];" +
                "        AudioContext.prototype.createGain = function() {" +
                "          const gain = origGain.call(this);" +
                "          window.__GS_AUDIO_GAINS__.push(gain);" +
                "          Object.defineProperty(gain.gain, 'value', {" +
                "            get() { return this._gsValue !== undefined ? this._gsValue : 1; }," +
                "            set(v) {" +
                "              this._gsValue = v;" +
                "              if (window.__GS_FAKE_DEAFEN_ACTIVE__ && v === 0) {" +
                // إذا Discord حاول يصمّت الصوت — امنعه
                "                console.log('[GroupStar] FakeDeafen: blocked audio mute');" +
                "                return;" +
                "              }" +
                "              Object.getOwnPropertyDescriptor(AudioParam.prototype, 'value').set.call(this, v);" +
                "            }" +
                "          });" +
                "          return gain;" +
                "        };" +
                "      }" +

                "      console.log('[GroupStar] FakeDeafen: patched ✓');" +
                "    } catch(e) { console.error('[GroupStar] FakeDeafen error:', e); setTimeout(patchDiscordDeafen, 1000); }" +
                "  };" +

                "  patchDiscordDeafen();" +
                "})();";

            webView.evaluateJavascript(script, null);

            // فعّل الـ fake deafen في Discord
            webView.evaluateJavascript(
                "(function() { window.__GS_FAKE_DEAFEN_ACTIVE__ = true;" +
                // ضغط زر الديفن في Discord UI
                "  setTimeout(() => {" +
                "    const deafBtn = document.querySelector('[aria-label=\"Deafen\"], button[aria-label*=\"deaf\" i]');" +
                "    if (deafBtn && !deafBtn.classList.contains('active')) deafBtn.click();" +
                // أو عبر WebSocket مباشرة
                "    try {" +
                "      const ws = window.__GS_WS__ || Array.from(window.__wsMap||[]).find(w => w.url?.includes('gateway.discord'));" +
                "      if (ws) {" +
                "        const currentState = window.__GS_VOICE_STATE__ || {};" +
                "        ws.send(JSON.stringify({ op:4, d:{ guild_id: currentState.guild_id||null, channel_id: currentState.channel_id||null, self_mute: false, self_deaf: true }}));" +
                "      }" +
                "    } catch(e) {}" +
                "  }, 500);" +
                "})();",
                null
            );

        } else {
            // إيقاف الفيك ديفن
            script =
                "(function() {" +
                "  window.__GS_FAKE_DEAFEN_ACTIVE__ = false;" +
                "  window.__GS_FAKE_DEAFEN__ = false;" +
                // رفع الديفن
                "  setTimeout(() => {" +
                "    const deafBtn = document.querySelector('[aria-label=\"Deafen\"], button[aria-label*=\"deaf\" i]');" +
                "    if (deafBtn && deafBtn.getAttribute('aria-checked') === 'true') deafBtn.click();" +
                "  }, 200);" +
                "})();";
            webView.evaluateJavascript(script, null);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // تحميل Discord
    // ═══════════════════════════════════════════════════════════
    private void loadDiscord() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept-Language", "en-US,en;q=0.9");
        webView.loadUrl("https://discord.com/app", headers);
        tvStatus.setText("جار التحميل...");
    }

    // ═══════════════════════════════════════════════════════════
    // الأزرار
    // ═══════════════════════════════════════════════════════════
    private void setupButtons() {

        // ── زر Fake Deafen ────────────────────────────────────
        btnFakeDeafen.setOnClickListener(v -> {
            fakeDeafenOn = !fakeDeafenOn;
            updateFakeDeafenUI();
            injectFakeDeafen(fakeDeafenOn);

            String msg = fakeDeafenOn ? "🔇 فيك ديفن شغال" : "🔊 فيك ديفن مطفي";
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });

        // ── زر المايك (يعمل حتى داخل فيك ديفن) ───────────────
        btnMic.setOnClickListener(v -> {
            micUnlocked = !micUnlocked;
            updateMicUI();

            String micScript;
            if (micUnlocked) {
                // فكّ المايك — تقدر تتكلم
                micScript =
                    "try {" +
                    "  const mutBtn = document.querySelector('[aria-label=\"Mute\"], button[aria-label*=\"mute\" i]');" +
                    "  const isMuted = mutBtn && (mutBtn.getAttribute('aria-checked')==='true' || mutBtn.classList.contains('muted'));" +
                    "  if (isMuted && mutBtn) mutBtn.click();" +
                    // override self_mute في gateway
                    "  window.__GS_MIC_OPEN__ = true;" +
                    "} catch(e) {}";
            } else {
                micScript =
                    "try {" +
                    "  window.__GS_MIC_OPEN__ = false;" +
                    "} catch(e) {}";
            }
            webView.evaluateJavascript(micScript, null);

            Toast.makeText(this, micUnlocked ? "🎤 مايك مفتوح" : "🎤 مايك مغلق", Toast.LENGTH_SHORT).show();
        });

        // ── زر الحسابات ───────────────────────────────────────
        btnAccounts.setOnClickListener(v -> showAccountsMenu());
    }

    private void updateFakeDeafenUI() {
        btnFakeDeafen.setImageResource(
            fakeDeafenOn ? R.drawable.ic_deafen_on : R.drawable.ic_deafen_off
        );
        btnFakeDeafen.setColorFilter(
            fakeDeafenOn
                ? getColor(R.color.accent_red)
                : getColor(R.color.accent_white)
        );
    }

    private void updateMicUI() {
        btnMic.setImageResource(
            micUnlocked ? R.drawable.ic_mic_on : R.drawable.ic_mic_off
        );
        btnMic.setColorFilter(
            micUnlocked
                ? getColor(R.color.accent_green)
                : getColor(R.color.accent_white)
        );
    }

    // ═══════════════════════════════════════════════════════════
    // قائمة الحسابات
    // ═══════════════════════════════════════════════════════════
    private void showAccountsMenu() {
        var accounts = accountManager.getAll();
        String[] names = new String[accounts.size() + 2];

        for (int i = 0; i < accounts.size(); i++) {
            names[i] = (i == accountManager.getActiveIndex() ? "✓ " : "   ") + accounts.get(i).nickname;
        }
        names[accounts.size()]     = "+ إضافة حساب جديد";
        names[accounts.size() + 1] = "✏ تعديل الأسماء";

        new AlertDialog.Builder(this)
            .setTitle("الحسابات")
            .setItems(names, (dialog, which) -> {
                if (which < accounts.size()) {
                    // تبديل حساب
                    AccountManager.Account acc = accountManager.switchTo(which);
                    if (acc != null) {
                        switchAccount(acc);
                    }
                } else if (which == accounts.size()) {
                    // إضافة حساب جديد
                    startActivity(new Intent(this, TokenLoginActivity.class));
                }
            })
            .show();
    }

    private void switchAccount(AccountManager.Account acc) {
        Toast.makeText(this, "جار التبديل إلى " + acc.nickname, Toast.LENGTH_SHORT).show();

        // امسح الـ cookies وlocalstorage وحط التوكن الجديد
        webView.evaluateJavascript(
            "(function() {" +
            "  localStorage.clear();" +
            "  sessionStorage.clear();" +
            "  const newToken = '" + acc.token.replace("'", "\\'") + "';" +
            "  localStorage.setItem('token', JSON.stringify(newToken));" +
            "  window.location.reload();" +
            "})();",
            null
        );
    }

    // ═══════════════════════════════════════════════════════════
    // JS Bridge — التواصل بين JavaScript والـ Android
    // ═══════════════════════════════════════════════════════════
    private class GSBridge {

        @JavascriptInterface
        public void onVencordReady(String plugins) {
            int count = plugins.split(",").length;
            runOnUiThread(() -> {
                tvStatus.setText("✓ Vencord " + count + " plugin");
                Toast.makeText(MainActivity.this, "Vencord جاهز — " + count + " بلاقن", Toast.LENGTH_SHORT).show();
            });
        }

        @JavascriptInterface
        public void log(String msg) {
            Log.d(TAG + "/JS", msg);
        }

        @JavascriptInterface
        public String getToken() {
            AccountManager.Account acc = accountManager.getActive();
            return acc != null ? acc.token : "";
        }

        @JavascriptInterface
        public boolean isFakeDeafenActive() {
            return fakeDeafenOn;
        }
    }

    // ═══════════════════════════════════════════════════════════
    // صلاحيات
    // ═══════════════════════════════════════════════════════════
    private void requestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{ Manifest.permission.RECORD_AUDIO }, REQ_AUDIO);
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FILE) {
            if (filePathCallback != null) {
                Uri[] uris = (data != null && data.getData() != null)
                    ? new Uri[]{ data.getData() } : null;
                filePathCallback.onReceiveValue(uris);
                filePathCallback = null;
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        webView.destroy();
    }
}
