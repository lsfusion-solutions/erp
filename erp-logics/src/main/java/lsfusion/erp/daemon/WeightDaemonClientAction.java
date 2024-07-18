package lsfusion.erp.daemon;

import jssc.SerialPort;
import jssc.SerialPortException;
import lsfusion.interop.action.ClientActionDispatcher;
import lsfusion.interop.action.ExecuteClientAction;
import lsfusion.server.physics.dev.integration.external.to.equ.com.SerialPortHandler;
import lsfusion.server.physics.dev.integration.external.to.equ.com.SerialPortHandler2;

import java.io.Serializable;

import static com.fazecast.jSerialComm.SerialPort.LISTENING_EVENT_DATA_AVAILABLE;

public class WeightDaemonClientAction extends ExecuteClientAction {
    private String comPort;
    private boolean useJSerialComm;

    public WeightDaemonClientAction(Integer comPort, boolean useJSerialComm) {
        this.comPort = "COM" + comPort;
        this.useJSerialComm = useJSerialComm;
    }

    @Override
    public void execute(ClientActionDispatcher dispatcher) {
        WeightDaemonListener weightDaemonListener = new WeightDaemonListener(dispatcher, comPort);
        weightDaemonListener.setEventBus(dispatcher.getEventBus());
        weightDaemonListener.start();
    }

    private class WeightDaemonListener extends AbstractDaemonListener implements Serializable {
        public static final String SCALES_SID = "SCALES";

        ClientActionDispatcher dispatcher;
        private String com;

        public WeightDaemonListener(ClientActionDispatcher dispatcher, String com) {
            this.dispatcher = dispatcher;
            this.com = com;
        }

        @Override
        public String start() {
            try {

                if(useJSerialComm) {
                    SerialPortHandler2.addSerialPort(dispatcher, com, 4800, (event, serialPort) -> {
                        if (event.getEventType() == LISTENING_EVENT_DATA_AVAILABLE) {
                            try {
                                Thread.sleep(50);
                                byte[] portBytes = new byte[serialPort.bytesAvailable()];
                                serialPort.readBytes(portBytes, portBytes.length);
                                if (portBytes[portBytes.length - 5] == (byte) 0x55 && portBytes[portBytes.length - 4] == (byte) 0xAA) {
                                    boolean negate = portBytes[portBytes.length - 1] != 0x00;
                                    int weightByte1 = portBytes[portBytes.length - 2];
                                    if (weightByte1 < 0)
                                        weightByte1 += 256;
                                    int weightByte2 = portBytes[portBytes.length - 3];
                                    if (weightByte2 < 0)
                                        weightByte2 += 256;
                                    double weight = ((double) ((negate ? -1 : 1) * (weightByte1 * 256 + weightByte2))) / 1000;
                                    if (weight >= 0.01) //игнорируем веса до 10г
                                        eventBus.fireValueChanged(SCALES_SID, weight);
                                }
                            } catch (InterruptedException ex) {
                                throw new RuntimeException(ex);
                            }
                        }
                    }, LISTENING_EVENT_DATA_AVAILABLE);
                } else {
                    SerialPortHandler.addSerialPort(dispatcher, com, 4800, (event, serialPort) -> {
                        if (event.isRXCHAR()) {
                            try {
                                Thread.sleep(50);
                                byte[] portBytes = serialPort.readBytes();
                                if (portBytes != null && portBytes[portBytes.length - 5] == (byte) 0x55 && portBytes[portBytes.length - 4] == (byte) 0xAA) {
                                    boolean negate = portBytes[portBytes.length - 1] != 0x00;
                                    int weightByte1 = portBytes[portBytes.length - 2];
                                    if(weightByte1 < 0)
                                        weightByte1 += 256;
                                    int weightByte2 = portBytes[portBytes.length - 3];
                                    if(weightByte2 < 0)
                                        weightByte2 += 256;
                                    double weight = ((double)((negate ? -1 : 1) * (weightByte1 * 256 + weightByte2))) / 1000;
                                    if(weight >= 0.01) //игнорируем веса до 10г
                                        eventBus.fireValueChanged(SCALES_SID, weight);
                                }
                            } catch (SerialPortException | InterruptedException ex) {
                                throw new RuntimeException(ex);
                            }
                        }
                    }, SerialPort.MASK_RXCHAR | SerialPort.MASK_CTS | SerialPort.MASK_DSR);
                }
            } catch (SerialPortException ex) {
                throw new RuntimeException(ex);
            }
            return null;
        }
    }
}