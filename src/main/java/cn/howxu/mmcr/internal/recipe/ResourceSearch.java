package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.async.AsyncResourceValue;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** A server-side keyed-resource capture. Only values, limits and physical pool numbers reach the worker.
 * @author howxu <dev@howxu.cn>
 */
public interface ResourceSearch extends MachineRequirement {
    boolean searchAvailable();
    Object predicateKey();
    long amount();
    float chance();
    default boolean validResource() { return true; }
    default boolean matchesPort(MachineCapability port) { return true; }
    Set<AsyncResourceValue> accepted(Set<AsyncResourceValue> universe);
    @Nullable AsyncResourceValue outputValue();
    /** Exact component-free resource authorized for native async extraction; search alone grants no permission. */
    default @Nullable ResourceLocation exactCommitInput() { return null; }
    ResourceLocation failure(boolean valid, boolean missingPort);
    default @Nullable ResourceLocation missingScopeReason() { return null; }
    default @Nullable ResourceLocation overlapIdentity() { return null; }
    Limits limits(MachineCapability port, int slot);

    record Limits(long inputLimit, long capacity, @Nullable ResourceLocation rejected) {}

    /** One captured occurrence may alias slots of a different port or even another resource view. */
    interface Access {
        Object searchStorage();
        default Object searchIdentity() { return searchStorage(); }
        default Object searchSlot(int slot) { return slot; }
    }
}
