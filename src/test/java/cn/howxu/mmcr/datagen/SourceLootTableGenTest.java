package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.ars_nouveau.ArsSourceIds;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
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
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises real provider output and the runtime conditional loot codec without an Ars registry.
 *
 * @author howxu <dev@howxu.cn>
 */
class SourceLootTableGenTest {
    private static final PackOutput OUTPUT = new PackOutput(Path.of("source-loot-test"));
    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        var serializers = NeoForgeRegistries.CONDITION_SERIALIZERS;
        if (!serializers.containsKey(ResourceLocation.fromNamespaceAndPath("neoforge", "mod_loaded"))) {
            var mutable = (MappedRegistry<?>) serializers;
            Field frozenField = MappedRegistry.class.getDeclaredField("frozen");
            frozenField.setAccessible(true);
            boolean frozen = frozenField.getBoolean(mutable);
            mutable.unfreeze();
            Registry.register(serializers, ResourceLocation.fromNamespaceAndPath("neoforge", "mod_loaded"), ModLoadedCondition.CODEC);
            if (frozen) mutable.freeze();
        }
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    @Test
    void outputAddsArsConditionsOnlyToTheTwoSourceTablesAndKeepsSelfDrops() {
        Map<Path, JsonObject> generated = generate();
        for (String port : List.of(ArsSourceIds.INPUT, ArsSourceIds.OUTPUT)) {
            JsonObject table = generated.get(path(MMCR.id("blocks/" + port)));
            assertThat(ICondition.LIST_CODEC.parse(JsonOps.INSTANCE, table.get(ConditionalOps.DEFAULT_CONDITIONS_KEY)).getOrThrow())
                    .containsExactly(new ModLoadedCondition("ars_nouveau"));
            assertThat(LootTable.DIRECT_CODEC.parse(JsonOps.INSTANCE, table).error()).isEmpty();
            assertThat(itemEntry(table).get("name").getAsString()).isEqualTo("minecraft:stone");
            assertThat(table.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("conditions")
                    .get(0).getAsJsonObject().get("condition").getAsString()).isEqualTo("minecraft:survives_explosion");
            assertThat(table.get("random_sequence").getAsString()).isEqualTo("mmcr:blocks/" + port);
        }
        assertThat(generated.get(path(MMCR.id("blocks/ae2_me_input_interface"))).has(ConditionalOps.DEFAULT_CONDITIONS_KEY)).isFalse();
        assertThat(generated.get(path(ResourceLocation.fromNamespaceAndPath("other", "blocks/" + ArsSourceIds.INPUT)))
                .has(ConditionalOps.DEFAULT_CONDITIONS_KEY)).isFalse();
    }

    @Test
    void absentArsShortCircuitsMissingPortItemParsingInTheActualLootDataCodec() throws Exception {
        Map<Path, JsonObject> generated = generate();
        Field instance = ModList.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        Object previous = instance.get(null);
        try {
            ModList mods = ModList.of(List.of(), List.of());
            Field indexedMods = ModList.class.getDeclaredField("indexedMods");
            indexedMods.setAccessible(true);
            indexedMods.set(mods, Map.of());
            for (String port : List.of(ArsSourceIds.INPUT, ArsSourceIds.OUTPUT)) {
                assertThat(BuiltInRegistries.ITEM.containsKey(MMCR.id(port))).isFalse();
                JsonObject table = generated.get(path(MMCR.id("blocks/" + port))).deepCopy();
                // The stand-in self-drop lets generation run without registering optional ports;
                // substitute the missing real port ID only for the runtime decoding scenario.
                itemEntry(table).addProperty("name", MMCR.id(port).toString());
                assertThat(LootTable.DIRECT_CODEC.parse(JsonOps.INSTANCE, table).error()).isPresent();
                var decoded = LootDataType.TABLE.conditionalCodec().parse(JsonOps.INSTANCE, table);
                assertThat(decoded.error()).isEmpty();
                assertThat(decoded.getOrThrow()).isEmpty();
                assertThat(LootDataType.TABLE.deserialize(MMCR.id("blocks/" + port), JsonOps.INSTANCE, table)).contains(LootTable.EMPTY);
            }
        } finally {
            instance.set(null, previous);
        }
    }

    private static Map<Path, JsonObject> generate() {
        List<ResourceLocation> ids = List.of(MMCR.id("blocks/" + ArsSourceIds.INPUT), MMCR.id("blocks/" + ArsSourceIds.OUTPUT),
                MMCR.id("blocks/ae2_me_input_interface"), ResourceLocation.fromNamespaceAndPath("other", "blocks/" + ArsSourceIds.INPUT));
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

    private static Path path(ResourceLocation id) {
        return OUTPUT.createRegistryElementsPathProvider(Registries.LOOT_TABLE).json(id);
    }

    private static JsonObject itemEntry(JsonObject table) {
        return table.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
    }
}
