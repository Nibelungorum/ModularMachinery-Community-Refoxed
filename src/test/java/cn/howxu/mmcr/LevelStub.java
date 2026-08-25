package cn.howxu.mmcr;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.RandomSource;
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
import net.neoforged.neoforge.capabilities.BlockCapability;

import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.world.level.biome.Biome;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import java.util.stream.Collectors;

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

    public static Level createStates(Map<BlockPos, BlockState> states) {
        return createFromStates(states);
    }

    public static Level createWithBlockEntities(List<BlockEntity> blockEntities) {
        Level level = create(Map.of());
        ((TestLevel) level).blockEntities = blockEntities.stream()
                .collect(Collectors.toMap(BlockEntity::getBlockPos, be -> be));
        return level;
    }

    public static Level createWithBlockEntities(List<BlockEntity> blockEntities, RandomSource random) {
        Level level = createWithBlockEntities(blockEntities);
        ((TestLevel) level).random = random;
        return level;
    }

    public static Level create(Map<BlockPos, Block> blocks, List<BlockEntity> blockEntities) {
        Level level = create(blocks);
        ((TestLevel) level).blockEntities = blockEntities.stream()
                .collect(Collectors.toMap(BlockEntity::getBlockPos, be -> be));
        return level;
    }

    public static Level createWithLoadedChunks(Map<BlockPos, Block> blocks, Set<Long> loadedChunks) {
        Level level = create(blocks);
        ((TestLevel) level).loadedChunks = new HashSet<>(loadedChunks);
        return level;
    }

    public static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
    }

    public static void putBlockEntity(Level level, BlockEntity blockEntity) {
        ((TestLevel) level).blockEntities.put(blockEntity.getBlockPos(), blockEntity);
    }

    public static <T, C> void setCapability(Level level, BlockCapability<T, C> capability, BlockPos pos, T value) {
        ((TestLevel) level).capabilities.computeIfAbsent(capability, ignored -> new HashMap<>()).put(pos, value);
    }

    public static void setDirectSignal(Level level, BlockPos pos, int signal) {
        ((TestLevel) level).directSignals.put(pos, signal);
    }

    public static void setGameTime(Level level, long gameTime) {
        ((TestLevel) level).gameTime = gameTime;
    }

    public static int sentBlockUpdates(Level level) {
        return ((TestLevel) level).sentBlockUpdates;
    }

    private static Level createFromStates(Map<BlockPos, BlockState> blocks) {
        try {
            var level = (TestLevel) unsafe().allocateInstance(TestLevel.class);
            level.blocks = new HashMap<>(blocks);
            level.directSignals = new HashMap<>();
            level.blockEntities = Map.of();
            level.capabilities = new HashMap<>();
            level.random = RandomSource.create(0L);
            Field registryAccess = Level.class.getDeclaredField("registryAccess");
            registryAccess.setAccessible(true);
            registryAccess.set(level, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
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
        private Map<BlockPos, BlockEntity> blockEntities = Map.of();
        private Map<BlockCapability<?, ?>, Map<BlockPos, Object>> capabilities = new HashMap<>();
        private Map<BlockPos, Integer> directSignals = new HashMap<>();
        private Set<Long> loadedChunks;
        private long gameTime;
        private int sentBlockUpdates;
        private RandomSource random = RandomSource.create(0L);

        private TestLevel() {
            super(null, Level.OVERWORLD, null, null, false, false, 0L, 0);
        }

        @Override public BlockState getBlockState(BlockPos pos) {
            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override public BlockEntity getBlockEntity(BlockPos pos) {
            return blockEntities.get(pos);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T, C> T getCapability(BlockCapability<T, C> capability, BlockPos pos, C context) {
            return (T) capabilities.getOrDefault(capability, Map.of()).get(pos);
        }

        @Override public int getDirectSignalTo(BlockPos pos) {
            return directSignals.getOrDefault(pos, 0);
        }

        @Override public void blockEntityChanged(BlockPos pos) {}

        @Override public boolean setBlock(BlockPos pos, BlockState state, int flags) {
            blocks.put(pos, state);
            BlockEntity blockEntity = blockEntities.get(pos);
            if (blockEntity != null) {
                try {
                    Field field = BlockEntity.class.getDeclaredField("blockState");
                    field.setAccessible(true);
                    field.set(blockEntity, state);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Unable to update block entity state", e);
                }
            }
            return true;
        }

        @Override public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) { sentBlockUpdates++; }
        @Override public void playSeededSound(Entity entity, double x, double y, double z, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed) {}
        @Override public void playSeededSound(Entity sourceEntity, Entity entity, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed) {}
        @Override public void explode(Entity source, DamageSource damageSource, ExplosionDamageCalculator calculator, double x, double y, double z, float radius, boolean fire, ExplosionInteraction interaction, ParticleOptions smallExplosionParticles, ParticleOptions largeExplosionParticles, WeightedList<ExplosionParticleInfo> blockInteractionParticles, Holder<SoundEvent> explosionSound) {}
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
        @Override public boolean hasChunk(int chunkX, int chunkZ) {
            return loadedChunks == null || loadedChunks.contains(chunkKey(chunkX, chunkZ));
        }
        @Override public long getGameTime() { return gameTime; }
        @Override public RandomSource getRandom() { return random; }
        @Override public int getSeaLevel() { return 0; }
        @Override public FeatureFlagSet enabledFeatures() { return FeatureFlagSet.of(); }
        @Override public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) { return null; }
        @Override public List<Entity> getEntities(Entity entity, AABB area, Predicate<? super Entity> predicate) { return List.of(); }
        @Override public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> typeTest, AABB area, Predicate<? super T> predicate) { return List.of(); }
        @Override public List<? extends Player> players() { return List.of(); }
        @Override public List<VoxelShape> getEntityCollisions(Entity entity, AABB area) { return List.of(); }
        @Override public BlockPos getHeightmapPos(Heightmap.Types type, BlockPos pos) { return pos; }
    }
}
