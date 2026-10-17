package cn.howxu.mmcr.compat.appliedenergistics2.loaded;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.EffectiveRecipe;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PendingPatternQueue;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PendingPatternQueue.OfferLimits;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Schedules owned standard requests without participating in crafting CPU accounting.
 * @author howxu <dev@howxu.cn>
 */
public final class PendingPatternScheduler {
    private static final Map<ServerLevel, Set<BlockPos>> PENDING_TICKS = new WeakHashMap<>();
    private static final Map<ServerLevel, InterfaceCursor> INTERFACE_CURSORS = new WeakHashMap<>();
    private final PatternInterfaceBlockEntity host;
    private final PendingPatternQueue queue;
    private final Map<List<GenericStack>, Capacity> capacities = new HashMap<>();
    private List<Source> sources = List.of();
    private long capacityTick = Long.MIN_VALUE;
    private long catalogVersion = Long.MIN_VALUE;
    private final GroupCursor groupCursor = new GroupCursor();
    private final Map<Long, Map<BlockPos, MachineControllerBlockEntity.PendingPatternCursor>> searchCursors = new HashMap<>();
    private int nextController;
    private long lastTick = Long.MIN_VALUE;
    private long completedStarts;
    private boolean ticking;

    public PendingPatternScheduler(PatternInterfaceBlockEntity host, PendingPatternQueue queue) {
        this.host = host;
        this.queue = queue;
    }

    public static void schedule(PatternInterfaceBlockEntity host) {
        if (host.getLevel() instanceof ServerLevel level && !host.isRemoved()) {
            PENDING_TICKS.computeIfAbsent(level, ignored -> new HashSet<>()).add(host.getBlockPos());
        }
    }

    /** All controller tickers must establish their real round permits before starts are attempted. */
    public static void completeLevelTick(ServerLevel level) {
        Set<BlockPos> positions = PENDING_TICKS.remove(level);
        if (positions == null || !level.tickRateManager().runsNormally()) return;
        InterfaceCursor cursor = INTERFACE_CURSORS.computeIfAbsent(level, ignored -> new InterfaceCursor());
        for (BlockPos position : cursor.order(positions)) {
            if (level.hasChunkAt(position) && level.getBlockEntity(position) instanceof PatternInterfaceBlockEntity host
                    && !host.isRemoved() && host.getMainNode().isActive()) {
                long starts = host.pendingScheduler().completedStarts;
                host.pendingScheduler().tick(level.getGameTime());
                if (host.pendingScheduler().completedStarts != starts) cursor.started(position);
            }
        }
    }

    public @Nullable OfferLimits admissionLimits(IPatternDetails pattern, KeyCounter[] inputs, long gameTime) {
        if (!PatternInterfaceCraftingMachine.validInputs(pattern, inputs, host.getLevel())) return null;
        Capacity capacity = capacity(pattern.getOutputs(), gameTime);
        return capacity.group() == 0L ? null : new OfferLimits(capacity.total(), capacity.group());
    }

    public static boolean ready(long now, long firstTick, long lastTick, long pendingCopies,
                                long runnableCopies, int effectiveDuration) {
        return now > firstTick && pendingCopies > 0L && runnableCopies > 0L
                && (pendingCopies >= runnableCopies || now - lastTick >= 5L
                || now - firstTick >= 40L || effectiveDuration <= 5);
    }

    public void tick(long gameTime) {
        if (ticking || lastTick == gameTime || host.isRemoved() || host.getLevel() == null) return;
        lastTick = gameTime;
        ticking = true;
        try {
            dispatch(gameTime);
        } finally {
            ticking = false;
        }
    }

    private void dispatch(long gameTime) {
        queue.rebaseClock(gameTime);
        var groups = queue.groups();
        List<Long> ids = groups.stream().map(PendingPatternQueue.GroupSnapshot::id).toList();
        searchCursors.keySet().retainAll(ids);
        if (groups.isEmpty()) return;
        int attempted = 0;
        int[] preparations = {8};
        for (long id : groupCursor.next(ids)) {
            if (attempted >= 8 || preparations[0] <= 0) break;
            var group = groups.stream().filter(candidate -> candidate.id() == id).findFirst().orElseThrow();
            Capacity capacity = capacity(group.outputs(), gameTime);
            if (!ready(gameTime, group.firstTick(), group.lastTick(), group.copies(),
                    capacity.runnable(), capacity.duration())) continue;
            List<MachineControllerBlockEntity> controllers = capacity.controllers();
            groupCursor.attempted(id);
            var searches = searchCursors.computeIfAbsent(id, ignored -> new HashMap<>());
            searches.keySet().retainAll(controllers.stream().map(MachineControllerBlockEntity::getBlockPos).toList());
            int first = controllers.isEmpty() ? 0 : Math.floorMod(nextController, controllers.size());
            for (int c = 0; c < controllers.size() && attempted < 8 && preparations[0] > 0; c++) {
                attempted++;
                int index = (first + c) % controllers.size();
                nextController = (index + 1) % controllers.size();
                MachineControllerBlockEntity controller = controllers.get(index);
                try {
                    if (host.craftingMachine().startPending(group, controller,
                            searches.computeIfAbsent(controller.getBlockPos(), ignored -> new MachineControllerBlockEntity.PendingPatternCursor()),
                            () -> preparations[0]-- > 0)) {
                        completedStarts++;
                        capacities.clear();
                        break;
                    }
                } catch (RuntimeException exception) {
                    MMCR.LOG.warn("Pending pattern start failed at {}", host.getBlockPos(), exception);
                }
            }
        }
    }

    private Capacity capacity(List<GenericStack> outputs, long gameTime) {
        List<Source> current = new ArrayList<>();
        if (host.getLevel() != null) {
            for (BlockPos position : host.linkedControllerPositions().stream().sorted(BlockPos::compareTo).toList()) {
                if (!host.getLevel().hasChunkAt(position)
                        || !(host.getLevel().getBlockEntity(position) instanceof MachineControllerBlockEntity controller)) continue;
                var snapshot = controller.runtimeSnapshot();
                current.add(new Source(controller, snapshot.structure().version(), snapshot.capabilityVersion(),
                        snapshot.modifierVersion(), snapshot.stateVersion(), snapshot.upgradeContentRevision(),
                        snapshot.factory().activeLaneCount(), controller.isRedstonePaused()));
            }
        }
        if (capacityTick != gameTime || catalogVersion != RecipeRegistry.registryVersion() || !sources.equals(current)) {
            capacities.clear();
            sources = List.copyOf(current);
            capacityTick = gameTime;
            catalogVersion = RecipeRegistry.registryVersion();
        }
        return capacities.computeIfAbsent(List.copyOf(outputs), this::inspect);
    }

    private Capacity inspect(List<GenericStack> stacks) {
        List<MachineOutput> outputs = PatternInterfaceCraftingMachine.outputs(stacks);
        if (outputs == null) return new Capacity(0, 0, 0, Integer.MAX_VALUE, List.of());
        long total = 0L, group = 0L, runnable = 0L;
        int duration = Integer.MAX_VALUE;
        List<MachineControllerBlockEntity> controllers = new ArrayList<>();
        for (Source source : sources) {
            var controller = source.controller();
            var snapshot = controller.runtimeSnapshot();
            if (!snapshot.structure().formed() || !snapshot.structure().structureAreaLoaded() || source.paused()
                    || !snapshot.linkedPortPositions().contains(host.getBlockPos())) continue;
            boolean factory = controller.hasFactoryController();
            long lanes = factory ? Math.min(snapshot.factory().laneLimit(), controller.effectiveFactoryThreadLimit()) : 1L;
            long localTotal = 0L, localGroup = 0L, localRunnable = 0L;
            for (MachineRecipe recipe : controller.recipesForMachine()) {
                if (!snapshot.moduleConnectionStatus().canRunRecipe(recipe.requiredHostIds())) continue;
                EffectiveRecipe effective = controller.effectivePatternRecipe(recipe);
                if (effective == null) continue;
                long threads = factory ? Math.min(lanes, Math.min(effective.factoryThreadLimit(), effective.recipeThreadLimit())) : 1L;
                localTotal = Math.max(localTotal, multiply(threads, effective.parallelismLimit()));
                if (!controller.patternOutputsMatch(recipe, snapshot, outputs)) continue;
                long activeRecipe = snapshot.factory().lanes().stream().filter(lane -> recipe.id().equals(lane.recipeId())).count();
                long idle = factory ? Math.min(threads - activeRecipe, lanes - snapshot.factory().activeLaneCount())
                        : snapshot.crafting().recipeId() == null ? 1L : 0L;
                localGroup = Math.max(localGroup, multiply(threads, effective.parallelismLimit()));
                localRunnable = Math.max(localRunnable, multiply(idle, effective.parallelismLimit()));
                duration = Math.min(duration, effective.duration());
            }
            total = add(total, localTotal);
            if (localGroup > 0L) controllers.add(controller);
            group = add(group, localGroup);
            runnable = add(runnable, localRunnable);
        }
        return new Capacity(total, group, runnable, duration, List.copyOf(controllers));
    }

    private static long multiply(long first, long second) {
        if (first <= 0L || second <= 0L) return 0L;
        return first > Long.MAX_VALUE / second ? Long.MAX_VALUE : first * second;
    }

    private static long add(long first, long second) {
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }

    /** Successful work moves only its own interface to the tail, not unrelated competitors.
     * @author howxu <dev@howxu.cn>
     */
    public static final class InterfaceCursor {
        private final List<BlockPos> pending = new ArrayList<>();

        public List<BlockPos> order(Set<BlockPos> positions) {
            pending.removeIf(position -> !positions.contains(position));
            for (BlockPos position : positions.stream().sorted(BlockPos::compareTo).toList()) {
                if (!pending.contains(position)) pending.add(position.immutable());
            }
            return List.copyOf(pending);
        }

        public void started(BlockPos position) {
            if (pending.remove(position)) pending.add(position.immutable());
        }
    }

    /** Unattempted groups keep priority while controllers are busy.
     * @author howxu <dev@howxu.cn>
     */
    public static final class GroupCursor {
        private final List<Long> pending = new ArrayList<>();

        public List<Long> next(List<Long> ids) {
            pending.removeIf(id -> !ids.contains(id));
            for (long id : ids) if (!pending.contains(id)) pending.add(id);
            return List.copyOf(pending);
        }

        public void attempted(long id) {
            if (pending.remove(id)) pending.add(id);
        }
    }

    /** @author howxu <dev@howxu.cn> */
    private record Source(MachineControllerBlockEntity controller, long structure, long capability, long modifier,
                          long state, long upgrade, int activeLanes, boolean paused) { }

    /** @author howxu <dev@howxu.cn> */
    private record Capacity(long total, long group, long runnable, int duration,
                            List<MachineControllerBlockEntity> controllers) { }
}
