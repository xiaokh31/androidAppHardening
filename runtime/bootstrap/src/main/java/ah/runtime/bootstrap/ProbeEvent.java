package ah.runtime.bootstrap;

import java.util.Objects;

/** Immutable, process-local evidence emitted by the ClassLoader proof of concept. */
public final class ProbeEvent {
    private final long sequence;
    private final String type;
    private final String componentClassName;
    private final ClassLoader classLoader;

    ProbeEvent(
            long sequence,
            String type,
            String componentClassName,
            ClassLoader classLoader) {
        this.sequence = sequence;
        this.type = Objects.requireNonNull(type, "type");
        this.componentClassName = componentClassName;
        this.classLoader = classLoader;
    }

    public long sequence() {
        return sequence;
    }

    public String type() {
        return type;
    }

    public String componentClassName() {
        return componentClassName;
    }

    public ClassLoader classLoader() {
        return classLoader;
    }

    public String classLoaderName() {
        return classLoader == null ? "<none>" : classLoader.getClass().getName();
    }
}
