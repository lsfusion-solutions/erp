package equ.srv;

import com.google.common.base.Throwables;
import equ.api.MachineryInfo;
import equ.api.RequestExchange;
import equ.api.cashregister.CashRegisterInfo;
import equ.api.cashregister.CashierInfo;
import equ.api.cashregister.DiscountCard;
import equ.api.terminal.TerminalOrder;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.form.property.Compare;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.action.controller.stack.ExecutionStack;
import lsfusion.server.logics.action.session.DataSession;
import lsfusion.server.logics.classes.ConcreteClass;
import lsfusion.server.logics.classes.data.integral.LongClass;
import lsfusion.server.logics.classes.data.time.DateClass;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.apache.commons.lang3.StringUtils.trim;

public class MachineryExchangeEquipmentServer {

    static ScriptingLogicsModule cashRegisterLM;
    static ScriptingLogicsModule discountCardLM;
    static ScriptingLogicsModule equLM;
    static ScriptingLogicsModule purchaseInvoiceAgreementLM;
    static ScriptingLogicsModule machineryLM;
    static ScriptingLogicsModule machineryPriceTransactionLM;
    static ScriptingLogicsModule machineryPriceTransactionDiscountCardLM;
    static ScriptingLogicsModule terminalLM;

    public static void init(BusinessLogics BL) {
        cashRegisterLM = BL.getModule("EquipmentCashRegister");
        discountCardLM = BL.getModule("DiscountCard");
        equLM = BL.getModule("Equipment");
        purchaseInvoiceAgreementLM = BL.getModule("PurchaseInvoiceAgreement");
        machineryLM = BL.getModule("Machinery");
        machineryPriceTransactionLM = BL.getModule("MachineryPriceTransaction");
        machineryPriceTransactionDiscountCardLM = BL.getModule("MachineryPriceTransactionDiscountCard");
        terminalLM = BL.getModule("EquipmentTerminal");
    }

    public static List<RequestExchange> readRequestExchange(EquipmentServer server, String sidEquipmentServer, BusinessLogics BL, ExecutionStack stack) throws SQLException {

        List<RequestExchange> requestExchangeList = new ArrayList();
        if(equLM != null) {

            try (DataSession session = server.createSession()) {

                KeyExpr requestExchangeExpr = new KeyExpr("requestExchange");
                ImRevMap<Object, KeyExpr> requestExchangeKeys = MapFact.singletonRev("requestExchange", requestExchangeExpr);
                QueryBuilder<Object, Object> requestExchangeQuery = new QueryBuilder<>(requestExchangeKeys);

                String[] requestExchangeNames = new String[]{"dateFromRequestExchange", "dateToRequestExchange", "nameRequestExchangeTypeRequestExchange"};
                LP[] requestExchangeProperties = equLM.findProperties("dateFrom[RequestExchange]", "dateTo[RequestExchange]", "nameRequestExchangeType[RequestExchange]");
                for (int i = 0; i < requestExchangeProperties.length; i++) {
                    requestExchangeQuery.addProperty(requestExchangeNames[i], requestExchangeProperties[i].getExpr(requestExchangeExpr));
                }
                if(machineryPriceTransactionDiscountCardLM != null) {
                    requestExchangeQuery.addProperty("startDateRequestExchange", machineryPriceTransactionDiscountCardLM.findProperty("startDate[RequestExchange]").getExpr(requestExchangeExpr));
                }
                ObjectValue equipmentServerObject = equLM.findProperty("sidTo[STRING[20]]").readClasses(session, new DataObject(sidEquipmentServer));
                requestExchangeQuery.and(equLM.findProperty("notOverSucceeded[RequestExchange, EquipmentServer]").getExpr(requestExchangeExpr, equipmentServerObject.getExpr()).getWhere());
                ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> requestExchangeResult = requestExchangeQuery.executeClasses(session);
                for (int i = 0; i < requestExchangeResult.size(); i++) {

                    DataObject requestExchangeObject = requestExchangeResult.getKey(i).get("requestExchange");
                    LocalDate dateFromRequestExchange = (LocalDate) requestExchangeResult.getValue(i).get("dateFromRequestExchange").getValue();
                    LocalDate dateToRequestExchange = (LocalDate) requestExchangeResult.getValue(i).get("dateToRequestExchange").getValue();
                    LocalDate startDateRequestExchange = machineryPriceTransactionDiscountCardLM != null ? (LocalDate) requestExchangeResult.getValue(i).get("startDateRequestExchange").getValue() : null;
                    String typeRequestExchange = trim((String) requestExchangeResult.getValue(i).get("nameRequestExchangeTypeRequestExchange").getValue());

                    //terminalOrder - единственный тип запроса для ТСД. Все остальные - только для касс
                    if(typeRequestExchange != null && typeRequestExchange.contains("terminalOrder")) {

                        if(terminalLM != null) {

                            KeyExpr terminalExpr = new KeyExpr("terminal");
                            ImRevMap<Object, KeyExpr> terminalKeys = MapFact.singletonRev("terminal", terminalExpr);
                            QueryBuilder<Object, Object> terminalQuery = new QueryBuilder<>(terminalKeys);

                            String[] terminalNames = new String[]{"idStockTerminal"};
                            LP[] terminalProperties = terminalLM.findProperties("idStock[Terminal]");
                            for (int j = 0; j < terminalProperties.length; j++) {
                                terminalQuery.addProperty(terminalNames[j], terminalProperties[j].getExpr(terminalExpr));
                            }
                            //нужно хотя бы 1 свойство от Terminal, чтобы отсечь в запросе остальные типы оборудования
                            terminalQuery.and(terminalLM.findProperty("groupTerminal[Terminal]").getExpr(terminalExpr).getWhere());
                            terminalQuery.and(terminalLM.findProperty("in[Terminal,RequestExchange]").getExpr(terminalExpr, requestExchangeObject.getExpr()).getWhere());
                            terminalQuery.and(terminalLM.findProperty("stock[Terminal]").getExpr(terminalExpr).compare(
                                    terminalLM.findProperty("stock[RequestExchange]").getExpr(requestExchangeObject.getExpr()), Compare.EQUALS));
                            terminalQuery.and(terminalLM.findProperty("inactive[Terminal]").getExpr(terminalExpr).getWhere().not());
                            ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> result = terminalQuery.executeClasses(session);

                            String idStock = null;
                            for (int j = 0; j < result.size(); j++) {
                                idStock = trim((String) result.getValue(j).get("idStockTerminal").getValue());
                            }

                            requestExchangeList.add(new RequestExchange((Long) requestExchangeObject.getValue(), new HashSet<>(),
                                    new HashSet<>(), idStock, dateFromRequestExchange, dateToRequestExchange, startDateRequestExchange, typeRequestExchange));
                        }

                    } else {

                        if(cashRegisterLM != null) {
                            Set<CashRegisterInfo> cashRegisterSet = new HashSet<>();
                            Set<CashRegisterInfo> extraCashRegisterSet = readExtraCashRegisterSet(session, requestExchangeObject);
                            String idStock = null;

                            KeyExpr cashRegisterExpr = new KeyExpr("cashRegister");
                            ImRevMap<Object, KeyExpr> cashRegisterKeys = MapFact.singletonRev("cashRegister", cashRegisterExpr);
                            QueryBuilder<Object, Object> cashRegisterQuery = new QueryBuilder<>(cashRegisterKeys);

                            String[] cashRegisterNames = new String[]{"overDirectoryCashRegister", "idStockCashRegister", "nppGroupMachinery",
                                    "nppCashRegister", "handlerModelCashRegister"};
                            LP[] cashRegisterProperties = cashRegisterLM.findProperties("overDirectory[CashRegister]", "idStock[CashRegister]",
                                    "nppGroupMachinery[Machinery]", "npp[CashRegister]", "handlerModel[CashRegister]");
                            for (int j = 0; j < cashRegisterProperties.length; j++) {
                                cashRegisterQuery.addProperty(cashRegisterNames[j], cashRegisterProperties[j].getExpr(cashRegisterExpr));
                            }
                            //нужно хотя бы 1 свойство от CashRegister, чтобы отсечь в запросе остальные типы оборудования
                            cashRegisterQuery.and(cashRegisterLM.findProperty("groupCashRegister[CashRegister]").getExpr(cashRegisterExpr).getWhere());
                            cashRegisterQuery.and(cashRegisterLM.findProperty("in[EquipmentServer,CashRegister,RequestExchange]").getExpr(equipmentServerObject.getExpr(), cashRegisterExpr, requestExchangeObject.getExpr()).getWhere());
                            cashRegisterQuery.and(cashRegisterLM.findProperty("stock[CashRegister]").getExpr(cashRegisterExpr).compare(
                                    cashRegisterLM.findProperty("stock[RequestExchange]").getExpr(requestExchangeObject.getExpr()), Compare.EQUALS));
                            cashRegisterQuery.and(cashRegisterLM.findProperty("inactive[CashRegister]").getExpr(cashRegisterExpr).getWhere().not());
                            ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> result = cashRegisterQuery.executeClasses(session);
                            for (int j = 0; j < result.size(); j++) {

                                String directoryCashRegister = trim((String) result.getValue(j).get("overDirectoryCashRegister").getValue());
                                idStock = trim((String) result.getValue(j).get("idStockCashRegister").getValue());
                                Integer nppGroupMachinery = (Integer) result.getValue(j).get("nppGroupMachinery").getValue();
                                Integer nppCashRegister = (Integer) result.getValue(j).get("nppCashRegister").getValue();
                                String handlerModelCashRegister = trim((String) result.getValue(j).get("handlerModelCashRegister").getValue());

                                cashRegisterSet.add(new CashRegisterInfo(nppGroupMachinery, nppCashRegister, handlerModelCashRegister, null, directoryCashRegister, null, null, false));
                            }

                            requestExchangeList.add(new RequestExchange((Long) requestExchangeObject.getValue(), cashRegisterSet, extraCashRegisterSet,
                                    idStock, dateFromRequestExchange, dateToRequestExchange, startDateRequestExchange, typeRequestExchange));

                        }

                    }
                }
                session.applyException(BL, stack);
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return requestExchangeList;
    }

    private static Set<CashRegisterInfo> readExtraCashRegisterSet(DataSession session, DataObject requestExchangeObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Set<CashRegisterInfo> extraCashRegisterSet = new HashSet<>();
        KeyExpr stockExpr = new KeyExpr("stock");
        KeyExpr cashRegisterExpr = new KeyExpr("cashRegister");
        ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap("stock", stockExpr, "cashRegister", cashRegisterExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);

        String[] cashRegisterNames = new String[]{"npp", "nppGroup", "overDirectory", "idStock", "handlerModel"};
        LP[] cashRegisterProperties = cashRegisterLM.findProperties("npp[CashRegister]", "nppGroupMachinery[CashRegister]", "overDirectory[CashRegister]", "idStock[CashRegister]", "handlerModel[CashRegister]");
        for (int j = 0; j < cashRegisterProperties.length; j++) {
            query.addProperty(cashRegisterNames[j], cashRegisterProperties[j].getExpr(cashRegisterExpr));
        }
        query.and(cashRegisterLM.findProperty("in[Stock,RequestExchange]").getExpr(stockExpr, requestExchangeObject.getExpr()).getWhere());
        query.and(cashRegisterLM.findProperty("overDirectory[CashRegister]").getExpr(cashRegisterExpr).getWhere());
        query.and(cashRegisterLM.findProperty("stock[CashRegister]").getExpr(cashRegisterExpr).compare(stockExpr, Compare.EQUALS));
        query.and(cashRegisterLM.findProperty("inactive[CashRegister]").getExpr(cashRegisterExpr).getWhere().not());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
        for (ImMap<Object, Object> entry : result.values()) {
            Integer number = (Integer) entry.get("npp");
            Integer numberGroup = (Integer) entry.get("nppGroup");
            String handlerModel = trim((String) entry.get("handlerModel"));
            String directory = trim((String) entry.get("overDirectory"));
            String idStock = trim((String) entry.get("idStock"));
            extraCashRegisterSet.add(new CashRegisterInfo(numberGroup, number, handlerModel, null, directory, null, idStock, false, null, null, null));
        }
        return extraCashRegisterSet;
    }

    public static void errorRequestExchange(EquipmentServer server, BusinessLogics BL, ExecutionStack stack, Map<Long, Throwable> failedRequestsMap) throws SQLException {
        if (machineryPriceTransactionLM != null) {
            try (DataSession session = server.createSession()) {
                for (Map.Entry<Long, Throwable> request : failedRequestsMap.entrySet()) {
                    errorRequestExchange(session, request.getKey(), request.getValue());
                }
                session.applyException(BL, stack);
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    public static void errorRequestExchange(EquipmentServer server, BusinessLogics BL, ExecutionStack stack, Long requestExchange, Throwable t) throws SQLException {
        if (machineryPriceTransactionLM != null) {
            try (DataSession session = server.createSession()) {
                errorRequestExchange(session, requestExchange, t);
                session.applyException(BL, stack);
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    private static void errorRequestExchange(DataSession session, Long requestExchange, Throwable t) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        DataObject errorObject = session.addObject((ConcreteCustomClass) machineryPriceTransactionLM.findClass("RequestExchangeError"));
        machineryPriceTransactionLM.findProperty("date[RequestExchangeError]").change(LocalDateTime.now(), session, errorObject);
        OutputStream os = new ByteArrayOutputStream();
        t.printStackTrace(new PrintStream(os));
        machineryPriceTransactionLM.findProperty("erTrace[RequestExchangeError]").change(os.toString(), session, errorObject);
        machineryPriceTransactionLM.findProperty("requestExchange[RequestExchangeError]").change(requestExchange, session, errorObject);
    }

    public static void finishRequestExchange(EquipmentServer server, String sidEquipmentServer, BusinessLogics BL, ExecutionStack stack, Set<Long> succeededRequestsSet) throws SQLException {
        if (equLM != null) {
            try (DataSession session = server.createSession()) {
                ObjectValue equipmentServerObject = equLM.findProperty("sidTo[STRING[20]]").readClasses(session, new DataObject(sidEquipmentServer));
                if (equipmentServerObject instanceof DataObject) {
                    for (Long request : succeededRequestsSet) {
                        DataObject requestExchangeObject = new DataObject(request, (ConcreteCustomClass) machineryPriceTransactionLM.findClass("RequestExchange"));
                        equLM.findProperty("succeeded[RequestExchange, EquipmentServer]").change(true, session, requestExchangeObject, (DataObject) equipmentServerObject);
                        equLM.findProperty("dateTimeSucceeded[RequestExchange, EquipmentServer]").change(LocalDateTime.now(), session, requestExchangeObject, (DataObject) equipmentServerObject);
                    }
                }
                session.applyException(BL, stack);
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    public static List<DiscountCard> readDiscountCardList(EquipmentServer server, BusinessLogics BL, ExecutionStack stack, RequestExchange requestExchange) {
        List<DiscountCard> discountCardList = new ArrayList<>();
        if(machineryPriceTransactionDiscountCardLM != null) {
            try (DataSession session = server.createSession()) {

                KeyExpr discountCardExpr = new KeyExpr("discountCard");
                ImRevMap<Object, KeyExpr> discountCardKeys = MapFact.singletonRev("discountCard", discountCardExpr);

                QueryBuilder<Object, Object> discountCardQuery = new QueryBuilder<>(discountCardKeys);
                String[] discountCardNames = new String[]{"idDiscountCard", "numberDiscountCard", "nameDiscountCard",
                        "percentDiscountCard", "dateDiscountCard", "dateToDiscountCard", "initialSumDiscountCard",
                        "idDiscountCardType", "nameDiscountCardType", "firstNameContact", "lastNameContact", "middleNameContact", "birthdayContact",
                        "sexContact", "extInfo"};
                LP[] discountCardProperties = discountCardLM.findProperties("id[DiscountCard]", "seriesNumber[DiscountCard]", "name[DiscountCard]",
                        "percent[DiscountCard]", "date[DiscountCard]", "dateTo[DiscountCard]", "initialSum[DiscountCard]",
                        "idDiscountCardType[DiscountCard]", "nameDiscountCardType[DiscountCard]", "firstNameContact[DiscountCard]", "lastNameContact[DiscountCard]",
                        "middleNameHttpServerContact[DiscountCard]", "birthdayContact[DiscountCard]", "numberSexHttpServerContact[DiscountCard]",
                        "extInfo[DiscountCard]");
                for (int i = 0; i < discountCardProperties.length; i++) {
                    discountCardQuery.addProperty(discountCardNames[i], discountCardProperties[i].getExpr(discountCardExpr));
                }
                discountCardQuery.and(discountCardLM.findProperty("number[DiscountCard]").getExpr(discountCardExpr).getWhere());
                discountCardQuery.and(discountCardLM.findProperty("skipLoad[DiscountCard]").getExpr(discountCardExpr).getWhere().not());
                discountCardQuery.and(discountCardLM.findProperty("isActive[DiscountCard]").getExpr(discountCardExpr).getWhere());

                DataObject requestExchangeObject = new DataObject(requestExchange.requestExchange, (ConcreteClass) machineryPriceTransactionLM.findClass("RequestExchange"));

                Long numberFrom = parseLong((String) machineryPriceTransactionDiscountCardLM.findProperty("numberDiscountCardFrom[RequestExchange]").read(session, requestExchangeObject));
                if (numberFrom != null)
                    discountCardQuery.and(discountCardLM.findProperty("longNumber[DiscountCard]").getExpr(discountCardExpr).compare(new DataObject(numberFrom, LongClass.instance).getExpr(), Compare.GREATER_EQUALS));

                Long numberTo = parseLong((String) machineryPriceTransactionDiscountCardLM.findProperty("numberDiscountCardTo[RequestExchange]").read(session, requestExchangeObject));
                if (numberTo != null)
                    discountCardQuery.and(discountCardLM.findProperty("longNumber[DiscountCard]").getExpr(discountCardExpr).compare(new DataObject(numberTo, LongClass.instance).getExpr(), Compare.LESS_EQUALS));

                if(requestExchange.startDate != null)
                    discountCardQuery.and(discountCardLM.findProperty("date[DiscountCard]").getExpr(discountCardExpr).compare(new DataObject(requestExchange.startDate, DateClass.instance).getExpr(), Compare.GREATER_EQUALS));

                ObjectValue requestExchangeType = machineryPriceTransactionDiscountCardLM.findProperty("discountCardType[RequestExchange]").readClasses(session, requestExchangeObject);
                if(requestExchangeType instanceof DataObject) {
                    discountCardQuery.and(discountCardLM.findProperty("discountCardType[DiscountCard]").getExpr(discountCardExpr).compare(requestExchangeType.getExpr(), Compare.EQUALS));
                }

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> discountCardResult = discountCardQuery.execute(session, MapFact.singletonOrder("idDiscountCard", false));

                for (int i = 0, size = discountCardResult.size(); i < size; i++) {
                    ImMap<Object, Object> row = discountCardResult.getValue(i);

                    String idDiscountCard = getRowValue(row, "idDiscountCard");
                    if (idDiscountCard == null)
                        idDiscountCard = String.valueOf(discountCardResult.getKey(i).get("discountCard"));
                    String numberDiscountCard = getRowValue(row, "numberDiscountCard");
                    String nameDiscountCard = getRowValue(row, "nameDiscountCard");
                    BigDecimal percentDiscountCard = (BigDecimal) row.get("percentDiscountCard");
                    BigDecimal initialSumDiscountCard = (BigDecimal) row.get("initialSumDiscountCard");
                    LocalDate dateFromDiscountCard = (LocalDate) row.get("dateDiscountCard");
                    LocalDate dateToDiscountCard = (LocalDate) row.get("dateToDiscountCard");
                    String idDiscountCardType = (String) row.get("idDiscountCardType");
                    String nameDiscountCardType = (String) row.get("nameDiscountCardType");
                    String firstNameContact = (String) row.get("firstNameContact");
                    String lastNameContact = (String) row.get("lastNameContact");
                    String middleNameContact = (String) row.get("middleNameContact");
                    LocalDate birthdayContact = (LocalDate) row.get("birthdayContact");
                    Integer sexContact = (Integer) row.get("sexContact");
                    String extInfo = (String) row.get("extInfo");
                    discountCardList.add(new DiscountCard(idDiscountCard, numberDiscountCard, nameDiscountCard,
                            percentDiscountCard, initialSumDiscountCard, dateFromDiscountCard, dateToDiscountCard,
                            idDiscountCardType, nameDiscountCardType, firstNameContact, lastNameContact, middleNameContact, birthdayContact,
                            sexContact, true, extInfo));
                }

                DataObject logObject = session.addObject((ConcreteCustomClass) machineryPriceTransactionLM.findClass("RequestExchangeLog"));
                machineryPriceTransactionLM.findProperty("date[RequestExchangeLog]").change(LocalDateTime.now(), session, logObject);
                machineryPriceTransactionLM.findProperty("message[RequestExchangeLog]").change("Найдено дисконтных карт: " + discountCardList.size(), session, logObject);
                machineryPriceTransactionLM.findProperty("requestExchange[RequestExchangeLog]").change(requestExchange.requestExchange, session, logObject);
                session.applyException(BL, stack);

            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
        return discountCardList;
    }

    public static List<CashierInfo> readCashierInfoList(EquipmentServer server) throws SQLException {
        List<CashierInfo> cashierInfoList = new ArrayList<>();
        if(equLM != null) {
            try (DataSession session = server.createSession()) {

                KeyExpr employeeExpr = new KeyExpr("employee");
                ImRevMap<Object, KeyExpr> employeeKeys = MapFact.singletonRev("employee", employeeExpr);

                QueryBuilder<Object, Object> employeeQuery = new QueryBuilder<>(employeeKeys);
                String[] employeeNames = new String[]{"idEmployee", "shortNameContact", "externalPassword", "idPositionEmployee", "namePositionEmployee", "idStockEmployee"};
                LP[] employeeProperties = equLM.findProperties("id[Employee]", "shortName[Contact]", "externalPassword[Employee]", "idPosition[Employee]", "namePosition[Employee]", "idStock[Employee]");
                for (int i = 0; i < employeeProperties.length; i++) {
                    employeeQuery.addProperty(employeeNames[i], employeeProperties[i].getExpr(employeeExpr));
                }
                employeeQuery.and(equLM.findProperty("idStock[Employee]").getExpr(employeeExpr).getWhere());
                employeeQuery.and(equLM.findProperty("id[Employee]").getExpr(employeeExpr).getWhere());
                employeeQuery.and(equLM.findProperty("shortName[Contact]").getExpr(employeeExpr).getWhere());
                employeeQuery.and(equLM.findProperty("active[Employee]").getExpr(employeeExpr).getWhere());

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> employeeResult = employeeQuery.execute(session);

                for (int i = 0, size = employeeResult.size(); i < size; i++) {
                    ImMap<Object, Object> row = employeeResult.getValue(i);

                    String idCashier = getRowValue(row, "idEmployee");
                    String nameCashier = getRowValue(row, "shortNameContact");
                    String passwordCashier = getRowValue(row, "externalPassword");
                    String idPosition = getRowValue(row, "idPositionEmployee");
                    String namePosition = getRowValue(row, "namePositionEmployee");
                    String idStock = getRowValue(row, "idStockEmployee");
                    cashierInfoList.add(new CashierInfo(idCashier, nameCashier, passwordCashier, idPosition, namePosition, idStock));
                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return cashierInfoList;
    }

    public static List<TerminalOrder> readTerminalOrderList(EquipmentServer server, RequestExchange requestExchange) throws SQLException {
        List<TerminalOrder> terminalOrderList = new ArrayList<>();
        if (purchaseInvoiceAgreementLM != null) {
            try (DataSession session = server.createSession()) {
                KeyExpr orderExpr = new KeyExpr("order");
                KeyExpr orderDetailExpr = new KeyExpr("orderDetail");
                ImRevMap<Object, KeyExpr> orderKeys = MapFact.toRevMap("Order", orderExpr, "OrderDetail", orderDetailExpr);
                QueryBuilder<Object, Object> orderQuery = new QueryBuilder<>(orderKeys);
                String[] orderNames = new String[]{"dateOrder", "numberOrder", "idSupplierOrder"};
                LP<?>[] orderProperties = purchaseInvoiceAgreementLM.findProperties("date[Purchase.Order]", "number[Purchase.Order]", "idExternalStock[Order.Order]");
                for (int i = 0; i < orderProperties.length; i++) {
                    orderQuery.addProperty(orderNames[i], orderProperties[i].getExpr(orderExpr));
                }
                String[] orderDetailNames = new String[]{"idBarcodeSkuOrderDetail", "nameSkuOrderDetail", "priceOrderDetail",
                        "quantityOrderDetail", "minDeviationQuantityOrderDetail", "maxDeviationQuantityOrderDetail",
                        "minDeviationPriceOrderDetail", "maxDeviationPriceOrderDetail"};
                LP<?>[] orderDetailProperties = purchaseInvoiceAgreementLM.findProperties("idBarcodeSku[Purchase.OrderDetail]", "nameSku[Purchase.OrderDetail]", "price[Purchase.OrderDetail]",
                        "quantity[Purchase.OrderDetail]", "minDeviationQuantity[Purchase.OrderDetail]", "maxDeviationQuantity[Purchase.OrderDetail]",
                        "minDeviationPrice[Purchase.OrderDetail]", "maxDeviationPrice[Purchase.OrderDetail]");
                for (int i = 0; i < orderDetailProperties.length; i++) {
                    orderQuery.addProperty(orderDetailNames[i], orderDetailProperties[i].getExpr(orderDetailExpr));
                }
                if (requestExchange.dateFrom != null)
                    orderQuery.and(purchaseInvoiceAgreementLM.findProperty("date[Purchase.Order]").getExpr(orderExpr).compare(
                            new DataObject(requestExchange.dateFrom, DateClass.instance).getExpr(), Compare.GREATER_EQUALS));
                if (requestExchange.dateTo != null)
                    orderQuery.and(purchaseInvoiceAgreementLM.findProperty("date[Purchase.Order]").getExpr(orderExpr).compare(
                            new DataObject(requestExchange.dateTo, DateClass.instance).getExpr(), Compare.LESS_EQUALS));
                if (requestExchange.idStock != null)
                    orderQuery.and(purchaseInvoiceAgreementLM.findProperty("customerStock[Purchase.Order]").getExpr(orderExpr).compare(
                            purchaseInvoiceAgreementLM.findProperty("stock[STRING[100]]").readClasses(session, new DataObject(requestExchange.idStock)).getExpr(), Compare.EQUALS));
                orderQuery.and(purchaseInvoiceAgreementLM.findProperty("order[Purchase.OrderDetail]").getExpr(orderDetailExpr).compare(orderExpr, Compare.EQUALS));
                orderQuery.and(purchaseInvoiceAgreementLM.findProperty("number[Purchase.Order]").getExpr(orderExpr).getWhere());
                orderQuery.and(purchaseInvoiceAgreementLM.findProperty("isOpened[Purchase.Order]").getExpr(orderExpr).getWhere());
                orderQuery.and(purchaseInvoiceAgreementLM.findProperty("idBarcodeSku[Purchase.OrderDetail]").getExpr(orderDetailExpr).getWhere());
                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> orderResult = orderQuery.execute(session);
                for (ImMap<Object, Object> entry : orderResult.values()) {
                    LocalDate dateOrder = (LocalDate) entry.get("dateOrder");
                    String numberOrder = trim((String) entry.get("numberOrder"));
                    String idSupplier = trim((String) entry.get("idSupplierOrder"));
                    String barcode = trim((String) entry.get("idBarcodeSkuOrderDetail"));
                    String name = trim((String) entry.get("nameSkuOrderDetail"));
                    BigDecimal price = (BigDecimal) entry.get("priceOrderDetail");
                    BigDecimal quantity = (BigDecimal) entry.get("quantityOrderDetail");
                    BigDecimal minQuantity = (BigDecimal) entry.get("minDeviationQuantityOrderDetail");
                    BigDecimal maxQuantity = (BigDecimal) entry.get("maxDeviationQuantityOrderDetail");
                    BigDecimal minPrice = (BigDecimal) entry.get("minDeviationPriceOrderDetail");
                    BigDecimal maxPrice = (BigDecimal) entry.get("maxDeviationPriceOrderDetail");
                    terminalOrderList.add(new TerminalOrder(dateOrder, null, numberOrder, idSupplier, barcode, null, name, price,
                            quantity, minQuantity, maxQuantity, minPrice, maxPrice));
                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return terminalOrderList;
    }

    public static List<MachineryInfo> readMachineryInfo(EquipmentServer server, String sidEquipmentServer) throws SQLException {
        List<MachineryInfo> machineryInfoList = new ArrayList<>();
        if (equLM != null) {
            try (DataSession session = server.createSession()) {

                KeyExpr machineryExpr = new KeyExpr("machinery");
                QueryBuilder<Object, Object> query = new QueryBuilder<>(MapFact.singletonRev("machinery", machineryExpr));

                String[] machineryNames = new String[]{"nppMachinery", "portMachinery", "overDirectoryMachinery", "nppGroupMachinery", "handlerModelMachinery"};
                LP[] machineryProperties = equLM.findProperties("npp[Machinery]", "port[Machinery]", "overDirectory[Machinery]", "nppGroupMachinery[Machinery]", "handlerModel[Machinery]");
                for (int i = 0; i < machineryProperties.length; i++) {
                    query.addProperty(machineryNames[i], machineryProperties[i].getExpr(machineryExpr));
                }

                query.and(equLM.findProperty("filter[STRING, Machinery]").getExpr(new DataObject(sidEquipmentServer).getExpr(), machineryExpr).getWhere());

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);

                for (ImMap<Object, Object> row : result.values()) {
                    machineryInfoList.add(new MachineryInfo(true, false, false, (Integer) row.get("nppGroupMachinery"), (Integer) row.get("nppMachinery"),
                            (String) row.get("handlerModelMachinery"), trim((String) row.get("portMachinery")),
                            trim((String) row.get("overDirectoryMachinery"))));
                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return machineryInfoList;
    }

    private static Long parseLong(String value) {
        try {
            if(value != null)
                return Long.parseLong(value);
        } catch(Exception ignored) {
        }
        return null;
    }

    private static String getRowValue(ImMap<Object, Object> row, String key) {
        return trim((String) row.get(key));
    }

}