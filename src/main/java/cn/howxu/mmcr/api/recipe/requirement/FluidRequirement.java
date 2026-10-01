package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;

import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.RecipeSyncCodec;
import cn.howxu.mmcr.api.recipe.FluidInput;
import cn.howxu.mmcr.api.recipe.FluidOutput;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * @author howxu <dev@howxu.cn>
 */
public record FluidRequirement(RecipeModifier.IOType io, @Nullable FluidIngredient fluid, int amount, FluidStack stack, float chance, List<String> tags, float consumeChance) implements MachineRequirement {
    private static final ResourceLocation TYPE_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "fluid");
    public static final MapCodec<FluidRequirement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(value -> TYPE_ID.toString()),
            RecipeModifier.IO_TYPE_CODEC.optionalFieldOf("io", RecipeModifier.IOType.INPUT)
                    .forGetter(FluidRequirement::io),
            FluidIngredient.CODEC.optionalFieldOf("fluid").forGetter(value -> Optional.ofNullable(value.fluid())),
            Codec.LONG.optionalFieldOf("amount", 0L).xmap(MachineOutput::optionalRecipeStackAmount,
                    Integer::longValue).forGetter(FluidRequirement::amount),
            MachineOutput.RECIPE_FLUID_STACK_CODEC.optionalFieldOf("stack", FluidStack.EMPTY).forGetter(FluidRequirement::stack),
            Codec.FLOAT.optionalFieldOf("chance", 1F).forGetter(FluidRequirement::chance),
            Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(FluidRequirement::tags),
            Codec.FLOAT.optionalFieldOf("consume_chance", 1F).forGetter(FluidRequirement::consumeChance)
    ).apply(instance, (ignored, io, fluid, amount, stack, chance, tags, consumeChance) ->
            new FluidRequirement(io, fluid.orElse(null), amount, stack, chance, tags, consumeChance)));
    private static final RequirementHandler<FluidRequirement> HANDLER = new FluidRequirementHandler();
    public static final RequirementType<FluidRequirement> TYPE =
            new RequirementType.Definition<>(TYPE_ID, CODEC, HANDLER, FluidRequirement::copy,
                    RecipeSyncCodec.json(CODEC.codec(), FluidRequirement::validateSync));

    public FluidRequirement(RecipeModifier.IOType io, @Nullable FluidIngredient fluid, int amount, FluidStack stack) {
        this(io, fluid, amount, stack, 1F, List.of(), 1F);
    }

    public FluidRequirement(RecipeModifier.IOType io, @Nullable FluidIngredient fluid, int amount, FluidStack stack, List<String> tags) {
        this(io, fluid, amount, stack, 1F, tags, 1F);
    }

    public FluidRequirement(RecipeModifier.IOType io, @Nullable FluidIngredient fluid, int amount, FluidStack stack, float chance, List<String> tags) {
        this(io, fluid, amount, stack, chance, tags, 1F);
    }

    public FluidRequirement {
        stack = stack == null ? FluidStack.EMPTY : stack.copy();
        chance = MachineOutput.clampChance(chance);
        consumeChance = MachineOutput.clampChance(consumeChance);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public FluidRequirement(RecipeModifier.IOType io, FluidIngredient fluid, int amount, FluidStack stack,
                            float chance, float consumeChance) {
        this(io, fluid, amount, stack, chance, List.of(), consumeChance);
        if (io == null) throw new IllegalArgumentException("io null");
        if (io.isInput() && (fluid == null || amount < 1)) throw new IllegalArgumentException("Invalid fluid input");
        if (!io.isInput() && this.stack.isEmpty()) throw new IllegalArgumentException("Fluid output must not be empty");
        if (!Float.isFinite(chance) || chance < 0F || chance > 1F
                || !Float.isFinite(consumeChance) || consumeChance < 0F || consumeChance > 1F) {
            throw new IllegalArgumentException("chance must be in [0, 1]");
        }
    }

    public static FluidRequirement input(FluidInput input) {
        return new FluidRequirement(RecipeModifier.IOType.INPUT, input.ingredient(), input.amount(), FluidStack.EMPTY,
                1F, input.consumeChance());
    }

    public static FluidRequirement output(FluidOutput output) {
        return new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0, output.stack(), output.chance(), 1F);
    }

    @Override
    public FluidStack stack() {
        return stack.copy();
    }

    /** Script-compatible name for the input ingredient. */
    public FluidIngredient ingredient() {
        return fluid;
    }

    private static FluidRequirement copy(FluidRequirement requirement) {
        return new FluidRequirement(requirement.io(), requirement.fluid(), requirement.amount(), requirement.stack(),
                requirement.chance(), requirement.tags(), requirement.consumeChance());
    }

    static FluidRequirement copyForTest(FluidRequirement requirement) {
        return copy(requirement);
    }

    private static void validateSync(FluidRequirement requirement) {
        int amount = requirement.io() == RecipeModifier.IOType.INPUT ? requirement.amount() : requirement.stack().getAmount();
        if (amount < 1) {
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
