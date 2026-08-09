package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContextPool;
import cn.howxu.mmcr.api.machine.FactoryThreadSpec;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Server-thread factory lane scheduler foundation. It never runs recipe work off-thread and async search must use snapshots only.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FactoryRecipeScheduler {

    private int threadLimit;
    private int parallelLimit;
    private final List<Lane> lanes = new ArrayList<>();
    private final List<FactoryRecipeThread> threads = new ArrayList<>();
    private final RecipeCraftingContextPool contextPool;

    public FactoryRecipeScheduler(int threadLimit) {
        this(threadLimit, RecipeCraftingContextPool.global());
    }

    public FactoryRecipeScheduler(int threadLimit, RecipeCraftingContextPool contextPool) {
        this.threadLimit = Math.max(1, threadLimit);
        this.parallelLimit = this.threadLimit;
        this.contextPool = contextPool;
        ensureBaseThread(null, contextPool);
    }

    public boolean startLane(Lane lane) {
        if (lane == null || !hasCapacity()) return false;
        lane.start();
        lanes.add(lane);
        return true;
    }

    public boolean hasCapacity() {
        return lanes.size() < threadLimit;
    }

    public int laneCapacity() {
        return Math.max(0, threadLimit - lanes.size());
    }

    public void tick() {
        tick(0L);
    }

    public void tick(long gameTime) {
        Iterator<Lane> iterator = lanes.iterator();
        while (iterator.hasNext()) {
            Lane lane = iterator.next();
            if (lane.tick(gameTime)) iterator.remove();
        }
    }

    public void stopAll() {
        for (Lane lane : lanes) {
            lane.stop();
        }
        lanes.clear();
        for (FactoryRecipeThread thread : threads) thread.invalidate();
        threads.clear();
        ensureBaseThread(null, contextPool);
    }

    public int activeLaneCount() {
        return lanes.size() + (int) threads.stream().filter(thread -> !thread.isBaseThread()).count();
    }

    public int threadLimit() {
        return threadLimit;
    }

    public void setThreadLimit(int threadLimit) {
        this.threadLimit = Math.max(1, threadLimit);
        while (threads.size() > this.threadLimit) {
            FactoryRecipeThread removed = threads.removeLast();
            removed.invalidate();
        }
        while (lanes.size() > this.threadLimit) {
            Lane removed = lanes.removeLast();
            removed.stop();
        }
    }

    public int availableParallelism() {
        return Math.max(0, parallelLimit - usedParallelism());
    }

    public int activeThreadCount() {
        return (int) threads.stream().filter(thread -> !thread.isIdle()).count();
    }

    public int usedParallelism() {
        int used = 0;
        for (FactoryRecipeThread thread : threads) {
            used = Math.min(parallelLimit, used + thread.usedParallelism());
        }
        return used;
    }

    public List<FactoryRecipeThread> allThreads() {
        return List.copyOf(threads);
    }

    public void ensureBaseThread(MachineControllerBlockEntity controller, RecipeCraftingContextPool contextPool) {
        if (!threads.isEmpty() && threads.getFirst().isBaseThread()) return;
        threads.removeIf(FactoryRecipeThread::isBaseThread);
        threads.addFirst(FactoryRecipeThread.base(controller, contextPool == null ? this.contextPool : contextPool));
    }

    public List<ThreadSnapshot> threadSnapshots() {
        List<ThreadSnapshot> snapshots = new ArrayList<>(threadLimit);
        for (int index = 0; index < threads.size(); index++) {
            FactoryRecipeThread thread = threads.get(index);
            var active = thread.getActiveRecipe();
            snapshots.add(new ThreadSnapshot(index, thread.isBaseThread(), thread.isCoreThread(), active != null,
                    active == null || active.getRecipe() == null ? "" : active.getRecipe().id().toString(),
                    active == null ? 0 : active.getTick(), active == null ? 0 : active.getTotalTick(),
                    active == null ? 1 : active.getParallelism()));
        }
        for (int index = snapshots.size(); index < threadLimit; index++) {
            snapshots.add(new ThreadSnapshot(index, false, false, false, "", 0, 0, 1));
        }
        return List.copyOf(snapshots);
    }

    public List<MachineRecipe> availableCandidates(List<MachineRecipe> candidates) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        Map<Identifier, Integer> activeCounts = activeRecipeCounts();
        return candidates.stream()
                .filter(recipe -> canStartAdditionalThread(recipe, activeCounts))
                .toList();
    }

    private Map<Identifier, Integer> activeRecipeCounts() {
        Map<Identifier, Integer> counts = new LinkedHashMap<>();
        for (FactoryRecipeThread thread : threads) {
            if (thread.isIdle()) continue;
            var activeRecipe = thread.getActiveRecipe();
            if (activeRecipe == null || activeRecipe.getRecipe() == null) continue;
            counts.merge(activeRecipe.getRecipe().id(), 1, Integer::sum);
        }
        return counts;
    }

    private static boolean canStartAdditionalThread(MachineRecipe recipe, Map<Identifier, Integer> activeCounts) {
        if (recipe == null) return false;
        int maxThreads = recipe.maxThreads();
        return maxThreads <= 0 || activeCounts.getOrDefault(recipe.id(), 0) < maxThreads;
    }

    public void syncCoreThreads(MachineControllerBlockEntity controller, Machine machine, List<MachineRecipe> candidates,
                                RecipeCraftingContextPool contextPool) {
        ensureBaseThread(controller, contextPool);
        if (machine == null) return;
        threads.removeIf(FactoryRecipeThread::isCoreThread);
        Map<Identifier, MachineRecipe> byId = new LinkedHashMap<>();
        for (MachineRecipe recipe : candidates == null ? List.<MachineRecipe>of() : candidates) byId.putIfAbsent(recipe.id(), recipe);
        for (FactoryThreadSpec spec : machine.factoryThreads()) {
            Set<MachineRecipe> recipes = new LinkedHashSet<>();
            for (Identifier id : spec.recipeIds()) {
                MachineRecipe recipe = byId.get(id);
                if (recipe != null) recipes.add(recipe);
            }
            threads.add(FactoryRecipeThread.core(controller, contextPool == null ? this.contextPool : contextPool, spec.name(), recipes));
        }
    }

    public void tickThreads(MachineControllerBlockEntity controller, List<MachineRecipe> candidates,
                            long structureVersion, int parallelLimit, RecipeCraftingContextPool contextPool) {
        ensureBaseThread(controller, contextPool);
        this.parallelLimit = Math.max(1, parallelLimit);
        for (FactoryRecipeThread thread : List.copyOf(threads)) {
            thread.bindController(controller);
            thread.tick();
            thread.tickIdle();
            if (thread.isTimedOut()) threads.remove(thread);
        }
        if (controller == null || candidates == null || candidates.isEmpty()) return;

        while (threads.size() < threadLimit) {
            List<MachineRecipe> availableCandidates = availableCandidates(candidates);
            if (availableCandidates.isEmpty()) break;
            FactoryRecipeThread thread = FactoryRecipeThread.simple(controller, contextPool == null ? this.contextPool : contextPool);
            if (!thread.searchAndStartRecipe(availableCandidates, this.parallelLimit, structureVersion)) break;
            threads.add(thread);
        }
    }

    public void addThreadForTesting(FactoryRecipeThread thread) {
        ensureBaseThread(null, contextPool);
        if (thread != null && !threads.contains(thread)) threads.add(thread);
    }

    public void save(ValueOutput output) {
        output.putInt("thread_count", threads.size());
        for (int i = 0; i < threads.size(); i++) threads.get(i).save(output.child("thread_" + i));
    }

    public void load(ValueInput input, MachineControllerBlockEntity controller, RecipeCraftingContextPool contextPool) {
        threads.clear();
        int count = Math.max(0, input.getIntOr("thread_count", 0));
        for (int i = 0; i < count; i++) {
            threads.add(FactoryRecipeThread.load(input.childOrEmpty("thread_" + i), controller,
                    contextPool == null ? this.contextPool : contextPool));
        }
        ensureBaseThread(controller, contextPool);
    }

    public static RecipeSnapshot captureSnapshot(Identifier recipeId,
                                                 int parallelism,
                                                 long structureVersion,
                                                 List<ProcessingComponent> components) {
        List<ComponentSnapshot> componentSnapshots = new ArrayList<>();
        for (ProcessingComponent component : components == null ? List.<ProcessingComponent>of() : components) {
            Identifier typeId = component.getType() == null ? null : component.getType().getRegistryName();
            if (typeId == null && component.getComponent() != null && component.getComponent().kind() != null) {
                componentSnapshots.add(new ComponentSnapshot(
                        component.getComponent().kind().id(),
                        component.getRelativePos(),
                        component.tags()));
                continue;
            }
            componentSnapshots.add(new ComponentSnapshot(
                    typeId == null ? "unknown" : typeId.toString(),
                    component.getRelativePos(),
                    component.tags()));
        }
        return new RecipeSnapshot(recipeId, Math.max(1, parallelism), structureVersion, componentSnapshots);
    }

    public interface Lane {
        default void start() { }

        default boolean tick(long gameTime) {
            return tick();
        }

        boolean tick();

        void stop();
    }

    public record RecipeSnapshot(Identifier recipeId, int parallelism, long structureVersion,
                                 List<ComponentSnapshot> components) {
        public RecipeSnapshot {
            components = List.copyOf(components == null ? List.of() : components);
        }
    }

    public record ComponentSnapshot(String type, BlockPos relativePos, List<String> tags) {
        public ComponentSnapshot {
            type = type == null ? "unknown" : type;
            relativePos = relativePos == null ? BlockPos.ZERO : relativePos.immutable();
            tags = List.copyOf(tags == null ? List.of() : tags);
        }
    }

    public record ThreadSnapshot(int index, boolean baseThread, boolean coreThread, boolean active,
                                 String recipeId, int tick, int totalTick, int parallelism) {
        public static ThreadSnapshot idleBase() {
            return new ThreadSnapshot(0, true, false, false, "", 0, 0, 1);
        }
    }
}
