package dk.lockfuglsang.xrayhunter;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
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
	// SQL
	public String url, user, pass;

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

		File file = new File(getDataFolder(), "config.yml");
		ConfigurationSection cfg = YamlConfiguration.loadConfiguration(file);

		// SQL
		ConfigurationSection sql = cfg.getConfigurationSection("sql");
		url = "jdbc:mysql://" + sql.getString("host") + ":" + sql.getString("port") + "/" + 
				sql.getString("db") + sql.getString("flags");
		user = sql.getString("username");
		pass = sql.getString("password");
	}

	// package protected
	private CoreProtectAPI getCoreProtect() {
		final Plugin plugin = getServer().getPluginManager().getPlugin("CoreProtect");
		if (plugin instanceof CoreProtect && plugin.isEnabled()) {
			final CoreProtect coreProtect = (CoreProtect) plugin;
			final CoreProtectAPI api = coreProtect.getAPI();
			if (api != null && api.APIVersion() >= 9) {
				Bukkit.getLogger().log(Level.INFO, "[XRayHunter] Loaded using CoreProtect API Version " + api.APIVersion());
				return api;
			}
		}
		return null;
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		Player p = e.getPlayer();
		if (p.hasPermission("mycommand.staff")) {
			doSusLookup(p);
		}
	}
	
	public void susCommand(CommandSender s) {
		if (s.hasPermission("mycommand.staff")) {
			doSusLookup(s);
		}
	}
	
	private void doSusLookup(CommandSender s) {
		World w1 = Bukkit.getWorld("Invenire");
		Location loc1 = w1.getSpawnLocation();
		World w2 = Bukkit.getWorld("Cyprus");
		Location loc2 = w2.getSpawnLocation();
		CoreProtectHandler.performLookup(this, s, loc1, TimeUtil.millisAsSeconds(TimeUtil.millisFromString("2d")), PlayerStatsComparator.MATS, new SusCallback(s, 99, loc1));
		CoreProtectHandler.performLookup(this, s, loc2, TimeUtil.millisAsSeconds(TimeUtil.millisFromString("2d")), PlayerStatsComparator.MATS, new SusCallback(s, 99, loc2));
	}

	private class SusCallback extends Callback {
		private final CommandSender sender;
		private final int size;
		private final Location loc;

		SusCallback(CommandSender sender, int size, Location loc) {
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
				if (actionId == CoreProtectHandler.ACTION_BREAK && !userPlacedBlocks.containsKey(blockKey) && parse.getY() <= 40) {
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
					
					int stoneCount = stat.getCount(Material.STONE) + stat.getCount(Material.DEEPSLATE) + stat.getCount(Material.DIORITE) + stat.getCount(Material.ANDESITE) +
							stat.getCount(Material.GRANITE);
					float stoneRatio = stat.getRatio(Material.STONE) + stat.getRatio(Material.DEEPSLATE) + stat.getRatio(Material.DIORITE) + stat.getRatio(Material.ANDESITE) +
							stat.getRatio(Material.GRANITE);
					
					if (hasRecentReport(stat.getPlayer())) {
						continue;
					}
					
					// Suspicious
					if (diaCount >= 10 && diaRatio >= 0.02 && stoneCount >= 100) {
						if (isEmpty) {
							sb.append("§4[§c§lMLMC§4] §cThe following players are tagged for x-ray in " + loc.getWorld().getName() + ":");
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
				sender.sendMessage("§4[§c§lMLMC§4] §cNo suspicious players in §e" + loc.getWorld().getName());
			}
		}
	}
	
	private boolean hasRecentReport(String name) {
		try{
			Connection con = DriverManager.getConnection(url, user, pass);
			Statement stmt = con.createStatement();
			ResultSet rs;
			
			// Show all relevant PPRs
			rs = stmt.executeQuery("SELECT * FROM neopprs_pprs WHERE LOWER(username) LIKE LOWER('" + name + "');");
			while (rs.next()) {
				String date = rs.getString(5);
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd-yy", Locale.ENGLISH);
				LocalDate pprTime = LocalDate.parse(date, formatter);
				LocalDate now = LocalDate.now();
				if (now.toEpochDay() - pprTime.toEpochDay() <= 1) {
					return true;
				}
			}
			con.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return false;
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
