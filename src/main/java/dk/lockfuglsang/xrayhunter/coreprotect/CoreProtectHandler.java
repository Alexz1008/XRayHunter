package dk.lockfuglsang.xrayhunter.coreprotect;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import net.coreprotect.database.Database;

/**
 * Proxy/Handler to CoreProtect, supplying the lookups the API currently doesn't support
 */
public class CoreProtectHandler {
	public static final int ACTION_BREAK = 0;
	public static final int ACTION_PLACE = 1;
	private static final Logger log = Logger.getLogger(CoreProtectHandler.class.getName());
	private static CoreProtectAPI api = null;

	public static void performLookup(final Plugin plugin, final CommandSender sender, Location loc, final int stime, final List<Object> restrictBlocks, final Callback callback) {
		if (api == null) {
			api = ((CoreProtect) Bukkit.getPluginManager().getPlugin("CoreProtect")).getAPI();
		}
		
		Bukkit.getScheduler().runTaskAsynchronously(plugin, (Runnable) () -> {
			try (Connection connection = Database.getConnection(true); Statement statement = connection.createStatement()) {
				final List<Integer> action_list = new ArrayList<>();
				action_list.add(0); // ActionId = 0 - Break
				final List<String[]> data = api.performLookup(stime, null, null, restrictBlocks, null, action_list, 10000, loc);
				callback.setData(data);
				Bukkit.getScheduler().runTaskAsynchronously(plugin, callback);
			} catch (final Exception e) {
				log.log(Level.WARNING, "Unable to lookup data", e);
			}
		});
	}
}
