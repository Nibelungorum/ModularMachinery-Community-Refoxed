package cn.howxu.mmcr.internal.item;

import cn.howxu.mmcr.config.Config;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.internal.assembly.MultiblockAssemblyService;
import cn.howxu.mmcr.internal.assembly.StructureItemStorage;
import cn.howxu.mmcr.internal.assembly.StructureItemStorageResolver;
import cn.howxu.mmcr.internal.network.PktTerminalStatePayload;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModDataComponents;
import cn.howxu.mmcr.registry.ModItems;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/**
 * Server-authoritative terminal configuration and structure operations.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class TerminalService {
    private TerminalService() {}

    public record Result(boolean accepted, String messageKey) {}

    public static Result bindController(ServerPlayer player, ItemStack stack, GlobalPos target) {
        if (!isHeldTerminal(player, stack)) return rejected(player, stack, "message.mmcr.terminal.not_held");
        MachineControllerBlockEntity controller = controllerAt(player, target).orElse(null);
        if (controller == null) return rejected(player, stack, "message.mmcr.terminal.invalid_controller");
        setData(stack, normalize(controller, TerminalData.from(stack).withController(target)));
        return accepted(player, stack, "message.mmcr.terminal.controller_bound");
    }

    public static Result bindContainer(ServerPlayer player, ItemStack stack, GlobalPos target) {
        if (!isHeldTerminal(player, stack)) return rejected(player, stack, "message.mmcr.terminal.not_held");
        TerminalData data = TerminalData.from(stack);
        if (data.inventoryMode() != TerminalInventoryMode.CONTAINER || !canAccess(player, target)) {
            return rejected(player, stack, "message.mmcr.terminal.invalid_container");
        }
        TerminalData candidate = data.withContainer(target);
        if (StructureItemStorageResolver.resolve(player, candidate).isEmpty()) {
            return rejected(player, stack, "message.mmcr.terminal.invalid_container");
        }
        setData(stack, candidate);
        return accepted(player, stack, "message.mmcr.terminal.container_bound");
    }

    public static void clear(ItemStack stack) {
        stack.set(ModDataComponents.TERMINAL_DATA.get(), TerminalData.DEFAULT);
    }

    public static Result clear(ServerPlayer player, ItemStack stack) {
        if (!isHeldTerminal(player, stack)) return rejected(player, stack, "message.mmcr.terminal.not_held");
        TerminalData data = TerminalData.from(stack);
        controllerAt(player, data.controller()).ifPresent(controller -> controller.clearStructurePreview(player));
        clear(stack);
        return accepted(player, stack, "message.mmcr.terminal.cleared");
    }

    public static Result execute(ServerPlayer player, ItemStack stack, TerminalAction action, int value,
                                 Identifier firstId, Identifier secondId) {
        if (!isHeldTerminal(player, stack)) return rejected(player, stack, "message.mmcr.terminal.not_held");
        if (action == null) return rejected(player, stack, "message.mmcr.terminal.invalid_action");
        TerminalData data = TerminalData.from(stack);
        MachineControllerBlockEntity controller = controllerAt(player, data.controller()).orElse(null);
        switch (action) {
            case SET_INVENTORY_MODE -> {
                TerminalInventoryMode[] modes = TerminalInventoryMode.values();
                if (value < 0 || value >= modes.length) return rejected(player, stack, "message.mmcr.terminal.invalid_mode");
                setData(stack, data.withInventoryMode(modes[value]));
                return accepted(player, stack, "message.mmcr.terminal.updated");
            }
            case SET_STAGE -> {
                if (controller == null || !controller.availableStructureStages().contains(value)) {
                    return rejected(player, stack, "message.mmcr.terminal.invalid_stage");
                }
                setData(stack, normalize(controller, data.withStage(value)));
                return accepted(player, stack, "message.mmcr.terminal.updated");
            }
            case SET_LEVEL -> {
                if (controller == null) return rejected(player, stack, "message.mmcr.terminal.no_controller");
                MachineLevel level = secondId == null ? null : MachineLevelRegistry.getLevel(secondId);
                if (firstId == null || level == null || !level.typeId().equals(firstId)) {
                    return rejected(player, stack, "message.mmcr.terminal.invalid_level");
                }
                setData(stack, normalize(controller, data.withSelectedLevel(firstId, secondId)));
                return accepted(player, stack, "message.mmcr.terminal.updated");
            }
            case SET_PREVIEW_ENABLED -> {
                if (controller == null) return rejected(player, stack, "message.mmcr.terminal.no_controller");
                boolean enabled = value != 0;
                setData(stack, data.withPreview(enabled, data.previewLayer()));
                if (enabled) controller.sendStructurePreview(player);
                else controller.clearStructurePreview(player);
                return accepted(player, stack, "message.mmcr.terminal.updated");
            }
            case SET_PREVIEW_LAYER -> {
                if (controller == null) return rejected(player, stack, "message.mmcr.terminal.no_controller");
                setData(stack, data.withPreview(data.previewEnabled(), value));
                return accepted(player, stack, "message.mmcr.terminal.updated");
            }
            case CHECK -> {
                if (controller == null) return rejected(player, stack, "message.mmcr.terminal.no_controller");
                controller.requestImmediateStructureCheck(player);
                return accepted(player, stack, "message.mmcr.terminal.check_requested");
            }
            case BUILD, DEMOLISH -> {
                if (controller == null) return rejected(player, stack, "message.mmcr.terminal.no_controller");
                TerminalData normalized = normalize(controller, data);
                if (!normalized.equals(data)) setData(stack, normalized);
                data = normalized;
                StructureItemStorage storage = StructureItemStorageResolver.resolve(player, data).orElse(null);
                if (storage == null) return rejected(player, stack, "message.mmcr.terminal.storage_unavailable");
                boolean freeInventoryBuild = player.isCreative() && data.inventoryMode() == TerminalInventoryMode.INVENTORY;
                MultiblockAssemblyService.Result result = action == TerminalAction.BUILD
                        ? MultiblockAssemblyService.build(player, controller, data.stage(), storage.source(), freeInventoryBuild)
                        : MultiblockAssemblyService.demolish(player, controller, data.stage(),
                                Config.TERMINAL_MAX_DEMOLISH_BLOCKS.get(), storage.sink());
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(result.message().key(), result.message().args()));
                return result.interactionResult().consumesAction()
                        ? accepted(player, stack, result.message().key()) : rejected(player, stack, result.message().key());
            }
        }
        return rejected(player, stack, "message.mmcr.terminal.invalid_action");
    }

    private static TerminalData normalize(MachineControllerBlockEntity controller, TerminalData data) {
        LinkedHashMap<Identifier, Identifier> levels = new LinkedHashMap<>();
        for (LevelType type : MachineLevelRegistry.types()) {
            List<MachineLevel> available = MachineLevelRegistry.levelsForType(type.id()).stream()
                    .sorted(java.util.Comparator.comparingInt(MachineLevel::priority)).toList();
            if (available.isEmpty()) continue;
            Identifier selected = data.selectedLevels().get(type.id());
            boolean validSelection = false;
            for (MachineLevel level : available) {
                if (level.id().equals(selected)) {
                    validSelection = true;
                    break;
                }
            }
            if (!validSelection) selected = available.getFirst().id();
            levels.put(type.id(), selected);
        }
        List<Integer> stages = controller.availableStructureStages();
        int stage = stages.contains(data.stage()) ? data.stage() : stages.getFirst();
        Identifier selectedType = data.selectedLevelType();
        if (selectedType == null || !levels.containsKey(selectedType)) selectedType = levels.isEmpty() ? null : levels.keySet().iterator().next();
        return new TerminalData(data.controller(), data.container(), data.inventoryMode(), selectedType, levels, stage,
                data.previewEnabled(), data.previewLayer());
    }

    private static Optional<MachineControllerBlockEntity> controllerAt(ServerPlayer player, GlobalPos target) {
        if (!canAccess(player, target)) return Optional.empty();
        ServerLevel level = player.level().getServer().getLevel(target.dimension());
        if (level == null || !level.hasChunkAt(target.pos())) return Optional.empty();
        return level.getBlockEntity(target.pos()) instanceof MachineControllerBlockEntity controller
                ? Optional.of(controller) : Optional.empty();
    }

    private static boolean canAccess(ServerPlayer player, GlobalPos target) {
        return player != null && target != null && player.level().dimension().equals(target.dimension())
                && player.blockPosition().distSqr(target.pos()) <= 64;
    }

    private static boolean isHeldTerminal(ServerPlayer player, ItemStack stack) {
        return player != null && stack != null && player.getMainHandItem() == stack && stack.is(ModItems.TERMINAL.get());
    }

    private static void setData(ItemStack stack, TerminalData data) {
        stack.set(ModDataComponents.TERMINAL_DATA.get(), data);
    }

    private static Result accepted(ServerPlayer player, ItemStack stack, String messageKey) {
        sendState(player, stack, messageKey);
        return new Result(true, messageKey);
    }

    private static Result rejected(ServerPlayer player, ItemStack stack, String messageKey) {
        sendState(player, stack, messageKey);
        return new Result(false, messageKey);
    }

    private static void sendState(ServerPlayer player, ItemStack stack, String statusKey) {
        if (player == null || player.connection == null || stack == null || !stack.is(ModItems.TERMINAL.get())) return;
        TerminalData data = TerminalData.from(stack);
        PacketDistributor.sendToPlayer(player, new PktTerminalStatePayload(data,
                controllerAt(player, data.controller()).isPresent(), StructureItemStorageResolver.resolve(player, data).isPresent(), statusKey));
    }
}
