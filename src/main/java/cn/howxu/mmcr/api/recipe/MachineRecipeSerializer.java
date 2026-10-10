package cn.howxu.mmcr.api.recipe;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.LenientJsonParser;
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
        JsonElement json = MachineRecipe.CODEC.codec()
                .encodeStart(buf.registryAccess().createSerializationContext(JsonOps.INSTANCE), recipe)
                .getOrThrow(error -> new EncoderException("Failed to encode: " + error + " " + recipe));
        buf.writeUtf(json.toString());
    }

    private static MachineRecipe read(RegistryFriendlyByteBuf buf) {
        JsonElement json = LenientJsonParser.parse(buf.readUtf());
        return MachineRecipe.CODEC.codec()
                .parse(buf.registryAccess().createSerializationContext(JsonOps.INSTANCE), json)
                .getOrThrow(error -> new DecoderException("Failed to decode JSON: " + error));
    }
}
