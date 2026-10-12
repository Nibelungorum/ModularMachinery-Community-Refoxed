package cn.howxu.mmcr.compat.emi;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.jei.JeiWorkstationRegistration;
import cn.howxu.mmcr.test.TestBootstrap;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** @author howxu <dev@howxu.cn> */
class EmiWorkstationsTest {
    @BeforeAll static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test void associationsDeduplicateComponentsPerPoolAndIgnoreUnknownTargets() {
        var first = MMCR.id("pool_one");
        var second = MMCR.id("pool_two");
        var machine = MMCR.id("machine");
        var categoryA = category("mmcr:recipe_pool/pool_one");
        var categoryB = category("mmcr:recipe_pool/pool_two");
        var external = category("addon:category");
        ItemStack named = new ItemStack(Items.FURNACE, 5);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("different"));
        List<JeiWorkstationRegistration> declarations = List.of(
                new JeiWorkstationRegistration.RecipePoolItem(first, ResourceLocation.parse("minecraft:furnace")),
                new JeiWorkstationRegistration.RecipePoolStack(first, new ItemStack(Items.FURNACE, 8)),
                new JeiWorkstationRegistration.RecipePoolStack(first, named),
                new JeiWorkstationRegistration.RecipePoolStack(second, named),
                new JeiWorkstationRegistration.RecipePoolStack(MMCR.id("missing_pool"), named),
                new JeiWorkstationRegistration.RecipePoolItem(first, MMCR.id("missing_item")),
                new JeiWorkstationRegistration.Machine(machine, external.getId()),
                new JeiWorkstationRegistration.Machine(MMCR.id("missing_machine"), external.getId()),
                new JeiWorkstationRegistration.Machine(machine, MMCR.id("missing_category")));
        var result = EmiWorkstations.collect(Map.of(first, categoryA, second, categoryB),
                Map.of(first, List.of(machine)), Map.of(machine, new ItemStack(Items.FURNACE)), declarations, List.of(external));
        named.remove(DataComponents.CUSTOM_NAME);

        assertThat(result).containsOnlyKeys(categoryA, categoryB, external);
        assertThat(result.get(categoryA)).hasSize(2).allSatisfy(stack -> assertThat(stack.getCount()).isEqualTo(1));
        assertThat(result.get(categoryA)).anySatisfy(stack -> assertThat(stack.get(DataComponents.CUSTOM_NAME))
                .isEqualTo(Component.literal("different")));
        assertThat(result.get(categoryB)).hasSize(1);
        assertThat(result.get(external)).singleElement().satisfies(stack -> assertThat(stack.is(Items.FURNACE)).isTrue());
        result.get(categoryA).getFirst().setCount(64);
        assertThat(declarations.stream().filter(JeiWorkstationRegistration.RecipePoolStack.class::isInstance)
                .map(JeiWorkstationRegistration.RecipePoolStack.class::cast).findFirst().orElseThrow().workstation().getCount()).isEqualTo(1);
    }

    @Test void vanillaMappingReadsCurrentCycleAndOtherCategoriesUseExactIds() {
        var old = VanillaEmiRecipeCategories.ANVIL_REPAIRING;
        try {
            var first = category("emi:anvil_repairing");
            var next = category("emi:anvil_repairing");
            VanillaEmiRecipeCategories.ANVIL_REPAIRING = first;
            assertThat(EmiWorkstations.resolveCategory(ResourceLocation.parse("minecraft:anvil"), List.of())).isSameAs(first);
            VanillaEmiRecipeCategories.ANVIL_REPAIRING = next;
            assertThat(EmiWorkstations.resolveCategory(ResourceLocation.parse("minecraft:anvil"), List.of())).isSameAs(next);
            var custom = category("addon:category");
            assertThat(EmiWorkstations.resolveCategory(custom.getId(), List.of(custom))).isSameAs(custom);
            assertThat(EmiWorkstations.resolveCategory(ResourceLocation.parse("other:category"), List.of(custom))).isNull();
        } finally { VanillaEmiRecipeCategories.ANVIL_REPAIRING = old; }
    }

    private static EmiRecipeCategory category(String id) { return new EmiRecipeCategory(ResourceLocation.parse(id), EmiStack.EMPTY); }
}
