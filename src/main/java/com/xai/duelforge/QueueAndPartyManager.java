package com.xai.duelforge;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material; // Added
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class QueueAndPartyManager implements Listener {

    private final DuelForge plugin;
    private final GuiManager guiManager;
    private Map<Player, QueueEntry> rankedQueue = new HashMap<>();
    private Map<Player, QueueEntry> unrankedQueue = new HashMap<>();
    private Map<Player, PartyInvite> pendingInvites = new HashMap<>();
    private Map<Player, String> chatInput = new HashMap<>();
    private List<Tournament> activeTournaments = new ArrayList<>();

    public QueueAndPartyManager(DuelForge plugin, GuiManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startQueueChecker();
        startTournamentChecker();
    }

    public void addToQueue(Player player, DuelForge.Kit kit, boolean ranked) {
        if (!plugin.getConfig().getBoolean("duelforge.queues.enabled")) {
            player.sendMessage(ChatColor.RED + "Queues are disabled!");
            return;
        }
        if (plugin.getActiveDuels().containsKey(player)) {
            player.sendMessage(ChatColor.RED + "You’re already in a duel!");
            return;
        }
        Map<Player, QueueEntry> queue = ranked ? rankedQueue : unrankedQueue;
        queue.put(player, new QueueEntry(player, kit, ranked));
        player.sendMessage(ChatColor.GREEN + "Joined " + (ranked ? "ranked" : "unranked") + " queue with " + kit.name + "!");
    }

    private void startQueueChecker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                matchPlayers(rankedQueue, true);
                matchPlayers(unrankedQueue, false);
            }
        }.runTaskTimer(plugin, 0, 20);
    }

    private void matchPlayers(Map<Player, QueueEntry> queue, boolean ranked) {
        if (queue.size() < 2) return;
        List<QueueEntry> entries = new ArrayList<>(queue.values());
        entries.sort(Comparator.comparingLong(e -> e.joinTime));
        for (int i = 0; i < entries.size() - 1; i += 2) {
            QueueEntry e1 = entries.get(i);
            QueueEntry e2 = entries.get(i + 1);
            if (e1.player.isOnline() && e2.player.isOnline()) {
                queue.remove(e1.player);
                queue.remove(e2.player);
                DuelForge.Kit kit = e1.kit != null ? e1.kit : plugin.kits.values().iterator().next();
                DuelForge.Arena arena = getRandomArena();
                plugin.startDuel(e1.player, e2.player, kit, 1, arena, ranked, new ArrayList<>());
                broadcastQueueMatch(e1.player, e2.player, ranked);
            }
        }
    }

    private void broadcastQueueMatch(Player p1, Player p2, boolean ranked) {
        String mode = ranked ? "Ranked" : "Unranked";
        Bukkit.broadcastMessage(ChatColor.GOLD + "[DuelForge] " + mode + " Queue Match: " + p1.getName() + " vs " + p2.getName() + "!");
    }

    public void removeFromQueue(Player player) {
        rankedQueue.remove(player);
        unrankedQueue.remove(player);
    }

    public void handlePartyCommand(Player player, String[] args) {
        DuelForge.Party party = plugin.parties.get(player);
        if (args.length == 0) {
            guiManager.openPartyGui(player);
            return;
        }
        String action = args[0].toLowerCase();

        switch (action) {
            case "create":
                if (party == null) {
                    plugin.parties.put(player, new DuelForge.Party(player));
                    player.sendMessage(ChatColor.GREEN + "Party created!");
                } else {
                    player.sendMessage(ChatColor.RED + "You’re already in a party!");
                }
                break;
            case "invite":
                if (party != null && party.leader == player && args.length == 2) {
                    Player target = Bukkit.getPlayer(args[1]);
                    if (target != null && target != player && !plugin.parties.containsKey(target)) {
                        sendPartyInvite(player, target);
                    } else {
                        player.sendMessage(ChatColor.RED + "Invalid player!");
                    }
                }
                break;
            case "accept":
                PartyInvite invite = pendingInvites.remove(player);
                if (invite != null) {
                    DuelForge.Party targetParty = plugin.parties.get(invite.leader);
                    if (targetParty != null) {
                        targetParty.members.add(player);
                        plugin.parties.put(player, targetParty);
                        targetParty.updatePartyElo();
                        targetParty.broadcast(ChatColor.GREEN + player.getName() + " joined the party! Party Elo: " + targetParty.partyElo);
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "No pending invite!");
                }
                break;
            case "duel":
                if (party != null && party.leader == player && args.length == 2) {
                    Player otherLeader = Bukkit.getPlayer(args[1]);
                    DuelForge.Party otherParty = plugin.parties.get(otherLeader);
                    if (otherParty != null && otherLeader == otherParty.leader) {
                        startPartyDuel(party, otherParty);
                    } else {
                        player.sendMessage(ChatColor.RED + "Invalid party leader!");
                    }
                }
                break;
            case "kick":
                if (party != null && party.leader == player && args.length == 2) {
                    Player toKick = Bukkit.getPlayer(args[1]);
                    if (toKick != null && party.members.contains(toKick) && toKick != player) {
                        party.members.remove(toKick);
                        plugin.parties.remove(toKick);
                        party.updatePartyElo();
                        party.broadcast(ChatColor.RED + toKick.getName() + " was kicked! Party Elo: " + party.partyElo);
                    }
                }
                break;
            case "split":
                if (party != null && party.leader == player) {
                    plugin.parties.remove(player);
                    party.broadcast(ChatColor.RED + "Party disbanded!");
                    party.members.forEach(p -> plugin.parties.remove(p));
                }
                break;
            case "tournament":
                if (party != null && party.leader == player) {
                    startPartyTournament(party);
                }
                break;
        }
    }

    private void sendPartyInvite(Player leader, Player target) {
        pendingInvites.put(target, new PartyInvite(leader));
        target.sendMessage(ChatColor.GREEN + leader.getName() + " invited you! /party accept to join.");
        leader.sendMessage(ChatColor.YELLOW + "Invite sent to " + target.getName() + "!");
        new BukkitRunnable() {
            @Override
            public void run() {
                if (pendingInvites.remove(target) != null) {
                    target.sendMessage(ChatColor.RED + "Invite from " + leader.getName() + " expired!");
                    leader.sendMessage(ChatColor.RED + "Invite to " + target.getName() + " expired!");
                }
            }
        }.runTaskLater(plugin, 600);
    }

    private void startPartyDuel(DuelForge.Party party1, DuelForge.Party party2) {
        if (party1.members.size() != party2.members.size()) {
            party1.broadcast(ChatColor.RED + "Party sizes must match!");
            party2.broadcast(ChatColor.RED + "Party sizes must match!");
            return;
        }
        DuelForge.Arena arena = getRandomArena();
        DuelForge.Kit kit = plugin.kits.values().iterator().next();
        int rounds = 3;
        List<Player> p1Members = new ArrayList<>(party1.members);
        List<Player> p2Members = new ArrayList<>(party2.members);
        for (int i = 0; i < p1Members.size(); i++) {
            Player p1 = p1Members.get(i);
            Player p2 = p2Members.get(i);
            if (p1.isOnline() && p2.isOnline()) {
                plugin.startDuel(p1, p2, kit, rounds, arena, true, new ArrayList<>());
            }
        }
        updatePartyEloAfterDuel(party1, party2);
        Bukkit.broadcastMessage(ChatColor.GOLD + "[DuelForge] Party Duel: " + party1.leader.getName() + " (" + party1.partyElo + ") vs " + party2.leader.getName() + " (" + party2.partyElo + ")!");
    }

    private void updatePartyEloAfterDuel(DuelForge.Party winner, DuelForge.Party loser) {
        int winnerElo = winner.partyElo;
        int loserElo = loser.partyElo;
        int eloChange = calculateEloChange(winnerElo, loserElo);
        winner.partyElo += eloChange;
        loser.partyElo -= eloChange;
        winner.broadcast(ChatColor.GREEN + "Party Victory! +" + eloChange + " Elo (" + winner.partyElo + ")");
        loser.broadcast(ChatColor.RED + "Party Defeat! -" + eloChange + " Elo (" + loser.partyElo + ")");
    }

    private int calculateEloChange(int winnerElo, int loserElo) {
        double expected = 1.0 / (1 + Math.pow(10, (loserElo - winnerElo) / 400.0));
        return (int) (32 * (1 - expected));
    }

    public void startPartyTournament(DuelForge.Party party) {
        if (!plugin.getConfig().getBoolean("duelforge.parties.tournaments")) {
            party.broadcast(ChatColor.RED + "Tournaments are disabled!");
            return;
        }
        if (party.members.size() < 4) {
            party.broadcast(ChatColor.RED + "Need at least 4 members for a tournament!");
            return;
        }
        Tournament tournament = new Tournament(party);
        activeTournaments.add(tournament);
        tournament.startRound();
        party.broadcast(ChatColor.GOLD + "Party Tournament Started! Participants: " + party.members.size());
    }

    private void startTournamentChecker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                Iterator<Tournament> it = activeTournaments.iterator();
                while (it.hasNext()) {
                    Tournament t = it.next();
                    if (t.isComplete()) {
                        awardTournamentRewards(t);
                        it.remove();
                    } else {
                        t.checkRound();
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 20);
    }

    private void awardTournamentRewards(Tournament tournament) {
        DuelForge.Party party = tournament.party;
        Player winner = tournament.currentRound.get(0);
        party.broadcast(ChatColor.GOLD + winner.getName() + " wins the tournament!");
        winner.getInventory().addItem(new ItemStack(Material.DIAMOND, 5));
        plugin.eloScores.put(winner, plugin.eloScores.get(winner) + 50);
        winner.sendMessage(ChatColor.GREEN + "+50 Elo for tournament win!");
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        if (!chatInput.containsKey(player)) return;
        e.setCancelled(true);
        String input = e.getMessage();
        String context = chatInput.remove(player);

        if (context.equals("party_invite")) {
            Player target = Bukkit.getPlayer(input);
            if (target != null && target != player && !plugin.parties.containsKey(target)) {
                sendPartyInvite(player, target);
            }
        } else if (context.equals("party_duel")) {
            Player otherLeader = Bukkit.getPlayer(input);
            DuelForge.Party party = plugin.parties.get(player);
            DuelForge.Party otherParty = plugin.parties.get(otherLeader);
            if (party != null && party.leader == player && otherParty != null && otherLeader == otherParty.leader) {
                startPartyDuel(party, otherParty);
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> guiManager.openPartyGui(player));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        removeFromQueue(player);
        pendingInvites.remove(player);
        chatInput.remove(player);
        DuelForge.Party party = plugin.parties.get(player);
        if (party != null && party.leader == player) {
            plugin.parties.remove(player);
            party.broadcast(ChatColor.RED + "Party disbanded due to leader logout!");
            party.members.forEach(p -> plugin.parties.remove(p));
        } else if (party != null) {
            party.members.remove(player);
            plugin.parties.remove(player);
            party.updatePartyElo();
            party.broadcast(ChatColor.YELLOW + player.getName() + " left the party! Party Elo: " + party.partyElo);
        }
    }

    private DuelForge.Arena getRandomArena() {
        List<DuelForge.Arena> available = new ArrayList<>(plugin.arenas.values());
        return available.get(new Random().nextInt(available.size()));
    }

    public void requestChatInput(Player player, String context) {
        chatInput.put(player, context);
        player.sendMessage(ChatColor.YELLOW + "Type your input in chat!");
    }

    public void handlePartyVsRandom(Player leader) {
        DuelForge.Party party = plugin.parties.get(leader);
        if (party == null || party.leader != leader) {
            leader.sendMessage(ChatColor.RED + "You must be a party leader!");
            return;
        }
        List<Player> opponents = new ArrayList<>();
        List<Player> queue = new ArrayList<>(unrankedQueue.keySet());
        for (int i = 0; i < party.members.size() && i < queue.size(); i++) {
            opponents.add(queue.get(i));
            unrankedQueue.remove(queue.get(i));
        }
        if (opponents.size() != party.members.size()) {
            party.broadcast(ChatColor.RED + "Not enough players in queue!");
            opponents.forEach(p -> addToQueue(p, plugin.kits.values().iterator().next(), false));
            return;
        }
        DuelForge.Party tempParty = new DuelForge.Party(opponents.get(0), opponents);
        startPartyDuel(party, tempParty);
    }

    private class QueueEntry {
        Player player;
        DuelForge.Kit kit; // Fully qualified
        boolean ranked;
        long joinTime;

        QueueEntry(Player player, DuelForge.Kit kit, boolean ranked) {
            this.player = player;
            this.kit = kit;
            this.ranked = ranked;
            this.joinTime = System.currentTimeMillis();
        }
    }

    private class PartyInvite {
        Player leader;
        long timestamp;

        PartyInvite(Player leader) {
            this.leader = leader;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private class Tournament {
        DuelForge.Party party; // Fully qualified
        List<Player> currentRound;
        int roundNumber;

        Tournament(DuelForge.Party party) {
            this.party = party;
            this.currentRound = new ArrayList<>(party.members);
            this.roundNumber = 1;
        }

        void startRound() {
            Collections.shuffle(currentRound);
            for (int i = 0; i < currentRound.size() - 1; i += 2) {
                Player p1 = currentRound.get(i);
                Player p2 = currentRound.get(i + 1);
                if (p1.isOnline() && p2.isOnline()) {
                    DuelForge.Kit kit = plugin.kits.values().iterator().next();
                    DuelForge.Arena arena = getRandomArena();
                    plugin.startDuel(p1, p2, kit, 1, arena, false, new ArrayList<>());
                }
            }
            party.broadcast(ChatColor.YELLOW + "Tournament Round " + roundNumber + " started!");
        }

        void checkRound() {
            List<Player> winners = new ArrayList<>();
            for (int i = 0; i < currentRound.size() - 1; i += 2) {
                Player p1 = currentRound.get(i);
                Player p2 = currentRound.get(i + 1);
                if (!plugin.getActiveDuels().containsKey(p1) && !plugin.getActiveDuels().containsKey(p2)) {
                    DuelForge.PlayerStats p1Stats = plugin.getStats().getOrDefault(p1, new DuelForge.PlayerStats());
                    DuelForge.PlayerStats p2Stats = plugin.getStats().getOrDefault(p2, new DuelForge.PlayerStats());
                    winners.add(p1Stats.wins > p2Stats.wins ? p1 : p2);
                }
            }
            if (winners.size() == currentRound.size() / 2) {
                currentRound = winners;
                roundNumber++;
                if (currentRound.size() > 1) {
                    startRound();
                }
            }
        }

        boolean isComplete() {
            return currentRound.size() == 1;
        }
    }

    void updatePartyElo(DuelForge.Party party) {
        if (plugin.getConfig().getBoolean("duelforge.parties.party-elo")) {
            party.partyElo = party.members.stream().mapToInt(p -> plugin.eloScores.getOrDefault(p, 1000)).sum() / party.members.size();
        }
    }
}