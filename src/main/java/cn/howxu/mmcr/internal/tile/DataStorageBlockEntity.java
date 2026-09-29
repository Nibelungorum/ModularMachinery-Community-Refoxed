package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.api.data.DataStorage;
import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.api.data.DataValueType;
import cn.howxu.mmcr.registry.ModBlockEntities;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
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
    private DataStorage storage = new DataStorage(this::onStorageChanged);
    private @Nullable BlockPos controllerPosition;
    private @Nullable ResourceLocation controllerMachine;
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

    public boolean claimController(BlockPos controllerPos, ResourceLocation machineId) {
        if (controllerPos == null || machineId == null) return false;
        if (controllerPosition != null && !controllerPosition.equals(controllerPos)) return false;
        if (controllerMachine != null && !controllerMachine.equals(machineId)) return false;
        boolean changed = controllerPosition == null || controllerMachine == null;
        controllerPosition = controllerPos.immutable();
        controllerMachine = machineId;
        if (changed) {
            linkControllerAppearanceSource(controllerPosition, null);
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
        if (Math.floorMod(linkCheckCounter++ + worldPosition.asLong(),
                ServerConfig.linkDataStorageCheckIntervalTicks()) != 0) return;
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
    protected void saveAdditional(CompoundTag output, HolderLookup.Provider registries) {
        super.saveAdditional(output, registries);
        if (controllerPosition != null) {
            output.putBoolean(HAS_CONTROLLER_KEY, true);
            output.putInt(CONTROLLER_X_KEY, controllerPosition.getX());
            output.putInt(CONTROLLER_Y_KEY, controllerPosition.getY());
            output.putInt(CONTROLLER_Z_KEY, controllerPosition.getZ());
            if (controllerMachine != null) output.putString(CONTROLLER_MACHINE_KEY, controllerMachine.toString());
        }
        ListTag entries = new ListTag();
        storage.values().forEach((key, value) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString(KEY_KEY, key);
            entry.putString(TYPE_KEY, value.type().name());
            writeValue(entry, value);
            entries.add(entry);
        });
        output.put(VALUES_KEY, entries);
    }

    @Override
    protected void loadAdditional(CompoundTag input, HolderLookup.Provider registries) {
        super.loadAdditional(input, registries);
        loading = true;
        try {
            storage = new DataStorage(this::onStorageChanged);
            controllerPosition = null;
            controllerMachine = null;
            if (input.getBoolean(HAS_CONTROLLER_KEY)) {
                controllerPosition = new BlockPos(input.getInt(CONTROLLER_X_KEY),
                        input.getInt(CONTROLLER_Y_KEY), input.getInt(CONTROLLER_Z_KEY));
                String machine = input.getString(CONTROLLER_MACHINE_KEY);
                if (!machine.isBlank()) controllerMachine = ResourceLocation.parse(machine);
            }
            ListTag entries = input.getList(VALUES_KEY, Tag.TAG_COMPOUND);
            for (int index = 0; index < entries.size(); index++) {
                CompoundTag entry = entries.getCompound(index);
                try {
                    String key = entry.getString(KEY_KEY);
                    DataValue value = readValue(entry, DataValueType.valueOf(entry.getString(TYPE_KEY)));
                    if (!key.isBlank() && value != null) storage.set(key, value);
                } catch (RuntimeException ignored) {
                    // Malformed persisted entries must not prevent the block entity from loading.
                }
            }
        } finally {
            loading = false;
        }
    }

    private void onStorageChanged(Map<String, DataValue> ignored) {
        if (loading) return;
        setChanged();
        storageSyncPending = true;
        if (level != null && controllerPosition != null
                && level.getBlockEntity(controllerPosition) instanceof MachineControllerBlockEntity controller) {
            controller.onDataStorageChanged(storage);
        }
    }

    private static void writeValue(CompoundTag output, DataValue value) {
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
                ListTag entries = new ListTag();
                for (DataValue element : value.asList().orElseThrow()) {
                    CompoundTag entry = new CompoundTag();
                    entry.putString(TYPE_KEY, element.type().name());
                    writeValue(entry, element);
                    entries.add(entry);
                }
                output.put(LIST_VALUES_KEY, entries);
            }
            case MAP -> {
                ListTag entries = new ListTag();
                value.asMap().orElseThrow().forEach((key, element) -> {
                    CompoundTag entry = new CompoundTag();
                    entry.putString(KEY_KEY, key);
                    entry.putString(TYPE_KEY, element.type().name());
                    writeValue(entry, element);
                    entries.add(entry);
                });
                output.put(MAP_ENTRIES_KEY, entries);
            }
        }
    }

    private static @Nullable DataValue readValue(CompoundTag input, DataValueType type) {
        try {
            return switch (type) {
            case BOOLEAN -> input.contains(VALUE_KEY) ? DataValue.of(input.getBoolean(VALUE_KEY)) : null;
            case STRING -> input.contains(VALUE_KEY) ? DataValue.of(input.getString(VALUE_KEY)) : null;
            case BYTE -> {
                int value = input.contains(VALUE_KEY) ? input.getInt(VALUE_KEY) : Integer.MIN_VALUE;
                yield value < Byte.MIN_VALUE || value > Byte.MAX_VALUE ? null : DataValue.of((byte) value);
            }
            case SHORT -> {
                int value = input.contains(VALUE_KEY) ? input.getInt(VALUE_KEY) : Integer.MIN_VALUE;
                yield value < Short.MIN_VALUE || value > Short.MAX_VALUE ? null : DataValue.of((short) value);
            }
            case INT -> input.contains(VALUE_KEY) ? DataValue.of(input.getInt(VALUE_KEY)) : null;
            case LONG -> input.contains(VALUE_KEY) ? DataValue.of(input.getLong(VALUE_KEY)) : null;
            case FLOAT -> input.contains(VALUE_KEY) ? DataValue.of(input.getFloat(VALUE_KEY)) : null;
            case DOUBLE -> input.contains(VALUE_KEY) ? DataValue.of(input.getDouble(VALUE_KEY)) : null;
            case BIG_INTEGER -> DataValue.of(new BigInteger(input.getString(VALUE_KEY)));
            case BIG_DECIMAL -> DataValue.of(new BigDecimal(input.getString(VALUE_KEY)));
            case LIST -> {
                if (!input.contains(LIST_VALUES_KEY)) yield null;
                ListTag entries = input.getList(LIST_VALUES_KEY, Tag.TAG_COMPOUND);
                var values = new ArrayList<DataValue>();
                for (int index = 0; index < entries.size(); index++) {
                    CompoundTag entry = entries.getCompound(index);
                    try {
                        DataValue value = readValue(entry,
                                DataValueType.valueOf(entry.getString(TYPE_KEY)));
                        if (value != null) values.add(value);
                    } catch (RuntimeException ignored) {
                        // Skip malformed nested entries without losing valid siblings.
                    }
                }
                yield DataValue.list(values);
            }
            case MAP -> {
                if (!input.contains(MAP_ENTRIES_KEY)) yield null;
                ListTag entries = input.getList(MAP_ENTRIES_KEY, Tag.TAG_COMPOUND);
                var values = new LinkedHashMap<String, DataValue>();
                for (int index = 0; index < entries.size(); index++) {
                    CompoundTag entry = entries.getCompound(index);
                    try {
                        String key = entry.getString(KEY_KEY);
                        if (key.isBlank()) continue;
                        DataValue value = readValue(entry,
                                DataValueType.valueOf(entry.getString(TYPE_KEY)));
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
