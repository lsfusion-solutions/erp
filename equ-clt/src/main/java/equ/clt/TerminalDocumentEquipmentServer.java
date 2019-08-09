package equ.clt;

import equ.api.EquipmentServerInterface;
import equ.api.terminal.TerminalDocumentBatch;
import equ.api.terminal.TerminalHandler;
import equ.api.terminal.TerminalInfo;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TerminalDocumentEquipmentServer {
    private final static Logger sendTerminalDocumentLogger = Logger.getLogger("TerminalDocumentLogger");

    static void sendTerminalDocumentInfo(EquipmentServerInterface remote, String sidEquipmentServer) throws SQLException, IOException {
        sendTerminalDocumentLogger.info("Send TerminalDocumentInfo");
        List<TerminalInfo> terminalInfoList = remote.readTerminalInfo(sidEquipmentServer);

        Map<String, List<TerminalInfo>> handlerModelTerminalMap = new HashMap<>();
        for (TerminalInfo terminal : terminalInfoList) {
            if (!handlerModelTerminalMap.containsKey(terminal.handlerModel))
                handlerModelTerminalMap.put(terminal.handlerModel, new ArrayList<>());
            handlerModelTerminalMap.get(terminal.handlerModel).add(terminal);
        }

        for (Map.Entry<String, List<TerminalInfo>> entry : handlerModelTerminalMap.entrySet()) {
            String handlerModel = entry.getKey();
            if (handlerModel != null) {

                try {
                    TerminalHandler clsHandler = (TerminalHandler) EquipmentServer.getHandler(handlerModel, remote);

                    TerminalDocumentBatch documentBatch = clsHandler.readTerminalDocumentInfo(terminalInfoList);
                    if (documentBatch == null || documentBatch.documentDetailList == null || documentBatch.documentDetailList.isEmpty()) {
                        sendTerminalDocumentLogger.info("TerminalDocumentInfo is empty");
                    } else {
                        sendTerminalDocumentLogger.info("Sending TerminalDocumentInfo");
                        String result = remote.sendTerminalInfo(documentBatch.documentDetailList);
                        if (result != null) {
                            sendTerminalDocumentLogger.error("Equipment server error: " + result);
                            EquipmentServer.reportEquipmentServerError(remote, sidEquipmentServer, new Throwable(result).fillInStackTrace());
                        } else {
                            sendTerminalDocumentLogger.info("Finish Reading starts");
                            clsHandler.finishReadingTerminalDocumentInfo(documentBatch);
                        }
                    }
                } catch (Throwable e) {
                    sendTerminalDocumentLogger.error("Equipment server error: ", e);
                    EquipmentServer.reportEquipmentServerError(remote, sidEquipmentServer, e);
                    return;
                }
            }
        }
    }

}