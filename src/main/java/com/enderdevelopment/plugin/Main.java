package com.enderdevelopment.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class Main extends JavaPlugin implements Listener {
    private NamespacedKey jobKey;
    private NamespacedKey chestKey;
    private BukkitTask workTask;

    @Override
    public void onEnable() {
        jobKey = new NamespacedKey(this, "worker_job");
        chestKey = new NamespacedKey(this, "worker_chest");
        Bukkit.getPluginManager().registerEvents(this, this);
        workTask = Bukkit.getScheduler().runTaskTimer(this, this::runWorkers, 40L, 40L);
        getLogger().info("AutomatedWorkers enabled.");
    }

    @Override
    public void onDisable() {
        if (workTask != null) workTask.cancel();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command must be used in game.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /worker assign <mine|lumberjack|dig>, /worker chest, /worker stop, /worker status", NamedTextColor.YELLOW));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "assign" -> assign(player, args);
            case "chest" -> linkChest(player);
            case "stop" -> stopWorker(player);
            case "status" -> showStatus(player);
            default -> player.sendMessage(Component.text("Unknown action. Use /worker for help.", NamedTextColor.RED));
        }
        return true;
    }

    private void assign(Player player, String[] args) {
        if (args.length != 2 || !Set.of("mine", "lumberjack", "dig").contains(args[1].toLowerCase(Locale.ROOT))) {
            player.sendMessage(Component.text("Choose a job: mine, lumberjack, or dig.", NamedTextColor.YELLOW));
            return;
        }
        LivingEntity mob = nearestMob(player);
        if (mob == null) {
            player.sendMessage(Component.text("No suitable living mob within 6 blocks.", NamedTextColor.RED));
            return;
        }
        String job = args[1].toLowerCase(Locale.ROOT);
        mob.setAI(false);
        mob.getPersistentDataContainer().set(jobKey, PersistentDataType.STRING, job);
        mob.getPersistentDataContainer().remove(chestKey);
        player.sendMessage(Component.text("Assigned " + mob.getType().name().toLowerCase(Locale.ROOT) + " as a " + job + " worker. Use /worker chest while looking at a chest to link it.", NamedTextColor.GREEN));
    }

    private void linkChest(Player player) {
        Block target = player.getTargetBlockExact(6);
        if (target == null || !(target.getState() instanceof Chest)) {
            player.sendMessage(Component.text("Look at a chest within 6 blocks.", NamedTextColor.RED));
            return;
        }
        LivingEntity mob = nearestWorker(player);
        if (mob == null) {
            player.sendMessage(Component.text("No assigned worker within 6 blocks.", NamedTextColor.RED));
            return;
        }
        mob.getPersistentDataContainer().set(chestKey, PersistentDataType.STRING, target.getLocation().toString());
        player.sendMessage(Component.text("Linked worker to this chest. Harvested items will be deposited there.", NamedTextColor.GREEN));
    }

    private void stopWorker(Player player) {
        LivingEntity mob = nearestWorker(player);
        if (mob == null) {
            player.sendMessage(Component.text("No assigned worker within 6 blocks.", NamedTextColor.RED));
            return;
        }
        mob.getPersistentDataContainer().remove(jobKey);
        mob.getPersistentDataContainer().remove(chestKey);
        mob.setAI(true);
        player.sendMessage(Component.text("Worker stopped; its normal AI is restored.", NamedTextColor.GREEN));
    }

    private void showStatus(Player player) {
        List<LivingEntity> workers = nearbyWorkers(player);
        if (workers.isEmpty()) {
            player.sendMessage(Component.text("No assigned workers within 16 blocks.", NamedTextColor.YELLOW));
            return;
        }
        for (LivingEntity worker : workers) {
            String job = worker.getPersistentDataContainer().get(jobKey, PersistentDataType.STRING);
            String chest = worker.getPersistentDataContainer().get(chestKey, PersistentDataType.STRING);
            player.sendMessage(Component.text(worker.getType().name().toLowerCase(Locale.ROOT) + " — " + job + (chest == null ? " (no chest)" : " (chest linked)"), NamedTextColor.AQUA));
        }
    }

    private LivingEntity nearestMob(Player player) {
        return player.getNearbyEntities(6, 4, 6).stream()
                .filter(e -> e instanceof LivingEntity && !(e instanceof Player))
                .map(e -> (LivingEntity) e)
                .filter(e -> !e.isDead() && !e.getPersistentDataContainer().has(jobKey))
                .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(player.getLocation())))
                .orElse(null);
    }

    private LivingEntity nearestWorker(Player player) {
        return nearbyWorkers(player).stream()
                .filter(e -> e.getLocation().distanceSquared(player.getLocation()) <= 36.0)
                .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(player.getLocation())))
                .orElse(null);
    }

    private List<LivingEntity> nearbyWorkers(Player player) {
        List<LivingEntity> workers = new ArrayList<>();
        for (Entity entity : player.getNearbyEntities(16, 8, 16)) {
            if (entity instanceof LivingEntity living && !(living instanceof Player) && living.getPersistentDataContainer().has(jobKey)) workers.add(living);
        }
        return workers;
    }

    private void runWorkers() {
        for (var world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof LivingEntity worker) || !worker.getPersistentDataContainer().has(jobKey)) continue;
                String job = worker.getPersistentDataContainer().get(jobKey, PersistentDataType.STRING);
                if (job == null || !worker.getChunk().isLoaded()) continue;
                switch (job) {
                    case "mine" -> harvest(worker, this::isOre, 4);
                    case "lumberjack" -> harvest(worker, this::isLog, 5);
                    case "dig" -> harvest(worker, this::isDiggable, 4);
                    default -> worker.getPersistentDataContainer().remove(jobKey);
                }
            }
        }
    }

    private void harvest(LivingEntity worker, java.util.function.Predicate<Material> target, int radius) {
        Location origin = worker.getLocation();
        int centerX = origin.getBlockX();
        int centerY = origin.getBlockY();
        int centerZ = origin.getBlockZ();
        Block selected = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int y = Math.max(worker.getWorld().getMinHeight(), centerY - radius); y <= Math.min(worker.getWorld().getMaxHeight() - 1, centerY + radius); y++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    Block block = worker.getWorld().getBlockAt(x, y, z);
                    if (!target.test(block.getType())) continue;
                    double distance = block.getLocation().distanceSquared(origin);
                    if (distance < bestDistance) { bestDistance = distance; selected = block; }
                }
            }
        }
        if (selected == null) return;
        BlockState state = selected.getState();
        List<ItemStack> drops = new ArrayList<>(selected.getDrops());
        selected.setType(Material.AIR, false);
        for (ItemStack drop : drops) depositOrDrop(worker, drop);
        worker.getWorld().playEffect(selected.getLocation(), org.bukkit.Effect.STEP_SOUND, state.getType());
    }

    private void depositOrDrop(LivingEntity worker, ItemStack item) {
        String locationText = worker.getPersistentDataContainer().get(chestKey, PersistentDataType.STRING);
        if (locationText != null) {
            Location chestLocation = parseLocation(locationText, worker.getWorld());
            if (chestLocation != null && chestLocation.getBlock().getState() instanceof Chest chest) {
                Inventory inventory = chest.getBlockInventory();
                if (inventory.addItem(item).isEmpty()) return;
            }
        }
        worker.getWorld().dropItemNaturally(worker.getLocation(), item);
    }

    private Location parseLocation(String text, org.bukkit.World world) {
        try {
            String[] parts = text.split(",");
            if (parts.length < 3) return null;
            return new Location(world, Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean isOre(Material material) {
        String name = material.name();
        return name.endsWith("_ORE") || name.equals("ANCIENT_DEBRIS") || name.equals("RAW_COPPER_BLOCK") || name.equals("RAW_IRON_BLOCK") || name.equals("RAW_GOLD_BLOCK");
    }

    private boolean isLog(Material material) {
        return material.name().endsWith("_LOG") || material.name().endsWith("_WOOD") || material.name().equals("CRIMSON_STEM") || material.name().equals("WARPED_STEM") || material.name().equals("MANGROVE_ROOTS");
    }

    private boolean isDiggable(Material material) {
        return material == Material.DIRT || material == Material.COARSE_DIRT || material == Material.ROOTED_DIRT || material == Material.GRASS_BLOCK || material == Material.SAND || material == Material.RED_SAND || material == Material.GRAVEL || material == Material.CLAY || material == Material.MUD || material == Material.MYCELIUM || material == Material.PODZOL;
    }

    @EventHandler
    public void onWorkerDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.getPersistentDataContainer().has(jobKey)) {
            entity.getPersistentDataContainer().remove(jobKey);
            entity.getPersistentDataContainer().remove(chestKey);
        }
    }
}
