package lsfusion.erp.region.by.integration.excel;

import com.google.common.base.Throwables;
import jxl.write.WriteException;
import lsfusion.base.Pair;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.base.file.RawFileData;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.classes.data.time.DateClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class ExportExcelGeneralLedgerAction extends ExportExcelAction {
    private final ClassPropertyInterface dateFromInterface;
    private final ClassPropertyInterface dateToInterface;

    public ExportExcelGeneralLedgerAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        dateFromInterface = i.next();
        dateToInterface = i.next();
    }

    public ExportExcelGeneralLedgerAction(ScriptingLogicsModule LM, ClassPropertyInterface dateFrom, ClassPropertyInterface dateTo) {
        super(LM, DateClass.instance, DateClass.instance);

        dateFromInterface = dateFrom;
        dateToInterface = dateTo;
    }

    @Override
    public Pair<String, RawFileData> createFile(ExecutionContext<ClassPropertyInterface> context) throws IOException, WriteException {
        return Pair.create("exportGeneralLedger", createFile(getTitles(), getRows(context)));

    }

    private List<String> getTitles() {
        return Arrays.asList("Проведён", "Дата", "Компания", "Регистр-основание", "Описание", "Дебет", "Субконто (дебет)",
                "Кредит", "Субконто (кредит)", "Сумма");
    }

    private List<List<String>> getRows(ExecutionContext<ClassPropertyInterface> context) {

        List<List<String>> data = new ArrayList<>();

        try {

            DataObject dateFromObject = context.getDataKeyValue(dateFromInterface);
            DataObject dateToObject = context.getDataKeyValue(dateToInterface);

            KeyExpr generalLedgerExpr = new KeyExpr("GeneralLedger");
            ImRevMap<Object, KeyExpr> generalLedgerKeys = MapFact.singletonRev("GeneralLedger", generalLedgerExpr);

            String[] generalLedgerNames = new String[]{"isPostedGeneralLedger", "dateGeneralLedger",
                    "nameLegalEntityGeneralLedger", "nameGLDocumentGeneralLedger", "descriptionGeneralLedger",
                    "idDebitGeneralLedger", "dimensionsDebitGeneralLedger", "idCreditGeneralLedger",
                    "dimensionsCreditGeneralLedger", "sumGeneralLedger"};
            LP[] generalLedgerProperties = findProperties("isPosted[GeneralLedger]", "date[GeneralLedger]",
                    "nameLegalEntity[GeneralLedger]", "nameGLDocument[GeneralLedger]", "description[GeneralLedger]",
                    "idDebit[GeneralLedger]", "dimensionsDebit[GeneralLedger]", "idCredit[GeneralLedger]",
                    "dimensionsCredit[GeneralLedger]", "sum[GeneralLedger]");
            QueryBuilder<Object, Object> generalLedgerQuery = new QueryBuilder<>(generalLedgerKeys);
            for (int i = 0; i < generalLedgerProperties.length; i++) {
                generalLedgerQuery.addProperty(generalLedgerNames[i], generalLedgerProperties[i].getExpr(context.getModifier(), generalLedgerExpr));
            }

            generalLedgerQuery.and(findProperty("sum[GeneralLedger]").getExpr(context.getModifier(), generalLedgerQuery.getMapExprs().get("GeneralLedger")).getWhere());

            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> generalLedgerResult = generalLedgerQuery.execute(context);

            for (ImMap<Object, Object> generalLedgerValue : generalLedgerResult.values()) {

                LocalDate date = getLocalDate(generalLedgerValue.get("dateGeneralLedger"));

                if ((dateFromObject.object == null || ((LocalDate) dateFromObject.object).compareTo(date) <= 0) && (dateToObject.object == null || ((LocalDate) dateToObject.object).compareTo(date) >= 0)) {

                    String isPostedGeneralLedger = generalLedgerValue.get("isPostedGeneralLedger") == null ? "FALSE" : "TRUE";
                    String dateGeneralLedger = date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
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