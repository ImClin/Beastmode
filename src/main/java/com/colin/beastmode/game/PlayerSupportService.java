package com.colin.beastmode.game;

import com.colin.beastmode.Beastmode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.UUID;

final class PlayerSupportService {

    private final Beastmode plugin;
    private final String prefix;
    private final NamespacedKey exitTokenKey;
    private final NamespacedKey preferenceKey;
    private final ItemStack exitTokenTemplate;
    private final int longEffectDurationTicks;

    PlayerSupportService(Beastmode plugin, String prefix, int longEffectDurationTicks,
                         NamespacedKey exitTokenKey, NamespacedKey preferenceKey, ItemStack exitTokenTemplate) {
        this.plugin = plugin;
        this.prefix = prefix;
        this.exitTokenKey = exitTokenKey;
        this.preferenceKey = preferenceKey;
        this.exitTokenTemplate = exitTokenTemplate;
        this.longEffectDurationTicks = longEffectDurationTicks;
    }

    void resetLoadout(Player player) {
        if (player == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(null);
        inventory.setItemInOffHand(null);
    }

    void restoreVitals(Player player) {
        if (player == null) {
            return;
        }

        var attribute = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
        double maxHealth = attribute != null ? attribute.getValue() : player.getHealthScale();
        if (Double.isNaN(maxHealth) || maxHealth <= 0) {
            maxHealth = 20.0;
        }

        player.setHealth(Math.min(maxHealth, player.getHealth()));
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setExhaustion(0.0f);
        if (player.getHealth() < maxHealth) {
            player.setHealth(maxHealth);
        }
    }

    void applyFireResistance(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        PotionEffect fireResistance = new PotionEffect(PotionEffectType.FIRE_RESISTANCE, longEffectDurationTicks, 0, false, false, true);
        player.addPotionEffect(fireResistance);
    }

    void scheduleRunnerReward(Player runner) {
        if (runner == null) {
            return;
        }

        UUID holder = runner.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player online = Bukkit.getPlayer(holder);
            if (online != null && online.isOnline()) {
                grantRunnerReward(online);
            }
        }, 20L);
    }

    private void grantRunnerReward(Player runner) {
        if (runner == null || !runner.isOnline()) {
            return;
        }

        resetLoadout(runner);
        restoreVitals(runner);

        PlayerInventory inventory = runner.getInventory();

        ItemStack sword = new ItemStack(org.bukkit.Material.NETHERITE_SWORD);
        ItemMeta swordMeta = sword.getItemMeta();
        if (swordMeta != null) {
            Enchantment sharpness = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("sharpness"));
            if (sharpness != null) {
                swordMeta.addEnchant(sharpness, 3, true);
            }
            sword.setItemMeta(swordMeta);
        }
        inventory.setItem(0, sword);
        runner.getInventory().setHeldItemSlot(0);

        ItemStack bow = createEnchantedBow(1);
        inventory.setItem(1, bow);

        ItemStack potion = createHealingPotion();
        for (int slot = 2; slot <= 6; slot++) {
            inventory.setItem(slot, potion.clone());
        }

        ItemStack arrow = new ItemStack(org.bukkit.Material.ARROW, 1);
        inventory.setItem(17, arrow);

        inventory.setHelmet(new ItemStack(org.bukkit.Material.NETHERITE_HELMET));
        inventory.setChestplate(new ItemStack(org.bukkit.Material.NETHERITE_CHESTPLATE));
        inventory.setLeggings(new ItemStack(org.bukkit.Material.NETHERITE_LEGGINGS));
        ItemStack boots = new ItemStack(org.bukkit.Material.NETHERITE_BOOTS);
        ItemMeta bootsMeta = boots.getItemMeta();
        if (bootsMeta != null) {
            Enchantment featherFalling = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("feather_falling"));
            if (featherFalling != null) {
                bootsMeta.addEnchant(featherFalling, 4, true);
            }
            bootsMeta.setUnbreakable(true);
            boots.setItemMeta(bootsMeta);
        }
        inventory.setBoots(boots);

        PotionEffect speedBoost = new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false, true);
        runner.removePotionEffect(PotionEffectType.SPEED);
        runner.addPotionEffect(speedBoost);

        runner.sendMessage(prefix + ChatColor.GOLD + "" + ChatColor.BOLD + "Victory spoils! "
                + ChatColor.RESET + ChatColor.GOLD + "You bolted through the finish and are ready for the Beast.");
    }

    boolean applyBeastLoadout(Player beast) {
        if (beast == null || !beast.isOnline()) {
            return false;
        }

        resetLoadout(beast);

        PlayerInventory inventory = beast.getInventory();

        ItemStack sword = new ItemStack(org.bukkit.Material.NETHERITE_SWORD);
        inventory.setItem(0, sword);
        beast.getInventory().setHeldItemSlot(0);

        ItemStack potion = createHealingPotion();
        inventory.setItem(1, potion.clone());
        inventory.setItem(2, potion.clone());
        inventory.setItem(3, potion.clone());

        inventory.setHelmet(new ItemStack(org.bukkit.Material.DIAMOND_HELMET));
        inventory.setChestplate(new ItemStack(org.bukkit.Material.DIAMOND_CHESTPLATE));
        inventory.setLeggings(new ItemStack(org.bukkit.Material.DIAMOND_LEGGINGS));

        ItemStack boots = new ItemStack(org.bukkit.Material.DIAMOND_BOOTS);
        ItemMeta bootsMeta = boots.getItemMeta();
        if (bootsMeta != null) {
            Enchantment featherFalling = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("feather_falling"));
            if (featherFalling != null) {
                bootsMeta.addEnchant(featherFalling, 4, true);
            }
            bootsMeta.setUnbreakable(true);
            boots.setItemMeta(bootsMeta);
        }
        inventory.setBoots(boots);

        return true;
    }

    void giveExitToken(Player player) {
        if (player == null || !player.isOnline() || exitTokenTemplate == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack current = inventory.getItem(GameManager.EXIT_TOKEN_SLOT);
        if (isExitToken(current)) {
            return;
        }
        inventory.setItem(GameManager.EXIT_TOKEN_SLOT, exitTokenTemplate.clone());
    }

    void removeExitToken(Player player) {
        if (player == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (isExitToken(item)) {
                inventory.clear(slot);
            }
        }
    }

    void removeExitTokens(java.util.Collection<Player> players) {
        if (players == null) {
            return;
        }
        for (Player player : players) {
            removeExitToken(player);
            clearPreferenceSelectors(player);
        }
    }

    boolean isExitToken(ItemStack stack) {
        if (stack == null || stack.getType() != org.bukkit.Material.NETHER_STAR) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(exitTokenKey, PersistentDataType.BYTE);
    }

    void givePreferenceSelectors(Player player, GameManager.RolePreference selected) {
        if (player == null || !player.isOnline()) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        GameManager.RolePreference applied = selected != null ? selected : GameManager.RolePreference.ANY;
        Bukkit.getScheduler().runTask(plugin, () -> {
            clearPreferenceSelectors(inventory);
            inventory.setItem(GameManager.PREFERENCE_BEAST_SLOT,
                    createPreferenceSelector(GameManager.RolePreference.BEAST, applied));
            inventory.setItem(GameManager.PREFERENCE_RUNNER_SLOT,
                    createPreferenceSelector(GameManager.RolePreference.RUNNER, applied));
            player.updateInventory();
        });
    }

    void clearPreferenceSelectors(Player player) {
        if (player == null) {
            return;
        }
        if (clearPreferenceSelectors(player.getInventory())) {
            player.updateInventory();
        }
    }

    boolean isPreferenceSelector(ItemStack stack) {
        GameManager.RolePreference type = readPreferenceType(stack);
        return type == GameManager.RolePreference.BEAST || type == GameManager.RolePreference.RUNNER;
    }

    private ItemStack createPreferenceSelector(GameManager.RolePreference type, GameManager.RolePreference selected) {
        org.bukkit.Material material = type == GameManager.RolePreference.BEAST
                ? org.bukkit.Material.RED_WOOL
                : org.bukkit.Material.GREEN_WOOL;
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        boolean chosen = selected == type;
        String title = (type == GameManager.RolePreference.BEAST ? ChatColor.DARK_RED : ChatColor.GREEN)
                + "" + ChatColor.BOLD + (type == GameManager.RolePreference.BEAST ? "Beast" : "Runner") + " Preference";

        java.util.List<String> lore = new java.util.ArrayList<>();
        if (chosen) {
            lore.add(ChatColor.GOLD + "Selected");
            lore.add(ChatColor.GRAY + "Right-click again to clear.");
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        } else {
            lore.add(ChatColor.GRAY + "Right-click to favor this role.");
            lore.add(ChatColor.DARK_GRAY + "Right-click again to reset.");
        }

        meta.setDisplayName(title);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        meta.getPersistentDataContainer().set(preferenceKey, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    private boolean clearPreferenceSelectors(PlayerInventory inventory) {
        if (inventory == null) {
            return false;
        }
        boolean changed = false;
        java.util.List<Integer> slotsToClear = new java.util.ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (isPreferenceSelector(item)) {
                slotsToClear.add(slot);
            }
        }
        for (int slot : slotsToClear) {
            inventory.clear(slot);
            changed = true;
        }
        return changed;
    }

    GameManager.RolePreference readPreferenceType(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        var container = meta.getPersistentDataContainer();
        if (!container.has(preferenceKey, PersistentDataType.STRING)) {
            return null;
        }
        String stored = container.get(preferenceKey, PersistentDataType.STRING);
        if (stored == null) {
            return null;
        }
        try {
            return GameManager.RolePreference.valueOf(stored);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    ItemStack createHealingPotion() {
        ItemStack potion = new ItemStack(org.bukkit.Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        if (meta != null) {
            meta.setBasePotionType(PotionType.STRONG_HEALING);
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Burst Heal");
            potion.setItemMeta(meta);
        }
        return potion;
    }

    private ItemStack createEnchantedBow(int powerLevel) {
        ItemStack bow = new ItemStack(org.bukkit.Material.BOW);
        ItemMeta bowMeta = bow.getItemMeta();
        if (bowMeta != null) {
            Enchantment infinity = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("infinity"));
            Enchantment power = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("power"));
            if (infinity != null) {
                bowMeta.addEnchant(infinity, 1, true);
            }
            if (power != null) {
                bowMeta.addEnchant(power, Math.max(1, powerLevel), true);
            }
            bow.setItemMeta(bowMeta);
        }
        return bow;
    }
}
