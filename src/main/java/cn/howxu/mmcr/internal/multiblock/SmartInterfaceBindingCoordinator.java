package cn.howxu.mmcr.internal.multiblock;

import cn.howxu.mmcr.api.machine.SmartInterfaceType;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.SmartInterfaceBlockEntity;
import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Reconciles smart-interface bindings against one formed controller structure.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class SmartInterfaceBindingCoordinator {
    private final Map<String, SmartInterfaceType> types;

    public SmartInterfaceBindingCoordinator(Map<String, SmartInterfaceType> types) {
        this.types = Collections.unmodifiableMap(new LinkedHashMap<>(types));
    }

    public void reconcile(MachineControllerBlockEntity controller, Collection<SmartInterfaceBlockEntity> interfaces) {
        BlockPos controllerPos = controller.getBlockPos();
        var ordered = interfaces.stream().sorted(Comparator.comparing(SmartInterfaceBlockEntity::getBlockPos)).toList();
        Set<String> usedTypes = new LinkedHashSet<>();
        for (SmartInterfaceBlockEntity smartInterface : ordered) {
            var binding = smartInterface.bindingFor(controllerPos);
            if (binding.isPresent() && (!binding.get().machineId().equals(controller.getFoundMachine().registryName())
                    || !types.containsKey(binding.get().type()))) {
                smartInterface.unbind(controllerPos);
            } else {
                binding.ifPresent(value -> usedTypes.add(value.type()));
            }
        }

        SmartInterfaceType fallback = types.values().stream()
                .max(Comparator.comparingInt(SmartInterfaceType::priority).thenComparing(SmartInterfaceType::type))
                .orElse(null);
        for (SmartInterfaceBlockEntity smartInterface : ordered) {
            if (smartInterface.bindingFor(controllerPos).isPresent()) continue;
            SmartInterfaceType type = types.values().stream().filter(candidate -> !usedTypes.contains(candidate.type()))
                    .findFirst().orElse(fallback);
            if (type != null && smartInterface.bind(controllerPos, controller.getFoundMachine().registryName(), type.type(), type.defaultValue())) {
                usedTypes.add(type.type());
            }
        }
    }

    public void unbindAll(MachineControllerBlockEntity controller, Collection<SmartInterfaceBlockEntity> interfaces) {
        for (SmartInterfaceBlockEntity smartInterface : interfaces) smartInterface.unbind(controller.getBlockPos());
    }
}
