package cn.howxu.mmcr.publicapi.presentation;

import org.jetbrains.annotations.ApiStatus;
import java.util.Optional;

/** MMCR-provided capability display value. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface IoDisplay {
    String label();
    String value();
    String unit();
    Optional<DisplayStackView> icon();
}
