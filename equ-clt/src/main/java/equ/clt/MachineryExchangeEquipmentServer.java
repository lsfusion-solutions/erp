package equ.clt;

import equ.api.EquipmentServerInterface;
import equ.api.MachineryHandler;
import equ.api.MachineryInfo;
import equ.api.RequestExchange;
import equ.api.cashregister.*;
import equ.api.terminal.TerminalHandler;
import equ.api.terminal.TerminalOrder;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

public class MachineryExchangeEquipmentServer {
    private final static Logger machineryExchangeLogger = Logger.getLogger("MachineryExchangeLogger");

    static void processMachineryExchange(EquipmentServerInterface remote, String sidEquipmentServer) throws SQLException, IOException {
        machineryExchangeLogger.info("Process MachineryExchange");
        List<MachineryInfo> machineryInfoList = remote.readMachineryInfo(sidEquipmentServer);
        List<RequestExchange> requestExchangeList = remote.readRequestExchange(sidEquipmentServer);

        if (!requestExchangeList.isEmpty()) {

            Map<String, Set<MachineryInfo>> handlerModelMachineryMap = new HashMap<>();
            for (MachineryInfo machinery : machineryInfoList) {
                if (!handlerModelMachineryMap.containsKey(machinery.handlerModel))
                    handlerModelMachineryMap.put(machinery.handlerModel, new HashSet<>());
                handlerModelMachineryMap.get(machinery.handlerModel).add(machinery);
            }

            for (Map.Entry<String, Set<MachineryInfo>> entry : handlerModelMachineryMap.entrySet()) {
                String handlerModel = entry.getKey();
                Set<MachineryInfo> machineryMap = entry.getValue();
                if (handlerModel != null) {
                    try {

                        MachineryHandler clsHandler = (MachineryHandler) EquipmentServer.getHandler(handlerModel, remote);
                        boolean isCashRegisterHandler = clsHandler instanceof CashRegisterHandler;
                        boolean isTerminalHandler = clsHandler instanceof TerminalHandler;

                        for (RequestExchange requestExchange : requestExchangeList) {
                            try {

                                if(isCashRegisterHandler) {

                                    //Cashier
                                    if (requestExchange.isCashier()) {
                                        List<CashierInfo> cashierInfoList = remote.readCashierInfoList();
                                        if (cashierInfoList != null && !cashierInfoList.isEmpty()) {
                                            ((CashRegisterHandler) clsHandler).sendCashierInfoList(cashierInfoList, requestExchange);
                                        }
                                        sendCashierTime(remote, sidEquipmentServer, (CashRegisterHandler) clsHandler, requestExchange, machineryInfoList);
                                        remote.finishRequestExchange(sidEquipmentServer, new HashSet<>(Collections.singletonList(requestExchange.requestExchange)));
                                    }

                                    //DiscountCard
                                    else if (requestExchange.isDiscountCard()) {
                                        Set<String> handlerSet = new HashSet<>();
                                        for(CashRegisterInfo cashRegisterInfo : requestExchange.cashRegisterSet) {
                                            handlerSet.add(cashRegisterInfo.handlerModel);
                                        }
                                        if(handlerSet.contains(handlerModel)) {
                                            List<DiscountCard> discountCardList = remote.readDiscountCardList(requestExchange);
                                            if (discountCardList != null && !discountCardList.isEmpty())
                                                ((CashRegisterHandler) clsHandler).sendDiscountCardList(discountCardList, requestExchange);
                                            remote.finishRequestExchange(sidEquipmentServer, new HashSet<>(Collections.singletonList(requestExchange.requestExchange)));
                                        }
                                    }

                                    //Promotion
                                    else if (requestExchange.isPromotion()) {
                                        PromotionInfo promotionInfo = remote.readPromotionInfo();
                                        if (promotionInfo != null)
                                            ((CashRegisterHandler) clsHandler).sendPromotionInfo(promotionInfo, requestExchange);
                                        remote.finishRequestExchange(sidEquipmentServer, new HashSet<>(Collections.singletonList(requestExchange.requestExchange)));
                                    }
                                }

                                //TerminalOrder
                                else if (requestExchange.isTerminalOrderExchange() && isTerminalHandler) {

                                    for (MachineryInfo machinery : machineryMap) {
                                        List<TerminalOrder> terminalOrderList = remote.readTerminalOrderList(requestExchange);
                                        if (terminalOrderList != null && !terminalOrderList.isEmpty())
                                            ((TerminalHandler) clsHandler).sendTerminalOrderList(terminalOrderList, machinery);
                                        remote.finishRequestExchange(sidEquipmentServer, new HashSet<>(Collections.singletonList(requestExchange.requestExchange)));
                                    }
                                }
                            } catch (Exception e) {
                                machineryExchangeLogger.error("Equipment server error: ", e);
                                remote.errorRequestExchange(requestExchange.requestExchange, e);
                            }
                        }
                    } catch (Throwable e) {
                        machineryExchangeLogger.error("Equipment server error: ", e);
                        EquipmentServer.reportEquipmentServerError(remote, sidEquipmentServer, e);
                        return;
                    }
                }
            }
        }
    }

    private static void sendCashierTime(EquipmentServerInterface remote, String sidEquipmentServer, CashRegisterHandler handler, RequestExchange requestExchange, List<MachineryInfo> cashRegisterInfoList)
            throws IOException, SQLException {
        try {
            List<CashierTime> cashierTimeList = handler.requestCashierTime(requestExchange, cashRegisterInfoList);
            if (cashierTimeList != null && !cashierTimeList.isEmpty()) {
                machineryExchangeLogger.info("Sending cashier time (" + cashierTimeList.size() + ")");
                String result = remote.sendCashierTimeList(cashierTimeList);
                if (result != null)
                    EquipmentServer.reportEquipmentServerError(remote, sidEquipmentServer, result);
            }
        } catch (Exception e) {
            EquipmentServer.reportEquipmentServerError(remote, sidEquipmentServer, e);
        }
    }
}