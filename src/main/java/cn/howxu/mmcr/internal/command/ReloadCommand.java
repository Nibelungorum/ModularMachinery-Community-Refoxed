package cn.howxu.mmcr.internal.command;

import cn.howxu.mmcr.internal.reload.DynamicContentReloadService;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class ReloadCommand {

    private ReloadCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mmcr")
                .then(Commands.literal("reload")
                        .requires(src -> src.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_ADMIN))
                        .executes(ctx -> {
                            var result = DynamicContentReloadService.reload(candidate -> {});
                            cn.howxu.mmcr.internal.network.ControllerSpecSync.sendToAll(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                    "MMCR reload: structures +%d ~%d -%d, recipes +%d ~%d -%d".formatted(
                                            result.addedStructures().size(), result.updatedStructures().size(),
                                            result.removedStructures().size(), result.addedRecipes(),
                                            result.updatedRecipes(), result.removedRecipes())), false);
                            return 1;
                        })));
    }
}
