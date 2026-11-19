package cn.howxu.mmcr.internal.event;

import io.netty.buffer.Unpooled;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.api.port.PortTierPolicy;
import cn.howxu.mmcr.api.capability.type.CapabilityCreationContext;
import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.internal.menu.InputIsolationMenuState;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.port.InputIsolationHost;
import cn.howxu.mmcr.api.capability.facet.InputRoutingFacet;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.external.ExternalCapabilityContext;
import cn.howxu.mmcr.api.capability.external.ExternalPortHost;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.CompiledMachinePattern;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachinePatternCompiler;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.port.MachinePort;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.storage.LongResourceStorage;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.world.level.Level;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the actual deferred factories and registered providers in an isolated startup process.
 * @author howxu <dev@howxu.cn>
 */
public final class IndependentPortFixture {
    private static final Kind KIND = new Kind();

    private IndependentPortFixture() {}

    public static void verify() throws Exception {
        PortKinds.register(KIND);
        var prefixed = new Kind() {
            @Override public String id() { return "item_input_bus_probe"; }
            @Override public Block createBlock(BlockBehaviour.Properties properties, Supplier<? extends BlockEntityType<?>> type) {
                return new IOPortBlock(this, type, properties);
            }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Level level, BlockPos pos) {
                return new HopperMenu(id, inventory);
            }
            @Override public void writeOpenData(FriendlyByteBuf buffer, BlockPos pos) {
                buffer.writeBlockPos(pos);
                buffer.writeUtf(id());
            }
        };
        PortKinds.register(prefixed);
        var machineId = MMCR.id("independent_host_machine");
        ModBlocks.registerMachineControllers(List.of(machineId));
        ModBlockEntities.registerMachineControllers(List.of(machineId));
        var bus = BusBuilder.builder().build();
        ModBlocks.register(bus);
        ModBlockEntities.register(bus);
        postRegistry(bus, BuiltInRegistries.BLOCK);
        postRegistry(bus, BuiltInRegistries.BLOCK_ENTITY_TYPE);

        var shell = (IOPortBlock) ModBlocks.BLOCKS.get(prefixed.id()).get();
        var shellState = shell.defaultBlockState();
        var shellPos = new BlockPos(0, 0, 1);
        var shellOwner = shell.newBlockEntity(shellPos, shellState);
        var shellLevel = LevelStub.create(Map.of(shellPos, shell), List.of(shellOwner));
        var provider = shellState.getMenuProvider(shellLevel, shellPos);
        var menu = provider.createMenu(17, new Inventory(null, null), null);
        assertThat(menu).isInstanceOf(HopperMenu.class);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            provider.writeClientSideData(menu, buffer);
            assertThat(buffer.readBlockPos()).isEqualTo(shellPos);
            assertThat(buffer.readUtf()).isEqualTo(prefixed.id());
            assertThat(buffer.isReadable()).isFalse();
        } finally {
            buffer.release();
        }

        var block = (PortBlock) ModBlocks.BLOCKS.get(KIND.id()).get();
        var pos = new BlockPos(1, 0, 0);
        var port = (PortEntity) block.newBlockEntity(pos, block.defaultBlockState());
        assertThat(port.getType()).isSameAs(BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(MMCR.id(KIND.id())));
        var controllerBlock = ModBlocks.controllerFor(machineId).get();
        var controller = new MachineControllerBlockEntity(BlockPos.ZERO, controllerBlock.defaultBlockState());
        var level = LevelStub.create(Map.of(BlockPos.ZERO, controllerBlock, pos, block), List.of(controller, port));
        controller.setLevel(level);
        port.setLevel(level);
        var pattern = new BlockArray(Map.of(pos, new BlockPredicate.OfBlock(block)));
        var compiled = MachinePatternCompiler.compile(new DynamicMachine(machineId, "probe", pattern));
        assertThat(compiled.portPositions(Direction.SOUTH)).containsExactly(pos);
        assertThat(compiled.componentPositions(Direction.SOUTH)).containsExactly(pos);
        var count = MachineControllerBlockEntity.class.getDeclaredMethod("countPorts", BlockArray.class, CompiledMachinePattern.class, Direction.class);
        var kinds = MachineControllerBlockEntity.class.getDeclaredMethod("portKinds", BlockArray.class, CompiledMachinePattern.class, Direction.class);
        count.setAccessible(true);
        kinds.setAccessible(true);
        var counts = (PortRequirementSpec.PortCounts) count.invoke(controller, pattern, compiled, Direction.SOUTH);
        assertThat(counts.count(KIND.id())).isOne();
        assertThat(counts.count("item_input_bus")).isOne();
        @SuppressWarnings("unchecked") var detected = (List<IOPortKind>) kinds.invoke(controller, pattern, compiled, Direction.SOUTH);
        assertThat(PortTierRequirementSpec.builder().anyItemInput().build().validate(detected)).isEmpty();

        var routing = controller.componentRuntime();
        routing.replaceComponents(List.of(new ProcessingComponent(null, port, pos, pos, (String) null)));
        var settings = InputIsolationMenuState.forPort(port);
        port.setInputIsolated(true);
        routing.refreshInputRouting(false);
        assertThat(settings.supported() && settings.enabled()).isTrue();
        assertThat(routing.capabilitySnapshot().facets(InputRoutingFacet.class).getFirst().inputPorts())
                .containsExactly(new InputRoutingFacet.Port(pos, true));

        var ctor = RegisterCapabilitiesEvent.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        var adapters = new ExternalCapabilityContext();
        var calls = new AtomicInteger();
        var view = BlockCapability.createSided(MMCR.id("independent_view"), String.class);
        adapters.bind(KIND.bindings().getFirst().type(), new CapabilityBinding.ExternalExposure<>(view.name(), String.class,
                (host, _, _) -> { assertThat(host).isSameAs(port); calls.incrementAndGet(); return "open"; }));
        ModCapabilities.registerNativePort(ctor.newInstance(), KIND, adapters);
        assertThat(view.getCapability(level, pos, null, null, Direction.NORTH)).isEqualTo("open");
        assertThat(ModCapabilities.ITEM_BLOCK.getCapability(level, pos, null, null, null)).isNotNull();
        assertThat(view.getCapability(level, pos, null, null, Direction.SOUTH)).isNull();
        assertThat(ModCapabilities.ITEM_BLOCK.getCapability(level, pos, null, null, Direction.SOUTH)).isNull();
        port.snapshot = new CapabilitySnapshot(List.of(port.item(IOType.OUTPUT)));
        assertThat(view.getCapability(level, pos, null, null, Direction.NORTH)).isNull();
        assertThat(ModCapabilities.ITEM_BLOCK.getCapability(level, pos, null, null, Direction.NORTH)).isNull();
        assertThat(calls.get()).isOne();
    }

    private static void postRegistry(IEventBus bus, Registry<?> registry) throws Exception {
        var ctor = RegisterEvent.class.getDeclaredConstructor(ResourceKey.class, Registry.class);
        ctor.setAccessible(true);
        ((MappedRegistry<?>) registry).unfreeze(true);
        bus.post(ctor.newInstance(registry.key(), registry));
        ((MappedRegistry<?>) registry).freeze();
    }

    /** @author howxu <dev@howxu.cn> */
    private static class Kind implements IOPortKind {
        @Override public String id() { return "independent_item_input"; }
        @Override public IOType ioType() { return IOType.INPUT; }
        @Override public PortDefinition definition() {
            return PortDefinition.of(MMCR.id(id()), new CapabilityBinding(BuiltinCapabilityDefinitions.ITEM_TYPE,
                    CapabilityDirections.input(), ctx -> ((PortEntity) ctx.host()).item(ctx.ioType()), PortTierPolicy.always()));
        }
        @Override public List<PortFamilyDescriptor> families() { return PortKinds.ITEM_INPUT.families(); }
        @Override public BlockEntityType.BlockEntitySupplier<PortEntity> entityFactory() { return PortEntity::new; }
        @Override public Block createBlock(BlockBehaviour.Properties properties, Supplier<? extends BlockEntityType<?>> type) {
            return new PortBlock(properties, type);
        }
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class PortBlock extends Block implements MachinePort, EntityBlock {
        private final Supplier<? extends BlockEntityType<?>> type;
        private PortBlock(Properties properties, Supplier<? extends BlockEntityType<?>> type) { super(properties); this.type = type; }
        @Override public IOPortKind kind() { return KIND; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return type.get().create(pos, state); }
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class PortEntity extends BlockEntity implements MachinePort, ExternalPortHost, InputIsolationHost, CapabilityCreationContext {
        private boolean isolated;
        private final LongResourceStorage<ItemResource> storage = new LongResourceStorage<>(
                ItemResource.class, 1, 64, ItemResource::isEmpty, this::setChanged);
        private CapabilitySnapshot snapshot = new CapabilitySnapshot(List.of(kind().bindings().getFirst().factory().create(this)));
        private PortEntity(BlockPos pos, BlockState state) {
            super(ModBlockEntities.BES.get(((MachinePort) state.getBlock()).kind().id()).get(), pos, state);
        }
        private ItemBusCapability item(IOType direction) { return new ItemBusCapability(storage, direction); }
        @Override public CapabilityHost host() { return this; }
        @Override public <T> Optional<T> service(Class<T> type) { return Optional.empty(); }
        @Override public Runnable onChanged() { return this::setChanged; }
        @Override public IOPortKind kind() { return ((MachinePort) getBlockState().getBlock()).kind(); }
        @Override public IOType ioType() { return kind().ioType(); }
        @Override public boolean supportsInputIsolation() { return true; }
        @Override public boolean isInputIsolated() { return isolated; }
        @Override public void setInputIsolated(boolean enabled) { isolated = enabled; }
        @Override public boolean isNativeSideExposed(CapabilityBinding binding, Direction side) { return side == null || side == Direction.NORTH; }
        @Override public CapabilitySnapshot capabilitySnapshot() { return snapshot; }
    }
}
