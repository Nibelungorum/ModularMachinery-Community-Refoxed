package cn.howxu.mmcr.mixin.client.preview;

/**
 * Captures the depth attachment state needed to restore the encoder read framebuffer.
 *
 * @author howxu <dev@howxu.cn>
 */
final class DepthAttachmentRestore {
    enum Kind { NONE, TEXTURE, RENDERBUFFER }

    record Attachment(Kind kind, int objectId, int level) {
        static Attachment none() {
            return new Attachment(Kind.NONE, 0, 0);
        }

        static Attachment texture(int objectId, int level) {
            return new Attachment(Kind.TEXTURE, objectId, level);
        }

        static Attachment renderbuffer(int objectId) {
            return new Attachment(Kind.RENDERBUFFER, objectId, 0);
        }
    }

    private DepthAttachmentRestore() {
    }
}
