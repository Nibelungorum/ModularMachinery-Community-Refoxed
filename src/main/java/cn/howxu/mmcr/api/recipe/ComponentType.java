package cn.howxu.mmcr.api.recipe;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public abstract class ComponentType {

    private Identifier registryName;

    public Identifier getRegistryName() {
        return registryName;
    }

    public void setRegistryName(Identifier name) {
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
