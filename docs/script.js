/* ===================================================================
   Elara Client — website interactions
   =================================================================== */

const MODULES = {
  combat: [
    { n: 'KillAura', i: '⚔', on: true },
    { n: 'AimAssist', i: '🎯', on: true },
    { n: 'AutoClicker', i: '🖱', on: true },
    { n: 'KeepSprint', i: '👟', on: true },
    { n: 'AutoAnduril', i: '🗡', on: false },
    { n: 'AutoProjectiles', i: '🏹', on: false },
    { n: 'BlockHit', i: '🛡', on: false },
    { n: 'Criticals', i: '💥', on: false },
    { n: 'Displace', i: '🌀', on: false },
    { n: 'HitBox', i: '📐', on: false },
    { n: 'HitSelect', i: '✳', on: false },
    { n: 'Hitflick', i: '⚡', on: false },
    { n: 'Knockback', i: '🥊', on: false },
    { n: 'KnockbackLegacy', i: '🥊', on: false },
    { n: 'Reach', i: '📏', on: false },
    { n: 'SuperKnockback', i: '💢', on: false },
    { n: 'TargetStrafe', i: '🔄', on: false },
    { n: 'Velocity', i: '🌊', on: false },
    { n: 'Wtap', i: '✋', on: false }
  ],
  movement: [
    { n: 'Sprint', i: '🏃', on: true },
    { n: 'NoSlow', i: '🚶', on: true },
    { n: 'Scaffold', i: '🏗', on: true },
    { n: 'Fly', i: '🕊', on: false },
    { n: 'LongJump', i: '🦘', on: false },
    { n: 'Speed', i: '⚡', on: false },
    { n: 'Eagle', i: '🦅', on: false },
    { n: 'NoFall', i: '🍂', on: false },
    { n: 'SafeWalk', i: '🧱', on: false },
    { n: 'AntiVoid', i: '🕳', on: false },
    { n: 'AutoMLG', i: '🪣', on: false },
    { n: 'Clutch', i: '🧗', on: false },
    { n: 'InventoryMove', i: '🎒', on: false },
    { n: 'Stasis', i: '⏸', on: false }
  ],
  render: [
    { n: 'TargetHUD', i: '🎯', on: true },
    { n: 'HUD', i: '🖥', on: true },
    { n: 'ESP', i: '👁', on: false },
    { n: 'Chams', i: '👤', on: false },
    { n: 'NameTags', i: '🏷', on: false },
    { n: 'Tracers', i: '📡', on: false },
    { n: 'Trajectories', i: '📈', on: false },
    { n: 'FreeLook', i: '👀', on: false },
    { n: 'Indicators', i: '📊', on: false },
    { n: 'ItemGlow', i: '✨', on: false },
    { n: 'PotionHUD', i: '🧪', on: false },
    { n: 'ShaderESP', i: '🌈', on: false },
    { n: 'CombatVisuals', i: '💫', on: false },
    { n: 'WaterMark', i: '💧', on: false }
  ],
  utility: [
    { n: 'AutoTool', i: '⛏', on: true },
    { n: 'AutoSwap', i: '🔁', on: false },
    { n: 'ChestStealer', i: '📦', on: false },
    { n: 'InvManager', i: '🗂', on: false },
    { n: 'InventoryClicker', i: '🖱', on: false },
    { n: 'GhostHand', i: '👻', on: false },
    { n: 'MoreKB', i: '🥊', on: false },
    { n: 'Piercing', i: '🔪', on: false },
    { n: 'Refill', i: '🫗', on: false },
    { n: 'Spammer', i: '💬', on: false }
  ],
  world: [
    { n: 'FullBright', i: '☀', on: true },
    { n: 'Scaffold', i: '🏗', on: false },
    { n: 'Telly', i: '📡', on: false },
    { n: 'BedBreaker', i: '🛏', on: false },
    { n: 'BedESP', i: '🛏', on: false },
    { n: 'BedPlates', i: '🛏', on: false },
    { n: 'BedTracker', i: '📍', on: false },
    { n: 'AutoBlockIn', i: '🧱', on: false },
    { n: 'ChestESP', i: '📦', on: false },
    { n: 'ItemESP', i: '📦', on: false },
    { n: 'SpeedMine', i: '⛏', on: false },
    { n: 'Xray', i: '🦴', on: false }
  ],
  exploit: [
    { n: 'BackTrack', i: '⏪', on: false },
    { n: 'Blink', i: '💨', on: false },
    { n: 'FakeLag', i: '🎭', on: false },
    { n: 'FastBow', i: '🏹', on: false },
    { n: 'FastPlace', i: '⚡', on: false },
    { n: 'LagRange', i: '📶', on: false },
    { n: 'NoHitDelay', i: '⏱', on: false },
    { n: 'NoHurtCam', i: '🎥', on: false },
    { n: 'NoJumpDelay', i: '🦘', on: false },
    { n: 'NoRotate', i: '🧭', on: false },
    { n: 'ServerLag', i: '📶', on: false },
    { n: 'Timer', i: '⏱', on: false }
  ],
  misc: [
    { n: 'AntiBot', i: '🤖', on: true },
    { n: 'AntiDebuff', i: '🛡', on: false },
    { n: 'AntiFireball', i: '🔥', on: false },
    { n: 'AntiObbyTrap', i: '🕸', on: false },
    { n: 'AntiObfuscate', i: '🕵', on: false },
    { n: 'ClientSpoofer', i: '🎭', on: false },
    { n: 'Disabler', i: '🔌', on: false },
    { n: 'FlagDetector', i: '🚩', on: false },
    { n: 'HackerDetector', i: '🕵', on: false },
    { n: 'NickHider', i: '🎭', on: false },
    { n: 'Teams', i: '👥', on: false },
    { n: 'ViewClip', i: '🎥', on: false }
  ]
};

/* ---------------- Module grid ---------------- */
const moduleGrid = document.getElementById('moduleGrid');
const moduleTabs = document.getElementById('moduleTabs');

function renderModules(cat) {
  const list = MODULES[cat] || MODULES.combat;
  moduleGrid.innerHTML = list.map(m => `
    <div class="module-card${m.on ? ' is-on' : ''}">
      <div class="mod-ic">${m.i}</div>
      <div class="mod-name">${m.n}</div>
      <span class="mod-state">${m.on ? 'ON' : 'OFF'}</span>
    </div>
  `).join('');
}

moduleTabs.addEventListener('click', (e) => {
  const btn = e.target.closest('.tab');
  if (!btn) return;
  moduleTabs.querySelectorAll('.tab').forEach(t => t.classList.remove('is-active'));
  btn.classList.add('is-active');
  renderModules(btn.dataset.cat);
});

/* ---------------- Music tabs ---------------- */
const musicTabs = document.getElementById('musicTabs');
musicTabs.addEventListener('click', (e) => {
  const btn = e.target.closest('.tab');
  if (!btn) return;
  musicTabs.querySelectorAll('.tab').forEach(t => t.classList.remove('is-active'));
  btn.classList.add('is-active');
  const target = btn.dataset.tab;
  document.querySelectorAll('.music-shots .shot-card').forEach(sc => {
    sc.classList.toggle('show', sc.dataset.shot === target);
  });
});

/* ---------------- Mobile menu ---------------- */
const sidebar = document.getElementById('sidebar');
const mobileToggle = document.getElementById('mobileToggle');
mobileToggle.addEventListener('click', () => sidebar.classList.toggle('open'));

// Close mobile menu on nav click
sidebar.addEventListener('click', (e) => {
  if (e.target.closest('a')) sidebar.classList.remove('open');
});

/* ---------------- Scrollspy ---------------- */
const spySections = [...document.querySelectorAll('main section[id]')];
const navItems = [...document.querySelectorAll('.nav-item[data-spy]')];

function onScroll() {
  const pos = window.scrollY + 140;
  let current = 'home';
  spySections.forEach(sec => {
    if (sec.offsetTop <= pos) current = sec.id;
  });
  navItems.forEach(item => {
    item.classList.toggle('is-active', item.dataset.spy === current);
  });
}
window.addEventListener('scroll', onScroll, { passive: true });
onScroll();

/* ---------------- Counter animation ---------------- */
function animateCount(el) {
  const target = +el.dataset.count;
  const dur = 1200;
  const start = performance.now();
  function tick(now) {
    const p = Math.min((now - start) / dur, 1);
    const eased = 1 - Math.pow(1 - p, 3);
    el.textContent = Math.round(eased * target);
    if (p < 1) requestAnimationFrame(tick);
  }
  requestAnimationFrame(tick);
}

/* ---------------- Reveal ---------------- */
const revealEls = document.querySelectorAll('.section-head, .shot-card, .theme-card, .music-feature, .clog-card, .oc-v-card, .oc-note, .meta-row, .module-grid');
const revealObserver = new IntersectionObserver((entries) => {
  entries.forEach(entry => {
    if (entry.isIntersecting) {
      entry.target.classList.add('reveal', 'in');
      revealObserver.unobserve(entry.target);
    }
  });
}, { threshold: 0.12 });

revealEls.forEach(el => revealObserver.observe(el));

/* ---------------- Counters on view ---------------- */
const statObserver = new IntersectionObserver((entries) => {
  entries.forEach(entry => {
    if (entry.isIntersecting) {
      animateCount(entry.target);
      statObserver.unobserve(entry.target);
    }
  });
}, { threshold: 0.6 });
document.querySelectorAll('.stat-num').forEach(el => statObserver.observe(el));

/* ---------------- Init ---------------- */
renderModules('combat');