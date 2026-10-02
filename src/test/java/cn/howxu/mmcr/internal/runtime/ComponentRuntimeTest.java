package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.storage.CapabilityStorage;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.ItemHandlerFacet;
import cn.howxu.mmcr.api.capability.facet.ValueFacet;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.machine.definition.MachineIoView;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.ModifierRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.internal.recipe.FactorySearchContext;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.ParallelControllerBlockEntity;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import com.sun.management.ThreadMXBean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies component replacement and capability publication semantics.
 *
 * @author howxu <dev@howxu.cn>
 */
class ComponentRuntimeTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void replacing_with_the_same_effective_components_does_not_increment_capability_version() {
        ComponentRuntime runtime = new ComponentRuntime();
        List<ProcessingComponent> components = List.of(new ProcessingComponent(null, "input", BlockPos.ZERO));

        runtime.replaceComponents(components);
        long version = runtime.capabilityVersion();
        runtime.replaceComponents(List.copyOf(components));

        assertThat(runtime.components()).containsExactlyElementsOf(components);
        assertThat(runtime.capabilities()).isEmpty();
        assertThat(runtime.capabilityVersion()).isEqualTo(version);
    }

    @Test
    void replacement_methods_report_only_effective_changes() {
        ComponentRuntime runtime = new ComponentRuntime();
        ProcessingComponent component = new ProcessingComponent(null, "input", BlockPos.ZERO);
        Map<String, List<MachineModifier>> modifiers = Map.of("modifier", List.of(
                MachineModifier.numeric("duration", "input", 1D, "add", false)));
        ResourceLocation levelId = ResourceLocation.fromNamespaceAndPath("mmcr_test", "replacement_level");
        MachineLevel level = new MachineLevel(levelId, levelId, 1, new BlockPredicate.Any(),
                ItemStack.EMPTY, ModifierDefinition.EMPTY);
        ModuleConnectionStatus connection = ModuleConnectionStatus.connected(
                ResourceLocation.fromNamespaceAndPath("mmcr_test", "host"));

        assertThat(runtime.replaceComponents(List.of(component))).isTrue();
        assertThat(runtime.replaceComponents(List.of(component))).isFalse();
        assertThat(runtime.replaceModifiers(modifiers)).isTrue();
        assertThat(runtime.replaceModifiers(new LinkedHashMap<>(modifiers))).isFalse();
        assertThat(runtime.replaceLevels(Map.of(levelId, level))).isTrue();
        assertThat(runtime.replaceLevels(Map.of(levelId, level))).isFalse();
        assertThat(runtime.replaceLinkedPortPositions(Set.of(BlockPos.ZERO))).isTrue();
        assertThat(runtime.replaceLinkedPortPositions(Set.of(BlockPos.ZERO))).isFalse();
        assertThat(runtime.replaceModuleConnectionState(connection, 1)).isTrue();
        assertThat(runtime.replaceModuleConnectionState(connection, 1)).isFalse();
    }

    @Test
    void component_and_capability_views_are_immutable() {
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceComponents(List.of(new ProcessingComponent(null, "input", BlockPos.ZERO)));

        assertThatThrownBy(() -> runtime.components().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> runtime.capabilities().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void effective_component_replacement_increments_capability_version_once() {
        ComponentRuntime runtime = new ComponentRuntime();
        ProcessingComponent first = component(new TestCapabilityHost(List.of(new TestCapability("first"))), "first");
        ProcessingComponent second = component(new TestCapabilityHost(List.of(new TestCapability("second"))), "second");

        runtime.replaceComponents(List.of(first));
        long firstVersion = runtime.capabilityVersion();
        runtime.replaceComponents(List.of(second));

        assertThat(runtime.capabilityVersion()).isEqualTo(firstVersion + 1);
        runtime.replaceComponents(List.of(second));
        assertThat(runtime.capabilityVersion()).isEqualTo(firstVersion + 1);
    }

    @Test
    void runtime_collects_every_capability_from_the_host_capabilities_view() {
        MachineCapability item = new TestCapability("item");
        MachineCapability fluid = new TestCapability("fluid");
        TestCapabilityHost host = new TestCapabilityHost(new CapabilitySnapshot(List.of(item)), List.of(item, fluid));
        ComponentRuntime runtime = new ComponentRuntime();

        runtime.replaceComponents(List.of(component(host, "combined")));

        assertThat(runtime.capabilities()).containsExactly(item, fluid);
        assertThat(runtime.capabilityPresentations())
                .extracting(ControllerRuntimeSnapshot.CapabilityPresentation::typeId)
                .containsExactly(item.type().id(), fluid.type().id());
    }

    @Test
    void replacing_component_wrappers_with_the_same_effective_capabilities_does_not_increment_version() {
        MachineCapability capability = new TestCapability("item");
        TestCapabilityHost host = new TestCapabilityHost(List.of(capability));
        ProcessingComponent first = component(host, "first");
        ProcessingComponent replacement = component(host, "replacement");
        ComponentRuntime runtime = new ComponentRuntime();

        runtime.replaceComponents(List.of(first));
        long version = runtime.capabilityVersion();
        runtime.replaceComponents(List.of(replacement));

        assertThat(runtime.components()).containsExactly(replacement);
        assertThat(runtime.capabilities()).containsExactly(capability);
        assertThat(runtime.capabilityVersion()).isEqualTo(version);
    }

    @Test
    void capability_content_and_order_changes_increment_version() {
        TestCapability first = new TestCapability("first");
        TestCapability second = new TestCapability("second");
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceComponents(List.of(component(new TestCapabilityHost(List.of(first)), "first")));
        long version = runtime.capabilityVersion();

        runtime.replaceComponents(List.of(component(new TestCapabilityHost(List.of(second)), "second")));

        assertThat(runtime.capabilities()).containsExactly(second);
        assertThat(runtime.capabilityVersion()).isEqualTo(version + 1);
    }

    @Test
    void changing_a_capability_storage_value_does_not_change_capability_identity() {
        LongValueStorage storage = new LongValueStorage(100, 100, () -> {});
        MachineCapability capability = new TestCapability("stored", storage);
        ProcessingComponent component = component(new TestCapabilityHost(List.of(capability)), "stored");
        ComponentRuntime runtime = new ComponentRuntime();

        runtime.replaceComponents(List.of(component));
        long version = runtime.capabilityVersion();
        storage.setAmount(40);
        runtime.replaceComponents(List.of(component));

        assertThat(runtime.capabilityVersion()).isEqualTo(version);
    }

    @Test
    void local_presentation_refresh_reuses_other_rows_without_reading_their_storage() {
        CountingItemStorage items = new CountingItemStorage();
        items.setContents(0, new ItemStack(Items.IRON_INGOT), 12);
        LongValueStorage energy = new LongValueStorage(100, 100, () -> {});
        BlockPos itemPos = new BlockPos(10, 20, 30);
        BlockPos energyPos = new BlockPos(11, 20, 30);
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceComponents(List.of(
                component(new TestCapabilityHost(itemPos, List.of(new TestCapability("items", items))), "items"),
                component(new TestCapabilityHost(energyPos, List.of(new TestCapability("energy", energy))), "energy")));
        var before = runtime.capabilityPresentations();
        long version = runtime.capabilityVersion();
        long epoch = runtime.capabilityPresentationEpoch();
        items.resetReads();

        energy.setAmount(40);
        runtime.markCapabilityPresentationChanged(energyPos);
        runtime.markCapabilityPresentationChanged(energyPos);
        var after = runtime.capabilityPresentations();

        assertThat(after.get(0)).isSameAs(before.get(0));
        assertThat(after.get(1).amount()).isEqualTo(40);
        assertThat(before.get(1).amount()).isZero();
        assertThat(before.get(0).slots().getFirst().amount()).isEqualTo(12);
        assertThat(runtime.capabilityPresentations()).isSameAs(after);
        assertThat(runtime.capabilityVersion()).isEqualTo(version);
        assertThat(runtime.capabilityPresentationEpoch()).isEqualTo(epoch + 2);
        assertThat(items.resourceReads).isZero();
        assertThat(items.amountReads).isZero();
        assertThat(items.capacityReads).isZero();
        assertThatThrownBy(after::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> after.get(0).slots().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void one_source_refreshes_every_capability_and_both_view_directions_in_original_order() {
        LongValueStorage energy = new LongValueStorage(100, 100, () -> {});
        LongValueStorage secondValue = new LongValueStorage(100, 100, () -> {});
        TestCapability bidirectional = new TestCapability("energy", energy, CapabilityDirections.bidirectional());
        CountingItemStorage otherItems = new CountingItemStorage();
        BlockPos source = new BlockPos(15, 20, 30);
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceComponents(List.of(
                component(new TestCapabilityHost(source, List.of(bidirectional,
                        new TestCapability("second", secondValue))), "combined"),
                component(new TestCapabilityHost(new BlockPos(16, 20, 30),
                        List.of(new TestCapability("items", otherItems))), "items")));
        var before = runtime.capabilityPresentations();
        otherItems.resetReads();

        energy.setAmount(40);
        secondValue.setAmount(25);
        runtime.markCapabilityPresentationChanged(source);
        var after = runtime.capabilityPresentations();

        assertThat(after.subList(0, 2)).extracting(ControllerRuntimeSnapshot.CapabilityPresentation::ioType)
                .containsExactlyElementsOf(bidirectional.view().directions().values());
        assertThat(after).extracting(ControllerRuntimeSnapshot.CapabilityPresentation::ioType)
                .containsExactlyElementsOf(before.stream()
                        .map(ControllerRuntimeSnapshot.CapabilityPresentation::ioType).toList());
        assertThat(after).extracting(ControllerRuntimeSnapshot.CapabilityPresentation::amount)
                .containsExactly(40L, 40L, 25L, 0L);
        assertThat(before).allSatisfy(row -> assertThat(row.amount()).isZero());
        assertThat(after.get(3)).isSameAs(before.get(3));
        assertThat(otherItems.resourceReads).isZero();
        assertThat(otherItems.amountReads).isZero();
        assertThat(otherItems.capacityReads).isZero();
    }

    @Test
    void a_shared_capability_refreshes_every_occurrence_at_the_source_and_other_hosts() {
        CountingItemStorage sharedItems = new CountingItemStorage();
        TestCapability shared = new TestCapability("shared", sharedItems);
        LongValueStorage untouched = new LongValueStorage(100, 100, () -> {});
        BlockPos firstPos = new BlockPos(21, 20, 30);
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceComponents(List.of(
                component(new TestCapabilityHost(firstPos, List.of(shared, shared)), "first"),
                component(new TestCapabilityHost(new BlockPos(22, 20, 30), List.of(shared)), "alias"),
                component(new TestCapabilityHost(new BlockPos(23, 20, 30),
                        List.of(new TestCapability("untouched", untouched))), "untouched")));
        var before = runtime.capabilityPresentations();
        sharedItems.setContents(0, new ItemStack(Items.GOLD_INGOT), 37);
        sharedItems.resetReads();

        runtime.markCapabilityPresentationChanged(firstPos);
        runtime.markCapabilityPresentationChanged(firstPos);
        var after = runtime.capabilityPresentations();

        assertThat(after.subList(0, 3)).allSatisfy(row -> {
            assertThat(row.amount()).isEqualTo(37);
            assertThat(row.slots().getFirst().amount()).isEqualTo(37);
        });
        assertThat(before.subList(0, 3)).allSatisfy(row -> assertThat(row.amount()).isZero());
        assertThat(after.get(3)).isSameAs(before.get(3));
        assertThat(sharedItems.resourceReads).isEqualTo(3);
        assertThat(sharedItems.amountReads).isEqualTo(3);
        assertThat(sharedItems.capacityReads).isEqualTo(3);
        assertThat(runtime.capabilityPresentations()).isSameAs(after);
        assertThat(sharedItems.resourceReads).isEqualTo(3);
    }

    @Test
    void repeated_seed_provider_expansion_is_linear_and_preserves_all_storage_aliases() {
        for (int repeats : List.of(1, 8, 32)) {
            CountingItemStorage original = new CountingItemStorage();
            CountingItemStorage replacement = new CountingItemStorage();
            CountingItemStorage latest = new CountingItemStorage();
            CountingItemStorage unrelated = new CountingItemStorage();
            original.setContents(0, new ItemStack(Items.IRON_INGOT), 12);
            replacement.setContents(0, new ItemStack(Items.GOLD_INGOT), 37);
            latest.setContents(0, new ItemStack(Items.GOLD_INGOT), 55);
            unrelated.setContents(0, new ItemStack(Items.IRON_INGOT), 7);
            MutableTestCapability provider = new MutableTestCapability("shared", original);
            MutableTestCapability currentAlias = new MutableTestCapability("current_alias", unrelated);
            BlockPos source = new BlockPos(24, 20, 30);
            ComponentRuntime runtime = new ComponentRuntime();
            runtime.replaceComponents(List.of(
                    component(new TestCapabilityHost(source, Collections.nCopies(repeats, provider)), "source"),
                    component(new TestCapabilityHost(new BlockPos(25, 20, 30), List.of(provider, provider)), "aliases"),
                    component(new TestCapabilityHost(new BlockPos(26, 20, 30), List.of(currentAlias)), "current_alias"),
                    component(new TestCapabilityHost(new BlockPos(27, 20, 30),
                            List.of(new TestCapability("published_alias", original))), "published_alias"),
                    component(new TestCapabilityHost(new BlockPos(28, 20, 30),
                            List.of(new TestCapability("unrelated", unrelated))), "unrelated")));
            var before = runtime.capabilityPresentations();
            long epoch = runtime.capabilityPresentationEpoch();
            long version = runtime.capabilityVersion();
            int occurrences = repeats + 2;
            unrelated.resetReads();
            original.setContents(0, new ItemStack(Items.IRON_INGOT), 19);

            // A second notification must resolve new backing even while every provider occurrence is dirty.
            for (CountingItemStorage backing : List.of(replacement, latest)) {
                provider.storage = backing;
                currentAlias.storage = backing;
                provider.facetReads = 0;
                provider.storageReads = 0;
                currentAlias.facetReads = 0;
                currentAlias.storageReads = 0;
                runtime.markCapabilityPresentationChanged(source);
                assertThat(provider.facetReads).isPositive().isLessThanOrEqualTo(3);
                assertThat(provider.storageReads).isPositive().isLessThanOrEqualTo(3);
                assertThat(currentAlias.facetReads).isPositive().isLessThanOrEqualTo(2);
                assertThat(currentAlias.storageReads).isEqualTo(1);
            }
            var after = runtime.capabilityPresentations();

            assertThat(after).hasSize(occurrences + 3);
            assertThat(after.subList(0, occurrences + 1)).allSatisfy(row -> {
                assertThat(row.amount()).isEqualTo(55);
                assertThat(row.slots().getFirst().amount()).isEqualTo(55);
            });
            assertThat(after.get(occurrences + 1).amount()).isEqualTo(19);
            assertThat(after.get(occurrences + 1)).isNotSameAs(before.get(occurrences + 1));
            assertThat(after.get(occurrences + 2)).isSameAs(before.get(occurrences + 2));
            assertThat(before.subList(0, occurrences)).allSatisfy(row -> assertThat(row.amount()).isEqualTo(12));
            assertThat(before.get(occurrences).amount()).isEqualTo(7);
            assertThat(before.get(occurrences + 1).amount()).isEqualTo(12);
            assertThat(unrelated.resourceReads).isZero();
            assertThat(unrelated.amountReads).isZero();
            assertThat(unrelated.capacityReads).isZero();
            assertThat(runtime.capabilityPresentationEpoch()).isEqualTo(epoch + 2);
            assertThat(runtime.capabilityVersion()).isEqualTo(version);
            assertThat(runtime.capabilityPresentations()).isSameAs(after);
        }
    }

    @Test
    void repeated_single_provider_notifications_stay_within_a_small_allocation_budget() {
        assumeTrue(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean);
        ThreadMXBean allocations = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        assumeTrue(allocations.isThreadAllocatedMemorySupported() && allocations.isThreadAllocatedMemoryEnabled());
        CountingItemStorage items = new CountingItemStorage();
        LongValueStorage energy = new LongValueStorage(100, 100, () -> {});
        MutableTestCapability provider = new MutableTestCapability("energy", energy);
        BlockPos source = new BlockPos(29, 20, 30);
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceComponents(List.of(
                component(new TestCapabilityHost(source, List.of(provider, provider)), "source"),
                component(new TestCapabilityHost(new BlockPos(30, 20, 30), List.of(provider)), "alias"),
                component(new TestCapabilityHost(new BlockPos(30, 21, 30),
                        List.of(new MutableTestCapability("items", items))), "unrelated")));
        var before = runtime.capabilityPresentations();
        energy.setAmount(40);
        items.resetReads();
        for (int notification = 0; notification < 20_000; notification++) {
            runtime.markCapabilityPresentationChanged(source);
        }
        long threadId = Thread.currentThread().threadId();
        long epoch = runtime.capabilityPresentationEpoch();
        long allocatedBefore = allocations.getThreadAllocatedBytes(threadId);
        int notifications = 6_000;
        for (int notification = 0; notification < notifications; notification++) {
            runtime.markCapabilityPresentationChanged(source);
        }
        long allocatedBytes = allocations.getThreadAllocatedBytes(threadId) - allocatedBefore;
        var after = runtime.capabilityPresentations();

        assertThat(allocatedBytes).isLessThan(256L * notifications);
        assertThat(after.subList(0, 3)).allSatisfy(row -> assertThat(row.amount()).isEqualTo(40));
        assertThat(after.get(3)).isSameAs(before.get(3));
        assertThat(before.subList(0, 3)).allSatisfy(row -> assertThat(row.amount()).isZero());
        assertThat(runtime.capabilityPresentationEpoch()).isEqualTo(epoch + notifications);
        assertThat(items.resourceReads).isZero();
        assertThat(items.amountReads).isZero();
        assertThat(items.capacityReads).isZero();
    }

    @Test
    void single_and_multiple_seed_providers_preserve_delegated_dual_storage_without_transitive_invalidation() {
        for (boolean multipleSeeds : List.of(false, true)) {
            LongValueStorage value = new LongValueStorage(100, 100, () -> {});
            LongValueStorage secondValue = new LongValueStorage(100, 100, () -> {});
            CountingItemStorage resource = new CountingItemStorage();
            CountingItemStorage unrelated = new CountingItemStorage();
            SplitStorageCapability provider = new SplitStorageCapability("split", value, resource);
            SplitStorageCapability valueAlias = new SplitStorageCapability("value_alias", value, unrelated);
            BlockPos source = new BlockPos(101, 20, 30);
            ComponentRuntime runtime = new ComponentRuntime();
            runtime.replaceComponents(List.of(
                    component(new TestCapabilityHost(source, multipleSeeds
                            ? List.of(provider, new TestCapability("second", secondValue)) : List.of(provider)), "source"),
                    component(new TestCapabilityHost(new BlockPos(102, 20, 30), List.of(valueAlias)), "value_alias"),
                    component(new TestCapabilityHost(new BlockPos(103, 20, 30),
                            List.of(new TestResourceCapability("resource_alias", resource))), "resource_alias"),
                    component(new TestCapabilityHost(new BlockPos(104, 20, 30),
                            List.of(new TestResourceCapability("unrelated", unrelated))), "unrelated")));
            var before = runtime.capabilityPresentations();
            int seeds = multipleSeeds ? 2 : 1;
            value.setAmount(40);
            secondValue.setAmount(25);
            resource.setContents(0, new ItemStack(Items.GOLD_INGOT), 27);
            unrelated.setContents(0, new ItemStack(Items.IRON_INGOT), 19);
            unrelated.resetReads();

            runtime.markCapabilityPresentationChanged(source);
            var after = runtime.capabilityPresentations();
            assertThat(after.get(0).amount()).isEqualTo(40);
            if (multipleSeeds) assertThat(after.get(1).amount()).isEqualTo(25);
            assertThat(after.get(seeds).amount()).isEqualTo(40);
            assertThat(after.get(seeds + 1).amount()).isEqualTo(27);
            assertThat(after.get(seeds + 2)).isSameAs(before.get(seeds + 2));
            assertThat(unrelated.resourceReads).isZero();
            assertThat(unrelated.amountReads).isZero();
            assertThat(unrelated.capacityReads).isZero();

            // A declared value facet with null storage falls back to the separate resource facet.
            provider.valueStorage = null;
            provider.resourceStorage = unrelated;
            value.setAmount(47);
            runtime.markCapabilityPresentationChanged(source);
            var rebound = runtime.capabilityPresentations();
            assertThat(rebound.get(0).amount()).isEqualTo(19);
            assertThat(rebound.get(0).slots()).hasSize(1);
            assertThat(rebound.get(seeds).amount()).isEqualTo(47);
            assertThat(rebound.get(seeds + 2).amount()).isEqualTo(19);
            assertThat(after.get(0).amount()).isEqualTo(40);
            assertThat(before).allSatisfy(row -> assertThat(row.amount()).isZero());
        }
    }

    @Test
    void single_provider_with_different_published_alias_backing_keeps_both_storage_dependencies() {
        SwitchingItemStorage original = new SwitchingItemStorage();
        CountingItemStorage replacement = new CountingItemStorage();
        CountingItemStorage unrelated = new CountingItemStorage();
        original.setContents(0, new ItemStack(Items.IRON_INGOT), 12);
        replacement.setContents(0, new ItemStack(Items.GOLD_INGOT), 27);
        MutableTestCapability provider = new MutableTestCapability("switching", original);
        BlockPos source = new BlockPos(105, 20, 30);
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceComponents(List.of(
                component(new TestCapabilityHost(source, List.of(provider)), "source"),
                component(new TestCapabilityHost(new BlockPos(106, 20, 30), List.of(provider)), "provider_alias"),
                component(new TestCapabilityHost(new BlockPos(107, 20, 30),
                        List.of(new TestCapability("old_storage_alias", original))), "old_storage_alias"),
                component(new TestCapabilityHost(new BlockPos(108, 20, 30),
                        List.of(new TestCapability("new_storage_alias", replacement))), "new_storage_alias"),
                component(new TestCapabilityHost(new BlockPos(109, 20, 30),
                        List.of(new TestCapability("unrelated", unrelated))), "unrelated")));
        original.afterRead = () -> provider.storage = replacement;
        var before = runtime.capabilityPresentations();
        assertThat(before.subList(0, 2)).extracting(ControllerRuntimeSnapshot.CapabilityPresentation::amount)
                .containsExactly(12L, 27L);
        original.setContents(0, new ItemStack(Items.IRON_INGOT), 19);
        replacement.setContents(0, new ItemStack(Items.GOLD_INGOT), 55);
        unrelated.resetReads();

        runtime.markCapabilityPresentationChanged(source);
        var after = runtime.capabilityPresentations();

        assertThat(after).extracting(ControllerRuntimeSnapshot.CapabilityPresentation::amount)
                .containsExactly(55L, 55L, 19L, 55L, 0L);
        assertThat(after.get(4)).isSameAs(before.get(4));
        assertThat(before).extracting(ControllerRuntimeSnapshot.CapabilityPresentation::amount)
                .containsExactly(12L, 27L, 12L, 27L, 0L);
        assertThat(unrelated.resourceReads).isZero();
        assertThat(unrelated.amountReads).isZero();
        assertThat(unrelated.capacityReads).isZero();
    }

    @Test
    void reading_presentations_from_a_facet_during_notification_keeps_historical_aliases_and_final_publication() {
        for (boolean multipleSeeds : List.of(false, true)) {
            LongValueStorage original = new LongValueStorage(100, 100, () -> {});
            LongValueStorage replacement = new LongValueStorage(100, 100, () -> {});
            CountingItemStorage unrelated = new CountingItemStorage();
            SplitStorageCapability provider = new SplitStorageCapability("reading", original, null);
            BlockPos source = new BlockPos(110, 20, 30);
            ComponentRuntime runtime = new ComponentRuntime();
            runtime.replaceComponents(List.of(
                    component(new TestCapabilityHost(source, multipleSeeds
                            ? List.of(provider, new TestCapability("second")) : List.of(provider)), "source"),
                    component(new TestCapabilityHost(new BlockPos(111, 20, 30),
                            List.of(new TestCapability("historical_alias", original))), "historical_alias"),
                    component(new TestCapabilityHost(new BlockPos(112, 20, 30),
                            List.of(new TestCapability("unrelated", unrelated))), "unrelated")));
            var before = runtime.capabilityPresentations();
            long epoch = runtime.capabilityPresentationEpoch();
            int seeds = multipleSeeds ? 2 : 1;
            original.setAmount(19);
            replacement.setAmount(55);
            provider.valueStorage = replacement;
            List<List<ControllerRuntimeSnapshot.CapabilityPresentation>> intermediate = new ArrayList<>();
            provider.beforeFacetRead = () -> intermediate.add(runtime.capabilityPresentations());
            unrelated.resetReads();

            runtime.markCapabilityPresentationChanged(source);
            var after = runtime.capabilityPresentations();

            assertThat(intermediate).hasSize(1);
            assertThat(intermediate.getFirst().get(0).amount()).isEqualTo(55);
            assertThat(intermediate.getFirst().get(seeds).amount()).isZero();
            assertThat(after.get(0).amount()).isEqualTo(55);
            assertThat(after.get(seeds).amount()).isEqualTo(19);
            assertThat(after.get(seeds + 1)).isSameAs(before.get(seeds + 1));
            assertThat(before).allSatisfy(row -> assertThat(row.amount()).isZero());
            assertThat(runtime.capabilityPresentationEpoch()).isEqualTo(epoch + 1);
            assertThat(runtime.capabilityPresentations()).isSameAs(after);
            assertThat(unrelated.resourceReads).isZero();
            assertThat(unrelated.amountReads).isZero();
            assertThat(unrelated.capacityReads).isZero();
        }
    }

    @Test
    void unknown_null_and_noarg_notifications_refresh_all_sources() {
        CountingItemStorage items = new CountingItemStorage();
        LongValueStorage energy = new LongValueStorage(100, 100, () -> {});
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceComponents(List.of(
                component(new TestCapabilityHost(new BlockPos(31, 20, 30),
                        List.of(new TestCapability("items", items))), "items"),
                component(new TestCapabilityHost(new BlockPos(32, 20, 30),
                        List.of(new TestCapability("energy", energy))), "energy")));
        var before = runtime.capabilityPresentations();
        long version = runtime.capabilityVersion();
        for (int refresh = 0; refresh < 3; refresh++) {
            items.setContents(0, new ItemStack(Items.IRON_INGOT), refresh + 1);
            energy.setAmount(refresh + 10);
            items.resetReads();
            switch (refresh) {
                case 0 -> runtime.markCapabilityPresentationChanged(BlockPos.ZERO);
                case 1 -> runtime.markCapabilityPresentationChanged(null);
                default -> runtime.markCapabilityPresentationChanged();
            }
            var after = runtime.capabilityPresentations();

            assertThat(after.get(0)).isNotSameAs(before.get(0));
            assertThat(after.get(1)).isNotSameAs(before.get(1));
            assertThat(after.get(0).amount()).isEqualTo(refresh + 1);
            assertThat(after.get(1).amount()).isEqualTo(refresh + 10);
            assertThat(items.resourceReads).isEqualTo(1);
            assertThat(items.amountReads).isEqualTo(1);
            assertThat(items.capacityReads).isEqualTo(1);
            assertThat(runtime.capabilityPresentations()).isSameAs(after);
            assertThat(runtime.capabilityVersion()).isEqualTo(version);
            before = after;
        }
    }

    @Test
    void equal_but_distinct_provider_replacement_refreshes_only_its_rows_without_changing_version() {
        CountingItemStorage items = new CountingItemStorage();
        LongValueStorage energy = new LongValueStorage(100, 100, () -> {});
        TestCapability original = new TestCapability("energy", energy);
        TestCapability replacement = new TestCapability("energy", energy);
        BlockPos energyPos = new BlockPos(42, 20, 30);
        ProcessingComponent itemComponent = component(new TestCapabilityHost(new BlockPos(41, 20, 30),
                List.of(new TestCapability("items", items))), "items");
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceComponents(List.of(itemComponent,
                component(new TestCapabilityHost(energyPos, List.of(original)), "energy")));
        var before = runtime.capabilityPresentations();
        long version = runtime.capabilityVersion();
        items.resetReads();

        energy.setAmount(40);
        runtime.replaceComponents(List.of(itemComponent,
                component(new TestCapabilityHost(energyPos, List.of(replacement)), "energy")));
        var after = runtime.capabilityPresentations();

        assertThat(replacement).isEqualTo(original).isNotSameAs(original);
        assertThat(runtime.capabilities().get(1)).isSameAs(replacement);
        assertThat(runtime.capabilityVersion()).isEqualTo(version);
        assertThat(after.get(0)).isSameAs(before.get(0));
        assertThat(after.get(1).amount()).isEqualTo(40);
        assertThat(before.get(1).amount()).isZero();
        assertThat(items.resourceReads).isZero();
        assertThat(items.amountReads).isZero();
        assertThat(items.capacityReads).isZero();
        runtime.replaceComponents(runtime.components());
        assertThat(runtime.capabilityPresentations()).isSameAs(after);
    }

    @Test
    void same_provider_with_equal_but_distinct_storage_refreshes_without_changing_execution_identity() {
        CountingItemStorage first = new EqualItemStorage();
        CountingItemStorage replacement = new EqualItemStorage();
        first.setContents(0, new ItemStack(Items.IRON_INGOT), 12);
        replacement.setContents(0, new ItemStack(Items.GOLD_INGOT), 27);
        MutableTestCapability capability = new MutableTestCapability("items", first);
        ProcessingComponent component = component(new TestCapabilityHost(List.of(capability)), "items");
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceComponents(List.of(component));
        var before = runtime.capabilityPresentations();
        long version = runtime.capabilityVersion();
        first.resetReads();
        replacement.resetReads();

        capability.storage = replacement;
        runtime.replaceComponents(List.of(component));
        var after = runtime.capabilityPresentations();

        assertThat(runtime.capabilityVersion()).isEqualTo(version);
        assertThat(after.getFirst().amount()).isEqualTo(27);
        assertThat(after.getFirst().slots().getFirst().resourceId()).isEqualTo(Items.GOLD_INGOT.toString());
        assertThat(before.getFirst().amount()).isEqualTo(12);
        assertThat(first.resourceReads).isZero();
        assertThat(first.amountReads).isZero();
        assertThat(first.capacityReads).isZero();
        assertThat(replacement.resourceReads).isEqualTo(1);
        assertThat(replacement.amountReads).isEqualTo(1);
        assertThat(replacement.capacityReads).isEqualTo(1);
    }

    @Test
    void different_providers_sharing_value_storage_refresh_together_after_dynamic_rebinding() {
        LongValueStorage original = new LongValueStorage(100, 100, () -> {});
        LongValueStorage shared = new LongValueStorage(100, 100, () -> {});
        CountingItemStorage unrelated = new CountingItemStorage();
        MutableTestCapability first = new MutableTestCapability("first", original);
        MutableTestCapability second = new MutableTestCapability("second", shared);
        BlockPos firstPos = new BlockPos(91, 20, 30);
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceComponents(List.of(
                component(new TestCapabilityHost(firstPos, List.of(first)), "first"),
                component(new TestCapabilityHost(new BlockPos(92, 20, 30), List.of(second)), "second"),
                component(new TestCapabilityHost(new BlockPos(93, 20, 30),
                        List.of(new TestCapability("unrelated", unrelated))), "unrelated"),
                component(new TestCapabilityHost(new BlockPos(98, 20, 30),
                        List.of(new TestCapability("former_alias", original))), "former_alias")));
        var before = runtime.capabilityPresentations();
        long version = runtime.capabilityVersion();
        unrelated.resetReads();

        first.storage = shared;
        shared.setAmount(40);
        runtime.markCapabilityPresentationChanged(firstPos);
        var after = runtime.capabilityPresentations();

        assertThat(after).extracting(ControllerRuntimeSnapshot.CapabilityPresentation::amount)
                .containsExactly(40L, 40L, 0L, 0L);
        assertThat(after.get(2)).isSameAs(before.get(2));
        assertThat(before.get(0).amount()).isZero();
        assertThat(before.get(1).amount()).isZero();
        assertThat(unrelated.resourceReads).isZero();
        assertThat(unrelated.amountReads).isZero();
        assertThat(unrelated.capacityReads).isZero();
        assertThat(runtime.capabilityVersion()).isEqualTo(version);

        shared.setAmount(55);
        runtime.markCapabilityPresentationChanged(new BlockPos(92, 20, 30));
        var changedAgain = runtime.capabilityPresentations();
        assertThat(changedAgain)
                .extracting(ControllerRuntimeSnapshot.CapabilityPresentation::amount)
                .containsExactly(55L, 55L, 0L, 0L);
        assertThat(after.get(0).amount()).isEqualTo(40L);

        original.setAmount(12L);
        runtime.markCapabilityPresentationChanged(new BlockPos(98, 20, 30));
        var formerAliasChanged = runtime.capabilityPresentations();
        assertThat(formerAliasChanged.get(0)).isSameAs(changedAgain.get(0));
        assertThat(formerAliasChanged.get(1)).isSameAs(changedAgain.get(1));
        assertThat(formerAliasChanged.get(2)).isSameAs(before.get(2));
        assertThat(formerAliasChanged.get(3).amount()).isEqualTo(12L);
    }

    @Test
    void shared_resource_storage_uses_identity_and_does_not_refresh_equal_distinct_storage() {
        CountingItemStorage shared = new EqualItemStorage();
        CountingItemStorage unrelated = new EqualItemStorage();
        BlockPos source = new BlockPos(94, 20, 30);
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceComponents(List.of(
                component(new TestCapabilityHost(source,
                        List.of(new TestResourceCapability("first", shared))), "first"),
                component(new TestCapabilityHost(new BlockPos(95, 20, 30),
                        List.of(new TestResourceCapability("second", shared))), "second"),
                component(new TestCapabilityHost(new BlockPos(96, 20, 30),
                        List.of(new TestResourceCapability("unrelated", unrelated))), "unrelated")));
        var before = runtime.capabilityPresentations();
        shared.resetReads();
        unrelated.resetReads();
        shared.setContents(0, new ItemStack(Items.GOLD_INGOT), 27L);
        runtime.markCapabilityPresentationChanged(source);
        var after = runtime.capabilityPresentations();

        assertThat(shared).isEqualTo(unrelated).isNotSameAs(unrelated);
        assertThat(after.get(0).amount()).isEqualTo(27L);
        assertThat(after.get(1).amount()).isEqualTo(27L);
        assertThat(after.get(1).slots()).isEqualTo(after.get(0).slots());
        assertThat(after.get(2)).isSameAs(before.get(2));
        assertThat(before.get(0).amount()).isZero();
        assertThat(before.get(1).amount()).isZero();
        assertThat(shared.resourceReads).isEqualTo(2);
        assertThat(shared.amountReads).isEqualTo(2);
        assertThat(shared.capacityReads).isEqualTo(2);
        assertThat(unrelated.resourceReads).isZero();
        assertThat(unrelated.amountReads).isZero();
        assertThat(unrelated.capacityReads).isZero();
    }

    @Test
    void dirty_refresh_then_return_to_original_storage_rebinds_published_provenance() {
        for (boolean equalStorage : List.of(false, true)) {
            CapabilityStorage original = equalStorage ? new EqualItemStorage() : new LongValueStorage(100, 100, () -> {});
            CapabilityStorage replacement = equalStorage ? new EqualItemStorage() : new LongValueStorage(100, 100, () -> {});
            if (replacement instanceof LongValueStorage value) value.setAmount(40L);
            else ((CountingItemStorage) replacement).setContents(0, new ItemStack(Items.GOLD_INGOT), 40L);
            MutableTestCapability capability = new MutableTestCapability("stored", original);
            BlockPos source = new BlockPos(97, 20, 30);
            ComponentRuntime runtime = new ComponentRuntime();
            runtime.replaceComponents(List.of(component(new TestCapabilityHost(source, List.of(capability)), "stored")));
            var before = runtime.capabilityPresentations();
            long version = runtime.capabilityVersion();

            capability.storage = replacement;
            if (equalStorage) runtime.markCapabilityPresentationChanged();
            else runtime.markCapabilityPresentationChanged(source);
            var refreshed = runtime.capabilityPresentations();
            assertThat(refreshed.getFirst().amount()).isEqualTo(40L);
            long epoch = runtime.capabilityPresentationEpoch();
            capability.storage = original;
            runtime.replaceComponents(runtime.components());
            var rebound = runtime.capabilityPresentations();

            assertThat(runtime.capabilityPresentationEpoch()).isEqualTo(epoch + 1L);
            assertThat(runtime.capabilityVersion()).isEqualTo(version);
            assertThat(rebound.getFirst()).isNotSameAs(refreshed.getFirst());
            assertThat(rebound.getFirst().amount()).isZero();
            assertThat(before.getFirst().amount()).isZero();
            assertThat(refreshed.getFirst().amount()).isEqualTo(40L);
            if (replacement instanceof CountingItemStorage items) {
                items.resetReads();
                runtime.markCapabilityPresentationChanged(source);
                runtime.capabilityPresentations();
                assertThat(items.resourceReads).isZero();
                assertThat(items.amountReads).isZero();
                assertThat(items.capacityReads).isZero();
            }
        }
    }

    @Test
    void dirty_presentations_resolve_current_storage_even_without_component_replacement() {
        LongValueStorage original = new LongValueStorage(100, 100, () -> {});
        LongValueStorage replacement = new LongValueStorage(100, 100, () -> {});
        replacement.setAmount(40);
        MutableTestCapability capability = new MutableTestCapability("stored", original);
        BlockPos source = new BlockPos(45, 20, 30);
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceComponents(List.of(component(new TestCapabilityHost(source, List.of(capability)), "stored")));
        var before = runtime.capabilityPresentations();
        long version = runtime.capabilityVersion();

        capability.storage = replacement;
        runtime.markCapabilityPresentationChanged();
        var fullRefresh = runtime.capabilityPresentations();
        assertThat(fullRefresh.getFirst().amount()).isEqualTo(40);
        assertThat(before.getFirst().amount()).isZero();

        CountingItemStorage items = new CountingItemStorage();
        items.setContents(0, new ItemStack(Items.IRON_INGOT), 12);
        capability.storage = items;
        runtime.markCapabilityPresentationChanged(source);
        var localRefresh = runtime.capabilityPresentations();
        assertThat(localRefresh.getFirst().amount()).isEqualTo(12);
        assertThat(localRefresh.getFirst().slots()).hasSize(1);
        assertThat(fullRefresh.getFirst().amount()).isEqualTo(40);
        assertThat(items.resourceReads).isEqualTo(1);
        assertThat(items.amountReads).isEqualTo(1);
        assertThat(items.capacityReads).isEqualTo(1);
        assertThat(runtime.capabilityVersion()).isEqualTo(version);
    }

    @Test
    void clear_releases_old_sources_and_rebinding_the_same_position_uses_the_new_storage() {
        CountingItemStorage oldItems = new CountingItemStorage();
        CountingItemStorage newItems = new CountingItemStorage();
        newItems.setContents(0, new ItemStack(Items.IRON_INGOT), 12);
        BlockPos source = new BlockPos(51, 20, 30);
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceComponents(List.of(component(new TestCapabilityHost(source,
                List.of(new TestCapability("items", oldItems))), "old")));
        var before = runtime.capabilityPresentations();
        oldItems.resetReads();

        runtime.clear();
        assertThat(runtime.components()).isEmpty();
        assertThat(runtime.capabilities()).isEmpty();
        assertThat(runtime.capabilityPresentations()).isEmpty();
        runtime.markCapabilityPresentationChanged(source);
        assertThat(runtime.capabilityPresentations()).isEmpty();
        runtime.replaceComponents(List.of(component(new TestCapabilityHost(source,
                List.of(new TestCapability("items", newItems))), "new")));
        var rebound = runtime.capabilityPresentations();
        newItems.setContents(0, new ItemStack(Items.GOLD_INGOT), 29);
        runtime.markCapabilityPresentationChanged(source);
        var after = runtime.capabilityPresentations();

        assertThat(before.getFirst().amount()).isZero();
        assertThat(rebound.getFirst().amount()).isEqualTo(12);
        assertThat(after.getFirst().amount()).isEqualTo(29);
        assertThat(oldItems.resourceReads).isZero();
        assertThat(oldItems.amountReads).isZero();
        assertThat(oldItems.capacityReads).isZero();
    }

    @Test
    void component_reordering_and_removal_rebuild_the_source_mapping_and_keep_row_order() {
        CountingItemStorage items = new CountingItemStorage();
        items.setContents(0, new ItemStack(Items.IRON_INGOT), 12);
        LongValueStorage energy = new LongValueStorage(100, 100, () -> {});
        energy.setAmount(40);
        BlockPos itemPos = new BlockPos(61, 20, 30);
        BlockPos energyPos = new BlockPos(62, 20, 30);
        ProcessingComponent itemComponent = component(new TestCapabilityHost(itemPos,
                List.of(new TestCapability("items", items))), "items");
        ProcessingComponent energyComponent = component(new TestCapabilityHost(energyPos,
                List.of(new TestCapability("energy", energy))), "energy");
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceComponents(List.of(itemComponent, energyComponent));
        var before = runtime.capabilityPresentations();

        runtime.replaceComponents(List.of(energyComponent, itemComponent));
        var reordered = runtime.capabilityPresentations();
        assertThat(reordered).extracting(ControllerRuntimeSnapshot.CapabilityPresentation::amount)
                .containsExactly(40L, 12L);
        items.resetReads();
        energy.setAmount(55);
        runtime.markCapabilityPresentationChanged(energyPos);
        var updated = runtime.capabilityPresentations();
        assertThat(updated.get(0).amount()).isEqualTo(55);
        assertThat(updated.get(1)).isSameAs(reordered.get(1));
        assertThat(items.resourceReads).isZero();
        assertThat(items.amountReads).isZero();
        assertThat(items.capacityReads).isZero();

        runtime.replaceComponents(List.of(itemComponent));
        assertThat(runtime.capabilityPresentations()).hasSize(1);
        items.setContents(0, new ItemStack(Items.IRON_INGOT), 23);
        runtime.markCapabilityPresentationChanged(energyPos);
        assertThat(runtime.capabilityPresentations().getFirst().amount()).isEqualTo(23);
        assertThat(before).extracting(ControllerRuntimeSnapshot.CapabilityPresentation::amount)
                .containsExactly(12L, 40L);
    }

    @Test
    void source_positions_are_owned_and_rebinding_updates_the_absolute_source() {
        CountingItemStorage items = new CountingItemStorage();
        LongValueStorage energy = new LongValueStorage(100, 100, () -> {});
        BlockPos oldSource = new BlockPos(72, 20, 30);
        BlockPos.MutableBlockPos source = oldSource.mutable();
        ProcessingComponent energyComponent = new ProcessingComponent(null,
                new TestCapabilityHost(oldSource, List.of(new TestCapability("energy", energy))),
                source, BlockPos.ZERO, List.of("energy"), null);
        ProcessingComponent itemComponent = component(new TestCapabilityHost(new BlockPos(71, 20, 30),
                List.of(new TestCapability("items", items))), "items");
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceComponents(List.of(itemComponent, energyComponent));
        var before = runtime.capabilityPresentations();
        items.resetReads();
        source.set(73, 20, 30);
        energy.setAmount(40);

        runtime.markCapabilityPresentationChanged(oldSource);
        var ownedSourceRows = runtime.capabilityPresentations();
        assertThat(ownedSourceRows.get(0)).isSameAs(before.get(0));
        assertThat(ownedSourceRows.get(1).amount()).isEqualTo(40);
        assertThat(items.resourceReads).isZero();
        assertThat(items.amountReads).isZero();
        assertThat(items.capacityReads).isZero();

        runtime.replaceComponents(List.of(itemComponent, energyComponent));
        var rebound = runtime.capabilityPresentations();
        assertThat(rebound.get(0)).isSameAs(before.get(0));
        assertThat(rebound.get(1)).isNotSameAs(ownedSourceRows.get(1));
        energy.setAmount(55);
        runtime.markCapabilityPresentationChanged(source.immutable());
        var after = runtime.capabilityPresentations();
        assertThat(after.get(0)).isSameAs(before.get(0));
        assertThat(after.get(1).amount()).isEqualTo(55);
        assertThat(items.resourceReads).isZero();
        assertThat(items.amountReads).isZero();
        assertThat(items.capacityReads).isZero();
        items.setContents(0, new ItemStack(Items.IRON_INGOT), 12);
        runtime.markCapabilityPresentationChanged(oldSource);
        assertThat(runtime.capabilityPresentations().get(0).amount()).isEqualTo(12);
    }

    @Test
    void equivalent_component_rebinding_preserves_pending_local_refreshes_and_other_rows() {
        CountingItemStorage items = new CountingItemStorage();
        LongValueStorage energy = new LongValueStorage(100, 100, () -> {});
        TestCapabilityHost itemHost = new TestCapabilityHost(new BlockPos(81, 20, 30),
                List.of(new TestCapability("items", items)));
        BlockPos energyPos = new BlockPos(82, 20, 30);
        TestCapabilityHost energyHost = new TestCapabilityHost(energyPos,
                List.of(new TestCapability("energy", energy)));
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceComponents(List.of(component(itemHost, "items"), component(energyHost, "energy")));
        var before = runtime.capabilityPresentations();
        long version = runtime.capabilityVersion();
        items.resetReads();

        energy.setAmount(40);
        runtime.markCapabilityPresentationChanged(energyPos);
        runtime.replaceComponents(List.of(component(itemHost, "new_items"), component(energyHost, "new_energy")));
        var after = runtime.capabilityPresentations();

        assertThat(runtime.capabilityVersion()).isEqualTo(version);
        assertThat(after.get(0)).isSameAs(before.get(0));
        assertThat(after.get(1).amount()).isEqualTo(40);
        assertThat(before.get(1).amount()).isZero();
        assertThat(items.resourceReads).isZero();
        assertThat(items.amountReads).isZero();
        assertThat(items.capacityReads).isZero();
    }

    @Test
    void empty_resource_storage_presentation_keeps_capacity_without_null_resource_names() {
        LongItemStorage storage = new LongItemStorage(2, 100L, () -> {});
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceComponents(List.of(component(
                new TestCapabilityHost(List.of(new ItemBusCapability(storage, IOType.INPUT))), "empty")));

        ControllerRuntimeSnapshot.CapabilityPresentation presentation =
                runtime.capabilityPresentations().getFirst();

        assertThat(presentation.amount()).isZero();
        assertThat(presentation.capacity()).isEqualTo(200L);
        assertThat(presentation.slots()).hasSize(2);
        assertThat(presentation.slots()).allSatisfy(slot -> {
            assertThat(slot.resourceId()).isEmpty();
            assertThat(slot.amount()).isZero();
            assertThat(slot.capacity()).isEqualTo(100L);
        });
    }

    @Test
    void resource_presentation_saturates_multi_slot_long_amounts_and_capacity() {
        LongItemStorage storage = new LongItemStorage(2, Long.MAX_VALUE, () -> {});
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 1);
        storage.setContents(0, iron, Long.MAX_VALUE);
        storage.setContents(1, iron, Long.MAX_VALUE);
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceComponents(List.of(component(
                new TestCapabilityHost(List.of(new ItemBusCapability(storage, IOType.INPUT))), "items")));

        ControllerRuntimeSnapshot.CapabilityPresentation presentation = runtime.capabilityPresentations().getFirst();

        assertThat(presentation.amount()).isEqualTo(Long.MAX_VALUE);
        assertThat(presentation.capacity()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void bidirectional_capability_view_publishes_input_and_output_identities() {
        MachineCapability capability = new ViewBidirectionalCapability();
        ComponentRuntime runtime = new ComponentRuntime();

        runtime.replaceComponents(List.of(component(new TestCapabilityHost(List.of(capability)), "bidirectional")));

        assertThat(runtime.capabilityPresentations())
                .extracting(ControllerRuntimeSnapshot.CapabilityPresentation::ioType)
                .containsExactlyInAnyOrder(IOType.INPUT, IOType.OUTPUT);
        assertThat(new MachineIoView(new CapabilitySnapshot(List.of(capability))).displays()).hasSize(2);
    }

    @Test
    void structure_normalization_preserves_capacity_above_integer_maximum() {
        ResourceLocation machineId = ResourceLocation.fromNamespaceAndPath("mmcr_test", "long_parallel_machine");
        var parallelBlock = ModBlocks.BLOCKS.get("parallel_controller_ultimate").get();
        ParallelControllerBlockEntity first = new ParallelControllerBlockEntity(ParallelTier.ULTIMATE,
                new BlockPos(1, 0, 0), parallelBlock.defaultBlockState());
        ParallelControllerBlockEntity second = new ParallelControllerBlockEntity(ParallelTier.ULTIMATE,
                new BlockPos(2, 0, 0), parallelBlock.defaultBlockState());
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceComponents(List.of(component(first, "first"), component(second, "second")));

        Machine machine = new Machine() {
            @Override
            public ResourceLocation registryName() {
                return machineId;
            }

            @Override
            public BlockArray pattern() {
                return new BlockArray(Map.of());
            }

            @Override
            public MachineControllerSpec controller() {
                return MachineControllerSpec.defaultsFor(machineId);
            }

            @Override
            public long maxParallelism() {
                return Long.MAX_VALUE;
            }

            @Override
            public boolean parallelizable() {
                return true;
            }
        };

        assertThat(runtime.maxParallelism(machine)).isEqualTo(2L * Integer.MAX_VALUE);
    }

    @Test
    void parallelism_reads_changed_controller_configuration_without_replacing_components() {
        var parallelBlock = ModBlocks.BLOCKS.get("parallel_controller_ultimate").get();
        ParallelControllerBlockEntity first = new ParallelControllerBlockEntity(ParallelTier.ULTIMATE,
                new BlockPos(1, 0, 0), parallelBlock.defaultBlockState());
        ParallelControllerBlockEntity second = new ParallelControllerBlockEntity(ParallelTier.ULTIMATE,
                new BlockPos(2, 0, 0), parallelBlock.defaultBlockState());
        first.setCurrentParallelism(4);
        second.setCurrentParallelism(7);
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceComponents(List.of(component(first, "first"),
                new ProcessingComponent(null, "input", BlockPos.ZERO), component(second, "second")));
        Machine machine = parallelizableMachine(ResourceLocation.fromNamespaceAndPath("mmcr_test", "live_parallel_machine"));
        long stateVersion = runtime.stateVersion();
        long capabilityVersion = runtime.capabilityVersion();

        assertThat(runtime.maxParallelism(machine)).isEqualTo(11L);

        first.setCurrentParallelism(19);

        assertThat(runtime.maxParallelism(machine)).isEqualTo(26L);
        assertThat(runtime.stateVersion()).isEqualTo(stateVersion);
        assertThat(runtime.capabilityVersion()).isEqualTo(capabilityVersion);
    }

    @Test
    void parallelism_applies_additive_modifiers_before_multiplication_and_clamps_the_result() {
        var parallelBlock = ModBlocks.BLOCKS.get("parallel_controller_ultimate").get();
        ParallelControllerBlockEntity controller = new ParallelControllerBlockEntity(ParallelTier.ULTIMATE,
                new BlockPos(1, 0, 0), parallelBlock.defaultBlockState());
        controller.setCurrentParallelism(10);
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceComponents(List.of(component(controller, "controller")));
        runtime.replaceModifiers(Map.of("parallelism", List.of(
                MachineModifier.numeric("parallelism", "machine", 2D, "multiply", false),
                MachineModifier.numeric("parallelism", "machine", -3D, "add", false))));
        Machine machine = parallelizableMachine(ResourceLocation.fromNamespaceAndPath("mmcr_test", "modified_parallel_machine"));

        assertThat(runtime.maxParallelism(machine)).isEqualTo(14L);

        controller.setCurrentParallelism(2);

        assertThat(runtime.maxParallelism(machine)).isEqualTo(1L);
    }

    @Test
    void parallelism_without_controllers_uses_the_minimum_before_modifiers_and_saturates_at_machine_limit() {
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceComponents(List.of(new ProcessingComponent(null, "input", BlockPos.ZERO)));
        ResourceLocation machineId = ResourceLocation.fromNamespaceAndPath("mmcr_test", "bounded_parallel_machine");
        Machine machine = parallelizableMachine(machineId);

        assertThat(runtime.maxParallelism(machine)).isEqualTo(1L);

        runtime.replaceModifiers(Map.of("parallelism", List.of(
                MachineModifier.numeric("parallelism", "machine", 3D, "add", false),
                MachineModifier.numeric("parallelism", "machine", 2D, "multiply", false))));

        assertThat(runtime.maxParallelism(machine)).isEqualTo(8L);

        runtime.replaceModifiers(Map.of("parallelism", List.of(
                MachineModifier.numeric("parallelism", "machine", Double.MAX_VALUE, "multiply", false),
                MachineModifier.numeric("parallelism", "machine", 2D, "multiply", false))));

        assertThat(runtime.maxParallelism(machine)).isEqualTo(Long.MAX_VALUE);
        assertThat(runtime.maxParallelism(parallelizableMachine(machineId, 5L))).isEqualTo(5L);
        assertThat(runtime.maxParallelism(parallelizableMachine(machineId, 0L))).isEqualTo(1L);
    }

    @Test
    void negative_level_parallelism_bonus_reduces_the_effective_limit_without_wrapping() {
        ResourceLocation machineId = ResourceLocation.fromNamespaceAndPath("mmcr_test", "negative_parallel_machine");
        ResourceLocation levelId = ResourceLocation.fromNamespaceAndPath("mmcr_test", "negative_parallel_level");
        var parallelBlock = ModBlocks.BLOCKS.get("parallel_controller_ultimate").get();
        ParallelControllerBlockEntity controller = new ParallelControllerBlockEntity(ParallelTier.ULTIMATE,
                new BlockPos(1, 0, 0), parallelBlock.defaultBlockState());
        MachineLevel level = new MachineLevel(levelId, levelId, 1, new BlockPredicate.Any(), ItemStack.EMPTY,
                new ModifierDefinition(List.of(MachineModifier.numeric(
                        "parallelism", "machine", -Integer.MAX_VALUE, "add", false))));
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceComponents(List.of(component(controller, "controller")));
        runtime.replaceLevels(Map.of(levelId, level));

        Machine machine = parallelizableMachine(machineId);

        assertThat(runtime.maxParallelism(machine)).isEqualTo(1L);
    }

    @Test
    void parallelism_saturating_add_clamps_both_long_overflow_directions() throws Exception {
        Method saturatingAdd = ComponentRuntime.class.getDeclaredMethod("saturatingAdd", long.class, long.class);
        saturatingAdd.setAccessible(true);

        assertThat((long) saturatingAdd.invoke(null, Long.MAX_VALUE, 1L)).isEqualTo(Long.MAX_VALUE);
        assertThat((long) saturatingAdd.invoke(null, Long.MIN_VALUE, -1L)).isEqualTo(Long.MIN_VALUE);
    }

    @Test
    void modifier_version_changes_only_for_effective_modifier_changes_and_preserves_order() {
        MachineModifier first = MachineModifier.numeric("duration", "input", 1D, "add", false);
        MachineModifier second = MachineModifier.numeric("output", "output", 2D, "multiply", false);
        Map<String, List<MachineModifier>> modifiers = new LinkedHashMap<>();
        modifiers.put("first", List.of(first));
        modifiers.put("second", List.of(second));
        ComponentRuntime runtime = new ComponentRuntime();

        runtime.replaceModifiers(modifiers);
        long version = runtime.modifierVersion();
        runtime.replaceModifiers(new LinkedHashMap<>(modifiers));

        assertThat(runtime.modifierVersion()).isEqualTo(version);
        assertThat(runtime.modifierList()).containsExactly(first, second);
        modifiers.put("third", List.of(first));
        runtime.replaceModifiers(modifiers);
        assertThat(runtime.modifierVersion()).isEqualTo(version + 1);
        assertThat(runtime.modifierList()).containsExactly(first, second, first);
    }

    @Test
    void modifier_list_reuses_immutable_content_until_modifiers_change() {
        MachineModifier modifier = MachineModifier.numeric("duration", "input", 1D, "add", false);
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceModifiers(Map.of("cached", List.of(modifier)));

        List<MachineModifier> first = runtime.modifierList();
        List<MachineModifier> second = runtime.modifierList();

        assertThat(second).isSameAs(first);
        assertThat(first).containsExactly(modifier);
        assertThatThrownBy(first::clear).isInstanceOf(UnsupportedOperationException.class);

        runtime.replaceModifiers(Map.of("changed", List.of(modifier)));

        assertThat(runtime.modifierList()).isNotSameAs(first);
        assertThat(runtime.modifierList()).containsExactly(modifier);
    }

    @Test
    void factory_search_context_defaults_optional_lists_and_parallelism() {
        ControllerRuntimeSnapshot snapshot = new ControllerRuntimeSnapshot(
                StructureSnapshot.empty(), 0L, 0L, 0L, Map.of(), Map.of(), Set.of(),
                ModuleConnectionStatus.disconnected(), 0,
                CraftingStateSnapshot.empty(0L, 0L, 0L), FactorySnapshot.empty(),
                List.of(), List.of(), List.of(), "", "", 0, false, false, 0, 0, 1, Map.of());
        FactorySearchContext context = new FactorySearchContext(snapshot, null, null, null,
                3L, 4L, 0, 5L);

        assertThat(context.orderedCandidates()).isEmpty();
        assertThat(context.capabilities()).isEmpty();
        assertThat(context.modifiers()).isEmpty();
        assertThat(context.maxParallelism()).isEqualTo(1);
        assertThatThrownBy(() -> new FactorySearchContext(null, List.of(), List.of(), List.of(),
                0L, 0L, 1, 0L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void levels_links_and_module_state_are_published_in_immutable_component_views() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mmcr_test", "level");
        MachineLevel level = new MachineLevel(id, id, 1, new BlockPredicate.Any(),
                ItemStack.EMPTY, ModifierDefinition.EMPTY);
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceLevels(Map.of(id, level));
        runtime.replaceLinkedPortPositions(Set.of(BlockPos.ZERO));

        assertThat(runtime.foundLevels()).containsEntry(id, level);
        assertThat(runtime.linkedPortPositions()).containsExactly(BlockPos.ZERO);
        assertThat(runtime.moduleConnectionStatus()).isEqualTo(ModuleConnectionStatus.disconnected());
        assertThat(runtime.installedModuleCount()).isZero();
    }

    @Test
    void controller_snapshot_publishes_module_state_and_count_together() {
        ResourceLocation hostId = ResourceLocation.fromNamespaceAndPath("mmcr_test", "host");
        ControllerRuntimeSnapshot snapshot = new ControllerRuntimeSnapshot(
                StructureSnapshot.empty(), 0L, 0L, 0L, Map.of(), Map.of(), Set.of(),
                ModuleConnectionStatus.connected(hostId), 2,
                CraftingStateSnapshot.empty(0L, 0L, 0L),
                FactorySnapshot.empty(), List.of(), List.of(), List.of(), "", "", 0,
                false, false, 0, 0, 1, Map.of());

        assertThat(snapshot.moduleConnectionStatus()).isEqualTo(ModuleConnectionStatus.connected(hostId));
        assertThat(snapshot.installedModuleCount()).isEqualTo(2);
        assertThatThrownBy(() -> snapshot.foundModifiers().put("x", List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void upgrade_bus_items_aggregate_modifier_units_across_buses_without_merging_snapshots() {
        ResourceLocation speedupId = ResourceLocation.fromNamespaceAndPath("mmcr_test", "speedup");
        ItemStack speedup = new ItemStack(Items.IRON_INGOT, 2);
        ItemStack sameSpeedup = new ItemStack(Items.IRON_INGOT, 3);
        ItemStack differentSpeedup = new ItemStack(Items.IRON_INGOT, 7);
        differentSpeedup.set(DataComponents.MAX_STACK_SIZE, 16);
        ModifierRegistry.installSnapshot(Map.of(speedupId,
                        new ModifierDefinition(List.of(
                                MachineModifier.numeric("parallelism", "machine", 1D, "add", false)))),
                Map.of(speedupId, List.of(speedup)));

        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceUpgradeBuses(List.of(
                new ComponentRuntime.UpgradeBusSnapshot(new BlockPos(1, 0, 0), List.of(sameSpeedup)),
                new ComponentRuntime.UpgradeBusSnapshot(BlockPos.ZERO, List.of(speedup, ItemStack.EMPTY, differentSpeedup))));

        assertThat(runtime.upgradeItems()).hasSize(3);
        assertThat(runtime.upgradeItems()).extracting(ItemStack::getCount).containsExactly(2, 7, 3);
        assertThat(runtime.upgradeItems()).allSatisfy(stack -> assertThat(stack).isNotSameAs(speedup)
                .isNotSameAs(sameSpeedup).isNotSameAs(differentSpeedup));
        assertThat(runtime.upgradeModifierUnits().get(speedupId)).isEqualTo(5L);
        assertThat(runtime.modifierList()).containsExactly(
                MachineModifier.numeric("parallelism", "machine", 5D, "add", false));
    }

    @Test
    void upgrade_bus_changes_increment_state_and_modifier_versions_even_when_values_stay_equal() {
        ComponentRuntime runtime = new ComponentRuntime();
        ItemStack stack = new ItemStack(Items.IRON_INGOT, 1);
        runtime.replaceUpgradeBuses(List.of(new ComponentRuntime.UpgradeBusSnapshot(BlockPos.ZERO, List.of(stack))));
        long modifierVersion = runtime.modifierVersion();
        long stateVersion = runtime.stateVersion();

        runtime.refreshUpgradeBuses(List.of(new ComponentRuntime.UpgradeBusSnapshot(BlockPos.ZERO, List.of(stack.copy()))));

        assertThat(runtime.modifierVersion()).isEqualTo(modifierVersion + 1);
        assertThat(runtime.stateVersion()).isEqualTo(stateVersion + 1);
    }

    private static ProcessingComponent component(BlockEntity host, String tag) {
        return new ProcessingComponent(null, host, host.getBlockPos(), BlockPos.ZERO, List.of(tag), null);
    }

    private static Machine parallelizableMachine(ResourceLocation id) {
        return parallelizableMachine(id, Long.MAX_VALUE);
    }

    private static Machine parallelizableMachine(ResourceLocation id, long maxParallelism) {
        return new Machine() {
            @Override
            public ResourceLocation registryName() {
                return id;
            }

            @Override
            public BlockArray pattern() {
                return new BlockArray(Map.of());
            }

            @Override
            public MachineControllerSpec controller() {
                return MachineControllerSpec.defaultsFor(id);
            }

            @Override
            public long maxParallelism() {
                return maxParallelism;
            }

            @Override
            public boolean parallelizable() {
                return true;
            }
        };
    }

    private static final class TestCapabilityHost extends BlockEntity implements CapabilityHost {
        private final CapabilitySnapshot snapshot;
        private final List<MachineCapability> capabilities;

        private TestCapabilityHost(List<MachineCapability> capabilities) {
            this(new CapabilitySnapshot(capabilities), capabilities);
        }

        private TestCapabilityHost(BlockPos position, List<MachineCapability> capabilities) {
            this(position, new CapabilitySnapshot(capabilities), capabilities);
        }

        private TestCapabilityHost(CapabilitySnapshot snapshot, List<MachineCapability> capabilities) {
            this(BlockPos.ZERO, snapshot, capabilities);
        }

        private TestCapabilityHost(BlockPos position, CapabilitySnapshot snapshot, List<MachineCapability> capabilities) {
            super(ModBlockEntities.BES.get("item_input_bus").get(), position,
                    ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
            this.snapshot = snapshot;
            this.capabilities = List.copyOf(capabilities);
        }

        @Override
        public CapabilitySnapshot capabilitySnapshot() {
            return snapshot;
        }

        @Override
        public List<MachineCapability> capabilities() {
            return capabilities;
        }
    }

    /**
     * Counts presentation reads while retaining the real item storage behavior.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static class CountingItemStorage extends LongItemStorage implements CapabilityStorage {
        private int resourceReads;
        private int amountReads;
        private int capacityReads;

        private CountingItemStorage() {
            super(1, 100L, () -> {});
        }

        @Override
        public ItemStack resource(int slot) {
            resourceReads++;
            return super.resource(slot);
        }

        @Override
        public long amount(int slot) {
            amountReads++;
            return super.amount(slot);
        }

        @Override
        public long capacity(int slot) {
            capacityReads++;
            return super.capacity(slot);
        }

        @Override
        public Object contentFingerprint() { return List.of(resource(0), amount(0)); }

        private void resetReads() {
            resourceReads = 0;
            amountReads = 0;
            capacityReads = 0;
        }
    }

    /**
     * Models storage providers whose equality does not distinguish their backing instances.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class EqualItemStorage extends CountingItemStorage {
        @Override
        public boolean equals(Object other) {
            return other instanceof EqualItemStorage;
        }

        @Override
        public int hashCode() {
            return EqualItemStorage.class.hashCode();
        }
    }

    /**
     * Allows a host to replace storage while retaining the same capability instance.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class MutableTestCapability implements MachineCapability, ValueFacet<CapabilityStorage>, ItemHandlerFacet {
        private final TestCapability delegate;
        private final CapabilityView view;
        private CapabilityStorage storage;
        private int facetReads;
        private int storageReads;

        private MutableTestCapability(String id, CapabilityStorage storage) {
            delegate = new TestCapability(id);
            view = CapabilityFactories.view(delegate.type(), delegate.directions(), Set.of(ValueFacet.class, ItemHandlerFacet.class));
            this.storage = storage;
        }

        @Override
        public CapabilityType type() { return delegate.type(); }

        @Override
        public CapabilityDirections directions() { return delegate.directions(); }

        @Override
        public CapabilityView view() { return view; }

        @Override
        public CapabilityStorage storage() {
            storageReads++;
            return storage;
        }

        @Override
        public IItemHandler itemHandler() { return storage instanceof IItemHandler items ? items : null; }

        @Override
        public <F extends CapabilityFacet> Optional<F> facet(Class<F> facetType) {
            facetReads++;
            return MachineCapability.super.facet(facetType);
        }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) { return delegate.prepare(request); }
    }

    private record TestCapability(String id, CapabilityStorage storage, CapabilityDirections directions)
            implements MachineCapability, ValueFacet<CapabilityStorage>, ItemHandlerFacet {
        private TestCapability(String id) {
            this(id, null, CapabilityDirections.input());
        }

        private TestCapability(String id, CapabilityStorage storage) {
            this(id, storage, CapabilityDirections.input());
        }

        @Override
        public CapabilityType type() {
            return new CapabilityType(ResourceLocation.fromNamespaceAndPath("mmcr_test", id));
        }

        @Override
        public CapabilityDirections directions() {
            return directions;
        }

        @Override
        public CapabilityStorage storage() {
            return storage;
        }

        @Override
        public IItemHandler itemHandler() { return storage instanceof IItemHandler items ? items : null; }

        @Override
        public CapabilityView view() {
            return new CapabilityView() {
                @Override
                public CapabilityType type() {
                    return TestCapability.this.type();
                }

                @Override
                public CapabilityDirections directions() {
                    return TestCapability.this.directions();
                }

                @Override
                public Set<Class<? extends CapabilityFacet>> facets() {
                    return Set.of(ValueFacet.class, ItemHandlerFacet.class);
                }
            };
        }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) {
            return CapabilityResult::successful;
        }
    }

    /** Models a backing change while an earlier occurrence is publishing its rows.
     * @author howxu <dev@howxu.cn>
     */
    private static final class SwitchingItemStorage extends CountingItemStorage {
        private Runnable afterRead;

        @Override
        public long amount(int slot) {
            long amount = super.amount(slot);
            Runnable action = afterRead;
            afterRead = null;
            if (action != null) action.run();
            return amount;
        }
    }

    /** Delegates the two storage facets to distinct objects, rather than implementing them itself.
     * @author howxu <dev@howxu.cn>
     */
    private static final class SplitStorageCapability implements MachineCapability {
        private final TestCapability delegate;
        private Runnable beforeFacetRead;
        private CapabilityStorage valueStorage;
        private IItemHandler resourceStorage;
        private final ValueFacet<CapabilityStorage> valueFacet = () -> valueStorage;
        private final ItemHandlerFacet resourceFacet = () -> resourceStorage;

        private SplitStorageCapability(String id, CapabilityStorage value, IItemHandler resource) {
            delegate = new TestCapability(id);
            valueStorage = value;
            resourceStorage = resource;
        }

        @Override
        public CapabilityType type() { return delegate.type(); }

        @Override
        public CapabilityDirections directions() { return delegate.directions(); }

        @Override
        public CapabilityView view() {
            return CapabilityFactories.view(type(), directions(), Set.of(ValueFacet.class, ItemHandlerFacet.class));
        }

        @Override
        public <F extends CapabilityFacet> Optional<F> facet(Class<F> facetType) {
            Runnable action = beforeFacetRead;
            beforeFacetRead = null;
            if (action != null) action.run();
            if (facetType == ValueFacet.class) return Optional.of(facetType.cast(valueFacet));
            if (facetType == ItemHandlerFacet.class) return Optional.of(facetType.cast(resourceFacet));
            return MachineCapability.super.facet(facetType);
        }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) { return delegate.prepare(request); }
    }

    /** Exposes the typed resource facet separately from the value-only fixture.
     * @author howxu <dev@howxu.cn>
     */
    private record TestResourceCapability(String id, IItemHandler storage)
            implements MachineCapability, ItemHandlerFacet {
        @Override
        public IItemHandler itemHandler() { return storage; }

        @Override
        public CapabilityType type() { return new TestCapability(id).type(); }

        @Override
        public CapabilityDirections directions() { return CapabilityDirections.input(); }

        @Override
        public CapabilityView view() {
            return CapabilityFactories.view(type(), directions(), Set.of(ItemHandlerFacet.class));
        }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) {
            return CapabilityResult::successful;
        }
    }

    private static final class ViewBidirectionalCapability implements MachineCapability {
        private static final CapabilityType TYPE = new CapabilityType(
                ResourceLocation.fromNamespaceAndPath("mmcr_test", "view_bidirectional"));

        @Override
        public CapabilityType type() {
            return TYPE;
        }

        @Override
        public CapabilityDirections directions() {
            return CapabilityDirections.input();
        }

        @Override
        public CapabilityView view() {
            return new CapabilityView() {
                @Override
                public CapabilityType type() {
                    return TYPE;
                }

                @Override
                public CapabilityDirections directions() {
                    return CapabilityDirections.bidirectional();
                }
            };
        }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) {
            return CapabilityResult::successful;
        }
    }
}
