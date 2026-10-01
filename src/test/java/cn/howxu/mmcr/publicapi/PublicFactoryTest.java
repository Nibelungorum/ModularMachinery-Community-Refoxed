package cn.howxu.mmcr.publicapi;

import cn.howxu.mmcr.publicapi.recipe.IoValues;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import cn.howxu.mmcr.publicapi.recipe.requirement.Requirements;
import cn.howxu.mmcr.publicapi.recipe.requirement.ItemRequirementSpec;
import cn.howxu.mmcr.publicapi.recipe.component.ComponentConditions;
import cn.howxu.mmcr.publicapi.recipe.component.ComponentConstraints;
import cn.howxu.mmcr.publicapi.recipe.component.TextMatchMode;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises Public factory composition without core declarations or adapters.
 * @author howxu <dev@howxu.cn>
 */
class PublicFactoryTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void public_component_and_requirement_factories_compose_without_caller_conversions() {
        var components = ComponentConstraints.ofIds(Map.of(ResourceLocation.parse("minecraft:repair_cost"),
                ComponentConditions.exact(new JsonPrimitive(3))));
        var input = IoValues.itemInput(Ingredient.of(Items.IRON_INGOT), 2, components, 0.25F);
        var stack = new ItemStack(Items.GOLD_INGOT, 2);
        var output = Requirements.itemOutput(IoValues.itemOutput(stack, 0.5F, components));
        var tagged = Requirements.item(IoDirection.INPUT, input.ingredient(), input.count(), ItemStack.EMPTY,
                1F, List.of("catalyst"), components, input.consumeChance());
        var definition = Recipes.recipe(ApiIds.id("public_factory_composition"))
                .recipePool(ApiIds.id("factory_pool")).requirement(tagged).requirement(output).build();
        stack.setCount(1);

        assertThat(definition.requirements()).satisfiesExactly(
                requirement -> {
                    assertThat(requirement).isInstanceOf(ItemRequirementSpec.class);
                    var published = (ItemRequirementSpec) requirement;
                    assertThat(published.io()).isEqualTo(tagged.io());
                    assertThat(published.ingredient()).isSameAs(tagged.ingredient());
                    assertThat(published.count()).isEqualTo(tagged.count());
                    assertThat(published.tags()).containsExactlyElementsOf(tagged.tags());
                    assertThat(published.consumeChance()).isEqualTo(tagged.consumeChance());
                    assertThat(published.components().values()).containsKey(DataComponents.REPAIR_COST);
                },
                requirement -> {
                    assertThat(requirement).isInstanceOf(ItemRequirementSpec.class);
                    var published = (ItemRequirementSpec) requirement;
                    assertThat(published.io()).isEqualTo(output.io());
                    assertThat(published.chance()).isEqualTo(output.chance());
                    assertThat(ItemStack.matches(published.resolvedStack(), output.resolvedStack())).isTrue();
                });
        assertThat(tagged.ingredient()).isSameAs(input.ingredient());
        assertThat(tagged.tags()).containsExactly("catalyst");
        assertThat(tagged.components().values()).containsKey(DataComponents.REPAIR_COST);
        assertThat(output.resolvedStack().get(DataComponents.REPAIR_COST)).isEqualTo(3);
        assertThat(output.stack().getCount()).isEqualTo(2);
        assertThat(Requirements.fluidInput(IoValues.fluidInput(Fluids.WATER, 100)).ingredient()).isNotNull();
    }

    @Test
    void public_text_modes_create_shared_matching_predicates() {
        var plain = ComponentConditions.text("name", TextMatchMode.PLAIN);
        var full = ComponentConditions.text("name", TextMatchMode.FULL);

        assertThat(plain).isNotEqualTo(full);
        assertThat(plain.isExact()).isFalse();
        assertThat(ComponentConstraints.ofIds(Map.of(ResourceLocation.parse("minecraft:custom_name"), plain))
                .hasNonExactValues()).isTrue();
    }
}
