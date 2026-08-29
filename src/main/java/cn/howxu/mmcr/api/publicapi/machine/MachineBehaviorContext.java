package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.api.data.DataStorage;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenText;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private final Map<BlockPos, DataStorage> dataStorages;
    private final MachineIoView ioView;

    public MachineBehaviorContext(MachineControllerBlockEntity controller, ServerLevel level,
                                  BlockPos controllerPos, Identifier machineId, long gameTime,
                                  ControllerScreenText screenText) {
        this(controller, level, controllerPos, machineId, gameTime, screenText, Map.of(), emptyIoView());
    }

    public MachineBehaviorContext(MachineControllerBlockEntity controller, ServerLevel level,
                                  BlockPos controllerPos, Identifier machineId, long gameTime,
                                  ControllerScreenText screenText, Map<BlockPos, DataStorage> dataStorages) {
        this(controller, level, controllerPos, machineId, gameTime, screenText, dataStorages, emptyIoView());
    }

    public MachineBehaviorContext(MachineControllerBlockEntity controller, ServerLevel level,
                                  BlockPos controllerPos, Identifier machineId, long gameTime,
                                  ControllerScreenText screenText, Map<BlockPos, DataStorage> dataStorages,
                                  MachineIoView ioView) {
        this.controller = controller;
        this.level = level;
        this.controllerPos = Objects.requireNonNull(controllerPos, "controllerPos").immutable();
        this.machineId = Objects.requireNonNull(machineId, "machineId");
        this.gameTime = gameTime;
        this.screenText = Objects.requireNonNull(screenText, "screenText");
        Map<BlockPos, DataStorage> copy = new LinkedHashMap<>();
        if (dataStorages != null) {
            dataStorages.forEach((pos, storage) -> copy.put(Objects.requireNonNull(pos, "data storage position").immutable(),
                    Objects.requireNonNull(storage, "data storage")));
        }
        this.dataStorages = Map.copyOf(copy);
        this.ioView = Objects.requireNonNull(ioView, "ioView");
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

    public Map<BlockPos, DataStorage> dataStorages() {
        return dataStorages;
    }

    public Optional<DataStorage> dataStorage(BlockPos pos) {
        return pos == null ? Optional.empty() : Optional.ofNullable(dataStorages.get(pos));
    }

    public MachineIoView ioView() {
        return ioView;
    }

    static MachineBehaviorContext empty(Identifier machineId) {
        return new MachineBehaviorContext(null, null, BlockPos.ZERO, machineId, 0L, EMPTY_SCREEN_TEXT);
    }

    private static MachineIoView emptyIoView() {
        return new MachineIoView(new CapabilitySnapshot(List.of()));
    }

    private static final ControllerScreenText EMPTY_SCREEN_TEXT = new ControllerScreenText() {
        @Override
        public void append(ControllerScreenTextScope scope, Identifier lineId, Component text) {
        }

        @Override
        public void appendAfter(ControllerScreenTextScope scope, Identifier lineId, Identifier afterLineId, Component text) {
        }

        @Override
        public void remove(ControllerScreenTextScope scope, Identifier lineId) {
        }

        @Override
        public void clear(ControllerScreenTextScope scope) {
        }
    };
}
