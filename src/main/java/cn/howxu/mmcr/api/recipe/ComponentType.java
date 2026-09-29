package cn.howxu.mmcr.api.recipe;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public abstract class ComponentType {

    private ResourceLocation registryName;

    public ResourceLocation getRegistryName() {
        return registryName;
    }

    public void setRegistryName(ResourceLocation name) {
        this.registryName = name;
    }

    @Nullable
    public String requiresModid() {
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ComponentType that = (ComponentType) o;
        return getRegistryName() != null && getRegistryName().equals(that.getRegistryName());
    }

    @Override
    public int hashCode() {
        return getRegistryName() == null ? 0 : getRegistryName().hashCode();
    }
}
