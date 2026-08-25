package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
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
                FluidStack.EMPTY, FluidStack.EMPTY);
    }

    private static TestServerLevel serverLevel(MachineControllerBlockEntity controller) throws Exception {
        TestServerLevel level = (TestServerLevel) unsafe().allocateInstance(TestServerLevel.class);
        level.controller = controller;
        return level;
    }

    private static ServerPlayer player(ServerLevel level, BlockPos pos) throws Exception {
        ServerPlayer player = (ServerPlayer) unsafe().allocateInstance(ServerPlayer.class);
        setField(Entity.class, player, "level", level);
        setField(Entity.class, player, "position", Vec3.atCenterOf(pos));
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

    private static void bind(Object deferredHolder, MenuType<FactoryControllerMenu> menuType) throws Exception {
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
        private MachineControllerBlockEntity controller;

        private TestServerLevel() {
            super(null, null, null, null, Level.OVERWORLD, null, false, 0L, List.of(), false);
        }

        @Override public BlockState getBlockState(BlockPos pos) {
            return controller != null && controller.getBlockPos().equals(pos)
                    ? ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                    : Blocks.AIR.defaultBlockState();
        }

        @Override public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos) {
            return controller != null && controller.getBlockPos().equals(pos) ? controller : null;
        }
    }
}
