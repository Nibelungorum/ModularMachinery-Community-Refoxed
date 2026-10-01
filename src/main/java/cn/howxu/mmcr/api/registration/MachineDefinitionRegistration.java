package cn.howxu.mmcr.api.registration;

import cn.howxu.mmcr.api.machine.definition.MachineBuilder;
import cn.howxu.mmcr.api.machine.definition.MachineDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

/** Canonical collector for machine declarations.
 * @author howxu <dev@howxu.cn>
 */
public class MachineDefinitionRegistration {
    private final Map<ResourceLocation, MachineDefinition> definitions = new LinkedHashMap<>();
    private boolean frozen;

    public void registerMachine(ResourceLocation id, UnaryOperator<MachineBuilder> consumer) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(consumer, "consumer");
        requireAvailable(id);
        String phase = "configuration";
        try {
            MachineBuilder builder = Objects.requireNonNull(consumer.apply(MachineBuilder.machine(id)),
                    "consumer result");
            phase = "build";
            registerMachine(builder.build());
        } catch (RuntimeException exception) {
            throw new ApiRegistrationException("Invalid machine " + id + " during " + phase + ": "
                    + exception.getMessage(), exception);
        }
    }

    public void registerMachine(MachineDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        requireAvailable(definition.id());
        definitions.put(definition.id(), definition);
    }

    private void requireAvailable(ResourceLocation id) {
        if (frozen) throw new ApiRegistrationException("Machine definitions are frozen during registration: " + id);
        if (definitions.containsKey(id)) throw new ApiRegistrationException("Duplicate machine during registration: " + id);
    }

    public Map<ResourceLocation, MachineDefinition> definitions() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }

    public void freeze() {
        frozen = true;
    }
}
