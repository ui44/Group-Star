// DsCome - Token Login + Account Switcher
// Injects token login UI at logout screen and account switcher in settings
(function() {
  if (window.__DSCOME_TOKEN_LOGIN_LOADED__) return;
  window.__DSCOME_TOKEN_LOGIN_LOADED__ = true;

  var STORAGE_KEY = 'dscome_saved_accounts';

  function getAccounts() {
    try {
      var raw = localStorage.getItem(STORAGE_KEY);
      return raw ? JSON.parse(raw) : [];
    } catch(e) { return []; }
  }

  function saveAccounts(accs) {
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(accs)); } catch(e) {}
  }

  function addAccount(token, username) {
    var accs = getAccounts();
    var exists = accs.find(function(a) { return a.token === token; });
    if (!exists) {
      accs.push({ token: token, username: username || 'Unknown', added: Date.now() });
      saveAccounts(accs);
    }
  }

  function removeAccount(token) {
    saveAccounts(getAccounts().filter(function(a) { return a.token !== token; }));
  }

  // Login via token — patches Discord's token storage and reloads
  function loginWithToken(token) {
    token = token.trim();
    if (!token) return;
    try {
      // get username from token payload (middle segment)
      var parts = token.split('.');
      var username = 'Account';
      if (parts.length >= 2) {
        try {
          var payload = JSON.parse(atob(parts[1]));
          username = payload.username || ('ID:' + atob(parts[0]));
        } catch(e) {}
      }
      addAccount(token, username);
      // set Discord auth token
      var iframe = document.createElement('iframe');
      iframe.style.display = 'none';
      document.body.appendChild(iframe);
      iframe.contentWindow.localStorage.setItem('token', '"' + token + '"');
      iframe.remove();
      // also set via webpackChunkdiscord_app
      try {
        Object.keys(window).forEach(function(k) {
          if (k.indexOf('webpackChunk') !== -1) {
            var chunk = window[k];
            if (chunk && chunk.push) {
              chunk.push([[Math.random()], {}, function(req) {
                try {
                  var mods = req.c;
                  Object.keys(mods).forEach(function(id) {
                    var mod = mods[id] && mods[id].exports;
                    if (mod && mod.default && mod.default.setToken) {
                      mod.default.setToken(token);
                    }
                    if (mod && mod.setToken) mod.setToken(token);
                  });
                } catch(ex) {}
              }]);
            }
          }
        });
      } catch(ex) {}
      // fallback: set in localStorage directly
      localStorage.setItem('token', '"' + token + '"');
      setTimeout(function() { location.reload(); }, 300);
    } catch(ex) {
      alert('DsCome: Login failed - ' + ex.message);
    }
  }

  // Build account switcher panel
  function buildSwitcherPanel() {
    var accs = getAccounts();
    var panel = document.createElement('div');
    panel.id = 'dscome-account-switcher';
    panel.style.cssText = 'position:fixed;top:50%;left:50%;transform:translate(-50%,-50%);z-index:99999;background:#2b2d31;border-radius:12px;padding:20px;min-width:340px;max-width:420px;box-shadow:0 8px 32px rgba(0,0,0,0.7);color:#dcddde;font-family:sans-serif;';
    panel.innerHTML = '<div style="font-size:18px;font-weight:700;margin-bottom:12px;color:#fff;">DsCome — Switch Account</div>';

    if (accs.length === 0) {
      panel.innerHTML += '<div style="color:#72767d;margin-bottom:12px;">No saved accounts.</div>';
    } else {
      accs.forEach(function(acc) {
        var row = document.createElement('div');
        row.style.cssText = 'display:flex;align-items:center;justify-content:space-between;background:#1e1f22;border-radius:8px;padding:10px 12px;margin-bottom:8px;cursor:pointer;';
        row.innerHTML = '<span style="flex:1;font-size:14px;">' + (acc.username || 'Unknown') + '</span>';
        var loginBtn = document.createElement('button');
        loginBtn.textContent = 'Login';
        loginBtn.style.cssText = 'background:#5865f2;color:#fff;border:none;border-radius:6px;padding:5px 12px;cursor:pointer;font-size:13px;margin-right:6px;';
        loginBtn.onclick = function(e) { e.stopPropagation(); panel.remove(); overlay.remove(); loginWithToken(acc.token); };
        var delBtn = document.createElement('button');
        delBtn.textContent = '✕';
        delBtn.style.cssText = 'background:#ed4245;color:#fff;border:none;border-radius:6px;padding:5px 8px;cursor:pointer;font-size:12px;';
        delBtn.onclick = function(e) { e.stopPropagation(); removeAccount(acc.token); panel.remove(); overlay.remove(); showSwitcher(); };
        row.appendChild(loginBtn);
        row.appendChild(delBtn);
        panel.appendChild(row);
      });
    }

    // token input
    var inputWrap = document.createElement('div');
    inputWrap.style.marginTop = '12px';
    var input = document.createElement('input');
    input.type = 'password';
    input.placeholder = 'Paste token here...';
    input.style.cssText = 'width:100%;box-sizing:border-box;background:#1e1f22;border:1px solid #4e5058;border-radius:6px;padding:9px 10px;color:#dcddde;font-size:13px;outline:none;';
    var addBtn = document.createElement('button');
    addBtn.textContent = 'Login with Token';
    addBtn.style.cssText = 'width:100%;margin-top:8px;background:#5865f2;color:#fff;border:none;border-radius:6px;padding:9px;cursor:pointer;font-size:14px;font-weight:600;';
    addBtn.onclick = function() { loginWithToken(input.value); };
    inputWrap.appendChild(input);
    inputWrap.appendChild(addBtn);
    panel.appendChild(inputWrap);

    // close button
    var closeBtn = document.createElement('button');
    closeBtn.textContent = '✕ Close';
    closeBtn.style.cssText = 'width:100%;margin-top:10px;background:transparent;color:#72767d;border:1px solid #4e5058;border-radius:6px;padding:7px;cursor:pointer;font-size:13px;';
    closeBtn.onclick = function() { panel.remove(); overlay.remove(); };
    panel.appendChild(closeBtn);

    return panel;
  }

  function showSwitcher() {
    var overlay = document.createElement('div');
    overlay.id = 'dscome-switcher-overlay';
    overlay.style.cssText = 'position:fixed;inset:0;background:rgba(0,0,0,0.6);z-index:99998;';
    overlay.onclick = function(e) { if (e.target === overlay) { overlay.remove(); var p = document.getElementById('dscome-account-switcher'); if(p) p.remove(); } };
    document.body.appendChild(overlay);
    document.body.appendChild(buildSwitcherPanel());
  }

  window.__DSCOME_SHOW_SWITCHER__ = showSwitcher;

  // Inject button into settings near "Log Out"
  function injectSettingsButton() {
    // look for logout button by text or aria
    var btns = Array.from(document.querySelectorAll('button, [role="menuitem"]'));
    var logoutBtn = btns.find(function(b) {
      var t = b.textContent || '';
      return t.match(/log.?out|sign.?out/i);
    });
    if (!logoutBtn || document.getElementById('dscome-switch-btn')) return;

    var btn = document.createElement('div');
    btn.id = 'dscome-switch-btn';
    btn.style.cssText = logoutBtn.style.cssText + ';cursor:pointer;color:#5865f2;margin-bottom:4px;padding:8px 12px;border-radius:4px;';
    btn.textContent = 'Switch Account (DsCome)';
    btn.onclick = function() { showSwitcher(); };
    logoutBtn.parentNode.insertBefore(btn, logoutBtn);
  }

  // Also inject at login/register page
  function injectLoginPageButton() {
    var loginPage = document.querySelector('[class*="loginContainer"], [class*="authBox"]');
    if (!loginPage || document.getElementById('dscome-login-page-btn')) return;

    var btn = document.createElement('button');
    btn.id = 'dscome-login-page-btn';
    btn.style.cssText = 'width:100%;margin-top:8px;background:#5865f2;color:#fff;border:none;border-radius:4px;padding:10px;cursor:pointer;font-size:14px;font-weight:600;';
    btn.textContent = 'Login with Token (DsCome)';
    btn.onclick = function() { showSwitcher(); };
    loginPage.appendChild(btn);
  }

  var _obs = new MutationObserver(function() {
    injectSettingsButton();
    injectLoginPageButton();
  });
  _obs.observe(document.documentElement, { childList: true, subtree: true });

  // If login page already up
  setTimeout(injectLoginPageButton, 1000);
})();
