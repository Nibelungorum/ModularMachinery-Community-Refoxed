package cn.howxu.mmcr.compat.fluxnetworks;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.type.CapabilityCreationContext;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachinePatternCompiler;
import cn.howxu.mmcr.api.machine.definition.InterfacePredicates;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkInputBlockEntity;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkInputCapability;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkInterfaceBlock;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkInterfaceKind;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkOutputBlockEntity;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkOutputCapability;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkPlugHandler;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkPointHandler;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.LoadedFluxNetworksBridge;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.internal.port.EnergyHatchSize;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.MachinePort;
import cn.howxu.mmcr.internal.port.PortFamilyIds;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sonar.fluxnetworks.api.device.IFluxPlug;
import sonar.fluxnetworks.api.device.IFluxPoint;
import sonar.fluxnetworks.common.device.TileFluxConnector;
import sonar.fluxnetworks.common.device.TileFluxDevice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** @author howxu <dev@howxu.cn> */
class FluxNetworksRegistrationTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        FluxNetworksTestBootstrap.bootstrap();
    }

    @Test
    void loadedBridgeDeclaresDirectionalInternalEnergyBindingsAndHostedCapabilities() {
        FluxNetworksBridge bridge = FluxNetworksBridgeBootstrap.selectForTesting(true);
        assertThat(bridge.available()).isTrue();
        assertThat(bridge.portKinds()).containsExactly(FluxNetworkInterfaceKind.INPUT, FluxNetworkInterfaceKind.OUTPUT);
        for (IOPortKind kind : bridge.portKinds()) {
            assertThat(kind.definition().id()).isEqualTo(MMCR.id(kind.id()));
            assertThat(kind.modDependencies()).containsExactly(FluxNetworksIds.MOD_ID);
            var binding = kind.bindings().getFirst();
            assertThat(binding.type()).isEqualTo(BuiltinCapabilityDefinitions.ENERGY_TYPE);
            assertThat(binding.directions().values()).containsExactly(kind.ioType());
            assertThat(binding.nativeTransferExposure()).isFalse();
            assertThat(binding.externalExposure()).isEmpty();
            assertThat(binding.supports(EnergyHatchSize.ULTIMATE.ordinal())).isFalse();
            assertThat(binding.supports(EnergyHatchSize.ULTIMATE.ordinal() + 1)).isTrue();
            var family = kind.families().getFirst();
            assertThat(family.familyId()).isEqualTo(PortFamilyIds.ENERGY);
            assertThat(family.ioType()).isEqualTo(kind.ioType());
            assertThat(family.matches(binding)).isTrue();
            assertThat(family.countAliases()).containsExactly(kind.ioType() == IOType.INPUT
                    ? "energy_input_hatch" : "energy_output_hatch");

            MachineCapability capability = kind.ioType() == IOType.INPUT
                    ? new FluxNetworkInputCapability(new FluxNetworkPointHandler(() -> false, () -> 1L, () -> {}), () -> "test")
                    : new FluxNetworkOutputCapability(new FluxNetworkPlugHandler(() -> false, () -> 0L, () -> {}));
            CapabilityHost host = () -> new CapabilitySnapshot(List.of(capability));
            CapabilityCreationContext context = new CapabilityCreationContext() {
                @Override public CapabilityHost host() { return host; }
                @Override public IOType ioType() { return kind.ioType(); }
                @Override public <T> Optional<T> service(Class<T> type) { return Optional.empty(); }
                @Override public Runnable onChanged() { return () -> {}; }
            };
            assertThat(binding.factory().create(context)).isSameAs(capability);
        }
        assertThat(IFluxPoint.class.isAssignableFrom(FluxNetworkInputBlockEntity.class)).isTrue();
        assertThat(IFluxPlug.class.isAssignableFrom(FluxNetworkOutputBlockEntity.class)).isTrue();
        for (Class<?> type : List.of(FluxNetworkInputBlockEntity.class, FluxNetworkOutputBlockEntity.class)) {
            assertThat(TileFluxDevice.class.isAssignableFrom(type)).isTrue();
            assertThat(TileFluxConnector.class.isAssignableFrom(type)).isFalse();
        }
    }

    @Test
    void nativeBlockFactoriesParticipateInPatternDiscoveryAndDirectionalEnergyAlternatives() {
        Map<String, DeferredHolder<Block, Block>> previousBlocks = new LinkedHashMap<>(ModBlocks.BLOCKS);
        PortKinds.clearForTesting();
        try {
            for (IOPortKind kind : new LoadedFluxNetworksBridge().portKinds()) {
                if (!PortKinds.all().contains(kind)) PortKinds.register(kind);
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mmcr_test", "registration_" + kind.id());
                Block block = registeredBlock(id, kind);
                ModBlocks.BLOCKS.put(kind.id(), DeferredHolder.create(Registries.BLOCK, id));
                assertThat(block).isInstanceOf(FluxNetworkInterfaceBlock.class);
                assertThat(((MachinePort) block).kind()).isSameAs(kind);
                assertThat(block.getStateDefinition().getProperties()).isEmpty();
                BlockPos portPos = new BlockPos(1, 0, 0);
                Machine machine = new Machine() {
                    @Override public ResourceLocation registryName() { return id; }
                    @Override public MachineControllerSpec controller() { return MachineControllerSpec.defaultsFor(id); }
                    @Override public BlockArray pattern() {
                        return new BlockArray(Map.of(portPos, new BlockPredicate.OfBlock(block)));
                    }
                };
                var compiled = MachinePatternCompiler.compile(machine);
                assertThat(compiled.componentPositions(Direction.SOUTH)).contains(portPos);
                assertThat(compiled.portPositions(Direction.SOUTH)).contains(portPos);
            }
            Block input = ModBlocks.BLOCKS.get(FluxNetworksIds.INPUT).get();
            Block output = ModBlocks.BLOCKS.get(FluxNetworksIds.OUTPUT).get();
            assertThat(alternativeBlocks(InterfacePredicates.anyOfEnergyInput())).contains(input).doesNotContain(output);
            assertThat(alternativeBlocks(InterfacePredicates.anyOfEnergyOutput())).contains(output).doesNotContain(input);
        } finally {
            ModBlocks.BLOCKS.clear();
            ModBlocks.BLOCKS.putAll(previousBlocks);
            PortKinds.clearForTesting();
        }
    }

    private static Block registeredBlock(ResourceLocation id, IOPortKind kind) {
        MappedRegistry<Block> blocks = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        if (blocks.containsKey(id)) return blocks.get(id);
        blocks.unfreeze();
        try {
            return Registry.register(blocks, id, kind.createBlock(Blocks.IRON_BLOCK.properties(), () -> BlockEntityType.CHEST));
        } finally {
            blocks.freeze();
        }
    }

    private static List<Block> alternativeBlocks(cn.howxu.mmcr.api.machine.definition.BlockPredicate predicate) {
        if (predicate.blockSupplier().isPresent()) return List.of(predicate.blockSupplier().orElseThrow().get());
        return predicate.alternatives().stream().flatMap(child -> alternativeBlocks(child).stream()).toList();
    }
}
