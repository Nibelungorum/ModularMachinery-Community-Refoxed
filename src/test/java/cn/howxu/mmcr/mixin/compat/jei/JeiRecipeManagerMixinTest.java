package cn.howxu.mmcr.mixin.compat.jei;

import cn.howxu.mmcr.compat.jei.JeiRecipeBackground;
import cn.howxu.mmcr.compat.jei.MachineRecipeCategory;
import cn.howxu.mmcr.compat.jei.MachineStructureCategory;
import mezz.jei.api.gui.drawable.IScalableDrawable;
import mezz.jei.api.recipe.category.IRecipeCategory;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks invocation targets without debug metadata and preserves non-MMCR recipe backgrounds.
 *
 * @author howxu <dev@howxu.cn>
 */
class JeiRecipeManagerMixinTest {
    @Test
    void injectionTargetsExistWithoutLocalVariableMetadata() throws Exception {
        ClassNode manager = new ClassNode();
        try (var stream = getClass().getResourceAsStream("/mezz/jei/library/recipes/RecipeManager.class")) {
            assertThat(stream).isNotNull();
            new ClassReader(stream).accept(manager, ClassReader.SKIP_DEBUG);
        }
        int hooks = 0;
        for (Method handler : JeiRecipeManagerMixin.class.getDeclaredMethods()) {
            ModifyArgs injection = handler.getAnnotation(ModifyArgs.class);
            if (injection == null) continue;
            hooks++;
            for (String selector : injection.method()) {
                var targets = manager.methods.stream()
                        .filter(method -> selector.equals(method.name) || selector.equals(method.name + method.desc))
                        .toList();
                assertThat(targets).as(selector).hasSize(1);
                var target = targets.getFirst();
                assertThat(target.localVariables).isNullOrEmpty();
                boolean found = false;
                for (var instruction : target.instructions) {
                    if (instruction instanceof MethodInsnNode call) {
                        String invocation = "L" + call.owner + ";" + call.name + call.desc;
                        if (invocation.equals(injection.at().target())) found = true;
                    }
                }
                assertThat(found).as("Invocation target in %s", selector).isTrue();
            }
        }
        assertThat(hooks).isEqualTo(2);
    }

    @Test
    void replacesOnlyMmcrBackgroundArgumentsIncludingErrorLayouts() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Unsafe unsafe = (Unsafe) field.get(null);
        IRecipeCategory<?> machine = (IRecipeCategory<?>) unsafe.allocateInstance(MachineRecipeCategory.class);
        IRecipeCategory<?> structure = (IRecipeCategory<?>) unsafe.allocateInstance(MachineStructureCategory.class);
        IRecipeCategory<?> other = (IRecipeCategory<?>) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{IRecipeCategory.class}, (proxy, method, args) -> null);
        IScalableDrawable original = (graphics, x, y, width, height) -> {};
        for (IRecipeCategory<?> category : new IRecipeCategory<?>[]{machine, structure, other}) {
            IScalableDrawable expected = category == machine ? JeiRecipeBackground.INSTANCE
                    : category == structure ? JeiRecipeBackground.PREVIEW : original;
            for (String name : new String[]{"mmcr$replaceRecipeBackground", "mmcr$replaceErrorBackground"}) {
                boolean errorLayout = name.equals("mmcr$replaceErrorBackground");
                Object[] values = errorLayout
                        ? new Object[]{category, new Object(), original, 4}
                        : new Object[]{category, new Object(), new Object(), new Object(), new Object(), original, 4};
                Object[] before = values.clone();
                Args args = new TestArgs(values);
                Method hook = JeiRecipeManagerMixin.class.getDeclaredMethod(name, Args.class);
                hook.setAccessible(true);
                hook.invoke(null, args);
                int backgroundIndex = errorLayout ? 2 : 5;
                assertThat(args.<Object>get(backgroundIndex)).isSameAs(expected);
                for (int index = 0; index < values.length; index++) {
                    if (index != backgroundIndex) assertThat(args.<Object>get(index)).isSameAs(before[index]);
                }
            }
        }
    }

    private static final class TestArgs extends Args {
        private TestArgs(Object[] values) {
            super(values);
        }

        @Override
        public <T> void set(int index, T value) {
            values[index] = value;
        }

        @Override
        public void setAll(Object... values) {
            System.arraycopy(values, 0, this.values, 0, values.length);
        }
    }
}
