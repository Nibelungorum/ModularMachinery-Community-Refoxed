package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.botania.BotaniaBridge;
import cn.howxu.mmcr.compat.botania.BotaniaBridgeBootstrap;
import cn.howxu.mmcr.compat.botania.BotaniaManaIds;
import cn.howxu.mmcr.compat.botania.ManaPortKind;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagBuilder;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises real recipe, tag and self-drop providers rather than source text or constants.
 * @author howxu <dev@howxu.cn>
 */
class BotaniaManaDataGenTest {
    private static final PackOutput OUTPUT = new PackOutput(Path.of("botania-mana-datagen-test"));
    private static final List<String> PORTS = List.of(BotaniaManaIds.INPUT, BotaniaManaIds.OUTPUT);
    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        var serializers = NeoForgeRegistries.CONDITION_SERIALIZERS;
        ResourceLocation id = ResourceLocation.parse("neoforge:mod_loaded");
        if (!serializers.containsKey(id)) {
            var mutable = (MappedRegistry<?>) serializers;
            Field frozen = MappedRegistry.class.getDeclaredField("frozen");
            frozen.setAccessible(true);
            boolean wasFrozen = frozen.getBoolean(mutable);
            mutable.unfreeze();
            Registry.register(serializers, id, ModLoadedCondition.CODEC);
            if (wasFrozen) mutable.freeze();
        }
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    @Test
    void actualRecipeProviderUsesNormalPoolAndDirectionalMaterialsAndSkipsAbsentBotania() throws Exception {
        Field holder = DeferredHolder.class.getDeclaredField("holder");
        holder.setAccessible(true);
        Object previousModularium = holder.get(ModItems.MODULARIUM);
        Map<String, DeferredHolder<Item, Item>> previousItems = new LinkedHashMap<>(ModItems.ITEMS);
        BotaniaBridge previousBridge = BotaniaBridge.get();
        try {
            holder.set(ModItems.MODULARIUM, Holder.direct(Items.COPPER_INGOT));
            fixtureItem(ResourceLocation.parse("botania:mana_pool"), null);
            for (String port : PORTS) {
                fixtureItem(MMCR.id(port), fixtureBlock(port));
                ModItems.ITEMS.put(port, DeferredHolder.create(Registries.ITEM, MMCR.id(port)));
            }
            var output = new CapturedRecipes();
            var provider = new ModRecipeProvider(OUTPUT, CompletableFuture.completedFuture(registries));
            Field recipeOutput = ModRecipeProvider.class.getDeclaredField("output");
            recipeOutput.setAccessible(true);
            recipeOutput.set(provider, output);
            var generate = ModRecipeProvider.class.getDeclaredMethod("botaniaManaPoolRecipes");
            generate.setAccessible(true);
            BotaniaBridgeBootstrap.installForTesting(BotaniaBridgeBootstrap.selectForTesting(false));
            generate.invoke(provider);
            assertThat(output.recipes).isEmpty();
            BotaniaBridgeBootstrap.installForTesting((BotaniaBridge) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{BotaniaBridge.class}, (proxy, method, arguments) -> {
                        if (method.getName().equals("available")) return true;
                        throw new AssertionError("Recipes must not require a native pool: " + method.getName());
                    }));
            generate.invoke(provider);
            assertThat(output.recipes.keySet()).containsExactlyInAnyOrderElementsOf(PORTS.stream().map(MMCR::id).toList());
            for (String port : PORTS) {
                JsonObject json = output.recipes.get(MMCR.id(port));
                assertCondition(json);
                assertThat(Recipe.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), json).getOrThrow())
                        .isInstanceOf(ShapedRecipe.class);
                assertThat(json.getAsJsonArray("pattern")).containsExactly(
                        JsonParser.parseString("\" M \""), JsonParser.parseString("\"RPR\""), JsonParser.parseString("\" R \""));
                assertThat(json.getAsJsonObject("key").getAsJsonObject("P").get("item").getAsString()).isEqualTo("botania:mana_pool");
                assertThat(json.getAsJsonObject("key").getAsJsonObject("M").get("item").getAsString()).isEqualTo("minecraft:copper_ingot");
                assertThat(json.getAsJsonObject("key").getAsJsonObject("R").get("item").getAsString())
                        .isEqualTo(port.equals(BotaniaManaIds.INPUT) ? "minecraft:redstone" : "minecraft:gold_ingot");
                assertThat(json.getAsJsonObject("result").get("id").getAsString()).isEqualTo(MMCR.id(port).toString());
                assertThat(json.getAsJsonObject("result").has("components")).isFalse();
            }
            Field modsInstance = ModList.class.getDeclaredField("INSTANCE");
            modsInstance.setAccessible(true);
            Object previousMods = modsInstance.get(null);
            try {
                ModList mods = ModList.of(List.of(), List.of());
                Field indexed = ModList.class.getDeclaredField("indexedMods");
                indexed.setAccessible(true);
                indexed.set(mods, Map.of());
                for (JsonObject generated : output.recipes.values()) {
                    JsonObject missingResult = generated.deepCopy();
                    missingResult.getAsJsonObject("result").addProperty("id", "missing_botania:pool");
                    var ops = registries.createSerializationContext(JsonOps.INSTANCE);
                    assertThat(Recipe.CODEC.parse(ops, missingResult).error()).isPresent();
                    var skipped = Recipe.CONDITIONAL_CODEC.parse(ops, missingResult);
                    assertThat(skipped.error()).isEmpty();
                    assertThat(skipped.getOrThrow()).isEmpty();
                }
            } finally {
                modsInstance.set(null, previousMods);
            }
        } finally {
            BotaniaBridgeBootstrap.installForTesting(previousBridge);
            holder.set(ModItems.MODULARIUM, previousModularium);
            ModItems.ITEMS.clear();
            ModItems.ITEMS.putAll(previousItems);
        }
    }

    @Test
    void realBlockLootSelfDropsAreConditionalAndMissingItemsAreSkippedBeforeDecoding() throws Exception {
        Map<String, DeferredHolder<Block, Block>> previousBlocks = new LinkedHashMap<>(ModBlocks.BLOCKS);
        Field modsInstance = ModList.class.getDeclaredField("INSTANCE");
        modsInstance.setAccessible(true);
        Object previousMods = modsInstance.get(null);
        try {
            ModBlocks.BLOCKS.clear();
            for (String port : PORTS) {
                fixtureItem(MMCR.id(port), fixtureBlock(port));
                ModBlocks.BLOCKS.put(port, DeferredHolder.create(Registries.BLOCK, MMCR.id(port)));
            }
            Map<Path, JsonObject> generated = new ConcurrentHashMap<>();
            CachedOutput capture = (path, bytes, hash) -> generated.put(path,
                    JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject());
            new LootTableGen(OUTPUT, CompletableFuture.completedFuture(registries)).run(capture).join();
            ModList mods = ModList.of(List.of(), List.of());
            Field indexed = ModList.class.getDeclaredField("indexedMods");
            indexed.setAccessible(true);
            indexed.set(mods, Map.of());
            assertThat(generated).hasSize(2);
            for (String port : PORTS) {
                JsonObject table = generated.get(OUTPUT.createRegistryElementsPathProvider(Registries.LOOT_TABLE)
                        .json(MMCR.id("blocks/" + port)));
                assertCondition(table);
                assertThat(LootTable.DIRECT_CODEC.parse(JsonOps.INSTANCE, table).error()).isEmpty();
                JsonObject entry = table.getAsJsonArray("pools").get(0).getAsJsonObject()
                        .getAsJsonArray("entries").get(0).getAsJsonObject();
                assertThat(entry.get("name").getAsString()).isEqualTo(MMCR.id(port).toString());
                assertThat(entry.has("functions")).isFalse();
                entry.addProperty("name", "missing_botania:pool");
                assertThat(LootTable.DIRECT_CODEC.parse(JsonOps.INSTANCE, table).error()).isPresent();
                var skipped = LootDataType.TABLE.conditionalCodec().parse(JsonOps.INSTANCE, table);
                assertThat(skipped.error()).isEmpty();
                assertThat(skipped.getOrThrow()).isEmpty();
            }
        } finally {
            modsInstance.set(null, previousMods);
            ModBlocks.BLOCKS.clear();
            ModBlocks.BLOCKS.putAll(previousBlocks);
        }
    }

    @Test
    void blockAndItemProvidersGenerateOptionalManaFamiliesWithDirectionalMembership() throws Exception {
        var lookup = CompletableFuture.completedFuture(registries);
        var blocks = new ModBlockTags(OUTPUT, lookup);
        var items = new ModItemTags(OUTPUT, lookup, CompletableFuture.completedFuture(TagsProvider.TagLookup.empty()));
        for (IOType io : List.of(IOType.INPUT, IOType.OUTPUT)) {
            IOPortKind kind = new ManaPortKind(io == IOType.INPUT ? BotaniaManaIds.INPUT : BotaniaManaIds.OUTPUT, io);
            for (var provider : List.of(blocks, items)) {
                var add = provider.getClass().getDeclaredMethod("addPortTags", IOPortKind.class);
                add.setAccessible(true);
                add.invoke(provider, kind);
            }
            assertThat(PortTagSet.forKind(kind).tags()).contains(MMCR.id("ports"), MMCR.id("mana_ports"),
                    MMCR.id("mana_" + io.getSerializedName() + "_ports"), MMCR.id("botania_ports"));
        }
        Field builders = TagsProvider.class.getDeclaredField("builders");
        builders.setAccessible(true);
        for (var provider : List.of(blocks, items)) {
            @SuppressWarnings("unchecked")
            Map<ResourceLocation, TagBuilder> tags = (Map<ResourceLocation, TagBuilder>) builders.get(provider);
            for (IOType io : List.of(IOType.INPUT, IOType.OUTPUT)) {
                ResourceLocation id = MMCR.id(io == IOType.INPUT ? BotaniaManaIds.INPUT : BotaniaManaIds.OUTPUT);
                for (String tag : List.of("ports", "mana_ports", "botania_ports", "mana_" + io.getSerializedName() + "_ports")) {
                    assertThat(tags.get(MMCR.id(tag)).build().stream().filter(entry -> entry.getId().equals(id)).toList())
                            .singleElement().satisfies(entry -> {
                                assertThat(entry.isRequired()).isFalse();
                                assertThat(entry.isTag()).isFalse();
                                assertThat(entry.verifyIfPresent(missing -> false, missing -> false)).isTrue();
                            });
                }
                assertThat(tags.get(MMCR.id("mana_" + (io == IOType.INPUT ? "output" : "input") + "_ports")).build())
                        .noneMatch(entry -> entry.getId().equals(id));
            }
        }
    }

    @Test
    void bothLanguagesCoverEveryVisibleManaDeclaration() throws Exception {
        for (String language : List.of("en_us", "zh_cn")) {
            try (var stream = getClass().getResourceAsStream("/assets/mmcr/lang/" + language + ".json")) {
                JsonObject lang = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                for (String key : List.of("block.mmcr." + BotaniaManaIds.INPUT, "block.mmcr." + BotaniaManaIds.OUTPUT,
                        "requirement.botania:mana", "requirement.botania:mana.description", "output.botania:mana",
                        "output.botania:mana.description", "gui.mmcr.failure.botania_unavailable", "gui.mmcr.failure.mana_input_missing",
                        "gui.mmcr.failure.mana_output_blocked", "jei.mmcr.mana", "jei.mmcr.machine_recipe.mana_input",
                        "jei.mmcr.machine_recipe.mana_output", "gui.mmcr.controller.recipe_output.mana", "gui.mmcr.mana.exact")) {
                    assertThat(lang.get(key).getAsString()).isNotBlank();
                }
            }
        }
    }

    private static void assertCondition(JsonObject json) {
        assertThat(ICondition.LIST_CODEC.parse(JsonOps.INSTANCE,
                json.get(ConditionalOps.DEFAULT_CONDITIONS_KEY)).getOrThrow()).containsExactly(new ModLoadedCondition("botania"));
    }

    private static Block fixtureBlock(String port) {
        MappedRegistry<Block> blocks = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        if (blocks.containsKey(MMCR.id(port))) return blocks.get(MMCR.id(port));
        blocks.unfreeze();
        try { return Registry.register(blocks, MMCR.id(port), new Block(Blocks.STONE.properties())); }
        finally { blocks.freeze(); }
    }

    private static Item fixtureItem(ResourceLocation id, Block block) {
        MappedRegistry<Item> items = (MappedRegistry<Item>) BuiltInRegistries.ITEM;
        Item item = items.get(id);
        if (!items.containsKey(id)) {
            items.unfreeze();
            try { item = Registry.register(items, id, block == null ? new Item(new Item.Properties()) : new BlockItem(block, new Item.Properties())); }
            finally { items.freeze(); }
        }
        if (block != null) Item.BY_BLOCK.put(block, item);
        return item;
    }

    /** Captures the serialized output of actual shaped builders and condition codecs.
     * @author howxu <dev@howxu.cn>
     */
    private static final class CapturedRecipes implements RecipeOutput {
        private final Map<ResourceLocation, JsonObject> recipes = new LinkedHashMap<>();

        @Override
        public void accept(ResourceLocation id, Recipe<?> recipe, AdvancementHolder advancement, ICondition... conditions) {
            assertThat(advancement).isNotNull();
            JsonObject json = Recipe.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), recipe)
                    .getOrThrow().getAsJsonObject();
            ICondition.writeConditions(registries, json, conditions);
            assertThat(recipes.put(id, json)).isNull();
        }

        @Override
        public Advancement.Builder advancement() { return Advancement.Builder.recipeAdvancement(); }
    }
}
