package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies type-ID dispatch for extensible JEI ingredients.
 *
 * @author howxu <dev@howxu.cn>
 */
class JeiIngredientAdapterRegistryTest {

    @Test
    void registeredAdapterPreservesRoleCountAndTransferCapability() {
        Identifier typeId = MMCR.id("test_jei_adapter");
        JeiIngredientAdapter adapter = new JeiIngredientAdapter() {
            @Override
            public Identifier typeId() {
                return typeId;
            }

            @Override
            public IIngredientType<?> ingredientType() {
                return VanillaTypes.ITEM_STACK;
            }

            @Override
            public Optional<JeiDisplayEntry> display(RecipeIoEntry entry) {
                return Optional.of(new JeiDisplayEntry(entry.role(), ingredientType(),
                        new ItemStack(Items.IRON_INGOT), Math.toIntExact(entry.amount()),
                        entry.role() == RecipeIngredientRole.INPUT));
            }

            @Override
            public Optional<IRecipeTransferHandler<?, ?>> transferHandler() {
                return Optional.empty();
            }
        };
        JeiIngredientAdapterRegistry.register(adapter);

        JeiDisplayEntry input = JeiIngredientAdapterRegistry.get(typeId).orElseThrow()
                .display(new RecipeIoEntry(RecipeIngredientRole.INPUT, typeId, "input", 8, 1F)).orElseThrow();
        JeiDisplayEntry output = JeiIngredientAdapterRegistry.get(typeId).orElseThrow()
                .display(new RecipeIoEntry(RecipeIngredientRole.OUTPUT, typeId, "output", 3, 0.25F)).orElseThrow();

        assertThat(input.role()).isEqualTo(RecipeIngredientRole.INPUT);
        assertThat(input.count()).isEqualTo(8);
        assertThat(input.transferable()).isTrue();
        assertThat(input.ingredientType()).isEqualTo(VanillaTypes.ITEM_STACK);
        assertThat(output.role()).isEqualTo(RecipeIngredientRole.OUTPUT);
        assertThat(output.count()).isEqualTo(3);
        assertThat(output.transferable()).isFalse();
    }

    @Test
    void unknownTypeUsesBoundedTextEntry() {
        JeiDisplayEntry entry = JeiIngredientAdapterRegistry.textEntry(new RecipeIoEntry(
                RecipeIngredientRole.INPUT, MMCR.id("unknown_jei_type"), "unknown", Long.MAX_VALUE, 1F));

        assertThat(entry.isTextOnly()).isTrue();
        assertThat(entry.count()).isEqualTo(Integer.MAX_VALUE);
        assertThat(entry.transferable()).isFalse();
    }
}
