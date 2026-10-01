package cn.howxu.mmcr.publicapi.runtime;

import org.jetbrains.annotations.ApiStatus;

/** MMCR-provided accepted/requested output amounts. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface OutputAcceptance {
    long requested();
    long accepted();
    OutputFit fit();
}
