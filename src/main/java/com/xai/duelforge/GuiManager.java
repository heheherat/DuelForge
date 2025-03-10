package com.xai.duelforge;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class GuiManager implements Listener {
    private final DuelForge plugin;
    private Map<Player, GuiState> guiStates = new HashMap<>();
    private static final int KIT_GUI_SIZE = 54;
    private static final int PARTY_GUI_SIZE = 36;

    public GuiManager(DuelForge plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openKitGui(Player player, Player target, boolean ranked, boolean queue) {
        if (!plugin.getConfig().getBoolean("duelforge.kits.enabled")) {
            player.sendMessage(ChatColor.RED + "Kits are disabled!");
            return;
        }
        Inventory gui = Bukkit.createInventory(player, KIT_GUI_SIZE, ChatColor.DARK_GREEN + "Select Kit");
        int slot = 0;
        for (DuelForge.Kit kit : plugin.kits.values()) {
            ItemStack item = kit.items[0].clone();
            ItemMeta meta = item.getItemMeta();
            meta.setLore(Arrays.asList(ChatColor.GRAY + "Click to select", ChatColor.YELLOW + "Right-click to preview"));
            item.setItemMeta(meta);
            gui.setItem(slot++, item);
        }
        addNavigation(gui, 0, plugin.kits.size());
        player.openInventory(gui);
        guiStates.put(player, new GuiState(target, null, 1, null, ranked, queue, new ArrayList<>(), GuiType.KIT, 0));
    }

    public void openRoundsGui(Player player, Player target, DuelForge.Kit kit, boolean ranked) {
        Inventory gui = Bukkit.createInventory(player, 27, ChatColor.DARK_BLUE + "Select Rounds");
        for (int i = 1; i <= 9; i++) {
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("" + ChatColor.GREEN + i + " Rounds");
            meta.setLore(Arrays.asList(ChatColor.GRAY + "Click to proceed"));
            item.setItemMeta(meta);
            gui.setItem(9 + i - 1, item);
        }
        fillEmptySlots(gui, Material.BLUE_STAINED_GLASS_PANE);
        player.openInventory(gui);
        guiStates.put(player, new GuiState(target, kit, 1, null, ranked, false, new ArrayList<>(), GuiType.ROUNDS, 0));
    }

    public void openArenaGui(Player player, Player target, DuelForge.Kit kit, int rounds, boolean ranked, List<String> modifiers) {
        if (!plugin.getConfig().getBoolean("duelforge.arenas.enabled")) {
            player.sendMessage(ChatColor.RED + "Arenas are disabled!");
            return;
        }
        Inventory gui = Bukkit.createInventory(player, KIT_GUI_SIZE, ChatColor.DARK_RED + "Select Arena");
        int slot = 0;
        for (DuelForge.Arena arena : plugin.arenas.values()) {
            ItemStack item = arena.icon.clone();
            ItemMeta meta = item.getItemMeta();
            meta.setLore(Arrays.asList(ChatColor.GRAY + "Click to select", ChatColor.YELLOW + "Right-click to preview"));
            item.setItemMeta(meta);
            gui.setItem(slot++, item);
        }
        addNavigation(gui, 0, plugin.arenas.size());
        player.openInventory(gui);
        guiStates.put(player, new GuiState(target, kit, rounds, null, ranked, false, modifiers, GuiType.ARENA, 0));
    }

    public void openModifiersGui(Player player, Player target, DuelForge.Kit kit, int rounds, DuelForge.Arena arena, boolean ranked) {
        if (!plugin.getConfig().getBoolean("duelforge.duels.modifiers")) {
            player.sendMessage(ChatColor.RED + "Modifiers are disabled!");
            return;
        }
        Inventory gui = Bukkit.createInventory(player, 27, ChatColor.DARK_PURPLE + "Select Modifiers");
        String[] mods = {"LowGravity", "SpeedBoost", "NoFallDamage", "OneHitKills", "RegenBoost"};
        for (int i = 0; i < mods.length; i++) {
            ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + mods[i]);
            meta.setLore(Arrays.asList(ChatColor.GRAY + "Click to toggle", ChatColor.GREEN + "Left: On", ChatColor.RED + "Right: Off"));
            item.setItemMeta(meta);
            gui.setItem(11 + i, item);
        }
        ItemStack confirm = new ItemStack(Material.EMERALD);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.setDisplayName(ChatColor.GREEN + "Confirm");
        confirm.setItemMeta(confirmMeta);
        gui.setItem(22, confirm);
        fillEmptySlots(gui, Material.PURPLE_STAINED_GLASS_PANE);
        player.openInventory(gui);
        guiStates.put(player, new GuiState(target, kit, rounds, arena, ranked, false, new ArrayList<>(), GuiType.MODIFIERS, 0));
    }

    public void openPartyGui(Player player) {
        if (!plugin.getConfig().getBoolean("duelforge.parties.enabled")) {
            player.sendMessage(ChatColor.RED + "Parties are disabled!");
            return;
        }
        Inventory gui = Bukkit.createInventory(player, PARTY_GUI_SIZE, ChatColor.DARK_AQUA + "Party Management");
        DuelForge.Party party = plugin.parties.get(player);
        
        ItemStack create = new ItemStack(Material.GREEN_WOOL);
        ItemMeta createMeta = create.getItemMeta();
        createMeta.setDisplayName(ChatColor.GREEN + "Create Party");
        create.setItemMeta(createMeta);
        gui.setItem(10, create);

        ItemStack invite = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta inviteMeta = invite.getItemMeta();
        inviteMeta.setDisplayName(ChatColor.YELLOW + "Invite Player");
        invite.setItemMeta(inviteMeta);
        gui.setItem(12, invite);

        ItemStack duel = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta duelMeta = duel.getItemMeta();
        duelMeta.setDisplayName(ChatColor.RED + "Party Duel");
        duel.setItemMeta(duelMeta);
        gui.setItem(14, duel);

        ItemStack tournament = new ItemStack(Material.GOLDEN_APPLE);
        ItemMeta tournMeta = tournament.getItemMeta();
        tournMeta.setDisplayName(ChatColor.GOLD + "Start Tournament");
        tournament.setItemMeta(tournMeta);
        gui.setItem(16, tournament);

        ItemStack disband = new ItemStack(Material.RED_WOOL);
        ItemMeta disbandMeta = disband.getItemMeta();
        disbandMeta.setDisplayName(ChatColor.RED + "Disband Party");
        disband.setItemMeta(disbandMeta);
        gui.setItem(22, disband);

        if (party != null) {
            int slot = 27;
            for (Player member : party.members) {
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                meta.setOwningPlayer(member);
                meta.setDisplayName(ChatColor.GREEN + member.getName());
                meta.setLore(Arrays.asList(ChatColor.GRAY + "Elo: " + plugin.eloScores.getOrDefault(member, 1000)));
                head.setItemMeta(meta);
                gui.setItem(slot++, head);
            }
        }
        fillEmptySlots(gui, Material.CYAN_STAINED_GLASS_PANE);
        player.openInventory(gui);
        guiStates.put(player, new GuiState(null, null, 1, null, false, false, new ArrayList<>(), GuiType.PARTY, 0));
    }

    public void openSpectatorGui(Player player) {
        if (!plugin.getConfig().getBoolean("duelforge.spectator.enabled")) {
            player.sendMessage(ChatColor.RED + "Spectator mode is disabled!");
            return;
        }
        Inventory gui = Bukkit.createInventory(player, KIT_GUI_SIZE, ChatColor.DARK_GRAY + "Spectate Duels");
        int slot = 0;
        for (Player p : plugin.getActiveDuels().keySet()) {
            DuelForge.Duel duel = plugin.getActiveDuels().get(p);
            Player opponent = duel.p1 == p ? duel.p2 : duel.p1;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(p);
            meta.setDisplayName(ChatColor.YELLOW + p.getName() + " vs " + opponent.getName());
            meta.setLore(Arrays.asList(ChatColor.GRAY + "Score: " + duel.score1 + " - " + duel.score2, ChatColor.BLUE + "Round: " + duel.currentRound));
            head.setItemMeta(meta);
            gui.setItem(slot++, head);
        }
        addNavigation(gui, 0, plugin.getActiveDuels().size() / 2);
        player.openInventory(gui);
        guiStates.put(player, new GuiState(null, null, 1, null, false, false, new ArrayList<>(), GuiType.SPECTATOR, 0));
    }

    public void openStatsGui(Player player) {
        if (!plugin.getConfig().getBoolean("duelforge.scoring.stats")) {
            player.sendMessage(ChatColor.RED + "Stats are disabled!");
            return;
        }
        Inventory gui = Bukkit.createInventory(player, 27, ChatColor.DARK_GREEN + "Your Stats");
        DuelForge.PlayerStats stats = plugin.getStats().getOrDefault(player, new DuelForge.PlayerStats());
        gui.setItem(10, createStatItem(Material.DIAMOND_SWORD, "Wins", stats.wins));
        gui.setItem(11, createStatItem(Material.IRON_SWORD, "Losses", stats.losses));
        gui.setItem(12, createStatItem(Material.BOW, "Kills", stats.kills));
        gui.setItem(13, createStatItem(Material.SKELETON_SKULL, "Deaths", stats.deaths));
        gui.setItem(14, createStatItem(Material.GOLDEN_SWORD, "K/D Ratio", stats.deaths == 0 ? stats.kills : stats.kills / stats.deaths));
        gui.setItem(15, createStatItem(Material.BLAZE_ROD, "Win Streak", stats.winStreak));
        gui.setItem(16, createStatItem(Material.BOOK, "Favorite Kit", stats.favoriteKit));
        fillEmptySlots(gui, Material.GREEN_STAINED_GLASS_PANE);
        player.openInventory(gui);
        guiStates.put(player, new GuiState(null, null, 1, null, false, false, new ArrayList<>(), GuiType.STATS, 0));
    }

    public void openLeaderboardGui(Player player) {
        if (!plugin.getConfig().getBoolean("duelforge.scoring.leaderboards")) {
            player.sendMessage(ChatColor.RED + "Leaderboards are disabled!");
            return;
        }
        Inventory gui = Bukkit.createInventory(player, KIT_GUI_SIZE, ChatColor.GOLD + "Leaderboards");
        List<Map.Entry<Player, Integer>> eloSorted = new ArrayList<>(plugin.eloScores.entrySet());
        eloSorted.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));
        int slot = 0;
        for (int i = 0; i < Math.min(10, eloSorted.size()); i++) {
            Player p = eloSorted.get(i).getKey();
            int elo = eloSorted.get(i).getValue();
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(p);
            meta.setDisplayName(ChatColor.YELLOW + "#" + (i + 1) + " " + p.getName());
            meta.setLore(Arrays.asList(ChatColor.RED + "Elo: " + elo, ChatColor.GRAY + "Wins: " + plugin.getStats().getOrDefault(p, new DuelForge.PlayerStats()).wins));
            head.setItemMeta(meta);
            gui.setItem(slot++, head);
        }
        addNavigation(gui, 0, Math.min(10, eloSorted.size()));
        player.openInventory(gui);
        guiStates.put(player, new GuiState(null, null, 1, null, false, false, new ArrayList<>(), GuiType.LEADERBOARD, 0));
    }

    public void openReplayGui(Player player, String replayId) {
        if (!plugin.getConfig().getBoolean("duelforge.replays.enabled")) {
            player.sendMessage(ChatColor.RED + "Replays are disabled!");
            return;
        }
        Inventory gui = Bukkit.createInventory(player, 27, ChatColor.DARK_BLUE + "Replay: " + replayId);
        ItemStack play = new ItemStack(Material.LIME_DYE);
        ItemMeta playMeta = play.getItemMeta();
        playMeta.setDisplayName(ChatColor.GREEN + "Play");
        play.setItemMeta(playMeta);
        gui.setItem(11, play);

        ItemStack pause = new ItemStack(Material.ROSE_RED);
        ItemMeta pauseMeta = pause.getItemMeta();
        pauseMeta.setDisplayName(ChatColor.RED + "Pause");
        pause.setItemMeta(pauseMeta);
        gui.setItem(13, pause);

        ItemStack skip = new ItemStack(Material.ARROW);
        ItemMeta skipMeta = skip.getItemMeta();
        skipMeta.setDisplayName(ChatColor.YELLOW + "Skip 10s");
        skip.setItemMeta(skipMeta);
        gui.setItem(15, skip);

        fillEmptySlots(gui, Material.BLUE_STAINED_GLASS_PANE);
        player.openInventory(gui);
        guiStates.put(player, new GuiState(null, null, 1, null, false, false, new ArrayList<>(), GuiType.REPLAY, 0));
    }

    public void openCosmeticsGui(Player player) {
        if (!plugin.getConfig().getBoolean("duelforge.cosmetics.enabled")) {
            player.sendMessage(ChatColor.RED + "Cosmetics are disabled!");
            return;
        }
        Inventory gui = Bukkit.createInventory(player, 27, ChatColor.LIGHT_PURPLE + "Cosmetics");
        Set<String> owned = plugin.getCosmetics().getOrDefault(player, new HashSet<>());
        gui.setItem(10, createCosmeticItem(Material.FIREWORK_ROCKET, "FlameTrail", owned.contains("FlameTrail"), "1500 Elo"));
        gui.setItem(12, createCosmeticItem(Material.FEATHER, "VictoryDance", owned.contains("VictoryDance"), "10 Wins"));
        gui.setItem(14, createCosmeticItem(Material.NAME_TAG, "LegendTag", owned.contains("LegendTag"), "2000 Elo"));
        fillEmptySlots(gui, Material.PURPLE_STAINED_GLASS_PANE);
        player.openInventory(gui);
        guiStates.put(player, new GuiState(null, null, 1, null, false, false, new ArrayList<>(), GuiType.COSMETICS, 0));
    }

    private ItemStack createStatItem(Material material, String name, Object value) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + name + ": " + ChatColor.GREEN + value.toString());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCosmeticItem(Material material, String name, boolean owned, String unlock) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((owned ? ChatColor.GREEN : ChatColor.RED) + name);
        meta.setLore(Arrays.asList(ChatColor.GRAY + "Unlock: " + unlock, owned ? ChatColor.GREEN + "Owned" : ChatColor.RED + "Locked"));
        item.setItemMeta(meta);
        return item;
    }

    private void fillEmptySlots(Inventory gui, Material material) {
        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, new ItemStack(material));
            }
        }
    }

    private void addNavigation(Inventory gui, int page, int totalItems) {
        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prev.getItemMeta();
            prevMeta.setDisplayName(ChatColor.YELLOW + "Previous Page");
            prev.setItemMeta(prevMeta);
            gui.setItem(gui.getSize() - 9, prev);
        }
        if ((page + 1) * 45 < totalItems) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = next.getItemMeta();
            nextMeta.setDisplayName(ChatColor.YELLOW + "Next Page");
            next.setItemMeta(nextMeta);
            gui.setItem(gui.getSize() - 1, next);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        GuiState state = guiStates.get(player);
        if (state == null) return;
        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        switch (state.type) {
            case KIT:
                if (name.equals("Previous Page") || name.equals("Next Page")) {
                    int newPage = name.equals("Previous Page") ? state.page - 1 : state.page + 1;
                    guiStates.put(player, new GuiState(state.target, null, 1, null, state.ranked, state.queue, state.modifiers, GuiType.KIT, newPage));
                    openKitGui(player, state.target, state.ranked, state.queue);
                } else if (e.isRightClick()) {
                    openKitPreview(player, plugin.kits.get(name));
                } else {
                    openRoundsGui(player, state.target, plugin.kits.get(name), state.ranked);
                }
                break;
            case ROUNDS:
                int rounds = Integer.parseInt(name.split(" ")[0]);
                openModifiersGui(player, state.target, state.kit, rounds, state.arena, state.ranked);
                break;
            case MODIFIERS:
                if (name.equals("Confirm")) {
                    openArenaGui(player, state.target, state.kit, state.rounds, state.ranked, state.modifiers);
                } else {
                    if (e.isLeftClick()) state.modifiers.add(name);
                    else state.modifiers.remove(name);
                    openModifiersGui(player, state.target, state.kit, state.rounds, state.arena, state.ranked);
                }
                break;
            case ARENA:
                if (name.equals("Previous Page") || name.equals("Next Page")) {
                    int newPage = name.equals("Previous Page") ? state.page - 1 : state.page + 1;
                    guiStates.put(player, new GuiState(state.target, state.kit, state.rounds, null, state.ranked, state.queue, state.modifiers, GuiType.ARENA, newPage));
                    openArenaGui(player, state.target, state.kit, state.rounds, state.ranked, state.modifiers);
                } else if (e.isRightClick()) {
                    openArenaPreview(player, plugin.arenas.get(name));
                } else {
                    sendDuelRequest(player, state.target, state.kit, state.rounds, plugin.arenas.get(name), state.ranked, state.modifiers);
                    player.closeInventory();
                }
                break;
            case PARTY:
                handlePartyAction(player, name);
                break;
            case SPECTATOR:
                if (name.contains(" vs ")) {
                    String[] parts = name.split(" vs ");
                    Player target = Bukkit.getPlayer(parts[0]);
                    if (target != null) plugin.spectateDuel(player, target);
                }
                break;
            case REPLAY:
                handleReplayAction(player, name);
                break;
            case COSMETICS:
                // Cosmetic selection handled in DuelForge via applyCosmetics
                break;
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        guiStates.remove(e.getPlayer());
    }

    private void sendDuelRequest(Player initiator, Player target, DuelForge.Kit kit, int rounds, DuelForge.Arena arena, boolean ranked, List<String> modifiers) {
        plugin.getDuelRequests().put(target.getName(), new DuelForge.DuelRequest(initiator, target, kit, rounds, arena, ranked, modifiers));
        initiator.sendMessage(ChatColor.GREEN + "Duel request sent!");
        target.sendMessage(ChatColor.YELLOW + initiator.getName() + " has challenged you! /accept to join.");
    }

    private void handlePartyAction(Player player, String action) {
        QueueAndPartyManager qpm = new QueueAndPartyManager(plugin, this);
        switch (action) {
            case "Create Party": qpm.handlePartyCommand(player, new String[]{"create"}); break;
            case "Invite Player": openPartyInviteGui(player); break;
            case "Party Duel": qpm.requestChatInput(player, "party_duel"); break;
            case "Start Tournament": qpm.startPartyTournament(plugin.parties.get(player)); break;
            case "Disband Party": qpm.handlePartyCommand(player, new String[]{"split"}); break;
        }
        player.closeInventory();
    }

    private void handleReplayAction(Player player, String action) {
        switch (action) {
            case "Play": player.sendMessage(ChatColor.GREEN + "Playing replay..."); break;
            case "Pause": player.sendMessage(ChatColor.RED + "Paused replay."); break;
            case "Skip 10s": player.sendMessage(ChatColor.YELLOW + "Skipped 10s."); break;
        }
    }

    private void openKitPreview(Player player, DuelForge.Kit kit) {
        Inventory gui = Bukkit.createInventory(player, 36, ChatColor.GREEN + "Preview: " + kit.name);
        for (int i = 0; i < Math.min(kit.items.length, 36); i++) {
            if (kit.items[i] != null) gui.setItem(i, kit.items[i].clone());
        }
        gui.setItem(31, createBackButton());
        fillEmptySlots(gui, Material.LIME_STAINED_GLASS_PANE);
        player.openInventory(gui);
    }

    private void openArenaPreview(Player player, DuelForge.Arena arena) {
        Inventory gui = Bukkit.createInventory(player, 36, ChatColor.RED + "Preview: " + arena.name);
        gui.setItem(13, arena.icon.clone());
        gui.setItem(15, createStatItem(Material.CLOCK, "Time", arena.time));
        gui.setItem(16, createStatItem(Material.CLOCK, "Weather", arena.weather));
        gui.setItem(31, createBackButton());
        fillEmptySlots(gui, Material.RED_STAINED_GLASS_PANE);
        player.openInventory(gui);
    }

    private void openPartyInviteGui(Player player) {
        Inventory gui = Bukkit.createInventory(player, KIT_GUI_SIZE, ChatColor.YELLOW + "Invite Players");
        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!plugin.parties.containsKey(online) && online != player) {
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                meta.setOwningPlayer(online);
                meta.setDisplayName(ChatColor.GREEN + online.getName());
                head.setItemMeta(meta);
                gui.setItem(slot++, head);
            }
        }
        addNavigation(gui, 0, Bukkit.getOnlinePlayers().size());
        player.openInventory(gui);
    }

    private ItemStack createBackButton() {
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta meta = back.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "Back");
        back.setItemMeta(meta);
        return back;
    }

    private class GuiState {
        Player target;
        DuelForge.Kit kit;
        int rounds;
        DuelForge.Arena arena;
        boolean ranked, queue;
        List<String> modifiers;
        GuiType type;
        int page;

        GuiState(Player target, DuelForge.Kit kit, int rounds, DuelForge.Arena arena, boolean ranked, boolean queue, List<String> modifiers, GuiType type, int page) {
            this.target = target;
            this.kit = kit;
            this.rounds = rounds;
            this.arena = arena;
            this.ranked = ranked;
            this.queue = queue;
            this.modifiers = modifiers;
            this.type = type;
            this.page = page;
        }
    }

    private enum GuiType {
        KIT, ROUNDS, ARENA, MODIFIERS, PARTY, SPECTATOR, STATS, LEADERBOARD, REPLAY, COSMETICS
    }
}