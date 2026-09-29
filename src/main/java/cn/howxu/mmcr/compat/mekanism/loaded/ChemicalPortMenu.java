package cn.howxu.mmcr.compat.mekanism.loaded;

import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplay;
import cn.howxu.mmcr.api.publicapi.machine.MachineIoView;
import cn.howxu.mmcr.internal.menu.AbstractMachineMenu;
import cn.howxu.mmcr.internal.menu.LongDataSlot;
import cn.howxu.mmcr.internal.menu.MenuSupport;
import cn.howxu.mmcr.registry.ModUIs;
import mekanism.api.chemical.ChemicalResource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.ResourceHandler;

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
                : new LongDataSlot(() -> owner.chemicalHandler(null).getAmountAsLong(0)));
        this.capacity = addLongDataSlot(owner == null ? LongDataSlot.standalone()
                : new LongDataSlot(() -> owner.chemicalHandler(null)
                        .getCapacityAsLong(0, ChemicalResource.EMPTY)));
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

    public ResourceHandler<ChemicalResource> storage() {
        ChemicalPortBlockEntity port = resolvedOwner();
        return port == null ? null : port.chemicalHandler(null);
    }

    public long chemicalAmount() {
        ResourceHandler<ChemicalResource> storage = storage();
        long value = storage == null ? amount.value() : storage.getAmountAsLong(0);
        return bounded(value, chemicalCapacity());
    }

    public long chemicalCapacity() {
        ChemicalPortBlockEntity port = resolvedOwner();
        long value = port == null ? capacity.value() : port.chemicalHandler(null)
                .getCapacityAsLong(0, ChemicalResource.EMPTY);
        return Math.max(0L, value);
    }

    public ResourceLocation chemicalResourceLocation() {
        ChemicalResource resource = chemicalResource();
        return resource.isEmpty() ? null : resource.getChemical().getIcon();
    }

    public int chemicalTint() {
        ChemicalResource resource = chemicalResource();
        return resource.isEmpty() ? 0xFFFFFFFF : resource.getChemicalTint();
    }

    public Component chemicalName() {
        ChemicalResource resource = chemicalResource();
        return resource.isEmpty() ? Component.empty() : resource.getTextComponent();
    }

    public List<CapabilityDisplay> displayEntries() {
        if (owner != null) return new MachineIoView(owner.capabilitySnapshot()).displays();
        return List.of(new CapabilityDisplay("chemical", Long.toString(chemicalAmount()), "mB", Optional.empty()));
    }

    private ChemicalPortBlockEntity resolvedOwner() {
        if (owner != null) return owner;
        return level.getBlockEntity(pos) instanceof ChemicalPortBlockEntity port ? port : null;
    }

    private ChemicalResource chemicalResource() {
        ResourceHandler<ChemicalResource> storage = storage();
        return storage == null ? ChemicalResource.EMPTY : storage.getResource(0);
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
