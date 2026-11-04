# Bot Host Manager — DsCome Edition

> Nuker Rights : DsCome | Dev : Blast & DsCome

Multi-language bot hosting manager. Host up to **100 bots** simultaneously with auto-restart.

## Features

- Multi-language support: Python, JavaScript, TypeScript, C++, Rust, Go, Java, Ruby, PHP, C#
- Auto-restart on crash
- Auto-fix common bot file issues (missing imports, token injection, line endings)
- Live log streaming per bot
- Save/restore state across restarts
- Up to 100 bots running simultaneously

## Install

```bash
pip install colorama
```

## Run

```bash
cd bot-host
python host.py
```

## Supported Languages

| Language   | Extension | Runtime needed     |
|------------|-----------|--------------------|
| Python     | .py       | python3            |
| JavaScript | .js       | node               |
| TypeScript | .ts       | ts-node or tsc     |
| C++        | .cpp/.cc  | g++                |
| C          | .c        | g++                |
| Rust       | .rs       | rustc              |
| Go         | .go       | go                 |
| Java       | .java     | javac + java       |
| Ruby       | .rb       | ruby               |
| C#         | .cs       | dotnet             |
| PHP        | .php      | php                |

## Menu Options

```
1  Add bot       — enter name, file path, token
2  List bots     — show all running bots
3  View logs     — tail last N lines of a bot's log
4  Restart bot   — kill + restart a bot
5  Bot status    — detailed info on one bot
6  Stop bot      — stop one bot
7  Stop ALL      — stop all bots
0  Exit          — shutdown manager
```

## Notes

- Logs saved in `bot_logs/` folder
- State saved in `bots_state.json` — restored on next launch
- Auto-restart delay: 3 seconds
- ICMP mode (stress tester) needs root
