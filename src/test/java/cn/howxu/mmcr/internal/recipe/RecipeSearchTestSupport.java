package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.api.capability.async.AsyncResourceValue;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.recipe.InputScope;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.*;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Pure values only; no ItemStack or registry fixture is needed. @author howxu <dev@howxu.cn> */
final class RecipeSearchTestSupport {
    static final Identifier ITEM = BuiltinCapabilityDefinitions.ITEM_TYPE.id();
    static final AsyncResourceValue IRON = new AsyncResourceValue(Identifier.parse("minecraft:iron_ingot"), "{}");
    static final Scope LEGACY = new Scope(InputScope.legacy(), Set.of());
    static final Overlap NO_OVERLAP = new Overlap(Set.of(), false, false, false);

    private RecipeSearchTestSupport() {}

    static RecipeSearchSnapshot singleResourceSnapshot(long available, long maximumParallelism) {
        return snapshot(List.of(pool(available)), List.of(candidate("a", maximumParallelism,
                List.of(input(0, 1L, 1F, List.of(new Port(0, 0, 0L, Long.MAX_VALUE)))), List.of(LEGACY))));
    }

    static RecipeSearchSnapshot twoScopeSnapshot(long first, long second) {
        Scope a = new Scope(new InputScope(InputScope.Kind.ISOLATED, List.of(new BlockPos(1, 0, 0))), Set.of(0));
        Scope b = new Scope(new InputScope(InputScope.Kind.ISOLATED, List.of(new BlockPos(2, 0, 0))), Set.of(1));
        return snapshot(List.of(pool(first), pool(second)), List.of(candidate("a", 10L,
                List.of(input(0, 1L, 1F, List.of(new Port(0, 0, 0, Long.MAX_VALUE),
                        new Port(1, 1, 0, Long.MAX_VALUE)))), List.of(a, b))));
    }

    static RecipeSearchSnapshot sharedPoolSnapshot() {
        List<Port> ports = List.of(new Port(0, 0, 0L, Long.MAX_VALUE), new Port(0, 1, 0L, Long.MAX_VALUE));
        return snapshot(List.of(pool(8L)), List.of(candidate("a", 256L,
                List.of(input(0, 1L, 1F, ports), input(1, 1L, 1F, ports)), List.of(LEGACY))));
    }

    static Rule input(int index, long amount, float consume, List<Port> ports) {
        return new Rule(index, ITEM, Kind.RESOURCE, IOType.INPUT, amount, 0D, 1F, consume,
                false, true, Set.of(IRON), null, ports, BuiltinFailureReasons.MISSING_INPUT.id());
    }

    static Rule output(int index, long amount, boolean partial, float chance, List<Port> ports) {
        return new Rule(index, ITEM, Kind.RESOURCE, IOType.OUTPUT, amount, 0D, chance, 1F,
                partial, true, Set.of(), IRON, ports, BuiltinFailureReasons.MISSING_OUTPUT.id());
    }

    static Candidate candidate(String id, long maximum, List<Rule> rules, List<Scope> scopes) {
        return new Candidate(Identifier.parse("test:" + id), 0, (int) rules.stream()
                .filter(rule -> rule.direction() == IOType.INPUT).count(), maximum, null, rules, scopes,
                NO_OVERLAP, null, -1);
    }

    static AsyncCapabilitySnapshot pool(long available) {
        return new AsyncCapabilitySnapshot.Resource(ITEM, List.of(new AsyncCapabilitySnapshot.ResourceSlot(
                Optional.of(IRON), available, Long.MAX_VALUE)));
    }

    static RecipeSearchSnapshot snapshot(List<AsyncCapabilitySnapshot> pools, List<Candidate> candidates) {
        return new RecipeSearchSnapshot(new Stamp(1L, 1L, 1L, 1L, 1L, 0L, 1L, 256L, 1), pools, candidates);
    }
}
