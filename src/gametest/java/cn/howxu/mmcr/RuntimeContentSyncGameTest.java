package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.network.RuntimeContentSync;
import cn.howxu.mmcr.internal.sync.RuntimeContentSnapshot;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Verifies that the final datapack sync includes content published after server scripts finish.
 * @author howxu <dev@howxu.cn>
 */
public final class RuntimeContentSyncGameTest {
    public static void finalDatapackSyncIncludesLateRecipes(GameTestHelper helper) {
        var previous = RecipeRegistry.kubeJSSnapshot();
        var early = RuntimeContentSync.createSnapshot();
        var recipeId = MMCR.id("late_datapack_sync_recipe");
        MachineRecipe recipe = RecipeRegistry.recipes().getFirst().withId(recipeId);
        List<RuntimeContentSnapshot> sent = new ArrayList<>();
        RuntimeContentSync.setSenderForTesting((server, snapshot) -> sent.add(snapshot));
        try {
            var recipes = new LinkedHashMap<>(previous);
            recipes.put(recipeId, recipe);
            RecipeRegistry.replaceKubeJS(recipes);
            NeoForge.EVENT_BUS.post(new OnDatapackSyncEvent(helper.getLevel().getServer().getPlayerList(), null));

            helper.assertTrue(!early.recipes().containsKey(recipeId), "Early script snapshot lacks the late recipe");
            helper.assertTrue(sent.size() == 1, "Final datapack event sends one complete runtime snapshot");
            var synced = sent.getFirst();
            helper.assertTrue(recipe.equals(synced.recipes().get(recipeId)), "Final sync includes the late KubeJS recipe");
            helper.assertTrue(synced.structures().equals(MachineStructureRegistry.effectiveSnapshot()),
                    "Final sync includes the authoritative structures");
            helper.assertTrue(synced.contentVersion() > early.contentVersion(), "Final sync supersedes the early version");
            helper.succeed();
        } finally {
            RuntimeContentSync.resetSenderForTesting();
            RecipeRegistry.replaceKubeJS(previous);
        }
    }
}
