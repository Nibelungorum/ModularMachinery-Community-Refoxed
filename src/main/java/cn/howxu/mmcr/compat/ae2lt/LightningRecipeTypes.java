package cn.howxu.mmcr.compat.ae2lt;

import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Canonical recipe declarations; capability registration belongs to the startup window.
 * @author howxu <dev@howxu.cn>
 */
public final class LightningRecipeTypes {
    public static final ResourceLocation HIGH_VOLTAGE = LightningTier.HIGH_VOLTAGE.requirementId();
    public static final ResourceLocation EXTREME_HIGH_VOLTAGE = LightningTier.EXTREME_HIGH_VOLTAGE.requirementId();
    public static final ResourceLocation LIGHTNING = ResourceLocation.fromNamespaceAndPath("ae2lt", "lightning");
    public static final long MAX_AMOUNT = Integer.MAX_VALUE;

    private static final Codec<Long> AMOUNT_CODEC = Codec.LONG.validate(amount -> amount > 0L
                    ? DataResult.success(amount) : DataResult.error(() -> "Lightning amount must be positive"))
            .xmap(LightningRecipeTypes::recipeAmount, amount -> amount);
    private static final Codec<RecipeModifier.IOType> IO_CODEC = Codec.STRING.comapFlatMap(value -> switch (value) {
        case "input" -> DataResult.success(RecipeModifier.IOType.INPUT);
        case "output" -> DataResult.success(RecipeModifier.IOType.OUTPUT);
        default -> DataResult.error(() -> "Unknown lightning IO type: " + value);
    }, RecipeModifier.IOType::getKey);
    private static final EnumMap<LightningTier, RequirementType<LightningRequirement>> REQUIREMENTS = new EnumMap<>(LightningTier.class);
    private static final EnumMap<LightningTier, OutputType<LightningOutput>> OUTPUTS = new EnumMap<>(LightningTier.class);

    static {
        for (LightningTier tier : LightningTier.values()) {
            REQUIREMENTS.put(tier, createRequirementType(tier));
            OUTPUTS.put(tier, createOutputType(tier));
        }
    }

    private LightningRecipeTypes() {}

    public static void register() {
        for (LightningTier tier : LightningTier.values()) {
            if (RequirementHandlerRegistry.typeFor(tier.requirementId()) == null) RequirementHandlerRegistry.register(requirementType(tier));
            if (OutputRegistry.typeFor(tier.requirementId()) == null) OutputRegistry.register(outputType(tier));
        }
    }

    public static RequirementType<LightningRequirement> requirementType(LightningTier tier) {
        return REQUIREMENTS.get(Objects.requireNonNull(tier, "tier"));
    }

    public static OutputType<LightningOutput> outputType(LightningTier tier) {
        return OUTPUTS.get(Objects.requireNonNull(tier, "tier"));
    }

    static long recipeAmount(long amount) {
        if (amount <= 0L) throw new IllegalArgumentException("Lightning amount must be positive");
        return Math.min(amount, MAX_AMOUNT);
    }

    public static void validateRecipeAmounts(List<MachineRequirement> requirements) {
        long[][] totals = new long[LightningTier.values().length][RecipeModifier.IOType.values().length];
        for (MachineRequirement requirement : requirements) {
            if (!(requirement instanceof LightningRequirement lightning)) continue;
            int tier = lightning.tier().ordinal();
            int io = lightning.io().ordinal();
            if (lightning.amount() > MAX_AMOUNT - totals[tier][io]) {
                throw new IllegalArgumentException("Combined lightning amount exceeds recipe limit: "
                        + lightning.tier().requirementId() + " / " + lightning.io().getKey());
            }
            totals[tier][io] += lightning.amount();
        }
    }

    private static RequirementType<LightningRequirement> createRequirementType(LightningTier tier) {
        MapCodec<Declaration> declaration = RecordCodecBuilder.mapCodec(instance -> instance.group(
                discriminator(tier).fieldOf("type").forGetter(ignored -> tier.requirementId().toString()),
                IO_CODEC.optionalFieldOf("io", RecipeModifier.IOType.INPUT).forGetter(Declaration::io),
                AMOUNT_CODEC.fieldOf("amount").forGetter(Declaration::amount),
                Codec.FLOAT.optionalFieldOf("chance", 1F).forGetter(Declaration::chance),
                MachineRequirement.TAGS_CODEC.optionalFieldOf("tags", List.of()).forGetter(Declaration::tags)
        ).apply(instance, (ignored, io, amount, chance, tags) -> new Declaration(io, amount, chance, tags)));
        MapCodec<LightningRequirement> codec = declaration.flatXmap(value -> {
            if (value.io().isInput() && value.chance() != 1F) return DataResult.error(() -> "Lightning inputs do not support chance");
            return DataResult.success(new LightningRequirement(tier, value.io(), value.amount(), value.chance(), value.tags()));
        }, value -> DataResult.success(new Declaration(value.io(), value.amount(), value.chance(), value.tags())));
        return new RequirementType<>() {
            private final RequirementHandler<LightningRequirement> handler = new LightningRequirementHandler();
            @Override public ResourceLocation id() { return tier.requirementId(); }
            @Override public Set<ResourceLocation> capabilityIds() { return Set.of(LIGHTNING); }
            @Override public MapCodec<LightningRequirement> codec() { return codec; }
            @Override public RequirementHandler<LightningRequirement> handler() { return handler; }
        };
    }

    private static OutputType<LightningOutput> createOutputType(LightningTier tier) {
        MapCodec<LightningOutput> codec = RecordCodecBuilder.mapCodec(instance -> instance.group(
                discriminator(tier).fieldOf("type").forGetter(ignored -> tier.requirementId().toString()),
                AMOUNT_CODEC.fieldOf("amount").forGetter(LightningOutput::amount),
                Codec.FLOAT.optionalFieldOf("chance", 1F).forGetter(LightningOutput::chance)
        ).apply(instance, (ignored, amount, chance) -> new LightningOutput(tier, amount, chance)));
        return new OutputType.Definition<>(tier.requirementId(), codec,
                (output, chance) -> new LightningOutput(tier, output.amount(), chance),
                (output, modifiers) -> {
                    LightningRequirement requirement = requirementType(tier).applyModifiers(
                            LightningRequirement.output(tier, output.amount(), output.chance()), modifiers);
                    return new LightningOutput(tier, requirement.amount(), requirement.chance());
                }, output -> new LightningOutput(tier, output.amount(), output.chance()),
                OutputType.Presentation.defaults(tier.requirementId()), tier.requirementId().toString(),
                (output, tags) -> new LightningRequirement(tier, RecipeModifier.IOType.OUTPUT, output.amount(), output.chance(), tags),
                requirement -> requirement instanceof LightningRequirement lightning && lightning.tier() == tier && !lightning.io().isInput(),
                requirement -> requirement instanceof LightningRequirement lightning && lightning.tier() == tier && !lightning.io().isInput()
                        ? new LightningOutput(tier, lightning.amount(), lightning.chance()) : null);
    }

    private static Codec<String> discriminator(LightningTier tier) {
        return Codec.STRING.validate(value -> value.equals(tier.requirementId().toString())
                ? DataResult.success(value) : DataResult.error(() -> "Wrong lightning type: " + value));
    }

    /** @author howxu <dev@howxu.cn> */
    private record Declaration(RecipeModifier.IOType io, long amount, float chance, List<String> tags) {}
}
