package su.plo.config.entry;

import lombok.Getter;

public final class DoubleConfigEntry extends ConfigEntry<Double> {

    @Getter
    private double min;
    @Getter
    private double max;

    public DoubleConfigEntry(double defaultValue, double min, double max) {
        super(defaultValue);
        this.min = min;
        this.max = max;
    }

    @Override
    public void set(Double value) {
        if (min != max) {
            super.set(Math.max(Math.min(value, max), min));
        } else {
            super.set(value);
        }
    }

    public void setDefault(double value, double min, double max) {
        super.setDefault(value);
        this.min = min;
        this.max = max;
    }
}
