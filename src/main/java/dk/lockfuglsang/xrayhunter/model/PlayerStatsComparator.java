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
	public static final List<Object> MATS = Arrays.asList(
			Material.DIAMOND_ORE,
			Material.DEEPSLATE_DIAMOND_ORE,
			Material.EMERALD_ORE,
			Material.DEEPSLATE_EMERALD_ORE,
			Material.SPAWNER,
			Material.DEEPSLATE_GOLD_ORE,
			Material.GOLD_ORE,
			Material.DEEPSLATE_IRON_ORE,
			Material.IRON_ORE,
			Material.DEEPSLATE,
			Material.STONE
			);

	public static final Map<Material, String> MAT_COLORS = new HashMap<>();
	static {
		MAT_COLORS.put(Material.DEEPSLATE_DIAMOND_ORE, "§b");
		MAT_COLORS.put(Material.DIAMOND_ORE, "§b");
		MAT_COLORS.put(Material.DEEPSLATE_EMERALD_ORE, "§a");
		MAT_COLORS.put(Material.EMERALD_ORE, "§a");
		MAT_COLORS.put(Material.SPAWNER, "§8");
		MAT_COLORS.put(Material.GOLD_ORE, "§e");
		MAT_COLORS.put(Material.DEEPSLATE_GOLD_ORE, "§e");
		MAT_COLORS.put(Material.IRON_ORE, "§f");
		MAT_COLORS.put(Material.DEEPSLATE_IRON_ORE, "§f");
		MAT_COLORS.put(Material.DEEPSLATE, "§7");
		MAT_COLORS.put(Material.STONE, "§7");
	}

	public static final Map<Material, String> MAT_ALIASES = new HashMap<>();
	static {
		MAT_ALIASES.put(Material.DEEPSLATE_DIAMOND_ORE, "DIA");
		MAT_ALIASES.put(Material.DIAMOND_ORE, "DDIA");
		MAT_ALIASES.put(Material.DEEPSLATE_EMERALD_ORE, "EME");
		MAT_ALIASES.put(Material.EMERALD_ORE, "DEME");
		MAT_ALIASES.put(Material.SPAWNER, "SPA");
		MAT_ALIASES.put(Material.GOLD_ORE, "GOL");
		MAT_ALIASES.put(Material.DEEPSLATE_GOLD_ORE, "DGOL");
		MAT_ALIASES.put(Material.IRON_ORE, "IRO");
		MAT_ALIASES.put(Material.DEEPSLATE_IRON_ORE, "DIRO");
		MAT_ALIASES.put(Material.DEEPSLATE, "DSTO");
		MAT_ALIASES.put(Material.STONE, "STO");
	}
	
	public static final Map<Material, Material> DEEPSLATE_VARIANTS = new HashMap<>();
	static {
		DEEPSLATE_VARIANTS.put(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE);
		DEEPSLATE_VARIANTS.put(Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE);
		DEEPSLATE_VARIANTS.put(Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE);
		DEEPSLATE_VARIANTS.put(Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE);
	}

	public static String getAlias(Material mat) {
		final String alias = MAT_ALIASES.get(mat);
		return alias != null ? alias : "";
	}

	public static Material getDeepslateVariant(Material mat) {
		final Material variant = DEEPSLATE_VARIANTS.get(mat);
		return variant;
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
