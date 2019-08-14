package lsfusion.erp.daemon;

import lsfusion.interop.form.event.EventBus;

public abstract class AbstractDaemonListener {
    protected EventBus eventBus;

    public abstract String start();

    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }
}