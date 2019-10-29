package lsfusion.erp.daemon;

import jssc.SerialPort;
import jssc.SerialPortException;
import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;
import lsfusion.server.physics.dev.integration.external.to.equ.com.SerialPortHandler;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScannerDaemonClientAction implements ClientAction {
    private Integer comPort;
    private boolean singleRead;

    public ScannerDaemonClientAction(Integer comPort, boolean singleRead) {
        this.comPort = comPort;
        this.singleRead = singleRead;
    }

    @Override
    public Object dispatch(ClientActionDispatcher dispatcher) {
        ScannerDaemonListener scannerDaemonListener = new ScannerDaemonListener(dispatcher, comPort, singleRead);
        scannerDaemonListener.setEventBus(dispatcher.getEventBus());
        return scannerDaemonListener.start();
    }

    private class ScannerDaemonListener extends AbstractDaemonListener implements Serializable {
        public static final String SCANNER_SID = "SCANNER";

        ClientActionDispatcher dispatcher;
        private int com;
        private final boolean singleRead;
        private Map<String, String> barcodeMap = new HashMap<>();


        public ScannerDaemonListener(ClientActionDispatcher dispatcher, int com, boolean singleRead) {
            this.dispatcher = dispatcher;
            this.com = com;
            this.singleRead = singleRead;
        }

        @Override
        public String start() {
            List<String> errorList = new ArrayList<>();
            while (com > 100) {
                String error = connect(com % 100);
                if(error != null) {
                    errorList.add(error);
                }
                com = com / 100;
            }
            String error = connect(com);
            if(error != null) {
                errorList.add(error);
            }
            return StringUtils.join(errorList.iterator(), "\n");
        }

        private String connect(int currentCom) {
            String result = null;
            try {
                String OS = System.getProperty("os.name").toLowerCase();
                boolean isUnix = (OS.contains("nix") || OS.contains("nux") || OS.contains("aix"));
                String portName = (isUnix ? "/dev/tty" : "COM") + currentCom;

                SerialPortHandler.addSerialPort(dispatcher, portName, 9600, (event, serialPort) -> {
                    if (event.isRXCHAR()) {
                        String barcode = barcodeMap.get(event.getPortName());
                        if (singleRead) {
                            try {
                                byte[] portBytes;

                                Thread.sleep(50);
                                portBytes = serialPort.readBytes();

                                if (portBytes != null) {
                                    barcode = "";
                                    for (byte portByte : portBytes) {
                                        if (((char) portByte) != '\n' && ((char) portByte) != '\r')
                                            barcode += (char) portByte;
                                    }
                                    if (!barcode.isEmpty())
                                        eventBus.fireValueChanged(SCANNER_SID, barcode.trim());
                                }
                            } catch (SerialPortException | InterruptedException ex) {
                                throw new RuntimeException(ex);
                            }
                        } else if (event.isRXCHAR() && event.getEventValue() > 0) {
                            try {
                                char ch = (char) serialPort.readBytes(1)[0];
                                if (ch >= '0' && ch <= '9')
                                    barcode += ch;
                                if (event.getEventValue() == 1) {
                                    eventBus.fireValueChanged(SCANNER_SID, barcode);
                                    barcode = "";
                                }
                            } catch (SerialPortException ex) {
                                throw new RuntimeException(ex);
                            }
                        }
                        barcodeMap.put(event.getPortName(), barcode);
                    }
                }, SerialPort.MASK_RXCHAR | SerialPort.MASK_CTS | SerialPort.MASK_DSR);
                barcodeMap.put(portName, "");
            } catch (Exception e) {
                result = e.getMessage() != null ? e.getMessage() : "Internal Server Error";
            }
            return result;
        }
    }
}