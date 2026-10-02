package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.RandomAccess;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Immutable aggregate view published by a machine controller runtime.
 *
 * @author howxu <dev@howxu.cn>
 */
public record ControllerRuntimeSnapshot(
        StructureSnapshot structure,
        long capabilityVersion,
        long modifierVersion,
        long stateVersion,
        Map<String, List<MachineModifier>> foundModifiers,
        Map<ResourceLocation, MachineLevel> foundLevels,
        Set<BlockPos> linkedPortPositions,
        ModuleConnectionStatus moduleConnectionStatus,
        int installedModuleCount,
        CraftingStateSnapshot crafting,
        FactorySnapshot factory,
        List<ComponentPresentation> componentPresentations,
        List<CapabilityPresentation> capabilityPresentations,
        List<String> foundLevelIds,
        String machineId,
        String machineName,
        int controllerRole,
        boolean factorySupported,
        boolean factoryControllerPresent,
        int parallelControllerCount,
        long maxParallelControllerCount,
        long maxParallelism,
        List<ItemStack> upgradeItems,
        long upgradeContentRevision,
        Map<String, DataValue> dataStorageValues,
        ControllerRecipePresentation recipePresentation) {

    public ControllerRuntimeSnapshot(StructureSnapshot structure, long capabilityVersion, long modifierVersion,
                                     long stateVersion, Map<String, List<MachineModifier>> foundModifiers,
                                     Map<ResourceLocation, MachineLevel> foundLevels, Set<BlockPos> linkedPortPositions,
                                     ModuleConnectionStatus moduleConnectionStatus, int installedModuleCount,
                                     CraftingStateSnapshot crafting, FactorySnapshot factory,
                                     List<ComponentPresentation> componentPresentations,
                                     List<CapabilityPresentation> capabilityPresentations, List<String> foundLevelIds,
                                     String machineId, String machineName, int controllerRole, boolean factorySupported,
                                     boolean factoryControllerPresent, int parallelControllerCount,
                                     long maxParallelControllerCount, long maxParallelism) {
        this(structure, capabilityVersion, modifierVersion, stateVersion, foundModifiers, foundLevels,
                linkedPortPositions, moduleConnectionStatus, installedModuleCount, crafting,
                factory, componentPresentations, capabilityPresentations, foundLevelIds, machineId, machineName,
                controllerRole, factorySupported, factoryControllerPresent, parallelControllerCount,
                maxParallelControllerCount, maxParallelism, List.of(), 0L, Map.of(),
                ControllerRecipePresentation.empty());
    }

    public ControllerRuntimeSnapshot(StructureSnapshot structure, long capabilityVersion,
                                     long modifierVersion, long stateVersion, Map<String, List<MachineModifier>> foundModifiers,
                                     Map<ResourceLocation, MachineLevel> foundLevels, Set<BlockPos> linkedPortPositions,
                                     ModuleConnectionStatus moduleConnectionStatus, int installedModuleCount,
                                     CraftingStateSnapshot crafting, FactorySnapshot factory,
                                     List<ComponentPresentation> componentPresentations,
                                     List<CapabilityPresentation> capabilityPresentations, List<String> foundLevelIds,
                                     String machineId, String machineName, int controllerRole, boolean factorySupported,
                                      boolean factoryControllerPresent, int parallelControllerCount,
                                      long maxParallelControllerCount, long maxParallelism,
                                      Map<String, DataValue> dataStorageValues) {
        this(structure, capabilityVersion, modifierVersion, stateVersion, foundModifiers, foundLevels,
                linkedPortPositions, moduleConnectionStatus, installedModuleCount, crafting, factory,
                componentPresentations, capabilityPresentations, foundLevelIds, machineId, machineName,
                controllerRole, factorySupported, factoryControllerPresent, parallelControllerCount,
                maxParallelControllerCount, maxParallelism, dataStorageValues, ControllerRecipePresentation.empty());
    }

    public ControllerRuntimeSnapshot(StructureSnapshot structure, long capabilityVersion,
                                     long modifierVersion, long stateVersion, Map<String, List<MachineModifier>> foundModifiers,
                                     Map<ResourceLocation, MachineLevel> foundLevels, Set<BlockPos> linkedPortPositions,
                                     ModuleConnectionStatus moduleConnectionStatus, int installedModuleCount,
                                     CraftingStateSnapshot crafting, FactorySnapshot factory,
                                     List<ComponentPresentation> componentPresentations,
                                     List<CapabilityPresentation> capabilityPresentations, List<String> foundLevelIds,
                                     String machineId, String machineName, int controllerRole, boolean factorySupported,
                                     boolean factoryControllerPresent, int parallelControllerCount,
                                     long maxParallelControllerCount, long maxParallelism,
                                     Map<String, DataValue> dataStorageValues,
                                     ControllerRecipePresentation recipePresentation) {
        this(structure, capabilityVersion, modifierVersion, stateVersion, foundModifiers, foundLevels,
                linkedPortPositions, moduleConnectionStatus, installedModuleCount,
                crafting, factory, componentPresentations, capabilityPresentations, foundLevelIds,
                machineId, machineName, controllerRole, factorySupported, factoryControllerPresent,
                parallelControllerCount, maxParallelControllerCount, maxParallelism, List.of(), 0L,
                dataStorageValues, recipePresentation);
    }

    public ControllerRuntimeSnapshot {
        structure = structure == null ? StructureSnapshot.empty() : structure;
        foundModifiers = ownModifiers(foundModifiers == null ? Map.of() : foundModifiers);
        foundLevels = ownMap(foundLevels == null ? Map.of() : foundLevels);
        dataStorageValues = ownMap(dataStorageValues == null ? Map.of() : dataStorageValues);
        linkedPortPositions = Set.copyOf(linkedPortPositions == null ? Set.of() : linkedPortPositions);
        moduleConnectionStatus = moduleConnectionStatus == null
                ? ModuleConnectionStatus.disconnected() : moduleConnectionStatus;
        if (installedModuleCount < 0) throw new IllegalArgumentException("installedModuleCount must not be negative");
        crafting = crafting == null ? CraftingStateSnapshot.empty(structure.version(), capabilityVersion, modifierVersion) : crafting;
        factory = factory == null ? FactorySnapshot.empty() : factory;
        componentPresentations = List.copyOf(componentPresentations == null ? List.of() : componentPresentations);
        capabilityPresentations = List.copyOf(capabilityPresentations == null ? List.of() : capabilityPresentations);
        foundLevelIds = List.copyOf(foundLevelIds == null ? List.of() : foundLevelIds);
        machineId = machineId == null ? "" : machineId;
        machineName = machineName == null ? "" : machineName;
        if (upgradeContentRevision < 0L) throw new IllegalArgumentException("Upgrade content revision must not be negative");
        upgradeItems = ownUpgradeItems(upgradeItems == null ? List.of() : upgradeItems);
        recipePresentation = recipePresentation == null ? ControllerRecipePresentation.empty() : recipePresentation;
        if (controllerRole < 0 || parallelControllerCount < 0 || maxParallelControllerCount < 0 || maxParallelism < 1) {
            throw new IllegalArgumentException("Invalid controller presentation values");
        }
    }

    /**
     * Publishes dynamic state after the caller has verified that static runtime inputs are unchanged.
     */
    public ControllerRuntimeSnapshot withRuntimeState(
            CraftingStateSnapshot crafting, FactorySnapshot factory,
            List<CapabilityPresentation> capabilityPresentations,
            long maxParallelism, ControllerRecipePresentation recipePresentation) {
        return new ControllerRuntimeSnapshot(structure, capabilityVersion, modifierVersion, stateVersion,
                foundModifiers, foundLevels, linkedPortPositions, moduleConnectionStatus, installedModuleCount,
                crafting, factory, componentPresentations, capabilityPresentations, foundLevelIds,
                machineId, machineName, controllerRole, factorySupported, factoryControllerPresent,
                parallelControllerCount, maxParallelControllerCount, maxParallelism, upgradeItems,
                upgradeContentRevision, dataStorageValues, recipePresentation);
    }

    private static Map<String, List<MachineModifier>> ownModifiers(Map<String, List<MachineModifier>> values) {
        if (values instanceof OwnedMap<?, ?>) return values;
        if (values.isEmpty()) return Map.of();
        return new OwnedMap<>(values, value -> List.copyOf(value == null ? List.of() : value));
    }

    private static <K, V> Map<K, V> ownMap(Map<K, V> values) {
        if (values instanceof OwnedMap<?, ?>) return values;
        if (values.isEmpty()) return Map.of();
        return new OwnedMap<>(values);
    }

    private static List<ItemStack> ownUpgradeItems(List<ItemStack> values) {
        if (values instanceof OwnedUpgradeItems) return values;
        if (values.isEmpty()) return List.of();
        return new OwnedUpgradeItems(values);
    }

    @Override
    public List<ItemStack> upgradeItems() {
        if (upgradeItems instanceof OwnedUpgradeItems owned) return copyStacks(owned.stacks);
        return List.of();
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::copy).toList();
    }

    /**
     * A privately produced ordered map whose mutation paths all use unmodifiable delegates.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class OwnedMap<K, V> extends AbstractMap<K, V> {
        private final Map<K, V> values;

        private OwnedMap(Map<K, V> values) {
            this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }

        private OwnedMap(Map<K, V> values, UnaryOperator<V> copyValue) {
            Map<K, V> copy = new LinkedHashMap<>(values);
            copy.replaceAll((key, value) -> copyValue.apply(value));
            this.values = Collections.unmodifiableMap(copy);
        }

        @Override
        public int size() { return values.size(); }

        @Override
        public boolean containsKey(Object key) { return values.containsKey(key); }

        @Override
        public boolean containsValue(Object value) { return values.containsValue(value); }

        @Override
        public V get(Object key) { return values.get(key); }

        @Override
        public V getOrDefault(Object key, V defaultValue) { return values.getOrDefault(key, defaultValue); }

        @Override
        public Set<K> keySet() { return values.keySet(); }

        @Override
        public Collection<V> values() { return values.values(); }

        @Override
        public Set<Entry<K, V>> entrySet() { return values.entrySet(); }

        @Override
        public V put(K key, V value) { return values.put(key, value); }

        @Override
        public V remove(Object key) { return values.remove(key); }

        @Override
        public void putAll(Map<? extends K, ? extends V> map) { values.putAll(map); }

        @Override
        public void clear() { values.clear(); }

        @Override
        public V putIfAbsent(K key, V value) { return values.putIfAbsent(key, value); }

        @Override
        public boolean remove(Object key, Object value) { return values.remove(key, value); }

        @Override
        public boolean replace(K key, V oldValue, V newValue) { return values.replace(key, oldValue, newValue); }

        @Override
        public V replace(K key, V value) { return values.replace(key, value); }

        @Override
        public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) { values.replaceAll(function); }

        @Override
        public V computeIfAbsent(K key, Function<? super K, ? extends V> function) {
            return values.computeIfAbsent(key, function);
        }

        @Override
        public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> function) {
            return values.computeIfPresent(key, function);
        }

        @Override
        public V compute(K key, BiFunction<? super K, ? super V, ? extends V> function) {
            return values.compute(key, function);
        }

        @Override
        public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> function) {
            return values.merge(key, value, function);
        }
    }

    /**
     * Private stack ownership; list access never exposes the mutable backing stacks.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class OwnedUpgradeItems extends AbstractList<ItemStack> implements RandomAccess {
        private final List<ItemStack> stacks;

        private OwnedUpgradeItems(List<ItemStack> stacks) {
            this.stacks = copyStacks(stacks);
        }

        @Override
        public ItemStack get(int index) { return stacks.get(index).copy(); }

        @Override
        public int size() { return stacks.size(); }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other instanceof OwnedUpgradeItems owned) return stacks.equals(owned.stacks);
            return super.equals(other);
        }

        // AbstractList would hash the temporary stack copies returned by get().
        @Override
        public int hashCode() { return stacks.hashCode(); }
    }

    /**
     * Immutable component identity captured when this runtime snapshot was published.
     *
     * @author howxu <dev@howxu.cn>
     */
    public record ComponentPresentation(BlockPos position, @Nullable String kindId,
                                        @Nullable IOType ioType, List<String> tags) {
        public ComponentPresentation {
            position = position == null ? BlockPos.ZERO : position.immutable();
            tags = List.copyOf(tags == null ? List.of() : tags);
        }
    }

    /**
     * Immutable capability values captured when this runtime snapshot was published.
     *
     * @author howxu <dev@howxu.cn>
     */
    public record CapabilityPresentation(@Nullable ResourceLocation typeId, @Nullable IOType ioType,
                                         long amount, long capacity, List<StorageSlot> slots) {
        public CapabilityPresentation {
            if (amount < 0L || capacity < 0L) throw new IllegalArgumentException("Capability values must not be negative");
            slots = List.copyOf(slots == null ? List.of() : slots);
        }
    }

    /**
     * Immutable storage slot values captured when this runtime snapshot was published.
     *
     * @author howxu <dev@howxu.cn>
     */
    public record StorageSlot(String resourceId, long amount, long capacity) {
        public StorageSlot {
            resourceId = resourceId == null ? "" : resourceId;
            if (amount < 0L || capacity < 0L) throw new IllegalArgumentException("Storage values must not be negative");
        }
    }
}
