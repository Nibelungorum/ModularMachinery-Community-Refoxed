package cn.howxu.mmcr.compat.emi;

import cn.howxu.mmcr.internal.client.RecipeViewerSnapshot.Version;
import cn.howxu.mmcr.internal.client.RecipeViewerSnapshot;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Deterministic native reload and stale page regression coverage.
 * @author howxu <dev@howxu.cn>
 */
class EmiRuntimeReloaderTest {
    private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    private final List<Throwable> failures = new ArrayList<>();
    private int reloads;
    private final EmiRuntimeReloader.State state = new EmiRuntimeReloader.State(
            tasks::addLast, () -> reloads++, () -> {}, failures::add, version(0, 0));

    @Test void initialCaptureConsumesLatestRequestWithoutStartingAnotherWorker() {
        state.request(version(1, 0));
        state.request(version(2, 0));
        drain();
        assertThat(reloads).isZero();
        state.started(version(2, 0));
        state.registered(version(2, 1));
        assertThat(state.completed()).isNull();
        state.tick(false, 2, true); // Status alone is not a completed bake.
        assertThat(state.completed()).isNull();
        state.tick(true, 1, true); // A concurrent restart can change status after the isLoaded read.
        assertThat(state.completed()).isNull();
        state.tick(true, 2, false);
        assertThat(state.completed()).isEqualTo(version(2, 1));
        drain();
        assertThat(reloads).isZero();
    }

    @Test void changesInOneClientTurnCoalesceAndOldBakeCannotCompleteNewRequest() {
        completeInitial();
        state.request(version(1, 0));
        state.request(version(2, 0));
        assertThat(tasks).hasSize(1);
        drain();
        assertThat(reloads).isEqualTo(1);
        state.started(version(2, 1));
        state.registered(version(2, 2));
        state.request(version(3, 0));
        state.tick(true, 2, false);
        assertThat(state.completed()).isEqualTo(version(0, 1));
        drain();
        assertThat(reloads).isEqualTo(2);
        state.started(version(3, 2));
        state.registered(version(3, 3));
        state.tick(true, 2, false);
        assertThat(state.completed()).isEqualTo(version(3, 3));
        drain();
        assertThat(reloads).isEqualTo(2);
    }

    @Test void externalReloadWithOnlyANewRegistrationCompletesWithoutReloadLoop() {
        completeInitial();
        state.tick(false, 1, true);
        state.started(version(0, 1));
        state.registered(version(0, 2));
        state.tick(true, 2, false);
        for (int i = 0; i < 5; i++) state.tick(true, 2, false);
        drain();
        assertThat(state.completed()).isEqualTo(version(0, 2));
        assertThat(reloads).isZero();
    }

    @Test void pluginFailureDoesNotCompleteEvenWhenOtherPluginsBakeSuccessfully() {
        completeInitial();
        state.request(version(1, 0));
        drain();
        state.started(version(1, 1));
        IllegalStateException failure = new IllegalStateException("plugin failed");
        state.failed(version(1, 1), failure);
        for (int i = 0; i < 5; i++) state.tick(true, 2, false);
        drain();
        assertThat(state.completed()).isEqualTo(version(0, 1));
        assertThat(state.failure()).isSameAs(failure);
        assertThat(failures).containsExactly(failure);
        assertThat(reloads).isEqualTo(1);
    }

    @Test void firstRegistrationFailureWaitsForNewContentThenCanComplete() {
        assertFirstFailureRecovers(version(1, 0), version(2, 0));
    }

    @Test void firstRegistrationFailureWaitsForNewInformationThenCanComplete() {
        assertFirstFailureRecovers(version(1, 0), new Version(0, 1, 1, 0));
    }

    @Test void resetSessionDoesNotInitializeEarlyButItsFirstFailureCanRecover() {
        completeInitial();
        Version initial = new Version(1, 0, 0, 0);
        state.reset(initial);
        state.started(version(99, 0));
        state.failed(version(99, 0), new IllegalStateException("old session"));
        state.request(new Version(1, 1, 0, 0));
        drain();
        assertThat(reloads).isZero();
        assertThat(state.failure()).isNull();
        assertFirstFailureRecovers(new Version(1, 1, 0, 0), new Version(1, 1, 1, 0));
    }

    private void assertFirstFailureRecovers(Version first, Version next) {
        state.request(first);
        drain();
        assertThat(reloads).isZero(); // Native EMI still owns initialization.
        state.started(first);
        IllegalStateException failure = new IllegalStateException("first registration failed");
        state.failed(first, failure);
        for (int i = 0; i < 5; i++) state.tick(true, 2, false);
        state.request(first);
        drain();
        assertThat(reloads).isZero();
        assertThat(state.completed()).isNull();
        assertThat(state.failure()).isSameAs(failure);
        state.request(next);
        state.request(next);
        assertThat(tasks).hasSize(1);
        drain();
        assertThat(reloads).isEqualTo(1);
        state.started(next);
        Version registered = new Version(next.session(), next.content(), next.information(), 1);
        state.registered(registered);
        state.tick(true, 2, false);
        assertThat(state.completed()).isEqualTo(registered);
        assertThat(state.failure()).isNull();
        assertThat(state.current()).isTrue();
        drain();
        assertThat(reloads).isEqualTo(1);
    }

    @Test void nativeFailureIsDiagnosticAndDoesNotRetryEveryTick() {
        completeInitial();
        state.request(version(1, 0));
        drain();
        for (int i = 0; i < 5; i++) state.tick(false, -1, false);
        drain();
        assertThat(state.failure()).hasMessageContaining("EMI reload failed");
        assertThat(failures).hasSize(1);
        assertThat(reloads).isEqualTo(1);
        state.request(version(2, 0));
        drain();
        assertThat(reloads).isEqualTo(2);
    }

    @Test void informationAloneRequestsReloadButRegistrationCounterDoesNot() {
        completeInitial();
        state.request(new Version(0, 0, 1, 1));
        drain();
        assertThat(reloads).isEqualTo(1);
        state.started(new Version(0, 0, 1, 1));
        state.registered(new Version(0, 0, 1, 2));
        state.tick(true, 2, false);
        state.request(new Version(0, 0, 1, 99));
        drain();
        assertThat(reloads).isEqualTo(1);
    }

    @Test void resetRejectsOldQueuedCallbackAndOldWorkerAndAcceptsLowerContentVersion() {
        completeInitial();
        state.request(version(90, 0));
        Runnable obsolete = tasks.removeFirst();
        Version next = new Version(1, 1, 0, 0);
        state.reset(next);
        state.request(next);
        obsolete.run();
        state.registered(version(90, 2));
        state.failed(version(90, 2), new IllegalStateException("old worker"));
        state.tick(true, 2, false);
        assertThat(state.completed()).isNull();
        assertThat(state.failure()).isNull();
        state.started(next);
        state.registered(new Version(1, 1, 0, 1));
        state.tick(true, 2, false);
        drain();
        assertThat(state.completed()).isEqualTo(new Version(1, 1, 0, 1));
        assertThat(reloads).isZero();
    }

    @Test void missingPluginRegistrationOnSuccessfulNativeBakeIsNotSuccess() {
        completeInitial();
        state.request(version(1, 0));
        drain();
        state.tick(true, 2, false);
        assertThat(state.completed()).isEqualTo(version(0, 1));
        assertThat(state.failure()).hasMessageContaining("MMCR registration");
        drain();
        assertThat(reloads).isEqualTo(1);
    }

    @Test void pageRefreshReplacesSameIdsUsesCurrentCategoriesAndRemovesDeletedEntries() {
        EmiRecipeCategory previous = category("old_pool"), replacement = category("new_pool");
        ResourceLocation id = ResourceLocation.parse("mmcr:/recipe/example/same_id");
        EmiRecipe old = recipe(previous, id), current = recipe(replacement, id);
        EmiRecipe deleted = recipe(previous, ResourceLocation.parse("mmcr:/structure/example/deleted"));
        EmiRecipe foreign = recipe(previous, ResourceLocation.parse("example:foreign"));
        var page = Map.of(previous, List.of(old, deleted, foreign));
        var refreshed = EmiRuntimeReloader.refreshRecipes(page, lookup -> lookup.equals(id) ? current : null);
        assertThat(refreshed.get(replacement)).containsExactly(current);
        assertThat(refreshed.get(previous)).containsExactly(foreign);
        assertThat(EmiRuntimeReloader.refreshRecipes(Map.of(previous, List.of(old, deleted)), lookup -> null)).isEmpty();
        assertThat(EmiRuntimeReloader.refreshRecipes(refreshed, lookup -> current)).isSameAs(refreshed);
    }

    @Test void nativeCriticalFailureThenSuccessfulRetryCanCompleteWhileRetryStatusRemainsError() {
        completeInitial();
        state.request(version(1, 0));
        drain();
        state.tick(false, -1, true);
        assertThat(state.failure()).hasMessageContaining("native retry active");
        state.started(version(1, 1));
        state.registered(version(1, 2));
        for (int i = 0; i < 5; i++) state.tick(false, -1, true);
        assertThat(state.completed()).isEqualTo(version(0, 1));
        assertThat(state.current()).isFalse();
        state.tick(true, 2, false);
        assertThat(state.completed()).isEqualTo(version(1, 2));
        assertThat(state.failure()).isNull();
        assertThat(state.current()).isTrue();
        drain();
        assertThat(reloads).isEqualTo(1);
        assertThat(failures).hasSize(1);
    }

    @Test void exhaustedNativeRetriesAreTerminalWithoutTickDrivenRestart() {
        completeInitial();
        state.request(version(1, 0));
        drain();
        state.tick(false, -1, true);
        for (int i = 0; i < 5; i++) state.tick(false, -1, false);
        assertThat(state.failure()).hasMessageContaining("worker terminated");
        assertThat(state.current()).isFalse();
        assertThat(failures).hasSize(2); // One active-error transition and one terminal transition.
        drain();
        assertThat(reloads).isEqualTo(1);
    }

    @Test void retryPluginFailureCannotBeHiddenByOverallNativeSuccess() {
        completeInitial();
        state.tick(false, -1, true);
        state.started(version(0, 1));
        IllegalStateException failure = new IllegalStateException("retry plugin failed");
        state.failed(version(0, 1), failure);
        state.tick(false, -1, true);
        state.tick(true, 2, false);
        assertThat(state.completed()).isEqualTo(version(0, 1));
        assertThat(state.failure()).isSameAs(failure);
        assertThat(state.current()).isFalse();
        drain();
        assertThat(reloads).isZero();
    }

    @Test void pluginCyclePassesImmutableStartSessionAndRejectsANewSessionSnapshot() throws Exception {
        Version start = version(0, 0);
        state.started(start);
        Version next = new Version(1, 0, 0, 1);
        state.reset(next);
        RecipeViewerSnapshot wrongSession = emptySnapshot(next);
        List<RecipeViewerSnapshot> writes = new ArrayList<>();
        List<Version> accepted = new ArrayList<>(), rejected = new ArrayList<>();
        assertThatThrownBy(() -> MachineEmiPlugin.registerCycle(start, expectedSession -> {
            assertThat(expectedSession).isEqualTo(start.session());
            return CompletableFuture.completedFuture(wrongSession);
        }, writes::add, accepted::add, (token, failure) -> {
            rejected.add(token);
            state.failed(token, failure);
        })).isInstanceOf(CancellationException.class);
        assertThat(writes).isEmpty();
        assertThat(accepted).isEmpty();
        assertThat(rejected).containsExactly(start);
        state.tick(true, 2, false);
        assertThat(state.completed()).isNull();
        assertThat(state.failure()).isNull();
    }

    @Test void cancelledBoundCaptureDoesNotRegisterOrCompleteTheNewSession() {
        Version start = version(0, 0);
        state.started(start);
        state.reset(new Version(1, 0, 0, 0));
        CompletableFuture<RecipeViewerSnapshot> cancelled = new CompletableFuture<>();
        cancelled.cancel(false);
        List<Version> accepted = new ArrayList<>();
        assertThatThrownBy(() -> MachineEmiPlugin.registerCycle(start, expectedSession -> cancelled,
                snapshot -> { throw new AssertionError("Cancelled capture must not write native data"); },
                accepted::add, state::failed)).isInstanceOf(CancellationException.class);
        assertThat(accepted).isEmpty();
        assertThat(state.failure()).isNull();
    }

    @Test void registrationBodyFailureDoesNotPublishARegisteredToken() throws Exception {
        completeInitial();
        Version start = version(0, 1);
        state.started(start);
        RecipeViewerSnapshot captured = emptySnapshot(version(0, 2));
        List<Version> accepted = new ArrayList<>();
        IllegalStateException failure = new IllegalStateException("final registry operation failed");
        assertThatThrownBy(() -> MachineEmiPlugin.registerCycle(start,
                expectedSession -> CompletableFuture.completedFuture(captured),
                snapshot -> { throw failure; }, token -> { accepted.add(token); state.registered(token); },
                state::failed)).isSameAs(failure);
        assertThat(accepted).isEmpty();
        state.tick(true, 2, false);
        assertThat(state.completed()).isEqualTo(version(0, 1));
        assertThat(state.failure()).isSameAs(failure);
        assertThat(state.current()).isFalse();
    }

    @Test void externalReloadSuspendsRouterOnceAndRebuildsOnlyAfterSuccessfulBake() {
        completeInitial();
        EmiRuntimeReloader.PageLifecycle pages = new EmiRuntimeReloader.PageLifecycle();
        RecordingHost host = new RecordingHost();
        pages.update(host, state.current(), state.completed());
        InputProbe old = host.probe;
        assertThat(host.router.mousePressed(1, 1, 0)).isTrue();

        state.tick(false, 1, true);
        pages.update(host, state.current(), state.completed());
        for (int i = 0; i < 5; i++) pages.suspend(host);
        assertThat(host.invalidations).isEqualTo(1);
        assertThat(old.closed).isTrue();
        assertThat(host.router.mousePressed(1, 1, 0)).isFalse();
        assertThat(old.presses).isEqualTo(1);
        assertThat(host.rebuilds).isEqualTo(1);

        state.started(version(0, 1));
        state.registered(version(0, 2));
        state.tick(false, 2, true);
        pages.update(host, state.current(), state.completed());
        assertThat(host.rebuilds).isEqualTo(1);
        state.tick(true, 2, false);
        for (int i = 0; i < 5; i++) pages.update(host, state.current(), state.completed());
        assertThat(host.rebuilds).isEqualTo(2);
        assertThat(host.invalidations).isEqualTo(1);
        assertThat(host.router.mousePressed(1, 1, 0)).isTrue();
        assertThat(old.presses).isEqualTo(1);
        assertThat(reloads).isZero();
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class RecordingHost implements EmiRuntimeReloader.ScreenHost {
        private final EmiInputRouter router = new EmiInputRouter();
        private InputProbe probe;
        private int invalidations, rebuilds;
        @Override public void mmcrInvalidateRecipes() { invalidations++; router.close(); }
        @Override public void mmcrRefreshRecipes() {
            rebuilds++;
            probe = new InputProbe();
            router.replace(List.of(new EmiInputRouter.Binding(probe, 0, 0, () -> new Bounds(0, 0, 10, 10))));
        }
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class InputProbe implements EmiInteractiveWidget {
        private boolean closed;
        private int presses;
        @Override public boolean mousePressed(double x, double y, int button) { presses++; return !closed; }
        @Override public boolean capturesDrag(double x, double y, int button) { return false; }
        @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return false; }
        @Override public boolean mouseReleased(double x, double y, int button) { return false; }
        @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) { return false; }
        @Override public boolean visible() { return !closed; }
        @Override public void tick() { }
        @Override public void close() { closed = true; }
    }

    private static RecipeViewerSnapshot emptySnapshot(Version version) throws ReflectiveOperationException {
        var constructor = RecipeViewerSnapshot.class.getDeclaredConstructor(Version.class, Map.class,
                List.class, Map.class, List.class, List.class, List.class);
        constructor.setAccessible(true);
        return constructor.newInstance(version, Map.of(), List.of(), Map.of(), List.of(), List.of(), List.of());
    }

    private void completeInitial() {
        state.started(version(0, 0));
        state.registered(version(0, 1));
        state.tick(true, 2, false);
    }
    private void drain() { while (!tasks.isEmpty()) tasks.removeFirst().run(); }
    private static Version version(long content, long registration) { return new Version(0, content, 0, registration); }
    private static EmiRecipeCategory category(String name) {
        return new EmiRecipeCategory(ResourceLocation.parse("example:" + name), EmiStack.EMPTY);
    }
    private static EmiRecipe recipe(EmiRecipeCategory category, ResourceLocation id) {
        return (EmiRecipe) Proxy.newProxyInstance(EmiRuntimeReloaderTest.class.getClassLoader(), new Class<?>[]{EmiRecipe.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "getCategory" -> category;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> id.toString();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
