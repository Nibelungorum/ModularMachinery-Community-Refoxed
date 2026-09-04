package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.api.data.DataStorage;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenText;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.api.publicapi.controller.JadeText;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.List;

/**
 * Server-authoritative context supplied to machine behavior callbacks.
 *
 * @author howxu <dev@howxu.cn>
 */
public class MachineBehaviorContext {
    private final MachineControllerBlockEntity controller;
    private final ServerLevel level;
    private final BlockPos controllerPos;
    private final Identifier machineId;
    private final long gameTime;
    private final ControllerScreenText screenText;
    private final @Nullable DataStorage dataStorage;
    private final MachineIoView ioView;
    private final List<ItemStack> upgradeItems;
    private final JadeText jadeText;

    public MachineBehaviorContext(MachineControllerBlockEntity controller, ServerLevel level,
                                  BlockPos controllerPos, Identifier machineId, long gameTime,
                                  ControllerScreenText screenText) {
        this(controller, level, controllerPos, machineId, gameTime, screenText, null, emptyIoView(), List.of(),
                JadeText.noop());
    }

    public MachineBehaviorContext(MachineControllerBlockEntity controller, ServerLevel level,
                                  BlockPos controllerPos, Identifier machineId, long gameTime,
                                  ControllerScreenText screenText, @Nullable DataStorage dataStorage) {
        this(controller, level, controllerPos, machineId, gameTime, screenText, dataStorage, emptyIoView(), List.of(),
                JadeText.noop());
    }

    public MachineBehaviorContext(MachineControllerBlockEntity controller, ServerLevel level,
                                  BlockPos controllerPos, Identifier machineId, long gameTime,
                                  ControllerScreenText screenText, @Nullable DataStorage dataStorage,
                                  MachineIoView ioView) {
        this(controller, level, controllerPos, machineId, gameTime, screenText, dataStorage, ioView, List.of(),
                JadeText.noop());
    }

    public MachineBehaviorContext(MachineControllerBlockEntity controller, ServerLevel level,
                                  BlockPos controllerPos, Identifier machineId, long gameTime,
                                  ControllerScreenText screenText, @Nullable DataStorage dataStorage,
                                  MachineIoView ioView, List<ItemStack> upgradeItems) {
        this(controller, level, controllerPos, machineId, gameTime, screenText, dataStorage, ioView, upgradeItems,
                JadeText.noop());
    }

    public MachineBehaviorContext(MachineControllerBlockEntity controller, ServerLevel level,
                                  BlockPos controllerPos, Identifier machineId, long gameTime,
                                  ControllerScreenText screenText, @Nullable DataStorage dataStorage,
                                  MachineIoView ioView, List<ItemStack> upgradeItems, JadeText jadeText) {
        this.controller = controller;
        this.level = level;
        this.controllerPos = Objects.requireNonNull(controllerPos, "controllerPos").immutable();
        this.machineId = Objects.requireNonNull(machineId, "machineId");
        this.gameTime = gameTime;
        this.screenText = Objects.requireNonNull(screenText, "screenText");
        this.dataStorage = dataStorage;
        this.ioView = Objects.requireNonNull(ioView, "ioView");
        this.upgradeItems = copyStacks(upgradeItems);
        this.jadeText = Objects.requireNonNull(jadeText, "jadeText");
    }

    public MachineControllerBlockEntity controller() {
        return controller;
    }

    public ServerLevel level() {
        return level;
    }

    public BlockPos controllerPos() {
        return controllerPos;
    }

    public Identifier machineId() {
        return machineId;
    }

    public long gameTime() {
        return gameTime;
    }

    public boolean isDue(long period) {
        if (period <= 0) throw new IllegalArgumentException("period must be positive");
        return Math.floorMod(gameTime, period) == 0;
    }

    public ControllerScreenText screenText() {
        return screenText;
    }

    public @Nullable DataStorage dataStorage() {
        return dataStorage;
    }

    public MachineIoView ioView() {
        return ioView;
    }

    public List<ItemStack> upgradeItems() {
        return copyStacks(upgradeItems);
    }

    public JadeText jadeText() {
        return jadeText;
    }

    public long countStructureBlocks(Block block) {
        Objects.requireNonNull(block, "block");
        return controller == null ? 0L : controller.countStructureBlocks(block);
    }

    /**
     * Counts blocks of the given registry name in the currently formed structure.
     *
     * @param blockId block registry name
     * @return number of matching blocks, or {@code 0} when the structure is unformed
     */
    public long countStructureBlocks(String blockId) {
        if (blockId == null || blockId.isBlank()) throw new IllegalArgumentException("blockId must not be blank");
        Identifier id;
        try {
            id = Identifier.parse(blockId);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid block id: " + blockId, exception);
        }
        if (!BuiltInRegistries.BLOCK.containsKey(id)) throw new IllegalArgumentException("Unknown block: " + blockId);
        return countStructureBlocks(BuiltInRegistries.BLOCK.getValue(id));
    }

    static MachineBehaviorContext empty(Identifier machineId) {
        return new MachineBehaviorContext(null, null, BlockPos.ZERO, machineId, 0L, EMPTY_SCREEN_TEXT);
    }

    private static MachineIoView emptyIoView() {
        return new MachineIoView(new CapabilitySnapshot(List.of()));
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) return List.of();
        return List.copyOf(stacks.stream()
                .map(stack -> Objects.requireNonNull(stack, "upgrade item").copy()).toList());
    }

    private static final ControllerScreenText EMPTY_SCREEN_TEXT = new ControllerScreenText() {
        @Override
        public void append(ControllerScreenTextScope scope, Identifier lineId, Component text) {
        }

        @Override
        public void appendAfter(ControllerScreenTextScope scope, Identifier lineId, Identifier afterLineId, Component text) {
        }

        @Override
        public void replace(Identifier lineId, Component text) {
        }

        @Override
        public void remove(ControllerScreenTextScope scope, Identifier lineId) {
        }

        @Override
        public void clear(ControllerScreenTextScope scope) {
        }
    };
}
