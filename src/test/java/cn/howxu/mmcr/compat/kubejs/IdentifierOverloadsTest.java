package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalViewFacet;
import cn.howxu.mmcr.api.controller.ControllerScreenText;
import cn.howxu.mmcr.api.controller.ControllerRuntimeContext;
import cn.howxu.mmcr.api.controller.JadeText;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.definition.MachineBehaviorContext;
import cn.howxu.mmcr.api.machine.definition.MachineIoView;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextState;
import cn.howxu.mmcr.test.TestBootstrap;
import dev.latvian.mods.kubejs.util.ID;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.ContextFactory;
import dev.latvian.mods.rhino.ScriptableObject;
import dev.latvian.mods.rhino.Wrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies explicit script overloads and their observable declaration effects.
 * @author howxu <dev@howxu.cn>
 */
class IdentifierOverloadsTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void kubejs_id_converter_does_not_intercept_state_property_expressions() {
        var context = kubeJsContext();
        var scope = context.initStandardObjects();
        ScriptableObject.putProperty(scope, "api", new KubeJSApi(), context);
        var result = (Wrapper) context.evaluateString(scope,
                "api.state('minecraft:oak_log[axis=x]')", "state-properties", 1, null);
        var state = ((BlockPredicate.OfBlockState) result.unwrap()).state();
        assertThat(state.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.X).isTrue();
        var typed = (Wrapper) context.evaluateString(scope,
                "api.state(api.id('minecraft:stone'))", "typed-state", 1, null);
        assertThat(((BlockPredicate.OfBlockState) typed.unwrap()).state().is(Blocks.STONE)).isTrue();
    }

    @Test
    void rhino_state_preserves_multiple_properties_when_id_conversion_is_registered() {
        var context = kubeJsContext();
        var scope = context.initStandardObjects();
        ScriptableObject.putProperty(scope, "api", new KubeJSApi(), context);
        var result = (Wrapper) context.evaluateString(scope,
                "api.state('minecraft:iron_bars[east=true,north=false,south=true,waterlogged=false,west=true]')",
                "state-multiple-properties", 1, null);
        var predicate = (BlockPredicate.OfBlockState) result.unwrap();
        var expected = Blocks.IRON_BARS.defaultBlockState()
                .setValue(IronBarsBlock.EAST, true)
                .setValue(IronBarsBlock.NORTH, false)
                .setValue(IronBarsBlock.SOUTH, true)
                .setValue(IronBarsBlock.WEST, true)
                .setValue(IronBarsBlock.WATERLOGGED, false);
        assertThat(predicate.state()).isEqualTo(expected);
        assertThat(predicate.matches(expected)).isTrue();
        assertThat(predicate.matches(expected.setValue(IronBarsBlock.EAST, false))).isFalse();
        assertThat(((BlockPredicate.OfBlockState) new KubeJSApi().state(ResourceLocation.parse("minecraft:stone")))
                .state()).isSameAs(Blocks.STONE.defaultBlockState());
    }

    @Test
    void kubejs_id_converter_preserves_namespaced_screen_text_validation() {
        var context = kubeJsContext();
        var scope = context.initStandardObjects();
        var state = new ControllerScreenTextState();
        ScriptableObject.putProperty(scope, "event", new ControllerScreenTextEventJS(
                new ControllerRuntimeContext(ResourceLocation.parse("test:machine"), BlockPos.ZERO, state)), context);
        ScriptableObject.putProperty(scope, "text", Component.literal("text"), context);
        ScriptableObject.putProperty(scope, "api", new KubeJSApi(), context);
        for (String expression : List.of(
                "event.append('controller', 'line', text)",
                "event.appendAfter('controller', 'test:line', 'line', text)",
                "event.replace('line', text)",
                "event.appendTranslatable('controller', 'line', 'test.key')",
                "event.appendAfterTranslatable('controller', 'test:line', 'line', 'test.key')",
                "event.replaceTranslatable('line', 'test.key')",
                "event.remove('controller', 'line')")) {
            assertThatThrownBy(() -> context.evaluateString(scope, expression, "screen-text-validation", 1, null))
                    .hasMessageContaining("namespace");
        }
        context.evaluateString(scope, "event.append('controller', api.id('test:line'), text)", "typed-screen-text", 1, null);
        assertThat(state.snapshot().lines()).singleElement().satisfies(line ->
                assertThat(line.lineId()).isEqualTo(ResourceLocation.parse("test:line")));
        context.evaluateString(scope, "event.remove('controller', api.id('test:line'))", "typed-screen-text", 1, null);
        assertThat(state.snapshot().lines()).isEmpty();
    }

    @Test
    void kubejs_id_converter_cannot_parse_before_missing_jei_short_circuit() {
        var context = kubeJsContext();
        var scope = context.initStandardObjects();
        var warnings = new ArrayList<String>();
        var event = new RecipeInformationEventJS(false, warnings::add);
        ScriptableObject.putProperty(scope, "event", event, context);
        context.evaluateString(scope, """
                event.addRecipePoolInfo('not even parsed', ' ')
                event.addRecipeInfo(null, null)
                """, "missing-jei", 1, null);
        assertThat(event.entries()).isEmpty();
        assertThat(warnings).singleElement().asString().contains("JEI is not loaded");
        var available = new RecipeInformationEventJS(true);
        ScriptableObject.putProperty(scope, "event", available, context);
        ScriptableObject.putProperty(scope, "api", new KubeJSApi(), context);
        context.evaluateString(scope, "event.addRecipeInfo(api.id('test:recipe'), 'test.key')", "typed-recipe-info", 1, null);
        assertThat(available.entries()).singleElement().satisfies(entry ->
                assertThat(entry.targetId()).isEqualTo(ResourceLocation.parse("test:recipe")));
    }

    private static Context kubeJsContext() {
        var factory = new ContextFactory();
        // Match BuiltinKubeJSPlugin's unconditional ResourceLocation converter, including its overload priority.
        factory.getTypeWrappers().registerDirect(ResourceLocation.class, ID::mc);
        return factory.enter();
    }

    @Test
    void exposed_identifier_methods_have_same_name_string_overloads() {
        for (Class<?> type : List.of(KubeJSApi.class, MMCRStartupEventJS.class, MMCRServerEventJS.class,
                RecipeInformationEventJS.class, ControllerScreenTextEventJS.class, MachineBuilderJS.class,
                MachineRecipeBuilderJS.class, MachineLevelBuilderJS.class, MachineStructureBuilderJS.class,
                MachineStructureStageBuilderJS.class, KubeJSInterfaceHelpers.class, ControllerScreenText.class,
                JadeText.class, MachineBehaviorContext.class, MachineIoView.class, MachineRecipe.class,
                ChemicalViewFacet.class, FailureOccurrence.class)) {
            for (var method : type.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) continue;
                if ((type == MachineRecipe.class || type == FailureOccurrence.class)
                        && Modifier.isStatic(method.getModifiers())) continue;
                Class<?>[] arguments = method.getParameterTypes();
                if (Arrays.stream(arguments).noneMatch(t -> t == ResourceLocation.class || t == ResourceLocation[].class)) continue;
                Class<?>[] strings = Arrays.stream(arguments)
                        .map(t -> t == ResourceLocation.class ? String.class : t == ResourceLocation[].class ? String[].class : t)
                        .toArray(Class<?>[]::new);
                assertThatCode(() -> type.getMethod(method.getName(), strings))
                        .as("%s.%s exposes explicit String IDs", type.getSimpleName(), method.getName())
                        .doesNotThrowAnyException();
            }
        }
    }

    @Test
    void string_failure_frames_preserve_nullable_recipe_ids_and_the_original_trace() {
        var original = FailureOccurrence.at(null, ResourceLocation.parse("test:source"), FailurePhase.RECIPE_START,
                ResourceLocation.parse("test:recipe"), 0, Map.of("detail", "kept"));
        var appended = original.append("test:outer", FailurePhase.RUNTIME, "test:outer_recipe", 2)
                .append("test:controller", FailurePhase.RUNTIME, (String) null, null);
        assertThat(original.trace().frames()).hasSize(1);
        assertThat(appended.trace().frames()).extracting(frame -> frame.source().toString())
                .containsExactly("test:source", "test:outer", "test:controller");
        assertThat(appended.trace().frames().get(1).recipeId()).isEqualTo(ResourceLocation.parse("test:outer_recipe"));
        assertThat(appended.trace().frames().getLast().recipeId()).isNull();
        assertThat(appended.details()).containsEntry("detail", "kept");
    }

    @Test
    void helper_identifier_overloads_resolve_the_same_registered_resources() {
        var api = new KubeJSApi();
        assertThat(((BlockPredicate.OfBlock) api.block(ResourceLocation.parse("minecraft:stone")))
                .block()).isSameAs(Blocks.STONE);
        assertThat(((BlockPredicate.OfTag) api.tag(ResourceLocation.parse("minecraft:logs")))
                .tag().location()).isEqualTo(ResourceLocation.parse("minecraft:logs"));
        var input = (MachineIngredient.ItemIngredient)
                api.itemInput(ResourceLocation.parse("minecraft:iron_ingot"), 2, 1F);
        assertThat(input.item().test(Items.IRON_INGOT.getDefaultInstance())).isTrue();
        assertThat(input.item().test(Items.GOLD_INGOT.getDefaultInstance())).isFalse();
        assertThat(api.fluidStack(ResourceLocation.parse("minecraft:water"), 10).getFluid()).isSameAs(Fluids.WATER);
        var builder = new MachineRecipeBuilderJS("test:identifier_recipe")
                .id(ResourceLocation.parse("test:renamed_recipe"))
                .itemInput(ResourceLocation.parse("minecraft:iron_ingot"), 2)
                .itemOutput("minecraft:gold_ingot", 1)
                .requiredHosts(ResourceLocation.parse("test:host"), ResourceLocation.parse("test:other_host"));
        builder.recipePoolId = ResourceLocation.parse("test:pool");
        var recipe = builder.createObject();
        assertThat(recipe.id()).isEqualTo(ResourceLocation.parse("test:renamed_recipe"));
        assertThat(recipe.canRunOnConnectedHost("test:host")).isTrue();
        assertThat(recipe.canRunOnConnectedHost("test:unlisted")).isFalse();
        assertThat(recipe.withId("test:copied").id()).isEqualTo(ResourceLocation.parse("test:copied"));
    }
}
