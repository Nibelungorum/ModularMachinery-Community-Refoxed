package cn.howxu.mmcr.compat.mekanism.loaded;

import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplay;
import cn.howxu.mmcr.api.machine.definition.MachineIoView;
import cn.howxu.mmcr.internal.menu.AbstractMachineMenu;
import cn.howxu.mmcr.internal.menu.LongDataSlot;
import cn.howxu.mmcr.internal.menu.MenuSupport;
import cn.howxu.mmcr.registry.ModUIs;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalTank;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/** Menu for a loaded Mekanism chemical port.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ChemicalPortMenu extends AbstractMachineMenu {
    private final ChemicalPortBlockEntity owner;
    private final Level level;
    private final BlockPos pos;
    private final LongDataSlot amount;
    private final LongDataSlot capacity;

    public ChemicalPortMenu(int containerId, Inventory playerInv, ChemicalPortBlockEntity owner) {
        super(ModUIs.CHEMICAL_PORT.get(), containerId);
        this.owner = owner;
        this.level = playerInv.player.level();
        this.pos = owner == null ? BlockPos.ZERO : owner.getBlockPos();
        this.amount = addLongDataSlot(owner == null ? LongDataSlot.standalone()
                : new LongDataSlot(() -> owner.chemicalTank().getStored()));
        this.capacity = addLongDataSlot(owner == null ? LongDataSlot.standalone()
                : new LongDataSlot(() -> owner.chemicalTank().getCapacity()));
        addPlayerSlots(playerInv);
    }

    public ChemicalPortMenu(int containerId, Inventory playerInv, BlockPos pos) {
        super(ModUIs.CHEMICAL_PORT.get(), containerId);
        this.owner = null;
        this.level = playerInv.player.level();
        this.pos = pos;
        this.amount = addLongDataSlot(LongDataSlot.standalone());
        this.capacity = addLongDataSlot(LongDataSlot.standalone());
        addPlayerSlots(playerInv);
    }

    public static ChemicalPortMenu clientOpen(int containerId, Inventory playerInv, FriendlyByteBuf buffer) {
        return new ChemicalPortMenu(containerId, playerInv, buffer.readBlockPos());
    }

    public ChemicalPortBlockEntity owner() {
        return owner;
    }

    public BlockPos pos() {
        return pos;
    }

    public IChemicalTank storage() {
        ChemicalPortBlockEntity port = resolvedOwner();
        return port == null ? null : port.chemicalTank();
    }

    public long chemicalAmount() {
        IChemicalTank storage = storage();
        long value = storage == null ? amount.value() : storage.getStored();
        return bounded(value, chemicalCapacity());
    }

    public long chemicalCapacity() {
        ChemicalPortBlockEntity port = resolvedOwner();
        long value = port == null ? capacity.value() : port.chemicalTank().getCapacity();
        return Math.max(0L, value);
    }

    public ResourceLocation chemicalResourceLocation() {
        ChemicalStack stack = chemicalStack();
        return stack.isEmpty() ? null : stack.getChemical().getIcon();
    }

    public int chemicalTint() {
        ChemicalStack stack = chemicalStack();
        return stack.isEmpty() ? 0xFFFFFFFF : stack.getChemicalTint();
    }

    public Component chemicalName() {
        ChemicalStack stack = chemicalStack();
        return stack.isEmpty() ? Component.empty() : stack.getTextComponent();
    }

    public List<CapabilityDisplay> displayEntries() {
        if (owner != null) return new MachineIoView(owner.capabilitySnapshot()).displays();
        return List.of(new CapabilityDisplay("chemical", Long.toString(chemicalAmount()), "mB", Optional.empty()));
    }

    private ChemicalPortBlockEntity resolvedOwner() {
        if (owner != null) return owner;
        return level.getBlockEntity(pos) instanceof ChemicalPortBlockEntity port ? port : null;
    }

    private ChemicalStack chemicalStack() {
        IChemicalTank storage = storage();
        return storage == null ? ChemicalStack.EMPTY : storage.getStack();
    }

    private static long bounded(long value, long capacity) {
        return Math.max(0L, Math.min(value, capacity));
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
