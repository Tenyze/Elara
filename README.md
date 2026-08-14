# Elara Client

**Elara** is a modern Minecraft 1.8.9 Forge PvP client built on top of the **OneConfig** API. Developed by **Tenyze**, it combines features from multiple well‑known clients into a single, highly customisable package.

Elara integrates natively with OneConfig: settings, HUDs, modules and keybinds are all exposed through the OneConfig GUI rather than a custom click GUI, while a thin adaptation layer keeps the client's own module/event system intact.

---

## Features

- **Modular System**  
  90+ modules across Combat, Movement, Render, World, Utility, Exploit and Misc categories — including KillAura, Scaffold, Velocity, ESP, TargetHUD, NoSlow, Telly, and many more. Each module exposes its own properties (booleans, sliders, modes, colours) and can be toggled and bound independently.

- **OneConfig Integration**  
  All configuration is surfaced through OneConfig. Modules, HUDs, profiles and accounts appear as OneConfig pages, and a dedicated adaptation layer bridges the client's module/property/event system to OneConfig's UI API.

- **Customisable HUDs**  
  PotionHUD, TargetHUD, MusicHUD, SessionHUD and a WaterMark — each repositionable, rescalable, and with glass‑morphism blur / bloom options. Theming supports light, dark and transparent monochrome variants.

- **Integrated Music Player**  
  Play local MP3/FLAC files or stream from the NetEase Cloud Music API. Features lyrics, a real‑time spectrum analyser, album art, a dedicated Music HUD, playlist browsing and a unified `.cache` folder (Audio/Picture/Cover).

- **Profile System**  
  Save and load complete configuration sets (module states, keybinds, HUD layouts) under named profiles and switch between playstyles instantly.

- **Account Manager**  
  Add Microsoft or offline accounts, switch between them and refresh tokens without leaving the game.

- **Developer Friendly**  
  A clean module base, a property system, a mixin layer and a command manager make it straightforward to add new features.

---

## Installation

### Prerequisites
- Java 17 (toolchain) for building; Java 8+ runtime under Minecraft 1.8.9 Forge
- **OneConfig** mod installed (Elara relies on OneConfig at runtime)

### From a Release
1. Download the latest `.jar` from the [releases page](../../releases).
2. Drop it into `.minecraft/mods`.
3. Install [OneConfig](https://github.com/Polyfrost/OneConfig) if you haven't already.
4. Launch Minecraft with the Forge 1.8.9 profile.

### Building from Source
```bash
git clone https://github.com/Tenyze/elara-client.git
cd elara-client
./gradlew clean build
```
The built jar is written to `build/libs/`.

> The build uses a Java 17 toolchain (configured in `build.gradle.kts`). If your `JAVA_HOME` points elsewhere, set it locally via `org.gradle.java.home` in `~/.gradle/gradle.properties` rather than committing it.

---

## Usage

| Key | Action |
|-----|--------|
| `RIGHT_SHIFT` | Open the OneConfig main GUI (Elara pages live here) |
| `R` (configurable) | Toggle KillAura |
| `G` (configurable) | Open the Music Player page |

All keybinds can be remapped inside the OneConfig GUI.

- **Modules** — `Modules` page: enable/disable modules and tweak their properties.
- **HUDs** — `HUD` page: enable HUD components and adjust position, scale, blur, outline and corner radius.
- **Profiles** — `Profiles` page: save the current configuration under a name and restore it later.
- **Accounts** — `Accounts` page: add Microsoft / offline accounts and switch instantly.

---

## Module Categories

| Category | Highlights |
|----------|-----------|
| Combat | KillAura, Criticals, Reach, HitBox, TargetStrafe, Knockback, SuperKnockback, Wtap, SprintReset, Displace, AimAssist, AutoHeal, AutoProjectiles |
| Movement | Speed, Sprint, LongJump, Fly, NoFall, SafeWalk, Eagle, Jesus, AntiVoid, AutoMLG, Clutch, Stasis, KeepSprint |
| Render | ESP, ShaderESP, Chams, Tracers, NameTags, TargetHUD, PotionHUD, WaterMark, ItemGlow, Trajectories, FreeLook, Indicators |
| World | Scaffold, Telly, SpeedMine, AutoBlockIn, BedBreaker, BedESP, BedTracker, BedPlates, ChestESP, ItemESP, Xray, FullBright |
| Utility | AutoClicker, AutoTool, AutoSwap, ChestStealer, InvManager, InvWalk, InventoryClicker, Refill, GhostHand, Spammer, Piercing, MoreKB |
| Exploit | NoSlow, Blink, FakeLag, BackTrack, LagRange, Timer, NoRotate, NoHurtCam, NoJumpDelay, NoHitDelay, FastBow, FastPlace, ServerLag |
| Misc | AntiBot, AntiDebuff, AntiFireball, AntiObbyTrap, AntiObfuscate, ClientSpoofer, Disabler, FlagDetector, HackerDetector, NickHider, Teams, ViewClip, GuiModule, MCF |

---

## Technical Stack

- **Minecraft 1.8.9** + **Forge 11.15.1.2318**
- **OneConfig** (Polyfrost) — UI, config and HUD framework
- **Mixin 0.7.11** — runtime transformations
- **Gradle + Essential Loom** — build toolchain (Java 17 toolchain)
- **Shadow** — dependency bundling
- Libraries bundled at runtime: JLayer (MP3), jflac (FLAC), OkHttp (networking), SLF4J (logging), Lombok (compile-time)

---

## Project Structure

```
src/main/java/elara/
├── Elara.java              # Client entry point
├── command/                # Chat commands (.bind, .toggle, .hide, ...)
├── config/                 # OneConfig pages & HUDs
│   ├── gui/                # AccountManager, Profiles, Music player pages, HUDs
│   └── music/              # Music engine, API, cache, players
├── event/                  # Event bus + event types
├── events/                 # Concrete events (Motion, Packet, SwapItem, ...)
├── management/             # Rotation, Target, Friend, Lag managers
├── mixin/                  # SpongePowered mixins
├── module/                 # All modules by category + Module base
├── property/               # Property system (Bool/Float/Int/Mode/Color/Percent)
└── util/                   # Rendering, networking, misc utilities
```

---

## License

See [LICENSE](LICENSE).

---

## Credits

- **OneConfig** by [Polyfrost](https://github.com/Polyfrost) — UI / config framework
- All library authors credited in `build.gradle.kts`
