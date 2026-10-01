package cn.howxu.mmcr.publicapi;

import cn.howxu.mmcr.publicapi.behavior.RecipeHooks;
import cn.howxu.mmcr.publicapi.data.DataStore;
import cn.howxu.mmcr.publicapi.event.RegisterMachineDefinitionsEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineRecipesEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.publicapi.machine.MachineDraft;
import cn.howxu.mmcr.publicapi.machine.MachineSpec;
import cn.howxu.mmcr.publicapi.recipe.RecipeDraft;
import cn.howxu.mmcr.publicapi.recipe.RecipeSpec;
import cn.howxu.mmcr.publicapi.registration.MachineDefinitionProvider;
import cn.howxu.mmcr.publicapi.runtime.IoCommitResult;
import cn.howxu.mmcr.publicapi.runtime.IoTransaction;
import cn.howxu.mmcr.publicapi.structure.StructureDraft;
import cn.howxu.mmcr.publicapi.structure.StructureSpec;
import java.lang.reflect.ParameterizedType;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Actual factory/callback signatures; comprehensive consumption is compiled by compileApiUsage.
 * @author howxu <dev@howxu.cn>
 */
class PublicApiInventoryTest {
    @Test
    void factories_return_public_drafts_and_drafts_build_public_specs() throws Exception {
        assertEquals(MachineDraft.class, Machines.class.getMethod("machine", ResourceLocation.class).getReturnType());
        assertEquals(RecipeDraft.class, Recipes.class.getMethod("recipe", ResourceLocation.class).getReturnType());
        assertEquals(StructureDraft.class, Structures.class.getMethod("structure").getReturnType());
        assertEquals(MachineSpec.class, MachineDraft.class.getMethod("build").getReturnType());
        assertEquals(RecipeSpec.class, RecipeDraft.class.getMethod("build").getReturnType());
        assertEquals(StructureSpec.class, StructureDraft.class.getMethod("build", ResourceLocation.class).getReturnType());
    }

    @Test
    void callbacks_and_provider_use_the_new_typed_public_contracts() throws Exception {
        ParameterizedType hooks = (ParameterizedType) MachineDraft.class
                .getMethod("recipeBehavior", Consumer.class).getGenericParameterTypes()[0];
        assertEquals(RecipeHooks.class, hooks.getActualTypeArguments()[0]);
        ParameterizedType transaction = (ParameterizedType) IoTransaction.class
                .getMethod("commitData", Consumer.class).getGenericParameterTypes()[0];
        assertEquals(DataStore.Transaction.class, transaction.getActualTypeArguments()[0]);
        assertEquals(IoCommitResult.class, IoTransaction.class.getMethod("commitData", Consumer.class).getReturnType());
        assertEquals(void.class, MachineDefinitionProvider.class
                .getMethod("register", RegisterMachineDefinitionsEvent.class).getReturnType());
        for (Class<?> event : new Class<?>[] {RegisterMachineDefinitionsEvent.class,
                RegisterMachineStructuresEvent.class, RegisterMachineRecipesEvent.class}) {
            assertTrue(event.getMethod("registrar").getReturnType().getPackageName()
                    .equals("cn.howxu.mmcr.publicapi.registration"));
        }
    }
}
