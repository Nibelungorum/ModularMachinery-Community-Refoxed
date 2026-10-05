package cn.howxu.mmcr.compat.pneumaticcraft;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachinePatternCompiler;
import cn.howxu.mmcr.compat.pneumaticcraft.loaded.AirInterfaceBlock;
import cn.howxu.mmcr.compat.pneumaticcraft.loaded.AirInterfaceKind;
import cn.howxu.mmcr.compat.pneumaticcraft.loaded.LoadedPneumaticCraftBridge;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.MachinePort;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
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

import static org.junit.jupiter.api.Assertions.*;

/** Native construction/factory execution is covered in GameTest, not an uninitialized PNC runtime. @author howxu <dev@howxu.cn> */
class AirPortRegistrationTest {
    @BeforeAll static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void missingModBridgeHasNoNativePorts() {
        var bridge = PneumaticCraftBridgeBootstrap.selectForTesting(false);
        assertFalse(bridge.available());
        assertTrue(bridge.portKinds().isEmpty());
        bridge.registerPorts(null);
    }

    @Test
    void nativeKindsDeclareDirectionalInternalBindingsAndDiscoverableFamilies() {
        var bridge = new LoadedPneumaticCraftBridge();
        assertTrue(bridge.available());
        assertEquals(List.of(AirInterfaceKind.INPUT, AirInterfaceKind.OUTPUT), bridge.portKinds());
        assertEquals(IOType.INPUT, AirInterfaceKind.INPUT.ioType());
        assertEquals(IOType.OUTPUT, AirInterfaceKind.OUTPUT.ioType());
        for (IOPortKind kind : bridge.portKinds()) {
            var binding = kind.definition().bindings().getFirst();
            assertEquals(PneumaticIds.AIR, binding.type().id());
            assertEquals(kind.id(), kind.definition().id().getPath());
            assertTrue(binding.directions().supports(kind.ioType()));
            assertFalse(binding.directions().supports(kind.ioType() == IOType.INPUT ? IOType.OUTPUT : IOType.INPUT));
            assertFalse(binding.nativeTransferExposure());
            assertTrue(binding.externalExposure().isEmpty());
            assertNotNull(binding.factory());
            assertTrue(kind.families().getFirst().matches(binding));
            assertEquals(List.of(kind.id()), kind.families().getFirst().countAliases());
            assertEquals(List.of(PneumaticIds.MOD_ID), kind.modDependencies());
        }
    }

    @Test
    void airBlockFactoryParticipatesInCompiledPortDiscovery() {
        for (AirInterfaceKind kind : AirInterfaceKind.values()) {
            Block block = registeredBlock(kind);
            assertInstanceOf(AirInterfaceBlock.class, block);
            assertSame(kind, ((MachinePort) block).kind());
            BlockPos portPos = new BlockPos(1, 0, 0);
            Machine machine = new Machine() {
                @Override public ResourceLocation registryName() { return ResourceLocation.parse("mmcr_test:air_discovery"); }
                @Override public MachineControllerSpec controller() { return MachineControllerSpec.defaultsFor(registryName()); }
                @Override public BlockArray pattern() { return new BlockArray(Map.of(portPos, new BlockPredicate.OfBlock(block))); }
            };
            var compiled = MachinePatternCompiler.compile(machine);
            assertTrue(compiled.componentPositions(Direction.SOUTH).contains(portPos));
            assertTrue(compiled.portPositions(Direction.SOUTH).contains(portPos));
        }
    }

    private static Block registeredBlock(AirInterfaceKind kind) {
        MappedRegistry<Block> blocks = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mmcr_test", "air_registration_" + kind.id());
        if (blocks.containsKey(id)) return blocks.get(id);
        blocks.unfreeze();
        try {
            return Registry.register(blocks, id, kind.createBlock(Blocks.IRON_BLOCK.properties(), () -> BlockEntityType.CHEST));
        } finally {
            blocks.freeze();
        }
    }
}
