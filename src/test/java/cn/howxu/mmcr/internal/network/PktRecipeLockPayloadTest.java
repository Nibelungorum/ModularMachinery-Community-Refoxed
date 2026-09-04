package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.TestBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the final server-owned recipe-lock payload boundary.
 *
 * @author howxu <dev@howxu.cn>
 */
class PktRecipeLockPayloadTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bind(ModUIs.FACTORY_CONTROLLER,
                new MenuType<>((containerId, inventory) -> FactoryControllerMenu.clientOpen(containerId, inventory),
                        FeatureFlags.VANILLA_SET));
        bind(ModUIs.MACHINE_CONTROLLER,
                new MenuType<>((containerId, inventory) -> MachineControllerMenu.clientOpen(containerId, inventory),
                        FeatureFlags.VANILLA_SET));
    }

    @AfterEach
    void clearRecipes() {
        RecipeRegistry.clearForTesting();
    }

    @Test
    void payload_round_trips_controller_position_and_thread_index() {
        PktRecipeLockPayload payload = new PktRecipeLockPayload(new BlockPos(3, 4, 5), 7);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY,
                ConnectionType.NEOFORGE);

        PktRecipeLockPayload.STREAM_CODEC.encode(buffer, payload);
        PktRecipeLockPayload decoded = PktRecipeLockPayload.STREAM_CODEC.decode(buffer);

        assertThat(decoded).isEqualTo(payload);
        buffer.release();
    }

    @Test
    void server_handler_rejects_a_missing_player_or_payload() {
        assertThat(PktRecipeLockPayload.toggleOnServer(null, null)).isFalse();
        assertThat(PktRecipeLockPayload.toggleOnServer(null,
                new PktRecipeLockPayload(BlockPos.ZERO, 0))).isFalse();
    }

    @Test
    void invalid_thread_index_does_not_toggle_the_base_lane() {
        FactoryRuntime runtime = new FactoryRuntime();
        runtime.ensureBaseLane(RuntimeTestFixtures.controller(MMCR.id("test_cube")));

        assertThat(runtime.toggleRecipeLock(-1)).isFalse();
        assertThat(runtime.toggleRecipeLock(999)).isFalse();
        assertThat(runtime.threadSnapshots().getFirst().locked()).isFalse();
    }

    @Test
    void server_handler_checks_formed_distance_menu_ownership_and_thread_status() throws Exception {
        BlockPos controllerPos = new BlockPos(1, 2, 3);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos);
        DynamicMachine machine = new DynamicMachine(MMCR.id("recipe_lock_boundary"), "Recipe Lock Boundary",
                new BlockArray(Map.of()), MachineControllerSpec.defaultsFor(MMCR.id("recipe_lock_boundary")),
                cn.howxu.mmcr.api.machine.PortRequirementSpec.none(), List.of(), Map.of(), 1, false, true, 1);
        RuntimeTestFixtures.publishStructure(controller, machine, true);
        TestServerLevel level = serverLevel(controller);
        controller.setLevel(level);
        ServerPlayer player = player(level, controllerPos);
        FactoryControllerMenu menu = new FactoryControllerMenu(1, new Inventory(null, null), controller);
        player.containerMenu = menu;
        PktRecipeLockPayload invalidIndex = new PktRecipeLockPayload(controllerPos, -1);

        assertThat(PktRecipeLockPayload.toggleOnServer(player, invalidIndex)).isFalse();
        assertThat(controller.factoryThreadSnapshots()).allMatch(thread -> !thread.locked());

        setField(Entity.class, player, "position",
                new Vec3(controllerPos.getX() + 100, controllerPos.getY(), controllerPos.getZ()));
        assertThat(PktRecipeLockPayload.toggleOnServer(player, invalidIndex)).isFalse();

        setField(Entity.class, player, "position", Vec3.atCenterOf(controllerPos));
        player.containerMenu = new AbstractContainerMenu(null, 1) {
            @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player ignored, int index) {
                return ItemStack.EMPTY;
            }

            @Override public boolean stillValid(net.minecraft.world.entity.player.Player ignored) {
                return true;
            }
        };
        assertThat(PktRecipeLockPayload.toggleOnServer(player, invalidIndex)).isFalse();

        player.containerMenu = new FactoryControllerMenu(2, new Inventory(null, null),
                RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos));
        assertThat(PktRecipeLockPayload.toggleOnServer(player, invalidIndex)).isFalse();

        controller.setFormed(false);
        player.containerMenu = menu;
        assertThat(PktRecipeLockPayload.toggleOnServer(player, invalidIndex)).isFalse();
    }

    @Test
    void server_handler_accepts_a_valid_formed_controller_thread_and_publishes_lock_state() throws Exception {
        BlockPos controllerPos = new BlockPos(1, 2, 3);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos);
        BlockArray pattern = new BlockArray(Map.of(new BlockPos(1, 0, 0),
                new cn.howxu.mmcr.api.machine.BlockPredicate.OfBlock(Blocks.IRON_BLOCK)));
        DynamicMachine machine = new DynamicMachine(MMCR.id("recipe_lock_success"), "Recipe Lock Success",
                pattern,
                MachineControllerSpec.defaultsFor(MMCR.id("recipe_lock_success")));
        RuntimeTestFixtures.formStructure(controller, machine);
        TestServerLevel level = serverLevel(controller);
        controller.setLevel(level);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("recipe_lock_success_recipe"), machine.registryName(), 20,
                List.of(), List.of());
        RecipeRegistry.register(recipe);
        controller.tickRuntimeWork(level, controllerPos);

        assertThat(controller.runtimeSnapshot().crafting().recipeId()).isEqualTo(recipe.id());

        ServerPlayer player = player(level, controllerPos);
        player.containerMenu = new MachineControllerMenu(1, new Inventory(null, null), controller);
        PktRecipeLockPayload payload = new PktRecipeLockPayload(controllerPos, 0);

        assertThat(PktRecipeLockPayload.toggleOnServer(player, payload)).isTrue();
        assertThat(controller.recipeLocked()).isTrue();
        assertThat(controller.lockedRecipeId()).isEqualTo(recipe.id());
        assertThat(PktMachineStatePayload.from(controllerPos, controller.runtimeSnapshot()).recipeLocked()).isTrue();

        assertThat(PktRecipeLockPayload.toggleOnServer(player, payload)).isTrue();
        assertThat(controller.recipeLocked()).isFalse();
        assertThat(PktMachineStatePayload.from(controllerPos, controller.runtimeSnapshot()).recipeLocked()).isFalse();
    }

    @Test
    void machine_state_payload_preserves_locked_recipe_id_and_detects_lock_changes() {
        String recipeId = "other_namespace:recipe_with_a_long_id";
        PktMachineStatePayload locked = machineState(true, recipeId);
        PktMachineStatePayload unlocked = machineState(false, "");

        assertThat(locked.lockedRecipeId()).isEqualTo(recipeId);
        assertThat(PktMachineStatePayload.stateChanged(locked, unlocked)).isTrue();
    }

    private static PktMachineStatePayload machineState(boolean locked, String recipeId) {
        return new PktMachineStatePayload(new BlockPos(1, 2, 3), "", true, false,
                List.of(), locked, recipeId, "", 0, 0, false, "", CraftingStatus.Status.IDLE, "", null,
                true, false, 0, 0, 1, 1, false, 0, 0, 0, 0, 0, 0,
                 FluidStack.EMPTY, FluidStack.EMPTY, Map.of());
    }

    private static TestServerLevel serverLevel(MachineControllerBlockEntity controller) throws Exception {
        TestServerLevel level = (TestServerLevel) unsafe().allocateInstance(TestServerLevel.class);
        level.controller = controller;
        level.controllerState = controller.getBlockState();
        setField(ServerLevel.class, level, "players", List.of());
        return level;
    }

    private static ServerPlayer player(ServerLevel level, BlockPos pos) throws Exception {
        ServerPlayer player = (ServerPlayer) unsafe().allocateInstance(ServerPlayer.class);
        setField(Entity.class, player, "level", level);
        setField(Entity.class, player, "position", Vec3.atCenterOf(pos));
        player.connection = (ServerGamePacketListenerImpl) unsafe().allocateInstance(TestConnection.class);
        return player;
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void setField(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = null;
        while (type != null && field == null) {
            try {
                field = type.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        if (field == null) throw new NoSuchFieldException(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void bind(Object deferredHolder, MenuType<?> menuType) throws Exception {
        Class<?> type = deferredHolder.getClass();
        Field holder = null;
        while (type != null && holder == null) {
            try {
                holder = type.getDeclaredField("holder");
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        if (holder == null) throw new NoSuchFieldException("holder");
        holder.setAccessible(true);
        holder.set(deferredHolder, Holder.direct(menuType));
    }

    private static final class TestServerLevel extends ServerLevel {
        private static final RecipeManager RECIPE_MANAGER = new RecipeManager(RegistryAccess.EMPTY);
        private static final LevelData LEVEL_DATA = (LevelData) Proxy.newProxyInstance(
                LevelData.class.getClassLoader(), new Class<?>[]{LevelData.class},
                (proxy, method, arguments) -> method.getName().equals("getGameTime") ? 1L : null);
        private MachineControllerBlockEntity controller;
        private BlockState controllerState;

        private TestServerLevel() {
            super(null, null, null, null, Level.OVERWORLD, null, false, 0L, List.of(), false);
        }

        @Override public LevelData getLevelData() {
            return LEVEL_DATA;
        }

        @Override public boolean isInValidBounds(BlockPos pos) {
            return true;
        }

        @Override public RecipeManager recipeAccess() {
            return RECIPE_MANAGER;
        }

        @Override public BlockState getBlockState(BlockPos pos) {
            return controller != null && controller.getBlockPos().equals(pos)
                    ? controllerState
                    : Blocks.AIR.defaultBlockState();
        }

        @Override public boolean setBlock(BlockPos pos, BlockState state, int flags) {
            if (controller != null && controller.getBlockPos().equals(pos)) {
                controllerState = state;
                try {
                    setField(net.minecraft.world.level.block.entity.BlockEntity.class, controller, "blockState", state);
                } catch (Exception exception) {
                    throw new AssertionError("Unable to update controller test state", exception);
                }
            }
            return true;
        }

        @Override public boolean hasChunk(int chunkX, int chunkZ) {
            return true;
        }

        @Override public void blockEntityChanged(BlockPos pos) {
        }

        @Override public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
        }

        @Override public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos) {
            return controller != null && controller.getBlockPos().equals(pos) ? controller : null;
        }
    }

    private static final class TestConnection extends ServerGamePacketListenerImpl {
        private TestConnection() {
            super(null, null, null, null);
        }

        @Override public void send(Packet<?> packet) {
        }
    }
}
