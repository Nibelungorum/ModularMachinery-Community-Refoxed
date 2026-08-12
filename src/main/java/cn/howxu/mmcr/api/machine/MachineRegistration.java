package cn.howxu.mmcr.api.machine;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Startup-time machine declaration. A registration must exist before controller
 * blocks are registered, and it intentionally contains no reloadable structure.
 *
 * @author howxu <dev@howxu.cn>
 */
public record MachineRegistration(
        Identifier id,
        String localizedName,
        MachineControllerSpec controllerSpec,
        MachineAppearanceSpec appearance,
        Identifier recipeFamilyId,
        boolean allowModifiers,
        boolean allowMultithreading,
        boolean allowParallelism,
        int maxParallelAmount,
        Map<String, SmartInterfaceType> smartInterfaceTypes
) {
    public MachineRegistration {
        if (id == null) throw new IllegalArgumentException("id null");
        if (localizedName == null || localizedName.isBlank()) throw new IllegalArgumentException("localizedName blank");
        controllerSpec = controllerSpec == null ? MachineControllerSpec.defaultsFor(id) : controllerSpec;
        appearance = appearance == null ? MachineAppearanceSpec.defaults() : appearance;
        recipeFamilyId = recipeFamilyId == null ? id : recipeFamilyId;
        maxParallelAmount = Math.max(1, maxParallelAmount);
        smartInterfaceTypes = Collections.unmodifiableMap(new LinkedHashMap<>(smartInterfaceTypes));
    }

    public static Builder builder(Identifier id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final Identifier id;
        private String localizedName = "Unknown Machine";
        private MachineControllerSpec controllerSpec;
        private MachineAppearanceSpec appearance;
        private Identifier recipeFamilyId;
        private boolean allowModifiers;
        private boolean allowMultithreading;
        private boolean allowParallelism;
        private int maxParallelAmount = 1;
        private final Map<String, SmartInterfaceType> smartInterfaceTypes = new LinkedHashMap<>();

        private Builder(Identifier id) {
            this.id = id;
        }

        public Builder localizedName(String localizedName) {
            this.localizedName = localizedName;
            return this;
        }

        public Builder controllerSpec(MachineControllerSpec controllerSpec) {
            this.controllerSpec = controllerSpec;
            return this;
        }

        public Builder appearance(MachineAppearanceSpec appearance) {
            this.appearance = appearance;
            return this;
        }

        public Builder recipeFamilyId(Identifier recipeFamilyId) {
            this.recipeFamilyId = recipeFamilyId;
            return this;
        }

        public Builder allowModifiers(boolean allowModifiers) {
            this.allowModifiers = allowModifiers;
            return this;
        }

        public Builder allowMultithreading(boolean allowMultithreading) {
            this.allowMultithreading = allowMultithreading;
            return this;
        }

        public Builder allowParallelism(boolean allowParallelism) {
            this.allowParallelism = allowParallelism;
            return this;
        }

        public Builder maxParallelAmount(int maxParallelAmount) {
            this.maxParallelAmount = maxParallelAmount;
            return this;
        }

        public Builder smartInterfaceType(SmartInterfaceType smartInterfaceType) {
            if (smartInterfaceTypes.putIfAbsent(smartInterfaceType.type(), smartInterfaceType) != null) {
                throw new IllegalArgumentException("Duplicate smart interface type: " + smartInterfaceType.type());
            }
            return this;
        }

        public MachineRegistration build() {
            return new MachineRegistration(id, localizedName, controllerSpec, appearance, recipeFamilyId, allowModifiers,
                    allowMultithreading, allowParallelism, maxParallelAmount, smartInterfaceTypes);
        }
    }
}
