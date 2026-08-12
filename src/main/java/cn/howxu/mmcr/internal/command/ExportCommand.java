package cn.howxu.mmcr.internal.command;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.export.MultiblockExportService;
import cn.howxu.mmcr.internal.item.MultiblockDetectorItem;
import cn.howxu.mmcr.internal.item.MultiblockDetectorSelection;
import cn.howxu.mmcr.registry.ModItems;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
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

/**
 * Registers and executes `/mmcr export` for multiblock detector selections.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ExportCommand {

    private static final int MAX_EXPORT_VOLUME = 32768;

    private ExportCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mmcr")
                .then(Commands.literal("export")
                        .executes(ExportCommand::run)));
    }

    private static int run(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.level();

        ItemStack detector = findSingleDetector(player);
        if (detector == null) {
            ctx.getSource().sendFailure(Component.translatable("command.mmcr.export.require_detector"));
            return 0;
        }

        MultiblockDetectorSelection selection = MultiblockDetectorItem.selection(detector);
        if (!selection.isComplete()) {
            ctx.getSource().sendFailure(Component.translatable("command.mmcr.export.incomplete_selection"));
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
            ctx.getSource().sendFailure(Component.translatable("command.mmcr.export.controller_outside"));
            return 0;
        }

        int volume = volume(min, max);
        if (volume > MAX_EXPORT_VOLUME) {
            ctx.getSource().sendFailure(Component.translatable("command.mmcr.export.volume_exceeded", volume, MAX_EXPORT_VOLUME));
            return 0;
        }

        List<MultiblockExportService.SnapshotEntry> snapshot = snapshot(level, controller, min, max);
        var server = level.getServer();
        var gameDir = server.getServerDirectory();
        var face = selection.controllerFace();
        ctx.getSource().sendSuccess(() -> Component.translatable("command.mmcr.export.started", volume), false);

        MultiblockExportService.executor().submit(() -> {
            try {
                var path = MultiblockExportService.writeExport(gameDir, LocalDateTime.now(), snapshot, face);
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

    private static ItemStack findSingleDetector(ServerPlayer player) {
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        boolean mainDetector = main.is(ModItems.MULTIBLOCK_DETECTOR.get());
        boolean offDetector = off.is(ModItems.MULTIBLOCK_DETECTOR.get());
        if (mainDetector == offDetector) return null;
        return mainDetector ? main : off;
    }

    private static List<MultiblockExportService.SnapshotEntry> snapshot(ServerLevel level, BlockPos controller,
                                                                        BlockPos min, BlockPos max) {
        List<MultiblockExportService.SnapshotEntry> entries = new ArrayList<>();
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    BlockPos worldPos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(worldPos);
                    entries.add(new MultiblockExportService.SnapshotEntry(
                            worldPos.subtract(controller),
                            BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                            state.isAir()));
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

    private static int volume(BlockPos min, BlockPos max) {
        return (max.getX() - min.getX() + 1)
                * (max.getY() - min.getY() + 1)
                * (max.getZ() - min.getZ() + 1);
    }
}
