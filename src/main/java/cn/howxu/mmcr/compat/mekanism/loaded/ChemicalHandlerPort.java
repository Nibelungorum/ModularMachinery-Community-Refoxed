package cn.howxu.mmcr.compat.mekanism.loaded;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.internal.capability.NativeReservationAccess;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;

/**
 * Multi-tank chemical capability with physical reservation identities.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface ChemicalHandlerPort extends MachineCapability {
    IChemicalHandler chemicalHandler();

    default boolean radioactive() { return false; }

    /** Whether this port supports the requested chemical's radiation category. */
    default boolean supportsRadioactivity(boolean radioactive) { return radioactive() == radioactive; }

    default Object planningIdentity() {
        return chemicalHandler() instanceof NativeReservationAccess access ? access.reservationIdentity() : this;
    }

    default Object planningSlot(int slot) {
        return chemicalHandler() instanceof NativeReservationAccess access ? access.reservationSlot(slot) : slot;
    }

    default Object planningKey(ChemicalStack stack) {
        return chemicalHandler() instanceof NativeReservationAccess access ? access.resourceKey(stack)
                : stack.isEmpty() ? null : stack.getChemical();
    }

    default Object storedKey(int slot) {
        return chemicalHandler() instanceof NativeReservationAccess access ? access.storedKey(slot)
                : planningKey(chemicalHandler().getChemicalInTank(slot));
    }

    default long storedAmount(int slot) {
        return chemicalHandler() instanceof NativeReservationAccess access ? access.storedAmount(slot)
                : chemicalHandler().getChemicalInTank(slot).getAmount();
    }
}
