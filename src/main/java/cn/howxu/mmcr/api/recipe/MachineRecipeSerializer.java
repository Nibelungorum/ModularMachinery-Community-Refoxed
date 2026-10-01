package cn.howxu.mmcr.api.recipe;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
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
        var ops = buf.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var json = MachineRecipe.CODEC.codec().encodeStart(ops, recipe)
                .getOrThrow(message -> new EncoderException("Failed to encode machine recipe: " + message));
        buf.writeUtf(json.toString());
    }

    private static MachineRecipe read(RegistryFriendlyByteBuf buf) {
        var ops = buf.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        return MachineRecipe.CODEC.codec().parse(ops, JsonParser.parseString(buf.readUtf()))
                .getOrThrow(message -> new DecoderException("Failed to decode machine recipe: " + message));
    }
}
