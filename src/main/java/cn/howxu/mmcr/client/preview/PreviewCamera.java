package cn.howxu.mmcr.client.preview;

import org.joml.Vector3f;

/**
 * Mutable orbit camera used by a structure preview.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PreviewCamera {
    private static final float MIN_DISTANCE = 1.0F;
    private static final float MAX_DISTANCE = 256.0F;
    private static final float MIN_PITCH = -1.55F;
    private static final float MAX_PITCH = 1.55F;
    private static final float INITIAL_YAW = (float) Math.toRadians(45.0D);
    private static final float INITIAL_PITCH = (float) Math.toRadians(35.0D);

    private final Vector3f lookAt = new Vector3f();
    private float distance = MIN_DISTANCE;
    private float yaw = INITIAL_YAW;
    private float pitch = INITIAL_PITCH;
    private long rotationVersion;

    public void reset(Vector3f center, float radius) {
        lookAt.set(center);
        distance = clamp(radius, MIN_DISTANCE, MAX_DISTANCE);
        if (yaw != INITIAL_YAW || pitch != INITIAL_PITCH) rotationVersion++;
        yaw = INITIAL_YAW;
        pitch = INITIAL_PITCH;
    }

    public void orbit(float yawDelta, float pitchDelta) {
        float updatedYaw = yaw + yawDelta;
        float updatedPitch = clamp(pitch + pitchDelta, MIN_PITCH, MAX_PITCH);
        if (updatedYaw == yaw && updatedPitch == pitch) return;
        yaw = updatedYaw;
        pitch = updatedPitch;
        rotationVersion++;
    }

    public void pan(float x, float y) {
        float horizontalX = (float) Math.cos(yaw);
        float horizontalZ = (float) -Math.sin(yaw);
        lookAt.add(horizontalX * x, -y, horizontalZ * x);
    }

    public void zoom(float factor) {
        distance = clamp(distance * factor, MIN_DISTANCE, MAX_DISTANCE);
    }

    public Vector3f position() {
        float horizontalDistance = distance * (float) Math.cos(pitch);
        return new Vector3f(lookAt).add(
                horizontalDistance * (float) Math.sin(yaw),
                distance * (float) Math.sin(pitch),
                horizontalDistance * (float) Math.cos(yaw));
    }

    public Vector3f lookAt() {
        return new Vector3f(lookAt);
    }

    public float distance() {
        return distance;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public long rotationVersion() {
        return rotationVersion;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
