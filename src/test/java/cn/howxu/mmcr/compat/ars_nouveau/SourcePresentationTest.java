package cn.howxu.mmcr.compat.ars_nouveau;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineOutputAmount;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.compat.ars_nouveau.client.SourceJeiAdapter;
import cn.howxu.mmcr.compat.ars_nouveau.client.SourceJeiIngredient;
import cn.howxu.mmcr.compat.ars_nouveau.client.SourceJadeElement;
import cn.howxu.mmcr.compat.ars_nouveau.client.SourcePortJadeComponentProvider;
import cn.howxu.mmcr.compat.jade.RecipeOutputCodec;
import cn.howxu.mmcr.compat.jade.RecipeOutputComponentProvider;
import cn.howxu.mmcr.compat.jei.JeiDisplayEntry;
import cn.howxu.mmcr.compat.jei.JeiIngredientAdapterRegistry;
import cn.howxu.mmcr.compat.jei.MachineRecipeDisplay;
import cn.howxu.mmcr.compat.jei.MachineRecipeLayout;
import cn.howxu.mmcr.compat.jei.RecipeIoEntry;
import cn.howxu.mmcr.test.TestBootstrap;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies source presentation without an Ars world or live client inventory.
 *
 * @author howxu <dev@howxu.cn>
 */
class SourcePresentationTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void jeiAdapterPreservesExactLongTotalsAsNonTransferableIngredientsForBothRoles() {
        SourceJeiAdapter adapter = new SourceJeiAdapter();
        assertThat(adapter.ingredientType()).isSameAs(SourceJeiIngredient.TYPE);
        assertThat(adapter.transferHandler()).isEmpty();
        for (RecipeIngredientRole role : List.of(RecipeIngredientRole.INPUT, RecipeIngredientRole.OUTPUT)) {
            SourceRequirement requirement = role == RecipeIngredientRole.INPUT
                    ? SourceRequirement.input(Long.MAX_VALUE) : SourceRequirement.output(Long.MAX_VALUE);
            JeiDisplayEntry display = adapter.display(new RecipeIoEntry(role, ArsSourceIds.SOURCE,
                    requirement, Long.MAX_VALUE, 1F)).orElseThrow();

            assertThat(display.role()).isEqualTo(role);
            assertThat(display.typeId()).isEqualTo(ArsSourceIds.SOURCE);
            assertThat(display.isTextOnly()).isFalse();
            assertThat(display.transferable()).isFalse();
            assertThat(display.count()).isEqualTo(Integer.MAX_VALUE);
            assertThat(display.ingredient()).isEqualTo(new SourceJeiIngredient(Long.MAX_VALUE, role == RecipeIngredientRole.INPUT));
            assertThat(((SourceJeiIngredient) display.ingredient()).tooltip()).isEqualTo(Component.translatable(role == RecipeIngredientRole.INPUT
                    ? "jei.mmcr.machine_recipe.source_input" : "jei.mmcr.machine_recipe.source_output",
                    "9,223,372,036,854,775,807"));
        }
    }

    @Test
    void machineRecipeDisplayUsesRealSourceTotalsThroughTheBuiltInNeutralAdapter() {
        try (var requirementScope = RequirementHandlerRegistry.openTestScope();
             var outputScope = OutputRegistry.openTestScope()) {
            ArsNouveauRecipeTypes.register();
            MachineRecipe recipe = new MachineRecipe(MMCR.id("source_presentation"), MMCR.id("test_cube"), 20,
                    List.of(SourceRequirement.input(10_000L)), List.of(new SourceOutput(3_000_000_001L)),
                    List.of(), 0, 1, false, false, false, Set.of());

            MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);
            List<JeiDisplayEntry> entries = display.entries();
            MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(display, 4);

            assertThat(JeiIngredientAdapterRegistry.get(ArsSourceIds.SOURCE).orElseThrow())
                    .isInstanceOf(SourceJeiAdapter.class);
            assertThat(entries).hasSize(2);
            assertThat(entries.get(0).role()).isEqualTo(RecipeIngredientRole.INPUT);
            assertThat(entries.get(0).ingredient()).isEqualTo(new SourceJeiIngredient(10_000L, true));
            assertThat(entries.get(1).role()).isEqualTo(RecipeIngredientRole.OUTPUT);
            assertThat(entries.get(1).ingredient()).isEqualTo(new SourceJeiIngredient(3_000_000_001L, false));
            assertThat(entries).allSatisfy(entry -> {
                assertThat(entry.isTextOnly()).isFalse();
                assertThat(entry.transferable()).isFalse();
            });
            assertThat(layout.sourceTextLines()).isEmpty();
            assertThat(layout.inputs().slots()).extracting(slot -> slot.entry().displayEntry()).containsExactly(entries.get(0));
            assertThat(layout.outputs().slots()).extracting(slot -> slot.entry().displayEntry()).containsExactly(entries.get(1));
            assertThat(layout.durationTextY()).isGreaterThan(layout.inputs().slots().getFirst().y() + 16);
        }
    }

    @Test
    void portJadeDisplaysOnlyTheServerTagForEitherDirectionIncludingEmptyStorage() {
        for (String io : List.of("input", "output")) {
            CompoundTag data = new CompoundTag();
            CompoundTag source = new CompoundTag();
            source.putInt("amount", 10_000);
            source.putInt("capacity", 50_000);
            source.putString("io", io);
            data.put("mmcr_source", source);
            List<Component> lines = new ArrayList<>();

            SourcePortJadeComponentProvider.INSTANCE.appendTooltip(tooltip(lines), serverOnlyAccessor(data), null);

            assertThat(lines).containsExactly(Component.translatable("jade.mmcr.source_port.amount", "10,000"));
            source.putInt("amount", 0);
            lines.clear();
            SourcePortJadeComponentProvider.INSTANCE.appendTooltip(tooltip(lines), serverOnlyAccessor(data), null);
            assertThat(lines).containsExactly(Component.translatable("jade.mmcr.source_port.amount", "0"));
        }
    }

    @Test
    void portJadeAddsNothingWithoutItsServerTag() {
        List<Component> lines = new ArrayList<>();

        SourcePortJadeComponentProvider.INSTANCE.appendTooltip(
                tooltip(lines), serverOnlyAccessor(new CompoundTag()), null);

        assertThat(lines).isEmpty();
    }

    @Test
    void controllerJadeRendersTheTransportedLongSourceTotalWithNativeIcon() {
        try (var outputScope = OutputRegistry.openTestScope()) {
            OutputRegistry.register(SourceOutput.TYPE);
            CompoundTag data = new CompoundTag();
            RecipeOutputCodec.write(data, List.of(new MachineOutputAmount(new SourceOutput(10_000L), Long.MAX_VALUE)));
            List<Component> lines = new ArrayList<>();
            List<Object> elements = new ArrayList<>();

            RecipeOutputComponentProvider.INSTANCE.appendTooltip(tooltip(lines, elements), serverOnlyAccessor(data), null);

            assertThat(elements).anyMatch(SourceJadeElement.class::isInstance);
            assertThat(lines).containsExactly(Component.translatable("jade.mmcr.machine_controller.recipe_output"),
                    Component.translatable("gui.mmcr.source.exact", "9,223,372,036,854,775,807"));
        }
    }

    private static BlockAccessor serverOnlyAccessor(CompoundTag data) {
        return (BlockAccessor) Proxy.newProxyInstance(SourcePresentationTest.class.getClassLoader(),
                new Class<?>[]{BlockAccessor.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getServerData")) return data;
                    throw new AssertionError("Presentation must only read server data: " + method.getName());
                });
    }

    private static ITooltip tooltip(List<Component> lines) {
        return tooltip(lines, new ArrayList<>());
    }

    private static ITooltip tooltip(List<Component> lines, List<Object> elements) {
        return (ITooltip) Proxy.newProxyInstance(SourcePresentationTest.class.getClassLoader(),
                new Class<?>[]{ITooltip.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("add") || method.getName().equals("append")) {
                        if (arguments[0] instanceof Component component) lines.add(component);
                        else elements.add(arguments[0]);
                    }
                    return null;
                });
    }
}
