package dk.lockfuglsang.xrayhunter.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import dk.lockfuglsang.minecraft.command.BaseCommandExecutor;
import dk.lockfuglsang.xrayhunter.XRayHunter;

public class MainCommand extends BaseCommandExecutor {

	public MainCommand(XRayHunter plugin) {
		super("xhunt", "xhunt.use", "Main XRay Hunter command");
		add(new LookupCommand(plugin));
		add(new DetailCommand());
		add(new TeleportCommand());
		add(new SuspiciousCommand(plugin));
	}

	@Override
	public boolean onCommand(CommandSender commandSender, Command command, String alias, String[] args) {
		if (XRayHunter.getCoreProtectAPI() == null) {
			commandSender.sendMessage("No valid CoreProtect plugin was found!");
			return true;
		}
		return super.onCommand(commandSender, command, alias, args);
	}
}
