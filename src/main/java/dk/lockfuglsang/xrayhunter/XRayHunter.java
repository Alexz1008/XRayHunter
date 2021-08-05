package dk.lockfuglsang.xrayhunter;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import dk.lockfuglsang.xrayhunter.command.MainCommand;
import dk.lockfuglsang.xrayhunter.coreprotect.CoreProtectHandler;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;

/**
 * Bukkit Plugin for hunting X-Rayers using the CoreProtect API
 */
public class XRayHunter extends JavaPlugin {
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
}
