package cn.howxu.mmcr.internal.command;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineSelector;
import cn.howxu.mmcr.internal.assembly.MultiblockAssemblyService;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class BuildCommand {

    private BuildCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        SuggestionProvider<CommandSourceStack> machineSuggestions = (ctx, builder) ->
                SharedSuggestionProvider.suggest(
                        MachineRegistry.effectiveSnapshot().keySet().stream().map(Identifier::toString),
                        builder);

        dispatcher.register(Commands.literal("mmcr")
                .then(Commands.literal("build")
                        .executes(BuildCommand::buildDefault)
                        .then(Commands.argument("machineId", IdentifierArgument.id())
                                .suggests(machineSuggestions)
                                .executes(BuildCommand::buildNamed))));
    }

    private static int buildDefault(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return run(ctx, null);
    }

    private static int buildNamed(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Identifier parsed = IdentifierArgument.getId(ctx, "machineId");
        return run(ctx, parsed);
    }

    private static int run(CommandContext<CommandSourceStack> ctx, Identifier requested) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.level();
        MachineSelector.Result selection = MachineSelector.select(requested, MachineRegistry.effectiveSnapshot());
        if (selection.machine() == null) {
            ctx.getSource().sendFailure(Component.translatable(
                    "command.mmcr.build.no_machine",
                    requested == null ? Component.empty()
                            : Component.literal(requested.toString())));
            return 0;
        }
        Machine machine = selection.machine();

        // 控制器朝玩家相反方向(类似放方块瞬间 controller 自动朝向玩家视线方向)
        Direction ctrlFacing = player.getDirection().getOpposite();
        BlockPos controller = anchorInFrontOf(player);
        placeController(level, machine, controller, ctrlFacing);
        MultiblockAssemblyService.Result result = null;
        BlockEntity blockEntity = level.getBlockEntity(controller);
        if (blockEntity instanceof MachineControllerBlockEntity controllerBlockEntity) {
            result = MultiblockAssemblyService.build(player, controllerBlockEntity, true);
        }
        if (result == null) {
            ctx.getSource().sendFailure(Component.literal("MMCR: controller block entity was not created."));
            return 0;
        }
        if (result.interactionResult() == InteractionResult.FAIL) {
            ctx.getSource().sendFailure(Component.translatable(result.message().key(), result.message().args()));
            return 0;
        }

        MultiblockAssemblyService.Result serviceResult = result;
        ctx.getSource().sendSuccess(() -> Component.translatable(serviceResult.message().key(), serviceResult.message().args())
                        .append(Component.literal(" "))
                        .append(Component.translatable(
                                "command.mmcr.build.accepted", serviceResult.changedBlocks(), machine.displayName(), controller.toShortString(),
                                selection.isFallback() ? Component.translatable("command.mmcr.build.default")
                                        : Component.empty())),
                true);
        return 1;
    }

    /** 控制器放在玩家视线前方一格 + 脚下高度,pattern 由 (0,0,0) 向外延伸。 */
    private static BlockPos anchorInFrontOf(ServerPlayer player) {
        BlockPos feet = player.blockPosition();
        return feet.relative(player.getDirection());
    }

    private static void placeController(ServerLevel level, Machine machine, BlockPos controller, Direction ctrlFacing) {
        BlockState controllerState = ModBlocks.controllerFor(machine.registryName()).get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, ctrlFacing);
        if (level.getBlockState(controller).is(controllerState.getBlock())) return;
        level.setBlock(controller, controllerState, 3);
    }
}
