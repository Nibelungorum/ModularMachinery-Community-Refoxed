package cn.howxu.mmcr.client.preview.world;

import cn.howxu.mmcr.internal.preview.MultiblockPreviewSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
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
    private final BlockModelShaper blockModels;
    private final LiquidBlockRenderer fluidModels;
    private final BlockColors blockColors;

    private WorldPreviewCompileInput(BlockAndTintGetter region, BlockModelShaper blockModels,
            LiquidBlockRenderer fluidModels, BlockColors blockColors) {
        this.region = region;
        this.blockModels = blockModels;
        this.fluidModels = fluidModels;
        this.blockColors = blockColors;
    }

    public static WorldPreviewCompileInput capture(Level level, BlockPos controllerPos,
            List<MultiblockPreviewSnapshot.Entry> entries,
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
                level.getMinBuildHeight(), level.getHeight(),
                level.getLightEngine()), minecraft.getBlockRenderer().getBlockModelShaper(),
                minecraft.getBlockRenderer().getLiquidBlockRenderer(), minecraft.getBlockColors());
    }

    BlockAndTintGetter region() { return region; }
    BlockModelShaper blockModels() { return blockModels; }
    LiquidBlockRenderer fluidModels() { return fluidModels; }
    BlockColors blockColors() { return blockColors; }

    private record SnapshotRegion(Map<Long, BlockState> states, Map<Long, Biome> biomes, Biome defaultBiome, int minY,
                                  int height, LevelLightEngine lightEngine) implements BlockAndTintGetter {
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
                return states.getOrDefault(position.asLong(), Blocks.AIR.defaultBlockState());
            }

            @Override
            public FluidState getFluidState(BlockPos position) {
                return getBlockState(position).getFluidState();
            }

            @Override
            public BlockEntity getBlockEntity(BlockPos position) {
                return null;
            }

            @Override
            public int getHeight() {
                return height;
            }

            @Override
            public int getMinBuildHeight() {
                return minY;
            }

            @Override
            public int getBrightness(LightLayer lightLayer, BlockPos position) {
                return WorldPreviewMeshCompiler.FULL_BRIGHT_LEVEL;
            }

            @Override
            public LevelLightEngine getLightEngine() {
                return lightEngine;
            }

            @Override
            public float getShade(net.minecraft.core.Direction direction, boolean shade) {
                return 1.0F;
            }

            @Override
            public int getBlockTint(BlockPos position, ColorResolver resolver) {
                return resolver.getColor(biomes.getOrDefault(position.asLong(), defaultBiome),
                        position.getX(), position.getZ());
            }
        }
}
