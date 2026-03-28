# IronKeep Plugin — Developer Notes

## Package Layout
All source files live in the flat `com.ironkeep` package under
`src/main/java/com/ironkeep/`.

## Commands
All commands use the Paper Brigadier lifecycle API with `BasicCommand` and
`LifecycleEvents.COMMANDS`. They are registered in `IronKeepPlugin.onEnable`.

## Config
All configuration is YAML-driven. Files live in the plugin data folder.
Mutations are saved immediately via `YamlConfiguration.save()`.

## Mail Room System
The mail sorting commission type lets players deliver labelled mail items to
the correct barrel.

**Config files:**
- `mail-room.yml` — static config (accuracy-bonus, difficulty tiers, seed barrel
  list). Synced from resources on each server start by `start.bat` — do NOT store
  runtime data here.
- `barrel-bindings.yml` — runtime barrel assignments created and managed by the
  binding wand. Never overwritten by `start.bat`. Not bundled in resources; created
  at runtime by `persistBarrels()` when barrels are bound/unbound.

Key classes:
- `MailRoomManager` — loads config, manages mail sessions, handles delivery
  logic, and persists barrel bindings.
- `MailSortingListener` — prevents mail item abuse (drop, inventory move) and
  cancels commissions on zone exit.
- `MailRoomCommand` — `/mailroom` admin command (subcommands: `setup`, `wand`).

### Binding Wand System
Admins can configure mailroom barrels in-game without editing YAML manually.

**Getting the wand:**
```
/mailroom wand   (requires ironkeep.admin permission)
```

**Usage:**
1. Hold the Mailroom Binding Wand (gold BLAZE_ROD).
2. Right-click any BARREL block.
3. A 27-slot GUI opens showing all available destinations as coloured wool.
4. Click a destination to bind the barrel; click the BARRIER (slot 26) to
   remove an existing binding.
5. The change is immediately saved to `mail-room.yml`.

**Key classes:**
- `BindingWandManager` — creates the wand item and checks PDC tag
  (`ironkeep:binding_wand`).
- `BindingWandGUI` — builds the destination picker inventory and tracks open
  GUI → barrel mappings.
- `BindingWandListener` — handles `PlayerInteractEvent` to open the GUI and
  `InventoryClickEvent` / `InventoryCloseEvent` to process selections and
  clean up.

**Persistence:**
`MailRoomManager.bindBarrel` / `unbindBarrel` mutate the in-memory maps and
call `persistBarrels()` which writes the full barrel list to `barrel-bindings.yml`
immediately. `mail-room.yml` is never modified at runtime.

## Skill System
Skill progression is stored in `data/skill-levels.yml`. Levels cap at the
value set in `skills.yml`. Commission completions grant XP and unlock gold/
shard bonuses and objective-count reductions.

## Commission System
Commissions are defined in `commissions.yml`. The `CommissionManager` wires
together ranks, skills, mail room, and kitchen to assign, track, and reward
commissions. State persists to `player-commissions.yml`.

## Kitchen System
Cooking commissions use a cauldron GUI. Recipes and frame locations are
configured in `kitchen.yml`.

## Permission Nodes
- `ironkeep.admin` — OP-level admin actions (mailroom setup, binding wand,
  commission debug commands).
