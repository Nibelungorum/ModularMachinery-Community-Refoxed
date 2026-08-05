package cn.howxu.mmcr;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.RecipeAccess;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.material.Fluid;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class LevelStub {

    private LevelStub() {}

    public static Level create(Block block, int w, int h, int l, BlockPos origin) {
        Map<BlockPos, BlockState> map = new HashMap<>();
        var state = block.defaultBlockState();
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                for (int z = 0; z < l; z++)
                    map.put(origin.offset(x, y, z), state);
        return createFromStates(map);
    }

    public static Level create(Map<BlockPos, Block> blocks) {
        Map<BlockPos, BlockState> map = new HashMap<>();
        for (var entry : blocks.entrySet()) map.put(entry.getKey(), entry.getValue().defaultBlockState());
        return createFromStates(map);
    }

    public static Level createWithBlockEntities(Map<BlockPos, BlockEntity> blockEntities) {
        try {
            var level = (TestLevel) unsafe().allocateInstance(TestLevel.class);
            level.blocks = Map.of();
            level.blockEntities = Map.copyOf(blockEntities);
            for (BlockEntity blockEntity : blockEntities.values()) {
                blockEntity.setLevel(level);
            }
            return level;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to create level stub", e);
        }
    }

    private static Level createFromStates(Map<BlockPos, BlockState> blocks) {
        try {
            var level = (TestLevel) unsafe().allocateInstance(TestLevel.class);
            level.blocks = Map.copyOf(blocks);
            level.blockEntities = Map.of();
            return level;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to create level stub", e);
        }
    }

    private static Unsafe unsafe() throws ReflectiveOperationException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static final class TestLevel extends Level {
        private Map<BlockPos, BlockState> blocks;
        private Map<BlockPos, BlockEntity> blockEntities;

        private TestLevel() {
            super(null, Level.OVERWORLD, null, null, false, false, 0L, 0);
        }

        @Override public BlockState getBlockState(BlockPos pos) {
            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override public BlockEntity getBlockEntity(BlockPos pos) {
            return blockEntities.get(pos);
        }

        @Override public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {}
        @Override public void playSeededSound(Entity entity, double x, double y, double z, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed) {}
        @Override public void playSeededSound(Entity sourceEntity, Entity entity, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed) {}
        @Override public void explode(Entity source, DamageSource damageSource, ExplosionDamageCalculator calculator, double x, double y, double z, float radius, boolean fire, ExplosionInteraction interaction, ParticleOptions smallExplosionParticles, ParticleOptions largeExplosionParticles, WeightedList<net.minecraft.core.particles.ExplosionParticleInfo> blockInteractionParticles, Holder<SoundEvent> explosionSound) {}
        @Override public String gatherChunkSourceStats() { return "LevelStub"; }
        @Override public void setRespawnData(LevelData.RespawnData respawnData) {}
        @Override public LevelData.RespawnData getRespawnData() { return null; }
        @Override public Entity getEntity(int id) { return null; }
        @Override public Collection<EnderDragonPart> dragonParts() { return List.of(); }
        @Override public TickRateManager tickRateManager() { return null; }
        @Override public MapItemSavedData getMapData(MapId mapId) { return null; }
        @Override public void destroyBlockProgress(int breakerId, BlockPos pos, int progress) {}
        @Override public Scoreboard getScoreboard() { return null; }
        @Override public RecipeAccess recipeAccess() { return null; }
        @Override public ClockManager clockManager() { return null; }
        @Override public EnvironmentAttributeSystem environmentAttributes() { return null; }
        @Override public PotionBrewing potionBrewing() { return null; }
        @Override public FuelValues fuelValues() { return null; }
        @Override public MinecraftServer getServer() { return null; }
        @Override public ChunkSource getChunkSource() { return null; }
        @Override protected LevelEntityGetter<Entity> getEntities() { return null; }
        @Override public WorldBorder getWorldBorder() { return new WorldBorder(); }
        @Override public void levelEvent(Entity entity, int type, BlockPos pos, int data) {}
        @Override public void gameEvent(Holder<GameEvent> event, Vec3 pos, GameEvent.Context context) {}
        @Override public <T> ScheduledTick<T> createTick(BlockPos pos, T value, int delay, TickPriority priority) { return null; }
        @Override public <T> ScheduledTick<T> createTick(BlockPos pos, T value, int delay) { return null; }
        @Override public LevelTickAccess<Block> getBlockTicks() { return null; }
        @Override public LevelTickAccess<Fluid> getFluidTicks() { return null; }
        @Override public boolean hasChunk(int chunkX, int chunkZ) { return true; }
        @Override public int getSeaLevel() { return 0; }
        @Override public FeatureFlagSet enabledFeatures() { return FeatureFlagSet.of(); }
        @Override public Holder<net.minecraft.world.level.biome.Biome> getUncachedNoiseBiome(int x, int y, int z) { return null; }
        @Override public List<net.minecraft.world.entity.Entity> getEntities(Entity entity, AABB area, Predicate<? super Entity> predicate) { return List.of(); }
        @Override public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> typeTest, AABB area, Predicate<? super T> predicate) { return List.of(); }
        @Override public List<? extends Player> players() { return List.of(); }
        @Override public List<VoxelShape> getEntityCollisions(Entity entity, AABB area) { return List.of(); }
        @Override public BlockPos getHeightmapPos(Heightmap.Types type, BlockPos pos) { return pos; }
    }
}
