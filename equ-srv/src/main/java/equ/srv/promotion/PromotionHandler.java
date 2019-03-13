package equ.srv.promotion;

import com.google.common.base.Throwables;
import equ.api.cashregister.*;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.remote.RmiServer;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.LogicsInstance;
import lsfusion.server.language.linear.LCP;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;
import org.springframework.beans.factory.InitializingBean;

import java.math.BigDecimal;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.sql.Time;
import java.util.ArrayList;
import java.util.List;

import static lsfusion.base.BaseUtils.trim;

public class PromotionHandler extends RmiServer implements PromotionInterface, InitializingBean {

    @Override
    public LogicsInstance getLogicsInstance() {
        return logicsInstance;
    }

    private LogicsInstance logicsInstance;

    public void setLogicsInstance(LogicsInstance logicsInstance) {
        this.logicsInstance = logicsInstance;
    }

    @Override
    public PromotionInfo readPromotionInfo() throws RemoteException, SQLException {
        try {

            BusinessLogics BL = logicsInstance.getBusinessLogics();
            ScriptingLogicsModule HTCPromotionLM = BL.getModule("HTCPromotion");
            
            if (HTCPromotionLM != null) {

                try (DataSession session = createSession()) {

                    //PromotionTime
                    KeyExpr htcPromotionTimeExpr = new KeyExpr("htcPromotionTime");
                    ImRevMap<Object, KeyExpr> htcPromotionTimeKeys = MapFact.singletonRev((Object) "htcPromotionTime", htcPromotionTimeExpr);
                    QueryBuilder<Object, Object> htcPromotionTimeQuery = new QueryBuilder<>(htcPromotionTimeKeys);

                    String[] htcPromotionTimeNames = new String[]{"isStopHTCPromotionTime", "captionDayHTCPromotionTime",
                            "numberDayHTCPromotionTime", "beginTimeHTCPromotionTime", "endTimeHTCPromotionTime", "percentHTCPromotionTime"};
                    LCP[] htcPromotionTimeProperties = HTCPromotionLM.findProperties("isStop[HTCPromotionTime]", "captionDay[HTCPromotionTime]",
                            "numberDay[HTCPromotionTime]", "beginTime[HTCPromotionTime]", "endTime[HTCPromotionTime]", "percent[HTCPromotionTime]");
                    for (int i = 0; i < htcPromotionTimeProperties.length; i++) {
                        htcPromotionTimeQuery.addProperty(htcPromotionTimeNames[i], htcPromotionTimeProperties[i].getExpr(htcPromotionTimeExpr));
                    }
                    htcPromotionTimeQuery.and(HTCPromotionLM.findProperty("percent[HTCPromotionTime]").getExpr(htcPromotionTimeExpr).getWhere());

                    ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> htcPromotionTimeResult = htcPromotionTimeQuery.execute(session);

                    List<PromotionTime> htcPromotionTimeList = new ArrayList<>();
                    for (int i = 0, size = htcPromotionTimeResult.size(); i < size; i++) {
                        ImMap<Object, Object> entryValue = htcPromotionTimeResult.getValue(i);
                        boolean isStopHTCPromotionTime = entryValue.get("isStopHTCPromotionTime") != null;
                        String captionDayHTCPromotionTime = trim((String) entryValue.get("captionDayHTCPromotionTime"));
                        Integer numberDayHTCPromotionTime = (Integer) entryValue.get("numberDayHTCPromotionTime");
                        Time beginTimeHTCPromotionTime = (Time) entryValue.get("beginTimeHTCPromotionTime");
                        Time endTimeHTCPromotionTime = (Time) entryValue.get("endTimeHTCPromotionTime");
                        BigDecimal percentHTCPromotionTime = (BigDecimal) entryValue.get("percentHTCPromotionTime");
                        htcPromotionTimeList.add(new PromotionTime(isStopHTCPromotionTime, null, captionDayHTCPromotionTime,
                                numberDayHTCPromotionTime, beginTimeHTCPromotionTime, endTimeHTCPromotionTime, percentHTCPromotionTime));
                    }

                    //PromotionQuantity
                    KeyExpr htcPromotionQuantityExpr = new KeyExpr("htcPromotionQuantity");
                    ImRevMap<Object, KeyExpr> htcPromotionQuantityKeys = MapFact.singletonRev((Object) "htcPromotionQuantity", htcPromotionQuantityExpr);
                    QueryBuilder<Object, Object> htcPromotionQuantityQuery = new QueryBuilder<>(htcPromotionQuantityKeys);

                    String[] htcPromotionQuantityNames = new String[]{"isStopHTCPromotionQuantity", "barcodeItemHTCPromotionQuantity",
                            "quantityHTCPromotionQuantity", "percentHTCPromotionQuantity"};
                    LCP[] htcPromotionQuantityProperties = HTCPromotionLM.findProperties("isStop[HTCPromotionQuantity]", "barcodeItem[HTCPromotionQuantity]",
                            "quantity[HTCPromotionQuantity]", "percent[HTCPromotionQuantity]");
                    for (int i = 0; i < htcPromotionQuantityProperties.length; i++) {
                        htcPromotionQuantityQuery.addProperty(htcPromotionQuantityNames[i], htcPromotionQuantityProperties[i].getExpr(htcPromotionQuantityExpr));
                    }
                    htcPromotionQuantityQuery.and(HTCPromotionLM.findProperty("percent[HTCPromotionQuantity]").getExpr(htcPromotionQuantityExpr).getWhere());

                    ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> htcPromotionQuantityResult = htcPromotionQuantityQuery.execute(session);

                    List<PromotionQuantity> htcPromotionQuantityList = new ArrayList<>();
                    for (int i = 0, size = htcPromotionQuantityResult.size(); i < size; i++) {
                        ImMap<Object, Object> entryValue = htcPromotionQuantityResult.getValue(i);
                        boolean isStopHTCPromotionQuantity = entryValue.get("isStopHTCPromotionQuantity") != null;
                        String barcodeItemHTCPromotionQuantity = trim((String) entryValue.get("barcodeItemHTCPromotionQuantity"));
                        BigDecimal quantityHTCPromotionQuantity = (BigDecimal) entryValue.get("quantityHTCPromotionQuantity");
                        BigDecimal percentHTCPromotionQuantity = (BigDecimal) entryValue.get("percentHTCPromotionQuantity");
                        htcPromotionQuantityList.add(new PromotionQuantity(isStopHTCPromotionQuantity, null,
                                null, barcodeItemHTCPromotionQuantity, quantityHTCPromotionQuantity, percentHTCPromotionQuantity));
                    }


                    //PromotionSum
                    KeyExpr htcPromotionSumExpr = new KeyExpr("htcPromotionSum");
                    ImRevMap<Object, KeyExpr> htcPromotionSumKeys = MapFact.singletonRev((Object) "htcPromotionSum", htcPromotionSumExpr);
                    QueryBuilder<Object, Object> htcPromotionSumQuery = new QueryBuilder<>(htcPromotionSumKeys);

                    String[] htcPromotionSumNames = new String[]{"isStopHTCPromotionSum", "sumHTCPromotionSum", "percentHTCPromotionSum"};
                    LCP[] htcPromotionSumProperties = HTCPromotionLM.findProperties("isStop[HTCPromotionSum]", "sum[HTCPromotionSum]", "percent[HTCPromotionSum]");
                    for (int i = 0; i < htcPromotionSumProperties.length; i++) {
                        htcPromotionSumQuery.addProperty(htcPromotionSumNames[i], htcPromotionSumProperties[i].getExpr(htcPromotionSumExpr));
                    }
                    htcPromotionSumQuery.and(HTCPromotionLM.findProperty("percent[HTCPromotionSum]").getExpr(htcPromotionSumExpr).getWhere());

                    ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> htcPromotionSumResult = htcPromotionSumQuery.execute(session);

                    List<PromotionSum> htcPromotionSumList = new ArrayList<>();
                    for (int i = 0, size = htcPromotionSumResult.size(); i < size; i++) {
                        ImMap<Object, Object> entryValue = htcPromotionSumResult.getValue(i);
                        boolean isStopHTCPromotionSum = entryValue.get("isStopHTCPromotionSum") != null;
                        BigDecimal sumHTCPromotionSum = (BigDecimal) entryValue.get("sumHTCPromotionSum");
                        BigDecimal percentHTCPromotionSum = (BigDecimal) entryValue.get("percentHTCPromotionSum");
                        htcPromotionSumList.add(new PromotionSum(isStopHTCPromotionSum, null, sumHTCPromotionSum, percentHTCPromotionSum));
                    }

                    return new PromotionInfo(htcPromotionTimeList, htcPromotionQuantityList, htcPromotionSumList);
                }
            } else return null;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
    
    @Override
    public void afterPropertiesSet() throws Exception {
    }

    @Override
    public String getEventName() {
        return "promotion-handler";
    }
}