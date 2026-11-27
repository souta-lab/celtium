package org.soutalab.celtium.forge.ui;

import net.minecraft.client.resources.I18n;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Lightweight native option model for {@link CeltiumVideoSettingsScreen}.
 *
 * <p>Supports exactly the three control types used by Celtium's video settings:
 * {@link SliderOption}, {@link ToggleOption}, and {@link CycleOption}.
 */
public abstract class OptionEntry<T> {

    public enum ConfigTarget {
        VANILLA,
        CELTIUM
    }

    private final String nameKey;
    private final String tooltipKey;
    private final ConfigTarget target;
    private final OptionImpact impact;
    private final Set<OptionFlag> flags;
    private final Supplier<T> getter;
    private final Consumer<T> setter;

    private T originalValue;
    private T pendingValue;

    protected OptionEntry(String nameKey, String tooltipKey, ConfigTarget target,
                          OptionImpact impact, Set<OptionFlag> flags,
                          Supplier<T> getter, Consumer<T> setter) {
        this.nameKey = Objects.requireNonNull(nameKey, "nameKey");
        this.tooltipKey = Objects.requireNonNull(tooltipKey, "tooltipKey");
        this.target = Objects.requireNonNull(target, "target");
        this.impact = impact;
        this.flags = flags == null || flags.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(flags));
        this.getter = Objects.requireNonNull(getter, "getter");
        this.setter = Objects.requireNonNull(setter, "setter");
        refreshFromStorage();
    }

    public final void refreshFromStorage() {
        this.originalValue = this.getter.get();
        this.pendingValue = this.originalValue;
    }

    public String getName() {
        return I18n.format(this.nameKey);
    }

    public String getNameKey() {
        return this.nameKey;
    }

    public String getTooltip() {
        return I18n.format(this.tooltipKey);
    }

    public ConfigTarget getTarget() {
        return this.target;
    }

    public OptionImpact getImpact() {
        return this.impact;
    }

    public Set<OptionFlag> getFlags() {
        return this.flags;
    }

    public T getValue() {
        return this.pendingValue;
    }

    public void setValue(T value) {
        this.pendingValue = value;
    }

    public boolean hasChanged() {
        return !Objects.equals(this.pendingValue, this.originalValue);
    }

    public void reset() {
        this.pendingValue = this.originalValue;
    }

    public void apply() {
        if (!hasChanged()) {
            return;
        }
        this.setter.accept(this.pendingValue);
        this.originalValue = this.getter.get();
        this.pendingValue = this.originalValue;
    }

    public abstract String getDisplayValue();

    /**
     * Integer range slider control.
     */
    public static final class SliderOption extends OptionEntry<Integer> {
        private final int min;
        private final int max;
        private final int step;
        private final IntFunction<String> formatter;

        public SliderOption(String nameKey, String tooltipKey, ConfigTarget target,
                            OptionImpact impact, Set<OptionFlag> flags,
                            int min, int max, int step, IntFunction<String> formatter,
                            Supplier<Integer> getter, Consumer<Integer> setter) {
            super(nameKey, tooltipKey, target, impact, flags, getter, setter);
            if (max < min) {
                throw new IllegalArgumentException("max (" + max + ") < min (" + min + ")");
            }
            if (step <= 0) {
                throw new IllegalArgumentException("step must be positive: " + step);
            }
            this.min = min;
            this.max = max;
            this.step = step;
            this.formatter = Objects.requireNonNull(formatter, "formatter");
            setValue(clampAndSnap(getValue()));
        }

        public int getMin() {
            return this.min;
        }

        public int getMax() {
            return this.max;
        }

        public int getStep() {
            return this.step;
        }

        public float getFraction() {
            if (this.max <= this.min) {
                return 0.0f;
            }
            int v = clampAndSnap(getValue());
            return (float) (v - this.min) / (float) (this.max - this.min);
        }

        public void setFromFraction(float fraction) {
            float clamped = Math.max(0.0f, Math.min(1.0f, fraction));
            int raw = Math.round(this.min + clamped * (this.max - this.min));
            setValue(clampAndSnap(raw));
        }

        private int clampAndSnap(int value) {
            int clamped = Math.max(this.min, Math.min(this.max, value));
            int offset = clamped - this.min;
            int snapped = this.min + Math.round((float) offset / this.step) * this.step;
            return Math.max(this.min, Math.min(this.max, snapped));
        }

        @Override
        public String getDisplayValue() {
            return this.formatter.apply(getValue());
        }
    }

    /**
     * Boolean checkbox / toggle control.
     */
    public static final class ToggleOption extends OptionEntry<Boolean> {
        public ToggleOption(String nameKey, String tooltipKey, ConfigTarget target,
                            OptionImpact impact, Set<OptionFlag> flags,
                            Supplier<Boolean> getter, Consumer<Boolean> setter) {
            super(nameKey, tooltipKey, target, impact, flags, getter, setter);
        }

        public void toggle() {
            setValue(!Boolean.TRUE.equals(getValue()));
        }

        @Override
        public String getDisplayValue() {
            return Boolean.TRUE.equals(getValue())
                    ? I18n.format("options.on")
                    : I18n.format("options.off");
        }
    }

    /**
     * Discrete cycling control over a fixed list of values.
     */
    public static final class CycleOption<T> extends OptionEntry<T> {
        private final List<T> values;
        private final Function<T, String> labelProvider;

        public CycleOption(String nameKey, String tooltipKey, ConfigTarget target,
                           OptionImpact impact, Set<OptionFlag> flags,
                           List<T> values, Function<T, String> labelProvider,
                           Supplier<T> getter, Consumer<T> setter) {
            super(nameKey, tooltipKey, target, impact, flags, getter, setter);
            if (values == null || values.isEmpty()) {
                throw new IllegalArgumentException("values must not be empty");
            }
            this.values = List.copyOf(values);
            this.labelProvider = Objects.requireNonNull(labelProvider, "labelProvider");
        }

        public void cycle(int delta) {
            int current = this.values.indexOf(getValue());
            if (current < 0) {
                current = 0;
            }
            int next = Math.floorMod(current + delta, this.values.size());
            setValue(this.values.get(next));
        }

        @Override
        public String getDisplayValue() {
            return this.labelProvider.apply(getValue());
        }
    }

    /**
     * Named visual group of options within a page.
     */
    public static final class OptionGroup {
        private final String titleKey;
        private final List<OptionEntry<?>> options;

        public OptionGroup(String titleKey, List<OptionEntry<?>> options) {
            this.titleKey = titleKey;
            this.options = List.copyOf(options);
        }

        public String getTitle() {
            return this.titleKey == null ? "" : I18n.format(this.titleKey);
        }

        public List<OptionEntry<?>> getOptions() {
            return this.options;
        }
    }

    /**
     * Top-level tab page in the video settings screen.
     */
    public static final class OptionPage {
        private final String id;
        private final String titleKey;
        private final List<OptionGroup> groups;
        private final String placeholderNoticeKey;

        public OptionPage(String id, String titleKey, List<OptionGroup> groups) {
            this(id, titleKey, groups, null);
        }

        public OptionPage(String id, String titleKey, List<OptionGroup> groups, String placeholderNoticeKey) {
            this.id = Objects.requireNonNull(id, "id");
            this.titleKey = Objects.requireNonNull(titleKey, "titleKey");
            this.groups = List.copyOf(groups);
            this.placeholderNoticeKey = placeholderNoticeKey;
        }

        public String getId() {
            return this.id;
        }

        public String getTitle() {
            return I18n.format(this.titleKey);
        }

        public List<OptionGroup> getGroups() {
            return this.groups;
        }

        public boolean isPlaceholder() {
            return this.placeholderNoticeKey != null;
        }

        public String getPlaceholderNotice() {
            return this.placeholderNoticeKey == null ? "" : I18n.format(this.placeholderNoticeKey);
        }
    }
}
