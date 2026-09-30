package cn.howxu.mmcr.api.recipe;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;

/** Codec-backed serializer for canonical machine recipes.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineRecipeSerializer {

    public static final RecipeSerializer<MachineRecipe> INSTANCE = new RecipeSerializer<>() {
        @Override
        public MapCodec<MachineRecipe> codec() {
            return MachineRecipe.CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, MachineRecipe> streamCodec() {
            return StreamCodec.of(MachineRecipeSerializer::write, MachineRecipeSerializer::read);
        }
    };

    private MachineRecipeSerializer() {
    }

    private static void write(RegistryFriendlyByteBuf buf, MachineRecipe recipe) {
        buf.writeJsonWithCodec(MachineRecipe.CODEC.codec(), recipe);
    }

    private static MachineRecipe read(RegistryFriendlyByteBuf buf) {
        return buf.readJsonWithCodec(MachineRecipe.CODEC.codec());
    }
}
