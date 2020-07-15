package com.example.mqtttest;

import java.util.concurrent.atomic.AtomicBoolean;

public class BaseBusyObservable<LISTENER_CLASS> extends BaseObservable<LISTENER_CLASS> {
    private final AtomicBoolean isBusy = new AtomicBoolean(false);

    public boolean isBusy() {
        return isBusy.get();
    }

    protected final void assertNotBusyAndBecomeBusy() {
        if (!isBusy.compareAndSet(false, true)) {
            throw new IllegalStateException("assertion violation: must'nt be busy");
        }
    }

    protected final boolean isFreeAndBecomeBusy() {
        return isBusy.compareAndSet(false, true);
    }

    protected final void becomeNotBusy() {
        isBusy.set(false);
    }
}
