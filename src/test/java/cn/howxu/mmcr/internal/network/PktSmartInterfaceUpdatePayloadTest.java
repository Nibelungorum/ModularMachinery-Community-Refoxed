package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.api.machine.SmartInterfaceType;
import cn.howxu.mmcr.internal.menu.SmartInterfaceMenu;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.network.IContainerFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

import net.minecraft.world.entity.player.Inventory;
import static org.assertj.core.api.Assertions.assertThat;

class PktSmartInterfaceUpdatePayloadTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bind(ModUIs.SMART_INTERFACE, new MenuType<>((IContainerFactory<SmartInterfaceMenu>) SmartInterfaceMenu::clientOpen,
                FeatureFlags.VANILLA_SET));
    }

    @Test
    void update_is_ignored_when_player_does_not_have_the_target_menu_open() throws Exception {
        ServerPlayer player = playerWith(SmartInterfaceMenu.clientOpen(1, new Inventory(null, null), BlockPos.ZERO));

        assertThat(PktSmartInterfaceUpdatePayload.canUpdate(player, new BlockPos(1, 2, 3), "temperature", 8F)).isFalse();
    }

    @Test
    void can_update_requires_open_menu_matching_pos_type_and_finite_value() throws Exception {
        BlockPos pos = new BlockPos(1, 2, 3);
        SmartInterfaceMenu menu = SmartInterfaceMenu.clientOpen(1, new Inventory(null, null), pos);
        ServerPlayer player = playerWith(menu);

        assertThat(PktSmartInterfaceUpdatePayload.canUpdate(player, pos, "temperature", 12F)).isTrue();
        assertThat(PktSmartInterfaceUpdatePayload.canUpdate(player, pos, "", 12F)).isFalse();
        assertThat(PktSmartInterfaceUpdatePayload.canUpdate(player, pos, "temperature", Float.NaN)).isFalse();
        assertThat(PktSmartInterfaceUpdatePayload.canUpdate(player, pos.above(), "temperature", 12F)).isFalse();
    }

    @Test
    void value_type_validation_rejects_fractional_integer_updates() {
        SmartInterfaceType integer = new SmartInterfaceType("batch", 1F, 0, SmartInterfaceType.ValueType.INTEGER);

        assertThat(PktSmartInterfaceUpdatePayload.typeAccepts(integer, 2F)).isTrue();
        assertThat(PktSmartInterfaceUpdatePayload.typeAccepts(integer, 2.5F)).isFalse();
    }

    @Test
    void value_validation_returns_minimum_for_invalid_updates() {
        SmartInterfaceType ranged = new SmartInterfaceType("temperature", 400F, 6800F, 0,
                SmartInterfaceType.ValueType.INTEGER);

        assertThat(PktSmartInterfaceUpdatePayload.validatedValue(ranged, 1200F)).contains(1200F);
        assertThat(PktSmartInterfaceUpdatePayload.validatedValue(ranged, 399F)).contains(400F);
        assertThat(PktSmartInterfaceUpdatePayload.validatedValue(ranged, 6801F)).contains(400F);
        assertThat(PktSmartInterfaceUpdatePayload.validatedValue(ranged, 1200.5F)).contains(400F);
        assertThat(PktSmartInterfaceUpdatePayload.validatedValue(null, 1200F)).isEmpty();
    }

    @Test
    void stale_current_menu_is_rejected() throws Exception {
        BlockPos requestedPos = new BlockPos(1, 2, 3);
        ServerPlayer player = playerWith(SmartInterfaceMenu.clientOpen(1, new Inventory(null, null), requestedPos.above()));

        assertThat(PktSmartInterfaceUpdatePayload.canUpdate(player, requestedPos, "temperature", 12F)).isFalse();
    }

    private static ServerPlayer playerWith(AbstractContainerMenu menu) throws Exception {
        ServerPlayer player = (ServerPlayer) unsafe().allocateInstance(ServerPlayer.class);
        player.containerMenu = menu;
        return player;
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void bind(Object deferredHolder, MenuType<SmartInterfaceMenu> menuType) throws Exception {
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
