package cn.howxu.mmcr;

import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.List;

/**
 * Exercises recipe amount codecs against the live game registry.
 *
 * @author howxu <dev@howxu.cn>
 */
public class RecipeAmountCodecGameTest {
    public void recipe_update_packet_preserves_registry_backed_components(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        MachineRecipe recipe = MachineRecipe.CODEC.codec().parse(ops, JsonParser.parseString("""
                {
                  "recipe_pool": "mmcr:packet_component_test",
                  "tick_time": 100,
                  "requirements": [
                    {"type":"minecraft:item", "io":"input", "item":{"item":"minecraft:iron_sword"},
                     "count":1, "components":{"minecraft:enchantments":{"levels":{"minecraft:sharpness":2}}}},
                    {"type":"minecraft:item", "io":"output", "stack":{"id":"minecraft:gold_ingot", "count":10,
                     "components":{"minecraft:custom_name":"{\\"text\\":\\"Named output\\"}",
                                   "minecraft:enchantments":{"levels":{"minecraft:sharpness":3}}}}},
                    {"type":"minecraft:item", "io":"output", "stack":{"id":"minecraft:potion", "count":1,
                     "components":{"minecraft:potion_contents":{"potion":"minecraft:healing"}}}}
                  ]
                }
                """)).getOrThrow();
        // This fixture exercises the missing-registry failure of the old plain-JsonOps serializer.
        helper.assertTrue(MachineRecipe.CODEC.codec().encodeStart(JsonOps.INSTANCE, recipe).error().isPresent(),
                "Registry-backed recipe components require serialization context");
        var recipeId = ResourceLocation.parse("mmcr:packet_component_test");
        var packet = new ClientboundUpdateRecipesPacket(List.of(new RecipeHolder<>(recipeId, recipe)));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            ClientboundUpdateRecipesPacket.STREAM_CODEC.encode(buffer, packet);
            var decodedPacket = ClientboundUpdateRecipesPacket.STREAM_CODEC.decode(buffer);
            helper.assertTrue(decodedPacket.getRecipes().size() == 1, "Recipe update retains the recipe entry");
            var holder = decodedPacket.getRecipes().getFirst();
            helper.assertTrue(holder.id().equals(recipeId), "Recipe update retains the holder ID");
            MachineRecipe decoded = (MachineRecipe) holder.value();
            for (int index = 0; index < recipe.machineOutputs().size(); index++) {
                ItemStack expected = ((MachineOutput.ItemOutput) recipe.machineOutputs().get(index)).stack();
                ItemStack actual = ((MachineOutput.ItemOutput) decoded.machineOutputs().get(index)).stack();
                helper.assertTrue(ItemStack.matches(expected, actual), "Recipe update preserves output components and count");
            }
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            sword.enchant(registries.registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SHARPNESS), 2);
            var input = (ItemRequirement) decoded.requirements().getFirst();
            helper.assertTrue(input.components().matches(sword, ops), "Recipe update preserves input enchantment matching");
            helper.assertTrue(!input.components().matches(new ItemStack(Items.IRON_SWORD), ops),
                    "Recipe update preserves rejection of unenchanted inputs");
            helper.assertTrue(!buffer.isReadable(), "Recipe update consumes its full payload");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    public void long_recipe_output_amounts_are_capped_at_native_stack_limits(GameTestHelper helper) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        MachineOutput item = MachineOutput.CODEC.parse(ops, output("item", "minecraft:iron_nugget", "count"))
                .getOrThrow();
        MachineOutput fluid = MachineOutput.CODEC.parse(ops, output("fluid", "minecraft:water", "amount"))
                .getOrThrow();

        helper.assertTrue(item instanceof MachineOutput.ItemOutput itemOutput
                        && itemOutput.stack().getCount() == Integer.MAX_VALUE,
                "Long item recipe output amounts are capped at the native item stack limit");
        helper.assertTrue(fluid instanceof MachineOutput.FluidOutput fluidOutput
                        && fluidOutput.stack().getAmount() == Integer.MAX_VALUE,
                "Long fluid recipe output amounts are capped at the native fluid stack limit");
        helper.succeed();
    }

    private static JsonObject output(String type, String id, String amountField) {
        JsonObject stack = new JsonObject();
        stack.addProperty("id", id);
        stack.addProperty(amountField, Long.MAX_VALUE);
        JsonObject output = new JsonObject();
        output.addProperty("type", type);
        output.add("stack", stack);
        return output;
    }
}
