package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.publicapi.event.RegisterMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRecipesEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRRegisterRecipesEvent;
import cn.howxu.mmcr.api.publicapi.event.RegisterMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.publicapi.machine.BlockPredicate;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.loading.FMLLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nibelungorum.builtin.PublicBuiltinLevelDefinitions;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies public lifecycle event behavior with real declarations.
 * @author howxu <dev@howxu.cn>
 */
class PublicEventSubscribersTest {
    private boolean lifecycleListenersActive;
    private boolean deprecatedListenerActive;

    @BeforeAll
    static void bootstrapMinecraft() throws Exception { TestBootstrap.bootstrap(); }

    @AfterEach
    void disableManualListeners() {
        lifecycleListenersActive = false;
        deprecatedListenerActive = false;
    }

    @Test
    void events_register_real_definition_structure_and_recipe_ids() {
        var machineId = MMCR.id("event_machine");
        var recipeId = MMCR.id("event_recipe");
        var definitionReceives = new AtomicInteger();
        var structureReceives = new AtomicInteger();
        var recipeReceives = new AtomicInteger();
        var definitions = new AtomicReference<MMCRMachineDefinationsEvent>();
        var structures = new AtomicReference<MMCRMachineStructuresEvent>();
        var recipes = new AtomicReference<MMCRMachineRecipesEvent>();

        lifecycleListenersActive = true;
        NeoForge.EVENT_BUS.addListener(MMCRMachineDefinationsEvent.class, event -> {
            if (!lifecycleListenersActive) return;
            definitionReceives.incrementAndGet();
            event.registerMachine(machineId, builder -> builder.displayNameKey("machine.mmcr.event_machine"));
            definitions.set(event);
        });
        NeoForge.EVENT_BUS.addListener(MMCRMachineStructuresEvent.class, event -> {
            if (!lifecycleListenersActive) return;
            structureReceives.incrementAndGet();
            event.registerStructure(machineId, builder -> builder.fullStructure(stage -> stage.pattern(pattern -> pattern
                    .layer("F").where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F'))));
            structures.set(event);
        });
        NeoForge.EVENT_BUS.addListener(MMCRMachineRecipesEvent.class, event -> {
            if (!lifecycleListenersActive) return;
            recipeReceives.incrementAndGet();
            event.registerRecipe(MachineRecipeBuilder.recipe(recipeId, machineId).duration(1).build());
            recipes.set(event);
        });

        NeoForge.EVENT_BUS.post(new MMCRMachineDefinationsEvent());
        NeoForge.EVENT_BUS.post(new MMCRMachineStructuresEvent(Set.of(machineId)));
        NeoForge.EVENT_BUS.post(new MMCRMachineRecipesEvent());

        assertThat(definitionReceives).hasValue(1);
        assertThat(structureReceives).hasValue(1);
        assertThat(recipeReceives).hasValue(1);
        assertThat(definitions.get().definitions()).containsOnlyKeys(machineId);
        assertThat(structures.get().structures()).containsOnlyKeys(machineId);
        assertThat(recipes.get().recipes()).containsOnlyKeys(recipeId);
    }

    @Test
    void builtin_level_subscriber_registers_the_complete_development_declaration() {
        var event = new MMCRMachineStructuresEvent(Set.of());

        PublicBuiltinLevelDefinitions.register(event);

        assertThat(event.levelTypes()).containsOnlyKeys(PublicBuiltinLevelDefinitions.THERMAL_SMELTING_COIL_TYPE);
        assertThat(event.levelTypes().get(PublicBuiltinLevelDefinitions.THERMAL_SMELTING_COIL_TYPE).displayName().getString())
                .isEqualTo("热能冶炼线圈");
        assertThat(event.levels()).containsOnlyKeys(
                PublicBuiltinLevelDefinitions.COPPER_COIL,
                PublicBuiltinLevelDefinitions.IRON_COIL,
                PublicBuiltinLevelDefinitions.GOLD_COIL,
                PublicBuiltinLevelDefinitions.DIAMOND_COIL);
        assertThat(event.levels().get(PublicBuiltinLevelDefinitions.COPPER_COIL))
                .satisfies(level -> assertBuiltinLevel(level, 0, Blocks.COPPER_BLOCK, 0.9D));
        assertThat(event.levels().get(PublicBuiltinLevelDefinitions.IRON_COIL))
                .satisfies(level -> assertBuiltinLevel(level, 1, Blocks.IRON_BLOCK, 0.8D));
        assertThat(event.levels().get(PublicBuiltinLevelDefinitions.GOLD_COIL))
                .satisfies(level -> assertBuiltinLevel(level, 2, Blocks.GOLD_BLOCK, 0.7D));
        assertThat(event.levels().get(PublicBuiltinLevelDefinitions.DIAMOND_COIL))
                .satisfies(level -> assertBuiltinLevel(level, 3, Blocks.DIAMOND_BLOCK, 0.6D));
    }

    @Test
    void builtin_level_subscriber_does_not_duplicate_an_existing_type() {
        var event = new MMCRMachineStructuresEvent(Set.of());
        event.registerLevelType(new LevelType(PublicBuiltinLevelDefinitions.THERMAL_SMELTING_COIL_TYPE,
                net.minecraft.network.chat.Component.literal("existing")));

        PublicBuiltinLevelDefinitions.register(event);

        assertThat(event.levelTypes()).containsOnlyKeys(PublicBuiltinLevelDefinitions.THERMAL_SMELTING_COIL_TYPE);
        assertThat(event.levels()).isEmpty();
    }

    @Test
    void builtin_level_subscriber_skips_development_levels_in_production() throws Exception {
        FmlLoaderState originalLoader = captureFmlLoader();
        installFmlLoader(true);
        try {
            assertThat(FMLLoader.getCurrent().isProduction()).isTrue();
            var event = new MMCRMachineStructuresEvent(Set.of());

            PublicBuiltinLevelDefinitions.register(event);

            assertThat(event.levelTypes()).isEmpty();
            assertThat(event.levels()).isEmpty();
        } finally {
            restoreFmlLoader(originalLoader);
        }
    }

    @SuppressWarnings("unchecked")
    private static FmlLoaderState captureFmlLoader() throws Exception {
        Class<?> fmlLoaderClass = Class.forName("net.neoforged.fml.loading.FMLLoader");
        Field currentField = findCurrentField(fmlLoaderClass);
        AtomicReference<Object> current = (AtomicReference<Object>) currentField.get(null);
        Object loader = current.get();
        Field loadingModListField = fmlLoaderClass.getDeclaredField("loadingModList");
        loadingModListField.setAccessible(true);
        return new FmlLoaderState(current, loader, loadingModListField, loadingModListField.get(loader));
    }

    private static void restoreFmlLoader(FmlLoaderState originalLoader) throws IllegalAccessException {
        originalLoader.loadingModListField.set(originalLoader.loader, originalLoader.loadingModList);
        originalLoader.current.set(originalLoader.loader);
    }

    @SuppressWarnings("unchecked")
    private static void installFmlLoader(boolean production) throws Exception {
        Class<?> fmlLoaderClass = Class.forName("net.neoforged.fml.loading.FMLLoader");
        Class<?> distClass = Class.forName("net.neoforged.api.distmarker.Dist");
        Class<?> loadingModListClass = Class.forName("net.neoforged.fml.loading.LoadingModList");
        ((AtomicReference<Object>) findCurrentField(fmlLoaderClass).get(null)).set(null);
        Constructor<?> fmlConstructor = fmlLoaderClass.getDeclaredConstructor(
                ClassLoader.class, String[].class, distClass, boolean.class, Path.class);
        fmlConstructor.setAccessible(true);
        fmlConstructor.newInstance(
                Thread.currentThread().getContextClassLoader(), new String[0],
                distClass.getField("CLIENT").get(null), production, Path.of("."));

        Constructor<?> loadingModListConstructor = loadingModListClass.getDeclaredConstructor(
                java.util.List.class, java.util.List.class, java.util.List.class, java.util.List.class, java.util.Map.class);
        loadingModListConstructor.setAccessible(true);
        Object emptyLoadingModList = loadingModListConstructor.newInstance(
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.Map.of());
        Field loadingModListField = fmlLoaderClass.getDeclaredField("loadingModList");
        loadingModListField.setAccessible(true);
        loadingModListField.set(fmlLoaderClass.getMethod("getCurrent").invoke(null), emptyLoadingModList);
    }

    private static Field findCurrentField(Class<?> fmlLoaderClass) {
        for (Field field : fmlLoaderClass.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())
                    && field.getType() == java.util.concurrent.atomic.AtomicReference.class) {
                field.setAccessible(true);
                return field;
            }
        }
        throw new IllegalStateException("Unable to locate active FML loader reference");
    }

    private record FmlLoaderState(AtomicReference<Object> current, Object loader,
            Field loadingModListField, Object loadingModList) {
    }

    @Test
    void structure_registration_rejects_unknown_machine_duplicate_null_and_missing_main() {
        var machineId = MMCR.id("known_machine");
        var event = new MMCRMachineStructuresEvent(Set.of(machineId));
        assertThatThrownBy(() -> event.registerStructure(MMCR.id("unknown"), builder -> builder))
                .isInstanceOf(ApiRegistrationException.class);
        assertThatThrownBy(() -> event.registerStructure(machineId, null))
                .isInstanceOf(ApiRegistrationException.class)
                .hasMessageContaining(machineId.toString());
        assertThatThrownBy(() -> event.registerStructure(machineId, builder -> {
            throw new IllegalArgumentException("invalid declaration");
        }))
                .isInstanceOf(ApiRegistrationException.class)
                .hasMessageContaining(machineId.toString());
        ApiRegistrationException declared = new ApiRegistrationException("declared failure");
        assertThatThrownBy(() -> event.registerStructure(machineId, builder -> {
            throw declared;
        })).isInstanceOf(ApiRegistrationException.class)
                .hasMessageContaining(machineId.toString());
        assertThatThrownBy(() -> event.registerStructure(machineId, builder -> builder))
                .isInstanceOf(ApiRegistrationException.class);
        event.registerStructure(machineId, builder -> builder.fullStructure(stage -> stage.pattern(pattern -> pattern
                .layer("F").where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F'))));
        assertThatThrownBy(() -> event.registerStructure(machineId, builder -> builder))
                .isInstanceOf(ApiRegistrationException.class);
    }

    @Test
    void all_events_reject_null_duplicate_and_writes_after_freeze() {
        var machineId = MMCR.id("frozen_machine");
        var definitions = new MMCRMachineDefinationsEvent();
        assertThatThrownBy(() -> definitions.registerMachine(machineId, null)).isInstanceOf(NullPointerException.class);
        definitions.registerMachine(machineId, builder -> builder);
        assertThatThrownBy(() -> definitions.registerMachine(machineId, builder -> builder))
                .isInstanceOf(IllegalStateException.class);
        definitions.freeze();
        assertThatThrownBy(() -> definitions.registerMachine(MMCR.id("later"), builder -> builder))
                .isInstanceOf(IllegalStateException.class);

        var structures = new MMCRMachineStructuresEvent(Set.of(machineId));
        structures.freeze();
        assertThatThrownBy(() -> structures.registerStructure(machineId, builder -> builder))
                .isInstanceOf(IllegalStateException.class);

        var recipes = new MMCRMachineRecipesEvent();
        var recipe = MachineRecipeBuilder.recipe(MMCR.id("frozen_recipe"), machineId).build();
        recipes.registerRecipe(recipe);
        assertThatThrownBy(() -> recipes.registerRecipe(recipe)).isInstanceOf(IllegalStateException.class);
        recipes.freeze();
        assertThatThrownBy(() -> recipes.registerRecipe(MachineRecipeBuilder.recipe(MMCR.id("later_recipe"), machineId).build()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void canonical_events_keep_deprecated_external_assignability() {
        assertThat(new MMCRMachineDefinationsEvent()).isInstanceOf(RegisterMachineDefinationsEvent.class);
        assertThat(new MMCRMachineStructuresEvent(Set.of())).isInstanceOf(RegisterMachineStructuresEvent.class);
        assertThat(new MMCRMachineRecipesEvent()).isInstanceOf(MMCRRegisterRecipesEvent.class);
    }

    @Test
    void provider_can_implement_only_the_canonical_definition_signature() {
        var event = new MMCRMachineDefinationsEvent();
        MachineDefinitionProvider provider = new MachineDefinitionProvider() {
            @Override
            public void register(MMCRMachineDefinationsEvent event) {
                event.registerMachine(MMCR.id("canonical_provider_machine"), builder -> builder);
            }
        };

        provider.register(event);

        assertThat(event.definitions()).containsKey(MMCR.id("canonical_provider_machine"));
    }

    @Test
    void deprecated_definition_listener_receives_the_canonical_event_instance() {
        var observed = new AtomicReference<RegisterMachineDefinationsEvent>();
        deprecatedListenerActive = true;
        NeoForge.EVENT_BUS.addListener(RegisterMachineDefinationsEvent.class, event -> {
            if (deprecatedListenerActive) observed.set(event);
        });
        var event = new MMCRMachineDefinationsEvent();
        NeoForge.EVENT_BUS.post(event);
        assertThat(observed.get()).isSameAs(event);
    }

    private static void assertBuiltinLevel(cn.howxu.mmcr.api.machine.level.MachineLevel level,
            int priority, net.minecraft.world.level.block.Block block, double durationMultiplier) {
        assertThat(level.typeId()).isEqualTo(PublicBuiltinLevelDefinitions.THERMAL_SMELTING_COIL_TYPE);
        assertThat(level.priority()).isEqualTo(priority);
        assertThat(level.statePredicate()).isInstanceOfSatisfying(cn.howxu.mmcr.api.machine.BlockPredicate.OfBlockState.class,
                predicate -> assertThat(predicate.state()).isEqualTo(block.defaultBlockState()));
        assertThat(level.modifier().durationMultiplier()).isEqualTo(durationMultiplier);
        assertThat(level.modifier().energyMultiplier()).isEqualTo(1D);
        assertThat(level.modifier().outputMultiplier()).isEqualTo(1D);
        assertThat(level.modifier().parallelismBonus()).isZero();
        assertThat(level.modifier().factoryThreadBonus()).isZero();
    }

}
