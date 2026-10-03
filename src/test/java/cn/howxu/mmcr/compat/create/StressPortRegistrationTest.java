package cn.howxu.mmcr.compat.create;

import cn.howxu.mmcr.compat.create.loaded.LoadedCreateBridge;
import cn.howxu.mmcr.compat.create.loaded.StressInterfaceKind;
import cn.howxu.mmcr.compat.create.loaded.StressInterfaceBlock;
import cn.howxu.mmcr.compat.create.loaded.StressInputBlockEntity;
import cn.howxu.mmcr.compat.create.loaded.StressOutputBlockEntity;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachinePatternCompiler;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.port.MachinePort;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.util.IOType;
import com.simibubi.create.content.kinetics.simpleRelays.AbstractShaftBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/** @author howxu <dev@howxu.cn> */
class StressPortRegistrationTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void ordinaryBlockFactoryRemainsOrdinaryAndNativeFactoryParticipatesInCompiledDiscovery() {
        var ordinary = registeredBlock(PortKinds.ITEM_INPUT, () -> BlockEntityType.CHEST);
        assertInstanceOf(IOPortBlock.class, ordinary);
        assertSame(PortKinds.ITEM_INPUT, ((MachinePort) ordinary).kind());

        for (var kind : StressInterfaceKind.values()) {
            var nativeBlock = registeredBlock(kind, () -> BuiltInRegistries.BLOCK_ENTITY_TYPE.get(fixtureId(kind)));
            var type = registeredType(kind, nativeBlock);
            assertInstanceOf(AbstractShaftBlock.class, nativeBlock);
            assertSame(kind, ((MachinePort) nativeBlock).kind());
            var stressBlock = (StressInterfaceBlock) nativeBlock;
            assertSame(type, stressBlock.getBlockEntityType());
            assertTrue(type.isValid(nativeBlock.defaultBlockState()));
            assertEquals(kind == StressInterfaceKind.INPUT ? StressInputBlockEntity.class : StressOutputBlockEntity.class,
                    stressBlock.getBlockEntityClass());
            BlockPos portPos = new BlockPos(1, 0, 0);
            Machine machine = new Machine() {
                @Override public ResourceLocation registryName() { return ResourceLocation.parse("mmcr_test:native_port"); }
                @Override public MachineControllerSpec controller() { return MachineControllerSpec.defaultsFor(registryName()); }
                @Override public BlockArray pattern() {
                    return new BlockArray(Map.of(portPos, new BlockPredicate.OfBlock(nativeBlock)));
                }
            };
            var compiled = MachinePatternCompiler.compile(machine);
            assertTrue(compiled.componentPositions(Direction.SOUTH).contains(portPos));
            assertTrue(compiled.portPositions(Direction.SOUTH).contains(portPos));
        }
    }

    @Test
    void nativeKindsDeclareInternalStressOnlyWithStablePortIds() {
        List<IOPortKind> kinds = new LoadedCreateBridge().portKinds();
        assertEquals(List.of(StressInterfaceKind.INPUT, StressInterfaceKind.OUTPUT), kinds);
        for (IOPortKind kind : kinds) {
            assertEquals(kind.id(), kind.definition().id().getPath());
            var binding = kind.bindings().getFirst();
            assertEquals(StressInterfaceKind.TYPE, binding.type());
            assertTrue(binding.directions().supports(kind.ioType()));
            assertFalse(binding.nativeTransferExposure());
            assertTrue(binding.externalExposure().isEmpty());
            assertTrue(kind.families().getFirst().matches(binding));
            assertEquals(List.of(kind.id()), kind.families().getFirst().countAliases());
            assertEquals(List.of("create"), kind.modDependencies());
        }
        assertEquals(IOType.INPUT, kinds.getFirst().ioType());
        assertEquals(IOType.OUTPUT, kinds.getLast().ioType());
    }

    private static ResourceLocation fixtureId(IOPortKind kind) {
        return ResourceLocation.fromNamespaceAndPath("mmcr_test", "stress_registration_" + kind.id());
    }

    private static Block registeredBlock(IOPortKind kind, Supplier<? extends BlockEntityType<?>> type) {
        MappedRegistry<Block> blocks = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        ResourceLocation id = fixtureId(kind);
        if (blocks.containsKey(id)) return blocks.get(id);
        blocks.unfreeze();
        try {
            return Registry.register(blocks, id, kind.createBlock(Blocks.IRON_BLOCK.properties(), type));
        } finally {
            blocks.freeze();
        }
    }

    private static BlockEntityType<?> registeredType(IOPortKind kind, Block block) {
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
}
