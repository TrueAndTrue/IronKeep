package com.ironkeep;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class KitchenManager {

    private static final String PREFIX = ChatColor.GOLD + "[Kitchen] " + ChatColor.RESET;

    private final IronKeepPlugin plugin;
    private final NamespacedKey cookingIngredientKey;
    private final NamespacedKey cookingSlotKey;

    private final Map<UUID, CookingState> activeStates = new HashMap<>();
    private final Map<UUID, String> lastRecipeByPlayer = new HashMap<>();
    private final List<CookingRecipe> recipes = new ArrayList<>();

    private double maxGoldBonus = 60.0;
    private double maxShardsBonus = 60.0;

    private String cauldronWorld = "world";
    private int cauldronX = 135, cauldronY = 3, cauldronZ = -11;

    // location key ("world,x,y,z") -> ingredient material
    private final Map<String, Material> ingredientFrameLocations = new HashMap<>();

    public KitchenManager(IronKeepPlugin plugin) {
        this.plugin = plugin;
        this.cookingIngredientKey = new NamespacedKey(plugin, "cooking_ingredient");
        this.cookingSlotKey = new NamespacedKey(plugin, "cooking_slot");
    }

    public void load() {
        recipes.clear();
        ingredientFrameLocations.clear();

        File file = new File(plugin.getDataFolder(), "kitchen.yml");
        if (!file.exists()) plugin.saveResource("kitchen.yml", false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        // Accuracy bonus
        ConfigurationSection bonusSection = yaml.getConfigurationSection("accuracy-bonus");
        if (bonusSection != null) {
            maxGoldBonus = bonusSection.getDouble("max-gold-coins", 60.0);
            maxShardsBonus = bonusSection.getDouble("max-shards", 60.0);
        }

        // Cauldron location
        ConfigurationSection cauldronSection = yaml.getConfigurationSection("cauldron");
        if (cauldronSection != null) {
            cauldronWorld = cauldronSection.getString("world", "world");
            cauldronX = cauldronSection.getInt("x", 135);
            cauldronY = cauldronSection.getInt("y", 3);
            cauldronZ = cauldronSection.getInt("z", -11);
        }

        // Recipes
        ConfigurationSection recipesSection = yaml.getConfigurationSection("recipes");
        if (recipesSection != null) {
            for (String id : recipesSection.getKeys(false)) {
                ConfigurationSection entry = recipesSection.getConfigurationSection(id);
                if (entry == null) continue;
                String displayName = entry.getString("display-name", id);
                int rankTier = entry.getInt("rank-tier", 1);
                List<String> ingredientNames = entry.getStringList("ingredients");
                List<Material> ingredients = new ArrayList<>();
                for (String name : ingredientNames) {
                    Material mat = Material.matchMaterial(name);
                    if (mat != null) {
                        ingredients.add(mat);
                    } else {
                        plugin.getLogger().warning("KitchenManager: unknown material '" + name
                                + "' in recipe '" + id + "'");
                    }
                }
                if (!ingredients.isEmpty()) {
                    recipes.add(new CookingRecipe(id, displayName, rankTier, ingredients));
                }
            }
        }

        // Ingredient frame locations
        List<?> frameList = yaml.getList("ingredient-frames");
        if (frameList != null) {
            for (Object obj : frameList) {
                if (!(obj instanceof Map<?, ?> map)) continue;
                String ingredientName = String.valueOf(map.get("ingredient"));
                String world = String.valueOf(map.get("world"));
                int x = toInt(map.get("x"));
                int y = toInt(map.get("y"));
                int z = toInt(map.get("z"));
                Material mat = Material.matchMaterial(ingredientName);
                if (mat != null) {
                    ingredientFrameLocations.put(frameKey(world, x, y, z), mat);
                }
            }
        }

        plugin.getLogger().info("KitchenManager: loaded " + recipes.size() + " recipe(s), "
                + ingredientFrameLocations.size() + " ingredient frame(s).");
    }

    private int toInt(Object obj) {
        if (obj instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(obj)); } catch (NumberFormatException e) { return 0; }
    }

    private String frameKey(String world, int x, int y, int z) {
        return world + "," + x + "," + y + "," + z;
    }

    // -------------------------------------------------------------------------
    // Recipe assignment
    // -------------------------------------------------------------------------

    public void assignRecipe(Player player, int rankNumber) {
        UUID uuid = player.getUniqueId();
        String lastRecipeId = lastRecipeByPlayer.get(uuid);

        // Recipes with rank-tier <= rankNumber (cumulative access)
        List<CookingRecipe> available = new ArrayList<>();
        for (CookingRecipe recipe : recipes) {
            if (recipe.getRankTier() <= rankNumber) available.add(recipe);
        }

        if (available.isEmpty()) {
            plugin.getLogger().warning("KitchenManager: no recipes available for rank " + rankNumber);
            return;
        }

        Collections.shuffle(available, new Random());
        CookingRecipe selected = available.get(0);
        if (available.size() > 1 && selected.getId().equals(lastRecipeId)) {
            selected = available.get(1);
        }

        activeStates.put(uuid, new CookingState(selected.getId()));
        lastRecipeByPlayer.put(uuid, selected.getId());

        player.sendMessage(PREFIX + ChatColor.YELLOW + "Your dish: " + ChatColor.WHITE + selected.getDisplayName());
        player.sendMessage(ChatColor.GOLD + "  Ingredients (in order):");
        List<Material> ingredients = selected.getIngredients();
        for (int i = 0; i < ingredients.size(); i++) {
            player.sendMessage(ChatColor.GRAY + "    " + (i + 1) + ". " + formatMaterial(ingredients.get(i)));
        }
        player.sendMessage(ChatColor.GOLD + "  Gather ingredients from the item frames, then use the cauldron.");
    }

    // -------------------------------------------------------------------------
    // Cauldron GUI
    // -------------------------------------------------------------------------

    public void openCauldronGui(Player player) {
        UUID uuid = player.getUniqueId();
        CookingState state = activeStates.get(uuid);
        if (state == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "You don't have a cooking assignment yet.");
            return;
        }

        CookingRecipe recipe = getRecipe(state.getRecipeId());
        if (recipe == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "Recipe not found. Contact an admin.");
            return;
        }

        Inventory gui = plugin.getServer().createInventory(null, 27, "Kitchen Cauldron");

        // Fill all slots with gray glass pane filler
        ItemStack filler = makeFiller();
        for (int i = 0; i < 27; i++) gui.setItem(i, filler);

        // Ingredient input slots: 0 to N-1
        int n = recipe.getIngredients().size();
        for (int i = 0; i < n; i++) {
            gui.setItem(i, makeSlotPlaceholder(i + 1));
        }

        // Recipe reference at slot 13 (center of middle row)
        gui.setItem(13, makeRecipeReference(recipe));

        // Confirm button at slot 22
        ItemStack confirm = new ItemStack(Material.GREEN_CONCRETE);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Confirm");
        confirmMeta.setLore(List.of(ChatColor.GRAY + "Click to lock in your ingredient order"));
        confirm.setItemMeta(confirmMeta);
        gui.setItem(22, confirm);

        player.openInventory(gui);
    }

    private ItemStack makeFiller() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeSlotPlaceholder(int slotNum) {
        ItemStack item = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GRAY + "Slot " + slotNum);
        meta.setLore(List.of(ChatColor.DARK_GRAY + "Place an ingredient here"));
        meta.getPersistentDataContainer().set(cookingSlotKey, PersistentDataType.INTEGER, slotNum);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeRecipeReference(CookingRecipe recipe) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + recipe.getDisplayName());
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Ingredients (in order):");
        List<Material> ingredients = recipe.getIngredients();
        for (int i = 0; i < ingredients.size(); i++) {
            lore.add(ChatColor.GOLD + "  " + (i + 1) + ". " + formatMaterial(ingredients.get(i)));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // -------------------------------------------------------------------------
    // Cauldron GUI click handling
    // -------------------------------------------------------------------------

    public void handleCauldronClick(Player player, InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();

        // Player inventory area — allow normal picks but block shift-click into GUI
        if (rawSlot >= 27) {
            if (event.isShiftClick()) event.setCancelled(true);
            return;
        }

        // GUI area — cancel by default
        event.setCancelled(true);

        UUID uuid = player.getUniqueId();
        CookingState state = activeStates.get(uuid);
        if (state == null) return;

        CookingRecipe recipe = getRecipe(state.getRecipeId());
        if (recipe == null) return;

        int n = recipe.getIngredients().size();

        if (rawSlot == 22) {
            processConfirm(player, state, recipe, event.getInventory());
            return;
        }

        if (rawSlot < n) {
            handleIngredientSlotClick(player, event, rawSlot);
        }
        // All other GUI slots (filler, recipe ref): already cancelled — do nothing
    }

    private void handleIngredientSlotClick(Player player, InventoryClickEvent event, int rawSlot) {
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        boolean cursorEmpty = cursor == null || cursor.getType() == Material.AIR;
        boolean currentIsCookingIngredient = isCookingIngredient(current);

        if (!cursorEmpty && isCookingIngredient(cursor)) {
            // Place cursor cooking ingredient into this slot
            ItemStack toPlace = cursor.clone();
            toPlace.setAmount(1);
            event.getInventory().setItem(rawSlot, toPlace);

            if (currentIsCookingIngredient) {
                // Swap: put previous ingredient back on cursor
                ItemStack swapBack = current.clone();
                swapBack.setAmount(1);
                player.setItemOnCursor(swapBack);
            } else {
                // Slot was a placeholder — consume one from cursor stack
                if (cursor.getAmount() > 1) {
                    cursor.setAmount(cursor.getAmount() - 1);
                    player.setItemOnCursor(cursor);
                } else {
                    player.setItemOnCursor(null);
                }
            }
        } else if (cursorEmpty && currentIsCookingIngredient) {
            // Pick up the ingredient and restore the placeholder
            ItemStack pickUp = current.clone();
            pickUp.setAmount(1);
            player.setItemOnCursor(pickUp);
            event.getInventory().setItem(rawSlot, makeSlotPlaceholder(rawSlot + 1));
        }
    }

    private void processConfirm(Player player, CookingState state, CookingRecipe recipe, Inventory inv) {
        int n = recipe.getIngredients().size();
        List<Material> placed = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            ItemStack item = inv.getItem(i);
            if (!isCookingIngredient(item)) {
                player.sendMessage(PREFIX + ChatColor.RED + "Fill all " + n
                        + " ingredient slot(s) before confirming!");
                return;
            }
            placed.add(item.getType());
        }

        state.setConfirmedIngredients(placed);
        state.setConfirmedOrder(true);

        // Schedule close to avoid closing inside event handler
        plugin.getServer().getScheduler().runTask(plugin, (Runnable) player::closeInventory);

        player.sendMessage(PREFIX + ChatColor.GREEN + "Dish locked in! Use "
                + ChatColor.YELLOW + "/commission complete"
                + ChatColor.GREEN + " to collect your reward.");
    }

    // -------------------------------------------------------------------------
    // Item frame interaction
    // -------------------------------------------------------------------------

    public void handleItemFrameClick(Player player, ItemFrame frame) {
        Location loc = frame.getLocation();
        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "world";
        String key = frameKey(worldName, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

        Material ingredient = ingredientFrameLocations.get(key);
        if (ingredient == null) return; // not a configured ingredient frame

        // Check if player already has this cooking ingredient
        for (ItemStack item : player.getInventory().getContents()) {
            if (isCookingIngredient(item) && item.getType() == ingredient) {
                player.sendMessage(PREFIX + ChatColor.YELLOW
                        + "You already have " + formatMaterial(ingredient) + ".");
                return;
            }
        }

        // Give one tagged copy of the ingredient
        ItemStack gift = new ItemStack(ingredient, 1);
        ItemMeta meta = gift.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.WHITE + formatMaterial(ingredient));
            meta.getPersistentDataContainer().set(cookingIngredientKey, PersistentDataType.BYTE, (byte) 1);
            gift.setItemMeta(meta);
        }
        player.getInventory().addItem(gift);
        player.sendMessage(PREFIX + ChatColor.WHITE + "Picked up: " + formatMaterial(ingredient));
    }

    // -------------------------------------------------------------------------
    // State management
    // -------------------------------------------------------------------------

    public void clearCooking(Player player) {
        UUID uuid = player.getUniqueId();
        if (player.getOpenInventory().getTitle().contains("Kitchen")) {
            player.closeInventory();
        }
        // Remove all cooking ingredient items from inventory
        player.getInventory().setContents(
                Arrays.stream(player.getInventory().getContents())
                        .map(item -> isCookingIngredient(item) ? null : item)
                        .toArray(ItemStack[]::new)
        );
        activeStates.remove(uuid);
    }

    public CookingState getState(UUID uuid) {
        return activeStates.get(uuid);
    }

    public CookingRecipe getRecipe(String id) {
        if (id == null) return null;
        for (CookingRecipe recipe : recipes) {
            if (recipe.getId().equals(id)) return recipe;
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Item helpers
    // -------------------------------------------------------------------------

    public boolean isCookingIngredient(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(cookingIngredientKey, PersistentDataType.BYTE);
    }

    public boolean isSlotPlaceholder(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(cookingSlotKey, PersistentDataType.INTEGER);
    }

    public boolean isCauldron(Block block) {
        if (block == null || block.getType() != Material.CAULDRON) return false;
        String worldName = block.getWorld().getName();
        return worldName.equals(cauldronWorld)
                && block.getX() == cauldronX
                && block.getY() == cauldronY
                && block.getZ() == cauldronZ;
    }

    public boolean isIngredientFrame(ItemFrame frame) {
        Location loc = frame.getLocation();
        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "world";
        String key = frameKey(worldName, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        return ingredientFrameLocations.containsKey(key);
    }

    // -------------------------------------------------------------------------
    // Kitchen setup (admin command)
    // -------------------------------------------------------------------------

    public void setupKitchen(World world) {
        // Place cauldron
        if (world.getName().equals(cauldronWorld)) {
            world.getBlockAt(cauldronX, cauldronY, cauldronZ).setType(Material.CAULDRON);
            plugin.getLogger().info("KitchenManager: placed cauldron at "
                    + cauldronX + "," + cauldronY + "," + cauldronZ);
        }

        // Place item frames
        int placed = 0;
        for (Map.Entry<String, Material> entry : ingredientFrameLocations.entrySet()) {
            String[] parts = entry.getKey().split(",");
            if (parts.length != 4) continue;
            String worldName = parts[0];
            if (!world.getName().equals(worldName)) continue;
            try {
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);
                Location loc = new Location(world, x + 0.5, y, z + 0.5);
                // Remove existing item frames at this location
                world.getNearbyEntities(loc, 0.6, 0.6, 0.6).stream()
                        .filter(e -> e instanceof ItemFrame)
                        .forEach(e -> e.remove());
                ItemFrame frame = (ItemFrame) world.spawnEntity(loc, EntityType.ITEM_FRAME);
                frame.setItem(new ItemStack(entry.getValue()));
                placed++;
            } catch (NumberFormatException ignored) {}
        }
        plugin.getLogger().info("KitchenManager: placed " + placed + " item frame(s) in '"
                + world.getName() + "'.");
    }

    // -------------------------------------------------------------------------
    // Bonus config accessors
    // -------------------------------------------------------------------------

    public double getMaxGoldBonus() { return maxGoldBonus; }
    public double getMaxShardsBonus() { return maxShardsBonus; }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String formatMaterial(Material material) {
        String name = material.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
