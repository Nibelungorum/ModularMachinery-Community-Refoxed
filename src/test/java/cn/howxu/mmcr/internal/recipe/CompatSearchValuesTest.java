package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.create.loaded.StressPortCapability;
import cn.howxu.mmcr.compat.create.loaded.StressInterfaceKind;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.infrastructure.config.CServer;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import java.lang.reflect.Field;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.compat.create.CreateFailureReasons;
import cn.howxu.mmcr.api.compat.create.StressFacet;
import cn.howxu.mmcr.api.compat.create.StressState;
import cn.howxu.mmcr.api.compat.pneumaticcraft.AirState;
import cn.howxu.mmcr.api.compat.pneumaticcraft.PneumaticAirFacet;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.InputScope;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import java.util.Collections;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.compat.create.CreateBridgeBootstrap;
import cn.howxu.mmcr.compat.create.CreateRecipeTypes;
import cn.howxu.mmcr.compat.create.StressRequirement;
import cn.howxu.mmcr.compat.create.StressSession;
import cn.howxu.mmcr.compat.pneumaticcraft.AirFailureReasons;
import cn.howxu.mmcr.compat.pneumaticcraft.AirRequirement;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticCraftBridgeBootstrap;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticIds;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticRecipeTypes;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Candidate;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Kind;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Overlap;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Rule;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Scope;
import cn.howxu.mmcr.test.ServerThreadTestScope;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/** Differential search checks use native handlers with value-only world substitutes, never commit IO.
 * @author howxu <dev@howxu.cn>
 */
class CompatSearchValuesTest {
    private ServerThreadTestScope server;
    private RequirementHandlerRegistry.TestScope requirements;
    private OutputRegistry.TestScope outputs;

    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @BeforeEach
    void setup() throws Exception {
        server = ServerThreadTestScope.open();
        requirements = RequirementHandlerRegistry.openTestScope();
        outputs = OutputRegistry.openTestScope();
        CreateRecipeTypes.register();
        PneumaticRecipeTypes.register();
        CreateBridgeBootstrap.installForTesting(() -> true);
        PneumaticCraftBridgeBootstrap.installForTesting(() -> true);
    }

    @AfterEach
    void cleanup() throws Exception {
        CreateBridgeBootstrap.resetForTesting();
        PneumaticCraftBridgeBootstrap.resetForTesting();
        outputs.close();
        requirements.close();
        server.close();
    }

    @Test
    void airSignedPressureCapacityAndRetainedInputsMatchNativePlanning() {
        for (int amount : List.of(-200, -100, 0, 100, 400, 600)) {
            var input = new AirPort(amount, IOType.INPUT);
            var output = new AirPort(amount, IOType.OUTPUT);
            for (long rate : List.of(0L, 10L, 200L)) {
                for (float minimum : List.of(0F, 2F, 5F)) {
                    assertEquivalent(List.of(AirRequirement.input(rate, minimum)), List.of(input), false);
                    assertEquivalent(List.of(AirRequirement.input(rate, minimum, 0F)), List.of(input), false);
                }
                assertEquivalent(List.of(AirRequirement.output(rate)), List.of(output), false);
                assertEquivalent(List.of(AirRequirement.output(rate)), List.of(output), true);
            }
        }
    }

    @Test
    void airRequirementsSharePhysicalBudgetsAndPressureUsesVirtualAir() {
        var input = new AirPort(400, IOType.INPUT);
        assertEquivalent(List.of(AirRequirement.input(100L, 2F), AirRequirement.input(100L, 3F)),
                List.of(input, input), false);
        assertEquivalent(List.of(AirRequirement.input(100L, 2F, 0F), AirRequirement.input(100L, 3F)),
                List.of(input), false);
        assertEquivalent(List.of(AirRequirement.input(100L, 3F)),
                List.of(new AirPort(400, IOType.INPUT), new AirPort(200, IOType.INPUT)), false);
        assertEquivalent(List.of(AirRequirement.output(100L), AirRequirement.output(100L)),
                List.of(new AirPort(400, IOType.OUTPUT)), false);
        assertEquivalent(List.of(AirRequirement.input(0L, 0F)), List.of(), false);
        assertEquivalent(List.of(AirRequirement.output(10L, 0F)), List.of(), false);
    }

    @Test
    void stressNetworkBudgetRotationAndFloatBoundsMatchNativePlanning() {
        Object network = new Object();
        for (double rpm : List.of(-16D, 0D, 8D, 16D, Double.NaN, (double) Float.MAX_VALUE)) {
            var input = new StressPort(network, IOType.INPUT, rpm, 160D, 0D);
            assertEquivalent(List.of(StressRequirement.input(2D, 16D)), List.of(input), false);
        }
        for (double rpm : List.of(-64D, 64D, 512D, Double.MIN_VALUE, (double) Float.MAX_VALUE)) {
            var output = new StressPort(network, IOType.OUTPUT, 0D, 0D, 0D);
            assertEquivalent(List.of(StressRequirement.output(2D, rpm)), List.of(output), false);
        }
        var disabled = new StressPort(network, IOType.INPUT, 16D, 0D, 32D);
        disabled.enabled = false;
        assertEquivalent(List.of(StressRequirement.input(10D, 1D)), List.of(disabled), false);
        var overloaded = new StressPort(network, IOType.INPUT, 16D, 160D, 0D);
        overloaded.overstressed = true;
        assertEquivalent(List.of(StressRequirement.input(2D, 1D)), List.of(overloaded), false);
    }

    @Test
    void stressSharedNetworksAndOutputRpmReservationsCannotBeDoubleSpent() {
        Object network = new Object();
        var first = new StressPort(network, IOType.INPUT, 10D, 100D, 0D);
        var second = new StressPort(network, IOType.INPUT, 10D, 100D, 0D);
        assertEquivalent(List.of(StressRequirement.input(15D, 1D)), List.of(first, second), false);
        assertEquivalent(List.of(StressRequirement.input(4D, 1D), StressRequirement.input(4D, 1D)), List.of(first, second), false);
        assertEquivalent(List.of(StressRequirement.input(15D, 1D)),
                List.of(first, new StressPort(new Object(), IOType.INPUT, 10D, 100D, 0D)), false);
        var output = new StressPort(network, IOType.OUTPUT, 0D, 0D, 0D);
        assertEquivalent(List.of(StressRequirement.output(2D, 64D), StressRequirement.output(2D, -64D)), List.of(output), false);
        assertEquivalent(List.of(StressRequirement.output(2D, 64D), StressRequirement.output(2D, 64D)), List.of(output), false);
        output.accepts = false;
        assertEquivalent(List.of(StressRequirement.output(2D, 64D)), List.of(output), false);
        assertEquivalent(List.of(StressRequirement.input(2D, 1D)), List.of(), false);
        assertEquivalent(List.of(StressRequirement.output(2D, 64D)), List.of(), false);
    }

    @Test
    void nativeStressOutputCapturesAdmissionAndRejectsConflictingRpmWithoutProof() throws Exception {
        CServer previous = AllConfigs.server();
        Field field = AllConfigs.class.getDeclaredField("server");
        field.setAccessible(true);
        try {
            if (previous == null) {
                CServer config = new CServer();
                var builder = new ModConfigSpec.Builder();
                config.registerAll(builder);
                config.specification = builder.build();
                var values = CommentedConfig.inMemory();
                config.specification.correct(values);
                var constructor = Class.forName("net.neoforged.fml.config.LoadedConfig").getDeclaredConstructors()[0];
                constructor.setAccessible(true);
                config.specification.acceptConfig((IConfigSpec.ILoadedConfig) constructor.newInstance(values, null, null));
                field.set(null, config);
            }
            var entity = new KineticBlockEntity(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
            var port = new StressPortCapability(entity, StressInterfaceKind.OUTPUT, () -> 0D, () -> 0D,
                    () -> { throw new AssertionError("search wrote a native contribution"); });
            var capabilities = new CapabilitySnapshot(List.of(port));
            CompatSearchCaptureTest.assertEquivalent(capabilities, CompatSearchCaptureTest.recipe(
                    List.of(StressRequirement.output(2D, 64D)), false));
            CompatSearchCaptureTest.assertEquivalent(capabilities, CompatSearchCaptureTest.recipe(
                    List.of(StressRequirement.output(2D, 64D), StressRequirement.output(2D, -64D)), false));
            assertThat(port.baseStress()).isZero();
        } finally {
            field.set(null, previous);
        }
    }

    @Test
    void nativeMissingPortsAndFailureReasonsSurviveCaptureAndConversion() {
        for (MachineRequirement requirement : List.of(AirRequirement.input(0L, 2F), AirRequirement.output(1L),
                StressRequirement.input(1D, 1D), StressRequirement.output(1D, 64D), StressRequirement.output(1D, 512D))) {
            var capabilities = new CapabilitySnapshot(List.of());
            var recipe = CompatSearchCaptureTest.recipe(List.of(requirement), false);
            CompatSearchCaptureTest.assertEquivalent(capabilities, recipe);
            var expected = new CraftingContext(capabilities).planStartResult(recipe, 16L);
            var request = AsyncRequirementPlanner.captureRecipeSearch(CompatSearchCaptureTest.runtime(), List.of(recipe), 16L,
                    capabilities, List.of(), 0L, new EffectiveRecipeSet.Cache());
            var result = request.result(CompletableFuture.supplyAsync(() -> request.newPlanner().advance()).join());
            assertThat(result.failure()).isNull();
            assertThat(result.result().failureReport().primary().reason()).isSameAs(expected.failure().reason());
        }
    }

    @Test
    void thirdPartyFacetsRemainBehindLiveCapabilityProof() {
        var air = CompatSearchCaptureTest.recipe(List.of(AirRequirement.input(1L, 0F)), false);
        var stress = CompatSearchCaptureTest.recipe(List.of(StressRequirement.input(1D, 1D)), false);
        assertThat(CompatSearchCaptureTest.capture(new CapabilitySnapshot(List.of(new AirPort(100, IOType.INPUT))),
                List.of(air)).candidates().getFirst().proofReason()).isEqualTo(RecipeSearchProof.Reason.LIVE_CAPABILITY);
        assertThat(CompatSearchCaptureTest.capture(new CapabilitySnapshot(List.of(
                new StressPort(new Object(), IOType.INPUT, 16D, 100D, 0D))), List.of(stress))
                .candidates().getFirst().proofReason()).isEqualTo(RecipeSearchProof.Reason.LIVE_CAPABILITY);
    }

    private static void assertEquivalent(List<? extends MachineRequirement> requirements, List<? extends Fixture> fixtures,
                                         boolean partial) {
        List<MachineRequirement> values = new ArrayList<>(requirements);
        var recipe = CompatSearchCaptureTest.recipe(values, partial);
        var capabilities = new CapabilitySnapshot(new ArrayList<>(fixtures));
        var expected = new CraftingContext(capabilities).planStartResult(recipe, 16L);
        Map<Object, Integer> identities = new IdentityHashMap<>();
        List<Rule> rules = new ArrayList<>();
        for (int index = 0; index < requirements.size(); index++) {
            var requirement = requirements.get(index);
            boolean output = requirement.io() == RecipeModifier.IOType.OUTPUT;
            IOType direction = output ? IOType.OUTPUT : IOType.INPUT;
            Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            if (requirement instanceof AirRequirement air) {
                List<CompatSearchValues.AirPort> ports = new ArrayList<>();
                for (Fixture fixture : fixtures) {
                    if (!(fixture instanceof AirPort port) || port.direction != direction || !seen.add(port)) continue;
                    ports.add(CompatSearchValues.airPort(id(identities, port), 0, port.state()));
                }
                rules.add(new Rule(index, PneumaticIds.AIR, Kind.AIR, direction, air.airPerTick(), 0D,
                        air.chance(), air.consumeChance(), output && partial, true, Set.of(), null, List.of(),
                        (output ? AirFailureReasons.OUTPUT_BLOCKED : AirFailureReasons.INSUFFICIENT_AIR).id(),
                        new CompatSearchValues.Air(air.minPressure(), ports)));
            } else if (requirement instanceof StressRequirement stress) {
                List<CompatSearchValues.StressPort> ports = new ArrayList<>();
                for (Fixture fixture : fixtures) {
                    if (!(fixture instanceof StressPort port) || port.direction != direction || !seen.add(port)) continue;
                    ports.add(CompatSearchValues.stressPort(id(identities, port), id(identities, port.network),
                            port.state(), port.enabled, port.accepts));
                }
                rules.add(new Rule(index, CreateRecipeTypes.STRESS, Kind.STRESS, direction, 0L, 0D,
                        1F, 1F, false, true, Set.of(), null, List.of(), CreateFailureReasons.INSUFFICIENT_STRESS.id(),
                        new CompatSearchValues.Stress(stress.stress(), stress.minRpm(), stress.rpm(), 256D, ports)));
            }
        }
        var candidate = new Candidate(recipe.id(), 0, recipe.inputRequirementCount(), 16L, null, rules,
                List.of(new Scope(InputScope.legacy(), Set.of())), new Overlap(Set.of(), false, false, false), null, -1);
        var snapshot = new RecipeSearchSnapshot(new RecipeSearchSnapshot.Stamp(0, 0, 0, 0, 0, 0, 0, 16, 1),
                List.of(), List.of(candidate));
        var actual = CompatSearchCaptureTest.advance(snapshot);
        String description = requirements + " ports=" + fixtures;
        if (expected.successful()) {
            assertThat(actual).as(description).isInstanceOfSatisfying(RecipeSearchPlanner.Match.class,
                    match -> assertThat(match.parallelism()).as(description).isEqualTo(expected.plan().parallelism()));
        } else assertThat(actual).as(description).isInstanceOfSatisfying(RecipeSearchPlanner.NoMatch.class,
                result -> assertThat(result.failures()).singleElement().satisfies(failure ->
                        assertThat(failure.reasonId()).as(description).isEqualTo(expected.failure().reason().id())));
    }

    private static int id(Map<Object, Integer> identities, Object identity) {
        return identities.computeIfAbsent(identity, ignored -> identities.size());
    }

    /** All live methods fail if a worker reaches the fixture.
     * @author howxu <dev@howxu.cn>
     */
    private abstract static class Fixture implements MachineCapability {
        final Thread owner = Thread.currentThread();
        final IOType direction;
        final CapabilityView view;
        private final CapabilityType type;

        Fixture(ResourceLocation id, IOType direction, Class<? extends CapabilityFacet> facet) {
            this.direction = direction;
            type = new CapabilityType(id);
            view = CapabilityFactories.view(type, directions(), Set.of(facet));
        }
        void checkThread() { if (Thread.currentThread() != owner) throw new AssertionError("worker accessed live facet"); }
        @Override public CapabilityType type() { checkThread(); return type; }
        @Override public CapabilityDirections directions() { return CapabilityDirections.of(direction); }
        @Override public CapabilityView view() { checkThread(); return view; }
        @Override public CapabilityOperation prepare(CapabilityRequest request) { throw new AssertionError("search prepared IO"); }
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class AirPort extends Fixture implements PneumaticAirFacet {
        final int air;
        AirPort(int air, IOType direction) { super(PneumaticIds.AIR, direction, PneumaticAirFacet.class); this.air = air; }
        @Override public Object queryIdentity() { checkThread(); return this; }
        @Override public AirState state() { checkThread(); return new AirState(BlockPos.ZERO, air, 100, 5F, 7F); }
        @Override public CapabilityResult validate(long amount, boolean insert, float minimum) { throw new AssertionError("search validated IO"); }
        @Override public CapabilityResult apply(long amount, boolean insert, float minimum) { throw new AssertionError("search applied IO"); }
        @Override public String toString() { return "air=" + air; }
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class StressPort extends Fixture implements StressFacet {
        final Object network;
        final double rpm;
        final double capacity;
        final double load;
        boolean enabled = true;
        boolean accepts = true;
        boolean overstressed;
        StressPort(Object network, IOType direction, double rpm, double capacity, double load) {
            super(CreateRecipeTypes.STRESS, direction, StressFacet.class);
            this.network = network; this.rpm = rpm; this.capacity = capacity; this.load = load;
        }
        @Override public Object networkIdentity() { checkThread(); return network; }
        @Override public StressState state() {
            checkThread();
            return new StressState(BlockPos.ZERO, rpm, rpm, 0D, 0D, 0D, capacity, load, true, overstressed);
        }
        @Override public boolean stressEnabled() { checkThread(); return enabled; }
        @Override public boolean acceptsGeneratedRpm(StressSession session, int index, double rpm) { checkThread(); return accepts; }
        @Override public double ownedActualStress(StressSession session, int index) { throw new AssertionError("search read owner"); }
        @Override public CapabilityResult apply(StressSession session, int index, double base, double generated) { throw new AssertionError("search applied stress"); }
        @Override public void release(StressSession session) { throw new AssertionError("search released stress"); }
        @Override public void release(StressSession session, int index) { throw new AssertionError("search released stress"); }
        @Override public String toString() { return "rpm=" + rpm + " capacity=" + capacity + " load=" + load; }
    }
}
