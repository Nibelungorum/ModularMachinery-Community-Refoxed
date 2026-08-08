package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import dev.latvian.mods.kubejs.registry.BuilderBase;
import net.minecraft.resources.Identifier;

public class MachineBuilderJS extends BuilderBase<MachineRegistration> {
    public transient String localizedName = "Unknown Machine";
    public transient Identifier controllerFrontTexture;
    public transient Identifier controllerSideTexture;
    public transient Identifier controllerTopTexture;
    public transient Identifier controllerBottomTexture;
    public transient boolean allowVerticalFacing = false;
    public transient boolean fullyRotationallySymmetric = false;
    public transient boolean requireVerticalFacing = false;
    public transient boolean allowModifiers = false;

    public MachineBuilderJS(Identifier id) {
        super(id);
    }

    public MachineBuilderJS(String id) {
        this(Identifier.parse(id));
    }

    public MachineBuilderJS localizedName(String name) {
        this.localizedName = name;
        return this;
    }

    @Override
    public MachineRegistration createObject() {
        return MachineRegistration.builder(id)
                .localizedName(localizedName)
                .controllerSpec(controllerSpec())
                .recipeFamilyId(id)
                .allowModifiers(allowModifiers)
                .build();
    }

    public MachineBuilderJS controllerTextures(String front, String otherFive) {
        return controllerTextures(Identifier.parse(front), Identifier.parse(otherFive));
    }

    public MachineBuilderJS controllerTextures(Identifier front, Identifier otherFive) {
        this.controllerFrontTexture = front;
        this.controllerSideTexture = otherFive;
        this.controllerTopTexture = otherFive;
        this.controllerBottomTexture = otherFive;
        return this;
    }

    public MachineBuilderJS controllerTextures(Identifier front, Identifier side, Identifier top, Identifier bottom) {
        this.controllerFrontTexture = front;
        this.controllerSideTexture = side;
        this.controllerTopTexture = top;
        this.controllerBottomTexture = bottom;
        return this;
    }

    public MachineBuilderJS controllerFrontTexture(String texture) {
        return controllerFrontTexture(Identifier.parse(texture));
    }

    public MachineBuilderJS controllerFrontTexture(Identifier texture) {
        this.controllerFrontTexture = texture;
        return this;
    }

    public MachineBuilderJS controllerSideTexture(String texture) {
        return controllerSideTexture(Identifier.parse(texture));
    }

    public MachineBuilderJS controllerSideTexture(Identifier texture) {
        this.controllerSideTexture = texture;
        return this;
    }

    public MachineBuilderJS controllerTopTexture(String texture) {
        return controllerTopTexture(Identifier.parse(texture));
    }

    public MachineBuilderJS controllerTopTexture(Identifier texture) {
        this.controllerTopTexture = texture;
        return this;
    }

    public MachineBuilderJS controllerBottomTexture(String texture) {
        return controllerBottomTexture(Identifier.parse(texture));
    }

    public MachineBuilderJS controllerBottomTexture(Identifier texture) {
        this.controllerBottomTexture = texture;
        return this;
    }

    public MachineBuilderJS allowVerticalFacing() {
        return allowVerticalFacing(true);
    }

    public MachineBuilderJS allowVerticalFacing(boolean allow) {
        this.allowVerticalFacing = allow;
        return this;
    }

    public MachineBuilderJS fullyRotationallySymmetric() {
        return fullyRotationallySymmetric(true);
    }

    public MachineBuilderJS fullyRotationallySymmetric(boolean symmetric) {
        this.fullyRotationallySymmetric = symmetric;
        return this;
    }

    public MachineBuilderJS requireVerticalFacing() {
        return requireVerticalFacing(true);
    }

    public MachineBuilderJS requireVerticalFacing(boolean required) {
        this.requireVerticalFacing = required;
        if (required) this.allowVerticalFacing = true;
        return this;
    }

    public MachineBuilderJS allowModifiers() {
        return allowModifiers(true);
    }

    public MachineBuilderJS allowModifiers(boolean allow) {
        this.allowModifiers = allow;
        return this;
    }

    public void registerObject() {
        MachineDefinitions.register(createObject());
    }

    public MachineBuilderJS register() {
        registerObject();
        return this;
    }

    private MachineControllerSpec controllerSpec() {
        MachineControllerSpec defaults = MachineControllerSpec.defaultsFor(id);
        return new MachineControllerSpec(
                defaults.id(),
                controllerFrontTexture != null ? controllerFrontTexture : defaults.frontTexture(),
                controllerSideTexture != null ? controllerSideTexture : defaults.sideTexture(),
                controllerTopTexture != null ? controllerTopTexture : defaults.topTexture(),
                controllerBottomTexture != null ? controllerBottomTexture : defaults.bottomTexture(),
                allowVerticalFacing,
                fullyRotationallySymmetric,
                requireVerticalFacing);
    }
}
