package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import cn.howxu.mmcr.api.publicapi.machine.BlockPredicate;
import cn.howxu.mmcr.api.publicapi.machine.PortTiers;
import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineBehavior;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import cn.howxu.mmcr.api.publicapi.machine.MachineRole;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.publicapi.machine.ModifierUse;
import cn.howxu.mmcr.api.publicapi.machine.PatternBuilder;
import cn.howxu.mmcr.api.publicapi.machine.RecipeBehavior;
import cn.howxu.mmcr.internal.api.PublicMachineAdapter;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.datagen.ModRecipeProvider;
import cn.howxu.mmcr.internal.port.UpgradeBusSize;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import net.minecraft.data.recipes.RecipeOutput;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies the public builders keep definitions and structures separate.
 * @author howxu <dev@howxu.cn>
 */
class PublicMachineBuilderTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void machine_builder_builds_base_properties_without_structure() {
        MachineDefinition definition = MachineBuilder.machine(MMCR.id("base_machine"))
                .displayNameKey("machine.mmcr.base_machine")
                .maxParallelism(4)
                .build();

        assertThat(definition.id()).isEqualTo(MMCR.id("base_machine"));
        assertThat(definition.displayNameKey()).isEqualTo("machine.mmcr.base_machine");
        assertThat(definition.behavior().kind()).isEqualTo(MachineBehavior.Kind.RECIPE);
        assertThat(MachineBuilder.class.getDeclaredMethods()).noneMatch(method ->
                method.getName().equals("pattern") || method.getName().equals("stage")
                        || method.getName().equals("expandableStructure"));
    }

    @Test
    void machine_builder_retains_hooks_for_default_recipe_behavior() {
        AtomicInteger preCalls = new AtomicInteger();
        AtomicInteger postCalls = new AtomicInteger();

        MachineDefinition definition = MachineBuilder.machine(MMCR.id("hook_builder_machine"))
                .preServerTick(context -> preCalls.incrementAndGet())
                .postServerTick(context -> postCalls.incrementAndGet())
                .build();

        assertThat(definition.behavior()).isInstanceOf(RecipeBehavior.class);
        RecipeBehavior behavior = (RecipeBehavior) definition.behavior();
        behavior.preServerTick().accept(null);
        behavior.postServerTick().accept(null);
        assertThat(preCalls).hasValue(1);
        assertThat(postCalls).hasValue(1);
    }

    @Test
    void machine_builder_rejects_hooks_when_selecting_tick_behavior() {
        assertThatThrownBy(() -> MachineBuilder.machine(MMCR.id("invalid_hook_machine"))
                .preServerTick(context -> { })
                .tickBehavior(builder -> builder.serverTick(context -> { })))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("server tick hooks");

        assertThatThrownBy(() -> MachineBuilder.machine(MMCR.id("invalid_tick_hook_machine"))
                .tickBehavior(builder -> builder.serverTick(context -> { }))
                .postServerTick(context -> { }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("server tick hooks");
    }

    @Test
    void machine_definition_builder_preserves_metadata_factory_and_role_semantics() {
        var moduleId = MMCR.id("processing_module");
        var definition = MachineBuilder.machine(MMCR.id("arc_furnace"))
                .displayNameKey("machine.mmcr.arc_furnace")
                .controller(controller -> controller
                        .textures(MMCR.id("block/arc_front"), MMCR.id("block/arc_side"))
                        .allowVerticalFacing()
                        .tooltip("tooltip.mmcr.arc_furnace.0"))
                .appearance(appearance -> appearance
                        .machineBasicBlock(MMCR.id("steel_casing"))
                        .controllerBaseTexture(MMCR.id("block/steel_controller"))
                        .formedPortBaseTexture(MMCR.id("block/steel_port")))
                .factory(factory -> factory.hasFactory(true).threadLimit(4)
                        .thread("smelting", MMCR.id("arc_recipe")))
                .role(MachineRole.HOST)
                .acceptedModule(moduleId)
                .maxParallelism(8)
                .parallelizable(true)
                .failureAction(RecipeFailureActions.RESET)
                .build();

        assertThat(definition.controller().tooltip()).containsExactly("tooltip.mmcr.arc_furnace.0");
        assertThat(definition.controller().allowVerticalFacing()).isTrue();
        assertThat(definition.appearance().machineBasicBlock()).isEqualTo(MMCR.id("steel_casing"));
        assertThat(definition.factory().hasFactory()).isTrue();
        assertThat(definition.factory().threadLimit()).isEqualTo(4);
        assertThat(definition.factory().threads()).hasSize(1);
        assertThat(definition.role()).isEqualTo(MachineRole.HOST);
        assertThat(definition.acceptedModuleIds()).containsExactly(moduleId);
        assertThat(definition.maxParallelism()).isEqualTo(8);
        assertThat(definition.parallelizable()).isTrue();
        assertThat(definition.failureAction()).isEqualTo(RecipeFailureActions.RESET);
    }

    @Test
    void machine_definition_builder_enforces_role_and_factory_values() {
        var moduleId = MMCR.id("processing_module");

        assertThatThrownBy(() -> MachineBuilder.machine(MMCR.id("invalid_host"))
                .role(MachineRole.HOST).build())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("accept");
        assertThatThrownBy(() -> MachineBuilder.machine(MMCR.id("invalid_normal"))
                .acceptedModule(moduleId).build())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("HOST");
        assertThatThrownBy(() -> MachineBuilder.machine(MMCR.id("bad_factory"))
                .factory(factory -> factory.threadLimit(0)).build())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("threadLimit");
    }

    @Test
    void alloy_furnace_builtin_registers_modifier_items_and_base_replacements() {
        Identifier machineId = MMCR.id("alloy_furnace");
        var definitions = new MMCRMachineDefinationsEvent();
        org.nibelungorum.builtin.ALLOY_FURNACE.registerDefinitions(definitions);

        var structures = new MMCRMachineStructuresEvent(List.of(machineId));
        org.nibelungorum.builtin.ALLOY_FURNACE.registerStructures(structures);

        Identifier diamondId = MMCR.id("alloy_furnace_diamond_speedup");
        Identifier goldId = MMCR.id("alloy_furnace_gold_doubling");
        assertThat(definitions.definitions()).containsKey(machineId);
        assertThat(structures.modifierItems().get(diamondId)).singleElement()
                .satisfies(stack -> assertThat(stack.is(Items.DIAMOND_BLOCK)).isTrue());
        assertThat(structures.modifierItems().get(goldId)).singleElement()
                .satisfies(stack -> assertThat(stack.is(Items.GOLD_BLOCK)).isTrue());

        var stage = structures.structures().get(machineId).stages().getFirst();
        assertThat(stage.pattern().predicates().get('M').block()).contains(Blocks.BLAST_FURNACE);
        assertThat(stage.requirements().modifierReplacements().get('M'))
                .extracting(ModifierUse::modifierId)
                .containsExactly(diamondId, goldId);
        assertThat(stage.requirements().modifierReplacements().get('M'))
                .extracting(ModifierUse::replacement)
                .allSatisfy(replacement -> assertThat(replacement).isNotNull());
    }

    @Test
    void recipe_provider_generates_all_upgrade_bus_tiers_and_model_entries() throws Exception {
        Method registerItem = TestBootstrap.class.getDeclaredMethod("registerItem", DeferredHolder.class);
        registerItem.setAccessible(true);
        Item modularium = (Item) registerItem.invoke(null, ModItems.MODULARIUM);
        Method bind = TestBootstrap.class.getDeclaredMethod("bind", Object.class, Object.class);
        bind.setAccessible(true);
        bind.invoke(null, ModItems.MODULARIUM, modularium);
        installRecipeTestTags();

        Map<Identifier, Recipe<?>> recipes = new LinkedHashMap<>();
        RecipeOutput output = new RecipeOutput() {
            @Override
            public void accept(ResourceKey<Recipe<?>> id, Recipe<?> recipe, AdvancementHolder advancement,
                               ICondition... conditions) {
                recipes.put(id.identifier(), recipe);
            }

            @Override
            public Advancement.Builder advancement() {
                return Advancement.Builder.recipeAdvancement();
            }

            @Override
            public void includeRootAdvancement() {
            }
        };
        HolderLookup.Provider lookup = HolderLookup.Provider.create(Stream.of(BuiltInRegistries.ITEM));
        assertThat(lookup.lookupOrThrow(Registries.ITEM).get(Tags.Items.INGOTS_COPPER)).isPresent();
        ModRecipeProvider provider = new ModRecipeProvider(lookup, output);
        Method buildRecipes = ModRecipeProvider.class.getDeclaredMethod("buildRecipes");
        buildRecipes.setAccessible(true);
        buildRecipes.invoke(provider);

        for (UpgradeBusSize size : UpgradeBusSize.values()) {
            String id = "upgrade_bus_" + size.id();
            assertThat(recipes).containsKey(MMCR.id(id));
            assertThat(PublicMachineBuilderTest.class.getResource("/assets/mmcr/models/block/" + id + ".json"))
                    .as("block model %s", id).isNotNull();
            assertThat(PublicMachineBuilderTest.class.getResource("/assets/mmcr/models/item/" + id + ".json"))
                    .as("item model %s", id).isNotNull();
            assertThat(ModItems.ITEMS).containsKey(id);
        }
    }

    private static void installRecipeTestTags() {
        Map<TagKey<Item>, List<Holder<Item>>> tags = new LinkedHashMap<>();
        tags.put(Tags.Items.INGOTS_COPPER, List.of(BuiltInRegistries.ITEM.wrapAsHolder(Items.COPPER_INGOT)));
        tags.put(Tags.Items.INGOTS_IRON, List.of(BuiltInRegistries.ITEM.wrapAsHolder(Items.IRON_INGOT)));
        tags.put(Tags.Items.DUSTS_REDSTONE, List.of(BuiltInRegistries.ITEM.wrapAsHolder(Items.REDSTONE)));
        tags.put(Tags.Items.DUSTS_GLOWSTONE, List.of(BuiltInRegistries.ITEM.wrapAsHolder(Items.GLOWSTONE_DUST)));
        tags.put(Tags.Items.STORAGE_BLOCKS_REDSTONE,
                List.of(BuiltInRegistries.ITEM.wrapAsHolder(Blocks.REDSTONE_BLOCK.asItem())));
        tags.put(Tags.Items.GEMS_DIAMOND, List.of(BuiltInRegistries.ITEM.wrapAsHolder(Items.DIAMOND)));
        tags.put(Tags.Items.CHESTS, List.of(BuiltInRegistries.ITEM.wrapAsHolder(Items.CHEST)));
        tags.put(Tags.Items.GLASS_PANES, List.of(BuiltInRegistries.ITEM.wrapAsHolder(Items.GLASS_PANE)));
        tags.put(Tags.Items.GEMS_LAPIS, List.of(BuiltInRegistries.ITEM.wrapAsHolder(Items.LAPIS_LAZULI)));
        tags.put(Tags.Items.GEMS_AMETHYST, List.of(BuiltInRegistries.ITEM.wrapAsHolder(Items.AMETHYST_SHARD)));
        tags.put(Tags.Items.INGOTS_GOLD, List.of(BuiltInRegistries.ITEM.wrapAsHolder(Items.GOLD_INGOT)));
        tags.put(Tags.Items.INGOTS_NETHERITE, List.of(BuiltInRegistries.ITEM.wrapAsHolder(Items.NETHERITE_INGOT)));
        tags.put(ItemTags.create(Identifier.withDefaultNamespace("bookshelf_books")),
                List.of(BuiltInRegistries.ITEM.wrapAsHolder(Items.BOOK)));
        BuiltInRegistries.ITEM.prepareTagReload(new TagLoader.LoadResult<>(Registries.ITEM, tags)).apply();
        assertThat(BuiltInRegistries.ITEM.get(Tags.Items.INGOTS_COPPER)).isPresent();
    }

    @Test
    void structure_builder_owns_main_structure_and_extensions() {
        MachineStructureDefinition structure = MachineStructureBuilder.structure()
                .fullStructure(stage -> stage.pattern(pattern -> pattern.layer("F")
                        .where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F')))
                .extension(stage -> stage.pattern(pattern -> pattern.layer("C")
                        .where('C', BlockPredicate.block(Blocks.STONE)).controller('C')))
                .build(MMCR.id("tower"));

        assertThat(structure.machineId()).isEqualTo(MMCR.id("tower"));
        assertThat(structure.stages()).extracting("kind")
                .containsExactly(cn.howxu.mmcr.api.publicapi.machine.StructureStage.Kind.FULL,
                        cn.howxu.mmcr.api.publicapi.machine.StructureStage.Kind.EXTENSION);
    }

    @Test
    void structure_builder_preserves_ports_tiers_and_requirements() {
        var machineId = MMCR.id("structured_machine");
        var structure = MachineStructureBuilder.structure()
                .fullStructure(stage -> stage
                        .pattern(pattern -> pattern.layer("CFC")
                                .where('C', BlockPredicate.block(Blocks.STONE))
                                .where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F'))
                        .ports(ports -> ports.min("item_input_bus", 1).range("energy_input_hatch", 1, 2))
                        .portTiers(tiers -> tiers.minItemInput(PortTiers.ItemTier.NORMAL)
                                .minFluidOutput(PortTiers.FluidTier.BIG)
                                .minEnergyInput(PortTiers.EnergyTier.NORMAL))
                        .requirements(requirements -> requirements
                                .levelSlot('C', MMCR.id("coil"))
                                 .modifier('C', ModifierUse.of(MMCR.id("gold_modifier"),
                                         BlockPredicate.block(Blocks.GOLD_BLOCK)))))
                .build(machineId);

        var stage = structure.stages().getFirst();
        assertThat(stage.portRequirements().requirements()).containsKeys("item_input_bus", "energy_input_hatch");
        assertThat(stage.portTiers().requirements()).extracting("minTierId")
                .containsExactly("normal", "big", "normal");
        assertThat(stage.requirements().levelSlots()).containsEntry('C', MMCR.id("coil"));
        assertThat(stage.requirements().modifierReplacements()).containsKey('C');
    }

    @Test
    void structure_builder_rejects_missing_main_structure() {
        assertThatThrownBy(() -> MachineStructureBuilder.structure()
                .extension(stage -> stage.pattern(pattern -> pattern.layer("C")
                        .where('C', BlockPredicate.block(Blocks.STONE)).controller('C')))
                .build(MMCR.id("missing_main")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Main machine structure");
    }

    @Test
    void structure_builder_expands_complete_levels_in_order() {
        var structure = MachineStructureBuilder.structure()
                .fullStructure(stage -> stage.pattern(pattern -> pattern.layer("F")
                        .where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F')))
                .expandStructure(stage -> stage.pattern(pattern -> pattern.layer("S")
                        .where('S', BlockPredicate.block(Blocks.STONE)).controller('S')))
                .expandStructure(stage -> stage.pattern(pattern -> pattern.layer("I")
                        .where('I', BlockPredicate.block(Blocks.IRON_BLOCK)).controller('I')))
                .build(MMCR.id("expanded_levels"));

        assertThat(structure.stages()).extracting("kind")
                .containsExactly(cn.howxu.mmcr.api.publicapi.machine.StructureStage.Kind.FULL,
                        cn.howxu.mmcr.api.publicapi.machine.StructureStage.Kind.EXPANSION,
                        cn.howxu.mmcr.api.publicapi.machine.StructureStage.Kind.EXPANSION);
    }

    @Test
    void structure_builder_rejects_expansion_before_main_structure() {
        assertThatThrownBy(() -> MachineStructureBuilder.structure()
                .expandStructure(stage -> stage.pattern(pattern -> pattern.layer("S")
                        .where('S', BlockPredicate.block(Blocks.STONE)).controller('S'))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("expandStructure requires a full structure first");
    }

    @Test
    void structure_builder_rejects_multiple_main_structures() {
        assertThatThrownBy(() -> MachineStructureBuilder.structure()
                .fullStructure(stage -> stage.pattern(pattern -> pattern.layer("F")
                        .where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F')))
                .fullStructure(stage -> stage.pattern(pattern -> pattern.layer("C")
                        .where('C', BlockPredicate.block(Blocks.STONE)).controller('C')))
                .build(MMCR.id("multiple_main")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only one main machine structure");
    }

    @Test
    void pattern_builder_preserves_immutable_structure_values_and_rejects_invalid_bindings() {
        BlockPredicate casing = BlockPredicate.block(Blocks.STONE);
        BlockPredicate controller = BlockPredicate.block(Blocks.FURNACE);
        var pattern = PatternBuilder.pattern().layer("CCC", "C C", "CFC")
                .where('C', casing).where('F', controller).controller('F').build();

        assertThat(pattern.layers()).containsExactly(List.of("CCC", "C C", "CFC"));
        assertThatThrownBy(() -> pattern.layers().add(List.of("X")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> PatternBuilder.pattern().layer("CC", "CCC"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("same width");
        assertThat(PatternBuilder.pattern().layer("CF")
                .where('C', casing).controller('F').build().predicates()).containsKey('F');
    }

    @Test
    void structure_builder_preserves_full_then_extension_conversion() {
        var machineId = MMCR.id("structure_conversion");
        var structure = MachineStructureBuilder.structure()
                .fullStructure(stage -> stage.pattern(pattern -> pattern.layer("F")
                        .where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F')))
                .extension(stage -> stage.pattern(pattern -> pattern.layer("C")
                        .where('C', BlockPredicate.machineCoupler()).controller('C')))
                .build(machineId);

        assertThat(PublicMachineAdapter.toStructureDefinition(structure).declarations())
                .extracting(cn.howxu.mmcr.api.machine.MachineStructureDefinition.Declaration::kind)
                .containsExactly(cn.howxu.mmcr.api.machine.MachineStructureDefinition.Declaration.Kind.FULL,
                        cn.howxu.mmcr.api.machine.MachineStructureDefinition.Declaration.Kind.EXTENSION);
    }
}
