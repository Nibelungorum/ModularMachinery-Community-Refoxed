package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests runtime snapshot ownership and safe reuse when only runtime state changes.
 *
 * @author howxu <dev@howxu.cn>
 */
class ControllerRuntimeSnapshotTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void runtime_state_updates_reuse_owned_maps_and_preserve_static_fields() {
        var before = snapshotWithOwnedInputs();
        var crafting = craftingAt(7);
        var factory = new FactorySnapshot(true, true, List.of(crafting), 2, 1, 8L,
                false, List.of(), "factory", 2, null, List.of("mmcr:second"), 0, 1);
        var capabilities = new ArrayList<>(List.of(new ControllerRuntimeSnapshot.CapabilityPresentation(
                ResourceLocation.parse("mmcr:energy"), IOType.INPUT, 5L, 10L, List.of())));
        var presentation = new ControllerRecipePresentation(List.of(), 2L, 3L, 0D, 20, 2L);

        var after = before.withRuntimeState(crafting, factory, capabilities, 8L, presentation);
        capabilities.clear();

        assertThat(after.crafting()).isSameAs(crafting);
        assertThat(after.crafting().tick()).isEqualTo(7);
        assertThat(before.crafting().tick()).isEqualTo(2);
        assertThat(after.factory()).isSameAs(factory);
        assertThat(before.factory().active()).isFalse();
        assertThat(after.capabilityPresentations()).hasSize(1);
        assertThat(after.capabilityPresentations().getFirst().amount()).isEqualTo(5L);
        assertThat(before.capabilityPresentations().getFirst().amount()).isEqualTo(1L);
        assertThat(after.maxParallelism()).isEqualTo(8L);
        assertThat(before.maxParallelism()).isEqualTo(4L);
        assertThat(after.recipePresentation()).isSameAs(presentation);
        assertThat(before.recipePresentation().durationTicks()).isZero();
        assertThat(after.structure()).isSameAs(before.structure());
        assertThat(after.capabilityVersion()).isEqualTo(before.capabilityVersion());
        assertThat(after.modifierVersion()).isEqualTo(before.modifierVersion());
        assertThat(after.stateVersion()).isEqualTo(before.stateVersion());
        assertThat(after.foundModifiers()).isSameAs(before.foundModifiers());
        assertThat(after.foundModifiers().get("second")).isSameAs(before.foundModifiers().get("second"));
        assertThat(after.foundLevels()).isSameAs(before.foundLevels());
        assertThat(after.dataStorageValues()).isSameAs(before.dataStorageValues());
        assertThat(after.componentPresentations()).isSameAs(before.componentPresentations());
        assertThat(after.foundLevelIds()).isSameAs(before.foundLevelIds());
        assertThat(after.linkedPortPositions()).isEqualTo(before.linkedPortPositions());
        assertThat(after.moduleConnectionStatus()).isSameAs(before.moduleConnectionStatus());
        assertThat(after.installedModuleCount()).isEqualTo(before.installedModuleCount());
        assertThat(after.machineId()).isEqualTo(before.machineId());
        assertThat(after.machineName()).isEqualTo(before.machineName());
        assertThat(after.controllerRole()).isEqualTo(before.controllerRole());
        assertThat(after.factorySupported()).isEqualTo(before.factorySupported());
        assertThat(after.factoryControllerPresent()).isEqualTo(before.factoryControllerPresent());
        assertThat(after.parallelControllerCount()).isEqualTo(before.parallelControllerCount());
        assertThat(after.maxParallelControllerCount()).isEqualTo(before.maxParallelControllerCount());
        assertThat(after.upgradeContentRevision()).isEqualTo(before.upgradeContentRevision());
        assertOwnedInputs(after);
        assertThatThrownBy(() -> after.capabilityPresentations().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void external_unmodifiable_views_and_mutable_stacks_are_copied_before_runtime_reuse() {
        var source = snapshotWithOwnedInputs();
        var modifierList = new ArrayList<>(source.foundModifiers().get("second"));
        var modifiers = new LinkedHashMap<>(source.foundModifiers());
        modifiers.put("second", modifierList);
        var levels = new LinkedHashMap<>(source.foundLevels());
        var data = new LinkedHashMap<>(source.dataStorageValues());
        var inputStack = new ItemStack(Items.IRON_INGOT, 4);
        var upgrades = new ArrayList<>(List.of(inputStack));
        var before = snapshotWithInputs(Collections.unmodifiableMap(modifiers), Collections.unmodifiableMap(levels),
                Collections.unmodifiableMap(data), Collections.unmodifiableList(upgrades));

        modifierList.clear();
        modifiers.clear();
        levels.clear();
        data.clear();
        inputStack.setCount(1);
        upgrades.clear();
        var after = before.withRuntimeState(craftingAt(7), before.factory(),
                before.capabilityPresentations(), before.maxParallelism(), before.recipePresentation());

        assertOwnedInputs(before);
        assertOwnedInputs(after);
        after.upgradeItems().getFirst().setCount(2);
        before.upgradeItems().getFirst().setCount(3);
        assertThat(after.upgradeItems().getFirst().getCount()).isEqualTo(4);
        assertThat(before.upgradeItems().getFirst().getCount()).isEqualTo(4);
    }

    @Test
    void normal_constructor_can_safely_reuse_previous_snapshot_maps() {
        var before = snapshotWithOwnedInputs();
        var upgrades = before.upgradeItems();
        var after = snapshotWithInputs(before.foundModifiers(), before.foundLevels(),
                before.dataStorageValues(), upgrades);

        upgrades.getFirst().setCount(1);

        assertThat(after.foundModifiers()).isSameAs(before.foundModifiers());
        assertThat(after.foundLevels()).isSameAs(before.foundLevels());
        assertThat(after.dataStorageValues()).isSameAs(before.dataStorageValues());
        assertOwnedInputs(before);
        assertOwnedInputs(after);
    }

    @Test
    void owned_maps_reject_all_mutators_and_writable_views() {
        var before = snapshotWithOwnedInputs();
        var after = before.withRuntimeState(craftingAt(7), before.factory(),
                before.capabilityPresentations(), before.maxParallelism(), before.recipePresentation());

        assertUnmodifiableMap(after.foundModifiers(), "missing", List.of());
        assertUnmodifiableMap(after.foundLevels(), ResourceLocation.parse("mmcr:missing"),
                before.foundLevels().values().iterator().next());
        assertUnmodifiableMap(after.dataStorageValues(), "missing", DataValue.of(99));
        assertThatThrownBy(() -> after.foundModifiers().get("second").set(0, MachineModifier.parallelized(false)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> after.foundModifiers().get("second").clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertOwnedInputs(before);
        assertOwnedInputs(after);
    }

    @Test
    void upgrade_accessor_returns_independent_unmodifiable_lists() {
        var before = snapshotWithOwnedInputs();
        var after = before.withRuntimeState(craftingAt(7), before.factory(),
                before.capabilityPresentations(), before.maxParallelism(), before.recipePresentation());
        var returned = after.upgradeItems();
        var other = after.upgradeItems();

        assertThat(returned.getFirst()).isNotSameAs(other.getFirst());
        returned.getFirst().setCount(1);
        assertThat(other.getFirst().getCount()).isEqualTo(4);
        assertThat(after.upgradeItems().getFirst().getCount()).isEqualTo(4);
        assertThat(before.upgradeItems().getFirst().getCount()).isEqualTo(4);
        assertThatThrownBy(() -> returned.set(0, ItemStack.EMPTY)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(returned::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> returned.removeIf(stack -> false)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void snapshot_hash_code_remains_stable_when_upgrade_copies_are_read_or_mutated() {
        var before = snapshotWithOwnedInputs();
        var after = before.withRuntimeState(craftingAt(7), before.factory(),
                before.capabilityPresentations(), before.maxParallelism(), before.recipePresentation());
        int beforeHash = before.hashCode();
        int afterHash = after.hashCode();

        assertThat(before.hashCode()).isEqualTo(beforeHash);
        assertThat(after.hashCode()).isEqualTo(afterHash);
        after.upgradeItems().getFirst().setCount(1);
        before.upgradeItems().getFirst().setCount(2);
        assertThat(before.hashCode()).isEqualTo(beforeHash);
        assertThat(after.hashCode()).isEqualTo(afterHash);
    }

    @Test
    void snapshot_equality_preserves_upgrade_identity_without_copy_producing_reads() throws Exception {
        var input = new ItemStack(Items.IRON_INGOT, 4);
        var before = snapshotWithInputs(Map.of(), Map.of(), Map.of(), List.of(input));
        var independent = snapshotWithInputs(Map.of(), Map.of(), Map.of(), List.of(input));
        var shared = before.withRuntimeState(before.crafting(), before.factory(), before.capabilityPresentations(),
                before.maxParallelism(), before.recipePresentation());
        var beforeReads = probeUpgradeReads(before);
        var independentReads = probeUpgradeReads(independent);
        int hash = before.hashCode();

        assertThat(before.equals(before)).isTrue();
        assertThat(before.equals(shared)).isTrue();
        assertThat(shared.equals(before)).isTrue();
        assertThat(shared.hashCode()).isEqualTo(hash);
        // Equal item contents retain the original ItemStack identity-based inequality.
        assertThat(before.equals(independent)).isFalse();
        assertThat(independent.equals(before)).isFalse();
        assertThat(before.hashCode()).isEqualTo(hash);
        assertThat(beforeReads.getCalls).isZero();
        assertThat(independentReads.getCalls).isZero();

        ownedUpgrades(before).getFirst().setCount(1);
        assertThat(beforeReads.getCalls).isEqualTo(1);
        input.setCount(2);
        assertThat(before.upgradeItems().getFirst().getCount()).isEqualTo(4);
        assertThat(shared.upgradeItems().getFirst().getCount()).isEqualTo(4);
        assertThat(independent.upgradeItems().getFirst().getCount()).isEqualTo(4);
        assertThat(before.hashCode()).isEqualTo(hash);
    }

    @Test
    void upgrade_equality_keeps_empty_stack_and_foreign_list_semantics() throws Exception {
        var first = snapshotWithInputs(Map.of(), Map.of(), Map.of(), List.of(ItemStack.EMPTY));
        var second = snapshotWithInputs(Map.of(), Map.of(), Map.of(), List.of(ItemStack.EMPTY));
        var longer = snapshotWithInputs(Map.of(), Map.of(), Map.of(), List.of(ItemStack.EMPTY, ItemStack.EMPTY));
        var empty = snapshotWithInputs(Map.of(), Map.of(), Map.of(), List.of());

        assertThat(first.equals(second)).isTrue();
        assertThat(second.equals(first)).isTrue();
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        assertThat(first.equals(longer)).isFalse();
        assertThat(longer.equals(first)).isFalse();
        assertThat(first.equals(empty)).isFalse();
        var owner = ownedUpgrades(first);
        assertThat(owner.equals(List.of(ItemStack.EMPTY))).isTrue();
        assertThat(List.of(ItemStack.EMPTY).equals(owner)).isTrue();
        assertThat(owner.equals(null)).isFalse();
        assertThat(owner.equals(new Object())).isFalse();
    }

    @Test
    void runtime_updates_still_use_constructor_normalization_and_validation() {
        var before = snapshotWithOwnedInputs();
        var after = before.withRuntimeState(null, null, null, 1L, null);

        assertThat(after.crafting().tick()).isZero();
        assertThat(after.crafting().capabilityVersion()).isEqualTo(before.capabilityVersion());
        assertThat(after.crafting().modifierVersion()).isEqualTo(before.modifierVersion());
        assertThat(after.factory().active()).isFalse();
        assertThat(after.capabilityPresentations()).isEmpty();
        assertThat(after.recipePresentation()).isEqualTo(ControllerRecipePresentation.empty());
        assertThatThrownBy(() -> before.withRuntimeState(craftingAt(7), before.factory(),
                before.capabilityPresentations(), 0L, before.recipePresentation()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void null_and_empty_collections_keep_existing_normalization() {
        var before = snapshotWithInputs(null, null, null, null);
        var after = before.withRuntimeState(craftingAt(7), before.factory(), List.of(), 1L, null);

        assertThat(after.foundModifiers()).isEmpty();
        assertThat(after.foundLevels()).isEmpty();
        assertThat(after.dataStorageValues()).isEmpty();
        assertThat(after.upgradeItems()).isEmpty();
        assertThatThrownBy(() -> after.foundModifiers().put("new", List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> after.upgradeItems().add(new ItemStack(Items.IRON_INGOT)))
                .isInstanceOf(UnsupportedOperationException.class);

        var modifiers = new LinkedHashMap<String, List<MachineModifier>>();
        modifiers.put("second", null);
        var withNullList = snapshotWithInputs(modifiers, Map.of(), Map.of(), List.of());
        assertThat(withNullList.foundModifiers().get("second")).isEmpty();
        assertThatThrownBy(() -> withNullList.foundModifiers().get("second").clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> ownedUpgrades(ControllerRuntimeSnapshot snapshot) throws Exception {
        Field field = ControllerRuntimeSnapshot.class.getDeclaredField("upgradeItems");
        field.setAccessible(true);
        return (List<ItemStack>) field.get(snapshot);
    }

    @SuppressWarnings("unchecked")
    private static UpgradeReadProbe probeUpgradeReads(ControllerRuntimeSnapshot snapshot) throws Exception {
        var owner = ownedUpgrades(snapshot);
        Field field = owner.getClass().getDeclaredField("stacks");
        field.setAccessible(true);
        var probe = new UpgradeReadProbe((List<ItemStack>) field.get(owner));
        field.set(owner, probe);
        return probe;
    }

    /** Counts backing reads that would pass through the owner's copying get().
     * @author howxu <dev@howxu.cn> */
    private static final class UpgradeReadProbe extends AbstractList<ItemStack> {
        private final List<ItemStack> backing;
        private int getCalls;

        private UpgradeReadProbe(List<ItemStack> backing) { this.backing = backing; }

        @Override
        public ItemStack get(int index) { getCalls++; return backing.get(index); }

        @Override
        public int size() { return backing.size(); }

        @Override
        public boolean equals(Object other) {
            return backing.equals(other instanceof UpgradeReadProbe probe ? probe.backing : other);
        }

        @Override
        public int hashCode() { return backing.hashCode(); }
    }

    private static <K, V> void assertUnmodifiableMap(Map<K, V> map, K missing, V replacement) {
        K existing = map.keySet().iterator().next();
        V value = map.get(existing);
        assertThatThrownBy(() -> map.put(missing, replacement)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> map.putAll(Map.of())).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> map.remove(missing)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(map::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> map.putIfAbsent(existing, replacement)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> map.remove(missing, replacement)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> map.replace(missing, replacement)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> map.replace(existing, value, replacement)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> map.replaceAll((key, previous) -> replacement)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> map.compute(existing, (key, previous) -> replacement)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> map.computeIfAbsent(existing, key -> replacement)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> map.computeIfPresent(missing, (key, previous) -> replacement)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> map.merge(existing, replacement, (first, second) -> replacement)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> map.entrySet().iterator().next().setValue(replacement))
                .isInstanceOf(UnsupportedOperationException.class);
        var entries = map.entrySet().iterator();
        entries.next();
        assertThatThrownBy(entries::remove).isInstanceOf(UnsupportedOperationException.class);
        var keys = map.keySet().iterator();
        keys.next();
        assertThatThrownBy(keys::remove).isInstanceOf(UnsupportedOperationException.class);
        var values = map.values().iterator();
        values.next();
        assertThatThrownBy(values::remove).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> map.keySet().remove(existing)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> map.values().remove(value)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> map.entrySet().removeIf(entry -> false)).isInstanceOf(UnsupportedOperationException.class);
    }

    private static void assertOwnedInputs(ControllerRuntimeSnapshot snapshot) {
        assertThat(snapshot.foundModifiers().keySet()).containsExactly("second", "first");
        assertThat(snapshot.foundModifiers().get("second")).containsExactly(MachineModifier.parallelized(true));
        assertThat(snapshot.foundLevels().keySet()).containsExactly(
                ResourceLocation.parse("mmcr:second"), ResourceLocation.parse("mmcr:first"));
        assertThat(snapshot.dataStorageValues().keySet()).containsExactly("second", "first");
        assertThat(snapshot.dataStorageValues().get("second")).isEqualTo(DataValue.of(2));
        assertThat(snapshot.upgradeItems()).hasSize(1);
        assertThat(snapshot.upgradeItems().getFirst().getItem()).isEqualTo(Items.IRON_INGOT);
        assertThat(snapshot.upgradeItems().getFirst().getCount()).isEqualTo(4);
    }

    private static ControllerRuntimeSnapshot snapshotWithOwnedInputs() {
        var modifiers = new LinkedHashMap<String, List<MachineModifier>>();
        modifiers.put("second", new ArrayList<>(List.of(MachineModifier.parallelized(true))));
        modifiers.put("first", new ArrayList<>(List.of(MachineModifier.parallelized(false))));
        var levels = new LinkedHashMap<ResourceLocation, MachineLevel>();
        for (String name : List.of("second", "first")) {
            ResourceLocation id = ResourceLocation.parse("mmcr:" + name);
            levels.put(id, new MachineLevel(id, ResourceLocation.parse("mmcr:tier"), levels.size(),
                    new BlockPredicate.OfBlockState(Blocks.IRON_BLOCK.defaultBlockState()),
                    new ItemStack(Items.IRON_INGOT), ModifierDefinition.EMPTY));
        }
        var data = new LinkedHashMap<String, DataValue>();
        data.put("second", DataValue.of(2));
        data.put("first", DataValue.of(1));
        return snapshotWithInputs(modifiers, levels, data, new ArrayList<>(List.of(new ItemStack(Items.IRON_INGOT, 4))));
    }

    private static ControllerRuntimeSnapshot snapshotWithInputs(Map<String, List<MachineModifier>> modifiers,
                                                               Map<ResourceLocation, MachineLevel> levels,
                                                               Map<String, DataValue> data, List<ItemStack> upgrades) {
        return new ControllerRuntimeSnapshot(StructureSnapshot.empty(), 3L, 5L, 9L, modifiers, levels,
                Set.of(new BlockPos(1, 2, 3)), ModuleConnectionStatus.disconnected(), 1,
                craftingAt(2), FactorySnapshot.empty(),
                List.of(new ControllerRuntimeSnapshot.ComponentPresentation(BlockPos.ZERO, "energy", IOType.INPUT, List.of())),
                List.of(new ControllerRuntimeSnapshot.CapabilityPresentation(
                        ResourceLocation.parse("mmcr:energy"), IOType.INPUT, 1L, 10L, List.of())),
                List.of("mmcr:second", "mmcr:first"), "mmcr:machine", "machine.name", 1, true, true,
                2, 3L, 4L, upgrades, 11L, data, ControllerRecipePresentation.empty());
    }

    private static CraftingStateSnapshot craftingAt(int tick) {
        return new CraftingStateSnapshot(ResourceLocation.parse("mmcr:recipe"), CraftingStatus.IDLE, null,
                0L, 3L, 5L, tick, 20, 2L, 4L);
    }
}
