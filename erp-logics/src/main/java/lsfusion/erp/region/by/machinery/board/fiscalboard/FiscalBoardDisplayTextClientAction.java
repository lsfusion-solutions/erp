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
    boolean useJssc;
    Integer timeout;

    public FiscalBoardDisplayTextClientAction(String line1, String line2, Integer baudRateBoard, Integer comPortBoard, boolean uppercase, boolean useJssc, Integer timeout) {
        this.line1 = uppercase(line1, uppercase);
        this.line2 = uppercase(line2, uppercase);
        this.baudRateBoard = baudRateBoard;
        this.comPortBoard = comPortBoard;
        this.useJssc = useJssc;
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
            String comPort = "COM" + comPortBoard;
            byte[] line1Bytes = line1.getBytes(Charset.forName("cp866"));
            byte[] line2Bytes = line2.getBytes(Charset.forName("cp866"));
            if(useJssc) {
                writeJssc(comPort, baudRateBoard, line1Bytes, line2Bytes);
            } else {
                writeJSerialComm(comPort, baudRateBoard, line1Bytes, line2Bytes);
            }


        } catch (SerialPortException e) {
            cashRegisterlogger.info(String.format("Board writeToPort failed: %s ms", (System.currentTimeMillis() - time)), e);
            return false;
        }
        cashRegisterlogger.info(String.format("Board writeToPort finished: %s ms", (System.currentTimeMillis() - time)));
        return true;
    }

    private void writeJSerialComm(String comPort, Integer baudRate, byte[] line1, byte[] line2) {
        com.fazecast.jSerialComm.SerialPort serialPort = com.fazecast.jSerialComm.SerialPort.getCommPort(comPort);
        try {
            serialPort.openPort();
            serialPort.setBaudRate(baudRate);
            serialPort.writeBytes(line1, line1.length);
            serialPort.writeBytes(line2, line2.length);
        } finally {
            serialPort.closePort();
        }
    }

    private void writeJssc(String comPort, Integer baudRate, byte[] line1, byte[] line2) throws SerialPortException {
        SerialPort serialPort = new SerialPort(comPort);
        serialPort.openPort();
        serialPort.setParams(baudRate, 8, 1, 0);
        serialPort.writeBytes(line1);
        serialPort.writeBytes(line2);
        serialPort.closePort();
    }
}