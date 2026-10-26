package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.preview.StructureMaterialSummary;
import cn.howxu.mmcr.client.preview.StructurePreviewSchema;
import cn.howxu.mmcr.test.TestBootstrap;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.library.ingredients.DisplayIngredientAcceptor;
import net.minecraft.core.BlockPos;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies structure material page timing.
 *
 * @author howxu <dev@howxu.cn>
 */
class StructureMaterialWidgetTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void pagedMaterialsPassJeiValidationAndClearUnusedSlots() {
        var blocks = List.of(Blocks.STONE, Blocks.COBBLESTONE, Blocks.DIRT, Blocks.SAND, Blocks.GRAVEL,
                Blocks.GOLD_BLOCK, Blocks.IRON_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.EMERALD_BLOCK, Blocks.REDSTONE_BLOCK);
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        for (int index = 0; index < blocks.size(); index++) {
            states.put(new BlockPos(index, 0, 0), blocks.get(index).defaultBlockState());
        }
        var summary = StructureMaterialSummary.from(new StructurePreviewSchema(
                MMCR.id("jei_material_validation"), states, Map.of()));
        var ingredientManager = JeiTestIngredients.manager();
        List<DisplayIngredientAcceptor> overrides = new ArrayList<>();
        List<IRecipeSlotDrawable> slots = new ArrayList<>();
        for (int index = 0; index < StructureMaterialWidget.SLOT_COUNT; index++) {
            int slotIndex = index;
            overrides.add(null);
            slots.add((IRecipeSlotDrawable) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{IRecipeSlotDrawable.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "clearDisplayOverrides" -> overrides.set(slotIndex, new DisplayIngredientAcceptor(
                                    ingredientManager, ContextMap.EMPTY, RecipeIngredientRole.RENDER_ONLY));
                            case "createDisplayOverrides" -> { return overrides.get(slotIndex); }
                            default -> throw new UnsupportedOperationException(method.getName());
                        }
                        return null;
                    }));
        }
        AtomicLong time = new AtomicLong();
        var widget = new StructureMaterialWidget(summary, slots, 0, 0, time::get);
        assertThat(overrides).allSatisfy(acceptor ->
                assertThat(acceptor.getAllIngredients()).singleElement().isNotNull());

        time.set(StructureMaterialWidget.PAGE_DURATION_MILLIS);
        widget.tick();
        assertThat(overrides.getFirst().getAllIngredients()).singleElement().satisfies(ingredient ->
                assertThat(ingredient.getItemStack().orElseThrow().is(blocks.getLast().asItem())).isTrue());
        assertThat(overrides.subList(1, overrides.size())).allSatisfy(acceptor ->
                assertThat(acceptor.getAllIngredients()).singleElement().isNull());

        StructureMaterialWidget.refreshPage(summary, slots, 0L);
        assertThat(overrides.getFirst().getAllIngredients()).singleElement().satisfies(ingredient ->
                assertThat(ingredient.getItemStack().orElseThrow().is(blocks.getFirst().asItem())).isTrue());
        assertThat(overrides).allSatisfy(acceptor ->
                assertThat(acceptor.getAllIngredients()).singleElement().isNotNull());
    }

    @Test
    void changesPageEveryEightSeconds() {
        assertThat(StructureMaterialWidget.pageFor(0L, 10)).isZero();
        assertThat(StructureMaterialWidget.pageFor(7_999L, 10)).isZero();
        assertThat(StructureMaterialWidget.pageFor(8_000L, 10)).isOne();
        assertThat(StructureMaterialWidget.pageFor(16_000L, 10)).isZero();
    }

    @Test
    void onePageDoesNotRotate() {
        assertThat(StructureMaterialWidget.pageFor(0L, 9)).isZero();
        assertThat(StructureMaterialWidget.pageFor(80_000L, 9)).isZero();
    }
}
