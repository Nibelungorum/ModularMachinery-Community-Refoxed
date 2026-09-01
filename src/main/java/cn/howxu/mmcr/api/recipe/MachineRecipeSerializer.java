package cn.howxu.mmcr.api.recipe;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;

/** Codec-backed serializer for canonical machine recipes.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineRecipeSerializer {

    public static final RecipeSerializer<MachineRecipe> INSTANCE = new RecipeSerializer<>(
            MachineRecipe.CODEC,
            StreamCodec.of(MachineRecipeSerializer::write, MachineRecipeSerializer::read)
    );

    private MachineRecipeSerializer() {
    }

    private static void write(RegistryFriendlyByteBuf buf, MachineRecipe recipe) {
        buf.writeJsonWithCodec(MachineRecipe.CODEC.codec(), recipe);
    }

    private static MachineRecipe read(RegistryFriendlyByteBuf buf) {
        return buf.readLenientJsonWithCodec(MachineRecipe.CODEC.codec());
    }
}
