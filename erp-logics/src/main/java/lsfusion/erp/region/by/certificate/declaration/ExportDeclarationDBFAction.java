package lsfusion.erp.region.by.certificate.declaration;

import com.hexiong.jdbf.DBFWriter;
import com.hexiong.jdbf.JDBFException;
import lsfusion.base.file.IOUtils;
import lsfusion.base.file.RawFileData;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.integration.DefaultExportAction;
import lsfusion.erp.integration.OverJDBField;
import lsfusion.interop.form.property.Compare;
import lsfusion.interop.action.ExportFileClientAction;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.classes.data.file.CustomStaticFormatFileClass;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import org.xBaseJ.DBF;
import org.xBaseJ.fields.Field;
import org.xBaseJ.xBaseJException;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.*;

public class ExportDeclarationDBFAction extends DefaultExportAction {
    private final ClassPropertyInterface declarationInterface;

    private final String DOP_NOMER = "D4035121";

    public ExportDeclarationDBFAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        declarationInterface = i.next();
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {

        try {

            CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(true, true, "Файлы DBF", "dbf");
            ObjectValue objectValue = context.requestUserData(valueClass, null);

            if (objectValue != null) {
            
            DataObject declarationObject = context.getDataKeyValue(declarationInterface);
            Declaration declaration = exportDeclaration(context, declarationObject);
            G44 g44 = exportG44ToList(context, declarationObject);

                Map<String, RawFileData> outputFiles = new HashMap<>();

                Map<String, RawFileData> fileList = valueClass.getMultipleNamedFiles(objectValue.getValue());
                for (Map.Entry<String, RawFileData> entry : fileList.entrySet()) {

                    Map<Field, Object> dbfFields = readDBFFields(entry.getValue());

                    if (entry.getKey().toLowerCase().equals("decl02.dbf") && declaration != null)
                        outputFiles.put(entry.getKey(), new RawFileData(exportDECL02(dbfFields, declaration)));

                    if (entry.getKey().toLowerCase().equals("dobl.dbf") && declaration != null)
                        outputFiles.put(entry.getKey(), new RawFileData(exportDOBL(dbfFields, declaration)));

                    if (entry.getKey().toLowerCase().equals("g18.dbf"))
                        outputFiles.put(entry.getKey(), new RawFileData(exportG18(dbfFields)));

                    if (entry.getKey().toLowerCase().equals("g20.dbf") && declaration != null)
                        outputFiles.put(entry.getKey(), new RawFileData(exportG20(dbfFields, declaration)));

                    if (entry.getKey().toLowerCase().equals("g21.dbf"))
                        outputFiles.put(entry.getKey(), new RawFileData(exportG21(dbfFields)));

                    if (entry.getKey().toLowerCase().equals("g40.dbf"))
                        outputFiles.put(entry.getKey(), new RawFileData(exportG40(dbfFields)));

                    if (entry.getKey().toLowerCase().equals("g44.dbf")) {
                        outputFiles.put(entry.getKey(), new RawFileData(exportG44(dbfFields, g44)));
                    }

                    if (entry.getKey().toLowerCase().equals("g47.dbf") && declaration != null)
                        outputFiles.put(entry.getKey(), new RawFileData(exportG47(dbfFields, declaration)));

                    if (entry.getKey().toLowerCase().equals("g313.dbf") && declaration != null)
                        outputFiles.put(entry.getKey(), new RawFileData(exportG313(dbfFields, declaration)));

                    if (entry.getKey().toLowerCase().equals("gb.dbf"))
                        outputFiles.put(entry.getKey(), new RawFileData(exportGB(dbfFields)));

                    if (entry.getKey().toLowerCase().equals("g316.dbf"))
                        outputFiles.put(entry.getKey(), new RawFileData(exportG316(dbfFields)));

                }
                if(outputFiles.size() > 0)
                    context.delayUserInterfaction(new ExportFileClientAction(outputFiles));
            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException | JDBFException | IOException | xBaseJException | ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private Declaration exportDeclaration(ExecutionContext<ClassPropertyInterface> context, DataObject declarationObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        String numberDeclaration = (String) findProperty("number[Declaration]").read(context, declarationObject);  //GA, NOMER_GTD
        BigDecimal sumDeclaration = (BigDecimal) findProperty("sumDeclarationDetail[Declaration]").read(context, declarationObject);  //G222
        Integer countDeclaration = (Integer) findProperty("countDeclarationDetail[Declaration]").read(context, declarationObject);  //G05
        numberDeclaration = numberDeclaration == null ? null : numberDeclaration.trim();
        String UNPLegalEntityDeclaration = (String) findProperty("UNPLegalEntity[Declaration]").read(context, declarationObject);  //G141
        String fullNameLegalEntityDeclaration = (String) findProperty("fullNameLegalEntity[Declaration]").read(context, declarationObject); //G142
        String addressLegalEntityDeclaration = (String) findProperty("addressLegalEntity[Declaration]").read(context, declarationObject); //G143
        LocalDate dateDeclaration = (LocalDate) findProperty("date[Declaration]").read(context, declarationObject);          //G542

        String[] exportNames = new String[]{"extraNameDeclarationDetail", "markinDeclarationDetail",
                "numberDeclarationDetail", "codeCustomsGroupDeclarationDetail", "sidOrigin2CountryDeclarationDetail",
                "sumGrossWeightDeclarationDetail", "extraComponentsQuantityDeclarationDetail", "sumDeclarationDetail",
                "nameCustomsDeclarationDetail", "quantityDeclarationDetail", "sumNetWeightDeclarationDetail",
                "shortNameUOMDeclarationDetail", "customsCodeUOMDeclarationDetail", "isVATCustomsExceptionDeclarationDetail", "VATCustomsExceptionDeclarationDetail", "homeSumDeclarationDetail",
                "baseVATSumDeclarationDetail", "isWeightDutyDeclarationDetail", "weightDutyDeclarationDetail",
                "percentDutyDeclarationDetail", "percentVATDeclarationDetail", "dutySumDeclarationDetail",
                "VATSumDeclarationDetail", "nameSupplierDeclarationDetail", "nameBrandDeclarationDetail", "nameManufacturerDeclarationDetail"};

        LP<?>[] exportProperties = findProperties("extraName[DeclarationDetail]", "markin[DeclarationDetail]",
                "number[DeclarationDetail]", "codeCustomsGroup[DeclarationDetail]", "sidOrigin2Country[DeclarationDetail]",
                "sumGrossWeight[DeclarationDetail]", "extraComponentsQuantity[DeclarationDetail]", "sum[DeclarationDetail]",
                "nameCustoms[DeclarationDetail]", "quantity[DeclarationDetail]", "sumNetWeight[DeclarationDetail]",
                "shortNameUOM[DeclarationDetail]", "customsCodeUOM[DeclarationDetail]", "isVATCustomsException[DeclarationDetail]", "VATCustomsException[DeclarationDetail]", "homeSum[DeclarationDetail]",
                "baseVATSum[DeclarationDetail]", "isWeightDuty[DeclarationDetail]", "weightDuty[DeclarationDetail]",
                "percentDuty[DeclarationDetail]", "percentVAT[DeclarationDetail]", "dutySum[DeclarationDetail]",
                "VATSum[DeclarationDetail]", "nameSupplier[DeclarationDetail]", "nameBrand[DeclarationDetail]", "nameManufacturer[DeclarationDetail]");

        LP<?> isDeclarationDetail = is(findClass("DeclarationDetail"));
        ImRevMap<Object, KeyExpr> keys = (ImRevMap<Object, KeyExpr>) isDeclarationDetail.getMapKeys();
        KeyExpr key = keys.singleValue();
        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
        for (int j = 0; j < exportProperties.length; j++) {
            query.addProperty(exportNames[j], exportProperties[j].getExpr(context.getModifier(), key));
        }
        query.and(isDeclarationDetail.getExpr(key).getWhere());
        query.and(findProperty("declaration[DeclarationDetail]").getExpr(context.getModifier(), key).compare(declarationObject.getExpr(), Compare.EQUALS));
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(context, MapFact.singletonOrder("numberDeclarationDetail", false));

        if (result.size() == 0)
            return null;
        List<DeclarationDetail> declarationDetailList = new ArrayList<>();
        for (int i = 0, size = result.size(); i < size; i++) {
            ImMap<Object, Object> values = result.getValue(i);
            String extraNameDeclarationDetail = (String) values.get("extraNameDeclarationDetail"); //G31_NT 
            String markinDeclarationDetail = (String) values.get("markinDeclarationDetail"); //g31_MARKIN
            Integer numberDeclarationDetail = (Integer) values.get("numberDeclarationDetail"); //G32
            String codeCustomsGroupDeclarationDetail = (String) values.get("codeCustomsGroupDeclarationDetail"); //G33
            String sidOrigin2CountryDeclarationDetail = (String) values.get("sidOrigin2CountryDeclarationDetail"); //G34
            BigDecimal sumGrossWeightDeclarationDetail = (BigDecimal) values.get("sumGrossWeightDeclarationDetail"); //G35
            BigDecimal extraComponentsQuantityDeclarationDetail = (BigDecimal) values.get("extraComponentsQuantityDeclarationDetail"); //G41
            BigDecimal sumDeclarationDetail = (BigDecimal) values.get("sumDeclarationDetail");  //G42
            String nameCustomsDeclarationDetail = (String) values.get("nameCustomsDeclarationDetail"); //G312, G31_NT
            BigDecimal quantityDeclarationDetail = (BigDecimal) values.get("quantityDeclarationDetail"); //G315A, G31_KT            
            BigDecimal sumNetWeightDeclarationDetail = (BigDecimal) values.get("sumNetWeightDeclarationDetail"); //G315B, G38, G38A
            String shortNameUOMDeclarationDetail = (String) values.get("shortNameUOMDeclarationDetail"); //G317A, G31_EI
            String customsCodeUOMDeclarationDetail = (String) values.get("customsCodeUOMDeclarationDetail"); //G317A, G31_EI
            Boolean isVATCustomsExceptionDeclarationDetail = (Boolean) values.get("isVATCustomsExceptionDeclarationDetail");
            Long VATCustomsExceptionDeclarationDetail = (Long) values.get("VATCustomsExceptionDeclarationDetail"); //G364
            BigDecimal homeSumDeclarationDetail = (BigDecimal) values.get("homeSumDeclarationDetail"); //G451, G472
            BigDecimal baseVATSumDeclarationDetail = (BigDecimal) values.get("baseVATSumDeclarationDetail"); //G472
            Boolean isWeightDutyDeclarationDetail = (Boolean) values.get("isWeightDutyDeclarationDetail"); //G473
            BigDecimal weightDutyDeclarationDetail = (BigDecimal) values.get("weightDutyDeclarationDetail"); //G473
            BigDecimal percentDutyDeclarationDetail = (BigDecimal) values.get("percentDutyDeclarationDetail"); //G473
            BigDecimal percentVATDeclarationDetail = (BigDecimal) values.get("percentVATDeclarationDetail"); //G473
            BigDecimal dutySumDeclarationDetail = (BigDecimal) values.get("dutySumDeclarationDetail"); //G474
            BigDecimal VATSumDeclarationDetail = (BigDecimal) values.get("VATSumDeclarationDetail"); //G474
            String nameSupplierDeclarationDetail = (String) values.get("nameSupplierDeclarationDetail");
            String nameBrandDeclarationDetail = (String) values.get("nameBrandDeclarationDetail"); //G312
            String nameManufacturerDeclarationDetail = (String) values.get("nameManufacturerDeclarationDetail");

            declarationDetailList.add(new DeclarationDetail(numberDeclarationDetail, codeCustomsGroupDeclarationDetail,
                    nameCustomsDeclarationDetail, quantityDeclarationDetail, sumNetWeightDeclarationDetail,
                    sumGrossWeightDeclarationDetail, shortNameUOMDeclarationDetail, customsCodeUOMDeclarationDetail, sidOrigin2CountryDeclarationDetail,
                    sumDeclarationDetail, homeSumDeclarationDetail, baseVATSumDeclarationDetail,
                    isWeightDutyDeclarationDetail, weightDutyDeclarationDetail, percentDutyDeclarationDetail,
                    percentVATDeclarationDetail, dutySumDeclarationDetail, VATSumDeclarationDetail,
                    isVATCustomsExceptionDeclarationDetail, VATCustomsExceptionDeclarationDetail, extraComponentsQuantityDeclarationDetail, extraNameDeclarationDetail,
                    markinDeclarationDetail, nameSupplierDeclarationDetail, nameBrandDeclarationDetail, nameManufacturerDeclarationDetail));
        }
        return new Declaration(numberDeclaration, dateDeclaration, UNPLegalEntityDeclaration, fullNameLegalEntityDeclaration,
                addressLegalEntityDeclaration, declarationDetailList, sumDeclaration, countDeclaration);
    }

    private G44 exportG44ToList(ExecutionContext<ClassPropertyInterface> context, DataObject declarationObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<G44Detail> customsDocumentDetailList = new ArrayList<>();
        List<G44Detail> complianceDetailList = new ArrayList<>();

        String numberDeclaration = (String) findProperty("number[Declaration]").read(context, declarationObject);

        String[] customsDocumentNames = new String[]{"orderCustomsDocument", "idCustomsDocument", "nameCustomsDocument", "dateCustomsDocument", "isVATCustomsExceptionCustomsDocument", "typePaymentCustomsDocument", "refDocCustomsDocument", "descriptionCustomsDocument"};
        LP<?>[] customsDocumentProperties = new LP[]{findProperty("order[CustomsDocument]"), findProperty("id[CustomsDocument]"), findProperty("name[CustomsDocument]"), findProperty("date[CustomsDocument]"), findProperty("isVATCustomsException[CustomsDocument]"), findProperty("typePayment[CustomsDocument]"), findProperty("refDoc[CustomsDocument]"), findProperty("description[CustomsDocument]")};

        KeyExpr declarationDetailExpr = new KeyExpr("declarationDetail");
        KeyExpr customsDocumentExpr = new KeyExpr("customsDocument");
        ImRevMap<Object, KeyExpr> customsDocumentKeys = MapFact.toRevMap("declarationDetail", declarationDetailExpr, "customsDocument", customsDocumentExpr);

        QueryBuilder<Object, Object> customsDocumentQuery = new QueryBuilder<>(customsDocumentKeys);

        customsDocumentQuery.addProperty("numberDeclarationDetail", findProperty("number[DeclarationDetail]").getExpr(declarationDetailExpr));
        customsDocumentQuery.addProperty("isVATCustomsExceptionDeclarationDetail", findProperty("isVATCustomsException[DeclarationDetail]").getExpr(declarationDetailExpr));
        for (int j = 0; j < customsDocumentProperties.length; j++) {
            customsDocumentQuery.addProperty(customsDocumentNames[j], customsDocumentProperties[j].getExpr(customsDocumentExpr));
        }
        customsDocumentQuery.and(findProperty("in[DeclarationDetail,CustomsDocument]").getExpr(declarationDetailExpr, customsDocumentExpr).getWhere());
        customsDocumentQuery.and(findProperty("declaration[DeclarationDetail]").getExpr(context.getModifier(), declarationDetailExpr).compare(declarationObject.getExpr(), Compare.EQUALS));

        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> customsDocumentResult = customsDocumentQuery.execute(context, MapFact.singletonOrder("numberDeclarationDetail", false));

        for (int i = 0, size = customsDocumentResult.size(); i < size; i++) {
            ImMap<Object, Object> resultValues = customsDocumentResult.getValue(i);

            Integer numberDeclarationDetail = (Integer) resultValues.get("numberDeclarationDetail");
            Boolean isVATCustomsExceptionDeclarationDetail = (Boolean) resultValues.get("isVATCustomsExceptionDeclarationDetail");
            Integer orderCustomsDocument = (Integer) resultValues.get("orderCustomsDocument");
            String idCustomsDocument = (String) resultValues.get("idCustomsDocument");
            String nameCustomsDocument = (String) resultValues.get("nameCustomsDocument");
            LocalDate dateCustomsDocument = (LocalDate) resultValues.get("dateCustomsDocument");
            Boolean isVATCustomsExceptionCustomsDocument = (Boolean) resultValues.get("isVATCustomsExceptionCustomsDocument");
            String typePaymentCustomsDocument = (String) resultValues.get("typePaymentCustomsDocument");
            String refDocCustomsDocument = (String) resultValues.get("refDocCustomsDocument");
            String descriptionCustomsDocument = (String) resultValues.get("descriptionCustomsDocument");
            if (isVATCustomsExceptionCustomsDocument == null || isVATCustomsExceptionDeclarationDetail != null)
                customsDocumentDetailList.add(new G44Detail(numberDeclarationDetail, orderCustomsDocument == null ? (Long) customsDocumentResult.getKey(i).getValue(1) : orderCustomsDocument, idCustomsDocument, nameCustomsDocument,
                        dateCustomsDocument, null, null, null, typePaymentCustomsDocument, refDocCustomsDocument, descriptionCustomsDocument, null, null));
        }


        String[] complianceNames = new String[]{"seriesNumberCompliance", "dateCompliance", "fromDateCompliance",
                "toDateCompliance", "numberDeclarationCompliance", "dateDeclarationCompliance"};
        LP<?>[] complianceProperties = findProperties("seriesNumber[Compliance]", "date[Compliance]", "fromDate[Compliance]",
                "toDate[Compliance]", "numberDeclaration[Compliance]", "dateDeclaration[Compliance]");

        KeyExpr declarationDetail2Expr = new KeyExpr("declarationDetail");
        KeyExpr complianceExpr = new KeyExpr("compliance");
        ImRevMap<Object, KeyExpr> complianceKeys = MapFact.toRevMap("declarationDetail", declarationDetail2Expr, "compliance", complianceExpr);

        QueryBuilder<Object, Object> complianceQuery = new QueryBuilder<>(complianceKeys);

        complianceQuery.addProperty("numberDeclarationDetail", findProperty("number[DeclarationDetail]").getExpr(declarationDetail2Expr));
        for (int j = 0; j < complianceProperties.length; j++) {
            complianceQuery.addProperty(complianceNames[j], complianceProperties[j].getExpr(complianceExpr));
        }
        complianceQuery.and(findProperty("in[DeclarationDetail,Compliance]").getExpr(declarationDetail2Expr, complianceExpr).getWhere());
        complianceQuery.and(findProperty("declaration[DeclarationDetail]").getExpr(context.getModifier(), declarationDetail2Expr).compare(declarationObject.getExpr(), Compare.EQUALS));

        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> complianceResult = complianceQuery.execute(context, MapFact.singletonOrder("numberDeclarationDetail", false));

        for (int i = 0, size = complianceResult.size(); i < size; i++) {
            ImMap<Object, Object> resultValues = complianceResult.getValue(i);

            Integer numberDeclarationDetail = (Integer) resultValues.get("numberDeclarationDetail");
            String seriesNumberCompliance = (String) resultValues.get("seriesNumberCompliance");
            LocalDate dateCompliance = (LocalDate) resultValues.get("dateCompliance");
            LocalDate fromDateCompliance = (LocalDate) resultValues.get("fromDateCompliance");
            LocalDate toDateCompliance = (LocalDate) resultValues.get("toDateCompliance");
            String numberDeclarationCompliance = (String) resultValues.get("numberDeclarationCompliance");
            LocalDate dateDeclarationCompliance = (LocalDate) resultValues.get("dateDeclarationCompliance");

            complianceDetailList.add(new G44Detail(numberDeclarationDetail, 100000000L, "01191", seriesNumberCompliance,
                    dateCompliance, fromDateCompliance, toDateCompliance, "BY", "", "", "", numberDeclarationCompliance, dateDeclarationCompliance));
        }

        customsDocumentDetailList.addAll(complianceDetailList);
        customsDocumentDetailList.sort(COMPARATOR);

        return new G44(numberDeclaration, customsDocumentDetailList);
    }

    private File exportDECL02(Map<Field, Object> dbfFields, Declaration declaration) throws JDBFException, IOException {

        OverJDBField[] dataFields = getJDBFieldArray(dbfFields);
        Map<String, Object> nameValueFieldMap = getNameValueFieldMap(dbfFields);

        File dbfFile = File.createTempFile("decl02", ".dbf");
        dbfFile.deleteOnExit();
        DBFWriter dbfwriter = new DBFWriter(dbfFile.getAbsolutePath(), dataFields, "CP866");

        String GA = DOP_NOMER + (declaration.number == null ? "" : declaration.number);
        DeclarationDetail dd = declaration.declarationDetailList.get(0);
        String G364 = dd.isVATCustomsException == null ? "ОО" : "ПД";

        nameValueFieldMap.put("GA", trim(GA, 14));
        nameValueFieldMap.put("G542", localDateToSqlDate(declaration.date));
        nameValueFieldMap.put("G141", trim(declaration.UNPLegalEntity, 9));
        nameValueFieldMap.put("G142", trim(declaration.fullNameLegalEntity, 35));
        
        nameValueFieldMap.put("G031", 1);
        nameValueFieldMap.put("G032", Math.ceil((double)(declaration.declarationDetailList.size() - 1) / 3) + 1);
        nameValueFieldMap.put("G34", trim(dd.sidOrigin2Country, 2));
        nameValueFieldMap.put("G32", dd.number);
        nameValueFieldMap.put("G33", trim(dd.codeCustomsGroup, 10));

        String name = dd.nameCustoms == null ? "" : (dd.nameCustoms + " ") + (dd.nameBrand == null ? "" : dd.nameBrand);
        nameValueFieldMap.put("G312", trim(name, 248));
        nameValueFieldMap.put("G315B", roundWeight(dd.sumNetWeight, true));
        nameValueFieldMap.put("G317BCODE", "166");
        nameValueFieldMap.put("G315A", dd.quantity);

        nameValueFieldMap.put("G317A", trim(dd.shortNameUOM, 13));
        nameValueFieldMap.put("G317ACODE", dd.codeUOM == null ? "796" : trim(dd.codeUOM, 3));
        nameValueFieldMap.put("G364", G364);
        nameValueFieldMap.put("G35", dd.sumGrossWeight);
        nameValueFieldMap.put("G38", roundWeight(dd.sumNetWeight, false));

        nameValueFieldMap.put("G38A", roundWeight(dd.sumNetWeight, false));
        nameValueFieldMap.put("G41", dd.componentsQuantity == null ? null : dd.componentsQuantity.intValue());
        nameValueFieldMap.put("G42", dd.sum);

        nameValueFieldMap.put("G451", dd.homeSum);
        nameValueFieldMap.put("NOMER_GTD", trim(declaration.number, 6));
        nameValueFieldMap.put("G05", declaration.count);
        nameValueFieldMap.put("G222", declaration.sum);

        if (nameValueFieldMap.containsKey("NOM_GUID"))
            nameValueFieldMap.put("NOM_GUID", null);

        dbfwriter.addRecord(nameValueFieldMap.values().toArray());
        dbfwriter.close();

        return dbfFile;
    }

    private File exportDOBL(Map<Field, Object> dbfFields, Declaration declaration) throws JDBFException, IOException {

        OverJDBField[] dataFields = getJDBFieldArray(dbfFields);
        Map<String, Object> nameValueFieldMap = getNameValueFieldMap(dbfFields);

        File dbfFile = File.createTempFile("dobl", ".dbf");
        dbfFile.deleteOnExit();
        DBFWriter dbfwriter = new DBFWriter(dbfFile.getAbsolutePath(), dataFields, "CP866");

        String GA = DOP_NOMER + (declaration.number == null ? "" : declaration.number);
       
        for (int i = 1; i<= declaration.declarationDetailList.size();i++) {
            if (i == 1) {
                continue;
            }

            DeclarationDetail dd = declaration.declarationDetailList.get(i - 1);
            
            String G364 = dd.isVATCustomsException == null ? "ОО" : "ПД";

            nameValueFieldMap.put("GA", trim(GA, 14));
            nameValueFieldMap.put("G542", localDateToSqlDate(declaration.date));
            nameValueFieldMap.put("G141", trim(declaration.UNPLegalEntity, 9));
            nameValueFieldMap.put("G142", trim(declaration.fullNameLegalEntity, 35));
            
            nameValueFieldMap.put("G031", Math.ceil((double)(i - 1) / 3) + 1);
            nameValueFieldMap.put("G032", Math.ceil((double)(declaration.declarationDetailList.size() - 1) / 3) + 1);
            nameValueFieldMap.put("G34", trim(dd.sidOrigin2Country, 2));
            nameValueFieldMap.put("G32", dd.number);
            nameValueFieldMap.put("G33", trim(dd.codeCustomsGroup, 10));

            String name = dd.nameCustoms == null ? "" : (dd.nameCustoms + " ") + (dd.nameBrand == null ? "" : dd.nameBrand);
            nameValueFieldMap.put("G312", trim(name, 248));
            nameValueFieldMap.put("G315B", roundWeight(dd.sumNetWeight, true));
            nameValueFieldMap.put("G317BCODE", "166");
            nameValueFieldMap.put("G315A", dd.quantity);

            nameValueFieldMap.put("G317A", trim(dd.shortNameUOM, 13));
            nameValueFieldMap.put("G317ACODE", dd.codeUOM == null ? "796" : trim(dd.codeUOM, 3));
            nameValueFieldMap.put("G364", G364);
            nameValueFieldMap.put("G35", dd.sumGrossWeight);
            nameValueFieldMap.put("G38", roundWeight(dd.sumNetWeight, false));

            nameValueFieldMap.put("G38A", roundWeight(dd.sumNetWeight, false));
            nameValueFieldMap.put("G41", dd.componentsQuantity == null ? null : dd.componentsQuantity.intValue());
            nameValueFieldMap.put("G42", dd.sum);

            nameValueFieldMap.put("G451", dd.homeSum);
            nameValueFieldMap.put("NOMER_GTD", trim(declaration.number, 6));

            dbfwriter.addRecord(nameValueFieldMap.values().toArray());
        }
        dbfwriter.close();

        return dbfFile;
    }

    private File exportG18(Map<Field, Object> dbfFields) throws JDBFException, IOException {

        OverJDBField[] dataFields = getJDBFieldArray(dbfFields);

        File dbfFile = File.createTempFile("g18", ".dbf");
        dbfFile.deleteOnExit();
        DBFWriter dbfwriter = new DBFWriter(dbfFile.getAbsolutePath(), dataFields, "CP866");
        dbfwriter.close();
        return dbfFile;
    }

    private File exportG20(Map<Field, Object> dbfFields, Declaration declaration) throws JDBFException, IOException {

        OverJDBField[] dataFields = getJDBFieldArray(dbfFields);
        Map<String, Object> nameValueFieldMap = getNameValueFieldMap(dbfFields);

        File dbfFile = File.createTempFile("g20", ".dbf");
        dbfFile.deleteOnExit();
        DBFWriter dbfwriter = new DBFWriter(dbfFile.getAbsolutePath(), dataFields, "CP866");

        nameValueFieldMap.put("G20I", 1);
        nameValueFieldMap.put("G202", "FCA");
        nameValueFieldMap.put("G205", "ДЭВЕНТРИ");
        nameValueFieldMap.put("NOMER_GTD", trim(declaration.number, 6));
        //nameValueFieldMap.put("DOP_NOMER", DOP_NOMER);

        dbfwriter.addRecord(nameValueFieldMap.values().toArray());
        dbfwriter.close();

        return dbfFile;
    }

    private File exportG21(Map<Field, Object> dbfFields) throws JDBFException, IOException {

        OverJDBField[] dataFields = getJDBFieldArray(dbfFields);

        File dbfFile = File.createTempFile("g21", ".dbf");
        dbfFile.deleteOnExit();
        DBFWriter dbfwriter = new DBFWriter(dbfFile.getAbsolutePath(), dataFields, "CP866");
        dbfwriter.close();
        return dbfFile;
    }

    private File exportG40(Map<Field, Object> dbfFields) throws JDBFException, IOException {

        OverJDBField[] dataFields = getJDBFieldArray(dbfFields);

        File dbfFile = File.createTempFile("g40", ".dbf");
        dbfFile.deleteOnExit();
        DBFWriter dbfwriter = new DBFWriter(dbfFile.getAbsolutePath(), dataFields, "CP866");
        dbfwriter.close();
        return dbfFile;
    }

    private File exportG44(Map<Field, Object> dbfFields, G44 g44) throws JDBFException, IOException {

        OverJDBField[] dataFields = getJDBFieldArray(dbfFields);
        Map<String, Object> nameValueFieldMap = getNameValueFieldMap(dbfFields);

        File dbfFile = File.createTempFile("g44", ".dbf");
        dbfFile.deleteOnExit();
        DBFWriter dbfwriter = new DBFWriter(dbfFile.getAbsolutePath(), dataFields, "CP866");
        for (G44Detail dd : g44.g44DetailList) {

            nameValueFieldMap.put("G32", dd.numberDeclarationDetail);
            nameValueFieldMap.put("G44KD", trim(dd.KD, 5));
            nameValueFieldMap.put("G44ND", trim(dd.ND, 50));
            nameValueFieldMap.put("G44DD", localDateToSqlDate(dd.DD));
            nameValueFieldMap.put("G44BEGDATE", localDateToSqlDate(dd.beginDate));

            nameValueFieldMap.put("G44ENDDATE", localDateToSqlDate(dd.endDate));
            nameValueFieldMap.put("G44CODESTR", dd.country);
            nameValueFieldMap.put("G44VIDPLAT", dd.vidplat);
            nameValueFieldMap.put("NOMER_GTD", trim(g44.number, 6));
            //nameValueFieldMap.put("DOP_NOMER", DOP_NOMER);

            nameValueFieldMap.put("G44PREFDOC", dd.refdoc);
            nameValueFieldMap.put("G44NAME", dd.description);

            nameValueFieldMap.put("G44REGNUM", trim(dd.numberDeclaration, 20));
            nameValueFieldMap.put("G44DS", localDateToSqlDate(dd.dateDeclaration));
            nameValueFieldMap.put("G44PP", dd.numberDeclaration != null ? "2" : "1");

            dbfwriter.addRecord(nameValueFieldMap.values().toArray()); //17
        }
        dbfwriter.close();

        return dbfFile;
    }

    private File exportG47(Map<Field, Object> dbfFields, Declaration declaration) throws JDBFException, IOException {

        OverJDBField[] dataFields = getJDBFieldArray(dbfFields);
        Map<String, Object> nameValueFieldMap = getNameValueFieldMap(dbfFields);

        File dbfFile = File.createTempFile("g47", "dbf");
        dbfFile.deleteOnExit();
        DBFWriter dbfwriter = new DBFWriter(dbfFile.getAbsolutePath(), dataFields, "CP866");
        for (DeclarationDetail dd : declaration.declarationDetailList) {
            String percentDuty = dd.percentDuty == null ? null : String.valueOf(dd.percentDuty.intValue());
            String weightDuty = dd.weightDuty == null ? null : String.valueOf(dd.weightDuty.intValue());
            String G473_1 = dd.isWeightDuty == null ? percentDuty : weightDuty;
            String G473_2 = dd.percentVAT == null ? null : String.valueOf(dd.percentVAT.intValue());

            nameValueFieldMap.put("G32", dd.number);
            nameValueFieldMap.put("G471", "2010");
            nameValueFieldMap.put("G472", dd.homeSum);
            nameValueFieldMap.put("G473", G473_1);
            nameValueFieldMap.put("G4731", dd.isWeightDuty == null ? "%" : "ЕВРО");

            nameValueFieldMap.put("G474", dd.dutySum);
            nameValueFieldMap.put("NOMER_GTD", trim(declaration.number, 6));
            //nameValueFieldMap.put("DOP_NOMER", DOP_NOMER);

            dbfwriter.addRecord(nameValueFieldMap.values().toArray());

            nameValueFieldMap.put("G32", dd.number);
            nameValueFieldMap.put("G471", "5010");
            nameValueFieldMap.put("G472", dd.baseVATSum);
            nameValueFieldMap.put("G473", G473_2);
            nameValueFieldMap.put("G4731", "%");

            nameValueFieldMap.put("G474", dd.VATSum);
            nameValueFieldMap.put("NOMER_GTD", trim(declaration.number, 6));
            //nameValueFieldMap.put("DOP_NOMER", DOP_NOMER);

            dbfwriter.addRecord(nameValueFieldMap.values().toArray());

        }
        dbfwriter.close();

        return dbfFile;
    }

    private File exportG313(Map<Field, Object> dbfFields, Declaration declaration) throws JDBFException, IOException {

        OverJDBField[] dataFields = getJDBFieldArray(dbfFields);
        Map<String, Object> nameValueFieldMap = getNameValueFieldMap(dbfFields);

        File dbfFile = File.createTempFile("g313", ".dbf");
        dbfFile.deleteOnExit();
        DBFWriter dbfwriter = new DBFWriter(dbfFile.getAbsolutePath(), dataFields, "CP866");
        for (DeclarationDetail dd : declaration.declarationDetailList) {

            String G31_NT = (dd.nameCustoms == null ? "" : dd.nameCustoms) + (dd.extraName == null ? (dd.nameBrand == null ? "" : "Т.М. " + dd.nameBrand) : ("(" + dd.extraName + ")"));

            nameValueFieldMap.put("G32", dd.number);
            nameValueFieldMap.put("G31_NT", trim(G31_NT, 150));
            nameValueFieldMap.put("G31_MARKIN", dd.markin);
            nameValueFieldMap.put("G31_MARK", dd.nameBrand);
            nameValueFieldMap.put("G31_FIRMA", dd.nameManufacturer);

            nameValueFieldMap.put("G31_KT", dd.quantity);
            nameValueFieldMap.put("G31_EI", trim(dd.shortNameUOM, 13));
            nameValueFieldMap.put("G31_CODIZM", trim(dd.codeUOM, 3));
            nameValueFieldMap.put("G313I", 1);
            //nameValueFieldMap.put("DOP_NOMER", DOP_NOMER);

            nameValueFieldMap.put("NOMER_GTD", trim(declaration.number, 6));

            dbfwriter.addRecord(nameValueFieldMap.values().toArray());
        }
        dbfwriter.close();

        return dbfFile;
    }

    private File exportGB(Map<Field, Object> dbfFields) throws JDBFException, IOException {

        OverJDBField[] dataFields = getJDBFieldArray(dbfFields);

        File dbfFile = File.createTempFile("exportGB", ".dbf");
        dbfFile.deleteOnExit();
        DBFWriter dbfwriter = new DBFWriter(dbfFile.getAbsolutePath(), dataFields, "CP866");
        dbfwriter.close();
        return dbfFile;
    }

    private File exportG316(Map<Field, Object> dbfFields) throws JDBFException, IOException {

        OverJDBField[] dataFields = getJDBFieldArray(dbfFields);

        File dbfFile = File.createTempFile("g316", ".dbf");
        dbfFile.deleteOnExit();
        DBFWriter dbfwriter = new DBFWriter(dbfFile.getAbsolutePath(), dataFields, "CP866");
        dbfwriter.close();
        return dbfFile;
    }

    private Map<Field, Object> readDBFFields(RawFileData rawFileData) throws IOException, xBaseJException, ParseException {

        Map<Field, Object> fieldMap = new LinkedHashMap<>();

        File tempFile = null;
        DBF dbfFile = null;
        try {
            tempFile = File.createTempFile("tempTnved", ".dbf");
            rawFileData.write(tempFile);

            dbfFile = new DBF(tempFile.getPath());
            String charset = getDBFCharset(tempFile);

            if (dbfFile.getRecordCount() > 0)
                dbfFile.read();
            for (int i = 1; i <= dbfFile.getFieldCount(); i++) {
                Field field = dbfFile.getField(i);
                String stringValue = trim(dbfFile.getRecordCount() > 0 ? new String(field.getBytes(), charset) : null);
                Object value = null;
                if (stringValue != null && !stringValue.isEmpty()) {
                    switch (field.getType()) {
                        case 'D':
                            value = parseDate(stringValue);
                            break;
                        case 'N':
                            value = Long.parseLong(stringValue.split("\\.|,")[0]);
                            break;
                        case 'F':
                            value = new BigDecimal(stringValue);
                            break;
                        case 'L':
                            value = stringValue.equals("T");
                            break;
                        case 'C':
                        default:
                            value = stringValue;
                            break;
                    }
                }
                fieldMap.put(dbfFile.getField(i), value);
            }
        } finally {
            if(dbfFile != null)
                dbfFile.close();
            if(tempFile != null && !tempFile.delete())
                tempFile.deleteOnExit();
        }            
        return fieldMap;
    }

    private OverJDBField[] getJDBFieldArray(Map<Field, Object> dbfFields) throws JDBFException {
        List<OverJDBField> dataFields = new ArrayList<>();

        for (Map.Entry<Field, Object> entry : dbfFields.entrySet()) {
            dataFields.add(new OverJDBField(entry.getKey().getName(), entry.getKey().getType(), entry.getKey().getLength(), entry.getKey().getDecimalPositionCount()));
        }
        return dataFields.toArray(new OverJDBField[dataFields.size()]);
    }

    private Map<String, Object> getNameValueFieldMap(Map<Field, Object> dbfFields) {
        LinkedHashMap<String, Object> nameValueFieldMap = new LinkedHashMap<>();

        for (Map.Entry<Field, Object> entry : dbfFields.entrySet()) {
            nameValueFieldMap.put(entry.getKey().getName(), entry.getValue());
        }
        return nameValueFieldMap;
    }

    private static Comparator<G44Detail> COMPARATOR = (o1, o2) -> {
        if (!o1.numberDeclarationDetail.equals(o2.numberDeclarationDetail))
            return o1.numberDeclarationDetail.compareTo(o2.numberDeclarationDetail);
        else
            return o1.order.compareTo(o2.order);
    };


    public String getDBFCharset(File file) throws IOException {
        byte charsetByte = IOUtils.getFileBytes(file)[29];
        String charset;
        switch (charsetByte) {
            case (byte) 0x65:
                charset = "cp866";
                break;
            case (byte) 0xC9:
                charset = "cp1251";
                break;
            default:
                charset = "cp866";
        }
        return charset;
    }

    private BigDecimal roundWeight(BigDecimal weight, boolean g315) {
        if(g315)
            //return weight.setScale(weight.compareTo(new BigDecimal(0.1)) > 0 ? 2 : 4, BigDecimal.ROUND_HALF_UP);
            return weight.setScale(2, BigDecimal.ROUND_HALF_UP);
        else
            return weight.setScale(3, BigDecimal.ROUND_HALF_UP);
    }
}
