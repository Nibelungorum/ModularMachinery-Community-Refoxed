package cn.howxu.mmcr.compat.emi;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.client.RecipeViewerRegistration;
import cn.howxu.mmcr.internal.client.RecipeViewerSnapshot.Version;
import cn.howxu.mmcr.internal.sync.RuntimeContentSnapshot;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.runtime.EmiReloadManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Field;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/** Coalesces main-thread requests; EMI owns the only registration/bake worker.
 * @author howxu <dev@howxu.cn>
 */
public final class EmiRuntimeReloader {
    private EmiRuntimeReloader() {}

    private static final class Holder {
        private static final PageLifecycle PAGES = new PageLifecycle();
        private static final State STATE = new State(task -> Minecraft.getInstance().tell(task),
                EmiReloadManager::reload, EmiRuntimeReloader::invalidate,
                failure -> MMCR.LOG.error("Failed to reload native EMI content", failure), RecipeViewerRegistration.currentVersion());
    }

    public static void reloadIfAvailable(RuntimeContentSnapshot snapshot) {
        Version current = RecipeViewerRegistration.currentVersion();
        Holder.STATE.request(new Version(current.session(), snapshot.contentVersion(), current.information(), current.registration()));
    }

    /** The caller has already incremented the coordinator's information version. */
    public static void informationChanged() { Holder.STATE.request(RecipeViewerRegistration.currentVersion()); }
    public static void resetForConnection() {
        Holder.STATE.reset(RecipeViewerRegistration.currentVersion());
        invalidate();
    }
    public static void registrationStarted(Version version) { Holder.STATE.started(version); }
    public static void registered(Version version) { Holder.STATE.registered(version); }
    public static void registrationFailed(Version version, Throwable failure) { Holder.STATE.failed(version, failure); }

    public static void tick() {
        synchronized (EmiReloadManager.class) {
            Holder.STATE.tick(EmiReloadManager.isLoaded(), EmiReloadManager.getStatus(), NativeThread.active());
        }
        Holder.PAGES.update(screenHost(), isLoaded(), Holder.STATE.completed());
    }

    /** Also called before render/input, which can observe an external reload before the next tick. */
    public static boolean suspendIfUnavailable() {
        if (isLoaded()) return false;
        Holder.PAGES.suspend(screenHost());
        return true;
    }

    public static boolean isLoaded() { return EmiReloadManager.isLoaded() && Holder.STATE.current(); }
    public static Map<EmiRecipeCategory, List<EmiRecipe>> refreshRecipes(Map<EmiRecipeCategory, List<EmiRecipe>> recipes) {
        return isLoaded() ? refreshRecipes(recipes, EmiApi.getRecipeManager()::getRecipe) : recipes;
    }

    /** Resolves only MMCR synthetic IDs. Foreign history entries retain their original objects. */
    static Map<EmiRecipeCategory, List<EmiRecipe>> refreshRecipes(Map<EmiRecipeCategory, List<EmiRecipe>> recipes,
                                                               Function<ResourceLocation, EmiRecipe> lookup) {
        Map<EmiRecipeCategory, List<EmiRecipe>> result = new LinkedHashMap<>();
        boolean changed = false;
        for (var entry : recipes.entrySet()) {
            for (EmiRecipe previous : entry.getValue()) {
                ResourceLocation id = previous.getId();
                boolean owned = id != null && id.getNamespace().equals(MMCR.MODID)
                        && (id.getPath().startsWith("/recipe/") || id.getPath().startsWith("/structure/"));
                EmiRecipe current = owned ? lookup.apply(id) : previous;
                changed |= current != previous;
                if (current != null) {
                    EmiRecipeCategory category = owned ? current.getCategory() : entry.getKey();
                    changed |= category != entry.getKey();
                    result.computeIfAbsent(category, ignored -> new ArrayList<>()).add(current);
                }
            }
        }
        return changed ? result : recipes;
    }

    private static void invalidate() {
        Holder.PAGES.suspend(screenHost());
        EmiPreviewSessions.instance().invalidate();
    }

    private static ScreenHost screenHost() {
        return Minecraft.getInstance().screen instanceof ScreenHost host ? host : null;
    }

    /** EMI keeps status=-1 during automatic retries; only the actual worker lifetime distinguishes termination.
     * @author howxu <dev@howxu.cn>
     */
    private static final class NativeThread {
        private static final Field THREAD = threadField();
        private static Field threadField() {
            try {
                Field field = EmiReloadManager.class.getDeclaredField("thread");
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot observe EMI reload worker", exception);
            }
        }
        private static boolean active() {
            try {
                Thread thread = (Thread) THREAD.get(null);
                return thread != null && thread.isAlive();
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Cannot observe EMI reload worker", exception);
            }
        }
    }

    /** Implemented by RecipeScreen, including screens retained in EmiHistory.
     * @author howxu <dev@howxu.cn>
     */
    public interface ScreenHost {
        void mmcrRefreshRecipes();
        void mmcrInvalidateRecipes();
    }

    /** Main-thread page suspension/rebuild, independent of worker token callbacks.
     * @author howxu <dev@howxu.cn>
     */
    static final class PageLifecycle {
        private ScreenHost host;
        private boolean suspended;
        private Version displayed;

        void suspend(ScreenHost current) { update(current, false, null); }

        void update(ScreenHost current, boolean available, Version completed) {
            if (host != current) { host = current; suspended = false; displayed = null; }
            if (host == null) return;
            if (!available) {
                if (!suspended) { host.mmcrInvalidateRecipes(); suspended = true; }
            } else if (suspended || !Objects.equals(displayed, completed)) {
                suspended = false;
                displayed = completed;
                host.mmcrRefreshRecipes();
            }
        }
    }

    /** Synchronized token state: worker callbacks never read a world, run scripts or touch widgets.
     * @author howxu <dev@howxu.cn>
     */
    static final class State {
        private final Executor executor;
        private final Runnable reload;
        private final Runnable invalidate;
        private final Consumer<Throwable> diagnostic;
        private Version requested, lastRegistered, lastCompleted, dispatched, failedVersion;
        private Throwable failure;
        private Throwable nativeFailure;
        private boolean nativeErrorTerminal;
        private boolean queued, registrationAttempted, building;
        private long queueGeneration;

        State(Executor executor, Runnable reload, Runnable invalidate, Consumer<Throwable> diagnostic, Version initial) {
            this.executor = executor;
            this.reload = reload;
            this.invalidate = invalidate;
            this.diagnostic = diagnostic;
            requested = initial;
        }

        synchronized void request(Version version) {
            if (version.session() != requested.session() || same(version, requested)) return;
            requested = version;
            schedule();
        }

        synchronized void started(Version version) {
            if (version.session() != requested.session()) return;
            if (version.content() >= requested.content() && version.information() >= requested.information()) requested = version;
            building = true;
            lastRegistered = null;
            failedVersion = null;
            failure = null;
            if (nativeErrorTerminal) { nativeFailure = null; nativeErrorTerminal = false; }
        }

        synchronized void registered(Version version) {
            if (version.session() != requested.session()) return;
            lastRegistered = version;
            registrationAttempted = true;
            building = true;
            if (!same(version, requested)) schedule();
        }

        synchronized void failed(Version version, Throwable cause) {
            if (version.session() != requested.session()) return;
            registrationAttempted = true;
            lastRegistered = null;
            fail(version, cause);
        }

        synchronized void tick(boolean loaded, int status, boolean workerActive) {
            if (workerActive && (status == 1 || status == -1) && !building) {
                building = true;
                lastRegistered = null; // An external EMI reload must register MMCR again.
            }
            if (status == -1) {
                if (nativeFailure == null || !workerActive && !nativeErrorTerminal) {
                    nativeFailure = new IllegalStateException("EMI reload failed for " + requested
                            + (workerActive ? " (native retry active)" : " (worker terminated)"), nativeFailure);
                    diagnostic.accept(nativeFailure);
                }
                if (!workerActive) { nativeErrorTerminal = true; failedVersion = requested; building = false; }
            } else if (loaded && status == 2 && building) {
                building = false;
                if (lastRegistered != null && same(lastRegistered, requested) && !same(failedVersion, requested)) {
                    lastCompleted = lastRegistered;
                    dispatched = requested;
                    nativeFailure = null;
                    nativeErrorTerminal = false;
                } else if (lastRegistered == null && failure == null) {
                    fail(requested, new IllegalStateException("EMI bake finished without a successful MMCR registration for " + requested));
                }
                schedule();
            }
        }

        private void fail(Version version, Throwable cause) {
            failedVersion = version;
            failure = cause;
            diagnostic.accept(cause);
        }

        private void schedule() {
            // Do not race EMI's first initialization, but an attempted registration (even a
            // failed first attempt) permits recovery when a genuinely new token arrives.
            if (queued || !registrationAttempted || same(requested, lastCompleted)
                    || same(requested, dispatched) || same(requested, failedVersion)) return;
            queued = true;
            long generation = queueGeneration;
            executor.execute(() -> {
                synchronized (State.this) {
                    if (generation != queueGeneration) return;
                    queued = false;
                    if (!registrationAttempted || same(requested, lastCompleted) || same(requested, dispatched)
                            || same(requested, failedVersion)) return;
                    dispatched = requested;
                    building = true;
                    lastRegistered = null;
                    failure = null;
                    nativeFailure = null;
                    nativeErrorTerminal = false;
                    failedVersion = null;
                    try { invalidate.run(); reload.run(); }
                    catch (RuntimeException exception) { fail(requested, exception); building = false; }
                }
            });
        }

        synchronized void reset(Version version) {
            queueGeneration++;
            requested = version;
            lastRegistered = lastCompleted = dispatched = failedVersion = null;
            failure = null;
            nativeFailure = null;
            nativeErrorTerminal = false;
            queued = registrationAttempted = building = false;
        }
        synchronized Version completed() { return lastCompleted; }
        synchronized Throwable failure() { return failure != null ? failure : nativeFailure; }
        synchronized boolean current() {
            return same(requested, lastCompleted) && !building && !same(requested, failedVersion) && nativeFailure == null;
        }

        private static boolean same(Version left, Version right) {
            return left != null && right != null && left.session() == right.session()
                    && left.content() == right.content() && left.information() == right.information();
        }
    }
}
