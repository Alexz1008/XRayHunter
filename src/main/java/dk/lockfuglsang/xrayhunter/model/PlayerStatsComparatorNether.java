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
public class PlayerStatsComparatorNether implements Comparator<PlayerStats> {
	public static final List<Object> MATS = Arrays.asList(
			Material.ANCIENT_DEBRIS,
			Material.NETHER_QUARTZ_ORE,
			Material.NETHER_GOLD_ORE,
			Material.NETHERRACK
			);

	public static final Map<Material, String> MAT_COLORS = new HashMap<>();
	static {
		MAT_COLORS.put(Material.NETHER_GOLD_ORE, "§e");
		MAT_COLORS.put(Material.NETHER_QUARTZ_ORE, "§f");
		MAT_COLORS.put(Material.ANCIENT_DEBRIS, "§c");
		MAT_COLORS.put(Material.NETHERRACK, "§7");
	}

	public static String getColor(Material mat) {
		final String color = MAT_COLORS.get(mat);
		return color != null ? color : "";
	}

	@Override
	public int compare(PlayerStats o1, PlayerStats o2) {
		int cmp = 0;
		for (final Object obj : MATS) {
			Material blockId = (Material) obj;
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
