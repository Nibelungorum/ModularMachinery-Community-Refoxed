package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.api.capability.async.AsyncResourceValue;
import cn.howxu.mmcr.api.recipe.InputScope;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Value-only search inputs. Pool addresses represent physical reservation cells, not capability views.
 *
 * @author howxu <dev@howxu.cn>
 */
public record RecipeSearchSnapshot(Stamp stamp, List<AsyncCapabilitySnapshot> pools, List<Candidate> candidates) {
    public RecipeSearchSnapshot {
        pools = List.copyOf(pools);
        candidates = List.copyOf(candidates);
    }

    public record Stamp(long catalog, long structure, long capability, long modifier, long state,
                        long upgrade, long routing, long maximumParallelism, int laneLimit) {}

    public record Candidate(Identifier id, int priority, int inputCount, long maximumParallelism,
                            @Nullable Identifier staticFailure, List<Rule> rules, List<Scope> scopes,
                            Overlap overlap, @Nullable RecipeSearchProof.Reason proofReason,
                            int proofRequirementIndex) {
        public Candidate {
            if (maximumParallelism < 1L || scopes.isEmpty()) throw new IllegalArgumentException("Invalid search candidate");
            rules = List.copyOf(rules);
            scopes = List.copyOf(scopes);
        }
    }

    public enum Kind { RESOURCE, SCALAR, TEMPERATURE, HEAT, NOOP }

    public record Rule(int index, Identifier type, Kind kind, IOType direction, long amount,
                       double heat, float chance, float consumeChance, boolean partial, boolean valid,
                       Set<AsyncResourceValue> resources, @Nullable AsyncResourceValue output,
                       List<Port> ports, Identifier failureReason) {
        public Rule {
            resources = Set.copyOf(resources);
            ports = List.copyOf(ports);
        }
    }

    /** Resource capacity is captured for this rule's output resource, including validity and stack limits. */
    public record Port(int pool, int sourceIndex, long capacity, long transferLimit) {}

    public record Scope(InputScope input, Set<Integer> allowed) {
        public Scope {
            allowed = Set.copyOf(allowed);
        }

        public boolean allows(int sourceIndex) {
            return input.kind() == InputScope.Kind.LEGACY || allowed.contains(sourceIndex);
        }
    }

    /** Built-in item overlap ignores quantities/components, just as ItemRequirementHandler does. */
    public record Overlap(Set<Identifier> items, boolean anyItem, boolean hasItem, boolean customHandler) {
        public Overlap {
            items = Set.copyOf(items);
        }
    }
}
