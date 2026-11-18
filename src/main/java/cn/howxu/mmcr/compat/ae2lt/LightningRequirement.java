package cn.howxu.mmcr.compat.ae2lt;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;

import cn.howxu.mmcr.api.recipe.requirement.RequirementExecutionPolicy;

import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.async.AsyncResourceValue;
import cn.howxu.mmcr.api.compat.ae2lt.LightningViewFacet;
import cn.howxu.mmcr.internal.recipe.ResourceSearch;
import cn.howxu.mmcr.internal.capability.NativeAsyncResourceValues;
import net.minecraft.resources.ResourceLocation;
import java.util.Set;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import java.util.List;
import java.util.Objects;

/** One-shot lightning consumption or production, expressed per parallel recipe.
 * @author howxu <dev@howxu.cn>
 */
public record LightningRequirement(LightningTier tier, RecipeModifier.IOType io,
                                   long amount, float chance, List<String> tags) implements ResourceSearch {
    @Override public boolean searchAvailable() { return LightningBridge.get().available(); }
    @Override public Object predicateKey() { return tier; }
    @Override public Set<AsyncResourceValue> accepted(Set<AsyncResourceValue> universe) { return Set.of(outputValue()); }
    @Override public AsyncResourceValue outputValue() { return NativeAsyncResourceValues.lightning(tier); }
    @Override public ResourceLocation failure(boolean valid, boolean missingPort) {
        return (io.isInput() ? LightningFailureReasons.INPUT_MISSING : LightningFailureReasons.OUTPUT_BLOCKED).id();
    }
    @Override public ResourceLocation overlapIdentity() { return tier.requirementId(); }
    @Override public Limits limits(MachineCapability port, int slot) {
        var view = port.facet(LightningViewFacet.class);
        long capacity = io.isInput() ? 0L : view.map(facet -> facet.storage().capacity(slot, tier)).orElse(0L);
        return new Limits(Long.MAX_VALUE, capacity, null);
    }

    public LightningRequirement {
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(io, "io");
        amount = LightningRecipeTypes.recipeAmount(amount);
        if (io.isInput() && chance != 1F) throw new IllegalArgumentException("Lightning inputs do not support chance");
        chance = MachineOutput.clampChance(chance);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public static LightningRequirement input(LightningTier tier, long amount) {
        return new LightningRequirement(tier, RecipeModifier.IOType.INPUT, amount, 1F, List.of());
    }

    public static LightningRequirement output(LightningTier tier, long amount, float chance) {
        return new LightningRequirement(tier, RecipeModifier.IOType.OUTPUT, amount, chance, List.of());
    }

    @Override
    public RequirementExecutionPolicy execution() { return io.isInput() ? RequirementExecutionPolicy.START_MATERIAL : RequirementExecutionPolicy.LEGACY; }

    @Override
    public long batchAmount(int duration) { return amount; }

    @Override
    public Object batchIdentity(DynamicOps<JsonElement> ops) {
        return tier;
    }

    @Override
    public float consumptionProbability() { return 1F; }

    @Override
    public LightningRequirement asRetainedInput() {
        return this;
    }

    @Override public RequirementType<LightningRequirement> type() { return LightningRecipeTypes.requirementType(tier); }
}
