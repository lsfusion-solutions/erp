package lsfusion.erp.region.by.machinery.board.fiscalboard;

import jssc.SerialPort;
import jssc.SerialPortException;
import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;
import java.nio.charset.Charset;


public class FiscalBoardDisplayTextClientAction implements ClientAction {

    ReceiptItem item;
    Integer baudRateBoard;
    Integer comPortBoard;

    public FiscalBoardDisplayTextClientAction(ReceiptItem item, Integer baudRateBoard, Integer comPortBoard) {
        this.item = item;
        this.baudRateBoard = baudRateBoard;
        this.comPortBoard = comPortBoard;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        String[] linesBoard = generateText(item, 20);

        if (baudRateBoard != null && comPortBoard != null) {
            try {
                SerialPort serialPort = new SerialPort("COM" + comPortBoard);
                serialPort.openPort();
                serialPort.setParams(baudRateBoard, 8, 1, 0);
                serialPort.writeBytes((linesBoard[0]).getBytes(Charset.forName("cp866")));
                serialPort.writeBytes((linesBoard[1]).getBytes(Charset.forName("cp866")));
                serialPort.closePort();
            } catch (SerialPortException e) {
                return false;
            }
        }
        return null;
    }

    private static String[] generateText(ReceiptItem item, int len) {
        String firstLine = " " + toStr(item.quantity) + "x" + String.valueOf(item.price);
        int length = len - Math.min(len, firstLine.length());
        String name = item.name.substring(0, Math.min(length, item.name.length()));
        while ((name + firstLine).length() < 20)
            name = name + " ";
        firstLine = name + firstLine;
        String secondLine = String.valueOf(item.sumPos);
        while (secondLine.length() < (len - 5))
            secondLine = " " + secondLine;
        secondLine = "ИТОГ:" + secondLine;
        return new String[]{firstLine, secondLine};
    }

    private static String toStr(double value) {
        boolean isInt = (value - (int) value) == 0;
        return isInt ? String.valueOf((int) value) : String.valueOf(value);
    }
}
