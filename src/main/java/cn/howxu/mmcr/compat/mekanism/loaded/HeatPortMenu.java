package cn.howxu.mmcr.compat.mekanism.loaded;

import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplay;
import cn.howxu.mmcr.api.machine.definition.MachineIoView;
import cn.howxu.mmcr.internal.menu.AbstractMachineMenu;
import cn.howxu.mmcr.internal.menu.LongDataSlot;
import cn.howxu.mmcr.internal.menu.MenuSupport;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/** Menu for a loaded Mekanism heat port.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class HeatPortMenu extends AbstractMachineMenu {
    private final HeatPortBlockEntity owner;
    private final Level level;
    private final BlockPos pos;
    private final LongDataSlot heat;
    private final LongDataSlot capacity;

    public HeatPortMenu(int containerId, Inventory playerInv, HeatPortBlockEntity owner) {
        super(ModUIs.HEAT_PORT.get(), containerId);
        this.owner = owner;
        this.level = playerInv.player.level();
        this.pos = owner == null ? BlockPos.ZERO : owner.getBlockPos();
        this.heat = addLongDataSlot(owner == null ? LongDataSlot.standalone()
                : new LongDataSlot(() -> Math.round(owner.heatCapacitor().getHeat())));
        this.capacity = addLongDataSlot(owner == null ? LongDataSlot.standalone()
                : new LongDataSlot(() -> Math.round(owner.heatCapacitor().getHeatCapacity())));
        addPlayerSlots(playerInv);
    }

    public HeatPortMenu(int containerId, Inventory playerInv, BlockPos pos) {
        super(ModUIs.HEAT_PORT.get(), containerId);
        this.owner = null;
        this.level = playerInv.player.level();
        this.pos = pos;
        this.heat = addLongDataSlot(LongDataSlot.standalone());
        this.capacity = addLongDataSlot(LongDataSlot.standalone());
        addPlayerSlots(playerInv);
    }

    public static HeatPortMenu clientOpen(int containerId, Inventory playerInv, FriendlyByteBuf buffer) {
        return new HeatPortMenu(containerId, playerInv, buffer.readBlockPos());
    }

    public HeatPortBlockEntity owner() {
        return owner;
    }

    public BlockPos pos() {
        return pos;
    }

    public long heatAmount() {
        return owner == null ? Math.max(0L, heat.value()) : boundedRound(owner.heatCapacitor().getHeat());
    }

    public long heatCapacity() {
        return owner == null ? Math.max(0L, capacity.value())
                : boundedRound(owner.heatCapacitor().getHeatCapacity());
    }

    public double temperature() {
        long capacity = heatCapacity();
        return capacity <= 0L ? 0D : MekanismTemperatureDisplay.fromKelvin(heatAmount() / (double) capacity);
    }

    public List<CapabilityDisplay> displayEntries() {
        if (owner != null) return new MachineIoView(owner.capabilitySnapshot()).displays();
        return List.of(new CapabilityDisplay("heat", Long.toString(heatAmount()), "J", Optional.empty()));
    }

    private HeatPortBlockEntity resolvedOwner() {
        if (owner != null) return owner;
        return level.getBlockEntity(pos) instanceof HeatPortBlockEntity port ? port : null;
    }

    private static long boundedRound(double value) {
        if (!Double.isFinite(value) || value <= 0D) return 0L;
        return value >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.round(value);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return MenuSupport.noopQuickMove();
    }

    @Override
    public boolean stillValid(Player player) {
        return owner == null || owner.getLevel() != null
                && owner.getLevel().getBlockEntity(pos) == owner
                && MenuSupport.stillValidWithin(player, owner.getBlockPos());
    }
}
