package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.ars_nouveau.ArsSourceIds;
import cn.howxu.mmcr.compat.fluxnetworks.FluxNetworksBridgeBootstrap;
import cn.howxu.mmcr.compat.fluxnetworks.FluxNetworksIds;
import cn.howxu.mmcr.compat.fluxnetworks.FluxNetworksTestBootstrap;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkInterfaceKind;
import cn.howxu.mmcr.internal.item.InterfaceTooltips;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModItems;
import com.google.common.hash.Hashing;
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
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagEntry;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises optional provider output and codecs; native component matching and loot run in GameTest.
 *
 * @author howxu <dev@howxu.cn>
 */
class FluxNetworksDataGenTest {
    private static final PackOutput OUTPUT = new PackOutput(Path.of("fluxnetworks-datagen-test"));
    private static final Map<String, String> LOOT_MODS = Map.of(
            ArsSourceIds.INPUT, "ars_nouveau", ArsSourceIds.OUTPUT, "ars_nouveau",
            FluxNetworksIds.INPUT, FluxNetworksIds.MOD_ID, FluxNetworksIds.OUTPUT, FluxNetworksIds.MOD_ID);
    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrap() throws Exception {
        FluxNetworksTestBootstrap.bootstrap();
        var serializers = NeoForgeRegistries.CONDITION_SERIALIZERS;
        ResourceLocation id = ResourceLocation.parse("neoforge:mod_loaded");
        if (!serializers.containsKey(id)) {
            var mutable = (MappedRegistry<?>) serializers;
            Field frozenField = MappedRegistry.class.getDeclaredField("frozen");
            frozenField.setAccessible(true);
            boolean frozen = frozenField.getBoolean(mutable);
            mutable.unfreeze();
            Registry.register(serializers, id, ModLoadedCondition.CODEC);
            if (frozen) mutable.freeze();
        }
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    @Test
    void lootProviderConditionsExactlyTheFourOptionalTablesWithTheirOwnModIds() {
        Map<Path, JsonObject> generated = generateLoot();
        LOOT_MODS.forEach((port, mod) -> {
            JsonObject table = generated.get(lootPath(MMCR.id("blocks/" + port)));
            assertThat(ICondition.LIST_CODEC.parse(JsonOps.INSTANCE,
                    table.get(ConditionalOps.DEFAULT_CONDITIONS_KEY)).getOrThrow())
                    .containsExactly(new ModLoadedCondition(mod));
            assertThat(LootTable.DIRECT_CODEC.parse(JsonOps.INSTANCE, table).error()).isEmpty();
            assertThat(table.get("random_sequence").getAsString()).isEqualTo("mmcr:blocks/" + port);
            assertThat(table.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("conditions"))
                    .hasSize(1);
        });
        assertThat(generated.get(lootPath(MMCR.id("blocks/energy_input_hatch_tiny")))
                .has(ConditionalOps.DEFAULT_CONDITIONS_KEY)).isFalse();
        assertThat(generated.get(lootPath(ResourceLocation.fromNamespaceAndPath("other", "blocks/" + FluxNetworksIds.INPUT)))
                .has(ConditionalOps.DEFAULT_CONDITIONS_KEY)).isFalse();
    }

    @Test
    void absentModsSkipMissingOptionalItemsThroughTheRuntimeLootCodec() throws Exception {
        Field instance = ModList.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        Object previous = instance.get(null);
        try {
            ModList mods = ModList.of(List.of(), List.of());
            Field indexedMods = ModList.class.getDeclaredField("indexedMods");
            indexedMods.setAccessible(true);
            indexedMods.set(mods, Map.of());
            Map<Path, JsonObject> generated = generateLoot();
            for (String port : LOOT_MODS.keySet()) {
                ResourceLocation id = MMCR.id(port);
                assertThat(BuiltInRegistries.ITEM.containsKey(id)).isFalse();
                JsonObject table = generated.get(lootPath(MMCR.id("blocks/" + port))).deepCopy();
                // Generation uses stone without optional registries; reload must skip the real missing item.
                table.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries")
                        .get(0).getAsJsonObject().addProperty("name", id.toString());
                assertThat(LootTable.DIRECT_CODEC.parse(JsonOps.INSTANCE, table).error()).isPresent();
                var decoded = LootDataType.TABLE.conditionalCodec().parse(JsonOps.INSTANCE, table);
                assertThat(decoded.error()).isEmpty();
                assertThat(decoded.getOrThrow()).isEmpty();
                assertThat(LootDataType.TABLE.deserialize(MMCR.id("blocks/" + port), JsonOps.INSTANCE, table))
                        .contains(LootTable.EMPTY);
            }
        } finally {
            instance.set(null, previous);
        }
    }

    @Test
    void realRecipeProviderEmitsConditionalPointInputAndPlugOutputAndSkipsAbsentMod() throws Exception {
        Field bridge = FluxNetworksBridgeBootstrap.class.getDeclaredField("bridge");
        bridge.setAccessible(true);
        Object previousBridge = bridge.get(null);
        Map<String, DeferredHolder<Block, Block>> previousBlocks = new LinkedHashMap<>(ModBlocks.BLOCKS);
        Map<Block, Item> previousBlockItems = new LinkedHashMap<>();
        Field holder = DeferredHolder.class.getDeclaredField("holder");
        holder.setAccessible(true);
        Object previousCasing = holder.get(ModBlocks.BASIC_CASING);
        Object previousModularium = holder.get(ModItems.MODULARIUM);
        try {
            holder.set(ModBlocks.BASIC_CASING, Holder.direct(Blocks.IRON_BLOCK));
            holder.set(ModItems.MODULARIUM, Holder.direct(Items.COPPER_INGOT));
            for (var kind : FluxNetworkInterfaceKind.values()) {
                Block block = fixtureBlock(kind);
                previousBlockItems.put(block, Item.BY_BLOCK.get(block));
                Item.BY_BLOCK.put(block, fixtureItem(fixtureId(kind), block));
                ModBlocks.BLOCKS.put(kind.id(), DeferredHolder.create(Registries.BLOCK, fixtureId(kind)));
            }
            fixtureItem(ResourceLocation.parse("fluxnetworks:flux_point"), null);
            fixtureItem(ResourceLocation.parse("fluxnetworks:flux_plug"), null);
            CapturedRecipes output = new CapturedRecipes();
            ModRecipeProvider provider = new ModRecipeProvider(OUTPUT, CompletableFuture.completedFuture(registries));
            Field recipeOutput = ModRecipeProvider.class.getDeclaredField("output");
            recipeOutput.setAccessible(true);
            recipeOutput.set(provider, output);
            var generate = ModRecipeProvider.class.getDeclaredMethod("fluxNetworksInterfaceRecipes");
            generate.setAccessible(true);
            bridge.set(null, FluxNetworksBridgeBootstrap.selectForTesting(false));
            generate.invoke(provider);
            assertThat(output.recipes).isEmpty();
            bridge.set(null, FluxNetworksBridgeBootstrap.selectForTesting(true));
            generate.invoke(provider);
            assertThat(output.recipes.keySet()).containsExactlyInAnyOrder(MMCR.id(FluxNetworksIds.INPUT), MMCR.id(FluxNetworksIds.OUTPUT));
            var ops = registries.createSerializationContext(JsonOps.INSTANCE);
            for (var kind : FluxNetworkInterfaceKind.values()) {
                JsonObject json = output.recipes.get(MMCR.id(kind.id()));
                assertThat(ICondition.LIST_CODEC.parse(ops, json.get(ConditionalOps.DEFAULT_CONDITIONS_KEY)).getOrThrow())
                        .containsExactly(new ModLoadedCondition(FluxNetworksIds.MOD_ID));
                assertThat(Recipe.CODEC.parse(ops, json).getOrThrow()).isInstanceOf(ShapelessRecipe.class);
                assertThat(json.getAsJsonArray("ingredients")).hasSize(3);
                assertThat(json.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString())
                        .isEqualTo(kind == FluxNetworkInterfaceKind.INPUT ? "fluxnetworks:flux_point" : "fluxnetworks:flux_plug");
                assertThat(json.getAsJsonArray("ingredients").get(1).getAsJsonObject().get("item").getAsString())
                        .isEqualTo("minecraft:iron_block");
                assertThat(json.getAsJsonArray("ingredients").get(2).getAsJsonObject().get("item").getAsString())
                        .isEqualTo("minecraft:copper_ingot");
                assertThat(json.getAsJsonObject("result").get("id").getAsString()).isEqualTo(fixtureId(kind).toString());
            }
            Field instance = ModList.class.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            Object previousMods = instance.get(null);
            try {
                ModList mods = ModList.of(List.of(), List.of());
                Field indexedMods = ModList.class.getDeclaredField("indexedMods");
                indexedMods.setAccessible(true);
                indexedMods.set(mods, Map.of());
                for (var entry : output.recipes.entrySet()) {
                    JsonObject json = entry.getValue().deepCopy();
                    assertThat(BuiltInRegistries.ITEM.containsKey(entry.getKey())).isFalse();
                    json.getAsJsonObject("result").addProperty("id", entry.getKey().toString());
                    assertThat(Recipe.CODEC.parse(ops, json).error()).isPresent();
                    var decoded = Recipe.CONDITIONAL_CODEC.parse(ops, json);
                    assertThat(decoded.error()).isEmpty();
                    assertThat(decoded.getOrThrow()).isEmpty();
                }
            } finally {
                instance.set(null, previousMods);
            }
        } finally {
            bridge.set(null, previousBridge);
            holder.set(ModBlocks.BASIC_CASING, previousCasing);
            holder.set(ModItems.MODULARIUM, previousModularium);
            ModBlocks.BLOCKS.clear();
            ModBlocks.BLOCKS.putAll(previousBlocks);
            previousBlockItems.forEach((block, item) -> {
                if (item == null) Item.BY_BLOCK.remove(block);
                else Item.BY_BLOCK.put(block, item);
            });
        }
    }

    @Test
    void actualBlockAndItemTagProvidersDeclareOptionalEnergyPortsWithoutCrossingDirections() throws Exception {
        var lookup = CompletableFuture.completedFuture(registries);
        var blocks = new ModBlockTags(OUTPUT, lookup);
        var items = new ModItemTags(OUTPUT, lookup, CompletableFuture.completedFuture(TagsProvider.TagLookup.empty()));
        var blockPorts = ModBlockTags.class.getDeclaredMethod("addPortTags", IOPortKind.class);
        var itemPorts = ModItemTags.class.getDeclaredMethod("addPortTags", IOPortKind.class);
        blockPorts.setAccessible(true);
        itemPorts.setAccessible(true);
        for (var kind : FluxNetworkInterfaceKind.values()) {
            blockPorts.invoke(blocks, kind);
            itemPorts.invoke(items, kind);
            PortTagSet tags = PortTagSet.forKind(kind);
            assertThat(tags.optionalEntries()).isTrue();
            assertThat(tags.tags()).containsExactly(MMCR.id("ports"), MMCR.id("machines"), MMCR.id("neoforge_energy_ports"),
                    MMCR.id("neoforge_energy_" + kind.ioType().getSerializedName() + "_ports"), MMCR.id("fluxnetworks_ports"));
        }
        Field builders = TagsProvider.class.getDeclaredField("builders");
        builders.setAccessible(true);
        for (var provider : List.of(blocks, items)) {
            @SuppressWarnings("unchecked")
            Map<ResourceLocation, TagBuilder> tags = (Map<ResourceLocation, TagBuilder>) builders.get(provider);
            for (var kind : FluxNetworkInterfaceKind.values()) {
                ResourceLocation id = MMCR.id(kind.id());
                for (ResourceLocation tag : PortTagSet.forKind(kind).tags()) {
                    List<TagEntry> entries = tags.get(tag).build();
                    assertThat(entries.stream().filter(entry -> entry.getId().equals(id)).toList())
                            .singleElement().satisfies(entry -> {
                                assertThat(entry.isRequired()).isFalse();
                                assertThat(entry.isTag()).isFalse();
                                assertThat(entry.verifyIfPresent(missing -> false, missing -> false)).isTrue();
                            });
                }
                String opposite = kind == FluxNetworkInterfaceKind.INPUT ? "output" : "input";
                assertThat(tags.get(MMCR.id("neoforge_energy_" + opposite + "_ports")).build())
                        .noneMatch(entry -> entry.getId().equals(id));
            }
        }
    }

    @Test
    void tooltipsUseDirectionalTranslationKeysAvailableInBothLanguages() throws Exception {
        for (var kind : FluxNetworkInterfaceKind.values()) {
            List<String> keys = InterfaceTooltips.tooltipLines(fixtureBlock(kind)).stream()
                    .map(component -> ((TranslatableContents) component.getContents()).getKey()).toList();
            assertThat(keys).containsExactly("tooltip.mmcr.fluxnetworks." + kind.ioType().getSerializedName(),
                    "tooltip.mmcr.fluxnetworks.configure");
            for (String language : List.of("en_us", "zh_cn")) {
                try (var input = getClass().getResourceAsStream("/assets/mmcr/lang/" + language + ".json")) {
                    assertThat(input).isNotNull();
                    JsonObject translations = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
                    assertThat(translations.get("block.mmcr." + kind.id()).getAsString()).isNotBlank();
                    keys.forEach(key -> assertThat(translations.get(key).getAsString()).isNotBlank());
                }
            }
        }
    }

    @Test
    void unavailableBridgeRejectsNativeLootGeneration() {
        assertThatThrownBy(() -> FluxNetworksBridgeBootstrap.selectForTesting(false).deviceLoot(Blocks.STONE))
                .isInstanceOf(IllegalStateException.class).hasMessage("Flux Networks is not loaded");
    }

    private static Map<Path, JsonObject> generateLoot() {
        List<ResourceLocation> ids = new ArrayList<>(LOOT_MODS.keySet().stream()
                .map(port -> MMCR.id("blocks/" + port)).toList());
        ids.add(MMCR.id("blocks/energy_input_hatch_tiny"));
        ids.add(ResourceLocation.fromNamespaceAndPath("other", "blocks/" + FluxNetworksIds.INPUT));
        var tables = new LootTableProvider.SubProviderEntry(lookup -> consumer -> ids.forEach(id ->
                consumer.accept(ResourceKey.create(Registries.LOOT_TABLE, id), LootTable.lootTable().withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1)).add(LootItem.lootTableItem(Items.STONE))
                        .when(ExplosionCondition.survivesExplosion())))), LootContextParamSets.BLOCK);
        Map<Path, JsonObject> generated = new ConcurrentHashMap<>();
        CachedOutput output = (path, bytes, hash) -> {
            assertThat(hash).isEqualTo(Hashing.sha1().hashBytes(bytes));
            assertThat(generated.put(path, JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject())).isNull();
        };
        new LootTableGen(OUTPUT, CompletableFuture.completedFuture(registries), List.of(tables)).run(output).join();
        assertThat(generated).hasSize(ids.size());
        return generated;
    }

    private static Path lootPath(ResourceLocation id) {
        return OUTPUT.createRegistryElementsPathProvider(Registries.LOOT_TABLE).json(id);
    }

    private static ResourceLocation fixtureId(FluxNetworkInterfaceKind kind) {
        return ResourceLocation.fromNamespaceAndPath("mmcr_test", "flux_datagen_" + kind.id());
    }

    private static Block fixtureBlock(FluxNetworkInterfaceKind kind) {
        MappedRegistry<Block> blocks = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        ResourceLocation id = fixtureId(kind);
        if (blocks.containsKey(id)) return blocks.get(id);
        blocks.unfreeze();
        try {
            return Registry.register(blocks, id, kind.createBlock(Blocks.IRON_BLOCK.properties(), () -> BlockEntityType.CHEST));
        } finally {
            blocks.freeze();
        }
    }

    private static Item fixtureItem(ResourceLocation id, Block block) {
        MappedRegistry<Item> items = (MappedRegistry<Item>) BuiltInRegistries.ITEM;
        if (items.containsKey(id)) return items.get(id);
        items.unfreeze();
        try {
            return Registry.register(items, id, block == null ? new Item(new Item.Properties()) : new BlockItem(block, new Item.Properties()));
        } finally {
            items.freeze();
        }
    }

    /** Captures real RecipeOutput builders through their recipe and condition codecs.
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
        public Advancement.Builder advancement() {
            return Advancement.Builder.recipeAdvancement();
        }
    }
}
