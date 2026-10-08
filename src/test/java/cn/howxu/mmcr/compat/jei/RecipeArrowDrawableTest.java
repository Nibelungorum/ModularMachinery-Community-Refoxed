package cn.howxu.mmcr.compat.jei;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.joml.Vector2f;
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
        Matrix3x2fStack pose = new Matrix3x2fStack(4);
        pose.pushMatrix();
        pose.translate(5, 9).scale(2, 3);
        Matrix3x2f original = new Matrix3x2f(pose);
        GuiGraphicsExtractor graphics = graphicsWithPose(pose);
        AtomicReference<Object[]> drawn = new AtomicReference<>();
        AtomicReference<Matrix3x2f> delegatePose = new AtomicReference<>();
        IDrawable nativeArrow = (IDrawable) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{IDrawable.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWidth" -> 22;
                    case "getHeight" -> 16;
                    case "draw" -> {
                        drawn.set(args);
                        delegatePose.set(new Matrix3x2f(((GuiGraphicsExtractor) args[0]).pose()));
                        yield null;
                    }
                    default -> null;
                });

        new RecipeArrowDrawable(nativeArrow, true).draw(graphics, 72, 17);

        assertThat(drawn.get()).containsExactly(graphics, 0, 0);
        assertThat(delegatePose.get()).isNotNull();
        Matrix3x2f localPose = new Matrix3x2f(original).invert().mul(delegatePose.get());
        assertPosition(localPose, 0, 0, 88, 17);
        assertPosition(localPose, 22, 0, 88, 39);
        assertPosition(localPose, 0, 16, 72, 17);
        assertPosition(localPose, 22, 16, 72, 39);
        Vector2f direction = localPose.transformDirection(1, 0, new Vector2f());
        assertThat(direction.x).isCloseTo(0F, offset(0.0001F));
        assertThat(direction.y).isCloseTo(1F, offset(0.0001F));
        assertThat(pose.get(new float[6])).containsExactly(original.get(new float[6]));
        pose.popMatrix();
        assertThat(pose.get(new float[6])).containsExactly(new Matrix3x2f().get(new float[6]));
    }

    private static void assertPosition(Matrix3x2f pose, float x, float y, float expectedX, float expectedY) {
        Vector2f position = pose.transformPosition(x, y, new Vector2f());
        assertThat(position.x).isCloseTo(expectedX, offset(0.0001F));
        assertThat(position.y).isCloseTo(expectedY, offset(0.0001F));
    }

    private static GuiGraphicsExtractor graphicsWithPose(Matrix3x2fStack pose) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Unsafe unsafe = (Unsafe) field.get(null);
        // The delegate only records the pose; no atlas or render state is accessed.
        GuiGraphicsExtractor graphics = (GuiGraphicsExtractor) unsafe.allocateInstance(GuiGraphicsExtractor.class);
        Field poseField = GuiGraphicsExtractor.class.getDeclaredField("pose");
        poseField.setAccessible(true);
        poseField.set(graphics, pose);
        return graphics;
    }
}
