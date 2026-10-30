package cn.howxu.mmcr.client;

import cn.howxu.mmcr.internal.item.MultiblockDetectorSelection;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Verifies the controller overlay's face colors and outward geometry without a client renderer.
 * @author howxu <dev@howxu.cn>
 */
class MultiblockDetectorSelectionRendererTest {
    @Test
    void controller_only_selection_renders_the_selected_face_red_and_other_faces_yellow() {
        BlockPos controller = new BlockPos(10, 20, -30);
        Vec3 camera = new Vec3(13.25, -4.5, 8.75);
        Vec3 center = Vec3.atCenterOf(controller).subtract(camera);
        List<Direction> faces = List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST,
                Direction.EAST, Direction.UP, Direction.DOWN);

        for (Direction selectedFace : Direction.values()) {
            var selection = MultiblockDetectorSelection.EMPTY.withController(controller, selectedFace);
            List<Vertex> vertices = render(selection, camera);
            assertThat(vertices).hasSize(24);
            for (int i = 0; i < faces.size(); i++) {
                Direction face = faces.get(i);
                Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
                List<Vertex> quad = vertices.subList(i * 4, i * 4 + 4);
                assertThat(quad).extracting(Vertex::color)
                        .containsOnly(face == selectedFace ? 0x66FF0000 : 0x66FFFF00);
                Vec3 winding = quad.get(1).position().subtract(quad.get(0).position())
                        .cross(quad.get(2).position().subtract(quad.get(0).position())).normalize();
                assertThat(winding.subtract(normal).length()).isLessThan(0.00001);
                Vec3 faceCenter = Vec3.ZERO;
                for (Vertex vertex : quad) {
                    Vec3 offset = vertex.position().subtract(center);
                    assertThat(offset.dot(normal)).isCloseTo(0.502, within(0.00001));
                    assertThat(offset.lengthSqr()).isCloseTo(3 * 0.502 * 0.502, within(0.00001));
                    faceCenter = faceCenter.add(vertex.position());
                }
                assertThat(faceCenter.scale(0.25).subtract(center.add(normal.scale(0.502))).length())
                        .isLessThan(0.00001);
            }
        }
    }

    @Test
    void no_controller_overlay_is_emitted_without_a_position_and_selected_face() {
        assertThat(render(MultiblockDetectorSelection.EMPTY, Vec3.ZERO)).isEmpty();
        assertThat(render(new MultiblockDetectorSelection(BlockPos.ZERO, null, null, null), Vec3.ZERO)).isEmpty();
        assertThat(render(new MultiblockDetectorSelection(null, Direction.SOUTH, null, null), Vec3.ZERO)).isEmpty();
    }

    private static List<Vertex> render(MultiblockDetectorSelection selection, Vec3 camera) {
        List<Vertex> vertices = new ArrayList<>();
        Vec3[] pendingPosition = new Vec3[1];
        VertexConsumer buffer = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class}, (proxy, method, args) -> {
                    if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
                    if (method.getName().equals("addVertex") && args.length == 3) {
                        pendingPosition[0] = new Vec3((float) args[0], (float) args[1], (float) args[2]);
                    } else if (method.getName().equals("setColor") && args.length == 1) {
                        vertices.add(new Vertex(pendingPosition[0], (int) args[0]));
                    } else {
                        throw new AssertionError("Unexpected vertex operation: " + method);
                    }
                    return proxy;
                });
        MultiblockDetectorSelectionRenderer.renderControllerFaces(new PoseStack().last(), buffer, camera, selection);
        return vertices;
    }

    private record Vertex(Vec3 position, int color) {}
}
