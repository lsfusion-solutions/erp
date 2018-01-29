package lsfusion.erp.region.by.machinery.board.fiscalboard;

import jssc.SerialPort;
import jssc.SerialPortException;
import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;
import java.nio.charset.Charset;


public class FiscalBoardDisplayTextClientAction implements ClientAction {

    String line1;
    String line2;
    Integer baudRateBoard;
    Integer comPortBoard;

    public FiscalBoardDisplayTextClientAction(String line1, String line2, Integer baudRateBoard, Integer comPortBoard, boolean uppercase) {
        this.line1 = uppercase(line1, uppercase);
        this.line2 = uppercase(line2, uppercase);
        this.baudRateBoard = baudRateBoard;
        this.comPortBoard = comPortBoard;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        if (baudRateBoard != null && comPortBoard != null) {
            try {
                SerialPort serialPort = new SerialPort("COM" + comPortBoard);
                serialPort.openPort();
                serialPort.setParams(baudRateBoard, 8, 1, 0);
                serialPort.writeBytes(line1.getBytes(Charset.forName("cp866")));
                serialPort.writeBytes(line2.getBytes(Charset.forName("cp866")));
                serialPort.closePort();
            } catch (SerialPortException e) {
                return false;
            }
        }
        return null;
    }

    private String uppercase(String line, boolean uppercase) {
        return uppercase ? line.toUpperCase() : line;
    }
}
