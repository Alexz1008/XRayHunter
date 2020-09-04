package dk.lockfuglsang.xrayhunter;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
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

	private CoreProtectAPI api;

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
		this.api = coreProtectAPI;
		getCommand("xhunt").setExecutor(new MainCommand(this));
	}

	// package protected
	private CoreProtectAPI getCoreProtect() {
		final Plugin plugin = Bukkit.getPluginManager().getPlugin("CoreProtect");
		if (plugin instanceof CoreProtect && plugin.isEnabled()) {
			final CoreProtect coreProtect = (CoreProtect) plugin;
			final CoreProtectAPI api = coreProtect.getAPI();
			if (api != null && api.APIVersion() >= 6 && CoreProtectHandler.getAdaptor() != null) {
				return api;
			}
		}
		return null;
	}

	public CoreProtectAPI getAPI() {
		if (api == null) {
			final CoreProtectAPI coreProtectAPI = getCoreProtect();
			if (coreProtectAPI != null) {
				api = coreProtectAPI;
			}
		}
		return api;
	}
}
