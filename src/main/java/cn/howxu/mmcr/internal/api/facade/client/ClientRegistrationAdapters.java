package cn.howxu.mmcr.internal.api.facade.client;

import cn.howxu.mmcr.api.jei.JeiWorkstationRegistration;
import cn.howxu.mmcr.publicapi.client.jei.RecipeInformation;
import cn.howxu.mmcr.publicapi.client.jei.RecipeInformationRegistrar;
import cn.howxu.mmcr.publicapi.client.jei.Workstation;
import cn.howxu.mmcr.publicapi.client.jei.WorkstationRegistrar;
import cn.howxu.mmcr.publicapi.client.render.ControllerRenderer;
import cn.howxu.mmcr.publicapi.client.render.RendererRegistrar;
import cn.howxu.mmcr.api.recipe.RecipeInformationRegistration;
import cn.howxu.mmcr.api.jei.WorkstationRegistration;
import cn.howxu.mmcr.api.render.RenderRegistration;
import cn.howxu.mmcr.publicapi.event.RegisterControllerRenderersEvent;
import cn.howxu.mmcr.publicapi.event.RegisterJeiRecipeInformationEvent;
import cn.howxu.mmcr.publicapi.event.RegisterJeiWorkstationsEvent;
import cn.howxu.mmcr.publicapi.registration.RegistrationException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/** Client collector lifecycle and typed core snapshots; these are not public API entrypoints.
 * @author howxu <dev@howxu.cn>
 */
public final class ClientRegistrationAdapters {
    private ClientRegistrationAdapters() { }
    public static RendererRegistrar renderers(Collection<ResourceLocation> ids) {
        try { return new Renderers(new RenderRegistration(ids)); }
        catch (IllegalStateException exception) { throw rejected(exception); }
    }
    public static WorkstationRegistrar workstations() { return new Workstations(new WorkstationRegistration()); }
    public static RecipeInformationRegistrar information() { return new Information(new RecipeInformationRegistration()); }
    public static void freeze(RegisterControllerRenderersEvent event) { ((Renderers) event.registrar()).core.freeze(); }
    public static void freeze(RegisterJeiWorkstationsEvent event) { ((Workstations) event.registrar()).core.freeze(); }
    public static void freeze(RegisterJeiRecipeInformationEvent event) { ((Information) event.registrar()).core.freeze(); }
    public static Map<ResourceLocation, cn.howxu.mmcr.api.render.ControllerRenderer> coreRenderers(RegisterControllerRenderersEvent event) {
        return ((Renderers) event.registrar()).core.renderers();
    }
    public static List<JeiWorkstationRegistration> coreEntries(RegisterJeiWorkstationsEvent event) {
        return ((Workstations) event.registrar()).core.entries();
    }
    public static List<cn.howxu.mmcr.api.recipe.RecipeInformation> coreEntries(RegisterJeiRecipeInformationEvent event) {
        return ((Information) event.registrar()).core.entries();
    }

    private static RegistrationException rejected(IllegalStateException exception) {
        return new RegistrationException(exception.getMessage(), exception);
    }
    private static void register(Runnable operation) {
        try { operation.run(); }
        catch (IllegalStateException exception) { throw rejected(exception); }
    }

    private static final class Renderers implements RendererRegistrar {
        private final RenderRegistration core;
        private final Map<ResourceLocation, ControllerRenderer> views = new LinkedHashMap<>();
        private Renderers(RenderRegistration core) { this.core = core; }
        public void register(ResourceLocation id, ControllerRenderer renderer) {
            Objects.requireNonNull(id, "machineId");
            Objects.requireNonNull(renderer, "renderer");
            ClientRegistrationAdapters.register(() -> core.register(id, RenderAdapters.toCore(renderer)));
            views.put(id, renderer);
        }
        public Map<ResourceLocation, ControllerRenderer> renderers() {
            return Collections.unmodifiableMap(new LinkedHashMap<>(views));
        }
    }
    private record Workstations(WorkstationRegistration core) implements WorkstationRegistrar {
        public void addRecipePoolWorkstation(ResourceLocation poolId, ResourceLocation itemId) {
            register(() -> core.addRecipePoolWorkstation(poolId, itemId));
        }
        public void addRecipePoolWorkstation(ResourceLocation poolId, ItemStack stack) {
            register(() -> core.addRecipePoolWorkstation(poolId, stack));
        }
        public void addRecipePoolWorkstation(ResourceLocation poolId, ItemLike item) {
            register(() -> core.addRecipePoolWorkstation(poolId, item));
        }
        public void addMachineWorkstation(ResourceLocation machineId, ResourceLocation typeId) {
            register(() -> core.addMachineWorkstation(machineId, typeId));
        }
        public List<Workstation> entries() { return core.entries().stream().map(ClientRegistrationAdapters::wrap).toList(); }
    }
    private record Information(RecipeInformationRegistration core) implements RecipeInformationRegistrar {
        public void registerRecipePool(ResourceLocation id, String key, Object... args) {
            register(() -> core.registerRecipePool(id, key, args));
        }
        public void registerRecipe(ResourceLocation id, String key, Object... args) {
            register(() -> core.registerRecipe(id, key, args));
        }
        public List<RecipeInformation> entries() {
            return core.entries().stream().map(value -> (RecipeInformation) new InformationView(value)).toList();
        }
    }
    private static Workstation wrap(JeiWorkstationRegistration value) {
        return switch (value) {
            case JeiWorkstationRegistration.RecipePoolItem item -> new PoolItemView(item);
            case JeiWorkstationRegistration.RecipePoolStack stack -> new PoolStackView(stack);
            case JeiWorkstationRegistration.Machine machine -> new MachineView(machine);
        };
    }
    private record PoolItemView(JeiWorkstationRegistration.RecipePoolItem core) implements Workstation.RecipePoolItem {
        public ResourceLocation recipePoolId() { return core.recipePoolId(); }
        public ResourceLocation itemId() { return core.itemId(); }
    }
    private record PoolStackView(JeiWorkstationRegistration.RecipePoolStack core) implements Workstation.RecipePoolStack {
        public ResourceLocation recipePoolId() { return core.recipePoolId(); }
        public ItemStack workstation() { return core.workstation(); }
    }
    private record MachineView(JeiWorkstationRegistration.Machine core) implements Workstation.Machine {
        public ResourceLocation machineId() { return core.machineId(); }
        public ResourceLocation recipeTypeId() { return core.recipeTypeId(); }
    }
    private record InformationView(cn.howxu.mmcr.api.recipe.RecipeInformation core) implements RecipeInformation {
        public Target target() {
            return switch (core.target()) { case RECIPE_POOL -> Target.RECIPE_POOL; case RECIPE -> Target.RECIPE; };
        }
        public ResourceLocation targetId() { return core.targetId(); }
        public String translationKey() { return core.translationKey(); }
        public List<Object> arguments() { return core.arguments(); }
        public Component component() { return core.component(); }
    }
}
