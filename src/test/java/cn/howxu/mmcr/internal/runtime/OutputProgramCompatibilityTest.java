package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.definition.RecipeBehavior;
import cn.howxu.mmcr.api.machine.definition.RecipeStartContext;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.test.ConfigTestSupport;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.ServerThreadTestScope;
import cn.howxu.mmcr.test.TestBootstrap;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.config.IConfigSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Output compatibility at the real capture, paid-tick and finish boundaries.
 * @author howxu <dev@howxu.cn>
 */
class OutputProgramCompatibilityTest {
    private static final ResourceLocation MACHINE = MMCR.id("test_cube");
    private ServerThreadTestScope serverThread;
    private OutputRegistry.TestScope outputs;

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        TestBootstrap.bootstrapCapabilities();
        CommentedConfig config = CommentedConfig.inMemory();
        ServerConfig.SPEC.correct(config);
        var constructor = Class.forName("net.neoforged.fml.config.LoadedConfig").getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        ServerConfig.SPEC.acceptConfig((IConfigSpec.ILoadedConfig) constructor.newInstance(config, null, null));
    }

    @BeforeEach
    void prepare() throws Exception {
        serverThread = ServerThreadTestScope.open();
        outputs = OutputRegistry.openTestScope();
        OutputRegistry.register(CodecOutput.TYPE);
        OutputRegistry.register(AdaptedOutput.TYPE);
    }

    @AfterEach
    void cleanup() throws Exception {
        try {
            ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
            outputs.close();
        } finally {
            serverThread.close();
        }
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void codec_only_output_captures_and_blocks_at_finish_without_free_completion(MachineWorkMode mode) {
        Fixture fixture = fixture(mode, RecipeBehavior.defaults(), new CodecOutput(37L));
        assertThatCode(() -> fixture.runtime.ensureInstalledProgram()).doesNotThrowAnyException();
        var installed = fixture.runtime.installedProgram();
        assertThat(installed).isNotNull();
        assertThat(installed.program().finish().requiresCompatibility()).isTrue();
        assertThat(fixture.output.itemHandler().amount(0)).isZero();
        long afterStart = fixture.energy.energyStorage().getAmountAsLong();
        advance(fixture, installed, mode, 1L);
        assertThat(fixture.energy.energyStorage().getAmountAsLong()).isEqualTo(afterStart - 7L);
        assertBlocked(fixture.runtime, BuiltinFailureReasons.FINISH);
        var paid = fixture.runtime.ledger().state();
        LevelStub.setGameTime(fixture.runtime.controller().getLevel(), 100L);
        assertThat(installed.tryAttempt(100L, 100L)).isTrue();
        assertThatCode(installed::advance).doesNotThrowAnyException();
        assertThat(fixture.runtime.ledger().state()).isEqualTo(paid);
        assertThat(fixture.runtime.activeOutputs()).containsExactly(new CodecOutput(37L));
        assertThat(fixture.output.itemHandler().amount(0)).isZero();
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void before_finish_can_replace_codec_only_output_after_the_final_tick_is_paid(MachineWorkMode mode) {
        AtomicInteger callbacks = new AtomicInteger();
        Fixture fixture = fixture(mode, RecipeBehavior.builder().beforeFinish(context -> {
            callbacks.incrementAndGet();
            assertThat(context.outputs()).containsExactly(new CodecOutput(37L));
            context.setOutputs(List.of(new AdaptedOutput(2L)));
        }).build(), new CodecOutput(37L));
        var captured = fixture.runtime.captureProgramCompilation();
        assertThat(captured).isNotNull();
        assertThat(callbacks).hasValue(0);
        assertThat(fixture.output.itemHandler().amount(0)).isZero();
        var installed = fixture.runtime.installCompiledProgram(captured.identity(), captured.compile());
        long afterStart = fixture.energy.energyStorage().getAmountAsLong();
        advance(fixture, installed, mode, 1L);
        assertThat(callbacks).hasValue(1);
        assertThat(fixture.energy.energyStorage().getAmountAsLong()).isEqualTo(afterStart - 7L);
        assertThat(installed.ledger().state().outputPaid()).isTrue();
        assertThat(fixture.runtime.active()).isFalse();
        assertThat(fixture.output.itemHandler().getStackInSlot(0).is(Items.GOLD_NUGGET)).isTrue();
        assertThat(fixture.output.itemHandler().amount(0)).isEqualTo(2L);
        assertThat(installed.advance()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, -2L, -3L})
    void invalid_restored_adapter_output_never_becomes_an_empty_or_input_finish(long amount) {
        Fixture fixture = fixture(MachineWorkMode.SYNC, RecipeBehavior.defaults(), new CodecOutput(37L));
        var active = new ActiveMachineRecipe(fixture.runtime.recipe(), 1L);
        active.setEffectiveExecutionSnapshot(new RecipeStartContext.ExecutionSnapshot(1,
                List.of(new EnergyRequirement(7L)), List.of(new AdaptedOutput(amount))));
        active.setInputConsumptionPlan(new ActiveMachineRecipe.InputConsumptionPlan(List.of(0)));
        var snapshot = fixture.runtime.controller().currentRuntimeSnapshot();
        fixture.runtime.restore(active, null, snapshot.structure().version(), snapshot.capabilityVersion(),
                snapshot.modifierVersion(), snapshot.stateVersion());
        var captured = fixture.runtime.captureProgramCompilation();
        assertThat(captured).isNotNull();
        assertThat(captured.finish().requiresCompatibility()).isTrue();
        var installed = fixture.runtime.installCompiledProgram(captured.identity(), captured.compile());
        long beforeTick = fixture.energy.energyStorage().getAmountAsLong();
        advance(fixture, installed, MachineWorkMode.SYNC, 1L);
        assertBlocked(fixture.runtime, BuiltinFailureReasons.INVALID_OUTPUTS);
        assertThat(fixture.energy.energyStorage().getAmountAsLong()).isEqualTo(beforeTick - 7L);
        assertThat(fixture.output.itemHandler().amount(0)).isZero();
    }

    @Test
    void dynamic_finish_retains_unknown_output_until_a_later_callback_explicitly_replaces_it() {
        AtomicInteger callbacks = new AtomicInteger();
        Fixture fixture = fixture(MachineWorkMode.SYNC, RecipeBehavior.builder().beforeFinish(context -> {
            if (callbacks.incrementAndGet() > 1) context.setOutputs(List.of(new AdaptedOutput(2L)));
        }).build(), new CodecOutput(37L));
        var installed = fixture.runtime.ensureInstalledProgram();
        advance(fixture, installed, MachineWorkMode.SYNC, 1L);
        assertBlocked(fixture.runtime, BuiltinFailureReasons.FINISH);
        assertThat(fixture.output.itemHandler().amount(0)).isZero();
        long afterFinalTick = fixture.energy.energyStorage().getAmountAsLong();
        long paidAttempts = installed.ledger().state().paidAttempts();
        LevelStub.setGameTime(fixture.runtime.controller().getLevel(), 100L);
        assertThat(installed.tryAttempt(100L, 100L)).isTrue();
        assertThat(installed.advance()).isTrue();
        assertThat(callbacks).hasValue(2);
        assertThat(fixture.energy.energyStorage().getAmountAsLong()).isEqualTo(afterFinalTick);
        assertThat(installed.ledger().state().paidAttempts()).isEqualTo(paidAttempts);
        assertThat(installed.ledger().state().outputPaid()).isTrue();
        assertThat(fixture.output.itemHandler().amount(0)).isEqualTo(2L);
    }

    @Test
    void async_finish_context_copy_reports_invalid_output_without_escaping() {
        Fixture fixture = fixture(MachineWorkMode.SYNC, RecipeBehavior.defaults(), new CodecOutput(37L));
        var installed = fixture.runtime.ensureInstalledProgram();
        outputs.close();
        LevelStub.setGameTime(fixture.runtime.controller().getLevel(), 1L);
        fixture.runtime.tick();
        assertThatCode(() -> assertThat(fixture.runtime.prepareAsyncFinish()).isFalse()).doesNotThrowAnyException();
        assertBlocked(fixture.runtime, BuiltinFailureReasons.INVALID_OUTPUTS);
        assertThat(installed.ledger().state().outputPaid()).isFalse();
        assertThat(fixture.output.itemHandler().amount(0)).isZero();
    }

    @Test
    void output_losing_canonical_registration_is_contained_during_capture_and_finish_context_copy() {
        Fixture fixture = fixture(MachineWorkMode.SYNC, RecipeBehavior.defaults(), new CodecOutput(37L));
        outputs.close();
        var installed = fixture.runtime.ensureInstalledProgram();
        assertThat(installed).isNotNull();
        advance(fixture, installed, MachineWorkMode.SYNC, 1L);
        assertBlocked(fixture.runtime, BuiltinFailureReasons.INVALID_OUTPUTS);
        assertThat(fixture.output.itemHandler().amount(0)).isZero();
    }

    @Test
    void normal_output_keeps_a_fixed_finish_binding_even_if_another_capture_occurs() {
        Fixture fixture = fixture(MachineWorkMode.SYNC, RecipeBehavior.defaults(),
                new MachineOutput.ItemOutput(new ItemStack(Items.IRON_NUGGET), 1F));
        var installed = fixture.runtime.ensureInstalledProgram();
        assertThat(installed.finishRequirements()).isNotNull().hasSize(1);
        assertThat(installed.program().finish().requiresCompatibility()).isFalse();
        fixture.runtime.captureProgramCompilation();
        advance(fixture, installed, MachineWorkMode.SYNC, 1L);
        assertThat(installed.ledger().state().outputPaid()).isTrue();
        assertThat(fixture.output.itemHandler().getStackInSlot(0).is(Items.IRON_NUGGET)).isTrue();
        assertThat(fixture.runtime.active()).isFalse();
    }

    private static void assertBlocked(CraftingRuntime runtime, FailureReason reason) {
        assertThat(runtime.active()).isTrue();
        assertThat(runtime.finishPending()).isTrue();
        assertThat(runtime.ledger().state().finalTickPaid()).isTrue();
        assertThat(runtime.ledger().state().outputPaid()).isFalse();
        assertThat(runtime.failure().reason()).isEqualTo(reason);
        assertThat(runtime.failure().failure().trace().frames().getFirst().phase()).isEqualTo(FailurePhase.FINISH);
    }

    private static void advance(Fixture fixture, InstalledRecipeProgram installed, MachineWorkMode mode, long time) {
        LevelStub.setGameTime(fixture.runtime.controller().getLevel(), time);
        if (mode != MachineWorkMode.SYNC) assertThat(fixture.runtime.prepareInstalledTick(time)).isTrue();
        assertThat(installed.tryAttempt(time, time)).isTrue();
        assertThatCode(installed::advance).doesNotThrowAnyException();
    }

    private static Fixture fixture(MachineWorkMode mode, RecipeBehavior behavior, MachineOutput output) {
        ConfigTestSupport.setMachineWorkMode(mode);
        var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        energy.energyStorage().setAmount(100L);
        var bus = RuntimeTestFixtures.itemOutput(new BlockPos(2, 0, 0));
        var controller = RuntimeTestFixtures.controller(MACHINE, energy, bus);
        Machine original = controller.structureSnapshot().configuredMachine();
        controller.setMachine(new Machine() {
            public ResourceLocation registryName() { return original.registryName(); }
            public BlockArray pattern() { return original.pattern(); }
            public MachineControllerSpec controller() { return original.controller(); }
            public RecipeBehavior behavior() { return behavior; }
        });
        RuntimeTestFixtures.republish(controller);
        var runtime = new CraftingRuntime(controller, controller.componentRuntime());
        var recipe = MachineRecipe.fromCanonical(MMCR.id("output_program_compatibility"), MACHINE, 1,
                List.of(new EnergyRequirement(7L)), List.of(output), List.of(), 0, 1,
                false, false, false, Set.of());
        assertThat(runtime.start(recipe, 1L).isCrafting()).isTrue();
        return new Fixture(runtime, energy, bus);
    }

    /** @author howxu <dev@howxu.cn> */
    private record Fixture(CraftingRuntime runtime, EnergyInputHatchBlockEntity energy, ItemOutputBusBlockEntity output) { }

    /** A registered, serializable extension without an execution adapter. @author howxu <dev@howxu.cn> */
    private record CodecOutput(long amount) implements MachineOutput {
        private static final OutputType<CodecOutput> TYPE = new OutputType.Definition<>(
                MMCR.id("test_codec_only_output"), Codec.LONG.xmap(CodecOutput::new, CodecOutput::amount).fieldOf("amount"),
                (output, chance) -> output, (output, modifiers) -> output, output -> output);
        public OutputType<CodecOutput> outputType() { return TYPE; }
        public float chance() { return 1F; }
    }

    /** Null, wrong-direction, non-finite and empty output exercise malformed restored adapters.
     * @author howxu <dev@howxu.cn>
     */
    private record AdaptedOutput(long amount) implements MachineOutput {
        private static final OutputType<AdaptedOutput> TYPE = new OutputType.Definition<>(
                MMCR.id("test_adapted_output"), Codec.LONG.xmap(AdaptedOutput::new, AdaptedOutput::amount).fieldOf("amount"),
                (output, chance) -> output, (output, modifiers) -> output, output -> output,
                OutputType.Presentation.defaults(MMCR.id("test_adapted_output")), "test_adapted_output",
                (output, tags) -> output.amount() == 0L ? null : output.amount() == -3L
                        ? new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, ItemStack.EMPTY)
                        : output.amount() < 0L ? new EnergyRequirement(11L)
                        : new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                        new ItemStack(Items.GOLD_NUGGET, (int) output.amount()), 1F, tags),
                requirement -> false);
        public OutputType<AdaptedOutput> outputType() { return TYPE; }
        public float chance() { return amount == -2L ? Float.NaN : 1F; }
    }
}
