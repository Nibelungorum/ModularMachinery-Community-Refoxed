package cn.howxu.mmcr.compat.create.loaded;

import cn.howxu.mmcr.compat.create.StressContributions;
import cn.howxu.mmcr.compat.create.StressSession;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.test.TestBootstrap;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.infrastructure.config.CServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises native unloaded accounting and real entity disk/client packet paths.
 * @author howxu <dev@howxu.cn>
 */
class StressInterfacePersistenceTest {
    private static final HolderLookup.Provider LOOKUP = HolderLookup.Provider.create(Stream.empty());
    private static final Map<StressInterfaceKind, Block> BLOCKS = new EnumMap<>(StressInterfaceKind.class);
    private static final Map<StressInterfaceKind, DeferredHolder<BlockEntityType<?>, BlockEntityType<?>>> PREVIOUS_TYPES = new EnumMap<>(StressInterfaceKind.class);
    private static CServer previousServer;

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        previousServer = AllConfigs.server();
        if (previousServer == null) {
            CServer server = new CServer();
            ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
            server.registerAll(builder);
            server.specification = builder.build();
            CommentedConfig config = CommentedConfig.inMemory();
            server.specification.correct(config);
            var constructor = Class.forName("net.neoforged.fml.config.LoadedConfig").getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            server.specification.acceptConfig((IConfigSpec.ILoadedConfig) constructor.newInstance(config, null, null));
            Field field = AllConfigs.class.getDeclaredField("server");
            field.setAccessible(true);
            field.set(null, server);
        }
        for (StressInterfaceKind kind : StressInterfaceKind.values()) {
            PREVIOUS_TYPES.put(kind, ModBlockEntities.BES.get(kind.id()));
            Block block = registeredBlock(kind);
            registeredType(kind, block);
            var holder = DeferredHolder.<BlockEntityType<?>, BlockEntityType<?>>create(Registries.BLOCK_ENTITY_TYPE, fixtureId(kind));
            ModBlockEntities.BES.put(kind.id(), holder);
            BLOCKS.put(kind, block);
        }
    }

    private static ResourceLocation fixtureId(StressInterfaceKind kind) {
        return ResourceLocation.fromNamespaceAndPath("mmcr_test", "stress_persistence_" + kind.id());
    }

    private static Block registeredBlock(StressInterfaceKind kind) {
        MappedRegistry<Block> blocks = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        ResourceLocation id = fixtureId(kind);
        if (blocks.containsKey(id)) return blocks.get(id);
        blocks.unfreeze();
        try {
            return Registry.register(blocks, id, kind.createBlock(Blocks.IRON_BLOCK.properties(),
                    () -> BuiltInRegistries.BLOCK_ENTITY_TYPE.get(id)));
        } finally {
            blocks.freeze();
        }
    }

    private static BlockEntityType<?> registeredType(StressInterfaceKind kind, Block block) {
        MappedRegistry<BlockEntityType<?>> types = (MappedRegistry<BlockEntityType<?>>) BuiltInRegistries.BLOCK_ENTITY_TYPE;
        ResourceLocation id = fixtureId(kind);
        if (types.containsKey(id)) return types.get(id);
        types.unfreeze();
        try {
            return Registry.register(types, id, BlockEntityType.Builder.of(kind.entityFactory(), block).build(null));
        } finally {
            types.freeze();
        }
    }

    @AfterAll
    static void restoreRegistrations() throws Exception {
        for (StressInterfaceKind kind : StressInterfaceKind.values()) {
            var previous = PREVIOUS_TYPES.get(kind);
            if (previous == null) ModBlockEntities.BES.remove(kind.id());
            else ModBlockEntities.BES.put(kind.id(), previous);
        }
        Field field = AllConfigs.class.getDeclaredField("server");
        field.setAccessible(true);
        field.set(null, previousServer);
    }

    @Test
    void normalFinishCoastsForFiveGameTicksWithoutRenewingOnRepeatedFinish() throws Exception {
        OutputFixture fixture = outputFixture();
        StressSession session = new StressSession();
        assertTrue(fixture.port().apply(session, 1, 4, -16).success());
        session.onRecipeFinished();
        assertEquals(0D, fixture.port().ownedBaseStress(session, 1));
        assertEquals(new OutputSample(4, -16), fixture.sample());
        LevelStub.setGameTime(fixture.entity().getLevel(), 4);
        session.onRecipeFinished();
        fixture.port().tickOutputGrace();
        assertEquals(new OutputSample(4, -16), fixture.sample());
        assertEquals(List.of(new OutputSample(4, -16)), fixture.changes());
        LevelStub.setGameTime(fixture.entity().getLevel(), 5);
        fixture.port().tickOutputGrace();
        assertEquals(new OutputSample(0, 0), fixture.sample());
    }

    @Test
    void unchangedOutputRenewsGraceWithoutNativeSourceChurn() throws Exception {
        OutputFixture fixture = outputFixture();
        StressSession session = new StressSession();
        assertTrue(fixture.port().apply(session, 1, 4, -16).success());
        session.onRecipeFinished();
        LevelStub.setGameTime(fixture.entity().getLevel(), 3);
        assertTrue(fixture.port().apply(session, 1, 4, -16).success());
        session.onRecipeFinished();
        LevelStub.setGameTime(fixture.entity().getLevel(), 5);
        fixture.port().tickOutputGrace();
        assertEquals(new OutputSample(4, -16), fixture.sample());
        assertEquals(List.of(new OutputSample(4, -16)), fixture.changes());
        LevelStub.setGameTime(fixture.entity().getLevel(), 8);
        fixture.port().tickOutputGrace();
        assertEquals(new OutputSample(0, 0), fixture.sample());
    }

    @Test
    void newRequirementIndexAndRpmReplaceCoastingOutputWithoutAnIntermediateZero() throws Exception {
        OutputFixture fixture = outputFixture();
        StressSession session = new StressSession();
        assertTrue(fixture.port().apply(session, 1, 4, -16).success());
        session.onRecipeFinished();
        LevelStub.setGameTime(fixture.entity().getLevel(), 3);
        assertTrue(fixture.port().apply(session, 7, 8, 32).success());
        LevelStub.setGameTime(fixture.entity().getLevel(), 5);
        fixture.port().tickOutputGrace();
        assertEquals(List.of(new OutputSample(4, -16), new OutputSample(8, 32)), fixture.changes());
        assertEquals(0D, fixture.port().ownedBaseStress(session, 1));
        assertEquals(8D, fixture.port().ownedBaseStress(session, 7));
        session.releaseAll();
        assertEquals(new OutputSample(0, 0), fixture.sample());
    }

    @Test
    void sharedLanesExpireIndependentlyAndCannotOverrideAnActiveLanesRpm() throws Exception {
        OutputFixture fixture = outputFixture();
        StressSession first = new StressSession();
        StressSession second = new StressSession();
        assertTrue(fixture.port().apply(first, 1, 4, -16).success());
        assertTrue(fixture.port().apply(second, 1, 8, -16).success());
        first.onRecipeFinished();
        assertFalse(fixture.port().apply(first, 2, 4, 32).success());
        assertEquals(new OutputSample(12, -16), fixture.sample());
        LevelStub.setGameTime(fixture.entity().getLevel(), 1);
        second.onRecipeFinished();
        LevelStub.setGameTime(fixture.entity().getLevel(), 5);
        fixture.port().tickOutputGrace();
        assertEquals(new OutputSample(8, -16), fixture.sample());
        LevelStub.setGameTime(fixture.entity().getLevel(), 6);
        fixture.port().tickOutputGrace();
        assertEquals(new OutputSample(0, 0), fixture.sample());
    }

    @Test
    void explicitReleaseAndInterfaceClearRevokeCoastingImmediately() throws Exception {
        for (int cleanup = 0; cleanup < 3; cleanup++) {
            OutputFixture fixture = outputFixture();
            StressSession session = new StressSession();
            assertTrue(fixture.port().apply(session, 1, 4, -16).success());
            session.onRecipeFinished();
            switch (cleanup) {
                case 0 -> fixture.port().clear();
                case 1 -> session.releaseAll();
                case 2 -> session.releaseOutputs();
            }
            assertEquals(new OutputSample(0, 0), fixture.sample());
        }
    }

    @Test
    void sourceFirstNativeReloadDebitsInputAndOverpoweredOutputExactlyOnce() throws Exception {
        CompoundTag inputTag = savedInput();
        CompoundTag outputTag = savedOutput();
        KineticNetwork network = new KineticNetwork();
        // The native external source's saved total seeds the unloaded ledger before ports load.
        network.initFromTE(inputTag.getCompound("Network").getFloat("Capacity"),
                inputTag.getCompound("Network").getFloat("Stress"), inputTag.getCompound("Network").getInt("Size"));
        SavedSource source = new SavedSource();
        network.addSilently(source, 16F, 1F);
        StressInputBlockEntity input = input();
        StressOutputBlockEntity output = output();
        input.loadWithComponents(inputTag, LOOKUP);
        output.loadWithComponents(outputTag, LOOKUP);
        assertTrue(recovery(input).settle(network, input));
        assertTrue(recovery(output).settle(network, output));
        assertEquals(0F, unloaded(network, "unloadedStress"));
        assertEquals(0F, unloaded(network, "unloadedCapacity"));
        assertEquals(3, network.getSize());
        assertEquals(0F, input.calculateStressApplied());
        assertEquals(0F, output.calculateAddedStressCapacity());
        assertEquals(0F, output.getGeneratedSpeed());
        assertEquals(16F, network.sources.get(source).floatValue());
        assertEquals(1F, network.members.get(source).floatValue());
        assertEquals(0F, network.sources.get(output).floatValue());
        assertFalse(recovery(input).settle(network, input));
        assertFalse(recovery(output).settle(network, output));
        assertEquals(0F, unloaded(network, "unloadedCapacity"));
        assertEquals(3, network.getSize());
    }

    @Test
    void portFirstNativeInitializationKeepsFullAggregateUntilOtherMembersDebit() throws Exception {
        KineticNetwork network = new KineticNetwork();
        StressOutputBlockEntity output = output();
        output.loadWithComponents(savedOutput(), LOOKUP);
        assertTrue(recovery(output).settle(network, output));
        assertEquals(1024F, unloaded(network, "unloadedCapacity"));
        assertEquals(128F, unloaded(network, "unloadedStress"));
        network.addSilently(new SavedSource(), 16F, 1F);
        StressInputBlockEntity input = input();
        input.loadWithComponents(savedInput(), LOOKUP);
        assertTrue(recovery(input).settle(network, input));
        assertEquals(0F, unloaded(network, "unloadedCapacity"));
        assertEquals(0F, unloaded(network, "unloadedStress"));
        assertEquals(3, network.getSize());
    }

    @Test
    void liveRereadAlreadyAccountedMemberDoesNotDebitForeignUnloadedSlots() throws Exception {
        KineticNetwork network = new KineticNetwork();
        StressInputBlockEntity input = input();
        CompoundTag saved = savedInput();
        input.loadWithComponents(saved, LOOKUP);
        assertTrue(recovery(input).settle(network, input));
        float capacity = unloaded(network, "unloadedCapacity");
        float stress = unloaded(network, "unloadedStress");
        int size = network.getSize();
        input.loadWithComponents(saved, LOOKUP);
        assertFalse(recovery(input).settle(network, input));
        assertEquals(capacity, unloaded(network, "unloadedCapacity"));
        assertEquals(stress, unloaded(network, "unloadedStress"));
        assertEquals(size, network.getSize());
    }

    @Test
    void diskResaveBeforeSettlementPreservesDebtAndCannotRestoreGeneration() throws Exception {
        CompoundTag saved = savedOutput();
        StressOutputBlockEntity output = output();
        output.loadWithComponents(saved, LOOKUP);
        assertEquals(0F, output.getGeneratedSpeed());
        assertEquals(0F, output.calculateAddedStressCapacity());
        CompoundTag resaved = output.saveWithoutMetadata(LOOKUP);
        assertEquals(saved.getCompound("Network"), resaved.getCompound("Network"));
        assertEquals(saved.getCompound("StressRecovery"), resaved.getCompound("StressRecovery"));
        assertEquals(saved.get("Source"), resaved.get("Source"));
        assertEquals(-64F, resaved.getFloat("Speed"));
        assertFalse(resaved.contains("GeneratedRpm"));
        StressOutputBlockEntity second = output();
        second.loadWithComponents(resaved, LOOKUP);
        assertEquals(0F, second.getGeneratedSpeed());
        assertEquals(0F, second.calculateAddedStressCapacity());
        KineticNetwork network = new KineticNetwork();
        assertTrue(recovery(second).settle(network, second));
        assertEquals(1024F, unloaded(network, "unloadedCapacity"));
        assertEquals(3, network.getSize());
    }

    @Test
    void ownSourceDebitUsesOldRpmWithoutRevivingSavedRotation() throws Exception {
        CompoundTag tag = savedOutput();
        tag.remove("Source");
        tag.putFloat("Speed", -16F);
        tag.getCompound("Network").putFloat("Capacity", 64F);
        tag.getCompound("Network").putFloat("Stress", 0F);
        tag.getCompound("Network").putInt("Size", 2);
        StressOutputBlockEntity output = output();
        output.loadWithComponents(tag, LOOKUP);
        assertEquals(0F, output.getGeneratedSpeed());
        assertEquals(0F, output.getTheoreticalSpeed());
        KineticNetwork network = new KineticNetwork();
        assertTrue(recovery(output).settle(network, output));
        assertEquals(0F, unloaded(network, "unloadedCapacity"));
        assertEquals(2, network.getSize());
        assertEquals(0F, output.getGeneratedSpeed());
        assertEquals(0F, output.getTheoreticalSpeed());
    }

    @Test
    void realClientPacketsPreserveNativeDisplayContributionsAndClearOnRelease() throws Exception {
        StressInputBlockEntity serverInput = input();
        StressOutputBlockEntity serverOutput = output();
        StressSession owner = new StressSession();
        ledger(serverInput).replace(owner, 0, new StressContributions.Contribution(2D, 0D));
        ledger(serverOutput).replace(owner, 1, new StressContributions.Contribution(4D, -16D));
        serverInput.setSpeed(-32F);
        serverOutput.setSpeed(-64F);
        StressInputBlockEntity clientInput = input();
        StressOutputBlockEntity clientOutput = output();
        clientInput.readClient(serverInput.writeClient(new CompoundTag(), LOOKUP), LOOKUP);
        clientOutput.readClient(serverOutput.writeClient(new CompoundTag(), LOOKUP), LOOKUP);
        assertEquals(2F, clientInput.calculateStressApplied());
        assertEquals(4F, clientOutput.calculateAddedStressCapacity());
        assertEquals(-16F, clientOutput.getGeneratedSpeed());
        assertEquals(64F, clientInput.calculateStressApplied() * Math.abs(clientInput.getTheoreticalSpeed()));
        assertEquals(64F, clientOutput.calculateAddedStressCapacity() * Math.abs(clientOutput.getGeneratedSpeed()));
        // The client has no recipe ledger; packet data only drives native display calculations.
        assertEquals(0D, clientInput.capabilitySnapshot().capabilities().stream()
                .map(StressPortCapability.class::cast).findFirst().orElseThrow().baseStress());
        ledger(serverInput).release(owner);
        ledger(serverOutput).release(owner);
        clientInput.readClient(serverInput.writeClient(new CompoundTag(), LOOKUP), LOOKUP);
        clientOutput.readClient(serverOutput.writeClient(new CompoundTag(), LOOKUP), LOOKUP);
        assertEquals(0F, clientInput.calculateStressApplied());
        assertEquals(0F, clientOutput.calculateAddedStressCapacity());
        assertEquals(0F, clientOutput.getGeneratedSpeed());
    }

    private static StressInputBlockEntity input() {
        return new StressInputBlockEntity(new BlockPos(1, 0, 0), BLOCKS.get(StressInterfaceKind.INPUT).defaultBlockState());
    }

    private static StressOutputBlockEntity output() {
        return new StressOutputBlockEntity(new BlockPos(2, 0, 0), BLOCKS.get(StressInterfaceKind.OUTPUT).defaultBlockState());
    }

    private static OutputFixture outputFixture() throws Exception {
        KineticBlockEntity entity = new StaticKineticEntity();
        entity.setLevel(LevelStub.create(Map.of(BlockPos.ZERO, Blocks.CHEST), List.of(entity)));
        LevelStub.setGameTime(entity.getLevel(), 0);
        List<OutputSample> changes = new ArrayList<>();
        AtomicReference<StressPortCapability> reference = new AtomicReference<>();
        StressPortCapability port = new StressPortCapability(entity, StressInterfaceKind.OUTPUT, () -> 1_000_000D,
                () -> 0D, () -> changes.add(new OutputSample(reference.get().baseStress(), reference.get().generatedRpm())));
        reference.set(port);
        Field controller = StressPortCapability.class.getDeclaredField("controllerPos");
        controller.setAccessible(true);
        controller.set(port, BlockPos.ZERO);
        return new OutputFixture(entity, port, changes);
    }

    /** @author howxu <dev@howxu.cn> */
    private record OutputFixture(KineticBlockEntity entity, StressPortCapability port, List<OutputSample> changes) {
        OutputSample sample() { return new OutputSample(port.baseStress(), port.generatedRpm()); }
    }

    /** @author howxu <dev@howxu.cn> */
    private record OutputSample(double baseStress, double rpm) { }

    /** No native propagation runs in the facet's deterministic clock fixture.
     * @author howxu <dev@howxu.cn>
     */
    private static final class StaticKineticEntity extends KineticBlockEntity {
        private StaticKineticEntity() { super(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState()); }
        @Override public float getSpeed() { return getTheoreticalSpeed(); }
    }

    private static CompoundTag savedInput() throws Exception {
        StressInputBlockEntity input = input();
        ledger(input).replace(new StressSession(), 0, new StressContributions.Contribution(2D, 0D));
        input.network = 7L;
        input.source = BlockPos.ZERO;
        input.setSpeed(-32F);
        input.updateFromNetwork(1088F, 128F, 3);
        return input.saveWithoutMetadata(LOOKUP);
    }

    private static CompoundTag savedOutput() throws Exception {
        StressOutputBlockEntity output = output();
        ledger(output).replace(new StressSession(), 1, new StressContributions.Contribution(4D, -16D));
        output.network = 7L;
        output.source = BlockPos.ZERO;
        output.setSpeed(-64F);
        output.updateFromNetwork(1088F, 128F, 3);
        return output.saveWithoutMetadata(LOOKUP);
    }

    private static StressInterfacePersistence recovery(KineticBlockEntity entity) throws Exception {
        Field field = entity.getClass().getDeclaredField("recovery");
        field.setAccessible(true);
        return (StressInterfacePersistence) field.get(entity);
    }

    private static StressContributions ledger(KineticBlockEntity entity) throws Exception {
        Field field = entity.getClass().getDeclaredField("port");
        field.setAccessible(true);
        Field contributions = StressPortCapability.class.getDeclaredField("contributions");
        contributions.setAccessible(true);
        return (StressContributions) contributions.get(field.get(entity));
    }

    private static float unloaded(KineticNetwork network, String fieldName) throws Exception {
        Field field = KineticNetwork.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getFloat(network);
    }

    /** Native generator fixture: only physical source values, all accounting remains Create's.
     * @author howxu <dev@howxu.cn>
     */
    private static final class SavedSource extends GeneratingKineticBlockEntity {
        SavedSource() {
            super(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
            setSpeed(-64F);
        }
        @Override public float getGeneratedSpeed() { return -64F; }
        @Override public float calculateAddedStressCapacity() { return lastCapacityProvided = 16F; }
        @Override public float calculateStressApplied() { return lastStressApplied = 1F; }
    }
}
