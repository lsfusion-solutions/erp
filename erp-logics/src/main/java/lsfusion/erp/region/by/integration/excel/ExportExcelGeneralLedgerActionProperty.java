package lsfusion.erp.region.by.integration.excel;

import com.google.common.base.Throwables;
import jxl.write.WriteException;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.server.classes.DateClass;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.*;

public class ExportExcelGeneralLedgerActionProperty extends ExportExcelActionProperty {
    private final ClassPropertyInterface dateFromInterface;
    private final ClassPropertyInterface dateToInterface;

    public ExportExcelGeneralLedgerActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        dateFromInterface = i.next();
        dateToInterface = i.next();
    }

    public ExportExcelGeneralLedgerActionProperty(ScriptingLogicsModule LM, ClassPropertyInterface dateFrom, ClassPropertyInterface dateTo) {
        super(LM, DateClass.instance, DateClass.instance);

        dateFromInterface = dateFrom;
        dateToInterface = dateTo;
    }

    @Override
    public Map<String, byte[]> createFile(ExecutionContext<ClassPropertyInterface> context) throws IOException, WriteException {
        return createFile("exportGeneralLedger", getTitles(), getRows(context));

    }

    private List<String> getTitles() {
        return Arrays.asList("Проведён", "Дата", "Компания", "Регистр-основание", "Описание", "Дебет", "Субконто (дебет)",
                "Кредит", "Субконто (кредит)", "Сумма");
    }

    private List<List<String>> getRows(ExecutionContext<ClassPropertyInterface> context) {

        List<List<String>> data = new ArrayList<List<String>>();

        DataSession session = context.getSession();

        try {

            DataObject dateFromObject = context.getDataKeyValue(dateFromInterface);
            DataObject dateToObject = context.getDataKeyValue(dateToInterface);

            KeyExpr generalLedgerExpr = new KeyExpr("GeneralLedger");
            ImRevMap<Object, KeyExpr> generalLedgerKeys = MapFact.singletonRev((Object) "GeneralLedger", generalLedgerExpr);

            String[] generalLedgerNames = new String[]{"isPostedGeneralLedger", "dateGeneralLedger",
                    "nameLegalEntityGeneralLedger", "nameGLDocumentGeneralLedger", "descriptionGeneralLedger",
                    "idDebitGeneralLedger", "dimensionsDebitGeneralLedger", "idCreditGeneralLedger",
                    "dimensionsCreditGeneralLedger", "sumGeneralLedger"};
            LCP[] generalLedgerProperties = findProperties("isPostedGeneralLedger", "dateGeneralLedger",
                    "nameLegalEntityGeneralLedger", "nameGLDocumentGeneralLedger", "descriptionGeneralLedger",
                    "idDebitGeneralLedger", "dimensionsDebitGeneralLedger", "idCreditGeneralLedger",
                    "dimensionsCreditGeneralLedger", "sumGeneralLedger");
            QueryBuilder<Object, Object> generalLedgerQuery = new QueryBuilder<Object, Object>(generalLedgerKeys);
            for (int i = 0; i < generalLedgerProperties.length; i++) {
                generalLedgerQuery.addProperty(generalLedgerNames[i], generalLedgerProperties[i].getExpr(context.getModifier(), generalLedgerExpr));
            }

            generalLedgerQuery.and(findProperty("sumGeneralLedger").getExpr(context.getModifier(), generalLedgerQuery.getMapExprs().get("GeneralLedger")).getWhere());

            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> generalLedgerResult = generalLedgerQuery.execute(session);

            for (ImMap<Object, Object> generalLedgerValue : generalLedgerResult.values()) {

                Date date = (Date) generalLedgerValue.get("dateGeneralLedger");

                if ((dateFromObject.object == null || ((Date) dateFromObject.object).getTime() <= date.getTime()) && (dateToObject.object == null || ((Date) dateToObject.object).getTime() >= date.getTime())) {

                    String isPostedGeneralLedger = generalLedgerValue.get("isPostedGeneralLedger") == null ? "FALSE" : "TRUE";
                    String dateGeneralLedger = new SimpleDateFormat("dd.MM.yyyy").format(date);
                    String nameLegalEntity = trim((String) generalLedgerValue.get("nameLegalEntityGeneralLedger"), "");
                    String nameGLDocument = trim((String) generalLedgerValue.get("nameGLDocumentGeneralLedger"), "");
                    String description = trim((String) generalLedgerValue.get("descriptionGeneralLedger"), "");
                    String idDebit = trim((String) generalLedgerValue.get("idDebitGeneralLedger"), "");
                    String dimensionsDebit = trim((String) generalLedgerValue.get("dimensionsDebitGeneralLedger"), "");
                    String idCredit = trim((String) generalLedgerValue.get("idCreditGeneralLedger"), "");
                    String dimensionsCredit = trim((String) generalLedgerValue.get("dimensionsCreditGeneralLedger"), "");
                    BigDecimal sumGeneralLedger = (BigDecimal) generalLedgerValue.get("sumGeneralLedger");


                    data.add(Arrays.asList(isPostedGeneralLedger, dateGeneralLedger, nameLegalEntity, nameGLDocument, 
                            description, idDebit, dimensionsDebit, idCredit, dimensionsCredit, formatValue(sumGeneralLedger)));
                }
            }

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }


        return data;
    }




}