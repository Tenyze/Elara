// ===================== SCROLL REVEAL =====================
const observerOptions = {
  threshold: 0.15,
  rootMargin: '0px 0px -50px 0px'
};

const observer = new IntersectionObserver((entries) => {
  entries.forEach((entry) => {
    if (entry.isIntersecting) {
      entry.target.classList.add('visible');
      observer.unobserve(entry.target);
    }
  });
}, observerOptions);

document.querySelectorAll('.reveal-up, .reveal-right, .feature-card, .theme-card, .mod-preview, .mod-cards, .music-card, .music-features, .about-card, .cta-card, .mod-tabs')
  .forEach((el) => observer.observe(el));

// ===================== NAV SCROLL =====================
const nav = document.getElementById('nav');
let lastScroll = 0;

window.addEventListener('scroll', () => {
  const currentScroll = window.pageYOffset;
  nav.classList.toggle('scrolled', currentScroll > 50);
  lastScroll = currentScroll;
});

// ===================== COUNT UP =====================
const animateCount = (el) => {
  const target = parseInt(el.dataset.count, 10);
  const duration = 2000;
  const start = performance.now();

  const update = (now) => {
    const progress = Math.min((now - start) / duration, 1);
    const easeOut = 1 - Math.pow(1 - progress, 3);
    el.textContent = Math.floor(easeOut * target);
    if (progress < 1) requestAnimationFrame(update);
  };

  requestAnimationFrame(update);
};

const countObserver = new IntersectionObserver((entries) => {
  entries.forEach((entry) => {
    if (entry.isIntersecting) {
      animateCount(entry.target);
      countObserver.unobserve(entry.target);
    }
  });
}, { threshold: 0.5 });

document.querySelectorAll('.stat-num').forEach((el) => countObserver.observe(el));

// ===================== MODULE TABS =====================
const modData = {
  combat: [
    { icon: '⚔', name: 'KillAura', desc: 'Smart auto-attack with reach, target switching and rotations.' },
    { icon: '💥', name: 'Criticals', desc: 'Always deal critical hits with vanilla swing timing.' },
    { icon: '🎯', name: 'HitBox', desc: 'Expand entity hitboxes for easier clicking.' },
    { icon: '🏹', name: 'Reach', desc: 'Extend your attack range beyond vanilla limits.' },
    { icon: '👟', name: 'SprintReset', desc: 'Cancel sprint on hit for maximum knockback.' },
    { icon: '⚡', name: 'SuperKnockback', desc: 'Deal massive knockback with every hit.' },
    { icon: '🌀', name: 'TargetStrafe', desc: 'Strafe around your target with smart movement.' },
    { icon: '🔫', name: 'AutoProjectiles', desc: 'Automatically shoot projectiles at targets.' },
    { icon: '💊', name: 'AutoHeal', desc: 'Automatically drink pots and heal when low.' },
    { icon: '🪓', name: 'AutoAnduril', desc: 'Automate Anduril sword special attacks.' },
    { icon: '🎲', name: 'Displace', desc: 'Force opponents to move off their spot.' },
    { icon: '🖱', name: 'HitSelect', desc: 'Choose the best target to hit.' }
  ],
  movement: [
    { icon: '🏃', name: 'Speed', desc: 'Boost movement speed with vanilla-like feel.' },
    { icon: '💨', name: 'Sprint', desc: 'Auto-sprint in all directions, even backwards.' },
    { icon: '🪁', name: 'Fly', desc: 'Fly freely with configurable speed and mode.' },
    { icon: '🍂', name: 'NoFall', desc: 'Prevent fall damage from any height.' },
    { icon: '👟', name: 'LongJump', desc: 'Jump further than vanilla allows.' },
    { icon: '🛡', name: 'SafeWalk', desc: 'Prevent walking off edges.' },
    { icon: '🦅', name: 'Eagle', desc: 'Auto-sneak at block edges.' },
    { icon: '🟦', name: 'Jesus', desc: 'Walk on water and lava surfaces.' },
    { icon: '🕳', name: 'AntiVoid', desc: 'Prevent falling into the void.' },
    { icon: '🆘', name: 'AutoMLG', desc: 'Auto-place water to prevent fall damage.' },
    { icon: '🦘', name: 'KeepSprint', desc: 'Maintain sprint even after hitting.' },
    { icon: '⏳', name: 'Stasis', desc: 'Freeze in place while keeping momentum.' }
  ],
  render: [
    { icon: '👁', name: 'ESP', desc: 'See entities through walls with full customisation.' },
    { icon: '✨', name: 'ShaderESP', desc: 'Beautiful shader-based ESP rendering.' },
    { icon: '🔥', name: 'Chams', desc: 'See entities through walls with glow chams.' },
    { icon: '🎯', name: 'TargetHUD', desc: 'Detailed info display on your current target.' },
    { icon: '🧪', name: 'PotionHUD', desc: 'Display active potion effects with timers.' },
    { icon: '💧', name: 'WaterMark', desc: 'Customisable watermark display.' },
    { icon: '🏹', name: 'Tracers', desc: 'Draw lines to entities on screen.' },
    { icon: '📏', name: 'Trajectories', desc: 'Show projectile paths before shooting.' },
    { icon: '🏷', name: 'NameTags', desc: 'Customisable name tags above entities.' },
    { icon: '💡', name: 'ItemGlow', desc: 'Glow effects on dropped items.' },
    { icon: '🎭', name: 'FreeLook', desc: 'Look around without turning your body.' },
    { icon: '📊', name: 'Indicators', desc: 'Display combat indicators on screen.' }
  ],
  world: [
    { icon: '🏗', name: 'Scaffold', desc: 'Auto-place blocks with smart rotations and safe-point.' },
    { icon: '👕', name: 'Telly', desc: 'Teleport using ender pearls or chorus fruit.' },
    { icon: '⛏', name: 'SpeedMine', desc: 'Instantly break blocks with configurable delay.' },
    { icon: '📦', name: 'AutoBlockIn', desc: 'Auto-place blocks in specific scenarios.' },
    { icon: '🛏', name: 'BedBreaker', desc: 'Break beds quickly in the nether.' },
    { icon: '🛏', name: 'BedESP', desc: 'Highlight beds through walls.' },
    { icon: '🛏', name: 'BedTracker', desc: 'Track nearby beds for rush scenarios.' },
    { icon: '🛏', name: 'BedPlates', desc: 'Smart bed placement logic.' },
    { icon: '🏚', name: 'ChestESP', desc: 'Highlight chests and containers through walls.' },
    { icon: '💎', name: 'ItemESP', desc: 'Highlight valuable dropped items.' },
    { icon: '🔦', name: 'FullBright', desc: 'Maximum brightness in dark areas.' },
    { icon: '🔍', name: 'Xray', desc: 'See ores and valuable blocks through terrain.' }
  ],
  utility: [
    { icon: '🖱', name: 'AutoClicker', desc: 'Auto-click with configurable CPS and jitter.' },
    { icon: '⛏', name: 'AutoTool', desc: 'Automatically select the best tool.' },
    { icon: '🔄', name: 'AutoSwap', desc: 'Automatically swap items when needed.' },
    { icon: '📂', name: 'ChestStealer', desc: 'Automatically steal items from chests.' },
    { icon: '🎒', name: 'InvManager', desc: 'Organise and manage your inventory.' },
    { icon: '🚶', name: 'InvWalk', desc: 'Walk while managing inventory.' },
    { icon: '🖱', name: 'InventoryClicker', desc: 'Auto-click inventory slots.' },
    { icon: '📝', name: 'Refill', desc: 'Refill supplies from nearby chests.' },
    { icon: '👻', name: 'GhostHand', desc: 'Interact with blocks through walls.' },
    { icon: '💬', name: 'Spammer', desc: 'Send chat messages automatically.' },
    { icon: '🎯', name: 'Piercing', desc: 'Make projectiles pierce entities.' },
    { icon: '🖐', name: 'MoreKB', desc: 'Increase knockback dealt to opponents.' }
  ],
  exploit: [
    { icon: '🐢', name: 'NoSlow', desc: 'Remove slowdown from items like shields and cobwebs.' },
    { icon: '✨', name: 'Blink', desc: 'Teleport without sending movement packets.' },
    { icon: '🌙', name: 'FakeLag', desc: 'Lag your opponent with delayed packets.' },
    { icon: '⏪', name: 'BackTrack', desc: 'Send delayed position packets to server.' },
    { icon: '🎯', name: 'LagRange', desc: 'Hit opponents beyond normal range via lag.' },
    { icon: '⏱', name: 'Timer', desc: 'Speed up or slow down the game tick.' },
    { icon: '🔄', name: 'NoRotate', desc: 'Prevent server-side rotation corrections.' },
    { icon: '😵', name: 'NoHurtCam', desc: 'Remove the hurt camera shake.' },
    { icon: '🦘', name: 'NoJumpDelay', desc: 'Remove delay between jumps.' },
    { icon: '⛔', name: 'NoHitDelay', desc: 'Remove the hit cooldown after attacking.' },
    { icon: '🏹', name: 'FastBow', desc: 'Instantly shoot a fully drawn bow.' },
    { icon: '🏗', name: 'FastPlace', desc: 'Instantly place blocks without delay.' }
  ],
  misc: [
    { icon: '🤖', name: 'AntiBot', desc: 'Prevent bot detection triggers.' },
    { icon: '💚', name: 'AntiDebuff', desc: 'Prevent debuff application.' },
    { icon: '🔥', name: 'AntiFireball', desc: 'Block incoming fireballs.' },
    { icon: '🪤', name: 'AntiObbyTrap', desc: 'Escape obsidian traps.' },
    { icon: '🔒', name: 'AntiObfuscate', desc: 'Deobfuscate server packets.' },
    { icon: '🎭', name: 'ClientSpoofer', desc: 'Spoof client information.' },
    { icon: '🔌', name: 'Disabler', desc: 'Disable server-side anticheat checks.' },
    { icon: '🚩', name: 'FlagDetector', desc: 'Detect potential anticheat flags.' },
    { icon: '🕵', name: 'HackerDetector', desc: 'Detect players using cheats.' },
    { icon: '👤', name: 'NickHider', desc: 'Hide your real nickname.' },
    { icon: '👥', name: 'Teams', desc: 'Team detection and management.' },
    { icon: '🌀', name: 'ViewClip', desc: 'Render players through walls.' },
    { icon: '📋', name: 'MCF', desc: 'Manage custom Minecraft functions.' }
  ]
};

const modPreviewImg = document.getElementById('modPreviewImg');
const modCards = document.getElementById('modCards');
const modTabs = document.querySelectorAll('.mod-tab');

const previewImages = {
  combat: 'assets/images/Elara ModPage.png',
  movement: 'assets/images/Elara ModPage.png',
  render: 'assets/images/Elara ModPage.png',
  world: 'assets/images/Elara ModPage.png',
  utility: 'assets/images/Elara ModPage.png',
  exploit: 'assets/images/Elara ModPage.png',
  misc: 'assets/images/Elara ModPage.png'
};

const renderModCards = (cat) => {
  const mods = modData[cat] || [];
  modCards.style.opacity = '0';
  modCards.style.transform = 'translateX(20px)';

  setTimeout(() => {
    modCards.innerHTML = mods.map((m) => `
      <div class="mod-card">
        <div class="mod-card-header">
          <div class="mod-card-icon">${m.icon}</div>
          <div class="mod-card-title">${m.name}</div>
        </div>
        <div class="mod-card-desc">${m.desc}</div>
      </div>
    `).join('');
    modPreviewImg.src = previewImages[cat] || previewImages.combat;
    modCards.style.opacity = '1';
    modCards.style.transform = 'translateX(0)';
  }, 250);
};

modTabs.forEach((tab) => {
  tab.addEventListener('click', () => {
    modTabs.forEach((t) => t.classList.remove('active'));
    tab.classList.add('active');
    renderModCards(tab.dataset.cat);
  });
});

// Initial render
renderModCards('combat');

// ===================== SPECTRUM ANIMATION =====================
const spectrumBars = document.querySelectorAll('.spectrum-bar');
spectrumBars.forEach((bar, i) => {
  bar.style.setProperty('--i', i);
  const height = 30 + Math.random() * 60;
  bar.style.setProperty('--h', `${height}%`);
});

// ===================== THEME CARD ACTIVE =====================
document.querySelectorAll('.theme-card').forEach((card) => {
  card.addEventListener('click', () => {
    document.querySelectorAll('.theme-card').forEach((c) => c.classList.remove('active'));
    card.classList.add('active');
    document.querySelectorAll('.btn-apply').forEach((btn) => {
      btn.textContent = 'Apply';
      btn.classList.remove('active');
    });
    card.querySelector('.btn-apply').textContent = 'Active';
    card.querySelector('.btn-apply').classList.add('active');
  });
});

// ===================== SMOOTH SCROLL FOR ANCHORS =====================
document.querySelectorAll('a[href^="#"]').forEach((a) => {
  a.addEventListener('click', (e) => {
    const target = document.querySelector(a.getAttribute('href'));
    if (target) {
      e.preventDefault();
      const navHeight = nav.offsetHeight;
      const targetPos = target.getBoundingClientRect().top + window.pageYOffset - navHeight + 1;
      window.scrollTo({ top: targetPos, behavior: 'smooth' });
    }
  });
});

// ===================== PARALLAX ORBS =====================
const orbs = document.querySelectorAll('.orb');
let mouseX = 0, mouseY = 0;

window.addEventListener('mousemove', (e) => {
  mouseX = (e.clientX / window.innerWidth - 0.5) * 2;
  mouseY = (e.clientY / window.innerHeight - 0.5) * 2;
});

window.addEventListener('scroll', () => {
  const scrollY = window.pageYOffset;
  orbs.forEach((orb, i) => {
    const speed = 0.05 + i * 0.02;
    orb.style.transform = `translate(${mouseX * speed * 50}px, ${mouseY * speed * 50 + scrollY * speed}px)`;
  });
});

// ===================== MODAL (for download button) =====================
document.querySelectorAll('[href="#download"]').forEach((btn) => {
  btn.addEventListener('click', (e) => {
    e.preventDefault();
    const cta = document.querySelector('.cta-section');
    if (cta) {
      cta.scrollIntoView({ behavior: 'smooth' });
    }
  });
});

// ===================== COPY TO CLIPBOARD FOR GITHUB =====================
document.querySelectorAll('.btn-primary').forEach((btn) => {
  btn.addEventListener('click', (e) => {
    if (btn.textContent.includes('Download')) {
      e.preventDefault();
      const cta = document.querySelector('.cta-section');
      if (cta) cta.scrollIntoView({ behavior: 'smooth' });
    }
  });
});

// ===================== KEYBOARD SHORTCUT =====================
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') {
    document.body.style.transform = 'scale(1)';
  }
});

// ===================== LOADING COMPLETE =====================
window.addEventListener('load', () => {
  document.body.classList.add('loaded');
  // Trigger initial observer checks
  observer.callback && observer.callback();
});

// ===================== LAZY LOAD IMAGES =====================
if ('IntersectionObserver' in window) {
  const imgObserver = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        const img = entry.target;
        if (img.dataset.src) {
          img.src = img.dataset.src;
          img.removeAttribute('data-src');
        }
        imgObserver.unobserve(img);
      }
    });
  });

  document.querySelectorAll('img[data-src]').forEach((img) => {
    imgObserver.observe(img);
  });
}

// ===================== CURSOR GLOW EFFECT =====================
const cursorGlow = document.createElement('div');
cursorGlow.style.cssText = `
  position: fixed;
  width: 400px;
  height: 400px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(124, 92, 255, 0.15) 0%, transparent 70%);
  pointer-events: none;
  z-index: 1;
  transform: translate(-50%, -50%);
  transition: opacity 0.3s;
  opacity: 0;
  mix-blend-mode: screen;
`;
document.body.appendChild(cursorGlow);

window.addEventListener('mousemove', (e) => {
  cursorGlow.style.left = e.clientX + 'px';
  cursorGlow.style.top = e.clientY + 'px';
  cursorGlow.style.opacity = '1';
});

window.addEventListener('mouseleave', () => {
  cursorGlow.style.opacity = '0';
});

// Hide on touch devices
if ('ontouchstart' in window) {
  cursorGlow.style.display = 'none';
}
