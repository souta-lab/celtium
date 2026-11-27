package org.soutalab.celtium.core.shader;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Which descriptors a terrain stage program binds, stated declaratively at pack-compile time: the
 * pack uniform UBO, its sampler bindings and its storage-image bindings, each with the stages that
 * may read it. The HAL turns a plan into descriptor set layouts and pipeline layouts; the plan
 * itself carries no Vulkan handle.
 */
public record DescriptorBindingPlan(List<Binding> bindings) {

    public DescriptorBindingPlan {
        bindings = List.copyOf(Objects.requireNonNull(bindings, "bindings"));
    }

    public static DescriptorBindingPlan empty() {
        return new DescriptorBindingPlan(List.of());
    }

    public Optional<Binding> find(int binding) {
        return bindings.stream().filter(candidate -> candidate.binding() == binding).findFirst();
    }

    /** One descriptor binding slot: its index, what kind of descriptor lives there, and its visibility. */
    public record Binding(int binding, Kind kind, EnumSet<ShaderStage> stages, int descriptorCount) {

        public Binding {
            if (binding < 0) {
                throw new IllegalArgumentException("binding must not be negative");
            }
            Objects.requireNonNull(kind, "kind");
            stages = EnumSet.copyOf(Objects.requireNonNull(stages, "stages"));
            if (stages.isEmpty()) {
                throw new IllegalArgumentException("a binding must be visible to at least one stage");
            }
            if (descriptorCount <= 0) {
                throw new IllegalArgumentException("descriptorCount must be positive");
            }
        }

        @Override
        public EnumSet<ShaderStage> stages() {
            return EnumSet.copyOf(stages);
        }
    }

    public enum Kind {
        UNIFORM_BUFFER,
        SAMPLER,
        SAMPLED_IMAGE,
        COMBINED_IMAGE_SAMPLER,
        STORAGE_IMAGE,
        STORAGE_BUFFER
    }
}
