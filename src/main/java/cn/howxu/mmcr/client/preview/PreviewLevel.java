package cn.howxu.mmcr.client.preview;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.entity.PartEntity;

import net.minecraft.core.Direction;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.level.GameRules;
import net.minecraft.util.profiling.InactiveProfiler;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.mojang.serialization.Lifecycle;
import java.util.function.Consumer;

/**
 * Render-only level that exposes an immutable structure-preview schema.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PreviewLevel extends Level {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private final StructurePreviewSchema schema;
    private final PreviewChunkSource chunkSource;
    private final Map<BlockPos, BlockEntity> blockEntities = new ConcurrentHashMap<>();
    private final WorldBorder worldBorder = new WorldBorder();
    private final TickRateManager tickRateManager = new TickRateManager();
    private final Scoreboard scoreboard = new Scoreboard();
    private final AtomicReference<Supplier<PreviewVisibility>> visibilitySupplier;
    private final Thread renderThread;
    private final RecipeManager recipeManager;
    private int nextMapId;
    private float dayTimeFraction;
    private float dayTimePerTick = 1.0F;
    private volatile boolean closed;

    private PreviewLevel(StructurePreviewSchema schema, Supplier<PreviewVisibility> visibilitySupplier) {
        super(new PreviewLevelData(), Level.OVERWORLD, previewRegistryAccess(), overworldType(),
                () -> InactiveProfiler.INSTANCE, true, false, 0L, 0);
        this.schema = Objects.requireNonNull(schema, "schema");
        this.visibilitySupplier = new AtomicReference<>(Objects.requireNonNull(visibilitySupplier, "visibilitySupplier"));
        this.renderThread = Thread.currentThread();
        this.chunkSource = new PreviewChunkSource(this);
        this.recipeManager = new RecipeManager(registryAccess());
    }

    public static PreviewLevel create(StructurePreviewSchema schema, Supplier<PreviewVisibility> visibilitySupplier) {
        return new PreviewLevel(schema, visibilitySupplier);
    }

    public void updateVisibility(PreviewVisibility visibility) {
        PreviewVisibility updated = Objects.requireNonNull(visibility, "visibility");
        visibilitySupplier.set(() -> updated);
    }

    boolean isPreviewChunk(int x, int z) {
        int minX = ChunkPos.containing(schema.min()).x() - 1;
        int maxX = ChunkPos.containing(schema.max()).x() + 1;
        int minZ = ChunkPos.containing(schema.min()).z() - 1;
        int maxZ = ChunkPos.containing(schema.max()).z() + 1;
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    @Override
    public BlockState getBlockState(BlockPos position) {
        BlockState state = schema.stateAt(position);
        PreviewVisibility visibility = Objects.requireNonNull(visibilitySupplier.get().get(), "visibilitySupplier result");
        return !closed && state != null && visibility.isVisible(position, state) ? state : AIR;
    }

    @Override
    public FluidState getFluidState(BlockPos position) {
        return getBlockState(position).getFluidState();
    }

    @Override
    public BlockHitResult clip(ClipContext context) {
        return BlockGetter.traverseBlocks(context.getFrom(), context.getTo(), context, (clipContext, position) -> {
            BlockState blockState = getBlockState(position);
            FluidState fluidState = getFluidState(position);
            Vec3 from = clipContext.getFrom();
            Vec3 to = clipContext.getTo();
            VoxelShape blockShape = clipContext.getBlockShape(blockState, this, position);
            BlockHitResult blockResult = clipWithInteractionOverride(from, to, position, blockShape, blockState);
            VoxelShape fluidShape = clipContext.getFluidShape(fluidState, this, position);
            BlockHitResult fluidResult = fluidShape.clip(from, to, position);
            double blockDistance = blockResult == null ? Double.MAX_VALUE
                    : from.distanceToSqr(blockResult.getLocation());
            double fluidDistance = fluidResult == null ? Double.MAX_VALUE
                    : from.distanceToSqr(fluidResult.getLocation());
            return blockDistance <= fluidDistance ? blockResult : fluidResult;
        }, clipContext -> {
            Vec3 delta = clipContext.getFrom().subtract(clipContext.getTo());
            return BlockHitResult.miss(clipContext.getTo(),
                    Direction.getApproximateNearest(delta.x, delta.y, delta.z), BlockPos.containing(clipContext.getTo()));
        });
    }

    public float getShade(Direction direction, boolean shade) {
        if (!shade) return 1.0F;
        return switch (direction) {
            case DOWN -> 0.5F;
            case NORTH, SOUTH -> 0.8F;
            case WEST, EAST -> 0.6F;
            case UP -> 1.0F;
        };
    }

    @Override
    public int getBrightness(LightLayer lightLayer, BlockPos position) {
        return 15;
    }

    @Override
    public int getRawBrightness(BlockPos position, int amount) {
        return 15;
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos position) {
        BlockState state = getBlockState(position);
        if (closed || !state.hasBlockEntity()) return null;
        BlockPos immutablePosition = position.immutable();
        BlockEntity cached = blockEntities.get(immutablePosition);
        if (cached != null) return cached;
        assertRenderThread();
        BlockEntity blockEntity = state.getBlock() instanceof EntityBlock entityBlock
                ? entityBlock.newBlockEntity(immutablePosition, state) : null;
        if (blockEntity == null) return null;
        blockEntity.setLevel(this);
        BlockEntity existing = blockEntities.putIfAbsent(immutablePosition, blockEntity);
        return existing == null ? blockEntity : existing;
    }

    @Override
    public void close() {
        assertRenderThread();
        closed = true;
        blockEntities.clear();
    }

    void assertRenderThread() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null ? !minecraft.isSameThread() : Thread.currentThread() != renderThread) {
            throw new IllegalStateException("preview cache mutation must occur on the render thread");
        }
    }

    @Override public ChunkSource getChunkSource() { return chunkSource; }
    @Override public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) { }
    @Override public void playSeededSound(Entity source, double x, double y, double z, Holder<SoundEvent> sound, SoundSource category, float volume, float pitch, long seed) { }
    @Override public void playSeededSound(Entity source, Entity entity, Holder<SoundEvent> sound, SoundSource category, float volume, float pitch, long seed) { }
    @Override public String gatherChunkSourceStats() { return chunkSource.gatherStats(); }
    @Override public Entity getEntity(int id) { return null; }
    @Override public Collection<PartEntity<?>> getPartEntities() { return List.of(); }
    @Override public TickRateManager tickRateManager() { return tickRateManager; }
    @Override public MapItemSavedData getMapData(MapId id) { return null; }
    @Override public void setMapData(MapId id, MapItemSavedData data) { }
    @Override public MapId getFreeMapId() { return new MapId(nextMapId++); }
    @Override public void destroyBlockProgress(int id, BlockPos pos, int progress) { }
    @Override public Scoreboard getScoreboard() { return scoreboard; }
    @Override public RecipeManager getRecipeManager() { return recipeManager; }
    @Override protected LevelEntityGetter<Entity> getEntities() { return EmptyEntityGetter.INSTANCE; }
    @Override public PotionBrewing potionBrewing() { return PotionBrewing.EMPTY; }
    @Override public void setDayTimeFraction(float fraction) { dayTimeFraction = fraction; }
    @Override public float getDayTimeFraction() { return dayTimeFraction; }
    @Override public float getDayTimePerTick() { return dayTimePerTick; }
    @Override public void setDayTimePerTick(float ticks) { dayTimePerTick = ticks; }
    @Override public void levelEvent(Entity entity, int type, BlockPos pos, int data) { }
    @Override public void gameEvent(Holder<GameEvent> event, Vec3 position, GameEvent.Context context) { }
    @Override public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) { return registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS); }
    @Override public int getSeaLevel() { return 63; }
    @Override public FeatureFlagSet enabledFeatures() { return FeatureFlags.DEFAULT_FLAGS; }
    @Override public WorldBorder getWorldBorder() { return worldBorder; }
    @Override public List<? extends Player> players() { return List.of(); }
    @Override public LevelTickAccess<Block> getBlockTicks() { return BlackholeTickAccess.emptyLevelList(); }
    @Override public LevelTickAccess<Fluid> getFluidTicks() { return BlackholeTickAccess.emptyLevelList(); }
    @Override public int getHeight(Heightmap.Types type, int x, int z) { return schema.max().getY() + 1; }
    @Override public int getSkyDarken() { return 0; }

    private static RegistryAccess previewRegistryAccess() {
        MappedRegistry<Biome> biomes = new MappedRegistry<>(Registries.BIOME,
                Lifecycle.stable());
        Biome biome = new Biome.BiomeBuilder().hasPrecipitation(false).temperature(0.8F).downfall(0.0F)
                .specialEffects(new BiomeSpecialEffects.Builder().waterColor(0x3F76E4).build())
                .mobSpawnSettings(new MobSpawnSettings.Builder().build())
                .generationSettings(new BiomeGenerationSettings.PlainBuilder().build())
                .build();
        biomes.register(Biomes.PLAINS, biome, RegistrationInfo.BUILT_IN);
        MappedRegistry<DamageType> damageTypes =
                new MappedRegistry<>(Registries.DAMAGE_TYPE, Lifecycle.stable());
        for (Field field : DamageTypes.class.getFields()) {
            if (field.getType() != ResourceKey.class) continue;
            try {
                @SuppressWarnings("unchecked")
                ResourceKey<DamageType> key =
                        (ResourceKey<DamageType>) field.get(null);
                damageTypes.register(key, new DamageType(key.location().getPath(), 0.0F),
                        RegistrationInfo.BUILT_IN);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("cannot initialize preview damage registry", exception);
            }
        }
        return new RegistryAccess.ImmutableRegistryAccess(Map.of(
                Registries.BIOME, biomes.freeze(), Registries.DAMAGE_TYPE, damageTypes.freeze()));
    }

    private static Holder<DimensionType> overworldType() {
        return Holder.direct(new DimensionType(OptionalLong.empty(), true, false, false, true, 1.0D,
                true, false, -64, 384, 384, BlockTags.INFINIBURN_OVERWORLD,
                BuiltinDimensionTypes.OVERWORLD_EFFECTS, 0.0F,
                new DimensionType.MonsterSettings(false, false, ConstantInt.of(0), 0)));
    }

    /**
     * Inert local level metadata required by the Level superclass.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class PreviewLevelData implements WritableLevelData {
        private BlockPos spawn = BlockPos.ZERO;
        private float spawnAngle;
        @Override public BlockPos getSpawnPos() { return spawn; }
        @Override public float getSpawnAngle() { return spawnAngle; }
        @Override public long getGameTime() { return 0L; }
        @Override public long getDayTime() { return 0L; }
        @Override public boolean isThundering() { return false; }
        @Override public boolean isRaining() { return false; }
        @Override public void setRaining(boolean raining) { }
        @Override public boolean isHardcore() { return false; }
        @Override public GameRules getGameRules() { return new GameRules(); }
        @Override public Difficulty getDifficulty() { return Difficulty.PEACEFUL; }
        @Override public boolean isDifficultyLocked() { return true; }
        @Override public void setSpawn(BlockPos position, float angle) {
            spawn = position.immutable();
            spawnAngle = angle;
        }
    }

    /**
     * Empty entity view that prevents preview queries from observing world entities.
     *
     * @author howxu <dev@howxu.cn>
     */
    private enum EmptyEntityGetter implements LevelEntityGetter<Entity> {
        INSTANCE;
        @Override public Entity get(int id) { return null; }
        @Override public Entity get(UUID id) { return null; }
        @Override public Iterable<Entity> getAll() { return List.of(); }
        @Override public <U extends Entity> void get(EntityTypeTest<Entity, U> type, AbortableIterationConsumer<U> consumer) { }
        @Override public void get(AABB bounds, Consumer<Entity> consumer) { }
        @Override public <U extends Entity> void get(EntityTypeTest<Entity, U> type, AABB bounds, AbortableIterationConsumer<U> consumer) { }
    }
}
