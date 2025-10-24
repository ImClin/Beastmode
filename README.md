# Beastmode

Beastmode is a Spigot/Paper mini-game plugin focused on a hunter-versus-runner experience. Players queue into arenas where one competitor becomes the Beast who hunts down the rest of the runners before they can reach safety.

## Features
- Queue-based arena management with configurable runner limits.
- Comprehensive arena setup workflow (walls, spawns, delays, finish button).
- Inventory-driven arena editing GUI with guarded placement slots.
- Support for Beastmode join signs and exit tokens that gracefully handle edge cases.

## Requirements
- Java 21 or newer.
- Spigot or Paper server 1.21.x (api-version 1.21).
- Gradle 8 (wrapper included).

## Getting Started
1. Clone the repository:
   ```powershell
   git clone https://github.com/ImClin/Beastmode.git
   cd Beastmode
   ```
2. Build the plugin jar:
   ```powershell
   .\gradlew.bat build
   ```
3. Copy `build\libs\Beastmode-1.0-SNAPSHOT.jar` into your server's `plugins` folder.
4. Start the server; the plugin will create default configuration entries on first run.

## Commands
- `/beastmode` — open the arena management menu (aliases: `/bm`).
- `/beastmode create <name>` — begin the guided arena setup flow.
- `/beastmode edit <name>` — launch the inventory edit interface for an existing arena.
- `/beastmode setspawn <runner|beast>` — set arena spawn points during setup.
- `/beastmode setwaiting` — define the waiting room spawn during setup.
- `/beastmode cancel` — exit the current setup session.

## Permissions
- `beastmode.command` — grants access to all Beastmode commands (default: op).
- `beastmode.preference.vip` — allows players to pick Beast/Runner preference with a 40% Beast weight bonus (and 40% reduction if they opt for Runner).
- `beastmode.preference.njog` — allows preference selection with a 60% Beast weight bonus (and 60% reduction if they opt for Runner).

Players granted a preference permission receive red (Beast) and green (Runner) wool in their first two hotbar slots when queued; right-click to toggle a preference and right-click again to clear it.

## Development Notes
- `src/main/java` contains all plugin source code; `src/main/resources` holds configuration defaults.
- Use `gradlew.bat runServer` to start a local Paper server (configured for 1.21) for testing.
- Build artifacts, IDE metadata, and cache directories are ignored via `.gitignore`.

Contributions and issue reports are welcome—feel free to open a pull request or ticket on GitHub.
