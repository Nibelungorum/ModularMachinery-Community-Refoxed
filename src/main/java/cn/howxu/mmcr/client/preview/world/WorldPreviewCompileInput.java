package cn.howxu.mmcr.client.preview.world;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable client-thread snapshot consumed by the background mesh compiler.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class WorldPreviewCompileInput {
    private final BlockAndTintGetter region;
    private final BlockStateModelSet blockModels;
    private final FluidStateModelSet fluidModels;
    private final BlockColors blockColors;
    private final boolean ambientOcclusion;

    private WorldPreviewCompileInput(BlockAndTintGetter region, BlockStateModelSet blockModels,
            FluidStateModelSet fluidModels, BlockColors blockColors, boolean ambientOcclusion) {
        this.region = region;
        this.blockModels = blockModels;
        this.fluidModels = fluidModels;
        this.blockColors = blockColors;
        this.ambientOcclusion = ambientOcclusion;
    }

    public static WorldPreviewCompileInput capture(Level level, BlockPos controllerPos,
            List<cn.howxu.mmcr.internal.preview.MultiblockPreviewSnapshot.Entry> entries,
            int selectedLayer, Minecraft minecraft) {
        var plan = WorldPreviewMeshCompiler.plan(controllerPos, entries, selectedLayer);
        Map<Long, BlockState> states = new HashMap<>();
        Map<Long, Biome> biomes = new HashMap<>();
        Set<BlockPos> positions = new HashSet<>();
        for (var planned : plan.entries()) {
            BlockPos origin = planned.position();
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        positions.add(origin.offset(x, y, z));
                    }
                }
            }
        }
        positions.addAll(plan.entries().stream().map(WorldPreviewMeshCompiler.PlannedEntry::position).toList());
        for (BlockPos position : positions) {
            states.put(position.asLong(), level.getBlockState(position));
            Holder<Biome> biome = level.getBiome(position);
            biomes.put(position.asLong(), biome.value());
        }
        for (var planned : plan.entries()) states.put(planned.position().asLong(), planned.state());

        Biome defaultBiome = level.getBiome(controllerPos).value();
        return new WorldPreviewCompileInput(new SnapshotRegion(states, biomes, defaultBiome,
                level.getMinY(), level.getHeight(),
                level.getLightEngine()), minecraft.getModelManager().getBlockStateModelSet(),
                minecraft.getModelManager().getFluidStateModelSet(), minecraft.getBlockColors(),
                minecraft.options.ambientOcclusion().get());
    }

    BlockAndTintGetter region() { return region; }
    BlockStateModelSet blockModels() { return blockModels; }
    FluidStateModelSet fluidModels() { return fluidModels; }
    BlockColors blockColors() { return blockColors; }
    boolean ambientOcclusion() { return ambientOcclusion; }

    private static final class SnapshotRegion implements BlockAndTintGetter {
        private final Map<Long, BlockState> states;
        private final Map<Long, Biome> biomes;
        private final Biome defaultBiome;
        private final int minY;
        private final int height;
        private final LevelLightEngine lightEngine;

        private SnapshotRegion(Map<Long, BlockState> states, Map<Long, Biome> biomes, Biome defaultBiome,
                int minY, int height, LevelLightEngine lightEngine) {
            this.states = Map.copyOf(states);
            this.biomes = Map.copyOf(biomes);
            this.defaultBiome = defaultBiome;
            this.minY = minY;
            this.height = height;
            this.lightEngine = lightEngine;
        }

        @Override
        public BlockState getBlockState(BlockPos position) {
            return states.getOrDefault(position.asLong(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos position) {
            return getBlockState(position).getFluidState();
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos position) { return null; }

        @Override
        public int getHeight() { return height; }

        @Override
        public int getMinY() { return minY; }

        @Override
        public int getBrightness(LightLayer lightLayer, BlockPos position) {
            return WorldPreviewMeshCompiler.FULL_BRIGHT_LEVEL;
        }

        @Override
        public LevelLightEngine getLightEngine() { return lightEngine; }

        @Override
        public CardinalLighting cardinalLighting() { return CardinalLighting.DEFAULT; }

        @Override
        public int getBlockTint(BlockPos position, ColorResolver resolver) {
            return resolver.getColor(biomes.getOrDefault(position.asLong(), defaultBiome),
                    position.getX(), position.getZ());
        }
    }
}
