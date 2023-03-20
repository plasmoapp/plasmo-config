package su.plo.config.entry;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@ToString
public class ConfigEntry<E> implements SerializableConfigEntry {

    protected final List<Consumer<E>> changeListeners = new CopyOnWriteArrayList<>();

    protected transient E defaultValue;
    protected E value = null;

    @Setter
    @Getter
    protected boolean disabled;

    public ConfigEntry(E defaultValue) {
        this.defaultValue = defaultValue;
        this.reset();
    }

    public void reset() {
        this.value = this.defaultValue;
        changeListeners.forEach(listener -> listener.accept(value));
    }

    public boolean isDefault() {
        return Objects.equals(this.defaultValue, this.value);
    }

    public void set(E value) {
        if (!this.value.equals(value)) {
            this.value = value;
            changeListeners.forEach(listener -> listener.accept(value));
        }
    }

    public void setDefault(E value) {
        this.defaultValue = value;
        if (this.value == null) reset();
    }

    public E getDefault() {
        return this.defaultValue;
    }

    public E value() {
        return this.value;
    }

    public void addChangeListener(Consumer<E> listener) {
        changeListeners.add(listener);
    }

    public void removeChangeListener(Consumer<E> listener) {
        changeListeners.remove(listener);
    }

    public void clearChangeListeners() {
        changeListeners.clear();
    }

    public void deserialize(Object object) {
        try {
            this.value = (E) object;
        } catch (ClassCastException ignored) {
        }
    }

    public Object serialize() {
        return this.value;
    }
}
