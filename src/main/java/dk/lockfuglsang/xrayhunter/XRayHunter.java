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
			for (World world : Bukkit.getWorlds()) {
				if (world.getName().contains("Aurum")) {
					w = world;
					break;
				}
			}
			Location loc = w.getSpawnLocation();
			boolean banned = false;
			try {
				banned = BmAPI.isBanned("Okuur");
				System.out.println(banned);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
			// CoreProtectHandler.performLookup(this, p, loc, TimeUtil.millisAsSeconds(TimeUtil.millisFromString("2d")), PlayerStatsComparator.MATS, null, new LookupCallback(p, 99));
		}
	}

	private class LookupCallback extends Callback {
		private final CommandSender sender;
		private final int size;

		LookupCallback(CommandSender sender, int size) {
			this.sender = sender;
			this.size = size;
		}

		@Override
		public void run() {
			final List<String[]> result = getData();
			if (result == null || result.isEmpty()) {
				if (sender instanceof Player) {
					sender.sendMessage(MessageFormat.format("No suspicious activity within that time-frame in {0}!", ((Player) sender).getLocation().getWorld().getName()));
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
					sender.sendMessage(MessageFormat.format("No suspicious activity within that time-frame in {0}!", ((Player) sender).getLocation().getWorld().getName()));
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
			sb.append("§4[§c§lMLMC§4] §cThe following players are suspicious and not banned: \n");
			for (final PlayerStats stat : top10.subList(0, Math.min(top10.size(), size))) {
				try {
					Bukkit.getOfflinePlayer(BmAPI.getPlayer(stat.getPlayer()).getUUID()).isBanned();
				} catch (Exception e) {
					e.printStackTrace();
				}
				sb.append("§7- §e").append(stat.getPlayer());
				for (final Material mat : PlayerStatsComparator.MATS) {
					if (mat.toString().contains("DEEPSLATE")) continue;
					int count = stat.getCount(mat);
					float ratio = stat.getRatio(mat);
					if (PlayerStatsComparator.getDeepslateVariant(mat) != null) {
						Material variant = PlayerStatsComparator.getDeepslateVariant(mat);
						count += stat.getCount(variant);
						ratio += stat.getRatio(variant);
					}
					sb.append(PlayerStatsComparator.getColor(mat) +
							MessageFormat.format(" §l{0,number,##}§7 {1,number,##}%", count, 100 * ratio));
				}
				sb.append(" §9" + stat.getPlayer() + "\n");
			}
			sender.sendMessage(sb.toString().split("\n"));

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
