package de.minebench.simpleteamspeakbot;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

public class SimpleTeamspeakBotCommand implements CommandExecutor {
    private final SimpleTeamspeakBot plugin;
    
    private static final String TEAM_PREFIX = "team:";
    
    public SimpleTeamspeakBotCommand(SimpleTeamspeakBot plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0) {
            if ("reload".equalsIgnoreCase(args[0]) && sender.hasPermission("simpleteamspeakbot.command.reload")) {
                plugin.loadConfig();
                sender.sendMessage(ChatColor.YELLOW + "Config reloaded!");
                return true;
            } else if ("join".equalsIgnoreCase(args[0]) && sender.hasPermission("simpleteamspeakbot.command.join")) {
                if (args.length > 1) {
                    String channel;
                    Player target;
                    if (args.length > 2) {
                        if (!sender.hasPermission("simpleteamspeakbot.command.join.others")) {
                            sender.sendMessage(ChatColor.RED + "You don't have the permission simpleteamspeakbot.command.join.others!");
                            return true;
                        }
                        if (args[1].toLowerCase().startsWith(TEAM_PREFIX) && sender.hasPermission("simpleteamspeakbot.command.join.team")) {
                            Team team = plugin.getServer().getScoreboardManager().getMainScoreboard().getTeam(args[1].substring(TEAM_PREFIX.length()));
                            if (team == null) {
                                sender.sendMessage(ChatColor.RED + "No team with the name " + args[1].substring(TEAM_PREFIX.length()) + " is registered!");
                                return true;
                            }
                            
                            plugin.runAsync(() -> plugin.joinChannel(sender, team.getEntries(), args[2]));
                            return true;
                        }
                        target = plugin.getServer().getPlayer(args[1]);
                        if (target == null) {
                            sender.sendMessage(ChatColor.RED + "No player with the name " + args[1] + " was found online!");
                            return true;
                        }
                        channel = args[2];
                    } else if (sender instanceof Player) {
                        target = (Player) sender;
                        channel = args[1];
                    } else {
                        sender.sendMessage(ChatColor.RED + "You have to specify a player from the console! /" + label + " join <player> <channel>");
                        return true;
                    }
                    plugin.runAsync(() -> plugin.joinChannel(sender, target, channel));
                    return true;
                }
            } else if ("team".equalsIgnoreCase(args[0]) && sender.hasPermission("simpleteamspeakbot.command.team")) {
                if (args.length > 1) {
                    if (args[1].toLowerCase().startsWith(TEAM_PREFIX) && sender.hasPermission("simpleteamspeakbot.command.team.team")) {
                        Team team = plugin.getServer().getScoreboardManager().getMainScoreboard().getTeam(args[1].substring(TEAM_PREFIX.length()));
                        if (team == null) {
                            sender.sendMessage(ChatColor.RED + "No team with the name " + args[1].substring(TEAM_PREFIX.length()) + " is registered!");
                            return true;
                        }
                        
                        Integer teamChannel = plugin.getTeamChannel(team);
                        if (teamChannel == null) {
                            sender.sendMessage(ChatColor.RED + "No channel for team " + team.getName() + " configured!");
                            return true;
                        }
                        
                        plugin.runAsync(() -> plugin.joinChannel(sender, team.getEntries(), teamChannel));
                        return true;
                    } else if (sender.hasPermission("simpleteamspeakbot.command.team.others")) {
                        Player target = plugin.getServer().getPlayer(args[1]);
                        if (target == null) {
                            sender.sendMessage(ChatColor.RED + "No player with the name " + args[1] + " was found online!");
                            return true;
                        }
                        
                        Team team = plugin.getServer().getScoreboardManager().getMainScoreboard().getEntryTeam(target.getName());
                        if (team == null) {
                            sender.sendMessage(ChatColor.RED + target.getName() + " is not in any team!");
                            return true;
                        }
                        
                        Integer teamChannel = plugin.getTeamChannel(team);
                        if (teamChannel == null) {
                            sender.sendMessage(ChatColor.RED + "No channel for team " + team.getName() + " configured!");
                            return true;
                        }
                        
                        plugin.runAsync(() -> plugin.joinChannel(sender, target, teamChannel));
                        return true;
                    } else {
                        sender.sendMessage(ChatColor.RED + "You don't have the permission to join specific players/teams!");
                        return true;
                    }
                }
                
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "You have to specify a player/team from the console! /" + label + " team team:<team>|<player>");
                    return true;
                }
                
                Team team = plugin.getServer().getScoreboardManager().getMainScoreboard().getEntryTeam(sender.getName());
                if (team == null) {
                    sender.sendMessage(ChatColor.RED + "You are not in any team!");
                    return true;
                }
                
                Integer teamChannel = plugin.getTeamChannel(team);
                if (teamChannel == null) {
                    sender.sendMessage(ChatColor.RED + "No channel for team " + team.getName() + " configured!");
                    return true;
                }
    
                plugin.runAsync(() -> plugin.joinChannel(sender, (Player) sender, teamChannel));
                return true;
            }
        }
        return false;
    }
}
