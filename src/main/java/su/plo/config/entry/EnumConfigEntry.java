package su.plo.config.entry;

import lombok.ToString;

@ToString
public class EnumConfigEntry<E extends Enum<E>> extends ConfigEntry<E> {

    protected final Class<E> clazz;

    public EnumConfigEntry(Class<E> clazz, E defaultValue) {
        super(defaultValue);

        this.clazz = clazz;
    }

    @Override
    public Object serialize() {
        return value.toString();
    }

    @Override
    public void deserialize(Object object) {
        try {
            if (object instanceof String) this.value = Enum.valueOf(clazz, (String) object);
        } catch (IllegalArgumentException ignored) { // ignore bad enum
        }
    }
}
