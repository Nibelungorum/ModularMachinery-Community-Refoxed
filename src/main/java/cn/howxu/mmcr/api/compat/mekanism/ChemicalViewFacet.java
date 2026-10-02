package cn.howxu.mmcr.api.compat.mekanism;

import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.List;
import java.util.Objects;

/**
 * Mekanism-neutral read access to a chemical capability.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface ChemicalViewFacet extends CapabilityFacet {
    Optional<ResourceLocation> chemicalId();

    long amount();

    boolean matchesTag(ResourceLocation tagId);

    long outputCapacity(ResourceLocation chemicalId);

    default Object queryIdentity() { return this; }

    default List<ChemicalAmount> contents() {
        return chemicalId().filter(ignored -> amount() > 0L)
                .map(id -> List.of(new ChemicalAmount(id, amount()))).orElseGet(List::of);
    }

    default List<ChemicalAmount> tagContents(ResourceLocation tagId) {
        return matchesTag(tagId) ? contents() : List.of();
    }

    default long tagAmount(ResourceLocation tagId) {
        long amount = 0L;
        for (ChemicalAmount entry : tagContents(tagId)) {
            amount = entry.amount() > Long.MAX_VALUE - amount ? Long.MAX_VALUE : amount + entry.amount();
        }
        return amount;
    }

    /** @author howxu <dev@howxu.cn> */
    record ChemicalAmount(ResourceLocation id, long amount) {
        public ChemicalAmount {
            Objects.requireNonNull(id, "id");
            if (amount < 0L) throw new IllegalArgumentException("amount must be non-negative");
        }
    }
}
