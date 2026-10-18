package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Diagnostic scopes must not change or retry a reservation transaction.
 * @author howxu <dev@howxu.cn>
 */
class PatternStartDiagnosticsTest {
    private final RecordingAppender appender = new RecordingAppender();
    private final Logger logger = (Logger) LogManager.getLogger("mmcr");
    private Level previousLevel;

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        TestBootstrap.bootstrapCapabilities();
    }

    @BeforeEach
    void captureLogs() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void releaseLogs() {
        logger.removeAppender(appender);
        logger.setLevel(previousLevel);
        appender.stop();
    }

    @Test
    void sampledRejectionKeepsOriginalResultAndRunsOnlyOnce() {
        var calls = new AtomicInteger();
        var original = PatternStartReservation.unavailable();
        var result = PatternStartDiagnostics.trace(true, BlockPos.ZERO, new BlockPos(1, 2, 3), null, 12L, () -> {
            calls.incrementAndGet();
            PatternStartDiagnostics.reject("start-inputs", "failure", "missing-input");
            return original;
        });
        assertThat(result).isSameAs(original);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(appender.messages).singleElement().asString()
                .contains("reason=start-inputs", "parallelism=12", "missing-input", "x=1, y=2, z=3");
        PatternStartDiagnostics.reject("outside-scope");
        assertThat(appender.messages).hasSize(1);
    }

    @Test
    void nestedAndUnsampledCallsRestoreTheOuterContext() {
        PatternStartDiagnostics.trace(true, BlockPos.ZERO, BlockPos.ZERO, null, 12L, () -> {
            PatternStartDiagnostics.trace(false, BlockPos.ZERO, BlockPos.ZERO, null, 99L, () -> {
                PatternStartDiagnostics.reject("suppressed");
                return PatternStartReservation.unavailable();
            });
            PatternStartDiagnostics.trace(true, BlockPos.ZERO, BlockPos.ZERO, null, 2L, () -> {
                PatternStartDiagnostics.reject("inner");
                return PatternStartReservation.unavailable();
            });
            PatternStartDiagnostics.reject("outer");
            return PatternStartReservation.unavailable();
        });
        assertThat(appender.messages).hasSize(2);
        assertThat(appender.messages.get(0)).contains("reason=inner", "parallelism=2");
        assertThat(appender.messages.get(1)).contains("reason=outer", "parallelism=12");
    }

    @Test
    void transactionExceptionIsPreservedAndScopeIsCleared() {
        var failure = new IllegalStateException("original transaction failure");
        var calls = new AtomicInteger();
        assertThatThrownBy(() -> PatternStartDiagnostics.trace(true, null, null, null, 1L, () -> {
            calls.incrementAndGet();
            throw failure;
        })).isSameAs(failure);
        PatternStartDiagnostics.reject("leaked-context");
        assertThat(calls.get()).isEqualTo(1);
        assertThat(appender.messages).isEmpty();
    }

    @Test
    void brokenDiagnosticDetailCannotPreventReservation() {
        var original = PatternStartReservation.unavailable();
        Object broken = new Object() {
            @Override public String toString() { throw new IllegalStateException("broken diagnostic getter"); }
        };
        var result = PatternStartDiagnostics.trace(true, null, null, null, 1L, () -> {
            PatternStartDiagnostics.reject("diagnostic-detail", broken);
            return original;
        });
        assertThat(result).isSameAs(original);
    }

    @Test
    void loggingBackendFailureCannotSkipCleanupOrReplaceTheResult() {
        var attempts = new AtomicInteger();
        var cleanup = new AtomicInteger();
        var original = PatternStartReservation.unavailable();
        var failing = new AbstractAppender("pattern-diagnostic-failing-backend", null, null, false, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                attempts.incrementAndGet();
                throw new IllegalStateException("logging backend failed");
            }
        };
        failing.start();
        logger.addAppender(failing);
        try {
            var result = PatternStartDiagnostics.trace(true, null, null, null, 1L, () -> {
                PatternStartDiagnostics.reject("before-cleanup");
                cleanup.incrementAndGet();
                return original;
            });
            assertThat(result).isSameAs(original);
            assertThat(attempts.get()).isEqualTo(1);
            assertThat(cleanup.get()).isEqualTo(1);
        } finally {
            logger.removeAppender(failing);
            failing.stop();
        }
        int messages = appender.messages.size();
        PatternStartDiagnostics.reject("leaked-context");
        assertThat(appender.messages).hasSize(messages);
    }

    @Test
    void controllerAdmissionLogsTheActualUnformedState() {
        var controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        controller.setFormed(false);
        RuntimeTestFixtures.republish(controller);
        var recipe = MMCR.id("diagnostic_unformed");
        var result = PatternStartDiagnostics.trace(true, BlockPos.ZERO, controller.getBlockPos(), recipe, 1L,
                () -> controller.reserveExactPatternStart(BlockPos.ZERO, recipe, List.of(), 1L, List.of()));
        assertThat(result.status()).isEqualTo(PatternStartReservation.Status.UNAVAILABLE);
        assertThat(appender.messages).anySatisfy(message -> assertThat(message)
                .contains("reason=controller-admission", "formed, false"));
    }

    @Test
    void failedInputPreparationLogsReasonWithoutTakingOfferedMaterials() {
        var source = new BlockPos(3, 0, 0);
        var host = RuntimeTestFixtures.itemInput(source);
        var controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), host);
        controller.componentRuntime().replaceLinkedPortPositions(Set.of(source));
        var storage = new LongItemStorage(1, 64L, () -> { });
        storage.setContents(0, new ItemStack(Items.IRON_INGOT, 1), 1L);
        var input = new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 2, ItemStack.EMPTY);
        var recipe = RecipeTestSupport.create(MMCR.id("diagnostic_missing_input"), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 4, false, List.of(), List.of(input));
        var runtime = new CraftingRuntime(controller, controller.componentRuntime());
        PatternStartDiagnostics.trace(true, source, controller.getBlockPos(), recipe.id(), 1L, () -> {
            assertThat(runtime.prepareExactPatternStart(recipe, 1L,
                    List.of(new ItemBusCapability(storage, IOType.INPUT)), source)).isNull();
            return PatternStartReservation.unavailable();
        });
        assertThat(storage.amount(0)).isEqualTo(1L);
        assertThat(appender.messages).anySatisfy(message -> assertThat(message)
                .contains("reason=start-inputs", "failure", "actualParallelism"));
    }

    /** Captures real logger output, not source text or a mocked transaction.
     * @author howxu <dev@howxu.cn>
     */
    private static final class RecordingAppender extends AbstractAppender {
        private final List<String> messages = new ArrayList<>();

        private RecordingAppender() {
            super("pattern-start-diagnostics-test", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            messages.add(event.getMessage().getFormattedMessage());
        }
    }
}
