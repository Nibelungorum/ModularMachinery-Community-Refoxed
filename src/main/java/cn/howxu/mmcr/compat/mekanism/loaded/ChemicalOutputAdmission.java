package cn.howxu.mmcr.compat.mekanism.loaded;

import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import mekanism.api.chemical.ChemicalStack;

import java.util.List;

/**
 * Resource-dependent output admission for storage spanning a network and local slots.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface ChemicalOutputAdmission {
    long outputAvailable(ChemicalStack identity, PlanningReservations reservations);

    List<CapabilityRequests.ResourceAction<ChemicalStack>> reserveOutput(
            ChemicalStack identity, long amount, PlanningReservations reservations);
}
