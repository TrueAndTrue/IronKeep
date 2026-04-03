# IronKeep Core Plugin — Architecture & Development Guide

This is the comprehensive reference for the IronKeep prison server plugin. It covers every system,
data flow, edge case, and convention. **This file is a living document** — update it when you change
the codebase. See the [Maintaining This Document](#maintaining-this-document) section at the bottom.

---

## Table of Contents

1.  [Architecture Overview](#architecture-overview)
2.  [Startup Sequence](#startup-sequence)
3.  [Commission System (Core Gameplay Loop)](#commission-system)
4.  [Currency System](#currency-system)
5.  [Ranking System](#ranking-system)
6.  [Escape (Prestige) System](#escape-prestige-system)
7.  [Skill Tree System](#skill-tree-system)
8.  [Mail Sorting Minigame](#mail-sorting-minigame)
9.  [Cooking Minigame](#cooking-minigame)
10. [Zone System & Block Regeneration](#zone-system--block-regeneration)
11. [Commission Board GUI](#commission-board-gui)
12. [Daily Quest System](#daily-quest-system)
13. [Starter Kit & New Player Flow](#starter-kit--new-player-flow)
14. [Warden NPC](#warden-npc)
15. [Sidebar Scoreboard](#sidebar-scoreboard)
16. [Commands Reference](#commands-reference)
16. [Configuration Files Reference](#configuration-files-reference)
17. [Data Persistence Reference](#data-persistence-reference)
18. [Critical Invariants & Edge Cases](#critical-invariants--edge-cases)
19. [Maintaining This Document](#maintaining-this-document)

---

## Architecture Overview

### Dependency Graph

```
IronKeepPlugin (main class, wires everything)
├── CommissionRegistry        ← commissions.yml (read-only)
├── RankManager               ← ranks.yml + data/player-ranks.yml
├── EscapeManager             ← escapes.yml + data/player-escapes.yml
├── SkillManager              ← skills.yml + data/skill-levels.yml
├── DailyQuestManager         ← daily-quests.yml + data/daily-quest.yml
├── DailyBonusManager         ← config.yml daily-commission-bonus + data/daily-commission-bonus.yml
├── ZoneManager               ← zones.yml (read-only)
├── MailRoomManager           ← mail-room.yml (read-only, runtime state in memory)
├── KitchenManager            ← kitchen.yml (read-only, runtime state in memory)
├── BlockRegenManager         ← zones.yml regen config (listener)
├── CurrencyManager           ← balances.yml
├── CommissionStateStore      ← player-commissions.yml
├── CommissionManager         ← orchestrates all of the above (injected via setters)
├── CommissionBoardManager    ← config.yml board locations
├── StarterKitConfig/Manager  ← starter-kit.yml + received-kits.yml
├── WardenManager             ← config.yml warden spawn
└── SidebarManager            ← per-player scoreboard sidebar (no config)
```

### Key Design Patterns

- **Flat package:** All classes in `com.ironkeep`. No sub-packages.
- **Setter injection for CommissionManager:** Avoids circular dependency — managers are created
  first, then injected into CommissionManager via `setXxxManager()`.
- **Immediate persistence:** Every state mutation (balance change, rank up, skill XP) saves to
  disk immediately. No batch saves or flush-on-disable.
- **YAML-driven config:** All game tuning is in YAML files under `src/main/resources/`. Runtime
  player data goes in YAML files under the plugin's `data/` folder.
- **Paper Brigadier commands:** All commands implement `BasicCommand` and register via
  `LifecycleEvents.COMMANDS`. Tab completion via `suggest()`.

---

## Startup Sequence

`IronKeepPlugin.onEnable()` runs in this order:

1. `saveDefaultConfig()` — extracts config.yml if missing
2. Load managers (CommissionRegistry, RankManager, EscapeManager, SkillManager,
   DailyQuestManager, ZoneManager, MailRoomManager, KitchenManager, BlockRegenManager,
   CurrencyManager, CommissionStateStore)
3. Create CommissionManager, inject all manager dependencies via setters
4. Load CommissionBoardManager, register board listener, schedule `placeBoards()` at 1 tick
5. Load StarterKitConfig/Manager, register StarterKitListener
6. Register progress listeners: WoodcuttingListener, MiningListener, FarmingListener,
   MailSortingListener, KitchenListener
7. Create WardenManager, register WardenListener, schedule `spawnWarden()` at 20 ticks
8. Register all commands (CommissionCommand, BalanceCommand, PayCommand, etc.)
9. Schedule world setup (1 tick delay): enable daylight cycle, lock weather to clear
10. Start clock action bar task (repeats every 20 ticks / 1 second)

**Timing matters:** Board placement and warden spawn are delayed to let the world load first.

---

## Commission System

The commission system is the core gameplay loop. Players accept tasks, complete objectives, and
earn currency rewards.

### Lifecycle

```
[Player accepts commission]
    ↓
assignCommission(player, id)
    ├── Validate: no active commission, definition exists, rank permits type
    ├── Apply skill-based objective reduction: Math.max(1, default - reduction)
    ├── Create PlayerCommissionState (id, progress=0, overrideQuantity if reduced)
    ├── Persist state
    ├── For MAIL_SORTING: call mailRoomManager.assignMail()
    └── For COOKING: call kitchenManager.assignRecipe()
    ↓
[Player performs objective — events tracked by type-specific listeners]
    ↓
incrementProgress(uuid, amount)
    ├── Update progress count
    ├── Persist state
    └── If progress >= effectiveQuantity: notify "use /commission complete"
    ↓
[Player runs /commission complete]
    ↓
completeCommission(player)
    ├── For MAIL_SORTING → completeMailSorting() [different flow]
    ├── For COOKING → completeCooking() [different flow]
    ├── Standard: check progress >= effectiveQuantity
    ├── Check turn-in items in inventory, remove them
    ├── Calculate reward: base → escape bonus → skill bonus → round to long
    ├── Grant Gold Coins + Shards + skill XP
    ├── Clear commission state
    └── Display reward breakdown
```

### Commission Types and Their Listeners

| Type | Listener | Tracked Event | Special Behavior |
|------|----------|--------------|-----------------|
| MINING_* | MiningListener | BlockBreakEvent | Accepts deepslate variants (DEEPSLATE_COAL_ORE etc.) |
| WOODCUTTING | WoodcuttingListener | BlockBreakEvent | Optional axe requirement (config) |
| FARMING | FarmingListener | BlockBreakEvent | Only fully-grown crops (ageable.age == maxAge) |
| MAIL_SORTING | MailSortingListener | PlayerInteractEvent (barrel) | Own state machine, accuracy-based |
| COOKING | KitchenListener | Multiple events | Own state machine, accuracy-based |

### Reward Calculation (Standard Commissions)

```
baseGold = commissionDef.getRewardAmount()
baseGold = escapeManager.applyBonus(uuid, baseGold)        // multiply by escape level
skillGoldBonus = baseGold * skillManager.getGoldBonus()     // % of post-escape gold
skillShardsBonus = baseShardsReward * skillManager.getShardsBonus()
goldReward = Math.round(baseGold + skillGoldBonus)          // MUST round to long
shardsReward = Math.round(baseShardsReward + skillShardsBonus)
```

**Key rule:** Escape bonus applies to Gold Coins ONLY. Shards are NEVER affected by escape bonus.

### Reward Calculation (Mail Sorting / Cooking)

Same as standard, plus an accuracy bonus:
```
accuracyGoldBonus = Math.round(maxGoldBonus * accuracy / 100.0)
accuracyShardsBonus = Math.round(maxShardsBonus * accuracy / 100.0)
totalGold = baseGold + accuracyGoldBonus + skillGoldBonus
totalShards = baseShardsReward + accuracyShardsBonus + skillShardsBonus
```

### Effective Quantity (Skill Reduction)

On assignment, the required quantity may be reduced by the player's skill level:
```
reduction = (skillLevel - 1) / reductionEveryLevels   // integer division
effective = Math.max(1, default - reduction)           // minimum 1
```
Stored in `PlayerCommissionState.overrideQuantity`. Used by `getEffectiveQuantity()`.

---

## Currency System

Two currencies, both stored as doubles but always whole numbers.

| Currency | Tradeable | Escape Bonus | Earned From |
|----------|-----------|-------------|-------------|
| Gold Coins | No (server↔player only) | Yes | Commissions, daily quest |
| Shards | Yes (player↔player via /pay) | No | Commissions, daily quest |

### Whole-Number Enforcement (4 Layers)

1. **CurrencyManager:** `addBalance()`, `setBalance()`, `addShards()`, `removeShards()`,
   `setShards()` all call `Math.round()` on the stored result. This is the safety net.
2. **Callers:** All reward calculations round to `long` before passing to CurrencyManager.
3. **Display:** All format methods use `Math.round()` — never decimal formatting for currency.
4. **Input:** `/pay` parses with `Long.parseLong()`, rejecting fractional amounts.

**Do NOT weaken any of these layers.** If you add a new way to grant or display currency, follow
the same pattern: compute as double → `Math.round()` to long → pass/display.

### CurrencyManager API

```java
// Gold Coins (non-tradeable)
double getBalance(UUID)
void addBalance(UUID, double amount)     // rounds internally
void setBalance(UUID, double amount)     // rounds, clamps >= 0

// Shards (tradeable)
double getShards(UUID)
void addShards(UUID, double amount)      // rounds internally
boolean removeShards(UUID, double amount) // rounds, returns false if insufficient
void setShards(UUID, double amount)      // rounds, clamps >= 0
boolean hasShards(UUID, double amount)
```

### Backward Compatibility

`CurrencyManager.load()` supports an old flat format (`uuid: value`) in balances.yml and migrates
it to the current `gold-coins.<uuid>` / `shards.<uuid>` format on next save.

---

## Ranking System

4 ranks with linear progression. Each rank unlocks new commission types **cumulatively** — a Rank 3
player can access Rank 1 + 2 + 3 commission types.

| Rank | Name | Cost (Gold Coins) | Unlocks |
|------|------|-------------------|---------|
| 1 | Inmate | 0 (starting) | MINING_COAL, MAIL_SORTING, COOKING |
| 2 | Worker | 100 | WOODCUTTING |
| 3 | Laborer | 250 | FARMING |
| 4 | Trustee | 500 | MINING_IRON, MINING_GOLD, MINING_DIAMOND |

### Rank-Up Flow (`RankUpCommand`)

1. Verify not at max rank
2. Check sufficient Gold Coins for next rank's cost
3. Deduct cost via `addBalance(uuid, -cost)`
4. Set new rank, persist
5. **Cancel active commission if the new rank's cumulative types no longer include it** — this
   guards against edge cases where a rank restructure removes access to a previously-allowed type.

### Key APIs

```java
rankManager.getPlayerRank(UUID)                  // defaults to 1
rankManager.canAccept(UUID, commissionType)      // cumulative check
rankManager.getAccessibleCommissions(UUID)       // filtered list for board/random assignment
rankManager.getCumulativeUnlockedTypes(UUID)     // union of rank 1..current
```

---

## Escape (Prestige) System

Players at Rank 4 can `/escape` to reset their rank to 1 in exchange for a permanent Gold Coin
income multiplier. Shards are NEVER touched by escape.

| Escape Level | Cost | Multiplier |
|-------------|------|-----------|
| 1 | 600 | 1.10x (10% bonus) |
| 2 | 700 | 1.20x (20% bonus) |
| 3 | 800 | 1.30x (30% bonus) |

### Escape Execution Flow

1. Verify player is Rank 4, not at max escape, has sufficient Gold Coins
2. Deduct escape cost
3. **Set Gold Coins to 0** (full reset — not just the cost)
4. Reset rank to 1
5. Cancel active commission
6. Increment escape level, persist
7. Shards balance is untouched

### Where Escape Bonus Applies

- Commission Gold Coin rewards (all types, including mail/cooking base reward)
- Daily quest Gold Coin reward
- **NOT** applied to: Shards (ever), accuracy bonuses (applied after escape), skill XP

---

## Skill Tree System

Per-commission-type XP and leveling. 5 skill categories (MINING, WOODCUTTING, FARMING,
MAIL_SORTING, COOKING) with independent progression.

### XP & Leveling

- XP granted on commission completion: configurable per type in `skills.yml`
- XP required per level: `xpCurveBase * level^1.5` (e.g. level 1→2 = 200 XP)
- Level cap: 50 (configurable)
- At max level, XP is set to 0 (no overflow)

### Skill Bonuses (Per Level)

| Bonus | Formula | Example at Level 10 |
|-------|---------|-------------------|
| Gold Coins % | (level-1) * goldBonusPerLevel / 100 | +18% |
| Shards % | (level-1) * shardBonusPerLevel / 100 | +9% |
| Objective reduction | (level-1) / reductionEveryLevels | -0 (first at level 11) |

**Note:** MINING skill applies to ALL mining commission types (MINING_COAL, MINING_IRON, etc.).

### Skill GUI (`/skills`)

Two-level inventory GUI:

**Level 1 (27 slots):** Overview of all 5 skills at slots 10, 12, 14, 11, 13. Each shows level
and XP. Click to drill into Level 2.

**Level 2 (54 slots):** Single skill detail.
- Slot 4: Summary (XP bar, active bonuses)
- Slots 9-53: Level milestones (LIME_GLASS=unlocked, GRAY_GLASS=locked, GOLD_NUGGET=current)
- Current level (gold nugget) shows next level rewards + XP remaining
- Every 10th level has a milestone marker ("-1 required item")
- Slot 49: Back arrow

**GUI identification:** Level 1 and Level 2 titles are matched by exact string in the
InventoryClickEvent handler. The handler only cancels events for its own GUIs — it does NOT
cancel clicks in unrelated inventories.

---

## Mail Sorting Minigame

A commission type with its own state machine. Players receive paper "mail" items, deliver them to
labeled barrels, and earn accuracy-based bonuses.

### State Machine

```
[Commission assigned]
    ↓
assignMail(player, rankNum)
    ├── Resolve rank → mail count + destination pool
    ├── Generate N mail items (PAPER with persistent data: mail_destination)
    ├── Give items to player
    └── Create MailSortingState(totalMail, delivered=0, correct=0)
    ↓
[Player right-clicks barrel with mail in hand]
    ↓
handleDelivery(player, barrel, mailItem)
    ├── Consume 1 mail from held stack
    ├── Compare mail destination to barrel destination
    ├── If match: incrementCorrect(), green message
    ├── If mismatch: red message (shows correct destination)
    ├── incrementDelivered()
    └── CommissionManager.incrementProgress() for standard tracking
    ↓
[All mail delivered: state.isComplete() = true]
    ↓
/commission complete → completeMailSorting()
    ├── Calculate accuracy: correct / total * 100%
    ├── Calculate bonuses (accuracy + escape + skill)
    ├── Grant rewards, clear mail, clear state
    └── Display detailed breakdown
```

### Item Lock-Down

Mail items cannot be:
- Dropped (`PlayerDropItemEvent` cancelled)
- Moved to external inventories (`InventoryClickEvent` cancelled for non-player inventory)
- Kept after commission ends (`clearMail()` removes all from inventory)

### Zone Tracking (Lazy Entry Gate)

`MailSortingListener.onPlayerMove()` tracks zone entry/exit:
- Sets `enteredZone = true` when player first enters the mail sorting zone
- Only cancels commission when player was in zone AND then leaves
- **Why:** Prevents accidental cancellation when walking away from Commission Board after accepting

This same pattern is used by `KitchenListener`.

---

## Cooking Minigame

A commission type where players collect ingredients from item frames, arrange them in a cauldron
GUI in the correct order, and earn accuracy-based bonuses.

### State Machine

```
[Commission assigned]
    ↓
assignRecipe(player, rankNum)
    ├── Filter recipes by rank-tier <= playerRank
    ├── Pick random (avoid repeating last recipe if multiple available)
    ├── Create CookingState(recipeId)
    └── Display ingredient list message
    ↓
[Player right-clicks ingredient item frame]
    ↓
handleItemFrameClick(player, frame)
    ├── Check player doesn't already have this ingredient
    ├── Give tagged copy (persistent data: cooking_ingredient)
    └── Send pickup message
    ↓
[Player right-clicks cauldron in zone]
    ↓
openCauldronGui(player)
    └── 27-slot GUI with N ingredient placeholders + recipe book + confirm button
    ↓
[Player arranges ingredients in slots]
    ↓
handleCauldronClick(player, event)
    ├── Place ingredient: consume from cursor, replace placeholder
    ├── Pickup ingredient: restore placeholder, return to cursor
    └── Swap: swap ingredient and cursor
    ↓
[Player clicks confirm (GREEN_CONCRETE)]
    ↓
processConfirm(player)
    ├── Verify all N slots filled
    ├── Lock in order: state.setConfirmedIngredients(placed)
    ├── state.setConfirmedOrder(true)
    └── Close GUI (next tick to avoid event conflict)
    ↓
/commission complete → completeCooking()
    ├── Accuracy: count positions where confirmed[i] == expected[i]
    ├── Calculate bonuses (accuracy + escape + skill)
    ├── Grant rewards
    ├── clearCooking(player) — remove ingredients, clear state
    └── Display detailed breakdown
```

### Cleanup on GUI Close

`KitchenListener.onInventoryClose()` returns any cooking ingredients from GUI slots back to the
player's inventory. Overflow items are dropped at the player's location.

### Item Lock-Down

Same as mail sorting: cooking ingredients cannot be dropped or moved to external inventories.

---

## Zone System & Block Regeneration

### Zones

Zones define bounded 3D regions where commission progress is valid. Defined in `zones.yml`.

```java
zoneManager.isInValidZone(location, commissionType)
// Returns true if any zone for that type contains the location
// If NO zone is configured for the type: depends on block-without-zone config
```

Each zone has: world, x1/z1/x2/z2, yMin/yMax, commission-types list, optional border-y.

### Block Regeneration

`BlockRegenManager` (listener) restores broken blocks after a configurable delay:

| Commission Type | Placeholder Block | Behavior |
|----------------|------------------|----------|
| MINING | BEDROCK (configurable) | Replace ore with placeholder, restore after delay |
| WOODCUTTING | BEDROCK (configurable) | Replace log with placeholder, restore after delay |
| FARMING | N/A | Only trigger for fully-grown crops; protect farmland below from trampling |

**Regen workflow:**
1. Record original BlockData
2. Set placeholder block immediately
3. Schedule restore after `regen-delay-ticks` (default 60 = 3 seconds)
4. On restore: verify placeholder still exists (admin may have removed it), then restore original

**Farmland protection:** While a crop is regenerating, the farmland block beneath it is protected
from trampling via `PlayerInteractEvent(PHYSICAL)` cancellation.

**Survival players cannot break active placeholders** — `BlockBreakEvent` cancelled for non-creative.

---

## Commission Board GUI

Physical in-world blocks (OAK_WALL_SIGN by default) that open a chest GUI when right-clicked.

### GUI Layout (27 slots)

**With active commission:**
- Slot 11: Active commission (type icon + lore: description, progress, reward)
- Slot 15: Complete button (CHEST)
- Slot 22: Balance display (GOLD_INGOT)

**Without active commission:**
- Slots 10-16, 19+: Rank-filtered available commissions (clickable, has persistent data)
- Slot 22: Balance display
- Click commission → `assignCommission(player, id)`

**Board protection:** Non-OP players cannot break board blocks.

**Inventory holder pattern:** Uses `BoardHolder` (custom `InventoryHolder`) to identify board GUIs
in the InventoryClickEvent handler. All clicks in board GUIs are cancelled.

---

## Daily Quest System

Single daily quest: type "Hi" in chat (case-insensitive, trimmed).

### Flow

1. `AsyncPlayerChatEvent` detects "hi" message
2. Check not already completed today (epoch-based, midnight UTC reset)
3. Switch to main thread (async chat event cannot modify game state)
4. Calculate gold reward with escape bonus, round to long
5. Grant Gold Coins + Shards, mark completed
6. Display reward message

### `/daily` Command

Shows: objective, reward (with escape bonus for display), completion status, next reset time (UTC).

**Important:** Escape bonus applies to Gold Coin reward only, not Shards.

---

## Daily Commission Bonus

Players earn bonus Gold Coins and skill XP the first time they complete each distinct commission
type per day. Resets at midnight UTC (same cycle as daily quest).

### Configuration (config.yml)

```yaml
daily-commission-bonus:
  gold-bonus: 50    # Extra Gold Coins for first completion of each type per day
  xp-bonus: 50      # Extra skill XP for first completion of each type per day
```

### How It Works

- Each commission type (MINING_COAL, MINING_IRON, WOODCUTTING, FARMING, MAIL_SORTING, COOKING,
  etc.) has its own independent daily bonus.
- On commission completion, `grantDailyBonus()` checks if the player has already claimed the
  bonus for that type today. If not, it grants the bonus gold + XP and marks it claimed.
- The bonus gold follows the whole-number currency convention (config values are integers).
- The bonus XP is granted to the same skill type as the commission's normal XP.
- The bonus message appears between the reward summary and the balance display:
  `[Daily Bonus] First Mining Coal commission today! +50 Gold Coins, +50 XP`

### Persistence

- File: `data/daily-commission-bonus.yml`
- Format: `<uuid>.<TYPE>: <epoch seconds>` — timestamp of when the bonus was claimed
- Resets automatically when the timestamp is before the current day's midnight UTC start

### Integration Points

- `DailyBonusManager` is injected into `CommissionManager` via setter.
- `grantDailyBonus()` is called in all three completion paths: standard, mail sorting, cooking.
- Called AFTER normal rewards are granted but BEFORE the balance summary is displayed, so the
  balance lines reflect the bonus amount.

---

## Starter Kit & New Player Flow

### First Join

`StarterKitListener.onPlayerJoin()`:
1. Check if UUID is in `received-kits.yml` (cached set)
2. If not received: grant all items from `starter-kit.yml`, mark received, teleport to
   `new-player-spawn` (if configured), display welcome message

Items that don't fit in inventory are dropped at the player's feet.

### Starter Kit Config

Items defined in `starter-kit.yml` with material, quantity, optional display-name and enchantments.
Enchantments use lowercase Bukkit keys (e.g. `efficiency`, `durability`).

---

## Warden NPC

A Villager entity spawned at the configured location with:
- Invulnerable, silent, no AI, no gravity, persistent
- Custom name "Warden" (gray)
- Scoreboard tag `ironkeep_warden`

### First-Time Dialogue

`WardenListener.onPlayerJoin()` — for players not in `warden-seen.yml`:
1. Queue 3 dialogue lines with delays (1s, 3s, 5s)
2. After last line: assign first commission (`warden.first-commission` config, default `mining_coal`)
3. Mark player as seen

**Protection:** Warden cannot be damaged, targeted by mobs, or interacted with by players.

---

## Sidebar Scoreboard

A permanent right-side sidebar displayed to every online player via `SidebarManager`.

### Layout

```
§6§lIronKeep        ← objective display name (title)
PlayerName          ← score 5 (yellow)
Rank: Inmate        ← score 4 (gray label, green value)
Escape: Lv.0        ← score 3 (gray label, aqua value)
                    ← score 2 (blank separator)
Gold: 1,234         ← score 1 (gray label, gold value)
Shards: 567         ← score 0 (gray label, light-purple value)
```

### Implementation

- Each player gets a dedicated `Scoreboard` instance so values are player-specific.
- Lines use the team-prefix trick: each line is a unique invisible color-code entry (e.g. `"§0§r"`);
  a registered `Team` holds the entry and its `prefix` is updated with the visible text.
- Scores are set once and never change; only team prefixes are updated on refresh.
- Refreshes every 20 ticks (1 second) via a `runTaskTimer` in `SidebarManager.start()`.
- `PlayerJoinEvent`: calls `setup(player)` with a 2-tick delay so managers have loaded player data.
- `PlayerQuitEvent`: removes the board from the internal map (GC handles the Scoreboard object).

### Invariant

Do not send competing scoreboard assignments from other systems — calling `player.setScoreboard()`
elsewhere will replace IronKeep's sidebar. If another system needs a scoreboard, it must either
use the same `Scoreboard` object retrieved from `SidebarManager` or avoid `setScoreboard()`.

---

## Commands Reference

| Command | Class | Permission | Description |
|---------|-------|-----------|-------------|
| `/commission new\|status\|complete\|list\|skip\|choose` | CommissionCommand | skip/choose: OP | Manage commissions |
| `/balance` | BalanceCommand | All | Show Gold Coins + Shards |
| `/pay <player> <amount>` | PayCommand | All | Transfer Shards (whole numbers only) |
| `/rankup` | RankUpCommand | All | Rank up for Gold Coins |
| `/rank` | RankCommand | All | View rank, next cost, escape info |
| `/escape` | EscapeCommand | All | Prestige (requires Rank 4) |
| `/skills` | SkillCommand | All | Open skill tree GUI |
| `/daily` | DailyQuestListener | All | Check daily quest status |
| `/mailroom setup` | MailRoomCommand | OP | Place mail room barrels |
| `/kitchen setup` | KitchenCommand | OP | Place cauldron + item frames |
| `/removetarget` | RemoveTargetCommand | All | Clear entity targeting |

---

## Configuration Files Reference

All under `src/main/resources/`. Synced to `server/plugins/IronKeep/` by `start.bat`.

| File | Purpose | Loaded By |
|------|---------|-----------|
| `config.yml` | Warden spawn, new-player-spawn, woodcutting/mining/farming regions, commission board locations, daily commission bonus config | IronKeepPlugin, various managers, DailyBonusManager |
| `commissions.yml` | Commission definitions (type, items, quantities, rewards) | CommissionRegistry |
| `ranks.yml` | Rank definitions (cost, unlocked commission types) | RankManager |
| `escapes.yml` | Escape levels (cost, bonus percent) | EscapeManager |
| `skills.yml` | Skill config (level cap, XP curve, bonus rates, XP per type) | SkillManager |
| `zones.yml` | Zone boundaries, regen config, placeholders | ZoneManager, BlockRegenManager |
| `mail-room.yml` | Mail difficulty per rank, barrel locations, accuracy bonus caps | MailRoomManager |
| `kitchen.yml` | Recipes, ingredient frame locations, cauldron location, accuracy bonus caps | KitchenManager |
| `daily-quests.yml` | Quest objective, gold/shard rewards, reset schedule | DailyQuestManager |
| `starter-kit.yml` | First-join items with enchantments | StarterKitConfig |

### Cross-File Constraints

- Commission `type` values in `commissions.yml` **must** appear in `ranks.yml` `unlocked-types`
- Commission types in `zones.yml` **must** match types in `commissions.yml`
- `warden.first-commission` in `config.yml` **must** be a valid commission ID from `commissions.yml`
- Skill `xp-per-completion` keys (MINING, WOODCUTTING, etc.) are the **broad** categories, not the
  specific commission types (MINING covers MINING_COAL, MINING_IRON, etc.)
- Recipe `rank-tier` in `kitchen.yml` must be <= max rank number in `ranks.yml`
- Mail `difficulty.rankN` keys in `mail-room.yml` should cover all defined ranks

### YAML Value Rules

- **Currency amounts** (reward-amount, shards-reward, cost, max-gold-coins, max-shards):
  Always whole numbers. Java reads as `double` but values must be integers.
- **Material names:** Bukkit `Material` enum values, uppercase with underscores (e.g. `COAL_ORE`)
- **Coordinates:** Integer block coordinates (x, y, z). Spawn positions can be double.
- **Commission types:** Uppercase with underscores (e.g. `MINING_COAL`, `MAIL_SORTING`)

---

## Data Persistence Reference

All runtime player data under `plugins/IronKeep/` (or `plugins/IronKeep/data/`).

| File | Format | Written By | When |
|------|--------|-----------|------|
| `balances.yml` | `gold-coins.<uuid>: double`, `shards.<uuid>: double` | CurrencyManager | Every balance change |
| `player-commissions.yml` | `<uuid>.commission-id: string`, `<uuid>.progress: int` | CommissionStateStore | Every state change |
| `data/player-ranks.yml` | `<uuid>: int` | RankManager | Every rank change |
| `data/player-escapes.yml` | `<uuid>: int` | EscapeManager | Every escape |
| `data/skill-levels.yml` | `<uuid>.<TYPE>.level: int`, `<uuid>.<TYPE>.xp: double` | SkillManager | Every XP grant |
| `data/daily-quest.yml` | `<uuid>: long` (epoch seconds) | DailyQuestManager | Every completion |
| `data/daily-commission-bonus.yml` | `<uuid>.<TYPE>: long` (epoch seconds) | DailyBonusManager | Every bonus claim |
| `received-kits.yml` | `<uuid>: true` | StarterKitManager | On first kit grant |
| `warden-seen.yml` | `<uuid>: true` | WardenListener | On first dialogue |

**All saves are immediate.** No batching, no save-on-disable. If you add new persistent state,
follow this pattern: save to disk inside the mutation method.

---

## Critical Invariants & Edge Cases

These are behaviors that MUST be preserved. Violating them will cause bugs.

### Currency

1. **All currency values are whole numbers.** Enforced at 4 layers (see Currency System section).
   Do NOT introduce fractional currency anywhere.
2. **Escape bonus applies to Gold Coins ONLY.** Never multiply Shards by escape multiplier.
3. **Negative balances are impossible.** `setBalance`/`setShards` clamp to 0. `removeShards`
   returns false if insufficient.

### Commissions

4. **Only one active commission per player.** `assignCommission()` rejects if one is active.
5. **MAIL_SORTING and COOKING skip standard progress/inventory checks.** They have their own
   completion methods (`completeMailSorting`, `completeCooking`) with accuracy-based logic.
6. **Skill objective reduction is clamped to min 1.** `Math.max(1, default - reduction)`.
   A commission can never require 0 items.
7. **Turn-in item may differ from objective item.** E.g. objective=COAL_ORE but turn-in=COAL.
   Always use `def.getTurnInItem()` for inventory checks.
8. **Mining accepts deepslate variants.** `MiningListener` checks both `COAL_ORE` and
   `DEEPSLATE_COAL_ORE` when the objective is `COAL_ORE`.

### Zones & Listeners

9.  **Zone entry gate (lazy activation).** Mail and kitchen listeners track whether the player
    has entered the zone. Commission cancellation only fires AFTER the player enters AND then
    leaves. This prevents cancellation when walking away from the Commission Board.
10. **Mail/cooking items are locked down.** Cannot be dropped or moved to external inventories.
    Cleared on commission end, player quit, or zone exit.
11. **Block regen checks placeholder existence.** If an admin removes a placeholder block before
    the regen timer fires, the original block is NOT restored (prevents duplication).
12. **Farmland protection during crop regen.** The farmland beneath a regenerating crop cannot be
    trampled until the crop is restored.

### GUI

13. **SkillCommand only cancels its own GUI clicks.** The `InventoryClickEvent` handler matches
    title strings to identify skill GUIs. It does NOT cancel clicks in other inventories.
14. **CommissionBoardListener uses InventoryHolder.** It identifies board GUIs via `BoardHolder`
    instance check, not title strings.
15. **Kitchen GUI close returns items.** `InventoryCloseEvent` handler returns cooking ingredients
    from GUI slots to inventory (overflow drops on ground).

### Escape

16. **Escape zeros out ALL remaining Gold Coins.** Not just the cost — `setBalance(uuid, 0)` is
    called after deducting the cost. Shards are untouched.
17. **Escape resets rank to 1 and cancels active commission.**

### Daily Quest

18. **Async chat event → main thread.** The daily quest detects "hi" in `AsyncPlayerChatEvent`
    but grants rewards on the main thread via `runTask()`. Never grant currency from async context.
19. **Reset is midnight UTC**, epoch-based. Not player-local time.

### Startup

20. **Daylight cycle is enabled; weather is locked to clear.** Daylight cycle runs normally
    (players see day/night). Weather cycle is disabled and cleared on startup (1-tick delay).
21. **Clock action bar runs every 20 ticks.** Sends the current in-game time (rounded to
    30-minute intervals) to all online players via action bar. Uses the first world's time.
    Do not send competing action bar messages — they will overwrite the clock.

---

## Maintaining This Document

**This is a living document.** It must stay in sync with the codebase.

### When to Update

- **Adding a new system:** Add a new section with its lifecycle, state machine, edge cases, and
  configuration. Update the dependency graph and commands reference.
- **Adding a new commission type:** Update the Commission Types table, verify cross-file constraints
  section is still accurate, check if a new listener is needed.
- **Adding a new command:** Add to the Commands Reference table.
- **Adding a new config file:** Add to Configuration Files Reference and Data Persistence Reference.
- **Changing reward calculation:** Update the relevant Reward Calculation section and verify the
  Currency System section is still accurate.
- **Changing a convention:** Update the relevant convention section (e.g. currency rounding rules).
- **Removing a system:** Remove its section, update the dependency graph, remove from references.

### How to Update

1. Read the relevant section(s) before making code changes.
2. After your code changes compile and work, update the affected section(s).
3. Keep the same level of detail — document the "what", "why", and "watch out for".
4. Keep edge cases in the Critical Invariants section — these are the most important.
5. If in doubt, add documentation rather than skip it. Future agents depend on this file.

### What NOT to Put Here

- Line-by-line code documentation (that's what code comments are for)
- Temporary debugging notes or in-progress work
- Git history or change logs (use git for that)
