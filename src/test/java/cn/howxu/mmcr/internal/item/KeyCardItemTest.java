package cn.howxu.mmcr.internal.item;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachinePatternCompiler;
import cn.howxu.mmcr.api.machine.NetworkInterfaceSpec;
import cn.howxu.mmcr.api.network.KeyCardBinding;
import cn.howxu.mmcr.api.network.MachineReference;
import cn.howxu.mmcr.internal.multiblock.NetworkInterfaceBindingCoordinator;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerRuntime;
import cn.howxu.mmcr.internal.tile.NetworkInterfaceBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModDataComponents;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mojang.authlib.GameProfile;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies key-card endpoint selection and connection behavior.
 * @author howxu <dev@howxu.cn>
 */
class KeyCardItemTest {
    private static KeyCardItem keyCard;

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bind(ModDataComponents.KEY_CARD_BINDING, DataComponentType.<KeyCardBinding>builder()
                .persistent(KeyCardBinding.CODEC)
                .networkSynchronized(KeyCardBinding.STREAM_CODEC)
                .build());
        keyCard = (KeyCardItem) registerItem(ModItems.KEY_CARD);
    }

    @Test
    void shift_right_click_stores_and_replaces_the_interface_and_machine_reference() throws Exception {
        Fixture fixture = fixture(true, true);
        ItemStack stack = stack();
        TestPlayer player = player();
        player.setShiftKeyDown(true);

        assertThat(useOn(fixture, player, stack, fixture.sourceEndpoint)).isEqualTo(InteractionResult.SUCCESS);
        assertThat(stack.get(ModDataComponents.KEY_CARD_BINDING.get()))
                .isEqualTo(new KeyCardBinding(fixture.sourceEndpoint, fixture.sourceMachine));

        assertThat(useOn(fixture, player, stack, fixture.targetEndpoint)).isEqualTo(InteractionResult.SUCCESS);
        assertThat(stack.get(ModDataComponents.KEY_CARD_BINDING.get()))
                .isEqualTo(new KeyCardBinding(fixture.targetEndpoint, fixture.targetMachine));
        assertThat(player.messages).contains(Component.translatable("message.mmcr.key_card.selection_updated",
                fixture.targetEndpoint.pos().toShortString()));
    }

    @Test
    void only_shift_right_click_air_clears_the_binding() throws Exception {
        Fixture fixture = fixture(true, true);
        ItemStack stack = stack(new KeyCardBinding(fixture.sourceEndpoint, fixture.sourceMachine));
        TestPlayer player = player();
        player.hold(stack);

        assertThat(keyCard.use(fixture.level, player, InteractionHand.MAIN_HAND)).isEqualTo(InteractionResult.PASS);
        assertThat(stack.get(ModDataComponents.KEY_CARD_BINDING.get())).isNotNull();

        player.setShiftKeyDown(true);
        assertThat(keyCard.use(fixture.level, player, InteractionHand.MAIN_HAND)).isEqualTo(InteractionResult.SUCCESS);
        assertThat(stack.get(ModDataComponents.KEY_CARD_BINDING.get())).isNull();
        assertThat(player.messages).contains(Component.translatable("message.mmcr.key_card.cleared"));
    }

    @Test
    void successful_connection_keeps_the_binding_and_does_not_consume_the_card() throws Exception {
        Fixture fixture = fixture(true, true);
        ItemStack stack = stack(new KeyCardBinding(fixture.sourceEndpoint, fixture.sourceMachine));
        TestPlayer player = player();
        int count = stack.getCount();

        assertThat(useOn(fixture, player, stack, fixture.targetEndpoint)).isEqualTo(InteractionResult.SUCCESS);
        assertThat(stack.getCount()).isEqualTo(count);
        assertThat(stack.get(ModDataComponents.KEY_CARD_BINDING.get()))
                .isEqualTo(new cn.howxu.mmcr.api.network.KeyCardBinding(fixture.sourceEndpoint, fixture.sourceMachine));
        assertThat(fixture.sourceNetwork.connections()).hasSize(1);
        assertThat(fixture.targetNetwork.connections()).hasSize(1);
    }

    @Test
    void failed_connections_keep_the_binding_and_report_the_typed_result() throws Exception {
        Fixture mismatch = fixture(true, true);
        ItemStack mismatchStack = stack(new KeyCardBinding(mismatch.sourceEndpoint,
                new MachineReference(mismatch.sourceMachine.type(), mismatch.sourceMachine.hash() + 1L)));
        TestPlayer mismatchPlayer = player();

        assertThat(useOn(mismatch, mismatchPlayer, mismatchStack, mismatch.targetEndpoint)).isEqualTo(InteractionResult.SUCCESS);
        assertThat(mismatchStack.get(ModDataComponents.KEY_CARD_BINDING.get())).isNotNull();
        assertThat(mismatchPlayer.messages).contains(Component.translatable(
                "message.mmcr.key_card.result.source_identity_mismatch"));

        Fixture rejected = fixture(false, true);
        ItemStack rejectedStack = stack(new KeyCardBinding(rejected.sourceEndpoint,
                rejected.sourceMachine));
        TestPlayer rejectedPlayer = player();

        assertThat(useOn(rejected, rejectedPlayer, rejectedStack, rejected.targetEndpoint)).isEqualTo(InteractionResult.SUCCESS);
        assertThat(rejectedStack.get(ModDataComponents.KEY_CARD_BINDING.get())).isNotNull();
        assertThat(rejectedPlayer.messages).contains(Component.translatable(
                "message.mmcr.key_card.result.allowlist_rejected"));
    }

    @Test
    void absent_selection_is_reported_without_connecting() throws Exception {
        Fixture fixture = fixture(true, true);
        ItemStack stack = stack();
        TestPlayer player = player();

        assertThat(useOn(fixture, player, stack, fixture.targetEndpoint)).isEqualTo(InteractionResult.SUCCESS);
        assertThat(player.messages).contains(Component.translatable("message.mmcr.key_card.not_selected"));
        assertThat(fixture.targetNetwork.connections()).isEmpty();
    }

    @Test
    void unrelated_blocks_are_not_handled() throws Exception {
        Fixture fixture = fixture(true, true);
        TestPlayer player = player();

        assertThat(useOn(fixture, player, stack(), GlobalPos.of(Level.OVERWORLD, new BlockPos(100, 64, 100))))
                .isEqualTo(InteractionResult.PASS);
    }

    @Test
    void client_side_use_does_not_change_the_card() throws Exception {
        Fixture fixture = fixture(true, true);
        setField(Level.class, fixture.level, "isClientSide", true);
        ItemStack stack = stack();
        TestPlayer player = player();
        player.hold(stack);
        player.setShiftKeyDown(true);

        assertThat(useOn(fixture, player, stack, fixture.sourceEndpoint)).isEqualTo(InteractionResult.SUCCESS);
        assertThat(stack.get(ModDataComponents.KEY_CARD_BINDING.get())).isNull();
        assertThat(keyCard.use(fixture.level, player, InteractionHand.MAIN_HAND)).isEqualTo(InteractionResult.SUCCESS);
        assertThat(stack.get(ModDataComponents.KEY_CARD_BINDING.get())).isNull();
    }

    private static ItemStack stack(KeyCardBinding binding) {
        ItemStack stack = stack();
        stack.set(ModDataComponents.KEY_CARD_BINDING.get(), binding);
        return stack;
    }

    private static ItemStack stack() {
        return new ItemStack(keyCard);
    }

    private static InteractionResult useOn(Fixture fixture, TestPlayer player, ItemStack stack, GlobalPos position) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(position.pos()), Direction.UP, position.pos(), false);
        return keyCard.useOn(new net.minecraft.world.item.context.UseOnContext(fixture.level, player,
                InteractionHand.MAIN_HAND, stack, hit));
    }

    private static Fixture fixture(boolean sourceAllowsTarget, boolean targetAllowsSource) throws Exception {
        TestServerLevel level = allocate(TestServerLevel.class);
        level.blockEntities = new HashMap<>();
        level.blocks = new HashMap<>();
        setField(Level.class, level, "dimension", Level.OVERWORLD);
        setField(ServerLevel.class, level, "players", List.of());
        MinecraftServer server = allocate(DedicatedServer.class);
        setField(MinecraftServer.class, server, "levels", Map.of(Level.OVERWORLD, level));
        level.server = server;

        BlockPos sourceControllerPos = new BlockPos(0, 64, 0);
        BlockPos sourceInterfacePos = new BlockPos(1, 64, 0);
        BlockPos targetControllerPos = new BlockPos(32, 64, 0);
        BlockPos targetInterfacePos = new BlockPos(33, 64, 0);
        Machine source = machine(MMCR.id("source"), sourceAllowsTarget ? Set.of(MMCR.id("target")) : Set.of());
        Machine target = machine(MMCR.id("target"), targetAllowsSource ? Set.of(MMCR.id("source")) : Set.of());
        MachineControllerBlockEntity sourceController = controller(sourceControllerPos, source, level, sourceInterfacePos);
        MachineControllerBlockEntity targetController = controller(targetControllerPos, target, level, targetInterfacePos);
        NetworkInterfaceBlockEntity sourceNetwork = createInterface(sourceInterfacePos);
        NetworkInterfaceBlockEntity targetNetwork = createInterface(targetInterfacePos);
        sourceNetwork.setLevel(level);
        targetNetwork.setLevel(level);
        level.blockEntities.put(sourceInterfacePos, sourceNetwork);
        level.blockEntities.put(targetInterfacePos, targetNetwork);
        GlobalPos sourceOwner = global(sourceControllerPos);
        GlobalPos targetOwner = global(targetControllerPos);
        sourceNetwork.claimOwner(sourceOwner);
        targetNetwork.claimOwner(targetOwner);
        return new Fixture(level, sourceNetwork, targetNetwork,
                GlobalPos.of(Level.OVERWORLD, sourceInterfacePos), GlobalPos.of(Level.OVERWORLD, targetInterfacePos),
                sourceController.machineReference(), targetController.machineReference());
    }

    private static NetworkInterfaceBlockEntity createInterface(BlockPos pos) {
        BlockEntity entity = ModBlockEntities.NETWORK_INTERFACE.get().create(pos,
                ModBlocks.NETWORK_INTERFACE.get().defaultBlockState());
        return (NetworkInterfaceBlockEntity) entity;
    }

    private static MachineControllerBlockEntity controller(BlockPos pos, Machine machine, TestServerLevel level,
                                                            BlockPos interfacePos) throws Exception {
        MachineControllerBlockEntity controller = cn.howxu.mmcr.test.RuntimeTestFixtures.controllerEntity(
                MMCR.id("test_cube"), pos);
        controller.setLevel(level);
        level.blocks.put(pos, controller.getBlockState());
        publishFormed(controller, machine);
        setField(controller, "activeNetworkInterfacePositions", Set.of(interfacePos));
        level.blockEntities.put(pos, controller);
        return controller;
    }

    private static void publishFormed(MachineControllerBlockEntity controller, Machine machine) throws Exception {
        Field runtimeField = MachineControllerBlockEntity.class.getDeclaredField("runtime");
        runtimeField.setAccessible(true);
        MachineControllerRuntime runtime = (MachineControllerRuntime) runtimeField.get(controller);
        Method publishFormationState = MachineControllerRuntime.class.getDeclaredMethod("publishFormationState",
                Machine.class, BlockArray.class, cn.howxu.mmcr.api.machine.CompiledMachinePattern.class,
                Direction.class, Direction.class, int.class);
        publishFormationState.setAccessible(true);
        publishFormationState.invoke(runtime, machine, machine.pattern(), MachinePatternCompiler.compile(machine),
                Direction.SOUTH, Direction.NORTH, 1);
    }

    private static Machine machine(Identifier id, Set<Identifier> allowedMachines) {
        return new Machine() {
            @Override public Identifier registryName() { return id; }
            @Override public BlockArray pattern() { return new BlockArray(Map.of()); }
            @Override public MachineControllerSpec controller() { return MachineControllerSpec.defaultsFor(id); }
            @Override public NetworkInterfaceSpec networkInterface() {
                return new NetworkInterfaceSpec(1, 2, allowedMachines);
            }
        };
    }

    private static GlobalPos global(BlockPos pos) {
        return GlobalPos.of(Level.OVERWORLD, pos);
    }

    private static void bind(Object deferredHolder, Object value) throws Exception {
        Field holder = null;
        for (Class<?> type = deferredHolder.getClass(); type != null && holder == null; type = type.getSuperclass()) {
            try {
                holder = type.getDeclaredField("holder");
            } catch (NoSuchFieldException ignored) {
            }
        }
        if (holder == null) throw new NoSuchFieldException("holder");
        holder.setAccessible(true);
        holder.set(deferredHolder, net.minecraft.core.Holder.direct(value));
    }

    @SuppressWarnings("unchecked")
    private static Item registerItem(net.neoforged.neoforge.registries.DeferredHolder<Item, Item> itemHolder) throws Exception {
        if (BuiltInRegistries.ITEM.containsKey(itemHolder.getId())) return BuiltInRegistries.ITEM.getValue(itemHolder.getId());
        MappedRegistry<Item> items = (MappedRegistry<Item>) BuiltInRegistries.ITEM;
        items.unfreeze(true);
        Field entriesField = ModItems.REGISTER.getClass().getSuperclass().getDeclaredField("entries");
        entriesField.setAccessible(true);
        Map<net.neoforged.neoforge.registries.DeferredHolder<Item, ? extends Item>, java.util.function.Supplier<? extends Item>> entries =
                (Map<net.neoforged.neoforge.registries.DeferredHolder<Item, ? extends Item>, java.util.function.Supplier<? extends Item>>) entriesField.get(ModItems.REGISTER);
        Item item = entries.get(itemHolder).get();
        Registry.register(BuiltInRegistries.ITEM, itemHolder.getId(), item);
        items.freeze();
        item.builtInRegistryHolder().bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 1).build());
        return item;
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (T) ((sun.misc.Unsafe) field.get(null)).allocateInstance(type);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                setField(type, target, name, value);
                return;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void setField(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static TestPlayer player() throws Exception {
        return allocate(TestPlayer.class);
    }

    private static final class Fixture {
        private final TestServerLevel level;
        private final NetworkInterfaceBlockEntity sourceNetwork;
        private final NetworkInterfaceBlockEntity targetNetwork;
        private final GlobalPos sourceEndpoint;
        private final GlobalPos targetEndpoint;
        private final MachineReference sourceMachine;
        private final MachineReference targetMachine;

        private Fixture(TestServerLevel level, NetworkInterfaceBlockEntity sourceNetwork,
                        NetworkInterfaceBlockEntity targetNetwork, GlobalPos sourceEndpoint, GlobalPos targetEndpoint,
                        MachineReference sourceMachine, MachineReference targetMachine) {
            this.level = level;
            this.sourceNetwork = sourceNetwork;
            this.targetNetwork = targetNetwork;
            this.sourceEndpoint = sourceEndpoint;
            this.targetEndpoint = targetEndpoint;
            this.sourceMachine = sourceMachine;
            this.targetMachine = targetMachine;
        }
    }

    private static final class TestPlayer extends Player {
        private ItemStack held;
        private java.util.ArrayList<Component> messages;
        private boolean shift;

        private TestPlayer(Level level) {
            super(level, new GameProfile(UUID.randomUUID(), "key-card-test"));
        }

        private void hold(ItemStack stack) {
            held = stack;
            if (messages == null) messages = new java.util.ArrayList<>();
        }
        @Override public void setShiftKeyDown(boolean shiftKeyDown) { shift = shiftKeyDown; }
        @Override public boolean isShiftKeyDown() { return shift; }
        @Override public ItemStack getItemInHand(InteractionHand hand) { return held; }
        @Override public void sendSystemMessage(Component message) {
            if (messages == null) messages = new java.util.ArrayList<>();
            messages.add(message);
        }
        @Override public GameType gameMode() { return GameType.SURVIVAL; }
    }

    private static final class TestServerLevel extends ServerLevel {
        private Map<BlockPos, BlockEntity> blockEntities;
        private Map<BlockPos, BlockState> blocks;
        private MinecraftServer server;

        private TestServerLevel() {
            super(null, null, null, null, Level.OVERWORLD, null, false, 0L, List.of(), false);
        }

        @Override public MinecraftServer getServer() { return server; }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return blockEntities.get(pos); }
        @Override public boolean hasChunk(int chunkX, int chunkZ) { return true; }
        @Override public BlockState getBlockState(BlockPos pos) { return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
        @Override public void blockEntityChanged(BlockPos pos) { }
        @Override public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) { }
    }
}
