package de.minebench.simpleteamspeakbot;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.TS3Config;
import com.github.theholywaffle.teamspeak3.TS3Query;
import com.github.theholywaffle.teamspeak3.api.wrapper.ChannelBase;
import com.github.theholywaffle.teamspeak3.api.wrapper.Client;
import com.github.theholywaffle.teamspeak3.commands.CClientMove;
import com.github.theholywaffle.teamspeak3.commands.Command;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class SimpleTeamspeakBot extends JavaPlugin {
    
    private TS3Query ts3Query;
    private TS3Api ts3api;
    private Map<String, Integer> teamChannels = new HashMap<>();
    private Cache<UUID, Integer> clientCache = CacheBuilder.newBuilder().maximumSize(1000).expireAfterAccess(10, TimeUnit.MINUTES).build();
    private Method doCommand = null;
    
    @Override
    public void onEnable() {
        loadConfig();
        getCommand("simpleteamspeakbot").setExecutor(new SimpleTeamspeakBotCommand(this));
    }
    
    public void loadConfig() {
        saveDefaultConfig();
        reloadConfig();
    
        ConfigurationSection teamSection = getConfig().getConfigurationSection("teams");
        if (teamSection != null) {
            for (String team : teamSection.getKeys(false)) {
                teamChannels.put(team.toLowerCase(), teamSection.getInt(team));
                if (getServer().getScoreboardManager().getMainScoreboard().getTeam(team) == null) {
                    getLogger().log(Level.WARNING, "No team with the name " + team + " registered!");
                }
            }
        }
        
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            if (ts3Query != null && ts3Query.isConnected()) {
                ts3Query.exit();
            }
            ts3Query = new TS3Query(new TS3Config()
                    .setHost(getConfig().getString("ts.host"))
                    .setQueryPort(getConfig().getInt("ts.port")));
    
            try {
                doCommand = ts3Query.getClass().getDeclaredMethod("doCommand", Command.class);
                doCommand.setAccessible(true);
            } catch (NoSuchMethodException | SecurityException e) {
                e.printStackTrace();
            }
    
            ts3Query.connect();
            
            ts3api = ts3Query.getApi();
            ts3api.login(getConfig().getString("ts.user"), getConfig().getString("ts.pass"));
            ts3api.selectVirtualServerById(getConfig().getInt("ts.virtualserver"));
            ts3api.setNickname(getConfig().getString("ts.nickname"));
            
            for (Map.Entry<String, Integer> entry : teamChannels.entrySet()) {
                if (ts3api.getChannelInfo(entry.getValue()) == null) {
                    getLogger().log(Level.WARNING, "No channel with the id " + entry.getValue() + " for team " + entry.getKey() + " found!");
                }
            }
        });
    }
    
    public Integer getTeamChannel(Team team) {
        return teamChannels.get(team.getName().toLowerCase());
    }
    
    public void joinChannel(CommandSender sender, Player target, String channel) {
        try {
            joinChannel(sender, target, Integer.parseInt(channel));
        } catch (NumberFormatException e) {
            ChannelBase channelBase = ts3api.getChannelByNameExact(channel, true);
            if (channelBase == null) {
                sender.sendMessage(ChatColor.RED + "No Channel " + channel + " found!");
                return;
            }
            joinChannel(sender, target, channelBase);
        }
    }
    
    public void joinChannel(CommandSender sender, Player target, int channelId) {
        ChannelBase channelBase = ts3api.getChannelInfo(channelId);
        if (channelBase == null) {
            sender.sendMessage(ChatColor.RED + "No Channel with the ID " + channelId + " found!");
            return;
        }
        joinChannel(sender, target, channelBase);
    }
    
    public void joinChannel(CommandSender sender, Player target, ChannelBase channel) {
        int clientId = getClientId(target);
        if (clientId == -1) {
            sender.sendMessage(ChatColor.RED + "No Teamspeak client found online for player " + target.getUniqueId());
            return;
        }
    
        final CClientMove move = new CClientMove(clientId, channel.getId(), null);
        try {
            doCommand.invoke(ts3Query, move);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    
        getLogger().log(Level.INFO, move.getRawResponse());
        if (move.getError().isSuccessful()) {
            sender.sendMessage(ChatColor.GREEN + "Moved " + target.getName() + " to channel " + channel.getName());
        } else {
            sender.sendMessage(ChatColor.RED + "Error while moving " + target.getName() + " to channel " + channel.getName()
                    + ": " + move.getError().getId() + "/" + move.getError().getMessage());
        }
    }
    
    public void joinChannel(CommandSender sender, Set<String> targets, String channel) {
        try {
            joinChannel(sender, targets, Integer.parseInt(channel));
        } catch (NumberFormatException e) {
            ChannelBase channelBase = ts3api.getChannelByNameExact(channel, true);
            if (channelBase == null) {
                sender.sendMessage(ChatColor.RED + "No Channel " + channel + " found!");
                return;
            }
            joinChannel(sender, targets, channelBase);
        }
    }
    
    public void joinChannel(CommandSender sender, Set<String> targets, int channelId) {
        ChannelBase channelBase = ts3api.getChannelInfo(channelId);
        if (channelBase == null) {
            sender.sendMessage(ChatColor.RED + "No Channel with the ID " + channelId + " found!");
            return;
        }
        joinChannel(sender, targets, channelBase);
    }
    
    public void joinChannel(CommandSender sender, Set<String> targets, ChannelBase channel) {
        Set<Player> players = targets.stream()
                .map(s -> getServer().getPlayer(s))
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .collect(Collectors.toSet());
        Set<Integer> clientIds = getClientIds(players);
        
        final CClientMove move = new CClientMove(clientIds.stream().mapToInt(i->i).toArray(), channel.getId(), null);
        try {
            doCommand.invoke(ts3Query, move);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    
        getLogger().log(Level.INFO, move.getRawResponse());
        if (move.getError().isSuccessful()) {
            sender.sendMessage(ChatColor.GREEN + "Moved " + clientIds.size() + " clients to channel " + channel.getName() +
                    (targets.size() > clientIds.size() ? ChatColor.GRAY + "(" + (targets.size() - clientIds.size()) + " clients were not found!)" : ""));
        } else {
            sender.sendMessage(ChatColor.RED + "Error while trying to move " + clientIds.size() + " clients to channel " + channel.getName() +
                    (targets.size() > clientIds.size() ?  ChatColor.GRAY + "(" + (targets.size() - clientIds.size()) + " clients were not found!)" : "")
                    + ": " + move.getError().getId() + "/" + move.getError().getMessage());
        }
    }
    
    public int getClientId(Player target) {
        return getClientId(target, null);
    }
    
    public int getClientId(Player target, List<Client> clients) {
        try {
            return clientCache.get(target.getUniqueId(), () -> getId(target, clients == null ? ts3api.getClients() : clients));
        } catch (ExecutionException e) {
            return -1;
        }
    }
    
    public Set<Integer> getClientIds(Set<Player> targets) {
        Set<Integer> clientIds = new HashSet<>();
        boolean getClients = false;
        
        for (Player player : targets) {
            Integer id = clientCache.getIfPresent(player.getUniqueId());
            if (id == null) {
                getClients = true;
                break;
            }
            clientIds.add(id);
        }
        if (getClients) {
            List<Client> clients = ts3api.getClients();
            for (Player player : targets) {
                int id = getClientId(player, clients);
                if (id != -1) {
                    clientIds.add(id);
                }
            }
        }
        return clientIds;
    }
    
    private Integer getId(Player target, List<Client> clients) throws Exception {
        for (Client client : clients) {
            if (client.getIp().equals(target.getAddress().getAddress().getHostAddress())) {
                return client.getId();
            }
        }
        for (Client client : clients) {
            if (client.getNickname().equalsIgnoreCase(target.getName())) {
                return client.getId();
            }
        }
        for (Client client : clients) {
            if (client.getNickname().toLowerCase().startsWith(target.getName().toLowerCase())
                    || client.getNickname().toLowerCase().endsWith(target.getName().toLowerCase()) ) {
                return client.getId();
            }
        }
        throw new ClientNotFoundException();
    }
    
    @Override
    public void onDisable() {
        if (ts3Query != null) {
            ts3Query.exit();
        }
    }
    
    public void runAsync(Runnable runnable) {
        getServer().getScheduler().runTaskAsynchronously(this, runnable);
    }
    
    private class ClientNotFoundException extends Exception {}
}
