package cn.howxu.mmcr.compat.botania;

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
 * Neutral mana requirement with a canonical type independent of the loaded bridge.
 *
 * @author howxu <dev@howxu.cn>
 */
public record ManaRequirement(RecipeModifier.IOType io, long amount, List<String> tags) implements MachineRequirement {
    public static final MapCodec<ManaRequirement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(ignored -> BotaniaManaIds.MANA.toString()),
            RecipeModifier.IO_TYPE_CODEC.optionalFieldOf("io", RecipeModifier.IOType.INPUT)
                    .forGetter(ManaRequirement::io),
            Codec.LONG.fieldOf("amount").forGetter(ManaRequirement::amount),
            Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(ManaRequirement::tags)
    ).apply(instance, (ignored, io, amount, tags) -> new ManaRequirement(io, amount, tags)));
    public static final RecipeSyncCodec<ManaRequirement> SYNC_CODEC = RecipeSyncCodec.json(CODEC.codec());

    private static final RequirementHandler<ManaRequirement> UNAVAILABLE = (requirement, capabilities, context) ->
            RequirementHandlerSupport.blockedPlan(requirement, context, ManaFailureReasons.BOTANIA_UNAVAILABLE);
    private static volatile RequirementHandler<ManaRequirement> delegate = UNAVAILABLE;
    private static final RequirementHandler<ManaRequirement> HANDLER = new RequirementHandler<>() {
        @Override
        public RequirementPlan plan(ManaRequirement value, List<MachineCapability> capabilities,
                                    PlanningContext context) {
            return delegate.plan(value, capabilities, context);
        }

        @Override
        public List<ResourceWakeup> resourceWakeups(ManaRequirement value) {
            return delegate.resourceWakeups(value);
        }

        @Override
        public ManaRequirement applyModifiers(ManaRequirement value, List<RecipeModifier> modifiers) {
            return new ManaRequirement(value.io(),
                    ManaRecipeDeclarations.applyAmount(value.amount(), value.io(), modifiers), value.tags());
        }

        @Override
        public ManaRequirement applyLevelModifiers(ManaRequirement value, double energy, double output) {
            return value.io() == RecipeModifier.IOType.OUTPUT
                    ? new ManaRequirement(value.io(), ManaRecipeDeclarations.applyOutputMultiplier(value.amount(), output),
                            value.tags()) : value;
        }

        @Override
        public boolean overlaps(ManaRequirement value, MachineRequirement other) {
            return other instanceof ManaRequirement mana && mana.io() == value.io();
        }
    };

    public static final RequirementType<ManaRequirement> TYPE = new RequirementType.Definition<>(
            BotaniaManaIds.MANA, CODEC, HANDLER,
            value -> new ManaRequirement(value.io(), value.amount(), value.tags()), SYNC_CODEC);

    public ManaRequirement {
        Objects.requireNonNull(io, "io");
        if (amount <= 0L) throw new IllegalArgumentException("amount must be positive");
        tags = List.copyOf(Objects.requireNonNull(tags, "tags"));
        if (tags.size() > 1024) throw new IllegalArgumentException("Invalid tag count");
    }

    public static ManaRequirement input(long amount) {
        return new ManaRequirement(RecipeModifier.IOType.INPUT, amount, List.of());
    }

    public static ManaRequirement output(long amount) {
        return new ManaRequirement(RecipeModifier.IOType.OUTPUT, amount, List.of());
    }

    @Override
    public RequirementType<ManaRequirement> type() {
        return TYPE;
    }

    public static void installHandler(RequirementHandler<ManaRequirement> handler) {
        delegate = Objects.requireNonNull(handler, "handler");
    }

    public static void installUnavailableHandler() {
        delegate = UNAVAILABLE;
    }
}
