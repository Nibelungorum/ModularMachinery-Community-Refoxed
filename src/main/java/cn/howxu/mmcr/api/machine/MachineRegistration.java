package cn.howxu.mmcr.api.machine;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Startup-time machine declaration. A registration must exist before controller
 * blocks are registered, and it intentionally contains no reloadable structure.
 *
 * @author howxu <dev@howxu.cn>
 */
public record MachineRegistration(
        Identifier id,
        String displayNameKey,
        MachineControllerSpec controllerSpec,
        MachineAppearanceSpec appearance,
        Identifier recipeFamilyId,
        boolean allowModifiers,
        boolean allowMultithreading,
        boolean allowParallelism,
        int maxParallelAmount,
        Map<String, SmartInterfaceType> smartInterfaceTypes,
        boolean shareSmartInterfaces,
        List<SmartInterfaceModifier> smartInterfaceModifiers
) {
    public MachineRegistration {
        if (id == null) throw new IllegalArgumentException("id null");
        displayNameKey = defaultDisplayNameKey(id, displayNameKey);
        controllerSpec = controllerSpec == null ? MachineControllerSpec.defaultsFor(id) : controllerSpec;
        appearance = appearance == null ? MachineAppearanceSpec.defaults() : appearance;
        recipeFamilyId = recipeFamilyId == null ? id : recipeFamilyId;
        maxParallelAmount = Math.max(1, maxParallelAmount);
        smartInterfaceTypes = Collections.unmodifiableMap(new LinkedHashMap<>(smartInterfaceTypes));
        smartInterfaceModifiers = smartInterfaceModifiers == null ? List.of() : List.copyOf(smartInterfaceModifiers);
    }

    public static Builder builder(Identifier id) {
        return new Builder(id);
    }

    public Component displayName() {
        return Component.translatable(displayNameKey);
    }

    @Deprecated(forRemoval = true)
    public String localizedName() {
        return displayNameKey;
    }

    public static String defaultDisplayNameKey(Identifier id) {
        return defaultDisplayNameKey(id, null);
    }

    public static String defaultDisplayNameKey(Identifier id, String explicitKey) {
        if (id == null) throw new IllegalArgumentException("id null");
        if (explicitKey != null && !explicitKey.isBlank()) return explicitKey;
        return "machine." + id.getNamespace() + "." + id.getPath();
    }

    public static final class Builder {
        private final Identifier id;
        private String displayNameKey;
        private MachineControllerSpec controllerSpec;
        private MachineAppearanceSpec appearance;
        private Identifier recipeFamilyId;
        private boolean allowModifiers;
        private boolean allowMultithreading;
        private boolean allowParallelism;
        private int maxParallelAmount = 1;
        private final Map<String, SmartInterfaceType> smartInterfaceTypes = new LinkedHashMap<>();
        private boolean shareSmartInterfaces;
        private final List<SmartInterfaceModifier> smartInterfaceModifiers = new ArrayList<>();

        private Builder(Identifier id) {
            this.id = id;
        }

        public Builder displayNameKey(String displayNameKey) {
            this.displayNameKey = displayNameKey;
            return this;
        }

        @Deprecated(forRemoval = true)
        public Builder localizedName(String localizedName) {
            return displayNameKey(localizedName);
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

        public Builder shareSmartInterfaces(boolean shareSmartInterfaces) {
            this.shareSmartInterfaces = shareSmartInterfaces;
            return this;
        }

        public Builder smartInterfaceModifier(SmartInterfaceModifier modifier) {
            smartInterfaceModifiers.add(Objects.requireNonNull(modifier));
            return this;
        }

        public MachineRegistration build() {
            return new MachineRegistration(id, displayNameKey, controllerSpec, appearance, recipeFamilyId, allowModifiers,
                    allowMultithreading, allowParallelism, maxParallelAmount, smartInterfaceTypes, shareSmartInterfaces,
                    smartInterfaceModifiers);
        }
    }
}
