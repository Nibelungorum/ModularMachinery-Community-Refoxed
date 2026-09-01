package cn.howxu.mmcr.api.capability.presentation;

import cn.howxu.mmcr.api.publicapi.machine.DisplayStack;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable display data for one capability value.
 *
 * @author howxu <dev@howxu.cn>
 */
public record CapabilityDisplay(String label, String value, String unit, Optional<DisplayStack> icon) {
    public CapabilityDisplay {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(unit, "unit");
        icon = icon == null ? Optional.empty() : icon;
    }
}
