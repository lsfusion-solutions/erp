package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import lsfusion.interop.DaemonThreadFactory;
import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class FiscalVMKDisplayTextClientAction implements ClientAction {

    private static ExecutorService daemonTasksExecutor = Executors.newFixedThreadPool(5, new DaemonThreadFactory("vmk-displayText"));
    String ip;
    int comPort;
    int baudRate;
    private ReceiptItem receiptItem;

    FiscalVMKDisplayTextClientAction(String ip, Integer comPort, Integer baudRate, ReceiptItem receiptItem) {
        this.ip = ip;
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.receiptItem = receiptItem;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {
        daemonTasksExecutor.submit(new Runnable() {
            @Override
            public void run() {
                FiscalVMK.init();
                try {
                    if (FiscalVMK.safeOpenPort(ip, comPort, baudRate, 5000)) {
                        FiscalVMK.displayText(receiptItem);
                        FiscalVMK.closePort();
                    }
                } catch (RuntimeException e) {
                    FiscalVMK.logger.error("Display Text Error: ", e);
                }
            }
        });
        return null;
    }
}
