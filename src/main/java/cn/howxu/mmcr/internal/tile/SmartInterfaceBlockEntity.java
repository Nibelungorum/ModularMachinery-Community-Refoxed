package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.machine.SmartInterfaceType;
import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.PersistenceFacet;
import cn.howxu.mmcr.api.capability.storage.FloatValueStorage;
import cn.howxu.mmcr.compat.kubejs.SmartInterfaceEvents;
import cn.howxu.mmcr.compat.kubejs.SmartInterfaceUpdateEventJS;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.capability.SmartInterfaceCapability;
import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.registry.ModBlockEntities;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Stores smart-interface parameter values owned by one interface block.
 *
 * @author howxu <dev@howxu.cn>
 */
public class SmartInterfaceBlockEntity extends LinkedAppearanceBlockEntity implements CapabilityHost {
    private static final String MACHINE_ID_KEY = "machineId";
    private static final String VALUES_KEY = "values";
    private static final String CONTROLLERS_KEY = "controllers";
    private @Nullable ResourceLocation machineId;
    private final Map<String, Float> values = new LinkedHashMap<>();
    private final Set<BlockPos> controllers = new LinkedHashSet<>();
    private final FloatValueStorage capabilityStorage = new FloatValueStorage(this::applyCapabilityValues);
    private final MachineCapability inputCapability = new SmartInterfaceCapability(capabilityStorage, IOType.INPUT);
    private final MachineCapability outputCapability = new SmartInterfaceCapability(capabilityStorage, IOType.OUTPUT);

    public SmartInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMART_INTERFACE.get(), pos, state);
    }

    public Optional<ResourceLocation> machineId() {
        return Optional.ofNullable(machineId);
    }

    @Override
    public CapabilitySnapshot capabilitySnapshot() {
        return new CapabilitySnapshot(List.of(inputCapability, outputCapability), List.of(new StatePersistenceFacet()));
    }

    public Set<BlockPos> controllerPositions() {
        return Collections.unmodifiableSet(controllers);
    }

    public boolean hasController(BlockPos controllerPos) {
        return controllers.contains(controllerPos);
    }

    public List<String> parameterTypes() {
        return List.copyOf(values.keySet());
    }

    public Optional<Float> value(String type) {
        return Optional.ofNullable(values.get(type));
    }

    public boolean claimController(BlockPos controllerPos, ResourceLocation machineId, Map<String, SmartInterfaceType> types, boolean shared) {
        if (controllerPos == null || machineId == null || types == null || types.isEmpty()) return false;
        if (this.machineId != null && !this.machineId.equals(machineId)) return false;
        if (!shared && !controllers.isEmpty() && !controllers.contains(controllerPos)) return false;
        this.machineId = machineId;
        syncTypes(types);
        boolean added = controllers.add(controllerPos.immutable());
        if (added) changed();
        maintainControllerLink();
        return true;
    }

    public boolean releaseController(BlockPos controllerPos) {
        if (controllerPos == null || !controllers.remove(controllerPos)) return false;
        unlinkControllerAppearance(controllerPos);
        if (controllers.isEmpty()) machineId = null;
        changed();
        return true;
    }

    public boolean setValue(String type, float value) {
        if (type == null || !Float.isFinite(value) || !values.containsKey(type)) return false;
        float oldValue = values.get(type);
        if (Float.compare(oldValue, value) == 0) return true;
        values.put(type, value);
        capabilityStorage.set(type, value);
        changed();
        notifyControllersOfValueChange();
        postUpdate(type, oldValue, value);
        return true;
    }

    public void syncTypes(Map<String, SmartInterfaceType> types) {
        values.keySet().removeIf(type -> !types.containsKey(type));
        for (SmartInterfaceType type : types.values()) {
            values.putIfAbsent(type.type(), type.defaultValue());
        }
        capabilityStorage.replace(values);
    }

    public boolean bind(BlockPos controllerPos, ResourceLocation machineId, String type, float value) {
        if (type == null || type.isBlank() || !Float.isFinite(value)) return false;
        return claimController(controllerPos, machineId, Map.of(type, new SmartInterfaceType(type, value, 0)), true)
                && setValue(type, value);
    }

    public boolean unbind(BlockPos controllerPos) {
        return releaseController(controllerPos);
    }

    public Optional<Binding> binding(int index) {
        if (index < 0 || index >= values.size() || machineId == null || controllers.isEmpty()) return Optional.empty();
        String type = parameterTypes().get(index);
        return Optional.of(new Binding(controllers.iterator().next(), machineId, type, values.get(type)));
    }

    public Optional<Binding> bindingFor(BlockPos controllerPos) {
        if (controllerPos == null || !controllers.contains(controllerPos) || machineId == null || values.isEmpty()) {
            return Optional.empty();
        }
        String type = parameterTypes().getFirst();
        return Optional.of(new Binding(controllerPos, machineId, type, values.get(type)));
    }

    public boolean setValue(int index, float value) {
        return binding(index).map(binding -> setValue(binding.type(), value)).orElse(false);
    }

    public void serverTick() {
        if (level == null || level.isClientSide()
                || level.getGameTime() % ServerConfig.linkSmartInterfaceCheckIntervalTicks() != 0) return;
        if (controllers.removeIf(controllerPos -> level.hasChunkAt(controllerPos)
                && !(level.getBlockEntity(controllerPos) instanceof MachineControllerBlockEntity))) {
            changed();
        }
        maintainControllerLink();
    }

    @Override
    protected void saveAdditional(CompoundTag output, HolderLookup.Provider registries) {
        super.saveAdditional(output, registries);
        capabilitySnapshot().facets(PersistenceFacet.class).forEach(facet -> {
            CompoundTag state = new CompoundTag();
            facet.save(state, registries);
            output.put(facet.stateKey(), state);
        });
    }

    private void saveState(CompoundTag output, HolderLookup.Provider registries) {
        if (machineId != null) output.putString(MACHINE_ID_KEY, machineId.toString());
        ListTag serializedValues = new ListTag();
        values.forEach((type, value) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("type", type);
            entry.putFloat("value", value);
            serializedValues.add(entry);
        });
        output.put(VALUES_KEY, serializedValues);
        ListTag serializedControllers = new ListTag();
        controllers.forEach(pos -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt("x", pos.getX());
            entry.putInt("y", pos.getY());
            entry.putInt("z", pos.getZ());
            serializedControllers.add(entry);
        });
        output.put(CONTROLLERS_KEY, serializedControllers);
    }

    @Override
    protected void loadAdditional(CompoundTag input, HolderLookup.Provider registries) {
        super.loadAdditional(input, registries);
        if (input.contains("state")) loadState(input.getCompound("state"), registries);
    }

    private void loadState(CompoundTag input, HolderLookup.Provider registries) {
        String serializedMachineId = input.getString(MACHINE_ID_KEY);
        machineId = serializedMachineId.isBlank() ? null : ResourceLocation.tryParse(serializedMachineId);
        values.clear();
        controllers.clear();
        ListTag serializedValues = input.getList(VALUES_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < serializedValues.size(); index++) {
            CompoundTag entry = serializedValues.getCompound(index);
            String type = entry.getString("type");
            float value = entry.getFloat("value");
            if (!type.isBlank() && Float.isFinite(value) && !values.containsKey(type)) {
                values.put(type, value);
            }
        }
        ListTag serializedControllers = input.getList(CONTROLLERS_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < serializedControllers.size(); index++) {
            CompoundTag entry = serializedControllers.getCompound(index);
            controllers.add(new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z")).immutable());
        }
        capabilityStorage.replace(values);
    }

    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void postUpdate(String type, Float oldValue, Float newValue) {
        if (level == null || level.isClientSide() || machineId == null) return;
        SmartInterfaceEvents.post(new SmartInterfaceUpdateEventJS(worldPosition, machineId, type,
                oldValue, newValue, List.copyOf(controllers)));
    }

    private void applyCapabilityValues(Map<String, Float> nextValues) {
        boolean valuesChanged = !values.equals(nextValues);
        values.clear();
        values.putAll(nextValues);
        if (valuesChanged) {
            changed();
            notifyControllersOfValueChange();
        }
    }

    private void notifyControllersOfValueChange() {
        if (level == null || level.isClientSide()) return;
        for (BlockPos controllerPos : List.copyOf(controllers)) {
            if (level.getBlockEntity(controllerPos) instanceof MachineControllerBlockEntity controller) {
                controller.onSmartInterfaceValueChanged();
            }
        }
    }

    private final class StatePersistenceFacet implements PersistenceFacet {
        @Override
        public String stateKey() {
            return "state";
        }

        @Override
        public void save(CompoundTag output, HolderLookup.Provider registries) {
            saveState(output, registries);
        }

        @Override
        public void load(CompoundTag input, HolderLookup.Provider registries) {
            loadState(input, registries);
        }
    }

    public record Binding(BlockPos controllerPos, ResourceLocation machineId, String type, float value) {
        public static final Codec<Binding> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("controllerPos").forGetter(Binding::controllerPos),
                ResourceLocation.CODEC.fieldOf("machineId").forGetter(Binding::machineId),
                Codec.STRING.fieldOf("type").forGetter(Binding::type),
                Codec.FLOAT.fieldOf("value").forGetter(Binding::value)
        ).apply(instance, Binding::new));
    }

    public record ValueEntry(String type, float value) {
        public static final Codec<ValueEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("type").forGetter(ValueEntry::type),
                Codec.FLOAT.fieldOf("value").forGetter(ValueEntry::value)
        ).apply(instance, ValueEntry::new));
    }

    public record ControllerEntry(BlockPos pos) {
        public static final Codec<ControllerEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(ControllerEntry::pos)
        ).apply(instance, ControllerEntry::new));
    }
}
