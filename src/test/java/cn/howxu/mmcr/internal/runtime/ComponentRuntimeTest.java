package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.level.LevelModifier;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
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

    private static ProcessingComponent component(BlockEntity host, String tag) {
        return new ProcessingComponent(null, host, BlockPos.ZERO, BlockPos.ZERO, List.of(tag), null);
    }

    private static final class TestCapabilityHost extends BlockEntity implements CapabilityHost {
        private final CapabilitySnapshot snapshot;

        private TestCapabilityHost(List<MachineCapability> capabilities) {
            super(ModBlockEntities.BES.get("item_input_bus").get(), BlockPos.ZERO,
                    ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
            snapshot = new CapabilitySnapshot(capabilities);
        }

        @Override
        public CapabilitySnapshot capabilitySnapshot() {
            return snapshot;
        }
    }

    private record TestCapability(String id) implements MachineCapability {
        @Override
        public CapabilityType type() {
            return new CapabilityType(Identifier.fromNamespaceAndPath("mmcr_test", id));
        }

        @Override
        public IOType ioType() {
            return IOType.INPUT;
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
            };
        }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) {
            return transaction -> CapabilityResult.successful();
        }
    }
}
