package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.ars_nouveau.ArsSourceIds;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Data generator entry point for MMCR block loot tables.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class LootTableGen extends LootTableProvider {
    private final PackOutput output;
    private final CompletableFuture<HolderLookup.Provider> registries;

    public LootTableGen(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        this(output, registries, List.of(new SubProviderEntry(BlockLoot::new, LootContextParamSets.BLOCK)));
    }

    LootTableGen(PackOutput output, CompletableFuture<HolderLookup.Provider> registries,
                 List<SubProviderEntry> tables) {
        super(output, Set.of(), tables, registries);
        this.output = output;
        this.registries = registries;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cachedOutput) {
        var paths = output.createRegistryElementsPathProvider(Registries.LOOT_TABLE);
        Set<Path> sourceTables = Set.of(paths.json(MMCR.id("blocks/" + ArsSourceIds.INPUT)),
                paths.json(MMCR.id("blocks/" + ArsSourceIds.OUTPUT)));
        Map<Path, JsonObject> conditionedTables = new ConcurrentHashMap<>();
        // Keep vanilla generation, random sequences and validation. Its 1.21.1 loot
        // provider has no conditional output hook, unlike RecipeOutput.withConditions.
        CachedOutput collectingOutput = (path, bytes, hash) -> {
            if (sourceTables.contains(path)) {
                conditionedTables.put(path, JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject());
            } else {
                cachedOutput.writeIfNeeded(path, bytes, hash);
            }
        };
        return super.run(collectingOutput).thenCompose(unused -> registries.thenCompose(lookup -> {
            CompletableFuture<?>[] saves = conditionedTables.entrySet().stream().map(entry -> {
                ICondition.writeConditions(lookup, entry.getValue(), new ModLoadedCondition("ars_nouveau"));
                return DataProvider.saveStable(cachedOutput, entry.getValue(), entry.getKey());
            }).toArray(CompletableFuture[]::new);
            return CompletableFuture.allOf(saves);
        }));
    }
}
