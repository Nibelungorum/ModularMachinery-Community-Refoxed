package cn.howxu.mmcr.internal.block;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineRole;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import cn.howxu.mmcr.api.machine.definition.TickBehavior;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.menu.ControllerMenuOpenData;
import cn.howxu.mmcr.internal.menu.ControllerUiMenu;
import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot.Kind;
import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot.Role;
import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.Level;
import io.netty.buffer.Unpooled;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MachineControllerBlockTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bind(ModUIs.MACHINE_CONTROLLER,
                new MenuType<>((containerId, inventory) -> MachineControllerMenu.clientOpen(containerId, inventory),
                        FeatureFlags.VANILLA_SET));
        bind(ModUIs.FACTORY_CONTROLLER,
                new MenuType<>((containerId, inventory) -> FactoryControllerMenu.clientOpen(containerId, inventory),
                        FeatureFlags.VANILLA_SET));
    }

    @Test
    void vertical_allowed_controller_uses_clicked_face_for_placement() {
        assertThat(MachineControllerBlock.facingForPlacement(Direction.UP, 1.5d, 1.0d, Direction.NORTH, true)).isEqualTo(Direction.NORTH);
        assertThat(MachineControllerBlock.facingForPlacement(Direction.UP, 1.5d, 3.0d, Direction.NORTH, true)).isEqualTo(Direction.UP);
        assertThat(MachineControllerBlock.facingForPlacement(Direction.DOWN, 2.5d, 1.0d, Direction.NORTH, true)).isEqualTo(Direction.DOWN);
        assertThat(MachineControllerBlock.facingForPlacement(Direction.UP, 3.5d, 3.0d, Direction.NORTH, true)).isEqualTo(Direction.NORTH);
        assertThat(MachineControllerBlock.facingForPlacement(Direction.EAST, 3.5d, 2.0d, Direction.NORTH, true)).isEqualTo(Direction.DOWN);
    }

    @Test
    void vertical_placement_roll_facing_anchors_toward_player() {
        assertThat(MachineControllerBlock.rollFacingForPlacement(Direction.UP, 10.5d, 10.5d, 8.0d, 10.0d, Direction.NORTH)).isEqualTo(Direction.WEST);
        assertThat(MachineControllerBlock.rollFacingForPlacement(Direction.DOWN, 10.5d, 10.5d, 13.0d, 10.0d, Direction.NORTH)).isEqualTo(Direction.EAST);
        assertThat(MachineControllerBlock.rollFacingForPlacement(Direction.UP, 10.5d, 10.5d, 10.0d, 8.0d, Direction.NORTH)).isEqualTo(Direction.NORTH);
        assertThat(MachineControllerBlock.rollFacingForPlacement(Direction.DOWN, 10.5d, 10.5d, 10.0d, 13.0d, Direction.NORTH)).isEqualTo(Direction.SOUTH);
        assertThat(MachineControllerBlock.rollFacingForPlacement(Direction.UP, 10.5d, 10.5d, 9.5d, 9.5d, Direction.EAST)).isEqualTo(Direction.EAST);
        assertThat(MachineControllerBlock.rollFacingForPlacement(Direction.NORTH, 10.5d, 10.5d, 8.0d, 10.0d, Direction.SOUTH)).isEqualTo(Direction.SOUTH);
    }

    @Test
    void horizontal_only_controller_falls_back_when_clicked_face_is_vertical() {
        assertThat(MachineControllerBlock.facingForPlacement(Direction.UP, Direction.NORTH, false)).isEqualTo(Direction.NORTH);
        assertThat(MachineControllerBlock.facingForPlacement(Direction.DOWN, Direction.SOUTH, false)).isEqualTo(Direction.SOUTH);
        assertThat(MachineControllerBlock.facingForPlacement(Direction.EAST, Direction.NORTH, false)).isEqualTo(Direction.EAST);
    }

    @Test
    void required_vertical_controller_never_uses_horizontal_facing() {
        assertThat(MachineControllerBlock.facingForPlacement(Direction.EAST, 2.0d, 2.0d,
                Direction.NORTH, true, true).getAxis().isVertical()).isTrue();
        assertThat(MachineControllerBlock.facingForPlacement(Direction.WEST, 4.0d, 2.0d,
                Direction.NORTH, true, true).getAxis().isVertical()).isTrue();
    }

    @Test
    void tick_machine_uses_the_ordinary_controller_menu_with_a_factory_component() {
        var machineId = MMCR.id("tick_machine_controller_menu");
        DynamicMachine machine = new DynamicMachine(machineId, "Tick Machine Controller Menu",
                new BlockArray(Map.of()), MachineControllerSpec.defaultsFor(machineId), MachineAppearanceSpec.defaults(),
                PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), Map.of(), 1, false, true, 1,
                List.of(), MachineRole.NORMAL, Set.of(), List.of(), RecipeFailureActions.getDefaultAction(),
                TickBehavior.builder().build());
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(new BlockPos(1, 0, 0),
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        controller.setMachine(machine);
        controller.componentRuntime().replaceComponents(List.of(new ProcessingComponent(
                null, scheduler, scheduler.getBlockPos(), scheduler.getBlockPos(), (String) null)));

        AbstractContainerMenu menu = MachineControllerBlock.createMenu(1, new Inventory(null, null), null, controller);

        assertThat(menu).isInstanceOf(MachineControllerMenu.class);
        assertThat(menu).isNotInstanceOf(FactoryControllerMenu.class);
        assertThat(((ControllerUiMenu) menu).uiOpenData().kind()).isEqualTo(Kind.TICK);
        assertThat(((ControllerUiMenu) menu).uiOpenData().machineId()).isEqualTo(machineId);
    }

    @Test
    void unformed_controller_metadata_uses_physical_identity_without_a_runtime_machine() {
        var id = MMCR.id("test_cube");
        var controller = RuntimeTestFixtures.controllerEntity(id, new BlockPos(2, 4, 6));
        var menu = (ControllerUiMenu) MachineControllerBlock.createMenu(4, new Inventory(null, null), null, controller);
        assertThat(menu.uiOpenData().machineId()).isEqualTo(id);
        assertThat(menu.uiOpenData().pos()).isEqualTo(controller.getBlockPos());
        assertThat(menu.uiOpenData().formed()).isFalse();
        assertThat(menu.uiServerSession()).isNull();
    }

    @Test
    void both_client_readers_preserve_the_same_complete_open_data() {
        var data = new ControllerMenuOpenData(UUID.randomUUID(), Level.NETHER, new BlockPos(2, 4, 6),
                MMCR.id("test_cube"), Kind.FACTORY, Role.MODULE, true, 3, Optional.of(MMCR.id("host")),
                List.of(new ControllerMenuOpenData.Capability(MMCR.id("state"), 2, true)));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            ControllerMenuOpenData.write(buffer, data);
            var ordinary = MachineControllerMenu.clientOpen(4, new Inventory(null, null), buffer);
            assertThat(ordinary.uiOpenData()).isEqualTo(data);
            assertThat(ordinary.uiServerSession()).isNull();
            assertThat(buffer.isReadable()).isFalse();
            buffer.readerIndex(0);
            var factory = FactoryControllerMenu.clientOpen(4, new Inventory(null, null), buffer);
            assertThat(factory.uiOpenData()).isEqualTo(data);
            assertThat(factory.machineId()).isEqualTo(data.machineId());
            assertThat(factory.isModuleController()).isTrue();
            assertThat(factory.connectedHostId()).isEqualTo(data.connectedHostId());
            assertThat(factory.uiServerSession()).isNull();
            assertThat(buffer.isReadable()).isFalse();
            factory.setPlayerInventoryVisible(false);
            assertThat(factory.playerInventoryVisible()).isFalse();
            assertThat(ordinary.playerInventoryVisible()).isTrue();
        } finally {
            buffer.release();
        }
    }

    @Test
    void factory_menu_metadata_preserves_the_configured_controller_role() {
        for (MachineRole role : MachineRole.values()) {
            var machineId = MMCR.id("factory_metadata_" + role.name().toLowerCase(Locale.ROOT));
            var machine = new DynamicMachine(machineId, "machine.mmcr.test_cube", new BlockArray(Map.of()),
                    MachineControllerSpec.defaultsFor(machineId), MachineAppearanceSpec.defaults(),
                    PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), Map.of(), 1, false, true, 1,
                    List.of(), role, Set.of(), List.of(), RecipeFailureActions.getDefaultAction());
            var controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
            var scheduler = new FactorySchedulerBlockEntity(new BlockPos(1, 0, 0),
                    ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
            controller.setMachine(machine);
            controller.componentRuntime().replaceComponents(List.of(new ProcessingComponent(
                    null, scheduler, scheduler.getBlockPos(), scheduler.getBlockPos(), (String) null)));
            RuntimeTestFixtures.republish(controller);
            var menu = MachineControllerBlock.createMenu(1, new Inventory(null, null), null, controller);
            assertThat(menu).isInstanceOf(FactoryControllerMenu.class);
            var data = ((ControllerUiMenu) menu).uiOpenData();
            assertThat(data.kind()).isEqualTo(Kind.FACTORY);
            assertThat(data.machineId()).isEqualTo(machineId);
            assertThat(data.role()).isEqualTo(Role.valueOf(role.name()));
        }
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
}
