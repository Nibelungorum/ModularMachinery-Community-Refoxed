package cn.howxu.mmcr.api.publicapi.event;

import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

/** Canonical event for collecting machine declarations.
 * @author howxu <dev@howxu.cn>
 */
public class MMCRMachineDefinationsEvent extends Event implements IModBusEvent {
    private final Map<Identifier, MachineDefinition> definitions = new LinkedHashMap<>();
    private boolean frozen;

    public void registerMachine(Identifier id, UnaryOperator<MachineBuilder> consumer) {
        if (frozen) throw new IllegalStateException("Machine definitions are frozen");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(consumer, "consumer");
        if (definitions.containsKey(id)) throw new IllegalStateException("Duplicate machine: " + id);
        MachineDefinition definition = Objects.requireNonNull(consumer.apply(MachineBuilder.machine(id)),
                "consumer result").build();
        definitions.put(id, definition);
    }

    public void registerMachine(MachineDefinition definition) {
        if (frozen) throw new IllegalStateException("Machine definitions are frozen");
        Objects.requireNonNull(definition, "definition");
        if (definitions.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalStateException("Duplicate machine: " + definition.id());
        }
    }

    public Map<Identifier, MachineDefinition> definitions() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }

    public void freeze() {
        frozen = true;
    }
}
