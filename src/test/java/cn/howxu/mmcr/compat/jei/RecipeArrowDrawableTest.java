package cn.howxu.mmcr.compat.jei;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/**
 * Verifies that recipe extras use the geometry and native animated drawable.
 *
 * @author howxu <dev@howxu.cn>
 */
class RecipeArrowDrawableTest {
    @ParameterizedTest
    @CsvSource({"1,1,false", "4,3,false", "12,3,false", "24,7,true", "1,64,true"})
    void registersTheNativeArrowAtTheComputedPosition(int inputs, int outputs, boolean vertical) {
        AtomicReference<Integer> cycle = new AtomicReference<>();
        IDrawableAnimated nativeArrow = (IDrawableAnimated) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{IDrawableAnimated.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWidth" -> 22;
                    case "getHeight" -> 16;
                    default -> null;
                });
        IGuiHelper helper = (IGuiHelper) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{IGuiHelper.class}, (proxy, method, args) -> {
                    if (method.getName().equals("createAnimatedRecipeArrow")) {
                        cycle.set((Integer) args[0]);
                        return nativeArrow;
                    }
                    return null;
                });
        AtomicReference<Object[]> added = new AtomicReference<>();
        IRecipeExtrasBuilder extras = (IRecipeExtrasBuilder) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{IRecipeExtrasBuilder.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("addDrawable") && args.length == 3) added.set(args);
                    return null;
                });
        RecipeSlotLayout.Arrow arrow = RecipeSlotLayout.forCounts(inputs, outputs).arrow();
        MachineRecipeCategory.addRecipeArrow(extras, helper, arrow);
        assertThat(cycle.get()).isEqualTo(200);
        assertThat(added.get()).isNotNull();
        assertThat(added.get()[1]).isEqualTo(arrow.x());
        assertThat(added.get()[2]).isEqualTo(arrow.y());
        IDrawable drawable = (IDrawable) added.get()[0];
        assertThat(drawable.getWidth()).isEqualTo(arrow.width());
        assertThat(drawable.getHeight()).isEqualTo(arrow.height());
        assertThat(drawable.getWidth()).isEqualTo(vertical ? 16 : 22);
        assertThat(drawable.getHeight()).isEqualTo(vertical ? 22 : 16);
    }

    @Test
    void horizontalArrowDrawsTheNativeDelegateAtTheRequestedOffset() {
        AtomicReference<Object[]> drawn = new AtomicReference<>();
        IDrawable nativeArrow = (IDrawable) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{IDrawable.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("draw")) drawn.set(args);
                    return null;
                });

        new RecipeArrowDrawable(nativeArrow, false).draw(null, 72, 17);

        assertThat(drawn.get()).containsExactly(null, 72, 17);
    }

    @Test
    void verticalArrowRotatesTheNativeRectangleDownwardAndRestoresTheCallerPose() throws Exception {
        PoseStack pose = new PoseStack();
        pose.pushPose();
        pose.translate(5, 9, 0);
        pose.scale(2, 3, 1);
        Matrix4f original = new Matrix4f(pose.last().pose());
        GuiGraphics graphics = graphicsWithPose(pose);
        AtomicReference<Object[]> drawn = new AtomicReference<>();
        AtomicReference<Matrix4f> delegatePose = new AtomicReference<>();
        IDrawable nativeArrow = (IDrawable) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{IDrawable.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWidth" -> 22;
                    case "getHeight" -> 16;
                    case "draw" -> {
                        drawn.set(args);
                        delegatePose.set(new Matrix4f(((GuiGraphics) args[0]).pose().last().pose()));
                        yield null;
                    }
                    default -> null;
                });

        new RecipeArrowDrawable(nativeArrow, true).draw(graphics, 72, 17);

        assertThat(drawn.get()).containsExactly(graphics, 0, 0);
        assertThat(delegatePose.get()).isNotNull();
        Matrix4f localPose = new Matrix4f(original).invert().mul(delegatePose.get());
        assertPosition(localPose, 0, 0, 88, 17);
        assertPosition(localPose, 22, 0, 88, 39);
        assertPosition(localPose, 0, 16, 72, 17);
        assertPosition(localPose, 22, 16, 72, 39);
        Vector3f direction = localPose.transformDirection(new Vector3f(1, 0, 0));
        assertThat(direction.x).isCloseTo(0F, offset(0.0001F));
        assertThat(direction.y).isCloseTo(1F, offset(0.0001F));
        assertThat(pose.last().pose().get(new float[16])).containsExactly(original.get(new float[16]));
        pose.popPose();
        assertThat(pose.last().pose().get(new float[16])).containsExactly(new Matrix4f().get(new float[16]));
    }

    private static void assertPosition(Matrix4f pose, float x, float y, float expectedX, float expectedY) {
        Vector3f position = pose.transformPosition(new Vector3f(x, y, 0));
        assertThat(position.x).isCloseTo(expectedX, offset(0.0001F));
        assertThat(position.y).isCloseTo(expectedY, offset(0.0001F));
    }

    private static GuiGraphics graphicsWithPose(PoseStack pose) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Unsafe unsafe = (Unsafe) field.get(null);
        // The delegate only records the pose; no atlas or render state is accessed.
        GuiGraphics graphics = (GuiGraphics) unsafe.allocateInstance(GuiGraphics.class);
        Field poseField = GuiGraphics.class.getDeclaredField("pose");
        poseField.setAccessible(true);
        poseField.set(graphics, pose);
        return graphics;
    }
}
