package cn.howxu.mmcr.internal.port;

import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Describes one capability family exposed by an IO port kind.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PortFamilyDescriptor(
        Identifier familyId,
        IOType ioType,
        int detectionTier,
        List<String> countAliases) {

    public PortFamilyDescriptor {
        if (familyId == null) throw new IllegalArgumentException("familyId null");
        if (ioType == null) throw new IllegalArgumentException("ioType null");
        if (detectionTier < 0) throw new IllegalArgumentException("detectionTier must be >= 0");
        if (countAliases == null) throw new IllegalArgumentException("countAliases null");

        List<String> aliases = new ArrayList<>(countAliases.size());
        HashSet<String> seen = new HashSet<>();
        for (String alias : countAliases) {
            if (alias == null || alias.isBlank()) throw new IllegalArgumentException("count alias blank");
            if (!seen.add(alias)) throw new IllegalArgumentException("duplicate count alias: " + alias);
            aliases.add(alias);
        }
        countAliases = List.copyOf(aliases);
    }

    public boolean matches(CapabilityBinding binding) {
        return binding != null && familyId.equals(binding.type().id()) && ioType == binding.ioType();
    }
}
