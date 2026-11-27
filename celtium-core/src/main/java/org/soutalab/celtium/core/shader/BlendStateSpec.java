package org.soutalab.celtium.core.shader;

import java.util.List;
import java.util.Objects;

/**
 * A program's full blend state: one {@link AttachmentBlendSpec} per colour attachment of its
 * {@link AttachmentFormatKey}, in attachment order (design section 2.3: "overall + per-attachment
 * blend spec" - the MRT passes of a pack genuinely differ per attachment, so a single shared spec
 * is not expressible here on purpose).
 */
public record BlendStateSpec(List<AttachmentBlendSpec> attachments) {

    public BlendStateSpec {
        attachments = List.copyOf(Objects.requireNonNull(attachments, "attachments"));
        if (attachments.isEmpty()) {
            throw new IllegalArgumentException("a blend state covers at least one attachment");
        }
    }

    /** The same spec for every one of {@code attachmentCount} attachments. */
    public static BlendStateSpec uniform(AttachmentBlendSpec spec, int attachmentCount) {
        Objects.requireNonNull(spec, "spec");
        if (attachmentCount <= 0) {
            throw new IllegalArgumentException("attachmentCount must be positive");
        }
        AttachmentBlendSpec[] all = new AttachmentBlendSpec[attachmentCount];
        java.util.Arrays.fill(all, spec);
        return new BlendStateSpec(List.of(all));
    }

    public static BlendStateSpec opaque(int attachmentCount) {
        return uniform(AttachmentBlendSpec.opaque(), attachmentCount);
    }

    public AttachmentBlendSpec forAttachment(int index) {
        return attachments.get(index);
    }
}
