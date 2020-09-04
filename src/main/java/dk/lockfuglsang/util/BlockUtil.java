package dk.lockfuglsang.util;

import java.util.Arrays;
import java.util.Collection;

import org.bukkit.Material;

/**
 * Utility for common block related functions.
 */
public enum BlockUtil {;
	private static final Collection<Material> FLUIDS = Arrays.asList(Material.WATER, Material.LAVA);

	public static boolean isBreathable(Material block) {
		return !block.isSolid() && !isFluid(block);
	}

	public static boolean isFluid(Material block) {
		return FLUIDS.contains(block);
	}
}
