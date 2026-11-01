/*
 * Added to spelunkr.tv after it loads. This is the only code the app puts into the
 * page. It does not read, store or send anything.
 *
 * The app opens the site's own TV mode (https://spelunkr.tv/?tv), which already handles
 * the remote on the guide and watch screens. This fills the gaps elsewhere (About,
 * Directory, sign-in…): arrow keys move between buttons and links whenever the site
 * itself doesn't use the key. Only when nothing is in that direction does the page scroll.
 */
(function () {
  if (window.__spelunkrTv) return;
  window.__spelunkrTv = true;

  var css = document.createElement('style');
  css.textContent = '.sptv-focus:focus { outline: 4px solid #FFD640 !important; outline-offset: 2px; }';
  document.head.appendChild(css);

  var DIRS = { ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right' };
  var FOCUSABLE = 'a[href], button:not([disabled]), input, select, textarea, [tabindex]:not([tabindex="-1"])';

  function visible(el) {
    if (!el.offsetParent && getComputedStyle(el).position !== 'fixed') return false;
    var r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0;
  }

  // Picks the closest element in the given direction, favouring ones that line up.
  function next(from, dir) {
    var a = from.getBoundingClientRect();
    var ax = a.left + a.width / 2, ay = a.top + a.height / 2;
    var vertical = dir === 'up' || dir === 'down';
    var best = null, bestScore = Infinity;
    document.querySelectorAll(FOCUSABLE).forEach(function (el) {
      if (el === from || !visible(el)) return;
      var b = el.getBoundingClientRect();
      var bx = b.left + b.width / 2, by = b.top + b.height / 2;
      var main, cross;
      if (dir === 'up')    { main = a.top - b.bottom;  cross = Math.abs(bx - ax); if (by >= ay) return; }
      if (dir === 'down')  { main = b.top - a.bottom;  cross = Math.abs(bx - ax); if (by <= ay) return; }
      if (dir === 'left')  { main = a.left - b.right;  cross = Math.abs(by - ay); if (bx >= ax) return; }
      if (dir === 'right') { main = b.left - a.right;  cross = Math.abs(by - ay); if (bx <= ax) return; }
      // Stay roughly in that direction: sideways moves must stay on about the same row,
      // up/down may drift sideways a bit more.
      var along = vertical ? Math.abs(by - ay) : Math.abs(bx - ax);
      if (cross > along * (vertical ? 2 : 1)) return;
      var score = Math.max(main, 0) + cross * 2;
      if (score < bestScore) { bestScore = score; best = el; }
    });
    return best;
  }

  // Nothing focused yet: start from the on-screen element nearest the middle.
  function firstOnScreen() {
    var cx = window.innerWidth / 2, cy = window.innerHeight / 2, best = null, bestD = Infinity;
    document.querySelectorAll(FOCUSABLE).forEach(function (el) {
      var r = el.getBoundingClientRect();
      if (!visible(el) || r.top < 0 || r.bottom > window.innerHeight) return;
      var d = Math.hypot(r.left + r.width / 2 - cx, r.top + r.height / 2 - cy);
      if (d < bestD) { bestD = d; best = el; }
    });
    return best;
  }

  // Bubble phase on window: runs after the site's own handlers, and only acts if
  // the site didn't already use the key.
  window.addEventListener('keydown', function (e) {
    var dir = DIRS[e.key];
    if (!dir || e.defaultPrevented || e.altKey || e.ctrlKey || e.metaKey) return;
    var cur = document.activeElement;
    if (cur && /^(INPUT|TEXTAREA|SELECT)$/.test(cur.tagName)) return;
    var target = (cur && cur !== document.body && visible(cur)) ? next(cur, dir) : firstOnScreen();
    if (!target) return;  // nothing that way: let the page scroll
    e.preventDefault();
    target.classList.add('sptv-focus');
    target.focus({ preventScroll: true });
    target.scrollIntoView({ block: 'nearest', inline: 'nearest' });
  });
})();
