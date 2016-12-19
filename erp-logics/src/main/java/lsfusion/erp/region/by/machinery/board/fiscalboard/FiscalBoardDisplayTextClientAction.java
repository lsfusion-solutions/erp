package lsfusion.erp.region.by.machinery.board.fiscalboard;

import jssc.SerialPort;
import jssc.SerialPortException;
import lsfusion.interop.DaemonThreadFactory;
import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class FiscalBoardDisplayTextClientAction implements ClientAction {

    private static ExecutorService daemonTasksExecutor = Executors.newFixedThreadPool(5, new DaemonThreadFactory("board-displayText"));
    String ip;
    private String line1;
    private String line2;
    private Integer baudRateBoard;
    private Integer comPortBoard;

    FiscalBoardDisplayTextClientAction(String line1, String line2, Integer baudRateBoard, Integer comPortBoard) {
        this.line1 = line1;
        this.line2 = line2;
        this.baudRateBoard = baudRateBoard;
        this.comPortBoard = comPortBoard;
    }

    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        if (baudRateBoard != null && comPortBoard != null) {
            daemonTasksExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        SerialPort serialPort = new SerialPort("COM" + comPortBoard);
                        serialPort.openPort();
                        serialPort.setParams(baudRateBoard, 8, 1, 0);
                        serialPort.writeBytes(line1.getBytes(Charset.forName("cp866")));
                        serialPort.writeBytes(line2.getBytes(Charset.forName("cp866")));
                        serialPort.closePort();
                    } catch (SerialPortException ignored) {
                    }
                }
            });
        }
        return null;
    }
}
