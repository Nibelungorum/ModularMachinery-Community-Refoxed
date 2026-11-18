package cn.howxu.mmcr.compat.ars_nouveau;

import cn.howxu.mmcr.api.recipe.RecipeSemantics;

import cn.howxu.mmcr.api.recipe.requirement.ScalarRecipeRule;
import cn.howxu.mmcr.compat.ars_nouveau.loaded.SourcePortCapability;

import cn.howxu.mmcr.api.recipe.requirement.RequirementExecutionPolicy;

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
public record SourceRequirement(RecipeModifier.IOType io, long amount, float chance, float consumeChance,
                                List<String> tags) implements MachineRequirement {
    public static final MapCodec<SourceRequirement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(ignored -> ArsSourceIds.SOURCE.toString()),
            RecipeModifier.IO_TYPE_CODEC.optionalFieldOf("io", RecipeModifier.IOType.INPUT)
                    .forGetter(SourceRequirement::io),
            Codec.LONG.fieldOf("amount").forGetter(SourceRequirement::amount),
            Codec.floatRange(0F, 1F).optionalFieldOf("chance", 1F).forGetter(SourceRequirement::chance),
            Codec.floatRange(0F, 1F).optionalFieldOf("consume_chance", 1F).forGetter(SourceRequirement::consumeChance),
            Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(SourceRequirement::tags)
    ).apply(instance, (ignored, io, amount, chance, consumeChance, tags) ->
            new SourceRequirement(io, amount, chance, consumeChance, tags)));

    private static final RequirementHandler<SourceRequirement> UNAVAILABLE = (requirement, capabilities, context) ->
            RequirementHandlerSupport.blockedPlan(requirement, context, SourceFailureReasons.ARS_UNAVAILABLE);
    private static volatile Object version = new Object();
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
                    SourceRecipeDeclarations.applyAmount(value.amount(), value.io(), modifiers),
                    value.io() == RecipeModifier.IOType.OUTPUT
                            ? SourceRecipeDeclarations.applyChance(value.chance(), value.io(), modifiers) : value.chance(),
                    value.io() == RecipeModifier.IOType.INPUT
                            ? SourceRecipeDeclarations.applyChance(value.consumeChance(), value.io(), modifiers)
                            : value.consumeChance(), value.tags());
        }

        @Override
        public SourceRequirement applyLevelModifiers(SourceRequirement value, double energy, double output) {
            return value.io() == RecipeModifier.IOType.OUTPUT
                    ? new SourceRequirement(value.io(), SourceRecipeDeclarations.applyOutputMultiplier(value.amount(), output),
                            value.chance(), value.consumeChance(), value.tags()) : value;
        }

        @Override
        public boolean overlaps(SourceRequirement value, MachineRequirement other) {
            return other instanceof SourceRequirement source && source.io() == value.io();
        }
    };

    public static final RequirementType<SourceRequirement> TYPE = new RequirementType.Definition<>(
            ArsSourceIds.SOURCE, CODEC, HANDLER,
            value -> new SourceRequirement(value.io(), value.amount(), value.chance(), value.consumeChance(), value.tags()),
            RecipeSyncCodec.json(CODEC.codec()));

    public SourceRequirement(RecipeModifier.IOType io, long amount, List<String> tags) {
        this(io, amount, 1F, 1F, tags);
    }

    public SourceRequirement {
        Objects.requireNonNull(io, "io");
        if (amount <= 0L) throw new IllegalArgumentException("amount must be positive");
        if (!Float.isFinite(chance) || chance < 0F || chance > 1F
                || !Float.isFinite(consumeChance) || consumeChance < 0F || consumeChance > 1F) {
            throw new IllegalArgumentException("chance and consume_chance must be between 0 and 1");
        }
        tags = List.copyOf(Objects.requireNonNull(tags, "tags"));
        if (tags.size() > 1024) throw new IllegalArgumentException("Invalid tag count");
    }

    public static SourceRequirement input(long amount) {
        return input(amount, 1F);
    }

    public static SourceRequirement input(long amount, float consumeChance) {
        return new SourceRequirement(RecipeModifier.IOType.INPUT, amount, 1F, consumeChance, List.of());
    }

    public static SourceRequirement output(long amount) {
        return output(amount, 1F);
    }

    public static SourceRequirement output(long amount, float chance) {
        return new SourceRequirement(RecipeModifier.IOType.OUTPUT, amount, chance, 1F, List.of());
    }

    @Override
    public RequirementExecutionPolicy execution() { return io.isInput() ? RequirementExecutionPolicy.START_MATERIAL : RequirementExecutionPolicy.LEGACY; }

    @Override
    public long batchAmount(int duration) { return amount; }

    @Override
    public float consumptionProbability() { return consumeChance; }

    @Override
    public SourceRequirement asRetainedInput() {
        return consumeChance == 0F ? this : new SourceRequirement(io, amount, chance, 0F, tags);
    }

    @Override
    public ScalarRecipeRule scalarRule() {
        return new ScalarRecipeRule(type().id(), amount, chance, consumeChance, false,
                (io.isInput() ? SourceFailureReasons.INPUT_MISSING : SourceFailureReasons.OUTPUT_BLOCKED).id(), true);
    }

    @Override
    public boolean acceptsScalarPort(MachineCapability port) { return port instanceof SourcePortCapability; }

    @Override
    public Object derivationVersion() { return supportsSearchSnapshot() ? version : null; }

    @Override
    public RequirementType<SourceRequirement> type() {
        return TYPE;
    }

    public static void installHandler(RequirementHandler<SourceRequirement> handler) {
        delegate = Objects.requireNonNull(handler, "handler");
        version = new Object();
        RecipeSemantics.changed();
    }

    /** Whether the loaded delegate has the native, snapshot-safe planning semantics. */
    public static boolean supportsSearchSnapshot() {
        return delegate instanceof SourceRequirementHandler;
    }

    public static void installUnavailableHandler() {
        delegate = UNAVAILABLE;
        version = new Object();
        RecipeSemantics.changed();
    }
}
