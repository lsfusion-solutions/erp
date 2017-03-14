package equ.srv;

import com.google.common.base.Throwables;
import equ.api.cashregister.DiscountCard;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.Compare;
import lsfusion.server.classes.LongClass;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.DBManager;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.math.BigDecimal;
import java.rmi.RemoteException;
import java.sql.Date;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.trim;

public class MachineryExchangeEquipmentServer {

    static ScriptingLogicsModule discountCardLM;

    public static void init(BusinessLogics BL) {
        discountCardLM = BL.getModule("DiscountCard");
    }

    public static List<DiscountCard> readDiscountCardList(DBManager dbManager, String numberDiscountCardFrom, String numberDiscountCardTo) throws RemoteException, SQLException {
        List<DiscountCard> discountCardList = new ArrayList<>();
        if(discountCardLM != null) {
            try (DataSession session = dbManager.createSession()) {

                KeyExpr discountCardExpr = new KeyExpr("discountCard");
                ImRevMap<Object, KeyExpr> discountCardKeys = MapFact.singletonRev((Object) "discountCard", discountCardExpr);

                QueryBuilder<Object, Object> discountCardQuery = new QueryBuilder<>(discountCardKeys);
                String[] discountCardNames = new String[]{"idDiscountCard", "numberDiscountCard", "nameDiscountCard",
                        "percentDiscountCard", "dateDiscountCard", "dateToDiscountCard", "initialSumDiscountCard",
                        "typeDiscountCard", "firstNameContact", "lastNameContact", "middleNameContact", "birthdayContact",
                        "sexContact", "cityContact", "streetContact", "phoneContact", "emailContact", "agreeSubscribeContact"};
                LCP[] discountCardProperties = discountCardLM.findProperties("id[DiscountCard]", "number[DiscountCard]", "name[DiscountCard]",
                        "percent[DiscountCard]", "date[DiscountCard]", "dateTo[DiscountCard]", "initialSum[DiscountCard]",
                        "type[DiscountCard]", "firstNameHttpServerContact[DiscountCard]", "lastNameHttpServerContact[DiscountCard]",
                        "middleNameHttpServerContact[DiscountCard]", "birthdayHttpServerContact[DiscountCard]", "numberSexHttpServerContact[DiscountCard]",
                        "cityHttpServerContact[DiscountCard]", "streetHttpServerContact[DiscountCard]", "phoneHttpServerContact[DiscountCard]",
                        "emailHttpServerContact[DiscountCard]", "agreeSubscribeHttpServerContact[DiscountCard]");
                for (int i = 0; i < discountCardProperties.length; i++) {
                    discountCardQuery.addProperty(discountCardNames[i], discountCardProperties[i].getExpr(discountCardExpr));
                }
                discountCardQuery.and(discountCardLM.findProperty("number[DiscountCard]").getExpr(discountCardExpr).getWhere());
                discountCardQuery.and(discountCardLM.findProperty("skipLoad[DiscountCard]").getExpr(discountCardExpr).getWhere().not());
                Long numberFrom = parseLong(numberDiscountCardFrom);
                if (numberFrom != null)
                    discountCardQuery.and(discountCardLM.findProperty("longNumber[DiscountCard]").getExpr(discountCardExpr).compare(new DataObject(numberFrom, LongClass.instance).getExpr(), Compare.GREATER_EQUALS));
                Long numberTo = parseLong(numberDiscountCardTo);
                if (numberTo != null)
                    discountCardQuery.and(discountCardLM.findProperty("longNumber[DiscountCard]").getExpr(discountCardExpr).compare(new DataObject(numberTo, LongClass.instance).getExpr(), Compare.LESS_EQUALS));

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> discountCardResult = discountCardQuery.execute(session, MapFact.singletonOrder((Object) "idDiscountCard", false));

                for (int i = 0, size = discountCardResult.size(); i < size; i++) {
                    ImMap<Object, Object> row = discountCardResult.getValue(i);

                    String idDiscountCard = getRowValue(row, "idDiscountCard");
                    if (idDiscountCard == null)
                        idDiscountCard = String.valueOf(discountCardResult.getKey(i).get("discountCard"));
                    String numberDiscountCard = getRowValue(row, "numberDiscountCard");
                    String nameDiscountCard = getRowValue(row, "nameDiscountCard");
                    BigDecimal percentDiscountCard = (BigDecimal) row.get("percentDiscountCard");
                    BigDecimal initialSumDiscountCard = (BigDecimal) row.get("initialSumDiscountCard");
                    Date dateFromDiscountCard = (Date) row.get("dateDiscountCard");
                    Date dateToDiscountCard = (Date) row.get("dateToDiscountCard");
                    String typeDiscountCard = (String) row.get("typeDiscountCard");
                    String firstNameContact = (String) row.get("firstNameContact");
                    String lastNameContact = (String) row.get("lastNameContact");
                    String middleNameContact = (String) row.get("middleNameContact");
                    Date birthdayContact = (Date) row.get("birthdayContact");
                    Integer sexContact = (Integer) row.get("sexContact");
                    String cityContact = (String) row.get("cityContact");
                    String streetContact = (String) row.get("streetContact");
                    String phoneContact = (String) row.get("phoneContact");
                    String emailContact = (String) row.get("emailContact");
                    boolean agreeSubscribeContact = row.get("agreeSubscribeContact") != null;
                    discountCardList.add(new DiscountCard(idDiscountCard, numberDiscountCard, nameDiscountCard,
                            percentDiscountCard, initialSumDiscountCard, dateFromDiscountCard, dateToDiscountCard,
                            typeDiscountCard, firstNameContact, lastNameContact, middleNameContact, birthdayContact,
                            sexContact, cityContact, streetContact, phoneContact, emailContact, agreeSubscribeContact, true));
                }
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
        return discountCardList;
    }

    private static Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch(Exception e) {
            return null;
        }
    }

    private static String getRowValue(ImMap<Object, Object> row, String key) {
        return trim((String) row.get(key));
    }

}