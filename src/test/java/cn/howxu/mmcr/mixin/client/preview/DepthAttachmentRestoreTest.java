package cn.howxu.mmcr.mixin.client.preview;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies saved GL depth attachment kinds are restored without a live OpenGL context.
 *
 * @author howxu <dev@howxu.cn>
 */
class DepthAttachmentRestoreTest {
    @Test
    void restores_the_saved_depth_texture_instead_of_unconditionally_detaching() {
        DepthAttachmentRestore.Attachment attachment = DepthAttachmentRestore.Attachment.texture(42, 3);

        assertThat(attachment.objectId()).isEqualTo(42);
        assertThat(attachment.level()).isEqualTo(3);
        assertThat(attachment.kind()).isEqualTo(DepthAttachmentRestore.Kind.TEXTURE);
    }

    @Test
    void preserves_an_empty_depth_attachment_as_empty() {
        assertThat(DepthAttachmentRestore.Attachment.none().objectId()).isZero();
        assertThat(DepthAttachmentRestore.Attachment.none().kind()).isEqualTo(DepthAttachmentRestore.Kind.NONE);
    }
}
