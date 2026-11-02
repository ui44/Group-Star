// DsCome - ツcore Audio Engine Loader
// Bootstraps the ツcore audio engine (injector.js) which is bundled in the module assets
(function() {
  if (window.__DSCOME_UCORE_LOADED__) return;
  window.__DSCOME_UCORE_LOADED__ = true;

  // ツcore injector.js is too large (847KB) to inline here.
  // It's copied from module assets to Discord's data dir by DsComeInjectModule.
  // Load it via XHR from the local file path that the module copies it to.

  // The module copies ucore/injector.js to Discord's files/pyoncord/dscome_assets/ucore_injector.js
  // We load it from there using file:// URI (we're in Discord's WebView/React Native context)
  var INJECTOR_PATH = '/data/data/com.discord/files/pyoncord/dscome_assets/ucore_injector.js';

  function loadUcore(src) {
    window.__N7RCORDV2_LOADED__ = false; // reset so re-injection works
    if (!window.chrome) window.chrome = {};
    if (!window.chrome.runtime) window.chrome.runtime = {
      getURL: function(p) { return p; },
      sendMessage: function() {},
      onMessage: { addListener: function() {} }
    };
    var script = document.createElement('script');
    script.textContent = '(function(){if(window.__N7RCORDV2_LOADED__)return;window.__N7RCORDV2_LOADED__=true;' + src + '})();';
    document.documentElement.appendChild(script);
    script.remove();
    console.log('[DsCome] ツcore loaded');
  }

  // Try loading from file (React Native context allows file:// if module grants it)
  function tryFileLoad() {
    var xhr = new XMLHttpRequest();
    xhr.open('GET', 'file://' + INJECTOR_PATH, true);
    xhr.onload = function() {
      if (xhr.status === 0 || xhr.status === 200) {
        loadUcore(xhr.responseText);
      } else {
        console.warn('[DsCome] ツcore file load failed, status:', xhr.status);
      }
    };
    xhr.onerror = function() {
      console.warn('[DsCome] ツcore file not found at', INJECTOR_PATH);
    };
    xhr.send();
  }

  // Also expose manual trigger
  window.__DSCOME_LOAD_UCORE__ = tryFileLoad;

  // Inject ツcore button in UI
  function injectUcoreButton() {
    if (document.getElementById('dscome-ucore-btn')) return;
    var btn = document.createElement('button');
    btn.id = 'dscome-ucore-btn';
    btn.title = 'ツcore Audio Engine';
    btn.style.cssText = 'position:fixed;bottom:32px;right:12px;z-index:99990;background:#5865f2;border:none;border-radius:50%;width:36px;height:36px;cursor:pointer;font-size:14px;box-shadow:0 2px 8px rgba(0,0,0,0.5);color:#fff;font-weight:700;';
    btn.innerHTML = 'ツ';
    btn.onclick = function() {
      if (window.__UCORE_PANEL_TOGGLE__) window.__UCORE_PANEL_TOGGLE__();
      else tryFileLoad();
    };
    document.body.appendChild(btn);
  }

  var _obs = new MutationObserver(injectUcoreButton);
  _obs.observe(document.documentElement, { childList: true, subtree: true });
  setTimeout(function() {
    injectUcoreButton();
    tryFileLoad(); // auto-load on startup
  }, 2000);
})();
