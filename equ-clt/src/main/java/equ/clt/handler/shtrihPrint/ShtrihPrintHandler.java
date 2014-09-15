package equ.clt.handler.shtrihPrint;

import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.Dispatch;
import com.jacob.com.Variant;
import equ.api.*;
import equ.api.scales.*;
import equ.clt.EquipmentServer;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.IOException;
import java.util.*;

public class ShtrihPrintHandler extends ScalesHandler {

    protected final static Logger logger = Logger.getLogger(EquipmentServer.class);
    private FileSystemXmlApplicationContext springContext;

    public ShtrihPrintHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    @Override
    public void sendTransaction(TransactionScalesInfo transactionInfo, List<ScalesInfo> machineryInfoList) throws IOException {

        //System.setProperty(LibraryLoader.JACOB_DLL_PATH, "E:\\work\\Кассы-весы\\dll\\jacob-1.15-M3-x86.dll");

        logger.info("Shtrih: Send Transaction # " + transactionInfo.id);
        
        ActiveXComponent shtrihActiveXComponent = new ActiveXComponent("AddIn.DrvLP");
        Dispatch shtrihDispatch = shtrihActiveXComponent.getObject();

        ScalesSettings shtrihSettings = (ScalesSettings) springContext.getBean("shtrihSettings");
        boolean usePLUNumberInMessage = shtrihSettings == null || shtrihSettings.usePLUNumberInMessage;
        boolean newLineNoSubstring = shtrihSettings == null || shtrihSettings.newLineNoSubstring;

        Variant pass = new Variant(30);

        if (!machineryInfoList.isEmpty()) {
            Set<String> ips = new HashSet<String>();
            for (ScalesInfo scales : machineryInfoList) {
                if(scales.port != null)
                    ips.add(scales.port);
            }

            if (ips.isEmpty())
                throw new RuntimeException("ShtrihPrintHandler. No IP-addresses defined");
            else
                for (String ip : ips) {

                    logger.info("Shtrih: Connecting ip: " + ip);

                    shtrihActiveXComponent.setProperty("LDInterface", new Variant(1));
                    shtrihActiveXComponent.setProperty("LDRemoteHost", new Variant(ip));
                    Dispatch.call(shtrihDispatch, "AddLD");
                    Dispatch.call(shtrihDispatch, "SetActiveLD");

                    Variant result = Dispatch.call(shtrihDispatch, "Connect");
                    if (result.toString().equals("0")) {
                        for (ScalesItemInfo item : transactionInfo.itemsList) {
                            Integer barcode = Integer.parseInt(item.idBarcode.substring(0, 5));
                            Integer pluNumber = item.pluNumber != null ? item.pluNumber : barcode;
                            Integer shelfLife = item.expirationDate == null ? (item.daysExpiry == null ? 0 : item.daysExpiry) : 0;

                            int len = item.name.length();
                            String firstName = item.name.substring(0, len < 28 ? len : 28);
                            String secondName = len < 28 ? "" : item.name.substring(28, len < 56 ? len : 56);

                            shtrihActiveXComponent.setProperty("Password", pass);
                            shtrihActiveXComponent.setProperty("PLUNumber", new Variant(pluNumber));
                            shtrihActiveXComponent.setProperty("Price", new Variant(item.price));
                            shtrihActiveXComponent.setProperty("Tare", new Variant(0));
                            shtrihActiveXComponent.setProperty("ItemCode", new Variant(barcode));
                            shtrihActiveXComponent.setProperty("NameFirst", new Variant(firstName));
                            shtrihActiveXComponent.setProperty("NameSecond", new Variant(secondName));
                            shtrihActiveXComponent.setProperty("ShelfLife", new Variant(shelfLife)); //срок хранения в днях
                            String groupCode = item.idItemGroup == null ? null : item.idItemGroup.replace("_", "");
                            shtrihActiveXComponent.setProperty("GroupCode", new Variant(groupCode));
                            shtrihActiveXComponent.setProperty("PictureNumber", new Variant(0));
                            shtrihActiveXComponent.setProperty("ROSTEST", new Variant(0));
                            shtrihActiveXComponent.setProperty("ExpiryDate", new Variant(item.expirationDate == null ? new Date(2001 - 1900, 0, 1) : item.expirationDate));
                            shtrihActiveXComponent.setProperty("GoodsType", new Variant(item.splitItem ? 0 : 1));

                            int start = 0;
                            int total = item.description.length();
                            int i = 0;
                            while (i < 8 && start < total) {
                                shtrihActiveXComponent.setProperty("MessageNumber", new Variant(usePLUNumberInMessage ? item.pluNumber : item.descriptionNumber));
                                shtrihActiveXComponent.setProperty("StringNumber", new Variant(i + 1));
                                String message = "";
                                if (newLineNoSubstring) {
                                    message = item.description.substring(start, total).split("\n")[0];
                                    message = message.substring(0, Math.min(message.length(), 50));
                                } else
                                    message = item.description.substring(start, Math.min(start + 50, total)).split("\n")[0];
                                shtrihActiveXComponent.setProperty("MessageString", new Variant(message));
                                start += message.length() + 1;
                                i++;

                                result = Dispatch.call(shtrihDispatch, "SetMessageData");
                                if (!result.toString().equals("0")) {
                                    throw new RuntimeException("ShtrihPrintHandler. Item # " + item.idBarcode + " Error # " + result.toString());
                                }
                            }

                            result = Dispatch.call(shtrihDispatch, "SetPLUDataEx");
                            if (!result.toString().equals("0")) {
                                throw new RuntimeException("ShtrihPrintHandler. Item # " + item.idBarcode + " Error # " + result.toString());
                            }
                        }
                        result = Dispatch.call(shtrihDispatch, "Disconnect");
                        if (!result.toString().equals("0")) {
                            throw new RuntimeException("ShtrihPrintHandler. Disconnection error (# " + result.toString() + ")");
                        }
                    } else {
                        Dispatch.call(shtrihDispatch, "Disconnect");
                        throw new RuntimeException("ShtrihPrintHandler. Connection error (# " + result.toString() + ")");
                    }
                    logger.info("Shtrih: Disconnecting ip: " + ip);
                }
        }
    }

    @Override
    public void sendSoftCheck(SoftCheckInfo softCheckInfo) throws IOException {

    }
}
