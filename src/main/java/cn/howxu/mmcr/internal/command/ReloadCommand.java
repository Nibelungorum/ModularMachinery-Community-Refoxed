package cn.howxu.mmcr.internal.command;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContextPool;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.fml.ModList;
import org.nibelungorum.DefaultMachines;
import org.nibelungorum.DefaultRecipes;

public class ReloadCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mmcr")
                .then(Commands.literal("reload")
                        .requires(src -> src.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_ADMIN))
                        .executes(ctx -> {
                            DefaultMachines.ensureRegistered();
                            DefaultRecipes.ensureRegistered();
                            RecipeCraftingContextPool.onGlobalReload();
                            MachineRegistry.rebuildCompiledCache();
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
