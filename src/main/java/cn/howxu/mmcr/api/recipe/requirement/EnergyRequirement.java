package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.publicapi.recipe.RecipeIo;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.RecipeSyncCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * @author howxu <dev@howxu.cn>
 */
public record EnergyRequirement(RecipeModifier.IOType io, int fePerTick, List<String> tags) implements MachineRequirement {
    private static final Identifier TYPE_ID = Identifier.fromNamespaceAndPath("neoforge", "energy");
    public static final MapCodec<EnergyRequirement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(value -> TYPE_ID.toString()),
            RecipeModifier.IO_TYPE_CODEC.optionalFieldOf("io", RecipeModifier.IOType.INPUT)
                    .forGetter(EnergyRequirement::io),
            Codec.INT.fieldOf("fe_per_tick").forGetter(EnergyRequirement::fePerTick),
            Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(EnergyRequirement::tags)
    ).apply(instance, (ignored, io, fePerTick, tags) -> new EnergyRequirement(io, fePerTick, tags)));
    private static final RequirementHandler<EnergyRequirement> HANDLER = new EnergyRequirementHandler();
    public static final RequirementType<EnergyRequirement> TYPE =
            new RequirementType.Definition<>(TYPE_ID, CODEC, HANDLER, EnergyRequirement::copy,
                    RecipeSyncCodec.json(CODEC.codec(), EnergyRequirement::validateSync));

    public EnergyRequirement(int fePerTick) {
        this(RecipeModifier.IOType.INPUT, fePerTick, List.of());
    }

    public EnergyRequirement(int fePerTick, List<String> tags) {
        this(RecipeModifier.IOType.INPUT, fePerTick, tags);
    }

    public EnergyRequirement(RecipeModifier.IOType io, int fePerTick) {
        this(io, fePerTick, List.of());
    }

    public EnergyRequirement(RecipeIo io, int fePerTick) {
        this(io == RecipeIo.OUTPUT ? RecipeModifier.IOType.OUTPUT : RecipeModifier.IOType.INPUT, fePerTick);
    }

    public EnergyRequirement {
        if (io == null) io = RecipeModifier.IOType.INPUT;
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    private static EnergyRequirement copy(EnergyRequirement requirement) {
        return new EnergyRequirement(requirement.io(), requirement.fePerTick(), requirement.tags());
    }

    private static void validateSync(EnergyRequirement requirement) {
        if (requirement.fePerTick() < 1 || requirement.fePerTick() > 10_000_000) {
            throw new IllegalArgumentException("Invalid energy rate: " + requirement.fePerTick());
        }
        if (requirement.tags().size() > 1024) {
            throw new IllegalArgumentException("Invalid tag count: " + requirement.tags().size());
        }
    }

    @Override
    public RequirementType<EnergyRequirement> type() {
        return TYPE;
    }

}
