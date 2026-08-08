package cn.howxu.mmcr.internal.command;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.BlockRotator;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineSelector;
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
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import org.nibelungorum.DefaultMachines;

public final class BuildCommand {

    private BuildCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        SuggestionProvider<CommandSourceStack> machineSuggestions = (ctx, builder) ->
                SharedSuggestionProvider.suggest(
                        MachineRegistry.getAll().keySet().stream().map(Identifier::toString),
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
        DefaultMachines.ensureRegistered();

        MachineSelector.Result selection = MachineSelector.select(requested, MachineRegistry.getAll());
        if (selection.machine() == null) {
            ctx.getSource().sendFailure(net.minecraft.network.chat.Component.literal(
                    "MMCR: no machine" + (requested != null ? " '" + requested + "'" : "")
                            + " registered. Use /mmcr reload or check KubeJS bindings."));
            return 0;
        }
        Machine machine = selection.machine();

        // 控制器朝玩家相反方向(类似放方块瞬间 controller 自动朝向玩家视线方向)
        Direction ctrlFacing = player.getDirection().getOpposite();
        BlockPos controller = anchorInFrontOf(player);
        placeMachine(level, machine, controller, ctrlFacing);
        forceTickController(level, controller);

        ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                "MMCR built '" + machine.registryName() + "' at " + controller.toShortString()
                        + (selection.isFallback() ? " (default)" : "")),
                true);
        return 1;
    }

    /** 控制器放在玩家视线前方一格 + 脚下高度,pattern 由 (0,0,0) 向外延伸。 */
    private static BlockPos anchorInFrontOf(ServerPlayer player) {
        BlockPos feet = player.blockPosition();
        return feet.relative(player.getDirection());
    }

    /**
     * 按 pattern 相对 controller 的偏移逐格 setBlock;结构坐标随 controller 朝向旋转。
     */
    private static void placeMachine(ServerLevel level, Machine machine, BlockPos controller, Direction ctrlFacing) {
        BlockState controllerState = ModBlocks.controllerFor(machine.registryName()).get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, ctrlFacing);
        setBlock(level, controller, controllerState);

        for (var entry : machine.pattern().pattern().entrySet()) {
            if (entry.getKey().equals(BlockPos.ZERO)) continue;
            BlockPos world = controller.offset(BlockRotator.rotateSouthTo(entry.getKey(), ctrlFacing));
            BlockState state = resolveBlockState(entry.getValue());
            if (state == null) continue;
            setBlock(level, world, state);
        }
    }

    /** 写盘后立即强制控制器 tick 一次,form 状态同步到 FORMED BlockState。 */
    private static void forceTickController(ServerLevel level, BlockPos controller) {
        BlockEntity be = level.getBlockEntity(controller);
        if (be instanceof cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity ctrl) {
            ctrl.serverTick();
        }
    }

    private static BlockState resolveBlockState(BlockPredicate predicate) {
        return switch (predicate) {
            case BlockPredicate.OfBlock of -> of.block().defaultBlockState();
            case BlockPredicate.AnyOf anyOf -> anyOf.children().stream()
                    .filter(c -> c instanceof BlockPredicate.OfBlock)
                    .map(c -> ((BlockPredicate.OfBlock) c).block().defaultBlockState())
                    .findFirst().orElse(null);
            case BlockPredicate.Air ignored -> null;
            default -> Blocks.STONE.defaultBlockState();
        };
    }

    private static void setBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).is(state.getBlock())) return;
        level.setBlock(pos, state, 3);
        level.gameEvent(null, GameEvent.BLOCK_PLACE, pos);
    }
}
