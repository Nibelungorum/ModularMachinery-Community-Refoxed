package cn.howxu.mmcr.compat.ars_nouveau;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.recipe.RecipeSyncCodec;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerSupport;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Objects;

/**
 * Neutral source requirement with a canonical type independent of the loaded bridge.
 *
 * @author howxu <dev@howxu.cn>
 */
public record SourceRequirement(RecipeModifier.IOType io, long amount, List<String> tags) implements MachineRequirement {
    public static final MapCodec<SourceRequirement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(ignored -> ArsSourceIds.SOURCE.toString()),
            RecipeModifier.IO_TYPE_CODEC.optionalFieldOf("io", RecipeModifier.IOType.INPUT)
                    .forGetter(SourceRequirement::io),
            Codec.LONG.fieldOf("amount").forGetter(SourceRequirement::amount),
            Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(SourceRequirement::tags)
    ).apply(instance, (ignored, io, amount, tags) -> new SourceRequirement(io, amount, tags)));

    private static final RequirementHandler<SourceRequirement> UNAVAILABLE = (requirement, capabilities, context) ->
            RequirementHandlerSupport.blockedPlan(requirement, context, SourceFailureReasons.ARS_UNAVAILABLE);
    private static volatile RequirementHandler<SourceRequirement> delegate = UNAVAILABLE;
    private static final RequirementHandler<SourceRequirement> HANDLER = new RequirementHandler<>() {
        @Override
        public RequirementPlan plan(SourceRequirement value, List<MachineCapability> capabilities,
                                    PlanningContext context) {
            return delegate.plan(value, capabilities, context);
        }

        @Override
        public List<ResourceWakeup> resourceWakeups(SourceRequirement value) {
            return delegate.resourceWakeups(value);
        }

        @Override
        public SourceRequirement applyModifiers(SourceRequirement value, List<RecipeModifier> modifiers) {
            return new SourceRequirement(value.io(),
                    SourceRecipeDeclarations.applyAmount(value.amount(), value.io(), modifiers), value.tags());
        }

        @Override
        public SourceRequirement applyLevelModifiers(SourceRequirement value, double energy, double output) {
            return value.io() == RecipeModifier.IOType.OUTPUT
                    ? new SourceRequirement(value.io(), SourceRecipeDeclarations.applyOutputMultiplier(value.amount(), output),
                            value.tags()) : value;
        }

        @Override
        public boolean overlaps(SourceRequirement value, MachineRequirement other) {
            return other instanceof SourceRequirement source && source.io() == value.io();
        }
    };

    public static final RequirementType<SourceRequirement> TYPE = new RequirementType.Definition<>(
            ArsSourceIds.SOURCE, CODEC, HANDLER,
            value -> new SourceRequirement(value.io(), value.amount(), value.tags()), RecipeSyncCodec.json(CODEC.codec()));

    public SourceRequirement {
        Objects.requireNonNull(io, "io");
        if (amount <= 0L) throw new IllegalArgumentException("amount must be positive");
        tags = List.copyOf(Objects.requireNonNull(tags, "tags"));
        if (tags.size() > 1024) throw new IllegalArgumentException("Invalid tag count");
    }

    public static SourceRequirement input(long amount) {
        return new SourceRequirement(RecipeModifier.IOType.INPUT, amount, List.of());
    }

    public static SourceRequirement output(long amount) {
        return new SourceRequirement(RecipeModifier.IOType.OUTPUT, amount, List.of());
    }

    @Override
    public RequirementType<SourceRequirement> type() {
        return TYPE;
    }

    public static void installHandler(RequirementHandler<SourceRequirement> handler) {
        delegate = Objects.requireNonNull(handler, "handler");
    }

    public static void installUnavailableHandler() {
        delegate = UNAVAILABLE;
    }
}
