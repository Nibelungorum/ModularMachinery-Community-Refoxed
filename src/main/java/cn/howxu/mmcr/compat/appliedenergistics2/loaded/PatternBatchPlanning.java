package cn.howxu.mmcr.compat.appliedenergistics2.loaded;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.pattern.PatternBatchDetails;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.internal.item.PatternBatchData;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Main-thread publication and world-independent, conservative crafting-plan compression.
 * @author howxu <dev@howxu.cn>
 */
public final class PatternBatchPlanning {
    private static final Map<PatternInterfaceBlockEntity, Publication> HOSTS = new IdentityHashMap<>();
    private static final Map<IGrid, Snapshot> GRIDS = new IdentityHashMap<>();

    private PatternBatchPlanning() {
    }

    public static boolean isBatchHost(PatternProviderLogicHost host) {
        if (!(host instanceof PatternInterfaceBlockEntity patternHost)) return false;
        return switch (patternHost.kind().id()) {
            case "ae2_me_pattern_interface", "eae_me_extended_pattern_interface", "eaep_me_mirror_pattern_interface" -> true;
            default -> false;
        };
    }

    /** Called before AE2 refreshes the provider index, never from a crafting calculator. */
    public static List<IPatternDetails> publish(PatternInterfaceBlockEntity host, List<IPatternDetails> originals) {
        requireServerThread(host);
        if (host.isRemoved()) {
            remove(host);
            return List.copyOf(originals);
        }
        if (!isBatchHost(host)) return List.copyOf(originals);
        var patterns = new ArrayList<IPatternDetails>();
        Map<AEItemKey, BatchLimit> limits = new LinkedHashMap<>();
        for (var original : originals) {
            if (original instanceof PatternBatchDetails) continue;
            // Only native processing definitions have a proven, lossless standard decoding protocol.
            if (original.getClass() != AEProcessingPattern.class) {
                MMCR.LOG.info("[PatternBatchDiag] publish skipped: host={} patternType={} outputs={} reason=non-native-processing-pattern",
                        host.getBlockPos(), original.getClass().getName(), original.getOutputs());
                patterns.add(original);
                continue;
            }
            var profiles = PatternBatchSupport.inspect(host, original);
            if (profiles.isEmpty()) {
                MMCR.LOG.info("[PatternBatchDiag] publish fallback: host={} outputs={} reason=no-matching-profile",
                        host.getBlockPos(), original.getOutputs());
                patterns.add(original);
                continue;
            }
            for (var profile : profiles) {
                var binding = profile.binding();
                var unit = new PatternBatchDetails(original, new PatternBatchData(PatternBatchData.VERSION,
                        binding.recipePoolId(), binding.recipeId(), binding.recipeCraftsPerPattern(), 1));
                patterns.add(unit);
                limits.merge(unit.getDefinition(), new BatchLimit(profile.maxMultiplier(), profile.safety()),
                        BatchLimit::merge);
            }
        }
        var publication = new Publication(host.getMainNode().getGrid(), List.copyOf(patterns), Map.copyOf(limits));
        synchronized (HOSTS) {
            var previous = HOSTS.put(host, publication);
            if (previous != null && previous.grid() != publication.grid()) rebuild(previous.grid());
            rebuild(publication.grid());
        }
        MMCR.LOG.info("[PatternBatchDiag] publish: host={} kind={} grid={} links={} originals={} published={} limits={}",
                host.getBlockPos(), host.kind().id(), System.identityHashCode(publication.grid()),
                host.linkedControllerPositions(), originals.size(), patterns.size(), limits.size());
        return publication.patterns();
    }

    public static void remove(PatternInterfaceBlockEntity host) {
        requireServerThread(host);
        synchronized (HOSTS) {
            var previous = HOSTS.remove(host);
            if (previous != null) rebuild(previous.grid());
        }
    }

    /** Pure lookup: safe even when calculation construction occurs off the server thread. */
    public static Snapshot capture(IGrid grid) {
        synchronized (HOSTS) {
            return GRIDS.getOrDefault(grid, Snapshot.EMPTY);
        }
    }

    public static List<IPatternDetails> publishedPatterns(PatternInterfaceBlockEntity host,
                                                         List<IPatternDetails> originals) {
        synchronized (HOSTS) {
            var publication = HOSTS.get(host);
            return publication == null ? List.copyOf(originals) : publication.patterns();
        }
    }

    /** Only this exact unit identity is indexed; never route to its bare definition. */
    public static IPatternDetails providerKey(IPatternDetails pattern) {
        return pattern instanceof PatternBatchDetails batch ? batch.unitPattern() : pattern;
    }

    public static boolean contains(PatternInterfaceBlockEntity host, List<IPatternDetails> originals,
                                   IPatternDetails requested) {
        var published = publishedPatterns(host, originals);
        if (requested instanceof PatternBatchDetails batch) {
            return originals.contains(batch.original()) && published.contains(batch.unitPattern());
        }
        return originals.contains(requested) && published.contains(requested);
    }

    private static void requireServerThread(PatternInterfaceBlockEntity host) {
        if (!(host.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()) {
            throw new IllegalStateException("Pattern batch publication requires the server thread");
        }
    }

    /** Caller holds HOSTS; replace the entire immutable grid value in one publication. */
    private static void rebuild(IGrid grid) {
        if (grid == null) return;
        Map<AEItemKey, BatchLimit> limits = new LinkedHashMap<>();
        for (var publication : HOSTS.values()) {
            if (publication.grid() == grid) {
                publication.limits().forEach((key, limit) -> limits.merge(key, limit, BatchLimit::merge));
            }
        }
        if (limits.isEmpty()) GRIDS.remove(grid);
        else GRIDS.put(grid, new Snapshot(limits));
    }

    /** Does not mutate simulation state, its counters, or any original pattern. */
    public static LinkedHashMap<IPatternDetails, Long> rewrite(Map<IPatternDetails, Long> crafts, Snapshot snapshot) {
        var unchanged = new LinkedHashMap<>(crafts);
        if (snapshot.limits().isEmpty() || crafts.isEmpty()) {
            MMCR.LOG.info("[PatternBatchDiag] rewrite skipped: tasks={} limits={} reason=empty-tasks-or-snapshot",
                    crafts.size(), snapshot.limits().size());
            return unchanged;
        }
        var patterns = new ArrayList<>(crafts.keySet());
        var graph = new DependencyGraph(patterns.size());
        for (int index = 0; index < patterns.size(); index++) {
            var pattern = patterns.get(index);
            var original = pattern instanceof PatternBatchDetails batch ? batch.original() : pattern;
            // Unknown input and remaining-item semantics prevent proving a complete dependency graph.
            if (original.getClass() != AEProcessingPattern.class) {
                MMCR.LOG.info("[PatternBatchDiag] rewrite skipped: patternType={} reason=non-native-processing-pattern",
                        original.getClass().getName());
                return unchanged;
            }
            boolean proven = crafts.get(pattern) > 0;
            if (pattern instanceof PatternBatchDetails batch) {
                var limit = snapshot.limits().get(batch.unitPattern().getDefinition());
                proven &= batch.data().multiplier() == 1 && limit != null
                        && limit.safety() == PatternBatchSupport.Safety.PROVEN_LINEAR;
            }
            for (var input : original.getInputs()) {
                var candidates = input.getPossibleInputs();
                if (candidates.length == 0) {
                    MMCR.LOG.info("[PatternBatchDiag] rewrite skipped: outputs={} reason=no-input-candidates", original.getOutputs());
                    return unchanged;
                }
                proven &= candidates.length == 1 && input.getMultiplier() > 0;
                for (var candidate : candidates) {
                    graph.input(index, candidate.what());
                    proven &= candidate.amount() > 0;
                    var remaining = input.getRemainingKey(candidate.what());
                    if (remaining != null) {
                        graph.output(index, remaining);
                        proven = false;
                    }
                }
            }
            for (var output : original.getOutputs()) {
                graph.output(index, output.what());
                proven &= output.amount() > 0;
            }
            graph.proven[index] = proven;
        }
        var safe = graph.safeComponents();
        var rewritten = new LinkedHashMap<IPatternDetails, Long>();
        try {
            for (int index = 0; index < patterns.size(); index++) {
                var pattern = patterns.get(index);
                long count = crafts.get(pattern);
                MMCR.LOG.info("[PatternBatchDiag] rewrite task: patternType={} outputs={} count={} proven={} safeComponent={} batch={}",
                        pattern.getClass().getName(), pattern.getOutputs(), count, graph.proven[index], safe[index],
                        pattern instanceof PatternBatchDetails batch ? batch.data() : null);
                if (safe[index] && pattern instanceof PatternBatchDetails batch && batch.data().multiplier() == 1) {
                    long maximum = snapshot.limits().get(batch.getDefinition()).maxMultiplier();
                    long full = count / maximum;
                    long remainder = count % maximum;
                    MMCR.LOG.info("[PatternBatchDiag] rewrite split: recipe={} maximum={} full={} remainder={}",
                            batch.data().recipeId(), maximum, full, remainder);
                    if (full > 0) rewritten.merge(batch.scaled(maximum), full, Math::addExact);
                    if (remainder > 0) rewritten.merge(batch.scaled(remainder), 1L, Math::addExact);
                } else {
                    rewritten.merge(pattern, count, Math::addExact);
                }
            }
        } catch (ArithmeticException exception) {
            MMCR.LOG.info("[PatternBatchDiag] rewrite skipped: reason=arithmetic-overflow", exception);
            return unchanged;
        }
        return rewritten;
    }

    /** A known serial recipe is distinct from an unproven recipe.
     * @author howxu <dev@howxu.cn>
     */
    public record BatchLimit(long maxMultiplier, PatternBatchSupport.Safety safety) {
        public BatchLimit {
            if (maxMultiplier < 1) throw new IllegalArgumentException("Batch multiplier must be positive");
            Objects.requireNonNull(safety, "safety");
        }

        public BatchLimit merge(BatchLimit other) {
            return new BatchLimit(Math.max(maxMultiplier, other.maxMultiplier),
                    safety == PatternBatchSupport.Safety.PROVEN_LINEAR
                            && other.safety == PatternBatchSupport.Safety.PROVEN_LINEAR
                            ? PatternBatchSupport.Safety.PROVEN_LINEAR : PatternBatchSupport.Safety.UNPROVEN);
        }
    }

    /** No world, host, hook, or mutable provider reference is captured.
     * @author howxu <dev@howxu.cn>
     */
    public record Snapshot(Map<AEItemKey, BatchLimit> limits) {
        public static final Snapshot EMPTY = new Snapshot(Map.of());

        public Snapshot {
            limits = Map.copyOf(limits);
        }
    }

    /** Implemented on each calculation, not on the shared simulation state.
     * @author howxu <dev@howxu.cn>
     */
    public interface SnapshotSource {
        Snapshot mmcr$batchSnapshot();
    }

    /** @author howxu <dev@howxu.cn> */
    private record Publication(IGrid grid, List<IPatternDetails> patterns, Map<AEItemKey, BatchLimit> limits) {
    }

    /** Task/key graph; work scales with plan size, never with the number of crafts.
     * @author howxu <dev@howxu.cn>
     */
    private static final class DependencyGraph {
        private final Map<AEKey, Set<Integer>> readers = new HashMap<>();
        private final Map<AEKey, Set<Integer>> writers = new HashMap<>();
        private final int[] parent;
        private final boolean[] proven;

        private DependencyGraph(int size) {
            parent = new int[size];
            proven = new boolean[size];
            for (int index = 0; index < size; index++) parent[index] = index;
        }

        private void input(int node, AEKey key) {
            readers.computeIfAbsent(key, ignored -> new HashSet<>()).add(node);
        }

        private void output(int node, AEKey key) {
            writers.computeIfAbsent(key, ignored -> new HashSet<>()).add(node);
        }

        private int root(int node) {
            while (parent[node] != node) {
                parent[node] = parent[parent[node]];
                node = parent[node];
            }
            return node;
        }

        private boolean[] safeComponents() {
            Map<AEKey, Set<Integer>> participants = new HashMap<>();
            readers.forEach((key, nodes) -> participants.computeIfAbsent(key, ignored -> new HashSet<>()).addAll(nodes));
            writers.forEach((key, nodes) -> participants.computeIfAbsent(key, ignored -> new HashSet<>()).addAll(nodes));
            for (var nodes : participants.values()) {
                int first = nodes.iterator().next();
                for (int node : nodes) parent[root(node)] = root(first);
            }
            var edges = new ArrayList<Set<Integer>>(parent.length);
            int[] indegree = new int[parent.length];
            for (int index = 0; index < parent.length; index++) edges.add(new HashSet<>());
            writers.forEach((key, producers) -> {
                for (int producer : producers) {
                    for (int consumer : readers.getOrDefault(key, Set.of())) {
                        if (edges.get(producer).add(consumer)) indegree[consumer]++;
                    }
                }
            });
            var ready = new ArrayDeque<Integer>();
            for (int index = 0; index < parent.length; index++) if (indegree[index] == 0) ready.add(index);
            while (!ready.isEmpty()) {
                int node = ready.remove();
                for (int target : edges.get(node)) if (--indegree[target] == 0) ready.add(target);
            }
            Set<Integer> unsafe = new HashSet<>();
            for (int index = 0; index < parent.length; index++) {
                if (!proven[index] || indegree[index] != 0) unsafe.add(root(index));
            }
            boolean[] safe = new boolean[parent.length];
            for (int index = 0; index < parent.length; index++) safe[index] = !unsafe.contains(root(index));
            return safe;
        }
    }
}
