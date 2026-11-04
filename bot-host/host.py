#!/usr/bin/env python3
# bot-host/host.py — Multi-Language Bot Hosting Manager
# Nuker Rights : DsCome | Dev : Blast & DsCome
#
# deps: pip install colorama
# run:  python host.py
#
# Supports: Python, JavaScript (Node.js), C++, TypeScript, Rust, Go, Java, Ruby

import os
import sys
import json
import time
import signal
import shutil
import subprocess
import threading
import traceback
from pathlib import Path
from datetime import datetime
from colorama import Fore, Style, init

init(autoreset=True)

R  = Fore.RED
G  = Fore.GREEN
Y  = Fore.YELLOW
C  = Fore.CYAN
M  = Fore.MAGENTA
W  = Fore.WHITE
RE = Style.RESET_ALL
BR = Style.BRIGHT

# ── config ───────────────────────────────────────────────────
MAX_BOTS     = 100
LOG_DIR      = Path("bot_logs")
STATE_FILE   = Path("bots_state.json")
RESTART_DELAY = 3   # seconds before auto-restart

LOG_DIR.mkdir(exist_ok=True)

# ── global bot registry ──────────────────────────────────────
bots = {}       # slot_id -> BotProcess
bots_lock = threading.Lock()

# ── helpers ──────────────────────────────────────────────────
def ts():
    return datetime.now().strftime("%H:%M:%S")

def log(msg, color=W):
    print(f"{C}[{ts()}]{RE} {color}{msg}{RE}")

def log_ok(m):   print(f"{C}[{ts()}]{RE} {G}[OK]{RE} {m}")
def log_err(m):  print(f"{C}[{ts()}]{RE} {R}[!!]{RE} {m}")
def log_info(m): print(f"{C}[{ts()}]{RE} {C}[..]{RE} {m}")
def log_bot(slot, name, m, color=W):
    print(f"{C}[{ts()}]{RE} {M}[BOT #{slot} | {name}]{RE} {color}{m}{RE}")

# ── language detection ───────────────────────────────────────
LANG_MAP = {
    ".py":   "python",
    ".js":   "javascript",
    ".ts":   "typescript",
    ".cpp":  "cpp",
    ".cc":   "cpp",
    ".cxx":  "cpp",
    ".c":    "c",
    ".rs":   "rust",
    ".go":   "go",
    ".java": "java",
    ".rb":   "ruby",
    ".cs":   "csharp",
    ".php":  "php",
}

def detect_language(filepath):
    ext = Path(filepath).suffix.lower()
    return LANG_MAP.get(ext, "unknown")

# ── auto-fixer ───────────────────────────────────────────────
def fix_python(filepath, token):
    """Fix common Python bot issues"""
    with open(filepath, "r", encoding="utf-8", errors="ignore") as f:
        content = f.read()

    original = content
    fixes = []

    # fix missing token placeholder
    if "YOUR_TOKEN_HERE" in content or "BOT_TOKEN" not in content and token:
        # check if token is already in file
        if token not in content:
            # try to inject token near bot.run / client.run
            if "bot.run(" in content:
                content = content.replace('bot.run("")', f'bot.run("{token}")')
                content = content.replace("bot.run('')", f'bot.run("{token}")')
                if 'bot.run("YOUR_TOKEN_HERE")' in content:
                    content = content.replace('bot.run("YOUR_TOKEN_HERE")', f'bot.run("{token}")')
            if "client.run(" in content:
                content = content.replace('client.run("")', f'client.run("{token}")')
                content = content.replace("client.run('')", f'client.run("{token}")')
                if 'client.run("YOUR_TOKEN_HERE")' in content:
                    content = content.replace('client.run("YOUR_TOKEN_HERE")', f'client.run("{token}")')
            fixes.append("injected token")

    # fix missing discord.py import
    if "discord" in content and "import discord" not in content:
        content = "import discord\n" + content
        fixes.append("added import discord")

    # fix missing asyncio import
    if "asyncio" in content.lower() and "import asyncio" not in content:
        content = "import asyncio\n" + content
        fixes.append("added import asyncio")

    # fix Windows-style line endings
    if "\r\n" in content:
        content = content.replace("\r\n", "\n")
        fixes.append("fixed line endings")

    if content != original:
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(content)
        return fixes
    return []

def fix_javascript(filepath, token):
    """Fix common JS/Node bot issues"""
    with open(filepath, "r", encoding="utf-8", errors="ignore") as f:
        content = f.read()

    original = content
    fixes = []

    if token and ("YOUR_TOKEN_HERE" in content or "TOKEN_HERE" in content):
        content = content.replace("YOUR_TOKEN_HERE", token)
        content = content.replace("TOKEN_HERE", token)
        fixes.append("injected token")

    if "\r\n" in content:
        content = content.replace("\r\n", "\n")
        fixes.append("fixed line endings")

    if content != original:
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(content)
        return fixes
    return []

def fix_file(filepath, lang, token):
    """Apply language-specific fixes, return list of fixes made"""
    try:
        if lang == "python":
            return fix_python(filepath, token)
        elif lang in ("javascript", "typescript"):
            return fix_javascript(filepath, token)
    except Exception as e:
        log_err(f"Fixer error: {e}")
    return []

# ── build step (compiled languages) ─────────────────────────
def build_cpp(filepath, slot):
    """Compile C++ file, return path to binary or None"""
    out = LOG_DIR / f"bot_{slot}_binary"
    cmd = ["g++", "-O2", "-o", str(out), filepath, "-lpthread"]
    log_info(f"Compiling C++: {' '.join(cmd)}")
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
    if r.returncode != 0:
        log_err(f"Compile failed:\n{r.stderr}")
        return None
    return str(out)

def build_rust(filepath, slot):
    out = LOG_DIR / f"bot_{slot}_binary"
    r = subprocess.run(["rustc", "-o", str(out), filepath],
                       capture_output=True, text=True, timeout=120)
    if r.returncode != 0:
        log_err(f"Rust compile failed:\n{r.stderr}")
        return None
    return str(out)

def build_java(filepath, slot):
    r = subprocess.run(["javac", filepath],
                       capture_output=True, text=True, timeout=60)
    if r.returncode != 0:
        log_err(f"Java compile failed:\n{r.stderr}")
        return None
    class_name = Path(filepath).stem
    return ("java", class_name, str(Path(filepath).parent))

def build_typescript(filepath, slot):
    r = subprocess.run(["tsc", filepath, "--outDir", str(LOG_DIR)],
                       capture_output=True, text=True, timeout=60)
    if r.returncode != 0:
        log_err(f"TS compile failed:\n{r.stderr}")
        return None
    out = LOG_DIR / (Path(filepath).stem + ".js")
    return str(out)

# ── get run command ──────────────────────────────────────────
def get_run_cmd(filepath, lang, slot):
    """Return (cmd_list, env_dict) or None on failure"""
    env = os.environ.copy()

    if lang == "python":
        py = shutil.which("python3") or shutil.which("python") or "python3"
        return [py, filepath], env

    elif lang == "javascript":
        node = shutil.which("node") or shutil.which("nodejs") or "node"
        return [node, filepath], env

    elif lang == "typescript":
        # try ts-node first, fallback to compile
        tsnode = shutil.which("ts-node")
        if tsnode:
            return [tsnode, filepath], env
        compiled = build_typescript(filepath, slot)
        if compiled:
            node = shutil.which("node") or "node"
            return [node, compiled], env
        return None, env

    elif lang in ("cpp", "c"):
        binary = build_cpp(filepath, slot)
        if binary:
            return [binary], env
        return None, env

    elif lang == "rust":
        binary = build_rust(filepath, slot)
        if binary:
            return [binary], env
        return None, env

    elif lang == "go":
        return ["go", "run", filepath], env

    elif lang == "java":
        result = build_java(filepath, slot)
        if result:
            _, class_name, class_dir = result
            return ["java", "-cp", class_dir, class_name], env
        return None, env

    elif lang == "ruby":
        return ["ruby", filepath], env

    elif lang == "csharp":
        return ["dotnet", "run", "--project", str(Path(filepath).parent)], env

    elif lang == "php":
        return ["php", filepath], env

    else:
        log_err(f"Unknown language: {lang}")
        return None, env

# ── BotProcess ───────────────────────────────────────────────
class BotProcess:
    def __init__(self, slot, name, filepath, token, lang):
        self.slot     = slot
        self.name     = name
        self.filepath = filepath
        self.token    = token
        self.lang     = lang
        self.proc     = None
        self.alive    = True
        self.restarts = 0
        self.started  = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        self.log_file = LOG_DIR / f"bot_{slot}_{name}.log"
        self._thread  = None

    def start(self):
        self._thread = threading.Thread(target=self._run_loop, daemon=True)
        self._thread.start()

    def _run_loop(self):
        while self.alive:
            self._launch()
            if not self.alive:
                break
            self.restarts += 1
            log_bot(self.slot, self.name,
                    f"Restarting in {RESTART_DELAY}s... (restart #{self.restarts})", Y)
            time.sleep(RESTART_DELAY)

    def _launch(self):
        cmd, env = get_run_cmd(self.filepath, self.lang, self.slot)
        if not cmd:
            log_bot(self.slot, self.name, "Failed to get run command — stopping", R)
            self.alive = False
            return

        log_bot(self.slot, self.name, f"Starting: {' '.join(cmd)}", G)

        try:
            with open(self.log_file, "a", encoding="utf-8") as lf:
                self.proc = subprocess.Popen(
                    cmd,
                    stdout=lf,
                    stderr=lf,
                    env=env,
                    cwd=str(Path(self.filepath).parent)
                )
                # stream last line of log to console every 2s
                self._stream_log()
                self.proc.wait()

            rc = self.proc.returncode
            if rc == 0:
                log_bot(self.slot, self.name, f"Exited cleanly (rc=0) — restarting", Y)
            else:
                log_bot(self.slot, self.name, f"Crashed (rc={rc}) — restarting", R)
        except Exception as e:
            log_bot(self.slot, self.name, f"Launch error: {e}", R)

    def _stream_log(self):
        """Tail log file and print new lines to console"""
        def tail():
            try:
                pos = 0
                while self.proc and self.proc.poll() is None:
                    with open(self.log_file, "r", encoding="utf-8", errors="ignore") as f:
                        f.seek(pos)
                        new = f.read()
                        if new:
                            for line in new.splitlines():
                                if line.strip():
                                    log_bot(self.slot, self.name, line, W)
                            pos = f.tell()
                    time.sleep(1)
            except:
                pass
        t = threading.Thread(target=tail, daemon=True)
        t.start()

    def stop(self):
        self.alive = False
        if self.proc and self.proc.poll() is None:
            self.proc.terminate()
            try:
                self.proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.proc.kill()
        log_bot(self.slot, self.name, "Stopped", R)

    def status(self):
        running = self.proc and self.proc.poll() is None
        return {
            "slot":     self.slot,
            "name":     self.name,
            "lang":     self.lang,
            "file":     self.filepath,
            "running":  running,
            "restarts": self.restarts,
            "started":  self.started,
        }

# ── save/load state ──────────────────────────────────────────
def save_state():
    with bots_lock:
        data = {str(s): {"name": b.name, "file": b.filepath,
                          "token": b.token, "lang": b.lang}
                for s, b in bots.items()}
    with open(STATE_FILE, "w") as f:
        json.dump(data, f, indent=2)

def load_state():
    if not STATE_FILE.exists():
        return
    try:
        with open(STATE_FILE) as f:
            data = json.load(f)
        for slot_str, info in data.items():
            slot = int(slot_str)
            log_info(f"Restoring bot #{slot}: {info['name']}")
            add_bot(slot, info["name"], info["file"], info["token"], info["lang"],
                    _save=False)
    except Exception as e:
        log_err(f"State load error: {e}")

# ── core commands ────────────────────────────────────────────
def next_slot():
    used = set(bots.keys())
    for i in range(1, MAX_BOTS + 1):
        if i not in used:
            return i
    return None

def add_bot(slot, name, filepath, token, lang, _save=True):
    with bots_lock:
        if slot in bots:
            log_err(f"Slot {slot} already in use")
            return False
        b = BotProcess(slot, name, filepath, token, lang)
        bots[slot] = b
    b.start()
    if _save:
        save_state()
    return True

def cmd_add():
    slot = next_slot()
    if slot is None:
        log_err(f"Max {MAX_BOTS} bots reached")
        return

    print(f"\n{C}=== Add Bot (Slot #{slot}) ==={RE}")
    name = input(f"{W}Bot name: {RE}").strip()
    if not name:
        log_err("Name required")
        return

    filepath = input(f"{W}Bot file path: {RE}").strip()
    if not filepath or not Path(filepath).exists():
        log_err(f"File not found: {filepath}")
        return

    token = input(f"{W}Bot token (leave empty to skip injection): {RE}").strip()

    lang = detect_language(filepath)
    log_info(f"Detected language: {Y}{lang}{RE}")

    # run fixer
    log_info("Checking file for common issues...")
    fixes = fix_file(filepath, lang, token)
    if fixes:
        log_ok(f"Fixed: {', '.join(fixes)}")
    else:
        log_ok("File looks clean")

    ok = add_bot(slot, name, filepath, token, lang)
    if ok:
        log_ok(f"Bot '{name}' started on slot #{slot}")

def cmd_list():
    with bots_lock:
        if not bots:
            log_info("No bots running")
            return
        print(f"\n{C}{'#':<5} {'Name':<20} {'Lang':<12} {'Running':<10} {'Restarts':<10} {'Started'}{RE}")
        print(C + "-" * 75 + RE)
        for slot in sorted(bots):
            s = bots[slot].status()
            running_str = f"{G}YES{RE}" if s["running"] else f"{R}NO{RE}"
            print(f"{Y}{slot:<5}{RE} {s['name']:<20} {s['lang']:<12} {running_str:<20} {s['restarts']:<10} {s['started']}")
        print()

def cmd_stop():
    slot_str = input(f"{W}Slot # to stop: {RE}").strip()
    if not slot_str.isdigit():
        log_err("Invalid slot")
        return
    slot = int(slot_str)
    with bots_lock:
        if slot not in bots:
            log_err(f"No bot on slot {slot}")
            return
        b = bots.pop(slot)
    b.stop()
    save_state()

def cmd_stopall():
    confirm = input(f"{R}Stop ALL bots? (yes/no): {RE}").strip().lower()
    if confirm != "yes":
        log_info("Cancelled")
        return
    with bots_lock:
        slots = list(bots.keys())
    for slot in slots:
        with bots_lock:
            b = bots.pop(slot, None)
        if b:
            b.stop()
    save_state()
    log_ok("All bots stopped")

def cmd_logs():
    slot_str = input(f"{W}Slot # to view logs: {RE}").strip()
    if not slot_str.isdigit():
        log_err("Invalid slot")
        return
    slot = int(slot_str)
    with bots_lock:
        b = bots.get(slot)
    if not b:
        log_err(f"No bot on slot {slot}")
        return
    lines = input(f"{W}Last N lines [default 50]: {RE}").strip()
    n = int(lines) if lines.isdigit() else 50
    if b.log_file.exists():
        with open(b.log_file, "r", encoding="utf-8", errors="ignore") as f:
            all_lines = f.readlines()
        print(f"\n{C}=== Last {n} lines: {b.name} ==={RE}")
        for line in all_lines[-n:]:
            print(line, end="")
        print()
    else:
        log_info("No logs yet")

def cmd_restart():
    slot_str = input(f"{W}Slot # to restart: {RE}").strip()
    if not slot_str.isdigit():
        log_err("Invalid slot")
        return
    slot = int(slot_str)
    with bots_lock:
        b = bots.get(slot)
    if not b:
        log_err(f"No bot on slot {slot}")
        return
    if b.proc and b.proc.poll() is None:
        b.proc.terminate()
        log_ok(f"Bot #{slot} restarting...")
    else:
        log_info(f"Bot #{slot} already stopped — it will auto-restart")

def cmd_status():
    slot_str = input(f"{W}Slot # for status: {RE}").strip()
    if not slot_str.isdigit():
        log_err("Invalid slot")
        return
    slot = int(slot_str)
    with bots_lock:
        b = bots.get(slot)
    if not b:
        log_err(f"No bot on slot {slot}")
        return
    s = b.status()
    print(f"\n{C}=== Bot #{slot} Status ==={RE}")
    for k, v in s.items():
        color = G if (k == "running" and v) else (R if k == "running" else W)
        print(f"  {Y}{k:<12}{RE} {color}{v}{RE}")
    print()

# ── banner ───────────────────────────────────────────────────
def banner():
    print(C + BR + "=" * 60 + RE)
    print(C + BR + "   BOT HOST MANAGER v1.0 -- DsCome Edition" + RE)
    print(Y + "   Nuker Rights : DsCome  |  Dev : Blast & DsCome" + RE)
    print(C + BR + "   Supports: Python | JS | TS | C++ | Rust | Go | Java" + RE)
    print(C + BR + "=" * 60 + RE)
    print()

def menu():
    print(f"\n{C}{'─' * 40}{RE}")
    print(f"  {G}1{RE}  Add bot")
    print(f"  {C}2{RE}  List bots")
    print(f"  {Y}3{RE}  View logs")
    print(f"  {M}4{RE}  Restart bot")
    print(f"  {W}5{RE}  Bot status")
    print(f"  {R}6{RE}  Stop bot")
    print(f"  {R}7{RE}  Stop ALL bots")
    print(f"  {W}0{RE}  Exit")
    print(f"{C}{'─' * 40}{RE}")

# ── signal handler ───────────────────────────────────────────
def handle_exit(sig, frame):
    print(f"\n{Y}Shutting down...{RE}")
    with bots_lock:
        slots = list(bots.keys())
    for slot in slots:
        with bots_lock:
            b = bots.get(slot)
        if b:
            b.stop()
    sys.exit(0)

signal.signal(signal.SIGINT, handle_exit)
signal.signal(signal.SIGTERM, handle_exit)

# ── main ─────────────────────────────────────────────────────
def main():
    banner()
    log_info(f"Max bots: {MAX_BOTS} | Logs: {LOG_DIR}/ | Auto-restart: ON")
    print()

    # restore saved bots
    load_state()

    while True:
        menu()
        choice = input(f"{W}> {RE}").strip()

        if choice == "1":
            cmd_add()
        elif choice == "2":
            cmd_list()
        elif choice == "3":
            cmd_logs()
        elif choice == "4":
            cmd_restart()
        elif choice == "5":
            cmd_status()
        elif choice == "6":
            cmd_stop()
        elif choice == "7":
            cmd_stopall()
        elif choice == "0":
            handle_exit(None, None)
        else:
            log_err("Unknown option")

if __name__ == "__main__":
    main()
