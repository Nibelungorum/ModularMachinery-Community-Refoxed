package cn.howxu.mmcr.internal.command;

import cn.howxu.mmcr.internal.reload.DynamicContentReloadService;
import cn.howxu.mmcr.internal.network.RuntimeContentSync;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;


public final class ReloadCommand {

    private ReloadCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mmcr")
                .then(Commands.literal("reload")
                        .requires(src -> src.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> {
                            var commit = DynamicContentReloadService.reloadCurrentWithSnapshot();
                            var result = commit.result();
                            RuntimeContentSync.sendToAll(ctx.getSource().getServer(), commit.snapshot());
                            ctx.getSource().sendSuccess(() -> Component.translatable("command.mmcr.reload.success",
                                    result.addedStructures().size(), result.updatedStructures().size(),
                                    result.removedStructures().size(), result.addedRecipes(),
                                    result.updatedRecipes(), result.removedRecipes()), false);
                            return 1;
                        })));
    }
}
