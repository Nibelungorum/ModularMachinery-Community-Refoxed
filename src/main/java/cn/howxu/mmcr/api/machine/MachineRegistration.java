package cn.howxu.mmcr.api.machine;

import net.minecraft.resources.Identifier;

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
        Identifier recipeFamilyId,
        boolean allowModifiers
) {
    public MachineRegistration {
        if (id == null) throw new IllegalArgumentException("id null");
        if (localizedName == null || localizedName.isBlank()) throw new IllegalArgumentException("localizedName blank");
        controllerSpec = controllerSpec == null ? MachineControllerSpec.defaultsFor(id) : controllerSpec;
        recipeFamilyId = recipeFamilyId == null ? id : recipeFamilyId;
    }

    public static Builder builder(Identifier id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final Identifier id;
        private String localizedName = "Unknown Machine";
        private MachineControllerSpec controllerSpec;
        private Identifier recipeFamilyId;
        private boolean allowModifiers;

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

        public Builder recipeFamilyId(Identifier recipeFamilyId) {
            this.recipeFamilyId = recipeFamilyId;
            return this;
        }

        public Builder allowModifiers(boolean allowModifiers) {
            this.allowModifiers = allowModifiers;
            return this;
        }

        public MachineRegistration build() {
            return new MachineRegistration(id, localizedName, controllerSpec, recipeFamilyId, allowModifiers);
        }
    }
}
