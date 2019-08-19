package lsfusion.erp.region.by.certificate.declaration;

import lsfusion.base.file.RawFileData;
import lsfusion.server.logics.classes.user.CustomClass;
import lsfusion.server.logics.classes.data.file.CustomStaticFormatFileClass;
import lsfusion.server.logics.classes.data.time.DateClass;
import lsfusion.server.logics.classes.data.StringClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.physics.dev.integration.service.*;
import org.xBaseJ.DBF;
import org.xBaseJ.xBaseJException;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ImportTNVEDClassifierAction extends InternalAction {

    public ImportTNVEDClassifierAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {
            if(findProperty("defaultCountry[]").read(context) == null) {
                ObjectValue countryBelarus = findProperty("country[BPSTRING[3]]").readClasses(context, new DataObject("112", StringClass.get(3)));
                findProperty("defaultCountry[]").change(countryBelarus, context);
                context.apply();
            }

            CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get("Файлы DBF", "dbf");
            ObjectValue objectValue = context.requestUserData(valueClass, null);
            if (objectValue != null) {
                importGroups(context, (RawFileData) objectValue.getValue());
                importParents(context, (RawFileData) objectValue.getValue());
            }
        } catch (xBaseJException | IOException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }

    private void importGroups(ExecutionContext<ClassPropertyInterface> context, RawFileData fileBytes) throws IOException, xBaseJException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<List<Object>> data = new ArrayList<>();

        File tempFile = null;
        DBF dbfFile = null;
        try {

            tempFile = File.createTempFile("tempTnved", ".dbf");
            fileBytes.write(tempFile);

            dbfFile = new DBF(tempFile.getPath());
            int recordCount = dbfFile.getRecordCount();

            BigDecimal defaultVAT = new BigDecimal(20);
            Date defaultDate = new Date(2001 - 1900, 0, 1);


            for (int i = 1; i <= recordCount; i++) {
                dbfFile.read();

                String groupID = new String(dbfFile.getField("KOD").getBytes(), "Cp866").trim();
                String name = new String(dbfFile.getField("NAIM").getBytes(), "Cp866").trim();
                String extraName = new String(dbfFile.getField("KR_NAIM").getBytes(), "Cp866").trim();

                Boolean hasCode = true;
                if (groupID.equals("··········")) {
                    groupID = "-" + i;
                    hasCode = null;
                }
                data.add(Arrays.asList(groupID, name + extraName, i, "БЕЛАРУСЬ", hasCode, defaultVAT, defaultDate));
            }
        } finally {
            if(dbfFile != null)
                dbfFile.close();
            if(tempFile != null && !tempFile.delete())
                tempFile.deleteOnExit();
        }
        
        ImportField codeCustomsGroupField = new ImportField(findProperty("code[CustomsGroup]"));
        ImportField nameCustomsGroupField = new ImportField(findProperty("name[CustomsGroup]"));
        ImportField numberCustomsGroupField = new ImportField(findProperty("number[CustomsGroup]"));
        ImportField nameCustomsZoneField = new ImportField(findProperty("name[CustomsZone]"));
        ImportField hasCodeCustomsGroupField = new ImportField(findProperty("hasCode[CustomsGroup]"));
        ImportField vatField = new ImportField(findProperty("dataValueSupplierVAT[CustomsGroup,DATE]"));
        ImportField dateField = new ImportField(DateClass.instance);

        ImportKey<?> customsGroupKey = new ImportKey((CustomClass) findClass("CustomsGroup"), findProperty("customsGroup[BPSTRING[10]]").getMapping(codeCustomsGroupField));
        ImportKey<?> customsZoneKey = new ImportKey((CustomClass) findClass("CustomsZone"), findProperty("customsZone[ISTRING[50]]").getMapping(nameCustomsZoneField));
        ImportKey<?> VATKey = new ImportKey((CustomClass) findClass("Range"), findProperty("valueCurrentVATDefault[NUMERIC[10,5]]").getMapping(vatField));

        List<ImportProperty<?>> properties = new ArrayList<>();
        properties.add(new ImportProperty(codeCustomsGroupField, findProperty("code[CustomsGroup]").getMapping(customsGroupKey)));
        properties.add(new ImportProperty(nameCustomsGroupField, findProperty("name[CustomsGroup]").getMapping(customsGroupKey)));
        properties.add(new ImportProperty(numberCustomsGroupField, findProperty("number[CustomsGroup]").getMapping(customsGroupKey)));
        properties.add(new ImportProperty(nameCustomsZoneField, findProperty("name[CustomsZone]").getMapping(customsZoneKey)));
        properties.add(new ImportProperty(nameCustomsZoneField, findProperty("customsZone[CustomsGroup]").getMapping(customsGroupKey),
                object(findClass("CustomsZone")).getMapping(customsZoneKey)));
        properties.add(new ImportProperty(hasCodeCustomsGroupField, findProperty("hasCode[CustomsGroup]").getMapping(customsGroupKey)));

        ImportTable table = new ImportTable(Arrays.asList(codeCustomsGroupField, nameCustomsGroupField,
                numberCustomsGroupField, nameCustomsZoneField, hasCodeCustomsGroupField, vatField, dateField), data);

        try (ExecutionContext.NewSession newContext = context.newSession()) {
            IntegrationService service = new IntegrationService(newContext, table,
                    Arrays.asList(customsGroupKey, customsZoneKey, VATKey), properties);
            service.synchronize(true, false);
            newContext.apply();
        }
    }

    private void importParents(ExecutionContext<ClassPropertyInterface> context, RawFileData fileBytes) throws IOException, xBaseJException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<List<Object>> data = new ArrayList<>();
        File tempFile = null;
        DBF file = null;
        try {
            tempFile = File.createTempFile("tempTnved", ".dbf");
            fileBytes.write(tempFile);

            file = new DBF(tempFile.getPath());

            List<String> groupIDsList = new ArrayList<>();
            int recordCount = file.getRecordCount();
            for (int i = 1; i <= recordCount; i++) {
                file.read();

                String groupID = new String(file.getField("KOD").getBytes(), "Cp866").trim();
                String parentID = null;
                if (!groupID.equals("··········"))
                    for (int j = groupID.length() - 1; j > 0; j--) {
                        if (groupIDsList.contains(groupID.substring(0, j))) {
                            parentID = groupID.substring(0, j);
                            break;
                        }
                    }

                if (groupID.equals("··········")) {
                    groupID = "-" + i;
                }

                data.add(Arrays.asList(groupID, parentID));
                groupIDsList.add(groupID);
            }
        } finally {
            if(file != null)
                file.close();
            if(tempFile != null && !tempFile.delete())
                tempFile.deleteOnExit();
        }

        ImportField groupIDField = new ImportField(findProperty("code[CustomsGroup]"));
        ImportField parentIDField = new ImportField(findProperty("code[CustomsGroup]"));

        ImportKey<?> customsGroupKey = new ImportKey((CustomClass) findClass("CustomsGroup"), findProperty("customsGroup[BPSTRING[10]]").getMapping(groupIDField));
        ImportKey<?> parentCustomsGroupKey = new ImportKey((CustomClass) findClass("CustomsGroup"), findProperty("customsGroup[BPSTRING[10]]").getMapping(parentIDField));

        List<ImportProperty<?>> properties = new ArrayList<>();
        properties.add(new ImportProperty(parentIDField, findProperty("parent[CustomsGroup]").getMapping(customsGroupKey),
                object(findClass("CustomsGroup")).getMapping(parentCustomsGroupKey)));

        ImportTable table = new ImportTable(Arrays.asList(groupIDField, parentIDField), data);

        try (ExecutionContext.NewSession newContext = context.newSession()) {
            IntegrationService service = new IntegrationService(newContext, table,
                    Arrays.asList(customsGroupKey, parentCustomsGroupKey), properties);
            service.synchronize(true, false);
            newContext.apply();
        }
    }
}