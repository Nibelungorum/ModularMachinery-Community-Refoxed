package cn.howxu.mmcr.internal.item;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.compat.appliedenergistics2.AE2Bridge;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.assembly.MultiblockAssemblyService;
import cn.howxu.mmcr.internal.assembly.StructureItemSink;
import cn.howxu.mmcr.internal.assembly.StructureItemStorage;
import cn.howxu.mmcr.internal.assembly.StructureItemStorageResolver;
import cn.howxu.mmcr.internal.network.PktTerminalStatePayload;
import cn.howxu.mmcr.internal.runtime.StructureSnapshot;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModDataComponents;
import cn.howxu.mmcr.registry.ModItems;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

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
        TerminalData data = TerminalData.from(stack);
        controllerAt(player, data.controller()).ifPresent(previous -> previous.clearStructurePreview(player));
        setData(stack, normalize(controller, data.withController(target).withPreview(false, Integer.MAX_VALUE)));
        return accepted(player, stack, "message.mmcr.terminal.controller_bound");
    }

    public static Result bindContainer(ServerPlayer player, ItemStack stack, GlobalPos target) {
        if (!isHeldTerminal(player, stack)) return rejected(player, stack, "message.mmcr.terminal.not_held");
        TerminalData data = TerminalData.from(stack);
        if (data.inventoryMode() != TerminalInventoryMode.CONTAINER || !canAccess(player, target)
                || AE2Bridge.get().isWirelessAccessPoint(player, target)) {
            return rejected(player, stack, "message.mmcr.terminal.invalid_container");
        }
        TerminalData candidate = data.withContainer(target);
        if (StructureItemStorageResolver.resolve(player, candidate).isEmpty()) {
            return rejected(player, stack, "message.mmcr.terminal.invalid_container");
        }
        setData(stack, candidate);
        return accepted(player, stack, "message.mmcr.terminal.container_bound");
    }

    public static Result bindAe2AccessPoint(ServerPlayer player, ItemStack stack, GlobalPos target) {
        if (!isHeldTerminal(player, stack)) return rejected(player, stack, "message.mmcr.terminal.not_held");
        TerminalData data = TerminalData.from(stack);
        if (data.inventoryMode() != TerminalInventoryMode.AE2
                || AE2Bridge.get().resolveTerminalNetworkStorage(player, target).isEmpty()) {
            return rejected(player, stack, "message.mmcr.terminal.invalid_ae2_access_point");
        }
        setData(stack, data.withAe2AccessPoint(target));
        return accepted(player, stack, "message.mmcr.terminal.ae2_access_point_bound");
    }

    public static void clear(ItemStack stack) {
        stack.set(ModDataComponents.TERMINAL_DATA.get(), TerminalData.from(stack).clear());
    }

    public static Result clear(ServerPlayer player, ItemStack stack) {
        if (!isHeldTerminal(player, stack)) return rejected(player, stack, "message.mmcr.terminal.not_held");
        TerminalData data = TerminalData.from(stack);
        controllerAt(player, data.controller()).ifPresent(controller -> controller.clearStructurePreview(player));
        clear(stack);
        return accepted(player, stack, "message.mmcr.terminal.cleared");
    }

    public static Result execute(ServerPlayer player, ItemStack stack, TerminalAction action, int value,
                                 ResourceLocation firstId, ResourceLocation secondId) {
        if (!isHeldTerminal(player, stack)) return rejected(player, stack, "message.mmcr.terminal.not_held");
        if (action == null) return rejected(player, stack, "message.mmcr.terminal.invalid_action");
        TerminalData data = TerminalData.from(stack);
        MachineControllerBlockEntity controller = controllerAt(player, data.controller()).orElse(null);
        switch (action) {
            case SET_INVENTORY_MODE -> {
                TerminalInventoryMode[] modes = TerminalInventoryMode.values();
                if (value < 0 || value >= modes.length) return rejected(player, stack, "message.mmcr.terminal.invalid_mode");
                if (modes[value] == TerminalInventoryMode.AE2 && !AE2Bridge.get().available()) {
                    return rejected(player, stack, "message.mmcr.terminal.invalid_mode");
                }
                setData(stack, data.withInventoryMode(modes[value]));
                return accepted(player, stack, "message.mmcr.terminal.updated");
            }
            case REQUEST_STATE -> {
                sendState(player, stack, "");
                return new Result(true, "");
            }
            case SET_STAGE -> {
                if (controller == null || !controller.availableStructureStages().contains(value)) {
                    return rejected(player, stack, "message.mmcr.terminal.invalid_stage");
                }
                TerminalData updated = normalize(controller, data.withStage(value)
                        .withPreview(data.previewEnabled(), Integer.MAX_VALUE));
                setData(stack, updated);
                if (updated.previewEnabled()) controller.sendTerminalStructurePreview(player, updated.stage(), updated.selectedLevels());
                return accepted(player, stack, "message.mmcr.terminal.updated");
            }
            case SET_LEVEL -> {
                if (controller == null) return rejected(player, stack, "message.mmcr.terminal.no_controller");
                MachineLevel level = secondId == null ? null : MachineLevelRegistry.getLevel(secondId);
                TerminalData normalized = normalize(controller, data);
                if (level == null || !level.typeId().equals(firstId)
                        || !normalized.selectedLevels().containsKey(firstId)) {
                    return rejected(player, stack, "message.mmcr.terminal.invalid_level");
                }
                TerminalData updated = normalize(controller, normalized.withSelectedLevel(firstId, secondId)
                        .withPreview(normalized.previewEnabled(), Integer.MAX_VALUE));
                setData(stack, updated);
                if (updated.previewEnabled()) controller.sendTerminalStructurePreview(player, updated.stage(), updated.selectedLevels());
                return accepted(player, stack, "message.mmcr.terminal.updated");
            }
            case SET_PREVIEW_ENABLED -> {
                if (controller == null) return rejected(player, stack, "message.mmcr.terminal.no_controller");
                boolean enabled = value != 0;
                TerminalData updated = data.withPreview(enabled, data.previewLayer());
                setData(stack, updated);
                if (enabled) controller.sendTerminalStructurePreview(player, updated.stage(), updated.selectedLevels());
                else controller.clearStructurePreview(player);
                return accepted(player, stack, "message.mmcr.terminal.updated");
            }
            case SET_PREVIEW_LAYER -> {
                if (controller == null) return rejected(player, stack, "message.mmcr.terminal.no_controller");
                if (!previewLayerAllowed(value, currentPreviewLayers(controller, data))) {
                    return rejected(player, stack, "message.mmcr.terminal.invalid_preview_layer");
                }
                TerminalData updated = data.withPreview(data.previewEnabled(), value);
                setData(stack, updated);
                if (updated.previewEnabled()) controller.sendTerminalStructurePreview(player, updated.stage(), updated.selectedLevels());
                return accepted(player, stack, "message.mmcr.terminal.updated");
            }
            case CHECK -> {
                if (controller == null) return rejected(player, stack, "message.mmcr.terminal.no_controller");
                controller.verifyStage(player, data.stage());
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
                StructureItemSink demolitionSink = freeInventoryBuild ? ignored -> true : storage.sink();
                if (action == TerminalAction.BUILD && data.previewEnabled()) {
                    controller.clearStructurePreview(player);
                    data = data.withPreview(false, data.previewLayer());
                    setData(stack, data);
                }
                MultiblockAssemblyService.Result result = action == TerminalAction.BUILD
                        ? MultiblockAssemblyService.build(player, controller, data.stage(), storage.source(), freeInventoryBuild,
                                data.selectedLevels())
                        : MultiblockAssemblyService.demolish(player, controller, data.stage(),
                                ServerConfig.TERMINAL_MAX_DEMOLISH_BLOCKS.get(), demolitionSink);
                player.sendSystemMessage(Component.translatable(result.message().key(), result.message().args()));
                return result.interactionResult().consumesAction()
                        ? accepted(player, stack, result.message().key()) : rejected(player, stack, result.message().key());
            }
        }
        return rejected(player, stack, "message.mmcr.terminal.invalid_action");
    }

    static OptionalInt effectiveStage(List<Integer> availableStages, int requestedStage) {
        if (availableStages.contains(requestedStage)) return OptionalInt.of(requestedStage);
        return availableStages.stream().mapToInt(Integer::intValue).findFirst();
    }

    static boolean previewLayerAllowed(int layer, List<Integer> currentLayers) {
        return layer == Integer.MAX_VALUE || currentLayers.contains(layer);
    }

    private static TerminalData normalize(MachineControllerBlockEntity controller, TerminalData data) {
        List<Integer> stages = controller.availableStructureStages();
        int stage = stages.contains(data.stage()) ? data.stage() : stages.getFirst();
        List<ResourceLocation> levelTypes = controller.boundMachine().map(machine -> machine.structureStages().stream()
                .filter(structureStage -> structureStage.number() == stage)
                .flatMap(structureStage -> structureStage.levelSlots().values().stream())
                .distinct().toList()).orElse(List.of());
        LinkedHashMap<ResourceLocation, ResourceLocation> levels = new LinkedHashMap<>();
        for (LevelType type : MachineLevelRegistry.types()) {
            if (!levelTypes.contains(type.id())) continue;
            List<MachineLevel> available = MachineLevelRegistry.levelsForType(type.id()).stream()
                    .sorted(Comparator.comparingInt(MachineLevel::priority)).toList();
            if (available.isEmpty()) continue;
            ResourceLocation selected = data.selectedLevels().get(type.id());
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
        ResourceLocation selectedType = data.selectedLevelType();
        if (selectedType == null || !levels.containsKey(selectedType)) selectedType = levels.isEmpty() ? null : levels.keySet().iterator().next();
        return new TerminalData(data.controller(), data.container(), data.ae2AccessPoint(), data.inventoryMode(), selectedType,
                levels, stage, data.previewEnabled(), data.previewLayer());
    }

    private static Optional<MachineControllerBlockEntity> controllerAt(ServerPlayer player, GlobalPos target) {
        if (!canAccess(player, target)) return Optional.empty();
        ServerLevel level = player.level().getServer().getLevel(target.dimension());
        if (level == null || !level.hasChunkAt(target.pos())) return Optional.empty();
        return level.getBlockEntity(target.pos()) instanceof MachineControllerBlockEntity controller
                ? Optional.of(controller) : Optional.empty();
    }

    private static boolean canAccess(ServerPlayer player, GlobalPos target) {
        int radius = ServerConfig.terminalControllerAccessRadius();
        return player != null && target != null && player.level().dimension().equals(target.dimension())
                && player.blockPosition().distSqr(target.pos()) <= radius * radius;
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
        boolean ae2Available = AE2Bridge.get().available();
        if (!ae2Available && data.inventoryMode() == TerminalInventoryMode.AE2) {
            data = data.withInventoryMode(TerminalInventoryMode.INVENTORY);
            setData(stack, data);
        }
        MachineControllerBlockEntity controller = controllerAt(player, data.controller()).orElse(null);
        StructureSnapshot structure = controller == null ? null : controller.currentRuntimeSnapshot().structure();
        Machine machine = structure == null ? null
                : structure.machine() == null ? structure.configuredMachine() : structure.machine();
        List<Integer> stages = controller == null ? List.of() : controller.availableStructureStages();
        TerminalData stateData = controller == null ? data : normalize(controller, data);
        BlockArray pattern = previewPattern(controller, data, structure, machine, stages);
        PacketDistributor.sendToPlayer(player, new PktTerminalStatePayload(stateData,
                controller != null, StructureItemStorageResolver.resolve(player, data).isPresent(), ae2Available,
                stages,
                machine == null ? Component.translatable("gui.mmcr.terminal.no_controller")
                        : machine.displayName(),
                previewLayers(pattern, PktTerminalStatePayload.maxPreviewLayers()), statusKey));
    }

    private static List<Integer> currentPreviewLayers(MachineControllerBlockEntity controller, TerminalData data) {
        StructureSnapshot structure = controller.currentRuntimeSnapshot().structure();
        Machine machine = structure.machine() == null ? structure.configuredMachine() : structure.machine();
        List<Integer> stages = controller.availableStructureStages();
        return previewLayers(previewPattern(controller, data, structure, machine, stages),
                PktTerminalStatePayload.maxPreviewLayers());
    }

    private static BlockArray previewPattern(MachineControllerBlockEntity controller, TerminalData data,
            StructureSnapshot structure, Machine machine, List<Integer> stages) {
        if (controller == null || structure == null) return null;
        if (structure.pattern() != null) return structure.pattern();
        if (machine == null) return null;
        OptionalInt stage = effectiveStage(stages, data.stage());
        if (stage.isEmpty()) return null;
        try {
            return controller.assemblyPattern(machine, stage.getAsInt());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static List<Integer> previewLayers(BlockArray pattern, int maxLayers) {
        if (pattern == null || maxLayers <= 0) return List.of();
        return pattern.pattern().keySet().stream().map(BlockPos::getY).distinct().sorted()
                .limit(maxLayers).toList();
    }
}
