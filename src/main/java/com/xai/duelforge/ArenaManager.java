package com.xai.duelforge;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class ArenaManager implements Listener {

    private final DuelForge plugin;
    private Map<Player, ArenaSetup> arenaSetups = new HashMap<>();
    private Map<String, ArenaTemplate> templates = new HashMap<>();
    private Random random = new Random();

    public ArenaManager(DuelForge plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        loadDefaultTemplates();
        startHazardTasks();
        startEventTasks();
    }

    public void handleArenaCommand(Player player, String[] args) {
        if (!plugin.getConfig().getBoolean("duelforge.arenas.enabled")) {
            player.sendMessage(ChatColor.RED + "Arenas are disabled!");
            return;
        }
        if (!player.hasPermission("duelforge.admin")) {
            player.sendMessage(ChatColor.RED + "No permission!");
            return;
        }
        if (args.length == 0) {
            player.sendMessage(ChatColor.YELLOW + "Arena Commands:");
            player.sendMessage(ChatColor.YELLOW + "/arena create <name>");
            player.sendMessage(ChatColor.YELLOW + "/arena setpos1|setpos2 <name>");
            player.sendMessage(ChatColor.YELLOW + "/arena weather|time|hazard|event <name> <value>");
            player.sendMessage(ChatColor.YELLOW + "/arena finish <name>");
            player.sendMessage(ChatColor.YELLOW + "/arena delete <name>");
            player.sendMessage(ChatColor.YELLOW + "/arena template load <name> <template>");
            player.sendMessage(ChatColor.YELLOW + "/arena list");
            return;
        }

        String action = args[0].toLowerCase();
        if (args.length < 2 && !action.equals("list")) {
            player.sendMessage(ChatColor.RED + "Specify an arena name!");
            return;
        }
        String name = args.length >= 2 ? args[1] : "";

        switch (action) {
            case "create":
                startArenaSetup(player, name);
                break;
            case "setpos1":
                setPosition(player, name, true);
                break;
            case "setpos2":
                setPosition(player, name, false);
                break;
            case "weather":
                if (args.length == 3) setWeather(player, name, args[2]);
                else player.sendMessage(ChatColor.RED + "Usage: /arena weather <name> <clear|rain|thunder>");
                break;
            case "time":
                if (args.length == 3) setTime(player, name, args[2]);
                else player.sendMessage(ChatColor.RED + "Usage: /arena time <name> <day|night>");
                break;
            case "hazard":
                if (args.length == 3) setHazard(player, name, args[2]);
                else player.sendMessage(ChatColor.RED + "Usage: /arena hazard <name> <none|lavarain|tntshower|mobspawn>");
                break;
            case "event":
                if (args.length == 3) setEvent(player, name, args[2]);
                else player.sendMessage(ChatColor.RED + "Usage: /arena event <name> <none|suddendeath|powerup>");
                break;
            case "finish":
                finishArenaSetup(player, name);
                break;
            case "delete":
                deleteArena(player, name);
                break;
            case "template":
                if (args.length == 4 && args[2].equalsIgnoreCase("load")) {
                    loadTemplate(player, name, args[3]);
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /arena template load <name> <template>");
                }
                break;
            case "list":
                listArenas(player);
                break;
            default:
                player.sendMessage(ChatColor.RED + "Unknown action!");
        }
    }

    private void startArenaSetup(Player player, String name) {
        if (plugin.arenas.containsKey(name)) {
            player.sendMessage(ChatColor.RED + "Arena '" + name + "' already exists!");
            return;
        }
        arenaSetups.put(player, new ArenaSetup(name));
        player.sendMessage(ChatColor.GREEN + "Started arena setup for '" + name + "'. Use a stick to set positions or /arena setpos1|setpos2 <name>");
        player.getInventory().addItem(new ItemStack(Material.STICK));
    }

    private void setPosition(Player player, String name, boolean pos1) {
        ArenaSetup setup = arenaSetups.get(player);
        if (setup == null || !setup.name.equals(name)) {
            player.sendMessage(ChatColor.RED + "Start setup first with /arena create " + name);
            return;
        }
        Location loc = player.getLocation();
        if (pos1) setup.pos1 = loc;
        else setup.pos2 = loc;
        player.sendMessage(ChatColor.GREEN + (pos1 ? "Position 1" : "Position 2") + " set at " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
    }

    private void setWeather(Player player, String name, String weather) {
        ArenaSetup setup = arenaSetups.get(player);
        DuelForge.Arena arena = plugin.arenas.get(name);
        if (setup == null && arena == null) {
            player.sendMessage(ChatColor.RED + "No active setup or existing arena named '" + name + "'!");
            return;
        }
        String w = weather.toLowerCase();
        if (Arrays.asList("clear", "rain", "thunder").contains(w)) {
            if (setup != null) setup.weather = w;
            if (arena != null) {
                arena.weather = w;
                applyArenaSettings(arena);
            }
            player.sendMessage(ChatColor.GREEN + "Weather for '" + name + "' set to " + w);
        } else {
            player.sendMessage(ChatColor.RED + "Valid options: clear, rain, thunder");
        }
    }

    private void setTime(Player player, String name, String time) {
        ArenaSetup setup = arenaSetups.get(player);
        DuelForge.Arena arena = plugin.arenas.get(name);
        if (setup == null && arena == null) {
            player.sendMessage(ChatColor.RED + "No active setup or existing arena named '" + name + "'!");
            return;
        }
        String t = time.toLowerCase();
        if (Arrays.asList("day", "night").contains(t)) {
            if (setup != null) setup.time = t;
            if (arena != null) {
                arena.time = t;
                applyArenaSettings(arena);
            }
            player.sendMessage(ChatColor.GREEN + "Time for '" + name + "' set to " + t);
        } else {
            player.sendMessage(ChatColor.RED + "Valid options: day, night");
        }
    }

    private void setHazard(Player player, String name, String hazard) {
        if (!plugin.getConfig().getBoolean("duelforge.arenas.hazards.enabled")) {
            player.sendMessage(ChatColor.RED + "Hazards are disabled!");
            return;
        }
        ArenaSetup setup = arenaSetups.get(player);
        DuelForge.Arena arena = plugin.arenas.get(name);
        if (setup == null && arena == null) {
            player.sendMessage(ChatColor.RED + "No active setup or existing arena named '" + name + "'!");
            return;
        }
        String h = hazard.toLowerCase();
        if (Arrays.asList("none", "lavarain", "tntshower", "mobspawn").contains(h)) {
            if (setup != null) setup.hazard = h;
            if (arena != null) arena.hazard = h;
            player.sendMessage(ChatColor.GREEN + "Hazard for '" + name + "' set to " + h);
        } else {
            player.sendMessage(ChatColor.RED + "Valid options: none, lavarain, tntshower, mobspawn");
        }
    }

    private void setEvent(Player player, String name, String event) {
        if (!plugin.getConfig().getBoolean("duelforge.arenas.events.enabled")) {
            player.sendMessage(ChatColor.RED + "Events are disabled!");
            return;
        }
        ArenaSetup setup = arenaSetups.get(player);
        DuelForge.Arena arena = plugin.arenas.get(name);
        if (setup == null && arena == null) {
            player.sendMessage(ChatColor.RED + "No active setup or existing arena named '" + name + "'!");
            return;
        }
        String e = event.toLowerCase();
        if (Arrays.asList("none", "suddendeath", "powerup").contains(e)) {
            if (setup != null) setup.event = e;
            if (arena != null) arena.event = e;
            player.sendMessage(ChatColor.GREEN + "Event for '" + name + "' set to " + e);
        } else {
            player.sendMessage(ChatColor.RED + "Valid options: none, suddendeath, powerup");
        }
    }

    private void finishArenaSetup(Player player, String name) {
        ArenaSetup setup = arenaSetups.remove(player);
        if (setup == null || !setup.name.equals(name)) {
            player.sendMessage(ChatColor.RED + "No active setup for '" + name + "'!");
            return;
        }
        if (setup.pos1 == null || setup.pos2 == null) {
            player.sendMessage(ChatColor.RED + "Set both positions first!");
            return;
        }
        ItemStack icon = new ItemStack(getIconMaterial(setup.hazard));
        DuelForge.Arena arena = new DuelForge.Arena(name, setup.pos1, setup.pos2, icon);
        arena.weather = setup.weather;
        arena.time = setup.time;
        arena.hazard = setup.hazard;
        arena.event = setup.event;
        plugin.arenas.put(name, arena);
        applyArenaSettings(arena);
        player.sendMessage(ChatColor.GREEN + "Arena '" + name + "' created successfully!");
    }

    private Material getIconMaterial(String hazard) {
        switch (hazard) {
            case "lavarain": return Material.LAVA_BUCKET;
            case "tntshower": return Material.TNT;
            case "mobspawn": return Material.ZOMBIE_HEAD;
            default: return Material.GRASS_BLOCK;
        }
    }

    private void deleteArena(Player player, String name) {
        if (plugin.arenas.remove(name) != null) {
            player.sendMessage(ChatColor.GREEN + "Arena '" + name + "' deleted!");
        } else {
            player.sendMessage(ChatColor.RED + "Arena '" + name + "' not found!");
        }
    }

    private void loadTemplate(Player player, String name, String templateName) {
        if (!plugin.getConfig().getBoolean("duelforge.arenas.templates")) {
            player.sendMessage(ChatColor.RED + "Templates are disabled!");
            return;
        }
        ArenaTemplate template = templates.get(templateName.toLowerCase());
        if (template == null) {
            player.sendMessage(ChatColor.RED + "Template '" + templateName + "' not found! Available: " + String.join(", ", templates.keySet()));
            return;
        }
        if (plugin.arenas.containsKey(name)) {
            player.sendMessage(ChatColor.RED + "Arena '" + name + "' already exists!");
            return;
        }
        Location center = player.getLocation();
        DuelForge.Arena arena = template.load(center, name);
        plugin.arenas.put(name, arena);
        applyArenaSettings(arena);
        player.sendMessage(ChatColor.GREEN + "Loaded template '" + templateName + "' as arena '" + name + "'!");
    }

    private void listArenas(Player player) {
        if (plugin.arenas.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "No arenas created yet!");
            return;
        }
        player.sendMessage(ChatColor.GREEN + "Available Arenas:");
        for (String name : plugin.arenas.keySet()) {
            DuelForge.Arena arena = plugin.arenas.get(name);
            player.sendMessage(ChatColor.YELLOW + "- " + name + " [Weather: " + arena.weather + ", Time: " + arena.time + ", Hazard: " + arena.hazard + ", Event: " + arena.event + "]");
        }
    }

    private void applyArenaSettings(DuelForge.Arena arena) {
        World world = arena.pos1.getWorld();
        switch (arena.weather) {
            case "rain": 
                world.setStorm(true); 
                world.setThundering(false); 
                break;
            case "thunder": 
                world.setStorm(true); 
                world.setThundering(true); 
                break;
            default: 
                world.setStorm(false); 
                world.setThundering(false); 
                break;
        }
        switch (arena.time) {
            case "day": world.setTime(1000); break;
            case "night": world.setTime(13000); break;
        }
    }

    private void startHazardTasks() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (DuelForge.Duel duel : new HashSet<>(plugin.getActiveDuels().values())) {
                    DuelForge.Arena arena = duel.arena;
                    if (arena.pos1 == null || arena.pos2 == null) continue;

                    if (!plugin.getConfig().getBoolean("duelforge.arenas.hazards.enabled")) continue;

                    switch (arena.hazard) {
                        case "lavarain":
                            if (plugin.getConfig().getBoolean("duelforge.arenas.hazards.lavarain"))
                                spawnLavaRain(arena);
                            break;
                        case "tntshower":
                            if (plugin.getConfig().getBoolean("duelforge.arenas.hazards.tntshower"))
                                spawnTNTShower(arena);
                            break;
                        case "mobspawn":
                            if (plugin.getConfig().getBoolean("duelforge.arenas.hazards.mobspawn"))
                                spawnMobs(arena);
                            break;
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 200); // Every 10 seconds
    }

    private void startEventTasks() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (DuelForge.Duel duel : new HashSet<>(plugin.getActiveDuels().values())) {
                    DuelForge.Arena arena = duel.arena;
                    if (arena.pos1 == null || arena.pos2 == null) continue;

                    if (!plugin.getConfig().getBoolean("duelforge.arenas.events.enabled")) continue;

                    switch (arena.event) {
                        case "suddendeath":
                            if (plugin.getConfig().getBoolean("duelforge.arenas.events.suddendeath"))
                                triggerSuddenDeath(duel);
                            break;
                        case "powerup":
                            if (plugin.getConfig().getBoolean("duelforge.arenas.events.powerup"))
                                spawnPowerUp(arena);
                            break;
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 600); // Every 30 seconds
    }

    private void spawnLavaRain(DuelForge.Arena arena) {
        World world = arena.pos1.getWorld();
        for (int i = 0; i < 5; i++) {
            double x = random.nextDouble() * (arena.pos2.getX() - arena.pos1.getX()) + arena.pos1.getX();
            double z = random.nextDouble() * (arena.pos2.getZ() - arena.pos1.getZ()) + arena.pos1.getZ();
            double y = Math.max(arena.pos1.getY(), arena.pos2.getY()) + 10;
            Location loc = new Location(world, x, y, z);
            Block block = world.getBlockAt(loc);
            if (block.getType() == Material.AIR) block.setType(Material.LAVA);
        }
        arena.pos1.getWorld().getPlayers().stream()
            .filter(p -> plugin.getActiveDuels().containsKey(p) && plugin.getActiveDuels().get(p).arena == arena)
            .forEach(p -> p.sendMessage(ChatColor.RED + "Lava rain incoming!"));
    }

    private void spawnTNTShower(DuelForge.Arena arena) {
        World world = arena.pos1.getWorld();
        for (int i = 0; i < 3; i++) {
            double x = random.nextDouble() * (arena.pos2.getX() - arena.pos1.getX()) + arena.pos1.getX();
            double z = random.nextDouble() * (arena.pos2.getZ() - arena.pos1.getZ()) + arena.pos1.getZ();
            double y = Math.max(arena.pos1.getY(), arena.pos2.getY()) + 10;
            Location loc = new Location(world, x, y, z);
            TNTPrimed tnt = world.spawn(loc, TNTPrimed.class);
            tnt.setFuseTicks(80);
            tnt.setVelocity(new Vector(0, -0.5, 0));
        }
        arena.pos1.getWorld().getPlayers().stream()
            .filter(p -> plugin.getActiveDuels().containsKey(p) && plugin.getActiveDuels().get(p).arena == arena)
            .forEach(p -> p.sendMessage(ChatColor.RED + "TNT shower incoming!"));
    }

    private void spawnMobs(DuelForge.Arena arena) {
        World world = arena.pos1.getWorld();
        for (int i = 0; i < 2; i++) {
            double x = random.nextDouble() * (arena.pos2.getX() - arena.pos1.getX()) + arena.pos1.getX();
            double z = random.nextDouble() * (arena.pos2.getZ() - arena.pos1.getZ()) + arena.pos1.getZ();
            double y = Math.max(arena.pos1.getY(), arena.pos2.getY());
            Location loc = new Location(world, x, y, z);
            world.spawnEntity(loc, random.nextBoolean() ? EntityType.ZOMBIE : EntityType.SKELETON);
        }
        arena.pos1.getWorld().getPlayers().stream()
            .filter(p -> plugin.getActiveDuels().containsKey(p) && plugin.getActiveDuels().get(p).arena == arena)
            .forEach(p -> p.sendMessage(ChatColor.RED + "Mobs spawning!"));
    }

    private void triggerSuddenDeath(DuelForge.Duel duel) {
        if (duel.currentRound >= duel.rounds - 1) {
            duel.p1.setHealth(Math.min(duel.p1.getHealth(), 2.0));
            duel.p2.setHealth(Math.min(duel.p2.getHealth(), 2.0));
            duel.p1.sendMessage(ChatColor.RED + "Sudden Death: One hit decides!");
            duel.p2.sendMessage(ChatColor.RED + "Sudden Death: One hit decides!");
        }
    }

    private void spawnPowerUp(DuelForge.Arena arena) {
        World world = arena.pos1.getWorld();
        double x = random.nextDouble() * (arena.pos2.getX() - arena.pos1.getX()) + arena.pos1.getX();
        double z = random.nextDouble() * (arena.pos2.getZ() - arena.pos1.getZ()) + arena.pos1.getZ();
        double y = Math.max(arena.pos1.getY(), arena.pos2.getY()) + 1;
        Location loc = new Location(world, x, y, z);
        ItemStack powerUp = new ItemStack(random.nextBoolean() ? Material.GOLDEN_APPLE : Material.POTION);
        if (powerUp.getType() == Material.POTION) {
            ItemMeta meta = powerUp.getItemMeta();
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Speed Potion");
            powerUp.setItemMeta(meta);
        }
        world.dropItemNaturally(loc, powerUp);
        arena.pos1.getWorld().getPlayers().stream()
            .filter(p -> plugin.getActiveDuels().containsKey(p) && plugin.getActiveDuels().get(p).arena == arena)
            .forEach(p -> p.sendMessage(ChatColor.GREEN + "A power-up has spawned!"));
    }

    private void loadDefaultTemplates() {
        if (!plugin.getConfig().getBoolean("duelforge.arenas.templates")) return;
        templates.put("basic", new ArenaTemplate("basic", Material.GRASS_BLOCK, "clear", "day", "none", "none"));
        templates.put("volcano", new ArenaTemplate("volcano", Material.MAGMA_BLOCK, "rain", "night", "lavarain", "suddendeath"));
        templates.put("desert", new ArenaTemplate("desert", Material.SAND, "clear", "day", "tntshower", "powerup"));
        templates.put("haunted", new ArenaTemplate("haunted", Material.SOUL_SAND, "thunder", "night", "mobspawn", "suddendeath"));
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player player = e.getPlayer();
        ArenaSetup setup = arenaSetups.get(player);
        if (setup == null || (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.LEFT_CLICK_BLOCK)) return;

        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.STICK) return;

        Block block = e.getClickedBlock();
        if (block != null) {
            if (e.getAction() == Action.LEFT_CLICK_BLOCK && setup.pos1 == null) {
                setup.pos1 = block.getLocation();
                player.sendMessage(ChatColor.GREEN + "Position 1 set at " + block.getX() + ", " + block.getY() + ", " + block.getZ());
            } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK && setup.pos2 == null && setup.pos1 != null) {
                setup.pos2 = block.getLocation();
                player.sendMessage(ChatColor.GREEN + "Position 2 set at " + block.getX() + ", " + block.getY() + ", " + block.getZ());
            }
            e.setCancelled(true);
        }
    }

    private class ArenaSetup {
        String name;
        Location pos1, pos2;
        String weather = "clear";
        String time = "day";
        String hazard = "none";
        String event = "none";

        ArenaSetup(String name) {
            this.name = name;
        }
    }

    private class ArenaTemplate {
        String name;
        Material icon;
        String weather, time, hazard, event;

        ArenaTemplate(String name, Material icon, String weather, String time, String hazard, String event) {
            this.name = name;
            this.icon = icon;
            this.weather = weather;
            this.time = time;
            this.hazard = hazard;
            this.event = event;
        }

        DuelForge.Arena load(Location center, String arenaName) {
            int size = 20; // Default arena size
            Location pos1 = center.clone().add(-size, 0, -size);
            Location pos2 = center.clone().add(size, 0, size);
            DuelForge.Arena arena = new DuelForge.Arena(arenaName, pos1, pos2, new ItemStack(icon));
            arena.weather = this.weather;
            arena.time = this.time;
            arena.hazard = this.hazard;
            arena.event = this.event;

            // Apply template-specific terrain
            World world = center.getWorld();
            for (int x = (int) pos1.getX(); x <= pos2.getX(); x++) {
                for (int z = (int) pos1.getZ(); z <= pos2.getZ(); z++) {
                    Location floor = new Location(world, x, Math.min(pos1.getY(), pos2.getY()) - 1, z);
                    world.getBlockAt(floor).setType(icon);
                }
            }
            return arena;
        }
    }
}