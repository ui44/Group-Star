# DsCome — Xposed Module

LSPosed/Xposed module that injects DsCome features into Discord.

## Features

| Feature | Description |
|---------|-------------|
| **Plugins** | Full plugin system (Revenge/Vendetta compatible) |
| **Fake Deafen** | Appear deafened to the server while still hearing & speaking — 🎭 button next to deafen in voice bar |
| **Token Login** | Login via account token — button on login screen |
| **Account Switcher** | Save multiple accounts, switch from settings/logout screen |
| **Nuker ☢** | Server nuker — delete channels, roles, kick/ban all, spam, and more |
| **ツcore Audio** | ツcore v3 audio engine — gain, EQ, voice changer, reverb, noise gate |

## Requirements

- Android with root (Magisk or KernelSU)
- LSPosed framework installed
- Discord app installed

## Installation

1. Install the APK (`releases/DsCome-Xposed.apk`)
2. Open LSPosed → Modules → Enable **DsCome**
3. Scope: select **Discord**
4. Force-stop Discord and reopen

## Building

```bash
cd DsCome-Xposed
./gradlew assembleRelease
```

## Structure

```
app/src/main/
├── kotlin/io/github/revenge/xposed/
│   ├── Main.kt                        — module entry, registers all sub-modules
│   ├── modules/
│   │   ├── DsComeInjectModule.kt      — copies injection scripts to Discord preloads
│   │   ├── HookScriptLoaderModule.kt  — hooks React Native bundle load, runs preloads
│   │   ├── PluginsModule.kt           — plugin system
│   │   └── ...
└── assets/
    ├── dscome_preloads/
    │   ├── 00_fake_deafen.js
    │   ├── 01_token_login.js
    │   ├── 02_nuker.js
    │   └── 03_ucore_loader.js
    └── dscome_ucore/
        └── ucore_injector.js          — ツcore audio engine (847KB)
```
