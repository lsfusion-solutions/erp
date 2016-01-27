package equ.clt.handler.cas;

import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.Dispatch;
import com.jacob.com.Variant;
import equ.api.*;
import equ.api.scales.ScalesHandler;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItemInfo;
import equ.api.scales.TransactionScalesInfo;
import equ.clt.handler.shtrihPrint.UDPPort;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class CL5000JHandler extends ScalesHandler {

    private final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    protected final static Logger processStopListLogger = Logger.getLogger("StopListLogger");

    Integer idDepartment = 1;

    @Override
    public Map<Integer, SendTransactionBatch> sendTransaction(List<TransactionScalesInfo> transactionList) throws IOException {

        //need jacob dll in jacob.dll.path

        Map<Integer, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

        ActiveXComponent activeXComponent = null;
        Dispatch dispatch = null;

        try {

            activeXComponent = new ActiveXComponent("ForCas2.ForCas");
            dispatch = activeXComponent.getObject();

            Variant result = Dispatch.call(dispatch, "Init");
            if(checkErrors(result, true)) {

                if (transactionList.isEmpty()) {
                    processTransactionLogger.error("CL5000J: Empty transaction list!");
                }
                for (TransactionScalesInfo transaction : transactionList) {
                    processTransactionLogger.info("CL5000J: Send Transaction # " + transaction.id);

                    List<MachineryInfo> succeededScalesList = new ArrayList<>();
                    Exception exception = null;
                    try {

                        if (!transaction.machineryInfoList.isEmpty()) {

                            List<ScalesInfo> enabledScalesList = getEnabledScalesList(transaction, succeededScalesList);
                            String errors = "";

                            processTransactionLogger.info("CL5000J: Starting sending to " + enabledScalesList.size() + " scales...");
                            for (ScalesInfo scales : enabledScalesList) {

                                processTransactionLogger.info("CL5000J: Sending to scales # " + scales.number);

                                UDPPort port = new UDPPort(scales.port, 20304, 5000);

                                port.open();

                                connect(dispatch, scales.port);

                                    if(transaction.snapshot) {
                                        Dispatch.call(dispatch, "DeletePluAll");
                                        waitForExecution(dispatch, scales.port);
                                    }

                                    int count = 0;
                                    for(ScalesItemInfo item : transaction.itemsList) {

                                        processTransactionLogger.info("CL5000J: Sending item " + item.idBarcode);
                                        String message = fillZeroes(idDepartment, 4) + //Отдел №
                                                fillZeroes(item.pluNumber == null ? item.idBarcode : item.pluNumber, 6) + //Товар №
                                                "01" + //Тип товара
                                                "00" + //Масса для цены
                                                fillZeroes(item.price == null ? "" : item.price.intValue(), 10) + //"0000000545" + //Цена
                                                "0000" + //Группа №
                                                fillZeroes(item.idBarcode, 13) + //"0000000008418" + //Код товара
                                                "0000" + //Тара №
                                                "0000000000" + //Масса тары
                                                "000000" + //Кол-во товаров в наборе
                                                "00" + //Наименов. единицы №
                                                (item.expiryDate == null ? "000000" :  new SimpleDateFormat("yyMMdd").format(item.expiryDate)) + //Годен до (дата)
                                                (item.expiryDate == null ? "000000" : new SimpleDateFormat("HHmmss").format(item.expiryDate)) + "000000" + //Годен до (время)
                                                "000000" + //Дата упаковки
                                                "000000" + //Время упаковки
                                                "000000" + //Дата производства
                                                "000000" + //Состав продукта №
                                                "00" + //Фиксиров. Стоимость
                                                "000000" + //Перемещ. мясопрод. №
                                                "000000" + //Страна происх. №
                                                "0000" + //Пищевая ценность №
                                                "0000" + //Label no
                                                "0000" + //Aux label no
                                                "0000" + //Barcode no
                                                "0000" + //Barcode2 no
                                                "0000" + //Sale msg
                                                "0000000000" + //Special price
                                                "0000000000" + //Fixed weight
                                                "00" + //Logo no
                                                fillSpaces("", 8) + //"НТ64    " + //Logo name
                                                fillSpaces(substr(item.name, 0, 40), 54) + //Plu name1
                                                fillSpaces(substr(item.name, 40, 80), 54) + //Plu name2
                                                fillSpaces("", 30) + //Name3
                                                trim(item.description, "", 300);/* "Прямое сообщение"*/;

                                        if(!checkErrors(Dispatch.call(dispatch, "AddPlu", message), false)) {
                                            errors += "CL5000J: Failed to load item " + item.idBarcode + "\n";
                                        }
                                        count++;
                                        if (count >= 50) {
                                            Dispatch.call(dispatch, "SendPlu");
                                            count = 0;
                                        }
                                    }
                                    if(count > 0)
                                        Dispatch.call(dispatch, "SendPlu");

                                    if (errors.isEmpty())
                                        succeededScalesList.add(scales);
                                    else
                                        exception = new RuntimeException(errors);

                            }
                        }
                    } catch (Exception e) {
                        exception = e;
                    }
                    sendTransactionBatchMap.put(transaction.id, new SendTransactionBatch(succeededScalesList, exception));
                }
            }
        } catch (UnsatisfiedLinkError e) {
            for(TransactionScalesInfo transaction : transactionList) {
                sendTransactionBatchMap.put(transaction.id, new SendTransactionBatch(e));
            }
        } finally {
                if (dispatch != null) {
                    Dispatch.call(dispatch, "DisconnectAll");
                    Dispatch.call(dispatch, "DeInit");
                    dispatch.safeRelease();
                }
                if (activeXComponent != null)
                    activeXComponent.safeRelease();
        }
        return sendTransactionBatchMap;
    }

    private void waitForExecution(Dispatch dispatch, String ip) {
        Integer state = null;
        while (state == null || (state != 1 && state != 55 && state != 99)) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {
            }
            state = ((ActiveXComponent) dispatch).getProperty("State").getInt();
        }
        //После очистки выставляется статус 99 - disconnect
        if (state == 99)
            connect(dispatch, ip);
    }

    private String fillZeroes(Object value, int length) {
        String result = value == null ? "" : String.valueOf(value);
        if(result.length() > length)
            result = result.substring(0, length);
        while (result.length() < length)
            result = "0" + result;
        return result;
    }

    private String fillSpaces(Object value, int length) {
        String result = value == null ? "" : String.valueOf(value);
        if(result.length() > length)
            result = result.substring(0, length);
        while (result.length() < length)
            result += " ";
        return result;
    }

    private String substr(String value, int from, int to) {
        return value == null || value.length() < from ? null : value.substring(from, Math.min(to, value.length()));
    }

    protected List<ScalesInfo> getEnabledScalesList(TransactionScalesInfo transaction, List<MachineryInfo> succeededScalesList) {
        List<ScalesInfo> enabledScalesList = new ArrayList<>();
        for (ScalesInfo scales : transaction.machineryInfoList) {
            if(scales.succeeded)
                succeededScalesList.add(scales);
            else if (scales.enabled)
                enabledScalesList.add(scales);
        }
        if (enabledScalesList.isEmpty())
            for (ScalesInfo scales : transaction.machineryInfoList) {
                if (!scales.succeeded)
                    enabledScalesList.add(scales);
            }
        return enabledScalesList;
    }

    public static boolean checkErrors(Variant result, Boolean throwException) throws RuntimeException {
        boolean error = result.toString().equals("-1");
        if (error) {
            if (throwException)
                throw new RuntimeException("CL5000J: Error occurred");
            else return false;
        } else return true;
    }

    private void connect(Dispatch dispatch, String ip) {
        Dispatch.call(dispatch, "Connection", new Variant(ip), new Variant(20304), new Variant(1), new Variant(5010));
    }


    protected String trim(String input, String defaultValue, Integer length) {
        return input == null ? defaultValue : (length == null || length >= input.trim().length() ? input.trim() : input.trim().substring(0, length));
    }

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<MachineryInfo> machineryInfoList) throws IOException {

        ActiveXComponent activeXComponent = null;
        Dispatch dispatch = null;

        try {
            activeXComponent = new ActiveXComponent("ForCas2.ForCas");
            dispatch = activeXComponent.getObject();

            Variant result = Dispatch.call(dispatch, "Init");
            if(checkErrors(result, true)) {

                if (stopListInfo != null && !stopListInfo.exclude) {

                    processStopListLogger.info("CL5000J: Send StopList # " + stopListInfo.number);

                    if (!machineryInfoList.isEmpty()) {

                        for (MachineryInfo scales : machineryInfoList) {

                            try {

                                if (scales.port != null) {

                                    processStopListLogger.info("CL5000J: Sending StopList to scale # " + scales.number);

                                    UDPPort port = new UDPPort(scales.port, 20304, 5000);

                                    port.open();

                                    connect(dispatch, scales.port);

                                    for (ItemInfo item : stopListInfo.stopListItemMap.values()) {

                                        processStopListLogger.error("CL5000J: Senging StopList - Deleting item " + item.idBarcode);
                                        if (!checkErrors(Dispatch.call(dispatch, "DeletePlu", idDepartment, fillZeroes(item.pluNumber == null ? item.idBarcode : item.pluNumber, 6)), false)) {
                                            processStopListLogger.error("CL5000J: Failed to delete item " + item.idBarcode);
                                        }
                                    }
                                }

                            } catch (Exception e) {
                                processStopListLogger.error(String.format("CL5000J: Send StopList %s to scales %s error", stopListInfo.number, scales.number), e);
                            }
                        }
                    }
                }
            }
        } catch (UnsatisfiedLinkError e) {
            processStopListLogger.error(String.format("CL5000J: Send StopList %s error", stopListInfo.number), e);
        } finally {
            if (dispatch != null) {
                Dispatch.call(dispatch, "DisconnectAll");
                Dispatch.call(dispatch, "DeInit");
                dispatch.safeRelease();
            }
            if (activeXComponent != null)
                activeXComponent.safeRelease();
        }
    }

    @Override
    public String getGroupId(TransactionScalesInfo transactionInfo) throws IOException {
        return "CL5000J";
    }

    @Override
    public void sendSoftCheck(SoftCheckInfo softCheckInfo) throws IOException {

    }
}