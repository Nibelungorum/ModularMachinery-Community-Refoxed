package cn.howxu.mmcr;

import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRecipesEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;

/** Test fixture for the shared optional source invocation path.
 * @author howxu <dev@howxu.cn>
 */
public final class OptionalGameTestSource {
    private static boolean invoked;
    private static boolean structuresInvoked;
    private static boolean recipesInvoked;

    private OptionalGameTestSource() {
    }

    public static void accept(MMCRMachineDefinationsEvent event) {
        invoked = true;
    }

    public static boolean invoked() {
        return invoked;
    }

    public static void acceptStructures(MMCRMachineStructuresEvent event) {
        structuresInvoked = true;
    }

    public static void acceptRecipes(MMCRMachineRecipesEvent event) {
        recipesInvoked = true;
    }

    public static boolean structuresInvoked() {
        return structuresInvoked;
    }

    public static boolean recipesInvoked() {
        return recipesInvoked;
    }
}
