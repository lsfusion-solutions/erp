package equ.clt.handler.digi;

import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItemInfo;
import equ.api.scales.TransactionScalesInfo;
import org.json.JSONObject;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static equ.clt.handler.HandlerUtils.trim;

public class DigiSM5300Handler extends DigiHandler {

    public DigiSM5300Handler(FileSystemXmlApplicationContext springContext) {
        super(springContext);
    }

    protected String getLogPrefix() {
        return "Digi SM5300: ";
    }

    @Override
    protected SendTransactionTask getTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales) {
        return new DigiSendTransactionTask(transaction, scales) {

            @Override
            protected Integer getMaxCompositionLinesCount() {
                return null; //has no limits
            }

            /*@Override
            protected String getPluNumberForPluRecord(ScalesItemInfo item) {
                return item.idBarcode;
            }*/

            protected boolean clearFiles(DataSocket socket, List<String> localErrors) throws IOException {
                return super.clearFiles(socket, localErrors)
                        && clearFile(socket, localErrors, scales.port, fileKeyAssignment)
                        && clearFile(socket, localErrors, scales.port, fileDF);
            }

            private Set<Integer> usedGroups = new HashSet<>();
            @Override
            protected boolean sendKeyAssignment(DataSocket socket, List<String> localErrors, ScalesItemInfo item, Integer plu) throws IOException {
                processTransactionLogger.info(getLogPrefix() + "Send key assignment started");
                JSONObject infoJSON = item.info != null ? new JSONObject(item.info).optJSONObject("digism5300") : null;
                processTransactionLogger.info(getLogPrefix() + "Send key assignment started: infoJSON=" + infoJSON + ", pluNumber=" + item.pluNumber);
                if (infoJSON != null && item.pluNumber != null) {

                    Integer numberGroup = infoJSON.optInt("numberGroup");
                    numberGroup = numberGroup == 0 ? 1 : numberGroup < 10 ? numberGroup : (numberGroup + 20);
                    String nameGroup = infoJSON.optString("nameGroup", "Group " + numberGroup);
                    String nameItem = infoJSON.optString("nameItem", item.name);
                    Integer overPluNumber = infoJSON.optInt("overPluNumber", item.pluNumber);

                    processTransactionLogger.info(String.format(getLogPrefix() + "Sending key assignment %s to scales %s", overPluNumber, scales.port));
                    int reply = sendRecord(socket, cmdWrite, fileKeyAssignment, makeKeyAssignmentRecord(item, numberGroup, nameGroup, nameItem, overPluNumber, false));
                    if (reply == 0) {
                        processTransactionLogger.info(String.format(getLogPrefix() + "Sending df %s to scales %s", overPluNumber, scales.port));
                        reply = sendRecord(socket, cmdWrite, fileDF, makeDFRecord(item, overPluNumber));
                    }
                    if (reply == 0 && !usedGroups.contains(numberGroup)) {
                        usedGroups.add(numberGroup);
                        processTransactionLogger.info(String.format(getLogPrefix() + "Sending group assignment %s to scales %s", numberGroup, scales.port));
                        reply = sendRecord(socket, cmdWrite, fileKeyAssignment, makeKeyAssignmentRecord(item, numberGroup, nameGroup, nameItem, overPluNumber, true));
                    }
                    if (reply != 0) {
                        logError(localErrors, String.format(getLogPrefix() + "Send key assignment %s to scales %s failed. Error: %s", plu, scales.port, reply));
                    }
                    return reply == 0;
                } else {
                    return true;
                }
            }

            private byte[] makeKeyAssignmentRecord(ScalesItemInfo item, Integer numberGroup, String nameGroup, String nameItem, Integer overPluNumber, boolean isGroup) throws UnsupportedEncodingException {
                int length = 55;
                ByteBuffer bytes = ByteBuffer.allocate(length);
                bytes.order(ByteOrder.LITTLE_ENDIAN);

                //PRESET NUMBER, 4 bytes
                bytes.put(getHexBytes(fillLeadingZeroes(isGroup ? (10000 + numberGroup) : ((numberGroup - 1) * 1000 + overPluNumber), 8)));

                //PRESET RECORD SIZE
                bytes.put((byte) (length >>> 8));
                bytes.put((byte) length);

                //PRESET KEY SWITCH, 4 bytes
                bytes.put(getHexBytes(fillLeadingZeroes(isGroup ? numberGroup : getPluNumberForPluRecord(item)/*item.idBarcode*/, 8)));

                //PRESET STATUS, 1 byte
                bytes.put((byte) (isGroup ? 32 : 0));

                //PRESET CSIZE, 1 byte
                bytes.put((byte) 0);

                //PRESET NAME, 32 bytes
                bytes.put(getBytes(trim(isGroup ? nameGroup : nameItem, 32)));

                //PRESET REFER TO FILE
                bytes.put((byte) 0);

                return bytes.array();
            }

            private byte[] makeDFRecord(ScalesItemInfo item, Integer overPluNumber) {
                int length = 10;
                ByteBuffer bytes = ByteBuffer.allocate(length);
                bytes.order(ByteOrder.LITTLE_ENDIAN);

                //CONTAINER  NO., 4 bytes
                bytes.put(getHexBytes(fillLeadingZeroes(overPluNumber, 8)));

                //CONTAINER REC. SIZE
                bytes.put((byte) (length >>> 8));
                bytes.put((byte) length);

                //PLU CODE, 4 bytes
                bytes.put(getHexBytes(fillLeadingZeroes(getPluNumberForPluRecord(item)/*item.idBarcode*/, 8)));

                return bytes.array();
            }
        };
    }
}