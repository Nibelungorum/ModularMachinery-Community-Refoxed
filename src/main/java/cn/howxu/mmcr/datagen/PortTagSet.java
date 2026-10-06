package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.botania.BotaniaManaIds;
import cn.howxu.mmcr.internal.port.IOPortKind;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Derives stable tag paths for one IO port kind.
 *
 * @author howxu <dev@howxu.cn>
 */
record PortTagSet(List<ResourceLocation> tags, boolean optionalEntries) {
    static PortTagSet forKind(IOPortKind kind) {
        LinkedHashSet<ResourceLocation> tags = new LinkedHashSet<>();
        tags.add(MMCR.id("ports"));
        tags.add(MMCR.id("machines"));
        kind.families().forEach(family -> {
            String prefix = family.familyId().getNamespace().equals(MMCR.MODID)
                    || family.familyId().getNamespace().equals("minecraft")
                    ? family.familyId().getPath()
                    : family.familyId().getNamespace() + "_" + family.familyId().getPath();
            tags.add(MMCR.id(prefix + "_ports"));
            tags.add(MMCR.id(prefix + "_" + family.ioType().getSerializedName() + "_ports"));
            if (family.familyId().equals(BotaniaManaIds.MANA)) {
                tags.add(MMCR.id("mana_ports"));
                tags.add(MMCR.id("mana_" + family.ioType().getSerializedName() + "_ports"));
                tags.add(ResourceLocation.fromNamespaceAndPath("botania", "mana_pools"));
                tags.add(ResourceLocation.fromNamespaceAndPath("botania", "all_mana_pools"));
            }
        });
        kind.modDependencies().forEach(dependency -> tags.add(MMCR.id(dependency + "_ports")));
        return new PortTagSet(List.copyOf(tags), !kind.modDependencies().isEmpty());
    }
}
