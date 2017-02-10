package lsfusion.erp.region.by.machinery.board.fiscalboard;

import lsfusion.erp.region.by.machinery.board.BoardDaemon;
import lsfusion.server.classes.DateClass;
import lsfusion.server.classes.DateTimeClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.*;
import lsfusion.server.logics.scripted.ScriptingBusinessLogics;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.session.DataSession;
import org.springframework.util.Assert;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.Socket;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.concurrent.Callable;

public class FiscalBoardDaemon extends BoardDaemon {
    private Integer serverPort;
    private String idPriceListType;
    private String idStock;

    public FiscalBoardDaemon(ScriptingBusinessLogics businessLogics, DBManager dbManager, LogicsInstance logicsInstance, Integer serverPort, String idPriceListType, String idStock) {
        super(businessLogics, dbManager, logicsInstance);
        this.serverPort = serverPort;
        this.idPriceListType = idPriceListType;
        this.idStock = idStock;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();
        Assert.notNull(serverPort, "serverPort must be specified");
        Assert.notNull(idPriceListType, "idPriceListType must be specified");
        Assert.notNull(idStock, "idStock must be specified");
    }

    @Override
    public String getEventName() {
        return "fiscal-board";
    }

    @Override
    protected Callable getCallable(Socket socket) {
        return new SocketCallable(businessLogics, socket);
    }

    public class SocketCallable implements Callable {

        private BusinessLogics BL;
        private Socket socket;

        public SocketCallable(BusinessLogics BL, Socket socket) {
            this.BL = BL;
            this.socket = socket;
        }

        @Override
        public Object call() {
            
            try {

                int length = 60;
                BufferedReader inFromClient = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                DataOutputStream outToClient = new DataOutputStream(socket.getOutputStream());
                String barcode = inFromClient.readLine();

                byte[] message = readMessage(BL, barcode);
                byte[] answer = new byte[length + 3];
                answer[0] = (byte) 0xAB; //command
                answer[1] = 0; //error code
                answer[2] = (byte) length; //message length
                System.arraycopy(message, 0, answer, 3, message.length);
                outToClient.write(answer);
                
                Thread.sleep(3000);
                outToClient.close();
                inFromClient.close();              
                
                return null;
            } catch (Exception e) {
                logger.error("FiscalBoard Error: ", e);
            }
            return null;
        }

        private byte[] readMessage(BusinessLogics BL, String idBarcode) throws SQLException, UnsupportedEncodingException, SQLHandledException, ScriptingErrorLog.SemanticErrorException {
            try (DataSession session = dbManager.createSession()) {
                int textLength = 44;
                int gapLength = 8; //передаётся 2 строки по 30 символов, но показывается только по 22
                Date date = new Date(Calendar.getInstance().getTime().getTime());
                ObjectValue skuObject = BL.getModule("Barcode").findProperty("skuBarcode[VARSTRING[15],DATE]").readClasses(session, new DataObject(idBarcode.substring(2)), new DataObject(date, DateClass.instance));
                if (skuObject instanceof NullValue) {
                    String notFound = "Штрихкод не найден";
                    while (notFound.length() < textLength)
                        notFound += " ";
                    return notFound.getBytes("cp1251");
                } else {
                    ObjectValue priceListTypeObject = BL.getModule("PriceListType").findProperty("priceListType[VARSTRING[100]]").readClasses(session, new DataObject(idPriceListType));
                    ObjectValue stockObject = BL.getModule("Stock").findProperty("stock[VARSTRING[100]]").readClasses(session, new DataObject(idStock));
                    String captionItem = (String) BL.getModule("Item").findProperty("caption[Item]").read(session, skuObject);
                    if (priceListTypeObject instanceof NullValue || stockObject instanceof NullValue) {
                        String notFound = "Неверные параметры сервера";
                        while (notFound.length() < textLength)
                            notFound += " ";
                        return notFound.getBytes("cp1251");
                    } else {
                        BigDecimal price = (BigDecimal) BL.getModule("PriceListType").findProperty("priceA[PriceListType,Sku,Stock,DATETIME]").read(session,
                                priceListTypeObject, skuObject, stockObject, new DataObject(new Timestamp(date.getTime()), DateTimeClass.instance));
                        String priceMessage = new DecimalFormat("###,###.#").format(price.doubleValue());
                        while (captionItem.length() + priceMessage.length() < (textLength - 1)) {
                            priceMessage = " " + priceMessage;
                        }
                        captionItem = captionItem.substring(0, Math.min(captionItem.length(), (textLength - priceMessage.length() - 1)));
                        String message = captionItem + " " + priceMessage;
                        String gap = "";
                        for (int i = 0; i < gapLength; i++) {
                            gap += " ";
                        }
                        return (message.substring(0, textLength / 2) + gap + message.substring(textLength / 2, textLength) + gap).getBytes("cp1251");
                    }
                }
            }
        }
    }
}
