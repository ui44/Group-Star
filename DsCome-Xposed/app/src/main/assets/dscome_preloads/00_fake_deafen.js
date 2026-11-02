// DsCome - Fake Deafen
// Intercepts Discord gateway WS to spoof self_deaf=true while keeping audio active
(function() {
  if (window.__DSCOME_FAKE_DEAFEN_LOADED__) return;
  window.__DSCOME_FAKE_DEAFEN_LOADED__ = true;

  var _fakeDeafEnabled = false;
  var _realDeafState = false;

  // Hook WebSocket send to intercept voice state updates (op:4)
  var _origWSProto = WebSocket.prototype.send;
  WebSocket.prototype.send = function(data) {
    if (_fakeDeafEnabled && typeof data === 'string') {
      try {
        var msg = JSON.parse(data);
        if (msg && msg.op === 4 && msg.d) {
          _realDeafState = msg.d.self_deaf;
          msg.d.self_deaf = true; // spoof deafened to server
          data = JSON.stringify(msg);
        }
      } catch(e) {}
    }
    return _origWSProto.call(this, data);
  };

  // Hook incoming WS messages to fix self_deaf in local state
  var _origAddEL = EventTarget.prototype.addEventListener;
  EventTarget.prototype.addEventListener = function(type, listener, opts) {
    if (type === 'message' && this instanceof WebSocket) {
      var _wrappedListener = function(e) {
        if (_fakeDeafEnabled && e.data && typeof e.data === 'string') {
          try {
            var msg = JSON.parse(e.data);
            // op:0 VOICE_STATE_UPDATE: fix our own state locally
            if (msg && msg.t === 'VOICE_STATE_UPDATE' && msg.d) {
              // let through so local UI shows real state
            }
          } catch(e) {}
        }
        return listener.call(this, e);
      };
      return _origAddEL.call(this, type, _wrappedListener, opts);
    }
    return _origAddEL.call(this, type, listener, opts);
  };

  // Expose API for UI
  window.__DSCOME_FAKE_DEAFEN__ = {
    isEnabled: function() { return _fakeDeafEnabled; },
    setEnabled: function(val) {
      _fakeDeafEnabled = !!val;
      // trigger a voice state update to push the spoofed state
      try {
        var stores = window.__DISCORD_STORES__ || {};
        var voiceStore = stores.VoiceStateStore || (window.DiscordNative && window.DiscordNative.voiceStateStore);
        if (window.__DSCOME_SEND_VOICE_STATE__) window.__DSCOME_SEND_VOICE_STATE__();
      } catch(ex) {}
      return _fakeDeafEnabled;
    }
  };

  // Inject UI: a "Fake Deafen" button in the voice controls bar
  function injectFakeDeafButton() {
    // look for the deafen button in Discord's voice controls
    var deafBtn = document.querySelector('[aria-label="Deafen"]') ||
                  document.querySelector('[class*="deafen"]') ||
                  document.querySelector('button[class*="buttonColor"]');
    if (!deafBtn || document.getElementById('dscome-fake-deaf-btn')) return;

    var btn = document.createElement('button');
    btn.id = 'dscome-fake-deaf-btn';
    btn.title = 'Fake Deafen (DsCome)';
    btn.style.cssText = 'background:transparent;border:none;cursor:pointer;padding:4px;margin:0 2px;opacity:0.6;font-size:18px;color:#fff;';
    btn.innerHTML = '🎭';
    btn.onclick = function(e) {
      e.stopPropagation();
      var enabled = window.__DSCOME_FAKE_DEAFEN__.setEnabled(!window.__DSCOME_FAKE_DEAFEN__.isEnabled());
      btn.style.opacity = enabled ? '1' : '0.6';
      btn.style.color = enabled ? '#43b581' : '#fff';
      btn.title = enabled ? 'Fake Deafen ON (DsCome)' : 'Fake Deafen OFF (DsCome)';
    };

    deafBtn.parentNode.insertBefore(btn, deafBtn.nextSibling);
  }

  // poll until voice controls appear
  var _btnInterval = setInterval(injectFakeDeafButton, 2000);
  // also on DOM mutations
  var _obs = new MutationObserver(injectFakeDeafButton);
  _obs.observe(document.body || document.documentElement, { childList: true, subtree: true });
})();
