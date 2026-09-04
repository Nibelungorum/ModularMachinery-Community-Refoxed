package cn.howxu.mmcr.internal.tile;

import com.mojang.serialization.Codec;
import cn.howxu.mmcr.api.data.DataStorage;
import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.api.data.DataValueType;
import cn.howxu.mmcr.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Optional;

/** Independent typed data storage linked to at most one machine controller.
 * @author howxu <dev@howxu.cn>
 */
public final class DataStorageBlockEntity extends LinkedAppearanceBlockEntity {
    private static final String VALUES_KEY = "Values";
    private static final String KEY_KEY = "Key";
    private static final String TYPE_KEY = "Type";
    private static final String VALUE_KEY = "Value";
    private static final String LIST_VALUES_KEY = "Values";
    private static final String MAP_ENTRIES_KEY = "Entries";
    private static final String HAS_CONTROLLER_KEY = "HasController";
    private static final String CONTROLLER_X_KEY = "ControllerX";
    private static final String CONTROLLER_Y_KEY = "ControllerY";
    private static final String CONTROLLER_Z_KEY = "ControllerZ";
    private static final String CONTROLLER_MACHINE_KEY = "ControllerMachine";
    private static final int LINK_CHECK_INTERVAL_TICKS = 40;

    private DataStorage storage = new DataStorage(this::onStorageChanged);
    private @Nullable BlockPos controllerPosition;
    private @Nullable Identifier controllerMachine;
    private boolean loading;
    private boolean storageSyncPending;
    private int linkCheckCounter;

    public DataStorageBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DATA_STORAGE.get(), pos, state);
    }

    public Optional<BlockPos> controllerPosition() {
        return Optional.ofNullable(controllerPosition);
    }

    public DataStorage storage() {
        return storage;
    }

    public boolean claimController(BlockPos controllerPos, Identifier machineId) {
        if (controllerPos == null || machineId == null) return false;
        if (controllerPosition != null && !controllerPosition.equals(controllerPos)) return false;
        if (controllerMachine != null && !controllerMachine.equals(machineId)) return false;
        boolean changed = controllerPosition == null || controllerMachine == null;
        controllerPosition = controllerPos.immutable();
        controllerMachine = machineId;
        if (changed) {
            linkControllerAppearance(controllerPosition, null);
            setChanged();
        }
        return true;
    }

    public boolean releaseController(BlockPos controllerPos) {
        if (controllerPos == null || !controllerPos.equals(controllerPosition)) return false;
        controllerPosition = null;
        controllerMachine = null;
        unlinkControllerAppearance(controllerPos);
        setChanged();
        return true;
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) return;
        if (storageSyncPending) {
            storageSyncPending = false;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        BlockPos currentControllerPosition = controllerPosition;
        if (currentControllerPosition == null) return;
        if (Math.floorMod(linkCheckCounter++ + worldPosition.asLong(), LINK_CHECK_INTERVAL_TICKS) != 0) return;
        if (!level.hasChunkAt(currentControllerPosition)) return;
        if (!(level.getBlockEntity(currentControllerPosition) instanceof MachineControllerBlockEntity controller)) {
            releaseController(currentControllerPosition);
        } else {
            var snapshot = controller.runtimeSnapshot();
            if (!snapshot.structure().formed() || !snapshot.linkedPortPositions().contains(worldPosition)) {
                releaseController(currentControllerPosition);
            }
        }
        maintainControllerLink();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (controllerPosition != null) {
            output.putBoolean(HAS_CONTROLLER_KEY, true);
            output.putInt(CONTROLLER_X_KEY, controllerPosition.getX());
            output.putInt(CONTROLLER_Y_KEY, controllerPosition.getY());
            output.putInt(CONTROLLER_Z_KEY, controllerPosition.getZ());
            if (controllerMachine != null) output.putString(CONTROLLER_MACHINE_KEY, controllerMachine.toString());
        }
        var entries = output.childrenList(VALUES_KEY);
        storage.values().forEach((key, value) -> {
            ValueOutput entry = entries.addChild();
            entry.putString(KEY_KEY, key);
            entry.putString(TYPE_KEY, value.type().name());
            writeValue(entry, value);
        });
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        loading = true;
        try {
            storage = new DataStorage(this::onStorageChanged);
            controllerPosition = null;
            controllerMachine = null;
            if (input.getBooleanOr(HAS_CONTROLLER_KEY, false)) {
                controllerPosition = new BlockPos(input.getIntOr(CONTROLLER_X_KEY, 0),
                        input.getIntOr(CONTROLLER_Y_KEY, 0), input.getIntOr(CONTROLLER_Z_KEY, 0));
                String machine = input.getStringOr(CONTROLLER_MACHINE_KEY, "");
                if (!machine.isBlank()) controllerMachine = Identifier.parse(machine);
            }
            for (ValueInput entry : input.childrenListOrEmpty(VALUES_KEY)) {
                try {
                    String key = entry.getStringOr(KEY_KEY, "");
                    DataValue value = readValue(entry, DataValueType.valueOf(entry.getStringOr(TYPE_KEY, "")));
                    if (!key.isBlank() && value != null) storage.set(key, value);
                } catch (RuntimeException ignored) {
                    // Malformed persisted entries must not prevent the block entity from loading.
                }
            }
        } finally {
            loading = false;
        }
    }

    private void onStorageChanged(java.util.Map<String, DataValue> ignored) {
        if (loading) return;
        setChanged();
        storageSyncPending = true;
    }

    private static void writeValue(ValueOutput output, DataValue value) {
        switch (value.type()) {
            case BOOLEAN -> output.putBoolean(VALUE_KEY, value.booleanValue());
            case STRING -> output.putString(VALUE_KEY, value.stringValue());
            case BYTE -> output.putInt(VALUE_KEY, value.byteValue());
            case SHORT -> output.putInt(VALUE_KEY, value.shortValue());
            case INT -> output.putInt(VALUE_KEY, value.intValue());
            case LONG -> output.putLong(VALUE_KEY, value.longValue());
            case FLOAT -> output.putFloat(VALUE_KEY, value.floatValue());
            case DOUBLE -> output.putDouble(VALUE_KEY, value.doubleValue());
            case BIG_INTEGER -> output.putString(VALUE_KEY, value.bigIntegerValue().toString());
            case BIG_DECIMAL -> output.putString(VALUE_KEY, value.bigDecimalValue().toString());
            case LIST -> {
                var entries = output.childrenList(LIST_VALUES_KEY);
                for (DataValue element : value.asList().orElseThrow()) {
                    ValueOutput entry = entries.addChild();
                    entry.putString(TYPE_KEY, element.type().name());
                    writeValue(entry, element);
                }
            }
            case MAP -> {
                var entries = output.childrenList(MAP_ENTRIES_KEY);
                value.asMap().orElseThrow().forEach((key, element) -> {
                    ValueOutput entry = entries.addChild();
                    entry.putString(KEY_KEY, key);
                    entry.putString(TYPE_KEY, element.type().name());
                    writeValue(entry, element);
                });
            }
        }
    }

    private static @Nullable DataValue readValue(ValueInput input, DataValueType type) {
        try {
            return switch (type) {
            case BOOLEAN -> input.read(VALUE_KEY, Codec.BOOL).map(DataValue::of).orElse(null);
            case STRING -> input.getString(VALUE_KEY).map(DataValue::of).orElse(null);
            case BYTE -> {
                int value = input.getIntOr(VALUE_KEY, Integer.MIN_VALUE);
                yield value < Byte.MIN_VALUE || value > Byte.MAX_VALUE ? null : DataValue.of((byte) value);
            }
            case SHORT -> {
                int value = input.getIntOr(VALUE_KEY, Integer.MIN_VALUE);
                yield value < Short.MIN_VALUE || value > Short.MAX_VALUE ? null : DataValue.of((short) value);
            }
            case INT -> input.getInt(VALUE_KEY).map(DataValue::of).orElse(null);
            case LONG -> input.getLong(VALUE_KEY).map(DataValue::of).orElse(null);
            case FLOAT -> input.read(VALUE_KEY, Codec.FLOAT).map(DataValue::of).orElse(null);
            case DOUBLE -> input.read(VALUE_KEY, Codec.DOUBLE).map(DataValue::of).orElse(null);
            case BIG_INTEGER -> DataValue.of(new BigInteger(input.getStringOr(VALUE_KEY, "")));
            case BIG_DECIMAL -> DataValue.of(new BigDecimal(input.getStringOr(VALUE_KEY, "")));
            case LIST -> {
                var entries = input.childrenList(LIST_VALUES_KEY).orElse(null);
                if (entries == null) yield null;
                var values = new ArrayList<DataValue>();
                for (ValueInput entry : entries) {
                    try {
                        DataValue value = readValue(entry,
                                DataValueType.valueOf(entry.getStringOr(TYPE_KEY, "")));
                        if (value != null) values.add(value);
                    } catch (RuntimeException ignored) {
                        // Skip malformed nested entries without losing valid siblings.
                    }
                }
                yield DataValue.list(values);
            }
            case MAP -> {
                var entries = input.childrenList(MAP_ENTRIES_KEY).orElse(null);
                if (entries == null) yield null;
                var values = new LinkedHashMap<String, DataValue>();
                for (ValueInput entry : entries) {
                    try {
                        String key = entry.getStringOr(KEY_KEY, "");
                        if (key.isBlank()) continue;
                        DataValue value = readValue(entry,
                                DataValueType.valueOf(entry.getStringOr(TYPE_KEY, "")));
                        if (value != null) values.put(key, value);
                    } catch (RuntimeException ignored) {
                        // Skip malformed nested entries without losing valid siblings.
                    }
                }
                yield DataValue.map(values);
            }
            };
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
