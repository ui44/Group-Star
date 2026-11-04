"""
Bot Host Manager — Android App
Dev : Blast & DsCome
"""

import os
import sys
import json
import time
import threading
import subprocess
import shutil
from pathlib import Path
from datetime import datetime

from kivy.app import App
from kivy.uix.screenmanager import ScreenManager, Screen, SlideTransition
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.gridlayout import GridLayout
from kivy.uix.scrollview import ScrollView
from kivy.uix.label import Label
from kivy.uix.button import Button
from kivy.uix.textinput import TextInput
from kivy.uix.popup import Popup
from kivy.uix.widget import Widget
from kivy.clock import Clock
from kivy.metrics import dp
from kivy.properties import StringProperty, BooleanProperty, NumericProperty
from kivy.core.window import Window
from kivy.utils import get_color_from_hex

# ── theme colors ─────────────────────────────────────────────
BG       = get_color_from_hex("#0D1117")
CARD     = get_color_from_hex("#161B22")
ACCENT   = get_color_from_hex("#00D4FF")
GREEN    = get_color_from_hex("#00FF88")
RED      = get_color_from_hex("#FF4444")
YELLOW   = get_color_from_hex("#FFD700")
TEXT     = get_color_from_hex("#E6EDF3")
SUBTEXT  = get_color_from_hex("#8B949E")
BORDER   = get_color_from_hex("#30363D")

Window.clearcolor = BG

# ── data dir ─────────────────────────────────────────────────
DATA_DIR  = Path(os.environ.get("ANDROID_PRIVATE", ".")) / "bothost"
LOG_DIR   = DATA_DIR / "logs"
STATE_FILE = DATA_DIR / "state.json"
DATA_DIR.mkdir(parents=True, exist_ok=True)
LOG_DIR.mkdir(exist_ok=True)

RESTART_DELAY = 3
MAX_BOTS = 100

# ── global bot registry ──────────────────────────────────────
bots = {}
bots_lock = threading.Lock()

# ── language detection ───────────────────────────────────────
LANG_MAP = {
    ".py": "python", ".js": "javascript", ".ts": "typescript",
    ".cpp": "cpp", ".cc": "cpp", ".c": "c", ".rs": "rust",
    ".go": "go", ".java": "java", ".rb": "ruby", ".php": "php",
}

def detect_language(filepath):
    return LANG_MAP.get(Path(filepath).suffix.lower(), "unknown")

def ts():
    return datetime.now().strftime("%H:%M:%S")

# ── auto-fixer ───────────────────────────────────────────────
def fix_file(filepath, lang, token):
    fixes = []
    try:
        with open(filepath, "r", encoding="utf-8", errors="ignore") as f:
            content = f.read()
        original = content
        if token:
            for placeholder in ["YOUR_TOKEN_HERE", "TOKEN_HERE", "BOT_TOKEN_HERE"]:
                if placeholder in content:
                    content = content.replace(placeholder, token)
                    fixes.append(f"replaced {placeholder}")
        if "\r\n" in content:
            content = content.replace("\r\n", "\n")
            fixes.append("fixed line endings")
        if content != original:
            with open(filepath, "w", encoding="utf-8") as f:
                f.write(content)
    except Exception as e:
        fixes.append(f"fix error: {e}")
    return fixes

# ── get run command ──────────────────────────────────────────
def get_run_cmd(filepath, lang):
    if lang == "python":
        py = shutil.which("python3") or shutil.which("python") or "python3"
        return [py, filepath]
    elif lang in ("javascript",):
        node = shutil.which("node") or shutil.which("nodejs") or "node"
        return [node, filepath]
    elif lang in ("cpp", "c"):
        out = str(LOG_DIR / f"bot_bin_{Path(filepath).stem}")
        r = subprocess.run(["g++", "-O2", "-o", out, filepath],
                           capture_output=True, timeout=60)
        if r.returncode == 0:
            return [out]
    elif lang == "go":
        return ["go", "run", filepath]
    elif lang == "ruby":
        return ["ruby", filepath]
    elif lang == "php":
        return ["php", filepath]
    return None

# ── BotProcess ───────────────────────────────────────────────
class BotProcess:
    def __init__(self, slot, name, filepath, token, lang):
        self.slot = slot
        self.name = name
        self.filepath = filepath
        self.token = token
        self.lang = lang
        self.proc = None
        self.alive = True
        self.restarts = 0
        self.started = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        self.log_file = LOG_DIR / f"bot_{slot}.log"
        self.last_log = ""
        self._thread = threading.Thread(target=self._run_loop, daemon=True)
        self._thread.start()

    def _run_loop(self):
        while self.alive:
            self._launch()
            if not self.alive:
                break
            time.sleep(RESTART_DELAY)
            self.restarts += 1

    def _launch(self):
        cmd = get_run_cmd(self.filepath, self.lang)
        if not cmd:
            self._log(f"[!!] Cannot run {self.lang} — missing runtime")
            self.alive = False
            return
        self._log(f"[>>] Starting: {' '.join(cmd)}")
        try:
            with open(self.log_file, "a", encoding="utf-8") as lf:
                self.proc = subprocess.Popen(
                    cmd, stdout=lf, stderr=lf,
                    cwd=str(Path(self.filepath).parent)
                )
                self.proc.wait()
            rc = self.proc.returncode
            self._log(f"[!!] Exited (rc={rc}) — restarting in {RESTART_DELAY}s")
        except Exception as e:
            self._log(f"[!!] Error: {e}")

    def _log(self, msg):
        line = f"[{ts()}] {msg}\n"
        self.last_log = msg
        try:
            with open(self.log_file, "a", encoding="utf-8") as f:
                f.write(line)
        except:
            pass

    def get_logs(self, n=50):
        if not self.log_file.exists():
            return "No logs yet"
        with open(self.log_file, "r", encoding="utf-8", errors="ignore") as f:
            lines = f.readlines()
        return "".join(lines[-n:])

    def stop(self):
        self.alive = False
        if self.proc and self.proc.poll() is None:
            self.proc.terminate()

    def is_running(self):
        return self.proc is not None and self.proc.poll() is None

# ── state ────────────────────────────────────────────────────
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
            b = BotProcess(slot, info["name"], info["file"],
                           info["token"], info["lang"])
            with bots_lock:
                bots[slot] = b
    except:
        pass

def next_slot():
    used = set(bots.keys())
    for i in range(1, MAX_BOTS + 1):
        if i not in used:
            return i
    return None

# ── UI helpers ────────────────────────────────────────────────
def mk_btn(text, color=ACCENT, on_press=None, height=dp(48)):
    btn = Button(
        text=text,
        size_hint=(1, None),
        height=height,
        background_normal="",
        background_color=(*color[:3], 0.15),
        color=(*color[:3], 1),
        font_size=dp(14),
        bold=True,
    )
    if on_press:
        btn.bind(on_press=on_press)
    return btn

def mk_label(text, color=TEXT, size=dp(14), bold=False, halign="left"):
    lbl = Label(
        text=text,
        color=color,
        font_size=size,
        bold=bold,
        halign=halign,
        size_hint=(1, None),
    )
    lbl.bind(texture_size=lambda i, v: setattr(i, "height", v[1]))
    return lbl

def mk_input(hint="", password=False, height=dp(44)):
    inp = TextInput(
        hint_text=hint,
        password=password,
        multiline=False,
        size_hint=(1, None),
        height=height,
        background_color=CARD,
        foreground_color=TEXT,
        hint_text_color=SUBTEXT,
        cursor_color=ACCENT,
        font_size=dp(14),
        padding=[dp(12), dp(10)],
    )
    return inp

def show_popup(title, msg, color=TEXT):
    content = BoxLayout(orientation="vertical", padding=dp(16), spacing=dp(12))
    content.add_widget(mk_label(msg, color=color, size=dp(13)))
    btn = mk_btn("OK", color=ACCENT)
    content.add_widget(btn)
    p = Popup(title=title, content=content,
              size_hint=(0.88, None), height=dp(220),
              background_color=(*CARD[:3], 1),
              title_color=ACCENT, separator_color=BORDER)
    btn.bind(on_press=p.dismiss)
    p.open()

# ── Screens ───────────────────────────────────────────────────

class HomeScreen(Screen):
    def __init__(self, **kw):
        super().__init__(**kw)
        self._build()
        Clock.schedule_interval(self._refresh, 2)

    def _build(self):
        root = BoxLayout(orientation="vertical", padding=dp(16), spacing=dp(12))

        # header
        hdr = BoxLayout(size_hint=(1, None), height=dp(56), spacing=dp(8))
        hdr.add_widget(mk_label("Bot Host Manager", color=ACCENT,
                                size=dp(20), bold=True))
        add_btn = Button(text="+ Add Bot", size_hint=(None, None),
                         width=dp(100), height=dp(40),
                         background_normal="", background_color=(*GREEN[:3], 0.2),
                         color=GREEN, font_size=dp(13), bold=True)
        add_btn.bind(on_press=lambda x: setattr(self.manager, "current", "add"))
        hdr.add_widget(add_btn)
        root.add_widget(hdr)

        root.add_widget(mk_label("Nuker Rights : DsCome  |  Dev : Blast & DsCome",
                                 color=SUBTEXT, size=dp(11)))

        # stats bar
        self.stats_label = mk_label("0 bots running", color=YELLOW, size=dp(13))
        root.add_widget(self.stats_label)

        # bot list scroll
        self.scroll = ScrollView()
        self.bot_list = BoxLayout(orientation="vertical", spacing=dp(8),
                                  size_hint_y=None)
        self.bot_list.bind(minimum_height=self.bot_list.setter("height"))
        self.scroll.add_widget(self.bot_list)
        root.add_widget(self.scroll)

        self.add_widget(root)

    def _refresh(self, dt):
        self.bot_list.clear_widgets()
        with bots_lock:
            items = list(bots.items())

        running = sum(1 for _, b in items if b.is_running())
        total   = len(items)
        self.stats_label.text = f"{running}/{total} bots running"

        if not items:
            self.bot_list.add_widget(
                mk_label("No bots added yet.\nTap [+ Add Bot] to start.",
                         color=SUBTEXT, size=dp(13), halign="center"))
            return

        for slot, bot in sorted(items):
            self.bot_list.add_widget(self._bot_card(slot, bot))

    def _bot_card(self, slot, bot):
        card = BoxLayout(orientation="vertical", size_hint=(1, None),
                         height=dp(100), padding=dp(12), spacing=dp(6))

        row1 = BoxLayout(size_hint=(1, None), height=dp(28))
        status_color = GREEN if bot.is_running() else RED
        status_txt = "RUNNING" if bot.is_running() else "STOPPED"
        row1.add_widget(mk_label(f"#{slot}  {bot.name}",
                                 color=TEXT, size=dp(15), bold=True))
        row1.add_widget(mk_label(status_txt, color=status_color,
                                 size=dp(12), halign="right"))
        card.add_widget(row1)

        card.add_widget(mk_label(
            f"Lang: {bot.lang}  |  Restarts: {bot.restarts}  |  {bot.started}",
            color=SUBTEXT, size=dp(11)))

        card.add_widget(mk_label(
            bot.last_log[:60] if bot.last_log else "—",
            color=SUBTEXT, size=dp(11)))

        btns = BoxLayout(size_hint=(1, None), height=dp(32), spacing=dp(6))

        log_btn = Button(text="Logs", size_hint=(1, 1),
                         background_normal="", background_color=(*ACCENT[:3], 0.15),
                         color=ACCENT, font_size=dp(12))
        log_btn.bind(on_press=lambda x, s=slot: self._open_logs(s))

        stop_btn = Button(text="Stop", size_hint=(1, 1),
                          background_normal="", background_color=(*RED[:3], 0.15),
                          color=RED, font_size=dp(12))
        stop_btn.bind(on_press=lambda x, s=slot: self._stop_bot(s))

        restart_btn = Button(text="Restart", size_hint=(1, 1),
                             background_normal="", background_color=(*YELLOW[:3], 0.15),
                             color=YELLOW, font_size=dp(12))
        restart_btn.bind(on_press=lambda x, s=slot: self._restart_bot(s))

        btns.add_widget(log_btn)
        btns.add_widget(restart_btn)
        btns.add_widget(stop_btn)
        card.add_widget(btns)

        return card

    def _open_logs(self, slot):
        with bots_lock:
            b = bots.get(slot)
        if not b:
            return
        self.manager.get_screen("logs").load(slot, b)
        self.manager.current = "logs"

    def _stop_bot(self, slot):
        with bots_lock:
            b = bots.pop(slot, None)
        if b:
            b.stop()
            save_state()

    def _restart_bot(self, slot):
        with bots_lock:
            b = bots.get(slot)
        if b and b.proc and b.proc.poll() is None:
            b.proc.terminate()


class AddScreen(Screen):
    def __init__(self, **kw):
        super().__init__(**kw)
        self._build()

    def _build(self):
        root = BoxLayout(orientation="vertical", padding=dp(20), spacing=dp(14))

        root.add_widget(mk_label("Add New Bot", color=ACCENT,
                                 size=dp(18), bold=True))

        self.name_inp = mk_input("Bot name")
        self.file_inp = mk_input("Bot file path  (e.g. /sdcard/bot.py)")
        self.token_inp = mk_input("Bot token (optional — auto-inject)")

        for w in [mk_label("Name:", color=SUBTEXT, size=dp(12)),
                  self.name_inp,
                  mk_label("File:", color=SUBTEXT, size=dp(12)),
                  self.file_inp,
                  mk_label("Token:", color=SUBTEXT, size=dp(12)),
                  self.token_inp]:
            root.add_widget(w)

        self.status_lbl = mk_label("", color=YELLOW, size=dp(12))
        root.add_widget(self.status_lbl)

        root.add_widget(mk_btn("Start Bot", color=GREEN, on_press=self._start))
        root.add_widget(mk_btn("Cancel", color=RED,
                               on_press=lambda x: setattr(
                                   self.manager, "current", "home")))

        self.add_widget(root)

    def _start(self, *a):
        name  = self.name_inp.text.strip()
        fpath = self.file_inp.text.strip()
        token = self.token_inp.text.strip()

        if not name:
            self.status_lbl.text = "Name required"
            return
        if not fpath or not Path(fpath).exists():
            self.status_lbl.text = f"File not found: {fpath}"
            return

        slot = next_slot()
        if slot is None:
            self.status_lbl.text = "Max 100 bots reached"
            return

        lang = detect_language(fpath)
        self.status_lbl.text = f"Detected: {lang} — fixing file..."

        def _do():
            fixes = fix_file(fpath, lang, token)
            fix_msg = ", ".join(fixes) if fixes else "no fixes needed"

            b = BotProcess(slot, name, fpath, token, lang)
            with bots_lock:
                bots[slot] = b
            save_state()

            def _done(dt):
                self.status_lbl.text = f"Bot #{slot} started! ({fix_msg})"
                self.name_inp.text = ""
                self.file_inp.text = ""
                self.token_inp.text = ""
                Clock.schedule_once(
                    lambda dt: setattr(self.manager, "current", "home"), 1.5)

            Clock.schedule_once(_done, 0)

        threading.Thread(target=_do, daemon=True).start()


class LogsScreen(Screen):
    def __init__(self, **kw):
        super().__init__(**kw)
        self._slot = None
        self._bot = None
        self._build()

    def _build(self):
        root = BoxLayout(orientation="vertical", padding=dp(12), spacing=dp(10))

        hdr = BoxLayout(size_hint=(1, None), height=dp(44))
        self.title_lbl = mk_label("Logs", color=ACCENT, size=dp(16), bold=True)
        back_btn = Button(text="Back", size_hint=(None, 1), width=dp(70),
                          background_normal="", background_color=(*SUBTEXT[:3], 0.2),
                          color=TEXT, font_size=dp(13))
        back_btn.bind(on_press=lambda x: setattr(self.manager, "current", "home"))
        hdr.add_widget(self.title_lbl)
        hdr.add_widget(back_btn)
        root.add_widget(hdr)

        self.refresh_btn = mk_btn("Refresh Logs", color=ACCENT,
                                  on_press=self._refresh_logs)
        root.add_widget(self.refresh_btn)

        scroll = ScrollView()
        self.log_lbl = Label(
            text="", color=TEXT, font_size=dp(11),
            halign="left", valign="top", size_hint=(1, None),
            text_size=(Window.width - dp(32), None),
        )
        self.log_lbl.bind(texture_size=lambda i, v: setattr(i, "height", v[1]))
        scroll.add_widget(self.log_lbl)
        root.add_widget(scroll)

        self.add_widget(root)

    def load(self, slot, bot):
        self._slot = slot
        self._bot = bot
        self.title_lbl.text = f"Logs — #{slot} {bot.name}"
        self._refresh_logs()

    def _refresh_logs(self, *a):
        if self._bot:
            self.log_lbl.text = self._bot.get_logs(100)


# ── App ───────────────────────────────────────────────────────
class BotHostApp(App):
    title = "DsCome Host"

    def build(self):
        sm = ScreenManager(transition=SlideTransition())
        sm.add_widget(HomeScreen(name="home"))
        sm.add_widget(AddScreen(name="add"))
        sm.add_widget(LogsScreen(name="logs"))

        load_state()
        return sm

    def on_stop(self):
        save_state()
        with bots_lock:
            for b in bots.values():
                b.stop()


if __name__ == "__main__":
    BotHostApp().run()
