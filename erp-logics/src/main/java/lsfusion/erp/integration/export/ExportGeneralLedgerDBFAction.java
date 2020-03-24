package lsfusion.erp.integration.export;

import com.google.common.base.Throwables;
import com.hexiong.jdbf.DBFWriter;
import com.hexiong.jdbf.JDBFException;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.base.file.IOUtils;
import lsfusion.base.file.RawFileData;
import lsfusion.base.file.WriteClientAction;
import lsfusion.erp.integration.DefaultExportAction;
import lsfusion.erp.integration.OverJDBField;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.interop.form.property.Compare;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.data.where.Where;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

public class ExportGeneralLedgerDBFAction extends DefaultExportAction {
    String charset = "cp1251";

    public ExportGeneralLedgerDBFAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {
        try {
            ObjectValue dateFrom = findProperty("dateFromExportGeneralLedgerDBF[]").readClasses(context);
            ObjectValue dateTo = findProperty("dateToExportGeneralLedgerDBF[]").readClasses(context);
            ObjectValue legalEntity = findProperty("legalEntityExportGeneralLedgerDBF[]").readClasses(context);
            ObjectValue glAccountType = findProperty("GLAccountTypeExportGeneralLedgerDBF[]").readClasses(context);

            File file = exportGeneralLedgers(context, dateFrom, dateTo, legalEntity, glAccountType);
            if (file != null) {
                context.delayUserInterfaction(new WriteClientAction(new RawFileData(file), "export", "dbf", false, true));
                if(!file.delete())
                    file.deleteOnExit();
            }
            else
                context.delayUserInterfaction(new MessageClientAction("По заданным параметрам не найдено ни одной проводки", "Ошибка"));
            
        } catch (IOException | SQLException | JDBFException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }

    private File exportGeneralLedgers(ExecutionContext<ClassPropertyInterface> context, ObjectValue dateFrom, ObjectValue dateTo, ObjectValue legalEntity, ObjectValue glAccountType)
            throws JDBFException, ScriptingErrorLog.SemanticErrorException, IOException, SQLException, SQLHandledException {

        OverJDBField[] fields = {

                new OverJDBField("D_VV", 'D', 8, 0), new OverJDBField("DOK", 'C', 8, 0),
                new OverJDBField("VNDOK", 'C', 8, 0), new OverJDBField("TEXTPR", 'C', 60, 0),
                new OverJDBField("K_OP", 'C', 3, 0), new OverJDBField("K_SCHD", 'C', 5, 0),
                new OverJDBField("K_SCHK", 'C', 5, 0), //7

                new OverJDBField("K_ANAD1", 'C', 100, 0), new OverJDBField("K_ANAD2", 'C', 100, 0),
                new OverJDBField("K_ANAD3", 'C', 100, 0), new OverJDBField("K_ANAD4", 'C', 100, 0),
                new OverJDBField("K_ANAK1", 'C', 100, 0), new OverJDBField("K_ANAK2", 'C', 100, 0),
                new OverJDBField("K_ANAK3", 'C', 100, 0), new OverJDBField("K_ANAK4", 'C', 100, 0), //15

                new OverJDBField("N_SUM", 'N', 17, 2),
                new OverJDBField("K_MAT", 'C', 12, 0), new OverJDBField("N_MAT", 'N', 17, 3),
                new OverJDBField("N_DSUM", 'N', 15, 2), new OverJDBField("KOD_ISP", 'C', 2, 0),
                new OverJDBField("P_AVT", 'C', 3, 0), new OverJDBField("SER_P", 'C', 2, 0) //22
        };

        boolean useNotDenominatedSum = findProperty("useNotDenominatedSum[]").read(context) != null;

        KeyExpr generalLedgerExpr = new KeyExpr("GeneralLedger");
        KeyExpr dimensionTypeExpr = new KeyExpr("DimensionType");

        ImRevMap<Object, KeyExpr> generalLedgerKeys = MapFact.toRevMap("GeneralLedger", generalLedgerExpr, "DimensionType", dimensionTypeExpr);

        QueryBuilder<Object, Object> generalLedgerQuery = new QueryBuilder<>(generalLedgerKeys);


        String[] generalLedgerNames = new String[]{"dateGeneralLedger", "numberGLDocument", "seriesGLDocument",
                "descriptionGeneralLedger", "idDebitGeneralLedger", "idCreditGeneralLedger", "sumGeneralLedger",
                "quantityGeneralLedger", "idOperationGeneralLedger"};
        LP<?>[] generalLedgerProperties = findProperties("date[GeneralLedger]", "numberGLDocument[GeneralLedger]", "seriesGLDocument[GeneralLedger]",
                "description[GeneralLedger]", "idDebit[GeneralLedger]", "idCredit[GeneralLedger]", "sum[GeneralLedger]",
                "quantity[GeneralLedger]", "idOperation[GeneralLedger]");
        for (int j = 0; j < generalLedgerProperties.length; j++) {
            generalLedgerQuery.addProperty(generalLedgerNames[j], generalLedgerProperties[j].getExpr(generalLedgerExpr));
        }


        String[] dimensionTypeNames = new String[]{"idDebitGeneralLedgerDimensionType", "orderDebitGeneralLedgerDimensionType",
                "idCreditGeneralLedgerDimensionType", "orderCreditGeneralLedgerDimensionType"};
        LP<?>[] dimensionTypeProperties = findProperties("idDebit[GeneralLedger,DimensionType]", "orderDebit[GeneralLedger,DimensionType]",
                "idCredit[GeneralLedger,DimensionType]", "orderCredit[GeneralLedger,DimensionType]");
        for (int j = 0; j < dimensionTypeProperties.length; j++) {
            generalLedgerQuery.addProperty(dimensionTypeNames[j], dimensionTypeProperties[j].getExpr(generalLedgerExpr, dimensionTypeExpr));
        }

        generalLedgerQuery.and(findProperty("isPosted[GeneralLedger]").getExpr(generalLedgerExpr).getWhere());
        generalLedgerQuery.and(findProperty("sum[GeneralLedger]").getExpr(generalLedgerExpr).getWhere());
        generalLedgerQuery.and(findProperty("name[DimensionType]").getExpr(dimensionTypeExpr).getWhere());
        
        if(glAccountType instanceof DataObject) {
            Where where1 = findProperty("glAccountTypeDebit[GeneralLedger]").getExpr(generalLedgerExpr).compare((DataObject) glAccountType, Compare.EQUALS);
            Where where2 = findProperty("glAccountTypeCredit[GeneralLedger]").getExpr(generalLedgerExpr).compare((DataObject) glAccountType, Compare.EQUALS);
            generalLedgerQuery.and(where1.or(where2));
        }
        if(legalEntity instanceof DataObject)
            generalLedgerQuery.and(findProperty("legalEntity[GeneralLedger]").getExpr(generalLedgerExpr).compare((DataObject) legalEntity, Compare.EQUALS));
        if (dateFrom instanceof DataObject)
            generalLedgerQuery.and(findProperty("date[GeneralLedger]").getExpr(generalLedgerExpr).compare((DataObject) dateFrom, Compare.GREATER_EQUALS));
        if (dateTo instanceof DataObject)
            generalLedgerQuery.and(findProperty("date[GeneralLedger]").getExpr(generalLedgerExpr).compare((DataObject) dateTo, Compare.LESS_EQUALS));

        ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> generalLedgerResult = generalLedgerQuery.executeClasses(context);

        if (generalLedgerResult.size() == 0)
            return null;

        Map<DataObject, List<Object>> generalLedgerMap = new HashMap<>();
        Map<DataObject, String> debit1Map = new HashMap<>(); //K_ANAD1
        Map<DataObject, String> debit2Map = new HashMap<>();  //K_ANAD2
        Map<DataObject, String> debit3Map = new HashMap<>();   //K_ANAD3
        Map<DataObject, String> debit4Map = new HashMap<>();   //K_ANAD4
        Map<DataObject, String> credit1Map = new HashMap<>(); //K_ANAK1
        Map<DataObject, String> credit2Map = new HashMap<>();  //K_ANAK2
        Map<DataObject, String> credit3Map = new HashMap<>();  //K_ANAK3
        Map<DataObject, String> credit4Map = new HashMap<>();  //K_ANAK4

        for (int i = 0, size = generalLedgerResult.size(); i < size; i++) {

            DataObject generalLedgerObject = generalLedgerResult.getKey(i).get("GeneralLedger");

            ImMap<Object, ObjectValue> resultValues = generalLedgerResult.getValue(i);

            LocalDate dateGeneralLedger = getLocalDate(resultValues.get("dateGeneralLedger").getValue()); //D_VV
            String numberGeneralLedger = trim((String) resultValues.get("numberGLDocument").getValue(), 8); //DOK
            String seriesGeneralLedger = trim((String) resultValues.get("seriesGLDocument").getValue(), 2); //SER_P

            String description = trim((String) resultValues.get("descriptionGeneralLedger").getValue(), 60); //TEXTPR
            String idDebit = (String) resultValues.get("idDebitGeneralLedger").getValue();   //K_SCHD
            idDebit = idDebit == null ? null : trim(idDebit.replace(".", ""), 5);
            String idCredit = (String) resultValues.get("idCreditGeneralLedger").getValue(); //K_SCHK
            idCredit = idCredit == null ? null : trim(idCredit.replace(".", ""), 5);

            BigDecimal sumGeneralLedger = (BigDecimal) resultValues.get("sumGeneralLedger").getValue(); //N_SUM
            if(useNotDenominatedSum)
                sumGeneralLedger = safeMultiply(sumGeneralLedger, 10000);
            String idOperationGeneralLedger = trim((String) resultValues.get("idOperationGeneralLedger").getValue(), 3); //K_OP

            BigDecimal quantityGeneralLedger = (BigDecimal) resultValues.get("quantityGeneralLedger").getValue(); //N_MAT

            String nameDebit = (String) resultValues.get("idDebitGeneralLedgerDimensionType").getValue();
            Integer orderDebit = (Integer) resultValues.get("orderDebitGeneralLedgerDimensionType").getValue();
            if (orderDebit != null) {
                if (orderDebit == 1)
                    debit1Map.put(generalLedgerObject, nameDebit);
                else if (orderDebit == 2)
                    debit2Map.put(generalLedgerObject, nameDebit);
                else if (orderDebit == 3)
                    debit3Map.put(generalLedgerObject, nameDebit);
                else if(orderDebit == 4)
                    debit4Map.put(generalLedgerObject, nameDebit);
            }
            String nameCredit = (String) resultValues.get("idCreditGeneralLedgerDimensionType").getValue();
            Integer orderCredit = (Integer) resultValues.get("orderCreditGeneralLedgerDimensionType").getValue();
            if (orderCredit != null) {
                if (orderCredit == 1)
                    credit1Map.put(generalLedgerObject, nameCredit);
                else if (orderCredit == 2)
                    credit2Map.put(generalLedgerObject, nameCredit);
                else if (orderCredit == 3)
                    credit3Map.put(generalLedgerObject, nameCredit);
                else if (orderCredit == 4)
                    credit4Map.put(generalLedgerObject, nameCredit);
            }

            generalLedgerMap.put(generalLedgerObject, Arrays.asList(dateGeneralLedger, numberGeneralLedger,
                    description, idDebit, idCredit, sumGeneralLedger, idOperationGeneralLedger, seriesGeneralLedger, quantityGeneralLedger));
        }

        File dbfFile = File.createTempFile("export", ".dbf");
        DBFWriter dbfwriter = new DBFWriter(dbfFile.getAbsolutePath(), fields, charset);

        List<GeneralLedger> generalLedgerList = new ArrayList<>();
        for (Map.Entry<DataObject, List<Object>> entry : generalLedgerMap.entrySet()) {
            DataObject key = entry.getKey();
            List<Object> values = entry.getValue();
            generalLedgerList.add(new GeneralLedger((LocalDate) values.get(0), (String) values.get(1), (String) values.get(7), (String) values.get(2),
                    (String) values.get(6), (String) values.get(3), (String) values.get(4),
                    debit1Map.get(key), debit2Map.get(key), debit3Map.get(key), debit4Map.get(key),
                    credit1Map.get(key), credit2Map.get(key), credit3Map.get(key), credit4Map.get(key),
                    (BigDecimal) values.get(8), (BigDecimal) values.get(5)));
        }
        
        generalLedgerList.sort(COMPARATOR);

        for(GeneralLedger gl : generalLedgerList) {
        dbfwriter.addRecord(new Object[]{localDateToSqlDate(gl.dateGeneralLedger), gl.numberGeneralLedger, null, gl.descriptionGeneralLedger, //4
                gl.idOperationGeneralLedger, gl.idDebitGeneralLedger, gl.idCreditGeneralLedger, //7
                gl.anad1, gl.anad2, gl.anad3, gl.anad4, gl.anak1, gl.anak2, gl.anak3, gl.anak4, //15,
                gl.sumGeneralLedger, null, gl.quantityGeneralLedger, 0, "00", "TMC", gl.seriesGeneralLedger}); //22
        }
        dbfwriter.close();

        byte[] bytes = IOUtils.getFileBytes(dbfFile);
        if(bytes.length > 29)
            bytes[29] = getCharsetByte(charset);
        FileUtils.writeByteArrayToFile(dbfFile, bytes);

        return dbfFile;
    }

    private byte getCharsetByte(String charset) {
        switch (charset) {
            case "cp866":
                return (byte) 0x65;
            case "cp1251":
                return (byte) 0xC9;
            default:
                return (byte) 0x00;
        }
    }

    private static Comparator<GeneralLedger> COMPARATOR = (g1, g2) -> {
        int result = g1.dateGeneralLedger == null ? (g2.dateGeneralLedger == null ? 0 : -1) : 
                (g2.dateGeneralLedger == null ? 1 : g1.dateGeneralLedger.compareTo(g2.dateGeneralLedger));
        if (result == 0)
            result = g1.numberGeneralLedger == null ? (g2.numberGeneralLedger == null ? 0 : -1) : 
                    (g2.numberGeneralLedger == null ? 1 : g1.numberGeneralLedger.compareTo(g2.numberGeneralLedger));
        return result;
    };
}
