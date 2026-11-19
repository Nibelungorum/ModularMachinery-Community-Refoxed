package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.internal.autoio.AutoIOAction;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.menu.AbstractMachineMenu;
import cn.howxu.mmcr.internal.menu.PortMenu;
import cn.howxu.mmcr.internal.tile.CombinedPortBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions.ENERGY_TYPE;
import static cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions.FLUID_TYPE;
import static cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions.ITEM_TYPE;

/** Server authorization and per-ID configuration through an addon menu.
 * @author howxu <dev@howxu.cn>
 */
public final class AutoIOConfigGameTest {
    public static void thirdEndpointAndStaleOwner(GameTestHelper helper) {
        var relative = new BlockPos(1, 1, 1);
        var state = ModBlocks.BLOCKS.get("combined_input_basic").get().defaultBlockState();
        helper.setBlock(relative, state);
        var pos = helper.absolutePos(relative);
        var owner = new CombinedPortBlockEntity(pos, state) {
            private CapabilitySnapshot snapshot;
            @Override public CapabilitySnapshot capabilitySnapshot() {
                if (snapshot == null) {
                    var endpoints = new ArrayList<>(super.capabilitySnapshot().capabilities());
                    var energy = new LongValueStorage(1000, 1000, () -> {});
                    endpoints.add(new EnergyHatchCapability(this, energy, IOType.OUTPUT));
                    endpoints.add(new EnergyHatchCapability(this, energy, IOType.INPUT));
                    snapshot = new CapabilitySnapshot(endpoints);
                }
                return snapshot;
            }
        };
        helper.getLevel().setBlockEntity(owner);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "mmcr-port-config"), ClientInformation.createDefault());
        player.containerMenu = new AddonMenu(owner);
        player.setPos(pos.getCenter());
        var ids = owner.configurableAutoIOIds();
        helper.assertTrue(ids.equals(List.of(ITEM_TYPE.id(), FLUID_TYPE.id(), ENERGY_TYPE.id())),
                "Only the input energy occurrence joins the two ordinary input endpoints");
        var configure = new PktAutoIOConfigPayload(pos, ids.get(2), AutoIOAction.SET_SIDE, Direction.EAST, false);
        helper.assertTrue(configure.apply(player) && !owner.autoIOConfig(ENERGY_TYPE).isSideEnabled(Direction.EAST)
                        && owner.autoIOConfig(ITEM_TYPE).isSideEnabled(Direction.EAST)
                        && owner.autoIOConfig(FLUID_TYPE).isSideEnabled(Direction.EAST),
                "The third selected ID changes only its own configuration");
        var restore = new PktAutoIOConfigPayload(pos, ids.get(2), AutoIOAction.SET_SIDE, Direction.EAST, true);
        player.setPos(pos.offset(32, 0, 0).getCenter());
        helper.assertFalse(restore.apply(player), "An addon menu cannot extend interaction range");
        player.setPos(pos.getCenter());
        var replacement = new CombinedPortBlockEntity(pos, state);
        helper.getLevel().setBlockEntity(replacement);
        var itemRequest = new PktAutoIOConfigPayload(pos, ITEM_TYPE.id(), AutoIOAction.SET_SIDE, Direction.EAST, false);
        helper.assertTrue(!itemRequest.apply(player) && replacement.autoIOConfig(ITEM_TYPE).isSideEnabled(Direction.EAST),
                "A valid ID and position do not authorize an old menu on a replacement host");
        helper.succeed();
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class AddonMenu extends AbstractMachineMenu implements PortMenu {
        private final IOPortBlockEntity owner;
        private AddonMenu(IOPortBlockEntity owner) { super(null, 4); this.owner = owner; }
        @Override public BlockPos pos() { return owner.getBlockPos(); }
        @Override public IOPortBlockEntity owner() { return owner; }
        @Override public boolean stillValid(Player player) { return true; }
        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    }
}
