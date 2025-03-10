package com.xai.duelforge;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent; // Added for TextComponent
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class DuelForge extends JavaPlugin implements Listener {

    private Map<String, DuelRequest> duelRequests = new HashMap<>();
    Map<String, Kit> kits = new HashMap<>();
    Map<String, Arena> arenas = new HashMap<>();
    Map<Player, Duel> activeDuels = new HashMap<>();
    Map<Player, Integer> eloScores = new HashMap<>();
    Map<Player, Party> parties = new HashMap<>();
    Map<Player, Location> arenaSetup = new HashMap<>();
    List<Player> rankedQueue = new ArrayList<>();
    List<Player> unrankedQueue = new ArrayList<>();
    private Map<Player, PlayerStats> stats = new HashMap<>();
    private Map<Player, List<ReplayAction>> replays = new HashMap<>();
    private Map<Player, Set<String>> cosmetics = new HashMap<>();
    private File statsFile;
    private YamlConfiguration statsConfig;
    private YamlConfiguration config;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml"));
        getServer().getPluginManager().registerEvents(this, this);
        initializeDefaults();
        if (config.getBoolean("duelforge.scoring.enabled")) startScoreboardTask();
        if (config.getBoolean("duelforge.scoring.stats")) loadStats();
        getLogger().info("DuelForge Enhanced - The Ultimate Dueling Experience!");
    }

    @Override
    public void onDisable() {
        if (config.getBoolean("general.save-stats")) saveStats();
        getLogger().info("DuelForge Disabled!");
    }

    private void initializeDefaults() {
        if (config.getBoolean("duelforge.kits.enabled")) {
            ItemStack[] warriorItems = { new ItemStack(Material.DIAMOND_SWORD) };
            kits.put("Warrior", new Kit("Warrior", warriorItems));
            ItemStack[] archerItems = { new ItemStack(Material.BOW), new ItemStack(Material.ARROW, 64) };
            kits.put("Archer", new Kit("Archer", archerItems));
        }
        if (config.getBoolean("duelforge.arenas.enabled")) {
            arenas.put("Plains", new Arena("Plains", null, null, new ItemStack(Material.GRASS_BLOCK)));
            arenas.put("Desert", new Arena("Desert", null, null, new ItemStack(Material.SAND)));
        }
        statsFile = new File(getDataFolder(), "stats.yml");
        statsConfig = YamlConfiguration.loadConfiguration(statsFile);
    }

    private void startScoreboardTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : activeDuels.keySet()) {
                    updateScoreboard(p);
                    if (config.getBoolean("duelforge.replays.enabled")) recordReplayAction(p);
                }
            }
        }.runTaskTimer(this, 0, 1);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return false;
        Player player = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("duel")) {
            if (!config.getBoolean("duelforge.duels.enabled")) {
                player.sendMessage(ChatColor.RED + "Duels are disabled!");
                return true;
            }
            if (args.length == 1) {
                Player target = Bukkit.getPlayer(args[0]);
                if (target != null && target != player && !activeDuels.containsKey(player) && !activeDuels.containsKey(target)) {
                    new GuiManager(this).openKitGui(player, target, true, false);
                    return true;
                }
                player.sendMessage(ChatColor.RED + "Invalid target or in duel!");
            }
            return false;
        }

        if (cmd.getName().equalsIgnoreCase("accept")) {
            if (!config.getBoolean("duelforge.duels.enabled")) {
                player.sendMessage(ChatColor.RED + "Duels are disabled!");
                return true;
            }
            DuelRequest request = duelRequests.remove(player.getName());
            if (request != null) {
                startDuel(request.initiator, player, request.kit, request.rounds, request.arena, request.ranked, request.modifiers);
                return true;
            }
            player.sendMessage(ChatColor.RED + "No duel request found!");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("kits")) {
            if (!config.getBoolean("duelforge.kits.enabled")) {
                player.sendMessage(ChatColor.RED + "Kits are disabled!");
                return true;
            }
            if (args.length == 0) new GuiManager(this).openKitGui(player, null, false, false);
            else if (player.hasPermission("duelforge.admin")) {
                if (args[0].equalsIgnoreCase("create") && args.length == 2) createKit(player, args[1]);
                else if (args[0].equalsIgnoreCase("delete") && args.length == 2) deleteKit(player, args[1]);
                else if (args[0].equalsIgnoreCase("save") && args.length == 2 && config.getBoolean("duelforge.kits.custom-inventory")) saveKit(player, args[1]);
                else return false;
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("queue")) {
            if (!config.getBoolean("duelforge.queues.enabled")) {
                player.sendMessage(ChatColor.RED + "Queues are disabled!");
                return true;
            }
            if (args.length == 1 && ((args[0].equalsIgnoreCase("ranked") && config.getBoolean("duelforge.queues.ranked-queue")) || 
                                     (args[0].equalsIgnoreCase("unranked") && config.getBoolean("duelforge.queues.unranked-queue")))) {
                new GuiManager(this).openKitGui(player, null, args[0].equalsIgnoreCase("ranked"), true);
                return true;
            }
            player.sendMessage(ChatColor.RED + "Invalid queue type or disabled!");
            return false;
        }

        if (cmd.getName().equalsIgnoreCase("party")) {
            if (!config.getBoolean("duelforge.parties.enabled")) {
                player.sendMessage(ChatColor.RED + "Parties are disabled!");
                return true;
            }
            new QueueAndPartyManager(this, new GuiManager(this)).handlePartyCommand(player, args);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("arena")) {
            if (!config.getBoolean("duelforge.arenas.enabled")) {
                player.sendMessage(ChatColor.RED + "Arenas are disabled!");
                return true;
            }
            new ArenaManager(this).handleArenaCommand(player, args);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("spectate")) {
            if (!config.getBoolean("duelforge.spectator.enabled")) {
                player.sendMessage(ChatColor.RED + "Spectator mode is disabled!");
                return true;
            }
            if (args.length == 1) {
                Player target = Bukkit.getPlayer(args[0]);
                if (target != null && activeDuels.containsKey(target)) {
                    spectateDuel(player, target);
                    return true;
                }
                player.sendMessage(ChatColor.RED + "Player not in duel!");
            }
            return false;
        }

        if (cmd.getName().equalsIgnoreCase("stats")) {
            if (!config.getBoolean("duelforge.scoring.stats")) {
                player.sendMessage(ChatColor.RED + "Stats are disabled!");
                return true;
            }
            new GuiManager(this).openStatsGui(player);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("leaderboard")) {
            if (!config.getBoolean("duelforge.scoring.leaderboards")) {
                player.sendMessage(ChatColor.RED + "Leaderboards are disabled!");
                return true;
            }
            new GuiManager(this).openLeaderboardGui(player);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("replay")) {
            if (!config.getBoolean("duelforge.replays.enabled")) {
                player.sendMessage(ChatColor.RED + "Replays are disabled!");
                return true;
            }
            if (args.length == 1) {
                new GuiManager(this).openReplayGui(player, args[0]);
                return true;
            }
            return false;
        }

        if (cmd.getName().equalsIgnoreCase("cosmetics")) {
            if (!config.getBoolean("duelforge.cosmetics.enabled")) {
                player.sendMessage(ChatColor.RED + "Cosmetics are disabled!");
                return true;
            }
            new GuiManager(this).openCosmeticsGui(player);
            return true;
        }

        return false;
    }

    public void startDuel(Player p1, Player p2, Kit kit, int rounds, Arena arena, boolean ranked, List<String> modifiers) {
        if (!config.getBoolean("duelforge.duels.enabled")) {
            p1.sendMessage(ChatColor.RED + "Duels are disabled!");
            return;
        }
        if (ranked && !config.getBoolean("duelforge.duels.ranked")) {
            p1.sendMessage(ChatColor.RED + "Ranked duels are disabled!");
            return;
        }
        if (!ranked && !config.getBoolean("duelforge.duels.unranked")) {
            p1.sendMessage(ChatColor.RED + "Unranked duels are disabled!");
            return;
        }
        List<String> enabledModifiers = new ArrayList<>();
        if (config.getBoolean("duelforge.duels.modifiers")) {
            for (String mod : modifiers) {
                if (config.getBoolean("duelforge.duels.modifiers." + mod.toLowerCase())) {
                    enabledModifiers.add(mod);
                }
            }
        }
        arena.reset();
        p1.teleport(arena.pos1);
        p2.teleport(arena.pos2);
        p1.getInventory().setContents(kit.items);
        p2.getInventory().setContents(kit.items);
        Duel duel = new Duel(p1, p2, rounds, arena, ranked, enabledModifiers);
        duel.kit = kit; // Set kit explicitly
        activeDuels.put(p1, duel);
        activeDuels.put(p2, duel);
        if (config.getBoolean("duelforge.scoring.elo")) {
            eloScores.putIfAbsent(p1, 1000);
            eloScores.putIfAbsent(p2, 1000);
        }
        applyModifiers(duel, p1, p2, enabledModifiers);
        if (config.getBoolean("duelforge.cosmetics.enabled")) {
            applyCosmetics(p1);
            applyCosmetics(p2);
        }
        if (config.getBoolean("duelforge.replays.enabled")) {
            replays.put(p1, new ArrayList<>());
            replays.put(p2, new ArrayList<>());
        }
        if (config.getBoolean("general.broadcast-duels")) broadcastDuelStart(p1, p2, kit, arena, ranked, enabledModifiers);
    }

    private void applyModifiers(Duel duel, Player p1, Player p2, List<String> modifiers) {
        for (String mod : modifiers) {
            switch (mod) {
                case "LowGravity": p1.setGravity(false); p2.setGravity(false); break;
                case "SpeedBoost": 
                    p1.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1)); 
                    p2.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1)); 
                    break;
                case "NoFallDamage": p1.setFallDistance(0); p2.setFallDistance(0); break;
                case "OneHitKills": duel.oneHitKills = true; break;
                case "RegenBoost": 
                    p1.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 1)); 
                    p2.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 1)); 
                    break;
            }
        }
    }

    private void applyCosmetics(Player player) {
        Set<String> owned = getCosmetics().getOrDefault(player, new HashSet<>());
        if (owned.contains("FlameTrail") && config.getBoolean("duelforge.cosmetics.flame-trail")) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!activeDuels.containsKey(player)) cancel();
                    player.getWorld().spawnParticle(Particle.FLAME, player.getLocation(), 5, 0.1, 0.1, 0.1, 0);
                }
            }.runTaskTimer(this, 0, 2);
        }
        if (owned.contains("VictoryDance") && config.getBoolean("duelforge.cosmetics.victory-dance")) {
            // Placeholder for dance animation
        }
        if (owned.contains("LegendTag") && config.getBoolean("duelforge.cosmetics.legend-tag")) {
            player.setDisplayName("[Legend] " + player.getName());
        }
    }

    private void broadcastDuelStart(Player p1, Player p2, Kit kit, Arena arena, boolean ranked, List<String> modifiers) {
        String mode = ranked ? "Ranked" : "Unranked";
        String modStr = modifiers.isEmpty() ? "" : " [Modifiers: " + String.join(", ", modifiers) + "]";
        Bukkit.broadcastMessage(ChatColor.GOLD + "[DuelForge] " + mode + " Duel: " + p1.getName() + " vs " + p2.getName() + " in " + arena.name + " with " + kit.name + modStr);
    }

    private void updateScoreboard(Player player) {
        Duel duel = activeDuels.get(player);
        if (duel == null) return;
        Player opponent = duel.p1 == player ? duel.p2 : duel.p1;
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = manager.getNewScoreboard();
        Objective obj = board.registerNewObjective("duel", "dummy", ChatColor.GOLD + "DuelForge");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        Score title = obj.getScore(ChatColor.YELLOW + player.getName() + " vs " + opponent.getName());
        title.setScore(8);
        Score score = obj.getScore(ChatColor.GREEN + "Score: " + duel.score1 + " - " + duel.score2);
        score.setScore(7);
        Score rounds = obj.getScore(ChatColor.BLUE + "Round: " + duel.currentRound + "/" + duel.rounds);
        rounds.setScore(6);
        if (config.getBoolean("duelforge.scoring.elo")) {
            Score elo = obj.getScore(ChatColor.RED + "Elo: " + eloScores.get(player));
            elo.setScore(5);
            Score oppElo = obj.getScore(ChatColor.RED + "Opp Elo: " + eloScores.get(opponent));
            oppElo.setScore(4);
            Score rank = obj.getScore(ChatColor.AQUA + "Rank: " + getRank(eloScores.get(player)));
            rank.setScore(2);
        }
        if (config.getBoolean("duelforge.scoring.stats")) {
            Score kd = obj.getScore(ChatColor.GRAY + "K/D: " + getStats().getOrDefault(player, new PlayerStats()).kills + "/" + getStats().getOrDefault(player, new PlayerStats()).deaths);
            kd.setScore(3);
            Score streak = obj.getScore(ChatColor.YELLOW + "Streak: " + getStats().getOrDefault(player, new PlayerStats()).winStreak);
            streak.setScore(0);
        }
        if (config.getBoolean("duelforge.duels.modifiers")) {
            Score mods = obj.getScore(ChatColor.LIGHT_PURPLE + "Mods: " + (duel.modifiers.isEmpty() ? "None" : String.join(", ", duel.modifiers)));
            mods.setScore(1);
        }
        player.setScoreboard(board);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.GREEN + "Score: " + duel.score1 + " - " + duel.score2));
    }

    private String getRank(int elo) {
        if (elo >= 2000) return "Legend";
        if (elo >= 1500) return "Master";
        if (elo >= 1200) return "Diamond";
        return "Bronze";
    }

    private void createKit(Player player, String name) {
        ItemStack icon = player.getInventory().getItemInMainHand();
        if (icon.getType() != Material.AIR) {
            kits.put(name, new Kit(name, new ItemStack[]{icon.clone()}));
            player.sendMessage(ChatColor.GREEN + "Kit " + name + " created!");
        } else {
            player.sendMessage(ChatColor.RED + "Hold an item!");
        }
    }

    private void deleteKit(Player player, String name) {
        if (kits.remove(name) != null) {
            player.sendMessage(ChatColor.GREEN + "Kit " + name + " deleted!");
        } else {
            player.sendMessage(ChatColor.RED + "Kit not found!");
        }
    }

    private void saveKit(Player player, String name) {
        ItemStack[] inventory = player.getInventory().getContents();
        kits.put(name, new Kit(name, inventory.clone()));
        player.sendMessage(ChatColor.GREEN + "Kit " + name + " saved with full inventory!");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        Duel duel = activeDuels.get(player);
        if (duel != null) endDuel(duel, duel.p1 == player ? duel.p2 : duel.p1, player);
        rankedQueue.remove(player);
        unrankedQueue.remove(player);
        Party party = parties.get(player);
        if (party != null && party.leader == player) {
            parties.remove(player);
            party.broadcast(ChatColor.RED + "Party disbanded!");
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player player = e.getEntity();
        Duel duel = activeDuels.get(player);
        if (duel == null) return;
        Player killer = duel.p1 == player ? duel.p2 : duel.p1;
        if (config.getBoolean("duelforge.scoring.stats")) {
            getStats().computeIfAbsent(killer, k -> new PlayerStats()).kills++;
            getStats().computeIfAbsent(player, k -> new PlayerStats()).deaths++;
        }
        duel.currentRound++;
        if (duel.p1 == player) duel.score2++; else duel.score1++;
        if (duel.currentRound > duel.rounds) {
            endDuel(duel, duel.score1 > duel.score2 ? duel.p1 : duel.p2, duel.score1 > duel.score2 ? duel.p2 : duel.p1);
        } else {
            resetRound(duel);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player) || !(e.getDamager() instanceof Player)) return;
        Player damaged = (Player) e.getEntity();
        Player damager = (Player) e.getDamager();
        Duel duel = activeDuels.get(damaged);
        if (duel != null && duel.oneHitKills) {
            e.setDamage(1000.0);
        }
    }

    private void endDuel(Duel duel, Player winner, Player loser) {
        activeDuels.remove(duel.p1);
        activeDuels.remove(duel.p2);
        duel.arena.reset();
        resetPlayerEffects(duel.p1);
        resetPlayerEffects(duel.p2);
        if (duel.ranked && config.getBoolean("duelforge.scoring.elo")) updateElo(winner, loser);
        if (config.getBoolean("duelforge.scoring.stats")) updateStats(winner, loser, duel);
        if (config.getBoolean("duelforge.cosmetics.enabled")) unlockCosmetics(winner);
        if (config.getBoolean("general.broadcast-duels")) Bukkit.broadcastMessage(ChatColor.GOLD + "[DuelForge] " + winner.getName() + " defeated " + loser.getName() + "!");
    }

    private void resetRound(Duel duel) {
        duel.p1.teleport(duel.arena.pos1);
        duel.p2.teleport(duel.arena.pos2);
        duel.p1.getInventory().setContents(duel.kit.items);
        duel.p2.getInventory().setContents(duel.kit.items);
        resetPlayerEffects(duel.p1);
        resetPlayerEffects(duel.p2);
        applyModifiers(duel, duel.p1, duel.p2, duel.modifiers);
    }

    private void resetPlayerEffects(Player player) {
        player.setGravity(true);
        player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
        player.setHealth(20.0);
        player.setFoodLevel(20);
    }

    private void updateElo(Player winner, Player loser) {
        int winnerElo = eloScores.get(winner);
        int loserElo = eloScores.get(loser);
        int eloChange = calculateEloChange(winnerElo, loserElo);
        eloScores.put(winner, winnerElo + eloChange);
        eloScores.put(loser, loserElo - eloChange);
        winner.sendMessage(ChatColor.GREEN + "Victory! +" + eloChange + " Elo (" + eloScores.get(winner) + ")");
        loser.sendMessage(ChatColor.RED + "Defeat! -" + eloChange + " Elo (" + eloScores.get(loser) + ")");
    }

    private int calculateEloChange(int winnerElo, int loserElo) {
        double expected = 1.0 / (1 + Math.pow(10, (loserElo - winnerElo) / 400.0));
        return (int) (32 * (1 - expected));
    }

    private void updateStats(Player winner, Player loser, Duel duel) {
        PlayerStats wStats = getStats().computeIfAbsent(winner, k -> new PlayerStats());
        PlayerStats lStats = getStats().computeIfAbsent(loser, k -> new PlayerStats());
        wStats.wins++;
        lStats.losses++;
        wStats.winStreak++;
        lStats.winStreak = 0;
        wStats.favoriteKit = duel.kit.name;
    }

    private void unlockCosmetics(Player player) {
        Set<String> owned = getCosmetics().computeIfAbsent(player, k -> new HashSet<>());
        int elo = eloScores.getOrDefault(player, 1000);
        int wins = getStats().getOrDefault(player, new PlayerStats()).wins;
        if (elo >= 1500 && !owned.contains("FlameTrail") && config.getBoolean("duelforge.cosmetics.flame-trail")) owned.add("FlameTrail");
        if (wins >= 10 && !owned.contains("VictoryDance") && config.getBoolean("duelforge.cosmetics.victory-dance")) owned.add("VictoryDance");
        if (elo >= 2000 && !owned.contains("LegendTag") && config.getBoolean("duelforge.cosmetics.legend-tag")) owned.add("LegendTag");
    }

    public void spectateDuel(Player spectator, Player target) {
        Duel duel = activeDuels.get(target);
        spectator.setGameMode(GameMode.SPECTATOR);
        spectator.setSpectatorTarget(target);
        spectator.sendMessage(ChatColor.GREEN + "Spectating " + target.getName() + "!");
    }

    private void recordReplayAction(Player player) {
        Duel duel = activeDuels.get(player);
        if (duel == null) return;
        List<ReplayAction> actions = replays.get(player);
        actions.add(new ReplayAction(player.getLocation(), player.getHealth(), System.currentTimeMillis()));
    }

    private void loadStats() {
        if (!statsFile.exists()) return;
        for (String key : statsConfig.getKeys(false)) {
            Player player = Bukkit.getPlayer(UUID.fromString(key));
            if (player != null) {
                PlayerStats ps = new PlayerStats();
                ps.wins = statsConfig.getInt(key + ".wins");
                ps.losses = statsConfig.getInt(key + ".losses");
                ps.kills = statsConfig.getInt(key + ".kills");
                ps.deaths = statsConfig.getInt(key + ".deaths");
                ps.winStreak = statsConfig.getInt(key + ".winStreak");
                ps.favoriteKit = statsConfig.getString(key + ".favoriteKit", "Warrior");
                getStats().put(player, ps);
                if (config.getBoolean("duelforge.scoring.elo")) {
                    eloScores.put(player, statsConfig.getInt(key + ".elo", 1000));
                }
            }
        }
    }

    private void saveStats() {
        for (Map.Entry<Player, PlayerStats> entry : getStats().entrySet()) {
            String uuid = entry.getKey().getUniqueId().toString();
            PlayerStats ps = entry.getValue();
            statsConfig.set(uuid + ".wins", ps.wins);
            statsConfig.set(uuid + ".losses", ps.losses);
            statsConfig.set(uuid + ".kills", ps.kills);
            statsConfig.set(uuid + ".deaths", ps.deaths);
            statsConfig.set(uuid + ".winStreak", ps.winStreak);
            statsConfig.set(uuid + ".favoriteKit", ps.favoriteKit);
            if (config.getBoolean("duelforge.scoring.elo")) {
                statsConfig.set(uuid + ".elo", eloScores.get(entry.getKey()));
            }
        }
        try {
            statsConfig.save(statsFile);
        } catch (IOException e) {
            getLogger().warning("Failed to save stats!");
        }
    }

    // Public getters for private fields
    public Map<Player, PlayerStats> getStats() { return stats; }
    public Map<Player, Set<String>> getCosmetics() { return cosmetics; }
    public Map<String, DuelRequest> getDuelRequests() { return duelRequests; }
    public Map<Player, Duel> getActiveDuels() { return activeDuels; }
    public YamlConfiguration getConfig() { return config; }

    // Static inner classes
    public static class DuelRequest {
        Player initiator, target;
        Kit kit;
        int rounds;
        Arena arena;
        boolean ranked;
        List<String> modifiers;

        DuelRequest(Player initiator, Player target, Kit kit, int rounds, Arena arena, boolean ranked, List<String> modifiers) {
            this.initiator = initiator;
            this.target = target;
            this.kit = kit;
            this.rounds = rounds;
            this.arena = arena;
            this.ranked = ranked;
            this.modifiers = modifiers;
        }
    }

    public static class Kit {
        String name;
        ItemStack[] items;

        Kit(String name, ItemStack[] items) {
            this.name = name;
            this.items = items;
            ItemStack icon = items[0].clone();
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(ChatColor.GREEN + name);
            icon.setItemMeta(meta);
            this.items[0] = icon;
        }
    }

    public static class Arena {
        String name;
        Location pos1, pos2;
        ItemStack icon;
        String weather = "clear";
        String time = "day";
        String hazard = "none";
        String event = "none";

        Arena(String name, Location pos1, Location pos2, ItemStack icon) {
            this.name = name;
            this.pos1 = pos1;
            this.pos2 = pos2;
            this.icon = icon.clone();
            ItemMeta meta = this.icon.getItemMeta();
            meta.setDisplayName(ChatColor.GREEN + name);
            this.icon.setItemMeta(meta);
        }

        void reset() {
            if (pos1 != null && pos2 != null) {
                pos1.getWorld().getEntitiesByClasses(org.bukkit.entity.Item.class).forEach(e -> {
                    if (isWithinBounds(e.getLocation())) e.remove();
                });
            }
        }

        boolean isWithinBounds(Location loc) {
            return loc.getX() >= Math.min(pos1.getX(), pos2.getX()) && loc.getX() <= Math.max(pos1.getX(), pos2.getX()) &&
                   loc.getZ() >= Math.min(pos1.getZ(), pos2.getZ()) && loc.getZ() <= Math.max(pos1.getZ(), pos2.getZ());
        }
    }

    public static class Duel {
        Player p1, p2;
        int rounds, currentRound, score1, score2;
        Arena arena;
        boolean ranked, oneHitKills;
        Kit kit;
        List<String> modifiers;

        Duel(Player p1, Player p2, int rounds, Arena arena, boolean ranked, List<String> modifiers) {
            this.p1 = p1;
            this.p2 = p2;
            this.rounds = rounds;
            this.currentRound = 1;
            this.score1 = 0;
            this.score2 = 0;
            this.arena = arena;
            this.ranked = ranked;
            this.modifiers = modifiers;
            this.kit = null; // Will be set externally
        }
    }

    public static class Party {
        Player leader;
        List<Player> members = new ArrayList<>();
        int partyElo;

        Party(Player leader) {
            this(leader, Collections.singletonList(leader));
        }

        Party(Player leader, List<Player> members) {
            this.leader = leader;
            this.members.addAll(members);
        }

        void broadcast(String message) {
            members.forEach(m -> m.sendMessage(message));
        }

        void updatePartyElo() {
            // Requires access to DuelForge instance; will fix in QueueAndPartyManager
        }
    }

    public static class PlayerStats {
        int wins, losses, kills, deaths, winStreak;
        String favoriteKit = "Warrior";
    }

    private static class ReplayAction {
        Location location;
        double health;
        long timestamp;

        ReplayAction(Location location, double health, long timestamp) {
            this.location = location;
            this.health = health;
            this.timestamp = timestamp;
        }
    }
}