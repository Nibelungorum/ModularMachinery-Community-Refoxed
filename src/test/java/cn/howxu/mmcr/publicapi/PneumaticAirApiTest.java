package cn.howxu.mmcr.publicapi;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.compat.pneumaticcraft.PneumaticAirFacet;
import cn.howxu.mmcr.api.machine.definition.MachineIoView;
import cn.howxu.mmcr.api.machine.definition.BlockPredicate;
import cn.howxu.mmcr.api.machine.definition.InterfacePredicates;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.compat.kubejs.KubeJSApi;
import cn.howxu.mmcr.compat.kubejs.KubeJSInterfaceHelpers;
import cn.howxu.mmcr.compat.pneumaticcraft.AirRequirement;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticCraftBridgeBootstrap;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticIds;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticRecipeTypes;
import cn.howxu.mmcr.internal.api.facade.recipe.RecipeAdapters;
import cn.howxu.mmcr.internal.api.facade.recipe.RequirementAdapters;
import cn.howxu.mmcr.internal.api.facade.runtime.IoAdapters;
import cn.howxu.mmcr.internal.api.facade.structure.StructureAdapters;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import cn.howxu.mmcr.publicapi.recipe.requirement.AirRequirementSpec;
import cn.howxu.mmcr.publicapi.recipe.requirement.Requirements;
import cn.howxu.mmcr.publicapi.runtime.AirState;
import cn.howxu.mmcr.publicapi.structure.BlockConditions;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Typed declarations and immutable per-interface air queries without native PNC types.
 * @author howxu <dev@howxu.cn>
 */
class PneumaticAirApiTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        PneumaticRecipeTypes.register();
    }

    @Test
    void factories_and_copy_preserve_long_rates_direction_pressure_and_isolated_tags() {
        var tags = new ArrayList<>(List.of("drive"));
        var input = Requirements.airInput(10_000_000_000L, 4.25F, tags);
        var output = Requirements.airOutput(Long.MAX_VALUE, tags);
        tags.clear();

        assertThat(input.io()).isEqualTo(IoDirection.INPUT);
        assertThat(output.io()).isEqualTo(IoDirection.OUTPUT);
        assertThat(input.kindId()).isEqualTo(PneumaticIds.AIR);
        assertThat(input.airPerTick()).isEqualTo(10_000_000_000L);
        assertThat(input.minPressure()).isEqualTo(4.25F);
        assertThat(output.minPressure()).isZero();
        assertThat(RequirementAdapters.unwrap(input)).isEqualTo(AirRequirement.input(10_000_000_000L, 4.25F, List.of("drive")));
        var copy = (AirRequirementSpec) output.copy();
        assertThat(copy.airPerTick()).isEqualTo(Long.MAX_VALUE);
        assertThat(copy.tags()).containsExactly("drive");
        assertThat(RequirementAdapters.unwrap(copy)).isEqualTo(RequirementAdapters.unwrap(output));
        assertThatThrownBy(() -> copy.tags().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(Requirements.airInput(0, 8F).airPerTick()).isZero();
        assertThatThrownBy(() -> Requirements.airInput(-1, 4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Requirements.airInput(1, Float.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Requirements.airOutput(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void core_and_public_builders_keep_both_air_directions_as_typed_requirements() {
        var id = ResourceLocation.parse("test:pneumatic_api");
        var core = MachineRecipeBuilder.recipe(id).recipePool(id)
                .inputAir(40, 4F, List.of("drive")).outputAir(80, List.of("generator"))
                .inputAir(0, 8F).outputAir(10_000_000_000L).build();
        var recipe = RecipeAdapters.recipe(id).recipePool(id)
                .inputAir(40, 4F, List.of("drive")).outputAir(80, List.of("generator"))
                .inputAir(0, 8F).outputAir(10_000_000_000L).build();

        assertThat(RecipeAdapters.unwrap(recipe).requirements()).isEqualTo(core.requirements());
        assertThat(recipe.requirements()).allSatisfy(value -> assertThat(value).isInstanceOf(AirRequirementSpec.class));
        assertThat(core.customOutputs()).isEmpty();
        assertThat(core.energyInputs()).isEmpty();
        assertThat(core.energyOutputs()).isEmpty();
    }

    @Test
    void snapshots_keep_signed_individual_pressure_direction_tags_identity_dedupe_and_live_reads() {
        var identity = new Object();
        var input = new AirCapability(IOType.INPUT, List.of("drive"), new BlockPos(1, 2, 3), identity, 40_000);
        var duplicate = new AirCapability(IOType.INPUT, List.of("drive", "alias"), input.position, identity, 40_000);
        var second = new AirCapability(IOType.INPUT, List.of("other"), new BlockPos(2, 2, 3), new Object(), 80_000);
        var output = new AirCapability(IOType.OUTPUT, List.of("drive"), new BlockPos(3, 2, 3), identity, -20_000);
        var core = new MachineIoView(new CapabilitySnapshot(List.of(input, duplicate, second, output, output)));
        var snapshot = IoAdapters.wrap(core);

        assertThat(core.airInputs()).hasSize(2);
        assertThat(snapshot.airInputs()).extracting(AirState::position).containsExactly(input.position, second.position);
        assertThat(snapshot.airInputs()).extracting(AirState::pressure).containsExactly(4F, 8F);
        assertThat(snapshot.airOutputs()).extracting(AirState::pressure).containsExactly(-2F);
        assertThat(snapshot.forTags(Set.of("drive")).airInputs()).hasSize(1);
        assertThat(snapshot.forTags(Set.of("alias")).airInputs()).hasSize(1);
        assertThat(snapshot.forTags(Set.of("other")).airOutputs()).isEmpty();
        assertThat(snapshot.energyInput()).isZero();
        assertThat(snapshot.energyOutputCapacity()).isZero();
        var before = snapshot.airInputs().getFirst();
        input.air = -10_000;
        assertThat(before.air()).isEqualTo(40_000);
        assertThat(snapshot.airInputs().getFirst().pressure()).isEqualTo(-1F);
        var outputState = snapshot.airOutputs().getFirst();
        assertThat(outputState.outputCapacity()).isEqualTo(core.airOutputs().getFirst().outputCapacity());
        assertThat(outputState.air()).isEqualTo(-20_000);
        assertThat(outputState.dangerPressure()).isEqualTo(20F);
        assertThat(outputState.criticalPressure()).isEqualTo(25F);
        assertThatThrownBy(() -> snapshot.airInputs().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void public_state_copies_mutable_positions_and_retains_signed_air() {
        var position = new BlockPos.MutableBlockPos(1, 2, 3);
        var state = new AirState(position, -10_000, 10_000, 20, 25);
        position.set(9, 9, 9);
        assertThat(state.position()).isEqualTo(new BlockPos(1, 2, 3));
        assertThat(state.pressure()).isEqualTo(-1F);
        assertThatThrownBy(() -> new AirState(BlockPos.ZERO, 0, 0, 20, 25)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void query_identity_uses_reference_equality_instead_of_value_equality() {
        var first = new AirCapability(IOType.INPUT, List.of(), BlockPos.ZERO, new String("same"), 40_000);
        var second = new AirCapability(IOType.INPUT, List.of(), new BlockPos(1, 0, 0), new String("same"), 80_000);
        var snapshot = IoAdapters.wrap(new MachineIoView(new CapabilitySnapshot(List.of(first, second))));
        assertThat(snapshot.airInputs()).extracting(AirState::pressure).containsExactly(4F, 8F);
    }

    @Test
    void structure_helpers_select_registered_air_families_and_bindings_and_hide_missing_mod() {
        PneumaticCraftBridgeBootstrap.installForTesting(() -> true);
        try {
            PortKinds.clearForTesting();
            PortKinds.register(new AirPortKind("minecraft:stone", IOType.INPUT, IOType.INPUT));
            PortKinds.register(new AirPortKind("minecraft:dirt", IOType.OUTPUT, IOType.OUTPUT));
            PortKinds.register(new AirPortKind("minecraft:cobblestone", IOType.INPUT, IOType.OUTPUT));
            var input = Blocks.STONE.defaultBlockState();
            var output = Blocks.DIRT.defaultBlockState();
            var mismatch = Blocks.COBBLESTONE.defaultBlockState();
            var api = new KubeJSApi();

            assertThat(predicateBlocks(InterfacePredicates.anyOfAirInput())).containsExactly(input.getBlock());
            assertThat(predicateBlocks(InterfacePredicates.anyOfAirOutput())).containsExactly(output.getBlock());
            assertThat(predicateBlocks(InterfacePredicates.anyOfAirPorts())).doesNotContain(mismatch.getBlock());
            assertThat(InterfacePredicates.anyAirInput()).isEqualTo(InterfacePredicates.anyOfAirInput());
            assertThat(InterfacePredicates.anyAirOutput()).isEqualTo(InterfacePredicates.anyOfAirOutput());
            assertThat(InterfacePredicates.anyAirPorts()).isEqualTo(InterfacePredicates.anyOfAirPorts());
            assertThat(predicateBlocks(StructureAdapters.unwrap(BlockConditions.airInput()))).containsExactly(input.getBlock());
            assertThat(predicateBlocks(StructureAdapters.unwrap(BlockConditions.airOutput()))).containsExactly(output.getBlock());
            assertThat(predicateBlocks(StructureAdapters.unwrap(BlockConditions.airPorts()))).containsExactly(input.getBlock(), output.getBlock());
            assertThat(KubeJSInterfaceHelpers.anyOfAirInput().matches(input)).isTrue();
            assertThat(KubeJSInterfaceHelpers.anyOfAirInput().matches(output)).isFalse();
            assertThat(KubeJSInterfaceHelpers.anyOfAirOutput().matches(output)).isTrue();
            assertThat(KubeJSInterfaceHelpers.anyAirInput().matches(input)).isTrue();
            assertThat(KubeJSInterfaceHelpers.anyAirOutput().matches(output)).isTrue();
            assertThat(KubeJSInterfaceHelpers.anyAirPorts().matches(output)).isTrue();
            assertThat(KubeJSInterfaceHelpers.anyOfAirPorts().matches(mismatch)).isFalse();
            assertThat(api.anyOfAirInput().matches(input)).isTrue();
            assertThat(api.anyOfAirOutput().matches(output)).isTrue();
            assertThat(api.anyOfAirPorts().matches(input)).isTrue();
            assertThat(api.anyAirInput().matches(input)).isTrue();
            assertThat(api.anyAirOutput().matches(output)).isTrue();
            assertThat(api.anyAirPorts().matches(output)).isTrue();
            assertThat(predicateBlocks(InterfacePredicates.ports())).contains(input.getBlock(), output.getBlock());

            PneumaticCraftBridgeBootstrap.installForTesting(PneumaticCraftBridgeBootstrap.selectForTesting(false));
            assertThat(InterfacePredicates.anyOfAirInput().alternatives()).isEmpty();
            assertThat(InterfacePredicates.anyOfAirOutput().alternatives()).isEmpty();
            assertThat(InterfacePredicates.anyAirPorts().alternatives()).isEmpty();
            assertThat(predicateBlocks(StructureAdapters.unwrap(BlockConditions.airPorts()))).isEmpty();
            assertThat(KubeJSInterfaceHelpers.anyOfAirPorts().matches(input)).isFalse();
            assertThat(api.anyAirPorts().matches(output)).isFalse();
            assertThat(predicateBlocks(InterfacePredicates.ports())).doesNotContain(input.getBlock(), output.getBlock());
        } finally {
            PortKinds.clearForTesting();
            PneumaticCraftBridgeBootstrap.resetForTesting();
        }
    }

    private static List<Block> predicateBlocks(BlockPredicate predicate) {
        if (predicate.blockSupplier().isPresent()) return List.of(predicate.blockSupplier().orElseThrow().get());
        return predicate.alternatives().stream().flatMap(child -> predicateBlocks(child).stream()).toList();
    }

    /** Neutral family fixture with an independently selected capability binding direction.
     * @author howxu <dev@howxu.cn>
     */
    private record AirPortKind(String id, IOType ioType, IOType bindingDirection) implements IOPortKind {
        public BlockEntityType.BlockEntitySupplier<? extends BlockEntity> entityFactory() { return (position, state) -> null; }
        public List<PortFamilyDescriptor> families() {
            return List.of(new PortFamilyDescriptor(PneumaticIds.AIR, ioType, 0, List.of()));
        }
        public PortDefinition definition() {
            return PortDefinition.of(ResourceLocation.parse(id),
                    IOPortKind.binding(new CapabilityType(PneumaticIds.AIR), bindingDirection, families()));
        }
    }

    /** Snapshot fixture whose mutation methods fail if a read crosses the writable boundary.
     * @author howxu <dev@howxu.cn>
     */
    private static final class AirCapability implements MachineCapability, PneumaticAirFacet {
        private final IOType direction;
        private final List<String> tags;
        private final BlockPos position;
        private final Object identity;
        private int air;
        AirCapability(IOType direction, List<String> tags, BlockPos position, Object identity, int air) {
            this.direction = direction;
            this.tags = tags;
            this.position = position;
            this.identity = identity;
            this.air = air;
        }
        public CapabilityType type() { return new CapabilityType(PneumaticIds.AIR); }
        public CapabilityDirections directions() { return CapabilityDirections.of(direction); }
        public CapabilityView view() {
            return new CapabilityView() {
                public CapabilityType type() { return AirCapability.this.type(); }
                public CapabilityDirections directions() { return AirCapability.this.directions(); }
                public Set<Class<? extends CapabilityFacet>> facets() { return Set.of(PneumaticAirFacet.class); }
                public List<String> tags() { return tags; }
            };
        }
        public Object queryIdentity() { return identity; }
        public cn.howxu.mmcr.api.compat.pneumaticcraft.AirState state() {
            return new cn.howxu.mmcr.api.compat.pneumaticcraft.AirState(position, air, 10_000, 20, 25);
        }
        public CapabilityResult validate(long amount, boolean insert, float minPressure) { throw new AssertionError("read-only"); }
        public CapabilityResult apply(long amount, boolean insert, float minPressure) { throw new AssertionError("read-only"); }
        public CapabilityOperation prepare(CapabilityRequest request) { throw new AssertionError("read-only"); }
    }
}
