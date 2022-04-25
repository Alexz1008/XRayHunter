package dk.lockfuglsang.xrayhunter.command;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dk.lockfuglsang.minecraft.command.AbstractCommand;
import dk.lockfuglsang.util.TimeUtil;
import dk.lockfuglsang.xrayhunter.XRayHunter;
import dk.lockfuglsang.xrayhunter.coreprotect.Callback;
import dk.lockfuglsang.xrayhunter.coreprotect.CoreProtectHandler;
import dk.lockfuglsang.xrayhunter.model.HuntSession;
import dk.lockfuglsang.xrayhunter.model.PlayerStats;
import dk.lockfuglsang.xrayhunter.model.PlayerStatsComparator;
import dk.lockfuglsang.xrayhunter.model.PlayerStatsComparatorNether;
import net.coreprotect.CoreProtectAPI;

/**
 * Lookups possible candidates for the last days.
 */
class LookupCommand extends AbstractCommand {
	private final XRayHunter plugin;

	LookupCommand(XRayHunter plugin) {
		super("lookup|l", null, "time", "Hunt in the past (1d, 2h, 2h30m)");
		this.plugin = plugin;
	}

	@Override
	public boolean execute(final CommandSender sender, String alias, Map<String, Object> data, String... args) {
		final Player player = (Player) sender;
		if (args.length >= 1) {
			final long millis = TimeUtil.millisFromString(args[0]);
			if (millis == 0) {
				sender.sendMessage("Invalid time-argument, try \u00a792d");
				return false;
			}

			int size = 10;
			if (args.length == 2) {
				size = Integer.parseInt(args[1]);
			}
			if(player.getWorld().getEnvironment() == World.Environment.NETHER) {
				CoreProtectHandler.performLookup(plugin, sender, player.getLocation(), TimeUtil.millisAsSeconds(millis), PlayerStatsComparatorNether.MATS, new LookupCallback(sender, size));
			} else { CoreProtectHandler.performLookup(plugin, sender, player.getLocation(), TimeUtil.millisAsSeconds(millis), PlayerStatsComparator.MATS, new LookupCallback(sender, size)); }

			return true;
		}
		return false;
	}

	private void updateMap(Map<Material, Integer> blockCount, Material blockId) {
		if (!blockCount.containsKey(blockId)) {
			blockCount.put(blockId, 0);
		}
		blockCount.put(blockId, blockCount.get(blockId) + 1);
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
					sender.sendMessage("Nosuspicious activity within that time-frame!");
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

			if(((Player) sender).getWorld().getEnvironment() == World.Environment.NETHER) {
				Collections.sort(top10, new PlayerStatsComparatorNether());
				HuntSession.getSession(sender)
				.setLookupCache(top10)
				.setUserData(dataMap);

				final StringBuilder sb = new StringBuilder();
				for (final Object obj : PlayerStatsComparatorNether.MATS) {
					Material mat = (Material) obj;
					sb.append(PlayerStatsComparatorNether.getColor(mat) + "§l " + mat.name().substring(0, 3));
				}
				sb.append("\n");
				int place = 1;
				for (final PlayerStats stat : top10.subList(0, Math.min(top10.size(), size))) {
					sb.append("#" + place);
					for (final Object obj : PlayerStatsComparatorNether.MATS) {
						Material mat = (Material) obj;
						sb.append(PlayerStatsComparatorNether.getColor(mat) +
								MessageFormat.format(" §l{0,number,##}§7({1,number,##}%)", stat.getCount(mat), 100 * stat.getRatio(mat)));
					}
					sb.append(" §9" + stat.getPlayer() + "\n");
					place++;
				}
				sender.sendMessage(sb.toString().split("\n"));
			} else {
				Collections.sort(top10, new PlayerStatsComparator());
				HuntSession.getSession(sender)
				.setLookupCache(top10)
				.setUserData(dataMap);

				final StringBuilder sb = new StringBuilder();
				for (final Object obj : PlayerStatsComparator.MATS) {
					Material mat = (Material) obj;
					if (mat.toString().contains("DEEPSLATE")) continue;
					sb.append(PlayerStatsComparator.getColor(mat) + "§l " + mat.toString().substring(0, 3));
				}
				sb.append("\n");
				int place = 1;
				for (final PlayerStats stat : top10.subList(0, Math.min(top10.size(), size))) {
					sb.append("#" + place);
					for (final Object obj : PlayerStatsComparator.MATS) {
						Material mat = (Material) obj;
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
					place++;
				}
				sender.sendMessage(sb.toString().split("\n"));
			}

		}
	}

	private String getBlockKey(CoreProtectAPI.ParseResult parse) {
		return parse.worldName() + ":" + parse.getX() + "," + parse.getY() + "," + parse.getZ();
	}
}
