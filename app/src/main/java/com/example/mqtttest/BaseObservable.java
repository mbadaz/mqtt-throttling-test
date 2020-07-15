package com.example.mqtttest;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class BaseObservable<LISTENER_CLASS> {
    private final Object MONITOR = new Object();
    private final Set<LISTENER_CLASS> listeners = new HashSet<>();

    public void registerListener(LISTENER_CLASS listener) {
        synchronized (MONITOR) {
            boolean hadNoListeners = listeners.size() == 0;
            listeners.add(listener);
            if (hadNoListeners && listeners.size() == 1) {
                onFirstListenerRegistered();
            }
        }
    }

    public void unregisterListener(LISTENER_CLASS listener) {
        synchronized (MONITOR) {
            boolean hadOneListener = listeners.size() == 1;
            listeners.remove(listener);
            if (hadOneListener && listeners.size() == 0) {
                onLastListenerUnregistered();
            }
        }
    }

    protected Set<LISTENER_CLASS> getListeners() {
        return Collections.unmodifiableSet(new HashSet<>(listeners));
    }

    protected void onLastListenerUnregistered() {

    }

    protected void onFirstListenerRegistered() {
    }
}
