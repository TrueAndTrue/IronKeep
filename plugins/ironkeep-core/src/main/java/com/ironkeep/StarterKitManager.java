package com.ironkeep;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class StarterKitManager {

    private final IronKeepPlugin plugin;
    private final StarterKitConfig kitConfig;
    private final File receivedFile;
    private final Set<UUID> received = new HashSet<>();

    public StarterKitManager(IronKeepPlugin plugin, StarterKitConfig kitConfig) {
        this.plugin = plugin;
        this.kitConfig = kitConfig;
        this.receivedFile = new File(plugin.getDataFolder(), "received-kits.yml");
    }

    public void load() {
        received.clear();
        if (!receivedFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(receivedFile);
        List<?> uuids = yaml.getList("received");
        if (uuids == null) return;
        for (Object obj : uuids) {
            try {
                received.add(UUID.fromString(String.valueOf(obj)));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("received", received.stream().map(UUID::toString).toList());
        try {
            yaml.save(receivedFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save received-kits.yml: " + e.getMessage());
        }
    }

    public boolean hasReceivedKit(UUID uuid) {
        return received.contains(uuid);
    }

    public void markReceived(UUID uuid) {
        received.add(uuid);
        save();
    }

    public void grantKit(Player player) {
        List<StarterKitItem> items = kitConfig.getItems();
        for (StarterKitItem kitItem : items) {
            Material material = Material.matchMaterial(kitItem.getMaterial());
            if (material == null) {
                plugin.getLogger().warning("Unknown material in starter kit: " + kitItem.getMaterial() + ", skipping.");
                continue;
            }

            ItemStack stack = new ItemStack(material, kitItem.getQuantity());
            ItemMeta meta = stack.getItemMeta();

            if (meta != null) {
                if (kitItem.getDisplayName() != null) {
                    meta.displayName(Component.text(kitItem.getDisplayName()));
                }
                for (Map.Entry<String, Integer> entry : kitItem.getEnchantments().entrySet()) {
                    NamespacedKey key = NamespacedKey.minecraft(entry.getKey());
                    Enchantment enchantment = Registry.ENCHANTMENT.get(key);
                    if (enchantment == null) {
                        plugin.getLogger().warning("Unknown enchantment in starter kit: " + entry.getKey() + ", skipping.");
                        continue;
                    }
                    meta.addEnchant(enchantment, entry.getValue(), true);
                }
                stack.setItemMeta(meta);
            }

            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            for (ItemStack dropped : overflow.values()) {
                player.getWorld().dropItem(player.getLocation(), dropped);
            }
        }
    }
}
