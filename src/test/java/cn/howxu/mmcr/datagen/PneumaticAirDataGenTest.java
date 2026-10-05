package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.pneumaticcraft.AirFailureReasons;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticIds;
import cn.howxu.mmcr.compat.pneumaticcraft.loaded.AirInterfaceKind;
import cn.howxu.mmcr.internal.item.InterfaceTooltips;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
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
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagBuilder;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises real air recipe, tag and self-drop providers without creating native air handlers.
 *
 * @author howxu <dev@howxu.cn>
 */
class PneumaticAirDataGenTest {
    private static final PackOutput OUTPUT = new PackOutput(Path.of("pneumatic-air-datagen-test"));
    private static HolderLookup.Provider registries;
    private RegistryFixture fixture;

    @BeforeEach
    void snapshotRegistries() throws Exception {
        fixture = new RegistryFixture();
    }

    @AfterEach
    void restoreRegistries() throws Exception {
        if (fixture != null) fixture.close();
    }

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrapCapabilities();
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
    void shapedRecipesReverseTubeAndCasingAndCarryThePneumaticCraftCondition() throws Exception {
        var previousItems = new LinkedHashMap<>(ModItems.ITEMS);
        Field holder = DeferredHolder.class.getDeclaredField("holder");
        holder.setAccessible(true);
        Object previousCasing = holder.get(ModBlocks.BASIC_CASING);
        try (var fixture = new RegistryFixture()) {
            holder.set(ModBlocks.BASIC_CASING, Holder.direct(Blocks.IRON_BLOCK));
            ModItems.ITEMS.remove(PneumaticIds.INPUT);
            ModItems.ITEMS.remove(PneumaticIds.OUTPUT);
            CapturedRecipes output = new CapturedRecipes();
            ModRecipeProvider provider = new ModRecipeProvider(OUTPUT, CompletableFuture.completedFuture(registries));
            Field recipeOutput = ModRecipeProvider.class.getDeclaredField("output");
            recipeOutput.setAccessible(true);
            recipeOutput.set(provider, output);
            var generate = ModRecipeProvider.class.getDeclaredMethod("pneumaticAirInterfaceRecipes");
            generate.setAccessible(true);
            // With no optional registrations the provider must not dereference any external items.
            generate.invoke(provider);
            assertThat(output.recipes).isEmpty();

            fixtureItem(PneumaticIds.ADVANCED_PRESSURE_TUBE, null);
            for (var kind : AirInterfaceKind.values()) {
                fixtureItem(fixtureId(kind), fixtureBlock(kind));
                ModItems.ITEMS.put(kind.id(), DeferredHolder.create(Registries.ITEM, fixtureId(kind)));
            }
            generate.invoke(provider);
            assertThat(output.recipes.keySet()).containsExactlyInAnyOrder(
                    fixtureId(AirInterfaceKind.INPUT), fixtureId(AirInterfaceKind.OUTPUT));
            var ops = registries.createSerializationContext(JsonOps.INSTANCE);
            for (var kind : AirInterfaceKind.values()) {
                JsonObject json = output.recipes.get(fixtureId(kind));
                assertThat(ICondition.LIST_CODEC.parse(ops, json.get(ConditionalOps.DEFAULT_CONDITIONS_KEY)).getOrThrow())
                        .containsExactly(new ModLoadedCondition(PneumaticIds.MOD_ID));
                ShapedRecipe recipe = (ShapedRecipe) Recipe.CODEC.parse(ops, json).getOrThrow();
                assertThat(json.getAsJsonArray("pattern")).containsExactly(
                        new JsonPrimitive("A"), new JsonPrimitive("B"));
                boolean input = kind == AirInterfaceKind.INPUT;
                assertThat(json.getAsJsonObject("key").getAsJsonObject("A").get("item").getAsString())
                        .isEqualTo(input ? PneumaticIds.ADVANCED_PRESSURE_TUBE.toString() : "minecraft:iron_block");
                assertThat(json.getAsJsonObject("key").getAsJsonObject("B").get("item").getAsString())
                        .isEqualTo(input ? "minecraft:iron_block" : PneumaticIds.ADVANCED_PRESSURE_TUBE.toString());
                assertThat(recipe.getIngredients().get(0).test(input
                        ? BuiltInRegistries.ITEM.get(PneumaticIds.ADVANCED_PRESSURE_TUBE).getDefaultInstance()
                        : Items.IRON_BLOCK.getDefaultInstance())).isTrue();
                assertThat(json.getAsJsonObject("result").get("id").getAsString()).isEqualTo(fixtureId(kind).toString());
            }
            assertMissingModSkipsUnknownRecipeItems(output);
        } finally {
            holder.set(ModBlocks.BASIC_CASING, previousCasing);
            ModItems.ITEMS.clear();
            ModItems.ITEMS.putAll(previousItems);
        }
    }

    @Test
    void realBlockAndItemProvidersUseOptionalFamilyAndDirectionTags() throws Exception {
        var lookup = CompletableFuture.completedFuture(registries);
        var blocks = new ModBlockTags(OUTPUT, lookup);
        var items = new ModItemTags(OUTPUT, lookup, CompletableFuture.completedFuture(TagsProvider.TagLookup.empty()));
        var blockPorts = ModBlockTags.class.getDeclaredMethod("addPortTags", IOPortKind.class);
        var itemPorts = ModItemTags.class.getDeclaredMethod("addPortTags", IOPortKind.class);
        blockPorts.setAccessible(true);
        itemPorts.setAccessible(true);
        for (var kind : AirInterfaceKind.values()) {
            blockPorts.invoke(blocks, kind);
            itemPorts.invoke(items, kind);
            assertThat(PortTagSet.forKind(kind).tags()).containsExactly(MMCR.id("ports"), MMCR.id("machines"),
                    MMCR.id("pneumaticcraft_air_ports"),
                    MMCR.id("pneumaticcraft_air_" + kind.ioType().getSerializedName() + "_ports"),
                    MMCR.id("pneumaticcraft_ports"));
        }
        Field builders = TagsProvider.class.getDeclaredField("builders");
        builders.setAccessible(true);
        for (var provider : List.of(blocks, items)) {
            @SuppressWarnings("unchecked")
            Map<ResourceLocation, TagBuilder> tags = (Map<ResourceLocation, TagBuilder>) builders.get(provider);
            for (var kind : AirInterfaceKind.values()) {
                ResourceLocation id = MMCR.id(kind.id());
                for (ResourceLocation tag : PortTagSet.forKind(kind).tags()) {
                    assertThat(tags.get(tag).build().stream().filter(entry -> entry.getId().equals(id)).toList())
                            .singleElement().satisfies(entry -> {
                                assertThat(entry.isRequired()).isFalse();
                                assertThat(entry.isTag()).isFalse();
                                assertThat(entry.verifyIfPresent(missing -> false, missing -> false)).isTrue();
                            });
                }
                String opposite = kind == AirInterfaceKind.INPUT ? "output" : "input";
                assertThat(tags.get(MMCR.id("pneumaticcraft_air_" + opposite + "_ports")).build())
                        .noneMatch(entry -> entry.getId().equals(id));
            }
        }
    }

    @Test
    void defaultLootProviderConditionsRealPortSelfDropsAndSkipsMissingItemsWithoutPneumaticCraft() throws Exception {
        var previousBlocks = new LinkedHashMap<>(ModBlocks.BLOCKS);
        Map<Path, JsonObject> generated = new ConcurrentHashMap<>();
        try (var fixture = new RegistryFixture()) {
            ModBlocks.BLOCKS.clear();
            for (var kind : AirInterfaceKind.values()) {
                ResourceLocation id = MMCR.id(kind.id());
                Block block = fixtureBlock(kind, id);
                Item.BY_BLOCK.put(block, fixtureItem(id, block));
                ModBlocks.BLOCKS.put(kind.id(), DeferredHolder.create(Registries.BLOCK, id));
            }
            CachedOutput output = (path, bytes, hash) -> generated.put(path,
                    JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject());
            new LootTableGen(OUTPUT, CompletableFuture.completedFuture(registries)).run(output).join();
            assertThat(generated).hasSize(AirInterfaceKind.values().length);
            for (var kind : AirInterfaceKind.values()) {
                ResourceLocation tableId = MMCR.id("blocks/" + kind.id());
                JsonObject table = generated.get(OUTPUT.createRegistryElementsPathProvider(Registries.LOOT_TABLE).json(tableId));
                assertThat(table).isNotNull();
                assertThat(ICondition.LIST_CODEC.parse(JsonOps.INSTANCE, table.get(ConditionalOps.DEFAULT_CONDITIONS_KEY)).getOrThrow())
                        .containsExactly(new ModLoadedCondition(PneumaticIds.MOD_ID));
                assertThat(LootTable.DIRECT_CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), table).error()).isEmpty();
                JsonObject pool = table.getAsJsonArray("pools").get(0).getAsJsonObject();
                JsonObject entry = pool.getAsJsonArray("entries").get(0).getAsJsonObject();
                assertThat(entry.get("type").getAsString()).isEqualTo("minecraft:item");
                assertThat(entry.get("name").getAsString()).isEqualTo(MMCR.id(kind.id()).toString());
                assertThat(pool.getAsJsonArray("conditions").get(0).getAsJsonObject().get("condition").getAsString())
                        .isEqualTo("minecraft:survives_explosion");
            }
        } finally {
            ModBlocks.BLOCKS.clear();
            ModBlocks.BLOCKS.putAll(previousBlocks);
        }
        assertMissingModSkipsUnknownLootItems(generated);
    }

    private static void assertMissingModSkipsUnknownLootItems(Map<Path, JsonObject> generated) throws Exception {
        Field instance = ModList.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        Object previous = instance.get(null);
        try {
            ModList mods = ModList.of(List.of(), List.of());
            Field indexedMods = ModList.class.getDeclaredField("indexedMods");
            indexedMods.setAccessible(true);
            indexedMods.set(mods, Map.of());
            var ops = registries.createSerializationContext(JsonOps.INSTANCE);
            for (var kind : AirInterfaceKind.values()) {
                ResourceLocation itemId = MMCR.id(kind.id());
                ResourceLocation tableId = MMCR.id("blocks/" + kind.id());
                assertThat(BuiltInRegistries.ITEM.containsKey(itemId)).isFalse();
                JsonObject table = generated.get(OUTPUT.createRegistryElementsPathProvider(Registries.LOOT_TABLE).json(tableId));
                assertThat(LootTable.DIRECT_CODEC.parse(ops, table).error()).isPresent();
                var decoded = LootDataType.TABLE.conditionalCodec().parse(ops, table);
                assertThat(decoded.error()).isEmpty();
                assertThat(decoded.getOrThrow()).isEmpty();
                // RegistryOps invokes the NeoForge load hook, which removes the empty skipped table.
                assertThat(LootDataType.TABLE.deserialize(tableId, ops, table)).isEmpty();
            }
        } finally {
            instance.set(null, previous);
        }
    }

    @Test
    void actualDirectionalTooltipsAndFailureReasonsResolveInBothLanguages() throws Exception {
        List<String> sharedKeys = new ArrayList<>(List.of("requirement.pneumaticcraft:air",
                "requirement.pneumaticcraft:air.description", "jei.mmcr.machine_recipe.air_in",
                "jei.mmcr.machine_recipe.air_condition", "jei.mmcr.machine_recipe.air_out",
                "jei.mmcr.machine_recipe.air_in.tooltip", "jei.mmcr.machine_recipe.air_condition.tooltip",
                "jei.mmcr.machine_recipe.air_out.tooltip"));
        List.of(AirFailureReasons.UNAVAILABLE, AirFailureReasons.MISSING_INTERFACE,
                        AirFailureReasons.INSUFFICIENT_PRESSURE, AirFailureReasons.INSUFFICIENT_AIR, AirFailureReasons.OUTPUT_BLOCKED)
                .forEach(reason -> sharedKeys.add(reason.translationKey()));
        for (String language : List.of("en_us", "zh_cn")) {
            try (var input = getClass().getResourceAsStream("/assets/mmcr/lang/" + language + ".json")) {
                assertThat(input).isNotNull();
                JsonObject translations = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
                sharedKeys.forEach(key -> assertThat(translations.get(key).getAsString()).isNotBlank());
                assertThat(String.format(Locale.ROOT, translations.get("jei.mmcr.machine_recipe.air_in").getAsString(), 40, 4))
                        .contains("40", "4").doesNotContain("%s");
                assertThat(String.format(Locale.ROOT, translations.get("jei.mmcr.machine_recipe.air_condition").getAsString(), 4))
                        .contains("4").doesNotContain("%s");
                assertThat(String.format(Locale.ROOT, translations.get("jei.mmcr.machine_recipe.air_out").getAsString(), 80))
                        .contains("80").doesNotContain("%s");
                assertThat(String.format(Locale.ROOT, translations.get("jei.mmcr.machine_recipe.air_in.tooltip").getAsString(), 40, 4))
                        .contains("40", "4").doesNotContain("%s");
                assertThat(String.format(Locale.ROOT, translations.get("jei.mmcr.machine_recipe.air_condition.tooltip").getAsString(), 4))
                        .contains("4").doesNotContain("%s");
                assertThat(String.format(Locale.ROOT, translations.get("jei.mmcr.machine_recipe.air_out.tooltip").getAsString(), 80))
                        .contains("80").doesNotContain("%s");
                for (var kind : AirInterfaceKind.values()) {
                    assertThat(translations.get("block.mmcr." + kind.id()).getAsString()).isNotBlank();
                    var lines = InterfaceTooltips.tooltipLines(fixtureBlock(kind)).stream()
                            .map(component -> (TranslatableContents) component.getContents()).toList();
                    assertThat(lines.stream().map(TranslatableContents::getKey).toList()).containsExactly(
                            "tooltip.mmcr.pneumaticcraft." + kind.ioType().getSerializedName(),
                            "tooltip.mmcr.pneumaticcraft.volume", "tooltip.mmcr.pneumaticcraft.safe_pressure",
                            "tooltip.mmcr.pneumaticcraft.equalization");
                    for (var line : lines) {
                        String format = translations.get(line.getKey()).getAsString();
                        assertThat(format).isNotBlank();
                        assertThat(String.format(Locale.ROOT, format, line.getArgs())).doesNotContain("%s");
                    }
                    assertThat(lines.get(1).getArgs()).containsExactly(10_000);
                    assertThat(lines.get(2).getArgs()).containsExactly(20);
                }
            }
        }
    }

    private static void assertMissingModSkipsUnknownRecipeItems(CapturedRecipes output) throws Exception {
        Field instance = ModList.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        Object previous = instance.get(null);
        try {
            ModList mods = ModList.of(List.of(), List.of());
            Field indexedMods = ModList.class.getDeclaredField("indexedMods");
            indexedMods.setAccessible(true);
            indexedMods.set(mods, Map.of());
            var ops = registries.createSerializationContext(JsonOps.INSTANCE);
            for (JsonObject recipe : output.recipes.values()) {
                JsonObject missingItems = recipe.deepCopy();
                missingItems.getAsJsonObject("result").addProperty("id", "mmcr_test:missing_air_result");
                missingItems.getAsJsonObject("key").getAsJsonObject("A").addProperty("item", "mmcr_test:missing_air_ingredient");
                assertThat(Recipe.CODEC.parse(ops, missingItems).error()).isPresent();
                var decoded = Recipe.CONDITIONAL_CODEC.parse(ops, missingItems);
                assertThat(decoded.error()).isEmpty();
                assertThat(decoded.getOrThrow()).isEmpty();
            }
        } finally {
            instance.set(null, previous);
        }
    }

    private static ResourceLocation fixtureId(AirInterfaceKind kind) {
        return ResourceLocation.fromNamespaceAndPath("mmcr_test", "air_datagen_" + kind.id());
    }

    private static Block fixtureBlock(AirInterfaceKind kind) {
        return fixtureBlock(kind, fixtureId(kind));
    }

    private static Block fixtureBlock(AirInterfaceKind kind, ResourceLocation id) {
        MappedRegistry<Block> blocks = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        if (blocks.containsKey(id)) return blocks.get(id);
        blocks.unfreeze();
        Block block;
        try {
            block = Registry.register(blocks, id, kind.createBlock(Blocks.IRON_BLOCK.properties(),
                    () -> BuiltInRegistries.BLOCK_ENTITY_TYPE.get(id)));
        } finally {
            blocks.freeze();
        }
        MappedRegistry<BlockEntityType<?>> types = (MappedRegistry<BlockEntityType<?>>) BuiltInRegistries.BLOCK_ENTITY_TYPE;
        types.unfreeze();
        try {
            Registry.register(types, id, BlockEntityType.Builder.of(
                    (pos, state) -> new FixturePort(BuiltInRegistries.BLOCK_ENTITY_TYPE.get(id), pos, state), block).build(null));
        } finally {
            types.freeze();
        }
        return block;
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

    /** Native-free entity with a registered type that accepts the real air fixture block.
     * @author howxu <dev@howxu.cn>
     */
    private static final class FixturePort extends BlockEntity {
        private FixturePort(BlockEntityType<?> type, BlockPos pos, BlockState state) {
            super(type, pos, state);
        }
    }

    /** Restores temporary block/item/BE registrations so absent-mod tests remain independent of execution order.
     * @author howxu <dev@howxu.cn>
     */
    private static final class RegistryFixture implements AutoCloseable {
        private final Map<Object, Map<Field, Object>> fields = new IdentityHashMap<>();
        private final Map<Map<Object, Object>, Map<Object, Object>> maps = new IdentityHashMap<>();
        private final Map<List<Object>, List<Object>> lists = new IdentityHashMap<>();
        private final Map<Block, Item> blockItems = new IdentityHashMap<>(Item.BY_BLOCK);

        @SuppressWarnings("unchecked")
        private RegistryFixture() throws IllegalAccessException {
            for (var registry : List.of(BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM, BuiltInRegistries.BLOCK_ENTITY_TYPE)) {
                Map<Field, Object> values = new LinkedHashMap<>();
                fields.put(registry, values);
                for (Field field : MappedRegistry.class.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    Object value = field.get(registry);
                    values.put(field, value);
                    if (value instanceof Map<?, ?> map) {
                        maps.put((Map<Object, Object>) map, new LinkedHashMap<>((Map<Object, Object>) map));
                    } else if (value instanceof List<?> list) {
                        lists.put((List<Object>) list, new ArrayList<>((List<Object>) list));
                    }
                }
            }
        }

        @Override
        public void close() throws IllegalAccessException {
            maps.forEach((map, original) -> {
                if (!map.equals(original)) {
                    map.clear();
                    map.putAll(original);
                }
            });
            lists.forEach((list, original) -> {
                if (!list.equals(original)) {
                    list.clear();
                    list.addAll(original);
                }
            });
            for (var registry : fields.entrySet()) {
                for (var entry : registry.getValue().entrySet()) {
                    if (entry.getKey().get(registry.getKey()) != entry.getValue()) {
                        entry.getKey().set(registry.getKey(), entry.getValue());
                    }
                }
            }
            Item.BY_BLOCK.clear();
            Item.BY_BLOCK.putAll(blockItems);
        }
    }

    /** Captures the real recipe and condition codecs without writing generated output.
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
