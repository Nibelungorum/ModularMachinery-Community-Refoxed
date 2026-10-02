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
import cn.howxu.mmcr.api.compat.create.StressFacet;
import cn.howxu.mmcr.api.machine.definition.MachineIoView;
import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.compat.create.CreateRecipeTypes;
import cn.howxu.mmcr.compat.create.StressRequirement;
import cn.howxu.mmcr.compat.create.StressSession;
import cn.howxu.mmcr.internal.api.facade.recipe.RecipeAdapters;
import cn.howxu.mmcr.internal.api.facade.recipe.RequirementAdapters;
import cn.howxu.mmcr.internal.api.facade.runtime.IoAdapters;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import cn.howxu.mmcr.publicapi.recipe.requirement.Requirements;
import cn.howxu.mmcr.publicapi.recipe.requirement.StressRequirementSpec;
import cn.howxu.mmcr.publicapi.runtime.StressState;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import dev.latvian.mods.rhino.ContextFactory;
import dev.latvian.mods.rhino.ScriptableObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies typed stress declarations and the read-only public boundary.
 * @author howxu <dev@howxu.cn>
 */
class CreateStressApiTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        CreateRecipeTypes.register();
    }

    @Test
    void factories_and_copy_preserve_canonical_type_direction_tags_and_signed_rpm() {
        var tags = new ArrayList<>(List.of("drive"));
        var input = Requirements.stressInput(8, 32, tags);
        var output = Requirements.stressOutput(16, -64, tags);
        tags.clear();

        assertThat(input.io()).isEqualTo(IoDirection.INPUT);
        assertThat(output.io()).isEqualTo(IoDirection.OUTPUT);
        assertThat(input.tags()).containsExactly("drive");
        assertThat(output.kindId()).isEqualTo(ResourceLocation.parse("create:stress"));
        assertThat(RequirementAdapters.unwrap(input)).isEqualTo(StressRequirement.input(8, 32, List.of("drive")));
        var copy = (StressRequirementSpec) output.copy();
        assertThat(copy.rpm()).isEqualTo(-64);
        assertThat(RequirementAdapters.unwrap(copy)).isEqualTo(RequirementAdapters.unwrap(output));
        assertThatThrownBy(() -> copy.tags().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void core_and_public_builders_keep_stress_output_as_a_requirement() {
        var id = ResourceLocation.parse("test:stress_api");
        var core = MachineRecipeBuilder.recipe(id).recipePool(id)
                .inputStress(8, 32, List.of("drive")).outputStress(16, -64, List.of("generator")).build();
        var draft = RecipeAdapters.recipe(id).recipePool(id)
                .inputStress(8, 32, List.of("drive")).outputStress(16, -64, List.of("generator")).build();

        assertThat(RecipeAdapters.unwrap(draft).requirements()).isEqualTo(core.requirements());
        assertThat(draft.requirements()).allSatisfy(value -> assertThat(value).isInstanceOf(StressRequirementSpec.class));
        assertThat(core.customOutputs()).isEmpty();
        assertThat(core.energyInputs()).isEmpty();
        assertThat(core.energyOutputs()).isEmpty();
        assertThatThrownBy(() -> Requirements.stressOutput(8, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MachineRecipeBuilder.recipe(id).inputStress(8, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void snapshot_keeps_per_port_states_tags_direction_and_live_reads_without_fe_aggregation() {
        var input = new StressCapability(IOType.INPUT, List.of("drive"), new BlockPos(1, 2, 3));
        var second = new StressCapability(IOType.INPUT, List.of("other"), new BlockPos(2, 2, 3));
        var output = new StressCapability(IOType.OUTPUT, List.of("drive"), new BlockPos(3, 2, 3));
        var core = new MachineIoView(new CapabilitySnapshot(List.of(input, second, output)));
        var snapshot = IoAdapters.wrap(core);

        assertThat(snapshot.stressInputs()).extracting(StressState::position).containsExactly(input.position, second.position);
        assertThat(snapshot.stressOutputs()).extracting(StressState::position).containsExactly(output.position);
        assertThat(snapshot.forTags(Set.of("drive")).stressInputs()).hasSize(1);
        assertThat(snapshot.forTags(Set.of("missing")).stressOutputs()).isEmpty();
        assertThat(snapshot.energyInput()).isZero();
        assertThat(snapshot.energyOutputCapacity()).isZero();
        StressState before = snapshot.stressInputs().getFirst();
        input.rpm = -128;
        assertThat(before.actualRpm()).isEqualTo(64);
        assertThat(snapshot.stressInputs().getFirst().actualRpm()).isEqualTo(-128);
        assertThat(snapshot.stressInputs().getFirst().networkCapacity()).isEqualTo(core.stressInputs().getFirst().networkCapacity());
        assertThatThrownBy(() -> snapshot.stressInputs().clear()).isInstanceOf(UnsupportedOperationException.class);
        var context = new ContextFactory().enter();
        var scope = context.initStandardObjects();
        ScriptableObject.putProperty(scope, "io", core, context);
        assertThat(context.evaluateString(scope,
                "io.stressInputs().get(0).actualRpm() == -128 && io.stressOutputs().size() == 1 && io.energyInput() == 0",
                "stress-state-query", 1, null)).isEqualTo(true);
    }

    @Test
    void public_state_copies_mutable_positions() {
        var position = new BlockPos.MutableBlockPos(1, 2, 3);
        var state = new StressState(position, -64, -64, -64, 8, 512, 1024, 512, true, false);
        position.set(9, 9, 9);
        assertThat(state.position()).isEqualTo(new BlockPos(1, 2, 3));
    }

    /** Read-only facet fixture: mutation operations must never be invoked by a snapshot.
     * @author howxu <dev@howxu.cn>
     */
    private static final class StressCapability implements MachineCapability, StressFacet {
        private final IOType direction;
        private final List<String> tags;
        private final BlockPos position;
        private double rpm = 64;
        StressCapability(IOType direction, List<String> tags, BlockPos position) {
            this.direction = direction;
            this.tags = tags;
            this.position = position;
        }
        public CapabilityType type() { return new CapabilityType(ResourceLocation.parse("create:stress")); }
        public CapabilityDirections directions() { return CapabilityDirections.of(direction); }
        public CapabilityView view() {
            return new CapabilityView() {
                public CapabilityType type() { return StressCapability.this.type(); }
                public CapabilityDirections directions() { return StressCapability.this.directions(); }
                public Set<Class<? extends CapabilityFacet>> facets() { return Set.of(StressFacet.class); }
                public List<String> tags() { return tags; }
            };
        }
        public Object networkIdentity() { return StressCapability.class; }
        public cn.howxu.mmcr.api.compat.create.StressState state() {
            return new cn.howxu.mmcr.api.compat.create.StressState(position, rpm, rpm, -64, 8, 512, 1024, 512, true, false);
        }
        public boolean stressEnabled() { return true; }
        public boolean acceptsGeneratedRpm(StressSession session, int index, double rpm) { throw new AssertionError("read-only"); }
        public double ownedActualStress(StressSession session, int index) { throw new AssertionError("read-only"); }
        public CapabilityResult apply(StressSession session, int index, double stress, double rpm) { throw new AssertionError("read-only"); }
        public void release(StressSession session) { throw new AssertionError("read-only"); }
        public void release(StressSession session, int index) { throw new AssertionError("read-only"); }
        public CapabilityOperation prepare(CapabilityRequest request) { throw new AssertionError("read-only"); }
    }
}
