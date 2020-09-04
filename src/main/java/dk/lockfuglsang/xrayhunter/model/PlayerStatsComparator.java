package dk.lockfuglsang.xrayhunter.model;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;

/**
 * Comparator the maps of block-counts for two users
 */
public class PlayerStatsComparator implements Comparator<PlayerStats> {
	public static final List<Material> MATS = Arrays.asList(
			Material.DIAMOND_ORE,
			Material.ANCIENT_DEBRIS,
			Material.SPAWNER,
			Material.EMERALD_ORE,
			Material.GOLD_ORE,
			Material.IRON_ORE,
			Material.STONE
			);

	public static final Map<Material, String> MAT_COLORS = new HashMap<>();
	static {
		MAT_COLORS.put(Material.DIAMOND_ORE, "§b");
		MAT_COLORS.put(Material.SPAWNER, "§0");
		MAT_COLORS.put(Material.EMERALD_ORE, "§a");
		MAT_COLORS.put(Material.GOLD_ORE, "§e");
		MAT_COLORS.put(Material.IRON_ORE, "§f");
		MAT_COLORS.put(Material.ANCIENT_DEBRIS, "§c");
		MAT_COLORS.put(Material.STONE, "§7");
	}

	public static String getColor(Material mat) {
		final String color = MAT_COLORS.get(mat);
		return color != null ? color : "";
	}

	@Override
	public int compare(PlayerStats o1, PlayerStats o2) {
		int cmp = 0;
		for (final Material blockId : MATS) {
			final int c1 = o1.getCount(blockId);
			final int c2 = o2.getCount(blockId);
			cmp = c2 - c1;
			if (cmp != 0) {
				return cmp;
			}
		}
		return cmp;
	}
}
