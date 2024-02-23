package lsfusion.erp.region.by.machinery.board.fiscalboard;

import jssc.SerialPort;
import jssc.SerialPortException;
import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.nio.charset.Charset;

import static lsfusion.erp.ERPLoggers.cashRegisterlogger;


public class FiscalBoardDisplayTextClientAction implements ClientAction {

    String line1;
    String line2;
    Integer baudRateBoard;
    Integer comPortBoard;
    Integer timeout;

    public FiscalBoardDisplayTextClientAction(String line1, String line2, Integer baudRateBoard, Integer comPortBoard, boolean uppercase, Integer timeout) {
        this.line1 = uppercase(line1, uppercase);
        this.line2 = uppercase(line2, uppercase);
        this.baudRateBoard = baudRateBoard;
        this.comPortBoard = comPortBoard;
        this.timeout = timeout;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) {

        if (baudRateBoard != null && comPortBoard != null) {

            if (timeout != null) {
                new Thread(() -> {
                    try {
                        Thread.sleep(timeout);
                        writeToPort();
                    } catch (InterruptedException ignored) {
                    }

                }).start();
            } else {
                return writeToPort();
            }
        }
        return null;
    }

    private String uppercase(String line, boolean uppercase) {
        return uppercase ? line.toUpperCase() : line;
    }

    private boolean writeToPort() {
        long time = System.currentTimeMillis();
        try {
            cashRegisterlogger.info("Board writeToPort started");
            SerialPort serialPort = new SerialPort("COM" + comPortBoard);
            serialPort.openPort();
            serialPort.setParams(baudRateBoard, 8, 1, 0);
            serialPort.writeBytes(line1.getBytes(Charset.forName("cp866")));
            serialPort.writeBytes(line2.getBytes(Charset.forName("cp866")));
            serialPort.closePort();

        } catch (SerialPortException e) {
            cashRegisterlogger.info(String.format("Board writeToPort failed: %s ms", (System.currentTimeMillis() - time)), e);
            return false;
        }
        cashRegisterlogger.info(String.format("Board writeToPort finished: %s ms", (System.currentTimeMillis() - time)));
        return true;
    }
}