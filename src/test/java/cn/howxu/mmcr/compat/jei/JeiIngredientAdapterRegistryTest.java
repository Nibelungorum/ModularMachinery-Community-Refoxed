package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies type-ID dispatch for extensible JEI ingredients.
 *
 * @author howxu <dev@howxu.cn>
 */
class JeiIngredientAdapterRegistryTest {

    private static final IIngredientType<String> CUSTOM_TYPE = () -> String.class;
    @SuppressWarnings("unchecked")
    private static final IIngredientRenderer<String> CUSTOM_RENDERER = (IIngredientRenderer<String>) Proxy.newProxyInstance(
            JeiIngredientAdapterRegistryTest.class.getClassLoader(), new Class<?>[]{IIngredientRenderer.class},
            (proxy, method, arguments) -> null);

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
                return CUSTOM_TYPE;
            }

            @Override
            public Optional<JeiDisplayEntry> display(RecipeIoEntry entry) {
                return Optional.of(new JeiDisplayEntry(entry.role(), typeId(), ingredientType(),
                        "custom-ingredient", Math.toIntExact(entry.amount()), entry.chance(), null,
                        entry.role() == RecipeIngredientRole.INPUT));
            }

            @Override
            public Optional<IIngredientRenderer<?>> renderer(RecipeIoEntry entry) {
                return Optional.of(CUSTOM_RENDERER);
            }

            @Override
            public Optional<IRecipeTransferHandler<?, ?>> transferHandler() {
                return Optional.empty();
            }
        };
        JeiIngredientAdapterRegistry.register(adapter);

        JeiDisplayEntry input = JeiIngredientAdapterRegistry.display(
                new RecipeIoEntry(RecipeIngredientRole.INPUT, typeId, "input", 8, 1F)).orElseThrow();
        JeiDisplayEntry output = JeiIngredientAdapterRegistry.display(
                new RecipeIoEntry(RecipeIngredientRole.OUTPUT, typeId, "output", 3, 0.25F)).orElseThrow();

        assertThat(input.role()).isEqualTo(RecipeIngredientRole.INPUT);
        assertThat(input.count()).isEqualTo(8);
        assertThat(input.transferable()).isTrue();
        assertThat(input.ingredientType()).isEqualTo(CUSTOM_TYPE);
        assertThat(input.typeId()).isEqualTo(typeId);
        assertThat(output.role()).isEqualTo(RecipeIngredientRole.OUTPUT);
        assertThat(output.count()).isEqualTo(3);
        assertThat(output.transferable()).isFalse();

        MachineRecipeLayout.RegionPlan layout = MachineRecipeLayout.regionForEntries(List.of(input),
                RecipeIngredientRole.INPUT, 4);
        assertThat(layout.slots()).singleElement().satisfies(slot -> {
            assertThat(slot.entry().kind()).isEqualTo(MachineRecipeLayout.Kind.GENERIC);
            assertThat(slot.entry().displayEntry()).isSameAs(input);
        });

        AtomicReference<Object[]> added = new AtomicReference<>();
        AtomicReference<Object[]> renderer = new AtomicReference<>();
        IRecipeSlotBuilder slot = (IRecipeSlotBuilder) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{IRecipeSlotBuilder.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("add")) added.set(arguments);
                    if (method.getName().equals("setCustomRenderer")) renderer.set(arguments);
                    return method.getReturnType().isAssignableFrom(IRecipeSlotBuilder.class) ? proxy : null;
                });
        MachineRecipeCategory.addGeneric(slot, input);
        assertThat(added.get()).containsExactly(CUSTOM_TYPE, "custom-ingredient");
        assertThat(renderer.get()).containsExactly(CUSTOM_TYPE, CUSTOM_RENDERER);
    }

    @Test
    void unknownTypeUsesBoundedTextEntry() {
        JeiDisplayEntry entry = JeiIngredientAdapterRegistry.textEntry(new RecipeIoEntry(
                RecipeIngredientRole.INPUT, MMCR.id("unknown_jei_type"), "unknown", Long.MAX_VALUE, 1F));

        assertThat(entry.isTextOnly()).isTrue();
        assertThat(entry.count()).isEqualTo(Integer.MAX_VALUE);
        assertThat(entry.transferable()).isFalse();
        assertThat(MachineRecipeLayout.regionForEntries(List.of(entry), RecipeIngredientRole.INPUT, 4).slots())
                .singleElement().extracting(slot -> slot.entry().kind()).isEqualTo(MachineRecipeLayout.Kind.TEXT);
    }
}
