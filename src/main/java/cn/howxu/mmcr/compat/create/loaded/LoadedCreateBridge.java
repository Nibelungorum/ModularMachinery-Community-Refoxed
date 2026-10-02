package cn.howxu.mmcr.compat.create.loaded;

import cn.howxu.mmcr.compat.create.CreateBridge;
import cn.howxu.mmcr.internal.port.IOPortKind;
import com.simibubi.create.infrastructure.config.AllConfigs;

import java.util.List;

/** @author howxu <dev@howxu.cn> */
public final class LoadedCreateBridge implements CreateBridge {
    @Override public boolean available() { return true; }

    @Override public double maxRpm() { return AllConfigs.server().kinetics.maxRotationSpeed.get(); }

    public List<IOPortKind> portKinds() {
        return List.of(StressInterfaceKind.INPUT, StressInterfaceKind.OUTPUT);
    }
}
