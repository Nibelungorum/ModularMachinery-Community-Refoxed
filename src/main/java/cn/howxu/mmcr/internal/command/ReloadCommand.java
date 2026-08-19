package cn.howxu.mmcr.internal.command;

import cn.howxu.mmcr.internal.reload.DynamicContentReloadService;

import cn.howxu.mmcr.internal.network.RuntimeContentSync;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import net.minecraft.server.permissions.Permissions;

public final class ReloadCommand {

    private ReloadCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mmcr")
                .then(Commands.literal("reload")
                        .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                        .executes(ctx -> {
                            var result = DynamicContentReloadService.reload(candidate -> {});
                            RuntimeContentSync.sendToAll(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.translatable("command.mmcr.reload.success",
                                    result.addedStructures().size(), result.updatedStructures().size(),
                                    result.removedStructures().size(), result.addedRecipes(),
                                    result.updatedRecipes(), result.removedRecipes()), false);
                            return 1;
                        })));
    }
}
