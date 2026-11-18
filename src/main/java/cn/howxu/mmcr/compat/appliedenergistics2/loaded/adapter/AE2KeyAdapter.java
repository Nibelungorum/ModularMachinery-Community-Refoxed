package cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.AEKeyType;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Converts one MMCR resource family to and from its AE2 key representation.
 *
 * @param <R> resource type handled by this adapter
 * @author howxu <dev@howxu.cn>
 */
public interface AE2KeyAdapter<R> {
    AEKeyType keyType();

    Class<R> resourceType();

    AEKey toKey(R resource);

    Optional<R> toResource(AEKey key);

    /** Null declines pattern conversion; a storage adapter need not support crafting. */
    default @Nullable MachineOutput patternOutput(R value, long amount) { return null; }
    default @Nullable R patternResource(MachineOutput output) { return null; }
    default @Nullable Predicate<R> patternInput(MachineRequirement requirement) { return null; }


    /** Exact pattern contribution for a JEI ingredient that cannot itself carry this count. */
    default @Nullable GenericStack exactJeiStack(MachineRequirement requirement) { return null; }

    default long maxPatternTotal() { return Long.MAX_VALUE; }
    default long stockingAmount() { return 1L; }

    /** Adjacent transfer is opt-in, independent of internal ME storage access. */
    default @Nullable CapabilityBinding.ExternalExposure<?> outputExposure() { return null; }
}
