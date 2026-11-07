package com.maitreyoshi.mcavie.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class Callbacks<T> {
    private final List<T> listeners = new ArrayList<>();

    public void register(T listener) {
        listeners.add(listener);
    }

    public void forEach(Consumer<T> action) {
        for (T listener : listeners) {
            action.accept(listener);
        }
    }
}
