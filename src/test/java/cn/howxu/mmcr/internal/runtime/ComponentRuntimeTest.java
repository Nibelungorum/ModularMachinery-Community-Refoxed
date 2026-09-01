package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.storage.CapabilityStorage;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
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
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.api.machine.level.LevelModifier;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.ModifierRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.internal.recipe.FactorySearchContext;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.ParallelControllerBlockEntity;
import cn.howxu.mmcr.internal.storage.LongResourceStorage;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        Map<String, List<RecipeModifier>> modifiers = Map.of("modifier", List.of(
                new RecipeModifier("modifier", RecipeModifier.IOType.INPUT, 1F,
                        RecipeModifier.Operation.ADD, false)));
        Identifier levelId = Identifier.fromNamespaceAndPath("mmcr_test", "replacement_level");
        MachineLevel level = new MachineLevel(levelId, levelId, 1, new BlockPredicate.Any(),
                net.minecraft.world.item.ItemStack.EMPTY, LevelModifier.IDENTITY);
        ModuleConnectionStatus connection = ModuleConnectionStatus.connected(
                Identifier.fromNamespaceAndPath("mmcr_test", "host"));

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
    void empty_resource_storage_presentation_keeps_capacity_without_null_resource_names() {
        ResourceStorage<ItemResource> storage = new LongResourceStorage<>(
                ItemResource.class, 2, 100L, resource -> resource.isEmpty(), () -> {});
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceComponents(List.of(component(
                new TestCapabilityHost(List.of(new TestCapability("empty", storage))), "empty")));

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
    void energy_aggregate_saturates_when_multiple_long_capabilities_are_full() {
        LongValueStorage firstStorage = new LongValueStorage(Long.MAX_VALUE, Long.MAX_VALUE, () -> {});
        LongValueStorage secondStorage = new LongValueStorage(Long.MAX_VALUE, Long.MAX_VALUE, () -> {});
        firstStorage.setAmount(Long.MAX_VALUE);
        secondStorage.setAmount(Long.MAX_VALUE);
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceComponents(List.of(component(new TestCapabilityHost(List.of(
                new TestCapability("first", firstStorage), new TestCapability("second", secondStorage))), "energy")));

        assertThat(runtime.capabilityAggregate().storedEnergy()).isEqualTo(Long.MAX_VALUE);
        assertThat(runtime.capabilityAggregate().energyCapacity()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void resource_presentation_saturates_multi_slot_long_amounts_and_capacity() {
        LongResourceStorage<ItemResource> storage = new LongResourceStorage<>(
                ItemResource.class, 2, Long.MAX_VALUE, resource -> resource.isEmpty(), () -> {});
        ItemResource iron = ItemResource.of(net.minecraft.world.item.Items.IRON_INGOT);
        storage.setContents(0, iron, Long.MAX_VALUE);
        storage.setContents(1, iron, Long.MAX_VALUE);
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceComponents(List.of(component(
                new TestCapabilityHost(List.of(new TestCapability("items", storage))), "items")));

        ControllerRuntimeSnapshot.CapabilityPresentation presentation = runtime.capabilityPresentations().getFirst();

        assertThat(presentation.amount()).isEqualTo(Long.MAX_VALUE);
        assertThat(presentation.capacity()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void structure_normalization_preserves_capacity_above_integer_maximum() {
        Identifier machineId = Identifier.fromNamespaceAndPath("mmcr_test", "long_parallel_machine");
        var parallelBlock = ModBlocks.BLOCKS.get("parallel_controller_ultimate").get();
        ParallelControllerBlockEntity first = new ParallelControllerBlockEntity(ParallelTier.ULTIMATE,
                new BlockPos(1, 0, 0), parallelBlock.defaultBlockState());
        ParallelControllerBlockEntity second = new ParallelControllerBlockEntity(ParallelTier.ULTIMATE,
                new BlockPos(2, 0, 0), parallelBlock.defaultBlockState());
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceComponents(List.of(component(first, "first"), component(second, "second")));

        Machine machine = new Machine() {
            @Override
            public Identifier registryName() {
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
    void modifier_version_changes_only_for_effective_modifier_changes_and_preserves_order() {
        RecipeModifier first = new RecipeModifier("first", RecipeModifier.IOType.INPUT, 1F,
                RecipeModifier.Operation.ADD, false);
        RecipeModifier second = new RecipeModifier("second", RecipeModifier.IOType.OUTPUT, 2F,
                RecipeModifier.Operation.MULTIPLY, false);
        Map<String, List<RecipeModifier>> modifiers = new LinkedHashMap<>();
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
        RecipeModifier modifier = new RecipeModifier("cached", RecipeModifier.IOType.INPUT, 1F,
                RecipeModifier.Operation.ADD, false);
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceModifiers(Map.of("cached", List.of(modifier)));

        List<RecipeModifier> first = runtime.modifierList();
        List<RecipeModifier> second = runtime.modifierList();

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
                new ComponentRuntime.CapabilityAggregate(0L, 0L, null, null),
                CraftingStateSnapshot.empty(0L, 0L, 0L), FactorySnapshot.empty(),
                List.of(), List.of(), List.of(), "", "", 0, false, false, 0, 0, 1);
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
        Identifier id = Identifier.fromNamespaceAndPath("mmcr_test", "level");
        MachineLevel level = new MachineLevel(id, id, 1, new BlockPredicate.Any(),
                net.minecraft.world.item.ItemStack.EMPTY, LevelModifier.IDENTITY);
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
        Identifier hostId = Identifier.fromNamespaceAndPath("mmcr_test", "host");
        ControllerRuntimeSnapshot snapshot = new ControllerRuntimeSnapshot(
                StructureSnapshot.empty(), 0L, 0L, 0L, Map.of(), Map.of(), Set.of(),
                ModuleConnectionStatus.connected(hostId), 2,
                new ComponentRuntime.CapabilityAggregate(0L, 0L, null, null), CraftingStateSnapshot.empty(0L, 0L, 0L),
                FactorySnapshot.empty(), List.of(), List.of(), List.of(), "", "", 0,
                false, false, 0, 0, 1);

        assertThat(snapshot.moduleConnectionStatus()).isEqualTo(ModuleConnectionStatus.connected(hostId));
        assertThat(snapshot.installedModuleCount()).isEqualTo(2);
        assertThatThrownBy(() -> snapshot.foundModifiers().put("x", List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void upgrade_bus_items_aggregate_modifier_units_across_buses_without_merging_snapshots() {
        Identifier speedupId = Identifier.fromNamespaceAndPath("mmcr_test", "speedup");
        ItemStack speedup = new ItemStack(Items.IRON_INGOT, 2);
        ItemStack sameSpeedup = new ItemStack(Items.IRON_INGOT, 3);
        ItemStack differentSpeedup = new ItemStack(Items.IRON_INGOT, 7);
        differentSpeedup.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 16);
        ModifierRegistry.installSnapshot(Map.of(speedupId,
                        new cn.howxu.mmcr.api.publicapi.machine.ModifierDefinition(List.of(
                                new RecipeModifier("item", RecipeModifier.IOType.INPUT, 1F,
                                        RecipeModifier.Operation.ADD, false)))),
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
        assertThat(runtime.modifierList()).containsExactly(new RecipeModifier("item", RecipeModifier.IOType.INPUT,
                5F, RecipeModifier.Operation.ADD, false));
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
        return new ProcessingComponent(null, host, BlockPos.ZERO, BlockPos.ZERO, List.of(tag), null);
    }

    private static final class TestCapabilityHost extends BlockEntity implements CapabilityHost {
        private final CapabilitySnapshot snapshot;
        private final List<MachineCapability> capabilities;

        private TestCapabilityHost(List<MachineCapability> capabilities) {
            this(new CapabilitySnapshot(capabilities), capabilities);
        }

        private TestCapabilityHost(CapabilitySnapshot snapshot, List<MachineCapability> capabilities) {
            super(ModBlockEntities.BES.get("item_input_bus").get(), BlockPos.ZERO,
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

    private record TestCapability(String id, CapabilityStorage storage)
            implements MachineCapability, ValueFacet<CapabilityStorage> {
        private TestCapability(String id) {
            this(id, null);
        }

        @Override
        public CapabilityType type() {
            return new CapabilityType(Identifier.fromNamespaceAndPath("mmcr_test", id));
        }

        @Override
        public IOType ioType() {
            return IOType.INPUT;
        }

        @Override
        public CapabilityStorage storage() {
            return storage;
        }

        @Override
        public CapabilityView view() {
            return new CapabilityView() {
                @Override
                public CapabilityType type() {
                    return TestCapability.this.type();
                }

                @Override
                public IOType ioType() {
                    return TestCapability.this.ioType();
                }

                @Override
                public Set<Class<? extends CapabilityFacet>> facets() {
                    return Set.of(ValueFacet.class);
                }
            };
        }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) {
            return transaction -> CapabilityResult.successful();
        }
    }
}
