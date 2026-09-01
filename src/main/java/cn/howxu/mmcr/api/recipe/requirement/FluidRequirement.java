package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.RecipeSyncCodec;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * @author howxu <dev@howxu.cn>
 */
public record FluidRequirement(RecipeModifier.IOType io, @Nullable FluidIngredient fluid, int amount, FluidStack stack, float chance, List<String> tags) implements MachineRequirement {
    private static final Identifier TYPE_ID = MMCR.id("fluid");
    public static final MapCodec<FluidRequirement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(value -> TYPE_ID.toString()),
            RecipeModifier.IO_TYPE_CODEC.optionalFieldOf("io", RecipeModifier.IOType.INPUT)
                    .forGetter(FluidRequirement::io),
            FluidIngredient.CODEC.optionalFieldOf("fluid").forGetter(value -> Optional.ofNullable(value.fluid())),
            Codec.INT.optionalFieldOf("amount", 0).forGetter(FluidRequirement::amount),
            FluidStack.CODEC.optionalFieldOf("stack", FluidStack.EMPTY).forGetter(FluidRequirement::stack),
            Codec.FLOAT.optionalFieldOf("chance", 1F).forGetter(FluidRequirement::chance),
            Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(FluidRequirement::tags)
    ).apply(instance, (ignored, io, fluid, amount, stack, chance, tags) ->
            new FluidRequirement(io, fluid.orElse(null), amount, stack, chance, tags)));
    private static final RequirementHandler<FluidRequirement> HANDLER = new FluidRequirementHandler();
    public static final RequirementType<FluidRequirement> TYPE =
            new RequirementType.Definition<>(TYPE_ID, CODEC, HANDLER, FluidRequirement::copy,
                    RecipeSyncCodec.json(CODEC.codec(), FluidRequirement::validateSync));

    public FluidRequirement(RecipeModifier.IOType io, @Nullable FluidIngredient fluid, int amount, FluidStack stack) {
        this(io, fluid, amount, stack, 1F, List.of());
    }

    public FluidRequirement(RecipeModifier.IOType io, @Nullable FluidIngredient fluid, int amount, FluidStack stack, List<String> tags) {
        this(io, fluid, amount, stack, 1F, tags);
    }

    public FluidRequirement {
        stack = stack == null ? FluidStack.EMPTY : stack.copy();
        chance = MachineOutput.clampChance(chance);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    private static FluidRequirement copy(FluidRequirement requirement) {
        return new FluidRequirement(requirement.io(), requirement.fluid(), requirement.amount(), requirement.stack(),
                requirement.chance(), requirement.tags());
    }

    private static void validateSync(FluidRequirement requirement) {
        int amount = requirement.io() == RecipeModifier.IOType.INPUT ? requirement.amount() : requirement.stack().getAmount();
        if (amount < 1 || amount > 10_000_000) {
            throw new IllegalArgumentException("Invalid fluid amount: " + amount);
        }
        if (requirement.tags().size() > 1024) {
            throw new IllegalArgumentException("Invalid tag count: " + requirement.tags().size());
        }
    }

    @Override
    public RequirementType<FluidRequirement> type() {
        return TYPE;
    }

}
