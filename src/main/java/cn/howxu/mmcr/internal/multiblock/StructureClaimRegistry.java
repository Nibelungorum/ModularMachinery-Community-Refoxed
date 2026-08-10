package cn.howxu.mmcr.internal.multiblock;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server-thread ownership graph for formed multiblock components.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class StructureClaimRegistry {
    private final Map<BlockPos, Set<BlockPos>> ownersByComponent = new LinkedHashMap<>();
    private final Map<BlockPos, Set<Claim>> claimsByController = new LinkedHashMap<>();
    private final Map<BlockPos, ResourceDomain> domainsByController = new HashMap<>();
    private long nextDomainId;
    private long nextGeneration;

    public ClaimResult claim(BlockPos controllerPos, List<Claim> requested) {
        BlockPos immutableControllerPos = controllerPos.immutable();
        List<Claim> normalized = requested.stream().distinct().toList();
        for (Claim claim : normalized) {
            Set<BlockPos> owners = ownersByComponent.getOrDefault(claim.componentPos(), Set.of());
            for (BlockPos owner : owners) {
                if (!owner.equals(immutableControllerPos)
                        && (claim.policy() == ComponentClaimPolicy.EXCLUSIVE
                        || claimsByController.get(owner).stream().anyMatch(existing ->
                        existing.componentPos().equals(claim.componentPos())
                                && existing.policy() == ComponentClaimPolicy.EXCLUSIVE))) {
                    return ClaimResult.conflict(claim.componentPos(), owner);
                }
            }
        }

        release(immutableControllerPos);
        claimsByController.put(immutableControllerPos, new LinkedHashSet<>(normalized));
        for (Claim claim : normalized) {
            ownersByComponent.computeIfAbsent(claim.componentPos(), ignored -> new LinkedHashSet<>())
                    .add(immutableControllerPos);
        }
        rebuildDomains();
        return ClaimResult.success();
    }

    public void release(BlockPos controllerPos) {
        Set<Claim> previous = claimsByController.remove(controllerPos);
        if (previous == null) {
            return;
        }
        for (Claim claim : previous) {
            Set<BlockPos> owners = ownersByComponent.get(claim.componentPos());
            if (owners == null) {
                continue;
            }
            owners.remove(controllerPos);
            if (owners.isEmpty()) {
                ownersByComponent.remove(claim.componentPos());
            }
        }
        rebuildDomains();
    }

    public Set<BlockPos> ownersOf(BlockPos componentPos) {
        return Set.copyOf(ownersByComponent.getOrDefault(componentPos, Set.of()));
    }

    public @Nullable ResourceDomain domainFor(BlockPos controllerPos) {
        return domainsByController.get(controllerPos);
    }

    public long generationFor(BlockPos controllerPos) {
        ResourceDomain domain = domainFor(controllerPos);
        return domain == null ? 0 : domain.generation();
    }

    private void rebuildDomains() {
        domainsByController.clear();
        Map<BlockPos, Set<BlockPos>> sharedControllersByComponent = new HashMap<>();
        for (Map.Entry<BlockPos, Set<Claim>> entry : claimsByController.entrySet()) {
            for (Claim claim : entry.getValue()) {
                if (claim.policy() == ComponentClaimPolicy.SHARED_SERIALIZED) {
                    sharedControllersByComponent.computeIfAbsent(claim.componentPos(), ignored -> new LinkedHashSet<>())
                            .add(entry.getKey());
                }
            }
        }

        Set<BlockPos> visited = new HashSet<>();
        for (BlockPos controller : claimsByController.keySet()) {
            if (!visited.add(controller)) {
                continue;
            }
            Set<BlockPos> controllers = new LinkedHashSet<>();
            ArrayDeque<BlockPos> pending = new ArrayDeque<>();
            pending.add(controller);
            while (!pending.isEmpty()) {
                BlockPos current = pending.removeFirst();
                controllers.add(current);
                for (Claim claim : claimsByController.get(current)) {
                    if (claim.policy() != ComponentClaimPolicy.SHARED_SERIALIZED) {
                        continue;
                    }
                    for (BlockPos connected : sharedControllersByComponent.get(claim.componentPos())) {
                        if (visited.add(connected)) {
                            pending.addLast(connected);
                        }
                    }
                }
            }
            ResourceDomain domain = new ResourceDomain(++nextDomainId, ++nextGeneration, controllers);
            for (BlockPos member : controllers) {
                domainsByController.put(member, domain);
            }
        }
    }

    public record Claim(BlockPos componentPos, ComponentClaimPolicy policy) {
        public Claim {
            componentPos = componentPos.immutable();
        }
    }

    public record Conflict(BlockPos componentPos, BlockPos ownerPos) {
        public Conflict {
            componentPos = componentPos.immutable();
            ownerPos = ownerPos.immutable();
        }
    }

    public record ClaimResult(boolean accepted, @Nullable Conflict conflict) {
        public static ClaimResult success() {
            return new ClaimResult(true, null);
        }

        public static ClaimResult conflict(BlockPos componentPos, BlockPos ownerPos) {
            return new ClaimResult(false, new Conflict(componentPos, ownerPos));
        }
    }

    public record ResourceDomain(long id, long generation, Set<BlockPos> controllers) {
        public ResourceDomain {
            controllers = Set.copyOf(controllers);
        }
    }
}
