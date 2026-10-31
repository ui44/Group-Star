(function () {
  if (window.__N7RCORDV2_LOADED__) return;
  window.__N7RCORDV2_LOADED__ = true;

  const _rt = (typeof browser !== 'undefined' && browser.runtime)
    ? browser.runtime
    : (typeof chrome !== 'undefined' && chrome.runtime)
    ? chrome.runtime
    : null;

  if (!_rt) { console.warn('[ツcore] no runtime found, bailing'); return; }

  // v3.17.0: picture from the extension's skin folder (skin/picture.jpg|png|webp|gif).
  // Read here (extension side, not blocked by Discord) and sent to the page as a data URI.
  let picData = null, picDone = false;
  const NAMES = ['picture.jpg', 'picture.jpeg', 'picture.png', 'picture.webp', 'picture.gif'];
  function send() { if (picData) window.postMessage({ n7rPicture: picData }, '*'); }
  (async () => {
    for (const n of NAMES) {
      try {
        const r = await fetch(_rt.getURL('skin/' + n));
        if (!r.ok) continue;
        const b = await r.blob();
        if (!b.size) continue;
        picData = await new Promise((res, rej) => { const f = new FileReader(); f.onload = () => res(f.result); f.onerror = rej; f.readAsDataURL(b); });
        break;
      } catch (e) {}
    }
    picDone = true; send();
  })();
  window.addEventListener('message', e => {
    if (e.source === window && e.data && e.data.n7rNeedPicture && picDone) send();
  });

  const s = document.createElement('script');
  s.src = _rt.getURL('injector.js');
  s.onload = () => s.remove();
  (document.head || document.documentElement).appendChild(s);
})();
