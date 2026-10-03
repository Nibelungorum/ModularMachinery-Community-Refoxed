package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.compat.create.CreateFailureReasons;
import cn.howxu.mmcr.client.model.RuntimeMachineModelRegistry;
import cn.howxu.mmcr.compat.create.loaded.StressInterfaceAppearance;
import cn.howxu.mmcr.compat.create.loaded.StressInterfaceKind;
import cn.howxu.mmcr.compat.create.StressRequirement;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.simibubi.create.content.kinetics.simpleRelays.AbstractShaftBlock;
import net.minecraft.core.Direction;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagEntry;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Semantic assertions on real provider builders, compared with Create's native rules.
 * @author howxu <dev@howxu.cn>
 */
class CreateStressDataGenTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void everyShaftAxisUsesSingleCasingWithMatchingRotation() {
        for (var kind : StressInterfaceKind.values()) {
            var block = registeredBlock(kind);
            ExistingFileHelper files = new ExistingFileHelper(List.of(), Set.of(), true, null, null) {
                @Override
                public boolean exists(ResourceLocation location, PackType packType) {
                    return super.exists(location, packType) || packType == PackType.CLIENT_RESOURCES
                            && getClass().getResource("/assets/" + location.getNamespace() + "/" + location.getPath()) != null;
                }
            };
            BlockStateProvider provider = new BlockStateProvider(new PackOutput(Path.of(".")), MMCR.MODID, files) {
                @Override protected void registerStatesAndModels() {}
            };
            StressInterfaceAppearance.generateModels(provider, block, kind.id());
            JsonObject variants = provider.getVariantBuilder(block).toJson().getAsJsonObject("variants");
            assertEquals(block.getStateDefinition().getPossibleStates().size(), variants.size());
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                JsonObject model = variants.entrySet().stream()
                        .filter(entry -> matches(entry.getKey(), state))
                        .findFirst().orElseThrow().getValue().getAsJsonObject();
                assertEquals("create:block/encased_chain_drive/single", model.get("model").getAsString());
                Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
                assertEquals(axis == Direction.Axis.Y ? 90 : 0, model.has("x") ? model.get("x").getAsInt() : 0);
                assertEquals(axis == Direction.Axis.X ? 90 : 0, model.has("y") ? model.get("y").getAsInt() : 0);
            }
            assertEquals("create:block/encased_chain_drive/item",
                    provider.itemModels().getBuilder(kind.id()).toJson().get("parent").getAsString());
        }
    }

    @Test
    void actualRecipeBuildersProduceReversedVerticalInputsAndCreateCondition() {
        for (boolean reverse : List.of(false, true)) {
            CapturedRecipe output = new CapturedRecipe();
            ModRecipeProvider.createStressRecipe(output, reverse ? Items.DIAMOND : Items.EMERALD,
                    Items.CHAIN, Items.IRON_BLOCK, Items.COPPER_INGOT, reverse);
            ShapedRecipe recipe = assertInstanceOf(ShapedRecipe.class, output.recipe);
            assertEquals(1, recipe.getWidth());
            assertEquals(3, recipe.getHeight());
            assertTrue(recipe.getIngredients().get(0).test(new ItemStack(reverse ? Items.COPPER_INGOT : Items.CHAIN)));
            assertTrue(recipe.getIngredients().get(1).test(new ItemStack(Items.IRON_BLOCK)));
            assertTrue(recipe.getIngredients().get(2).test(new ItemStack(reverse ? Items.CHAIN : Items.COPPER_INGOT)));
            ItemStack result = recipe.getResultItem(null);
            assertTrue(result.is(reverse ? Items.DIAMOND : Items.EMERALD));
            assertEquals(1, result.getCount());
            assertEquals(List.of(new ModLoadedCondition("create")), output.conditions);
            assertNotNull(output.advancement);
        }
    }

    @Test
    void nativeFamiliesAutomaticallyGenerateOptionalDirectionAndModTags() {
        for (var kind : StressInterfaceKind.values()) {
            PortTagSet tags = PortTagSet.forKind(kind);
            assertTrue(tags.optionalEntries());
            assertEquals(List.of(MMCR.id("ports"), MMCR.id("machines"), MMCR.id("create_stress_ports"),
                    MMCR.id("create_stress_" + kind.ioType().getSerializedName() + "_ports"),
                    MMCR.id("create_ports")), tags.tags());
        }
    }

    @Test
    void realProvidersDeclareCreatePortsOnlyOptionalAndPreserveOrdinaryRequiredEntries() throws Exception {
        Map<String, DeferredHolder<Block, Block>> previousBlocks = new LinkedHashMap<>(ModBlocks.BLOCKS);
        Map<Block, Item> previousBlockItems = new LinkedHashMap<>();
        Map<DeferredHolder<?, ?>, Object> previousItemBindings = new LinkedHashMap<>();
        Field holderField = DeferredHolder.class.getDeclaredField("holder");
        holderField.setAccessible(true);
        try {
            // Other registry tests can leave declarations without runtime-bound blocks.
            ModBlocks.BLOCKS.entrySet().removeIf(entry -> !entry.getValue().isBound());
            // Complete the existing declared item fixtures needed by the full item provider.
            var registerItem = TestBootstrap.class.getDeclaredMethod("registerItem", DeferredHolder.class);
            registerItem.setAccessible(true);
            var bind = TestBootstrap.class.getDeclaredMethod("bind", Object.class, Object.class);
            bind.setAccessible(true);
            for (var holder : List.of(ModItems.MODULARIUM, ModItems.MULTIBLOCK_DETECTOR)) {
                previousItemBindings.put(holder, holderField.get(holder));
                bind.invoke(null, holder, registerItem.invoke(null, holder));
            }
            for (StressInterfaceKind kind : StressInterfaceKind.values()) {
                Block block = registeredBlock(kind, MMCR.id(kind.id()));
                assertInstanceOf(AbstractShaftBlock.class, block);
                previousBlockItems.put(block, Item.BY_BLOCK.get(block));
                Item.BY_BLOCK.put(block, registeredBlockItem(block, MMCR.id(kind.id())));
                ModBlocks.BLOCKS.put(kind.id(), DeferredHolder.create(Registries.BLOCK, MMCR.id(kind.id())));
                RuntimeMachineModelRegistry.invalidate();
                assertTrue(RuntimeMachineModelRegistry.isDynamicBlock(block));
            }
            HolderLookup.Provider lookup = HolderLookup.Provider.create(Stream.of(
                    BuiltInRegistries.BLOCK.asLookup(), BuiltInRegistries.ITEM.asLookup()));
            var futureLookup = CompletableFuture.completedFuture(lookup);
            ModBlockTags blocks = new ModBlockTags(new PackOutput(Path.of(".")), futureLookup);
            ModItemTags items = new ModItemTags(new PackOutput(Path.of(".")), futureLookup,
                    CompletableFuture.completedFuture(TagsProvider.TagLookup.empty()));
            blocks.addTags(lookup);
            items.addTags(lookup);
            // Unit bootstrap has no optional mods. Feed the actual native declarations into
            // each real provider's port path without replacing the global PortKinds registry.
            var blockPorts = ModBlockTags.class.getDeclaredMethod("addPortTags", IOPortKind.class);
            var itemPorts = ModItemTags.class.getDeclaredMethod("addPortTags", IOPortKind.class);
            blockPorts.setAccessible(true);
            itemPorts.setAccessible(true);
            for (StressInterfaceKind kind : StressInterfaceKind.values()) {
                if (PortKinds.all().stream().noneMatch(registered -> registered.id().equals(kind.id()))) {
                    blockPorts.invoke(blocks, kind);
                    itemPorts.invoke(items, kind);
                }
            }
            Set<ResourceLocation> createIds = Set.of(MMCR.id(StressInterfaceKind.INPUT.id()), MMCR.id(StressInterfaceKind.OUTPUT.id()));
            for (TagsProvider<?> provider : List.of(blocks, items)) {
                Map<ResourceLocation, TagBuilder> tags = tagBuilders(provider);
                for (StressInterfaceKind kind : StressInterfaceKind.values()) {
                    for (ResourceLocation tagId : PortTagSet.forKind(kind).tags()) {
                        List<TagEntry> entries = tags.get(tagId).build();
                        List<TagEntry> references = entries.stream().filter(entry -> entry.getId().equals(MMCR.id(kind.id()))).toList();
                        assertEquals(1, references.size(), tagId + " must not contain duplicate required/optional port entries");
                        assertFalse(references.getFirst().isRequired());
                        assertFalse(references.getFirst().isTag());
                        assertTrue(entries.stream().allMatch(entry -> entry.verifyIfPresent(id -> !createIds.contains(id), id -> true)),
                                tagId + " must resolve when Create ports are absent");
                    }
                }
                for (String tag : List.of("machines", "ports")) {
                    List<TagEntry> entries = tags.get(MMCR.id(tag)).build();
                    assertRequiredElement(entries, MMCR.id(PortKinds.ITEM_INPUT.id()));
                    assertRequiredElement(entries, MMCR.id("smart_interface"));
                }
            }
        } finally {
            ModBlocks.BLOCKS.clear();
            ModBlocks.BLOCKS.putAll(previousBlocks);
            RuntimeMachineModelRegistry.invalidate();
            previousBlockItems.forEach((block, item) -> {
                if (item == null) Item.BY_BLOCK.remove(block);
                else Item.BY_BLOCK.put(block, item);
            });
            for (var entry : previousItemBindings.entrySet()) holderField.set(entry.getKey(), entry.getValue());
        }
    }

    @Test
    void bothLanguagesProvideEveryRegisteredStressFailureAndPortDescription() throws Exception {
        for (String language : List.of("en_us", "zh_cn")) {
            try (var input = getClass().getResourceAsStream("/assets/mmcr/lang/" + language + ".json")) {
                assertNotNull(input);
                JsonObject translations = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var reason : List.of(CreateFailureReasons.CREATE_UNAVAILABLE, CreateFailureReasons.MISSING_ROTATION,
                        CreateFailureReasons.INSUFFICIENT_RPM, CreateFailureReasons.INSUFFICIENT_STRESS,
                        CreateFailureReasons.INCOMPATIBLE_OUTPUT_RPM, CreateFailureReasons.INVALID_OUTPUT_RPM,
                        CreateFailureReasons.MISSING_RECIPE_SCOPE)) {
                    assertFalse(translations.get(reason.translationKey()).getAsString().isBlank());
                }
                for (String key : List.of("block.mmcr.create_stress_input_interface", "block.mmcr.create_stress_output_interface",
                        StressRequirement.TYPE.presentation().translationKey(), StressRequirement.TYPE.presentation().descriptionKey(),
                        "requirement.create.stress.input", "requirement.create.stress.output")) {
                    assertFalse(translations.get(key).getAsString().isBlank());
                }
            }
        }
    }

    private static boolean matches(String variant, BlockState state) {
        for (String assignment : variant.split(",")) {
            String[] pair = assignment.split("=");
            if (pair[0].equals(BlockStateProperties.AXIS.getName()) && !pair[1].equals(state.getValue(BlockStateProperties.AXIS).getSerializedName())) return false;
            if (pair[0].equals(BlockStateProperties.WATERLOGGED.getName())
                    && !pair[1].equals(state.getValue(BlockStateProperties.WATERLOGGED).toString())) return false;
        }
        return true;
    }

    private static Block registeredBlock(StressInterfaceKind kind) {
        return registeredBlock(kind, ResourceLocation.fromNamespaceAndPath("mmcr_test", "stress_datagen_" + kind.id()));
    }

    private static Block registeredBlock(StressInterfaceKind kind, ResourceLocation id) {
        MappedRegistry<Block> blocks = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        if (blocks.containsKey(id)) return blocks.get(id);
        blocks.unfreeze();
        try {
            return Registry.register(blocks, id, kind.createBlock(Blocks.IRON_BLOCK.properties(), () -> BlockEntityType.CHEST));
        } finally {
            blocks.freeze();
        }
    }

    private static Item registeredBlockItem(Block block, ResourceLocation id) {
        MappedRegistry<Item> items = (MappedRegistry<Item>) BuiltInRegistries.ITEM;
        if (items.containsKey(id)) return items.get(id);
        items.unfreeze();
        try {
            return Registry.register(items, id, new BlockItem(block, new Item.Properties()));
        } finally {
            items.freeze();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<ResourceLocation, TagBuilder> tagBuilders(TagsProvider<?> provider) throws Exception {
        Field builders = TagsProvider.class.getDeclaredField("builders");
        builders.setAccessible(true);
        return (Map<ResourceLocation, TagBuilder>) builders.get(provider);
    }

    private static void assertRequiredElement(List<TagEntry> entries, ResourceLocation id) {
        List<TagEntry> matches = entries.stream().filter(entry -> entry.getId().equals(id)).toList();
        assertEquals(1, matches.size());
        assertTrue(matches.getFirst().isRequired());
        assertFalse(matches.getFirst().isTag());
    }

    /** Captures recipes from the real RecipeOutput condition wrapper.
     * @author howxu <dev@howxu.cn>
     */
    private static final class CapturedRecipe implements RecipeOutput {
        private Recipe<?> recipe;
        private List<ICondition> conditions;
        private AdvancementHolder advancement;
        @Override
        public void accept(ResourceLocation id, Recipe<?> recipe, AdvancementHolder advancement, ICondition... conditions) {
            this.recipe = recipe;
            this.conditions = List.of(conditions);
            this.advancement = advancement;
        }
        @Override public Advancement.Builder advancement() { return Advancement.Builder.recipeAdvancement(); }
    }
}
