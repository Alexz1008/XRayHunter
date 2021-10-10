package dk.lockfuglsang.xrayhunter.command;

import java.util.Map;

import org.bukkit.command.CommandSender;

import dk.lockfuglsang.minecraft.command.AbstractCommand;
import dk.lockfuglsang.xrayhunter.XRayHunter;

public class SuspiciousCommand extends AbstractCommand {
	XRayHunter plugin;
    public SuspiciousCommand(XRayHunter plugin) {
        super("suspicious|sus", null, null, "Automatically checks most likely xrayers");
    	this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String alias, Map<String, Object> data, String... args) {
        plugin.susCommand(sender);
        return true;
    }
}
