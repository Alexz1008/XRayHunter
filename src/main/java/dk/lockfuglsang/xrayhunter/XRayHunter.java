package dk.lockfuglsang.xrayhunter;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import me.confuser.banmanager.common.api.BmAPI;


import dk.lockfuglsang.util.TimeUtil;
import dk.lockfuglsang.xrayhunter.command.MainCommand;
import dk.lockfuglsang.xrayhunter.coreprotect.Callback;
import dk.lockfuglsang.xrayhunter.coreprotect.CoreProtectHandler;
import dk.lockfuglsang.xrayhunter.model.HuntSession;
import dk.lockfuglsang.xrayhunter.model.PlayerStats;
import dk.lockfuglsang.xrayhunter.model.PlayerStatsComparator;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;

/**
 * Bukkit Plugin for hunting X-Rayers using the CoreProtect API
 */
public class XRayHunter extends JavaPlugin implements Listener {
	private static final Logger log = Logger.getLogger(XRayHunter.class.getName());

	private static CoreProtectAPI api;

	public static CoreProtectAPI getCoreProtectAPI() {
		return api;
	}

	@Override
	public void onEnable() {
		api = null;
		final CoreProtectAPI coreProtectAPI = getCoreProtect();
		if (coreProtectAPI == null) {
			log.info("No valid CoreProtect plugin was found!");
		}
		try {
			new Metrics(this, 3013);
		} catch (final Exception e) {
			log.log(Level.WARNING, "Failed to submit metrics data", e);
		}
		api = coreProtectAPI;
		getCommand("xhunt").setExecutor(new MainCommand(this));
		getServer().getPluginManager().registerEvents(this, this);
	}

	// package protected
	private CoreProtectAPI getCoreProtect() {
		final Plugin plugin = getServer().getPluginManager().getPlugin("CoreProtect");
		if (plugin instanceof CoreProtect && plugin.isEnabled()) {
			final CoreProtect coreProtect = (CoreProtect) plugin;
			final CoreProtectAPI api = coreProtect.getAPI();
			if (api != null && api.APIVersion() >= 7 && CoreProtectHandler.getAdaptor() != null) {
				return api;
			}
		}
		return null;
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		Player p = e.getPlayer();
		if (p.hasPermission("mycommand.staff")) {
			World w = null;
			ArrayList<World> resources = new ArrayList<World>();
			for (World world : Bukkit.getWorlds()) {
				if (world.getName().contains("Aurum")) {
					w = world;
					resources.add(world);
				}
			}
			if (resources.size() > 1) {
				int count = -1;
				World lowestWorld = null;
				for (World world : resources) {
					// Find the Aurum that is smallest number
					int num = Integer.parseInt(world.getName().substring(5));
					if (count == -1 || num < count) {
						count = num;
						lowestWorld = world;
					}
				}
				w = lowestWorld;
			}
			Location loc = w.getSpawnLocation();
			CoreProtectHandler.performLookup(this, p, loc, TimeUtil.millisAsSeconds(TimeUtil.millisFromString("2d")), PlayerStatsComparator.MATS, null, new LookupCallback(p, 99, loc));
		}
	}
	
	public void susCommand(CommandSender s) {
		if (s.hasPermission("mycommand.staff")) {
			World w = null;
			ArrayList<World> resources = new ArrayList<World>();
			for (World world : Bukkit.getWorlds()) {
				if (world.getName().contains("Aurum")) {
					w = world;
					resources.add(world);
				}
			}
			if (resources.size() > 1) {
				int count = -1;
				World lowestWorld = null;
				for (World world : resources) {
					// Find the Aurum that is smallest number
					int num = Integer.parseInt(world.getName().substring(5));
					if (count == -1 || num < count) {
						count = num;
						lowestWorld = world;
					}
				}
				w = lowestWorld;
			}
			Location loc = w.getSpawnLocation();
			CoreProtectHandler.performLookup(this, s, loc, TimeUtil.millisAsSeconds(TimeUtil.millisFromString("2d")), PlayerStatsComparator.MATS, null, new LookupCallback(s, 99, loc));
		}
	}

	private class LookupCallback extends Callback {
		private final CommandSender sender;
		private final int size;
		private final Location loc;

		LookupCallback(CommandSender sender, int size, Location loc) {
			this.sender = sender;
			this.size = size;
			this.loc = loc;
		}

		@Override
		public void run() {
			final List<String[]> result = getData();
			if (result == null || result.isEmpty()) {
				if (sender instanceof Player) {
					sender.sendMessage(MessageFormat.format("No suspicious activity within that time-frame in {0}!", loc.getWorld().getName()));
				} else {
					sender.sendMessage("No suspicious activity within that time-frame!");
				}
				return;
			}
			final Map<Material, Integer> blockCount = new HashMap<>();
			final Map<String, Map<Material, Integer>> playerCount = new HashMap<>();
			final Map<String, List<CoreProtectAPI.ParseResult>> dataMap = new HashMap<>();
			Collections.reverse(result); // Oldest first (so placements are detected before breaks)
			final Map<String, Boolean> userPlacedBlocks = new HashMap<>();
			for (final String[] line : result) {
				final CoreProtectAPI.ParseResult parse = XRayHunter.getCoreProtectAPI().parseResult(line);
				final Material blockType = parse.getType();
				final int actionId = parse.getActionId();
				final String blockKey = getBlockKey(parse);
				if (actionId == CoreProtectHandler.ACTION_PLACE) {
					userPlacedBlocks.put(blockKey, Boolean.TRUE);
					continue; // skip the rest for placements
				}
				if (actionId == CoreProtectHandler.ACTION_BREAK && !userPlacedBlocks.containsKey(blockKey)) {
					updateMap(blockCount, blockType);
					if (!playerCount.containsKey(parse.getPlayer())) {
						playerCount.put(parse.getPlayer(), new HashMap<>());
					}
					updateMap(playerCount.get(parse.getPlayer()), blockType);
					if (!dataMap.containsKey(parse.getPlayer())) {
						dataMap.put(parse.getPlayer(), new ArrayList<>());
					}
					dataMap.get(parse.getPlayer()).add(parse);
				}
			}
			final List<PlayerStats> top10 = new ArrayList<>();
			for (final String player : playerCount.keySet()) {
				top10.add(new PlayerStats(player, playerCount.get(player)));
			}
			if (top10.isEmpty()) {
				if (sender instanceof Player) {
					sender.sendMessage(MessageFormat.format("No suspicious activity within that time-frame in {0}!", loc.getWorld().getName()));
				} else {
					sender.sendMessage("No suspicious activity within that time-frame!");
				}
				return;
			}

			Collections.sort(top10, new PlayerStatsComparator());
			HuntSession.getSession(sender)
			.setLookupCache(top10)
			.setUserData(dataMap);

			final StringBuilder sb = new StringBuilder();
			boolean isEmpty = true;
			for (final PlayerStats stat : top10.subList(0, Math.min(top10.size(), size))) {
				if (!stat.getPlayer().equals("#tnt") && !BmAPI.isBanned(stat.getPlayer())) {
					int diaCount = stat.getCount(Material.DIAMOND_ORE);
					float diaRatio = stat.getRatio(Material.DIAMOND_ORE);
					diaCount += stat.getCount(Material.DEEPSLATE_DIAMOND_ORE);
					diaRatio += stat.getRatio(Material.DEEPSLATE_DIAMOND_ORE);
					
					int stoneCount = stat.getCount(Material.STONE);
					float stoneRatio = stat.getRatio(Material.STONE);
					
					// Suspicious
					if (diaCount >= 10 && diaRatio >= 0.02 && stoneCount >= 100) {
						if (isEmpty) {
							sb.append("§4[§c§lMLMC§4] §cThe following players are tagged for x-ray:");
							isEmpty = false;
						}
						sb.append("\n§7- §e" + stat.getPlayer() + "§: §b");
						sb.append(PlayerStatsComparator.getColor(Material.DIAMOND_ORE) +
								MessageFormat.format("§l{0,number,##} {1,number,##}%", diaCount, 100 * diaRatio));
						sb.append("§7, ");
						sb.append(PlayerStatsComparator.getColor(Material.STONE) +
								MessageFormat.format("§l{0,number,##} {1,number,##}%", stoneCount, 100 * stoneRatio));
					}
				}
			}
			if (!isEmpty) {
				sender.sendMessage(sb.toString().split("\n"));
			}
			else {
				sender.sendMessage("§4[§c§lMLMC§4] §cNo suspicious players in §e" + loc.getWorld());
			}
		}
	}

	private void updateMap(Map<Material, Integer> blockCount, Material blockId) {
		if (!blockCount.containsKey(blockId)) {
			blockCount.put(blockId, 0);
		}
		blockCount.put(blockId, blockCount.get(blockId) + 1);
	}

	private String getBlockKey(CoreProtectAPI.ParseResult parse) {
		return parse.worldName() + ":" + parse.getX() + "," + parse.getY() + "," + parse.getZ();
	}
}
