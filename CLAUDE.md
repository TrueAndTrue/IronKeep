# IronKeep - Claude Code Guidelines

IronKeep is a Minecraft Paper 1.21.11 prison server plugin (Java 21, Gradle). The single custom
plugin lives at `plugins/ironkeep-core/`. The `server/` directory is git-ignored runtime.

## Build & Deploy

```bash
cd plugins/ironkeep-core
./gradlew.bat build                  # Build + copy JAR to server/plugins/
./gradlew.bat build -x deployPlugin  # Build only, skip copy
```

From project root: `start.bat` builds the plugin and starts the server.

## Project Conventions

### Currency: Whole Numbers Only

All Gold Coin and Shard values MUST be whole numbers everywhere. This is enforced at 4 layers:

1. **CurrencyManager (safety net):** Every mutation method rounds via `Math.round()` before storing. Do NOT remove this.
2. **Callers (reward calculations):** Round computed rewards to `long` BEFORE passing to CurrencyManager or displaying. Pattern:
   ```java
   long goldReward = Math.round(baseGold + skillGoldBonus);
   currencyManager.addBalance(uuid, goldReward);
   ```
3. **Display formatting:** Always use `Math.round()` for currency — never `%.2f` or decimal formatting.
   ```java
   String.format("%,d", Math.round(amount))   // correct
   String.format("%,.2f", amount)              // WRONG for currency
   ```
4. **User input (PayCommand):** Parses with `Long.parseLong()`, rejecting fractional input.

YAML config reward values (`commissions.yml`, `escapes.yml`, `daily-quests.yml`, etc.) should always
be whole numbers. Java fields are `double` for Bukkit API compatibility but values must be integers.

### General Rules

- All source lives in a flat `com.ironkeep` package — no sub-packages.
- Commands use Paper's Brigadier lifecycle API (`BasicCommand` + `LifecycleEvents.COMMANDS`).
- Config is YAML-driven. Runtime player data persists in YAML files under `data/`.
- Player data saves happen immediately on mutation (no batching/flushing).
- Daylight cycle runs normally; weather is locked to clear on startup.
- A clock action bar (30-min intervals, 12h format) is sent to all players every second.
  Do not send competing action bar messages — they will overwrite the clock.

## Detailed Plugin Documentation

See `plugins/ironkeep-core/CLAUDE.md` for comprehensive architecture docs, system flows,
edge cases, configuration reference, and data format specifications.

## Maintaining These Documents

These CLAUDE.md files are **living documents**. When you make changes to the codebase:
1. Review whether your changes affect any documented behavior, flow, or convention.
2. Update the relevant CLAUDE.md section(s) to reflect the new state.
3. If you add a new system, add a new section documenting it.
4. If you remove or replace a system, remove or update its documentation.
5. Never leave stale information — a wrong doc is worse than no doc.
