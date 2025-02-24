package equ.clt.handler.digi;

import com.google.common.base.Throwables;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItem;
import equ.api.scales.TransactionScalesInfo;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.json.JSONObject;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

            @Override
            protected BigDecimal getTareWeight(ScalesItem item) {
                BigDecimal tareWeight = null;
                JSONObject infoJSON = getExtInfo(item.info);
                if (infoJSON != null) {
                    tareWeight = infoJSON.optBigDecimal("tareWeight", null);
                }
                return tareWeight;
            }

            protected boolean clearFiles(DataSocket socket, List<String> localErrors, boolean clearImages) throws IOException {
                return super.clearFiles(socket, localErrors, clearImages)
                        && clearFile(socket, localErrors, scales.port, fileKeyAssignment)
                        && clearFile(socket, localErrors, scales.port, fileDF)
                        && (!clearImages || clearImages());
            }

            private boolean clearImages() {
                FTPClient ftpClient = new FTPClient();
                ftpClient.setDataTimeout(120000); //2 minutes = 120 sec
                ftpClient.setConnectTimeout(60000); //1 minute = 60 sec
                ftpClient.setAutodetectUTF8(true);

                try {
                    ftpClient.connect(scales.port, 21);
                    boolean login = ftpClient.login("root", "teraoka");
                    if (login) {
                        ftpClient.enterLocalPassiveMode();
                        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
                        ftpClient.setFileTransferMode(FTP.BINARY_FILE_TYPE);
                        if(ftpClient.changeWorkingDirectory("../opt/pcscale/files/img/plu/")) {

                            for (FTPFile f : ftpClient.listFiles()) {
                                if (!f.isDirectory()) {
                                    boolean done = ftpClient.deleteFile(f.getName());
                                    if (!done) {
                                        throw new RuntimeException("Failed to delete '" + f.getName() + "'");
                                    }
                                }
                            }
                        } else {
                            throw new RuntimeException("Directory '/../opt/pcscale/files/img/plu/'not found for '" + scales.port + "'");
                        }
                        return true;
                    } else {
                        throw new RuntimeException("Incorrect login or password for '" + scales.port + "'");
                    }
                } catch (IOException e) {
                    throw Throwables.propagate(e);
                } finally {
                    if (ftpClient.isConnected()) {
                        try {
                            ftpClient.setSoTimeout(10000);
                            ftpClient.logout();
                            ftpClient.disconnect();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            private Set<Integer> usedGroups = new HashSet<>();
            @Override
            protected boolean sendKeyAssignment(DataSocket socket, List<String> localErrors, ScalesItem item, Integer plu) throws IOException {
                processTransactionLogger.info(getLogPrefix() + "Send key assignment started");
                JSONObject infoJSON = getExtInfo(item.info);
                processTransactionLogger.info(getLogPrefix() + "Send key assignment started: infoJSON=" + infoJSON + ", pluNumber=" + item.pluNumber);
                if (infoJSON != null && item.pluNumber != null) {

                    Integer originNumberGroup = infoJSON.optInt("numberGroup");
                    Integer numberGroup = originNumberGroup == 0 ? 1 : originNumberGroup < 10 ? originNumberGroup : (originNumberGroup + 20);
                    String nameGroup = infoJSON.optString("nameGroup", "Group " + numberGroup);
                    String nameItem = infoJSON.optString("nameItem", item.name);
                    Integer overPluNumber = infoJSON.optInt("overPluNumber", item.pluNumber);

                    processTransactionLogger.info(String.format(getLogPrefix() + "Sending key assignment %s to scales %s", overPluNumber, scales.port));
                    int reply = sendRecord(socket, cmdWrite, fileKeyAssignment, makeKeyAssignmentRecord(item, originNumberGroup, numberGroup, nameGroup, nameItem, overPluNumber, false));
                    if (reply == 0) {
                        processTransactionLogger.info(String.format(getLogPrefix() + "Sending df %s to scales %s", overPluNumber, scales.port));
                        reply = sendRecord(socket, cmdWrite, fileDF, makeDFRecord(item));
                    }
                    if (reply == 0 && !usedGroups.contains(numberGroup)) {
                        usedGroups.add(numberGroup);
                        processTransactionLogger.info(String.format(getLogPrefix() + "Sending group assignment %s to scales %s", numberGroup, scales.port));
                        reply = sendRecord(socket, cmdWrite, fileKeyAssignment, makeKeyAssignmentRecord(item, originNumberGroup, numberGroup, nameGroup, nameItem, overPluNumber, true));
                    }
                    if (reply != 0) {
                        logError(localErrors, String.format(getLogPrefix() + "Send key assignment %s to scales %s failed. Error: %s", plu, scales.port, reply));
                    }
                    return reply == 0;
                } else {
                    return true;
                }
            }

            private byte[] makeKeyAssignmentRecord(ScalesItem item, Integer originNumberGroup, Integer numberGroup, String nameGroup, String nameItem, Integer overPluNumber, boolean isGroup) throws UnsupportedEncodingException {
                int length = isGroup ? 85 : 44;
                ByteBuffer bytes = ByteBuffer.allocate(length);
                bytes.order(ByteOrder.LITTLE_ENDIAN);

                //PRESET NUMBER, 4 bytes
                bytes.put(getHexBytes(fillLeadingZeroes(isGroup ? (10000 + numberGroup) : ((numberGroup - 1) * 1000 + overPluNumber), 8)));

                //PRESET RECORD SIZE
                bytes.put((byte) (length >>> 8));
                bytes.put((byte) length);

                //PRESET KEY SWITCH, 4 bytes
                bytes.put(getHexBytes(fillLeadingZeroes(isGroup ? numberGroup : getPluNumberForPluRecord(item), 8)));

                //PRESET STATUS, 1 byte
                bytes.put((byte) (isGroup ? 32 : 0));

                //PRESET CSIZE, 1 byte
                bytes.put((byte) 0);

                //PRESET NAME, 32 bytes
                bytes.put(getBytes(isGroup ? nameGroup : nameItem, 32));

                if(isGroup) {
                    //PRESET BUTTON WIDTH, 2 bytes
                    bytes.putShort((short) 0);

                    //PRESET BUTTON HEIGHT, 2 bytes
                    bytes.putShort((short) 0);

                    //SYSTEM FILE, 32 bytes
                    bytes.put(ByteBuffer.allocate(32).array());

                    //BORDER, 1 byte
                    bytes.put((byte) 4);

                    //IMAGE NUMBER, 4 bytes
                    bytes.putInt(originNumberGroup);
                }

                return bytes.array();
            }

            private byte[] makeDFRecord(ScalesItem item) {
                int length = 10;
                ByteBuffer bytes = ByteBuffer.allocate(length);
                bytes.order(ByteOrder.LITTLE_ENDIAN);

                //CONTAINER  NO., 4 bytes
                String containerNumber = fillLeadingZeroes(item.pluNumber, 8);
                bytes.put(getHexBytes(containerNumber));

                //CONTAINER REC. SIZE
                bytes.put((byte) (length >>> 8));
                bytes.put((byte) length);

                //PLU CODE, 4 bytes
                String pluCode = fillLeadingZeroes(getPluNumberForPluRecord(item), 8);
                bytes.put(getHexBytes(pluCode));

                return bytes.array();
            }
        };
    }

    private JSONObject getExtInfo(String extInfo) {
        return getExtInfo(extInfo, "digism5300");
    }
}