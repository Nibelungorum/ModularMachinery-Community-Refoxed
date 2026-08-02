package cn.howxu.mmcr.internal.command;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.machine.MMCRDefaultMachines;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.fml.ModList;

public class MMCRReloadCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mmcr")
                .then(Commands.literal("reload")
                        .requires(src -> src.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_ADMIN))
                        .executes(ctx -> {
                            MMCRDefaultMachines.ensureRegistered();
                            ctx.getSource().sendSuccess(
                                    () -> net.minecraft.network.chat.Component.literal(
                                            "MMCR reload refreshed built-in machines; datapack recipes are read at runtime"), true);
                            if (ModList.get().isLoaded("kubejs")) {
                                MMCR.LOG.info("KubeJS reload would be triggered here");
                            }
                            return 1;
                        })));
    }
}
