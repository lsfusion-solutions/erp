package equ.clt.handler.shtrihPrint;

import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.Dispatch;
//import com.jacob.com.LibraryLoader;
import com.jacob.com.Variant;
import equ.api.*;
import equ.api.scales.*;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.IOException;
import java.util.*;

public class ShtrihPrintHandler extends ScalesHandler {

    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    
    private FileSystemXmlApplicationContext springContext;

    public ShtrihPrintHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    public String getGroupId(TransactionScalesInfo transactionInfo) {
        return "";
    }
    
    @Override
    public List<MachineryInfo> sendTransaction(TransactionScalesInfo transaction, List<ScalesInfo> scalesList) throws IOException {

        //System.setProperty(LibraryLoader.JACOB_DLL_PATH, "E:\\work\\Кассы-весы\\dll\\jacob-1.15-M3-x86.dll");
        
        processTransactionLogger.info("Shtrih: Send Transaction # " + transaction.id);
        List<MachineryInfo> succeededScalesList = new ArrayList<MachineryInfo>();
        
        ActiveXComponent shtrihActiveXComponent = new ActiveXComponent("AddIn.DrvLP");
        Dispatch shtrihDispatch = shtrihActiveXComponent.getObject();

        ScalesSettings shtrihSettings = (ScalesSettings) springContext.getBean("shtrihSettings");
        boolean usePLUNumberInMessage = shtrihSettings == null || shtrihSettings.usePLUNumberInMessage;
        boolean newLineNoSubstring = shtrihSettings == null || shtrihSettings.newLineNoSubstring;

        Variant pass = new Variant(30);

        if (!scalesList.isEmpty()) {

            List<ScalesInfo> enabledScalesList = new ArrayList<ScalesInfo>();
            for (ScalesInfo scales : scalesList) {
                if (scales.enabled)
                    enabledScalesList.add(scales);
            }
            
            Set<String> ips = new HashSet<String>();
            for (ScalesInfo scales : enabledScalesList.isEmpty() ? scalesList : enabledScalesList) {
                boolean error = false;
                String ip = scales.port;
                if (ip != null) {
                    ips.add(scales.port);
                    processTransactionLogger.info("Shtrih: Connecting ip: " + ip);

                    try {

                        shtrihActiveXComponent.setProperty("LDInterface", new Variant(1));
                        shtrihActiveXComponent.setProperty("LDRemoteHost", new Variant(ip));
                        Dispatch.call(shtrihDispatch, "AddLD");
                        Dispatch.call(shtrihDispatch, "SetActiveLD");

                        Variant result = Dispatch.call(shtrihDispatch, "Connect");
                        if (!isError(result)) {

                            shtrihActiveXComponent.setProperty("Password", pass);
                            if (!transaction.itemsList.isEmpty() && transaction.snapshot) {
                                Variant clear = Dispatch.call(shtrihDispatch, "ClearGoodsDB");
                                if (isError(clear))
                                    error = true;
                            }

                            if (!error) {
                                for (ScalesItemInfo item : transaction.itemsList) {
                                    Integer barcode = Integer.parseInt(item.idBarcode.substring(0, 5));
                                    Integer pluNumber = item.pluNumber != null ? item.pluNumber : barcode;
                                    Integer shelfLife = item.expirationDate == null ? (item.daysExpiry == null ? 0 : item.daysExpiry) : 0;

                                    int len = item.name.length();
                                    String firstName = item.name.substring(0, len < 28 ? len : 28);
                                    String secondName = len < 28 ? "" : item.name.substring(28, len < 56 ? len : 56);

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

                                    String description = item.description == null ? "" : item.description;
                                    int start = 0;
                                    int total = description.length();
                                    int i = 0;
                                    while (i < 8) {
                                        shtrihActiveXComponent.setProperty("MessageNumber", new Variant(usePLUNumberInMessage ? item.pluNumber : item.descriptionNumber));
                                        shtrihActiveXComponent.setProperty("StringNumber", new Variant(i + 1));
                                        String message = "";
                                        if (!description.isEmpty() && start < total) {
                                            if (newLineNoSubstring) {
                                                message = description.substring(start, total).split("\n")[0];
                                                message = message.substring(0, Math.min(message.length(), 50));
                                            } else
                                                message = description.substring(start, Math.min(start + 50, total)).split("\n")[0];
                                        }
                                        shtrihActiveXComponent.setProperty("MessageString", new Variant(message));
                                        start += message.length() + 1;
                                        i++;

                                        result = Dispatch.call(shtrihDispatch, "SetMessageData");
                                        if (isError(result)) {
                                            processTransactionLogger.error(String.format("ShtrihPrintHandler. Item # %s, Error # %s (%s)", item.idBarcode, result.getInt(), getErrorText(result)));
                                            error = true;
                                        }
                                    }

                                    result = Dispatch.call(shtrihDispatch, "SetPLUDataEx");
                                    if (isError(result)) {
                                        processTransactionLogger.error(String.format("ShtrihPrintHandler. Item # %s, Error # %s (%s)", item.idBarcode, result.getInt(), getErrorText(result)));
                                        error = true;
                                    }
                                }
                            }
                            result = Dispatch.call(shtrihDispatch, "Disconnect");
                            if (isError(result)) {
                                processTransactionLogger.error(String.format("ShtrihPrintHandler. Disconnection error # %s (%s)", result.getInt(), getErrorText(result)));
                                error = true;
                                continue;
                            }
                        } else {
                            Dispatch.call(shtrihDispatch, "Disconnect");
                            processTransactionLogger.error(String.format("ShtrihPrintHandler. Connection error # %s (%s)", result.getInt(), getErrorText(result)));
                            error = true;
                            continue;
                        }
                        processTransactionLogger.info("Shtrih: Disconnecting ip: " + ip);
                    } finally {
                        Dispatch.call(shtrihDispatch, "Disconnect");
                    }
                }
                if (!error)
                    succeededScalesList.add(scales);
            }
            if (ips.isEmpty())
                throw new RuntimeException("ShtrihPrintHandler. No IP-addresses defined");
        }
        return succeededScalesList;
    }
    
    private String getErrorText(Variant index) {
        switch (index.getInt()) {
            case -21: return "Блок данных имеет максимальную длину";
            case -20: return "Соединение не установлено";
            case -19: return "UDP - порт занят другим приложением";
            case -18: return "Неверный тип устройства";
            case -17: return "Неверная высота штрих - кода";
            case -16: return "Нет активного логического устройства";
            case -15: return "Команда не реализуется в данной версии";
            case -14: return "Удаление логического устройства невозможно";
            case -13: return "Устройство занято";
            case -12: return "Нет ответа на предыдущую команду";
            case -11: return "Команда не является широковещательной";
            case -10: return "Неверный номер логического устройства";
            case -9: return "Параметр вне диапазона";
            case -3: return "Сом - порт недоступен";
            case -2: return "Сом - порт занят другим приложением";
            case -1: return "Нет связи";
            default: return String.valueOf(index);
        }
    }
    
    private boolean isError(Variant value) {
        return !value.toString().equals("0");
    }

    @Override
    public void sendSoftCheck(SoftCheckInfo softCheckInfo) throws IOException {

    }
}
