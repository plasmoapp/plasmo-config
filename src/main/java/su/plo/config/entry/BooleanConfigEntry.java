package su.plo.config.entry;

public final class BooleanConfigEntry extends ConfigEntry<Boolean> {

    public BooleanConfigEntry(Boolean defaultValue) {
        super(defaultValue);
    }

    public void invert() {
        set(!value);
    }
}
