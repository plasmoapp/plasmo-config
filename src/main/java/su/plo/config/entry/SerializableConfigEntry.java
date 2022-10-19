package su.plo.config.entry;

public interface SerializableConfigEntry {

    void deserialize(Object object);

    Object serialize();
}
