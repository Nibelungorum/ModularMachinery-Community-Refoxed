package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineOutputAmount;
import cn.howxu.mmcr.internal.runtime.ControllerRecipePresentation;
import cn.howxu.mmcr.internal.network.PktMachineStatePayload;
import cn.howxu.mmcr.internal.network.PktMachineProgressPayload;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.test.TestBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the final machine controller menu synchronization boundary.
 *
 * @author howxu <dev@howxu.cn>
 */
class MachineControllerMenuTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bind(ModUIs.MACHINE_CONTROLLER,
                new MenuType<>((containerId, inventory) -> new MachineControllerMenu(containerId, inventory),
                        FeatureFlags.VANILLA_SET));
    }

    @Test
    void client_menu_applies_machine_state_and_connected_host_from_the_final_payload() {
        MachineControllerMenu menu = MachineControllerMenu.clientOpen(1, new Inventory(null));

        menu.applyClientSnapshot(new PktMachineStatePayload(new BlockPos(3, 4, 5), "mmcr:recipe", true, true,
                List.of("mmcr:steel"), "mmcr:test_cube", 2, 3, true,
                "mmcr:host", CraftingStatus.Status.CRAFTING, "", null, true, false,
                4, 20, 6, 8, true, 2, 1, 2, 3, Map.of(), 0, 1));

        assertThat(menu.isFormed()).isTrue();
        assertThat(menu.hasActiveRecipe()).isTrue();
        assertThat(menu.machineId()).isEqualTo(MMCR.id("test_cube"));
        assertThat(menu.connectedHostId()).hasValue(MMCR.id("host"));
        assertThat(menu.currentParallelism()).isEqualTo(6);
        assertThat(menu.maxParallelism()).isEqualTo(8);
        assertThat(menu.factoryThreadCount()).isEqualTo(2);
        assertThat(menu.installedModuleCount()).isEqualTo(3);
    }

    @Test
    void client_menu_exposes_the_selected_and_supported_recipe_pools() {
        ResourceLocation machineId = MMCR.id("menu_recipe_pool_machine");
        ResourceLocation firstPool = MMCR.id("menu_recipe_pool_first");
        ResourceLocation secondPool = MMCR.id("menu_recipe_pool_second");
        MachineRegistry.replaceClientRecipePools(Map.of(machineId, List.of(firstPool, secondPool)));
        MachineControllerMenu menu = MachineControllerMenu.clientOpen(1, new Inventory(null));
        menu.applyClientSnapshot(new PktMachineStatePayload(BlockPos.ZERO, "", true, false, List.of(),
                machineId.toString(), 0, 0, false, "", CraftingStatus.Status.IDLE, "", null, true, false,
                0, 0, 0, 1, false, 0, 0, 0, 0, Map.of(), 0, 1, secondPool.toString()));

        assertThat(menu.currentRecipePoolId()).isEqualTo(secondPool);
        assertThat(menu.recipePoolIds()).containsExactly(firstPool, secondPool);
        MachineRegistry.clearClientRecipePools();
    }

    @Test
    void client_menu_keeps_parallel_controller_count_from_payload_after_data_slot_update() {
        MachineControllerMenu menu = MachineControllerMenu.clientOpen(1, new Inventory(null));
        menu.applyClientSnapshot(new PktMachineStatePayload(new BlockPos(3, 4, 5), "mmcr:recipe", true, true,
                List.of(), "mmcr:test_cube", 0, 0, false, "",
                CraftingStatus.Status.CRAFTING, "", null, true, false,
                1, 20, 4, 4, false, 0, 0, 1, 4, Map.of(), 0, 1));

        menu.setData(6, 0);

        assertThat(menu.parallelControllerCount()).isEqualTo(1);
    }

    @Test
    void client_open_round_trips_role_and_machine_identity() {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
        MachineControllerMenu.writeClientOpenData(buffer, new BlockPos(7, 8, 9),
                MMCR.id("test_cube"), MMCR.id("host"), 1, true, 4);

        MachineControllerMenu menu = MachineControllerMenu.clientOpen(1, new Inventory(null), buffer);

        assertThat(menu.controllerPos()).isEqualTo(new BlockPos(7, 8, 9));
        assertThat(menu.machineId()).isEqualTo(MMCR.id("test_cube"));
        assertThat(menu.connectedHostId()).hasValue(MMCR.id("host"));
        assertThat(menu.isHostController()).isTrue();
        assertThat(menu.isFormed()).isTrue();
        assertThat(menu.installedModuleCount()).isEqualTo(4);
        buffer.release();
    }

    @Test
    void metadata_client_menu_keeps_the_open_token_and_inventory_policy_local() {
        var first = MachineControllerMenu.clientOpen(1, new Inventory(null, null));
        var second = MachineControllerMenu.clientOpen(1, new Inventory(null, null));
        var token = first.uiOpenData().sessionId();
        var slots = List.copyOf(first.slots);
        first.setPlayerInventoryVisible(false);
        assertThat(first.uiOpenData().sessionId()).isEqualTo(token);
        assertThat(first.uiServerSession()).isNull();
        assertThat(first.slots).containsExactlyElementsOf(slots);
        assertThat(first.slots).allMatch(slot -> !slot.isActive());
        assertThat(second.playerInventoryVisible()).isTrue();
        first.setPlayerInventoryVisible(true);
        assertThat(first.slots).allMatch(slot -> slot.isActive());
    }

    @Test
    void module_menu_state_keeps_role_and_connected_host_identity_across_payload_updates() {
        MachineControllerMenu menu = MachineControllerMenu.clientOpen(1, new Inventory(null),
                menuBuffer(new BlockPos(7, 8, 9), MMCR.id("module"), MMCR.id("host"), 2, true, 1));

        assertThat(menu.isModuleController()).isTrue();
        assertThat(menu.isHostController()).isFalse();
        assertThat(menu.connectedHostId()).hasValue(MMCR.id("host"));
        assertThat(menu.installedModuleCount()).isEqualTo(1);
        assertThat(MachineControllerMenu.resolvedControllerRole(0, 2, 0)).isEqualTo(2);

        menu.applyClientSnapshot(new PktMachineStatePayload(new BlockPos(7, 8, 9), "", true, false,
                List.of(), "mmcr:module", 2, 0, false, "",
                CraftingStatus.Status.IDLE, "", null, true, false,
                0, 0, 0, 1, false, 0, 0, 0, 0, Map.of(), 0, 1));

        assertThat(menu.isModuleController()).isTrue();
        assertThat(menu.connectedHostId()).isEmpty();
        assertThat(menu.installedModuleCount()).isZero();
    }

    @Test
    void matched_stage_accessor_reads_from_client_snapshot() {
        MachineControllerMenu menu = MachineControllerMenu.clientOpen(1, new Inventory(null));
        menu.applyClientSnapshot(new PktMachineStatePayload(new BlockPos(3, 4, 5), "", true, false,
                List.of(), "", 0, 0, false, "", CraftingStatus.Status.IDLE, "", null,
                true, false, 0, 0, 0, 1, false, 0, 0, 0, 0, Map.of(), 4, 10));

        assertThat(menu.matchedStage()).isEqualTo(4);
        assertThat(menu.stageCount()).isEqualTo(10);
    }

    @Test
    void matched_stage_accessor_falls_back_when_no_snapshot_present() {
        MachineControllerMenu menu = MachineControllerMenu.clientOpen(1, new Inventory(null));

        assertThat(menu.matchedStage()).isZero();
        assertThat(menu.stageCount()).isEqualTo(1);
    }

    @Test
    void progress_requires_a_matching_baseline_and_preserves_component_outputs_and_full_state() {
        MachineControllerMenu menu = MachineControllerMenu.clientOpen(1, new Inventory(null));
        menu.applyClientProgress("mmcr:recipe", 5, 20);
        assertThat(menu.activeRecipeTick()).isZero();
        ItemStack stack = new ItemStack(Items.DIAMOND);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("test output"));
        ControllerRecipePresentation presentation = new ControllerRecipePresentation(
                List.of(new MachineOutputAmount(new MachineOutput.ItemOutput(stack, 1F), Long.MAX_VALUE)),
                1L, 2L, 3D, 20, Long.MAX_VALUE);
        PktMachineStatePayload baseline = new PktMachineStatePayload(BlockPos.ZERO, "mmcr:recipe", true, true,
                List.of("mmcr:steel"), "mmcr:machine", 2, 3, true, "mmcr:host",
                CraftingStatus.Status.CRAFTING, "", null, true, true, 1, 20, Long.MAX_VALUE, Long.MAX_VALUE,
                true, 2, 1, 2, Long.MAX_VALUE, Map.of("mode", DataValue.of("running")), 4, 10,
                presentation, "mmcr:pool");
        menu.applyClientSnapshot(baseline);
        menu.applyClientProgress("mmcr:other_recipe", 6, 20);
        menu.applyClientProgress("mmcr:recipe", 6, 21);
        assertThat(menu.activeRecipeTick()).isEqualTo(1);
        menu.applyClientProgress("mmcr:recipe", 6, 20);
        assertThat(menu.activeRecipeTick()).isEqualTo(6);
        assertThat(menu.activeRecipeTotalTick()).isEqualTo(20);
        assertThat(menu.recipePresentation()).isSameAs(presentation);
        assertThat(menu.currentParallelism()).isEqualTo(Long.MAX_VALUE);
        assertThat(menu.currentRecipePoolId()).isEqualTo(MMCR.id("pool"));
        assertThat(menu.connectedHostId()).hasValue(MMCR.id("host"));
        assertThat(menu.foundLevelIds()).containsExactly("mmcr:steel");
        assertThat(menu.isRedstonePaused()).isTrue();
        assertThat(menu.matchedStage()).isEqualTo(4);
    }

    @Test
    void payload_defers_be_and_matching_menu_progress_until_enqueued_work_runs() throws Exception {
        MachineControllerBlockEntity controller = TestBootstrap.newController();
        Level level = LevelStub.createWithBlockEntities(List.of(controller));
        Field side = Level.class.getDeclaredField("isClientSide");
        side.setAccessible(true);
        side.set(level, true);
        controller.setLevel(level);
        MachineControllerMenu menu = new MachineControllerMenu(1, new Inventory(null), controller.getBlockPos());
        PktMachineStatePayload full = new PktMachineStatePayload(controller.getBlockPos(), "mmcr:recipe", false, false,
                List.of(), "", 0, 0, false, "", CraftingStatus.Status.CRAFTING, "", null, true, false,
                1, 20, 1L, 1L, false, 0, 0, 0, 0L, Map.of(), 0, 1);
        menu.applyClientSnapshot(full);
        controller.applyClientState("mmcr:recipe", false, false, List.of(), null, 0, 0, false, null,
                CraftingStatus.working(), null, true, 1, 20, 1L, 1L, Map.of());
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Player player = (Player) ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(TestPlayer.class);
        Field playerLevel = net.minecraft.world.entity.Entity.class.getDeclaredField("level");
        playerLevel.setAccessible(true);
        playerLevel.set(player, level);
        player.containerMenu = menu;
        AtomicReference<Runnable> work = new AtomicReference<>();
        IPayloadContext context = (IPayloadContext) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{IPayloadContext.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "enqueueWork" -> {
                        work.set((Runnable) args[0]);
                        yield CompletableFuture.completedFuture(null);
                    }
                    case "player" -> player;
                    default -> throw new AssertionError(method.getName());
                });
        new PktMachineProgressPayload(controller.getBlockPos(), "mmcr:recipe", 7, 20).handle(context);
        assertThat(controller.runtimeSnapshot().crafting().tick()).isEqualTo(1);
        assertThat(menu.activeRecipeTick()).isEqualTo(1);
        work.get().run();
        assertThat(controller.runtimeSnapshot().crafting().tick()).isEqualTo(7);
        assertThat(menu.activeRecipeTick()).isEqualTo(7);
        player.containerMenu = new MachineControllerMenu(2, new Inventory(null), controller.getBlockPos().above());
        new PktMachineProgressPayload(controller.getBlockPos(), "mmcr:recipe", 8, 20).handle(context);
        work.get().run();
        assertThat(controller.runtimeSnapshot().crafting().tick()).isEqualTo(8);
        assertThat(((MachineControllerMenu) player.containerMenu).activeRecipeTick()).isZero();
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class TestPlayer extends Player {
        private TestPlayer(Level level) { super(level, BlockPos.ZERO, 0F, new GameProfile(UUID.randomUUID(), "test")); }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return false; }
    }

    private static RegistryFriendlyByteBuf menuBuffer(BlockPos pos, ResourceLocation machineId,
                                                      ResourceLocation connectedHostId, int role,
                                                      boolean formed, int installedModules) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
        MachineControllerMenu.writeClientOpenData(buffer, pos, machineId, connectedHostId, role, formed,
                installedModules);
        return buffer;
    }

    private static void bind(Object deferredHolder, MenuType<MachineControllerMenu> menuType) throws Exception {
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
}
