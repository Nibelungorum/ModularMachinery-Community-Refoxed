package cn.howxu.mmcr.internal.command;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.export.MultiblockExportService;
import cn.howxu.mmcr.internal.item.MultiblockDetectorItem;
import cn.howxu.mmcr.internal.item.MultiblockDetectorSelection;
import cn.howxu.mmcr.registry.ModItems;

import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Registers and executes `/mmcr export` for multiblock detector selections.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ExportCommand {

    private static final int MAX_EXPORT_VOLUME = 3_276_800;

    private enum ExportFormat {
        JAVA,
        KUBEJS
    }

    private ExportCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mmcr")
                .then(Commands.literal("export")
                        .then(Commands.literal("java").executes(ctx -> run(ctx, ExportFormat.JAVA)))
                        .then(Commands.literal("kjs").executes(ctx -> run(ctx, ExportFormat.KUBEJS)))));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, ExportFormat format) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return export(player, format,
                message -> ctx.getSource().sendSuccess(() -> message, false),
                ctx.getSource()::sendFailure,
                false);
    }

    public static void exportFromScreen(ServerPlayer player, boolean kubeJs) {
        export(player, kubeJs ? ExportFormat.KUBEJS : ExportFormat.JAVA,
                player::sendSystemMessage, player::sendSystemMessage, true);
    }

    private static int export(ServerPlayer player, ExportFormat format, Consumer<Component> success,
                              Consumer<Component> failure, boolean mainHandOnly) {
        ServerLevel level = player.level();

        ItemStack detector = findSingleDetector(player, mainHandOnly);
        if (detector == null) {
            failure.accept(Component.translatable("command.mmcr.export.require_detector"));
            return 0;
        }

        MultiblockDetectorSelection selection = MultiblockDetectorItem.selection(detector);
        if (!selection.isComplete()) {
            failure.accept(Component.translatable("command.mmcr.export.incomplete_selection"));
            return 0;
        }

        BlockPos first = selection.firstPos();
        BlockPos second = selection.secondPos();
        BlockPos controller = selection.controllerPos();
        BlockPos min = new BlockPos(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()));
        BlockPos max = new BlockPos(
                Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ()));

        if (!contains(min, max, controller)) {
            failure.accept(Component.translatable("command.mmcr.export.controller_outside"));
            return 0;
        }

        long volume = volume(min, max);
        if (exceedsExportVolume(volume)) {
            failure.accept(Component.translatable("command.mmcr.export.volume_exceeded", volume, MAX_EXPORT_VOLUME));
            return 0;
        }

        List<MultiblockExportService.SnapshotEntry> snapshot = snapshot(level, controller, min, max);
        var server = level.getServer();
        var gameDir = server.getServerDirectory();
        var face = selection.controllerFace();
        var roll = controllerRoll(level, controller);
        success.accept(Component.translatable("command.mmcr.export.started", volume));

        MultiblockExportService.executor().submit(() -> {
            try {
                var path = MultiblockExportService.writeExport(gameDir, LocalDateTime.now(), snapshot, face, roll, format == ExportFormat.KUBEJS);
                server.executeIfPossible(() -> player.sendSystemMessage(Component.translatable(
                        "command.mmcr.export.written", gameDir.relativize(path).toString())));
            } catch (Exception e) {
                MMCR.LOG.error("Failed to export multiblock detector selection", e);
                server.executeIfPossible(() -> player.sendSystemMessage(Component.translatable(
                        "command.mmcr.export.failed", String.valueOf(e.getMessage()))));
            }
        });
        return 1;
    }

    private static ItemStack findSingleDetector(ServerPlayer player, boolean mainHandOnly) {
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        boolean mainDetector = main.is(ModItems.MULTIBLOCK_DETECTOR.get());
        boolean offDetector = off.is(ModItems.MULTIBLOCK_DETECTOR.get());
        if (!detectorSelectionAllowed(mainDetector, offDetector, mainHandOnly)) return null;
        return mainDetector ? main : off;
    }

    static boolean detectorSelectionAllowed(boolean mainHandDetector, boolean offHandDetector, boolean mainHandOnly) {
        return mainHandOnly ? mainHandDetector : mainHandDetector != offHandDetector;
    }

    private static Direction controllerRoll(ServerLevel level, BlockPos controller) {
        BlockState state = level.getBlockState(controller);
        return state.hasProperty(MachineControllerBlock.ROLL_FACING)
                ? state.getValue(MachineControllerBlock.ROLL_FACING)
                : Direction.SOUTH;
    }

    private static List<MultiblockExportService.SnapshotEntry> snapshot(ServerLevel level, BlockPos controller,
                                                                        BlockPos min, BlockPos max) {
        List<MultiblockExportService.SnapshotEntry> entries = new ArrayList<>();
        for (long y = min.getY(); y <= max.getY(); y++) {
            for (long z = min.getZ(); z <= max.getZ(); z++) {
                for (long x = min.getX(); x <= max.getX(); x++) {
                    BlockPos worldPos = new BlockPos((int) x, (int) y, (int) z);
                    BlockState state = level.getBlockState(worldPos);
                    entries.add(new MultiblockExportService.SnapshotEntry(
                            worldPos.subtract(controller),
                            BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                            state,
                            state.isAir(),
                            worldPos.equals(controller)));
                }
            }
        }
        return List.copyOf(entries);
    }

    private static boolean contains(BlockPos min, BlockPos max, BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    static boolean exceedsExportVolume(long volume) {
        return volume > MAX_EXPORT_VOLUME;
    }

    static long volume(BlockPos min, BlockPos max) {
        long x = (long) max.getX() - min.getX() + 1;
        long y = (long) max.getY() - min.getY() + 1;
        long z = (long) max.getZ() - min.getZ() + 1;
        if (x > Long.MAX_VALUE / y || x * y > Long.MAX_VALUE / z) return Long.MAX_VALUE;
        return x * y * z;
    }
}
