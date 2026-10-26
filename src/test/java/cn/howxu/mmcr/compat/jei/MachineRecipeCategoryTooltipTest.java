package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.test.TestBootstrap;
import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps fluid names available through both JEI tooltip entry points.
 *
 * @author howxu <dev@howxu.cn>
 */
class MachineRecipeCategoryTooltipTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        var holder = DeferredHolder.class.getDeclaredField("holder");
        holder.setAccessible(true);
        holder.set(NeoForgeMod.WATER_TYPE, Holder.direct(
                new FluidType(FluidType.Properties.create().descriptionId("block.minecraft.water"))));
    }

    @Test
    @SuppressWarnings({"unchecked", "removal"})
    void legacyAndContextualFluidTooltipsPreserveTheFluidName() throws Exception {
        var field = MachineRecipeCategory.class.getDeclaredField("FULL_FLUID_RENDERER");
        field.setAccessible(true);
        var renderer = (IIngredientRenderer<FluidStack>) field.get(null);
        FluidStack fluid = new FluidStack(Fluids.WATER, 1_000);

        assertThat(renderer.getTooltip(fluid, TooltipFlag.NORMAL)).containsExactly(fluid.getHoverName());
        assertThat(renderer.getTooltip(fluid, Item.TooltipContext.EMPTY, null, TooltipFlag.NORMAL))
                .containsExactly(fluid.getHoverName());
    }
}
