package lsfusion.erp.region.by.certificate.declaration;

import lsfusion.server.data.SQLHandledException;
import org.xBaseJ.DBF;
import org.xBaseJ.xBaseJException;
import lsfusion.base.IOUtils;
import lsfusion.server.classes.*;
import lsfusion.server.integration.*;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ImportTNVEDClassifierActionProperty extends ScriptingActionProperty {

    public ImportTNVEDClassifierActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            Object countryBelarus = findProperty("countrySID").read(context.getSession(), new DataObject("112", StringClass.get(3)));
            findProperty("defaultCountry").change(countryBelarus, context.getSession());
            context.getSession().apply(context);

            CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, "Файлы DBF", "DBF");
            ObjectValue objectValue = context.requestUserData(valueClass, null);
            if (objectValue != null) {
                List<byte[]> fileList = valueClass.getFiles(objectValue.getValue());

                for (byte[] file : fileList) {
                    importGroups(context, file);
                    importParents(context, file);
                }
            }
        } catch (xBaseJException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }

    private void importGroups(ExecutionContext<ClassPropertyInterface> context, byte[] fileBytes) throws IOException, xBaseJException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        File tempFile = File.createTempFile("tempTnved", ".dbf");
        IOUtils.putFileBytes(tempFile, fileBytes);

        DBF file = new DBF(tempFile.getPath());

        List<List<Object>> data = new ArrayList<List<Object>>();

        BigDecimal defaultVAT = new BigDecimal(20);
        Date defaultDate = new Date(2001 - 1900, 0, 1);

        int recordCount = file.getRecordCount();
        for (int i = 1; i <= recordCount; i++) {
            file.read();

            String groupID = new String(file.getField("KOD").getBytes(), "Cp866").trim();
            String name = new String(file.getField("NAIM").getBytes(), "Cp866").trim();
            String extraName = new String(file.getField("KR_NAIM").getBytes(), "Cp866").trim();

            Boolean hasCode = true;
            if (groupID.equals("··········")) {
                groupID = "-" + i;
                hasCode = null;
            }
            data.add(Arrays.asList((Object) groupID, name + extraName, i, "БЕЛАРУСЬ", hasCode, defaultVAT, defaultDate));
        }
        file.close();
        tempFile.delete();
        
        ImportField codeCustomsGroupField = new ImportField(findProperty("codeCustomsGroup"));
        ImportField nameCustomsGroupField = new ImportField(findProperty("nameCustomsGroup"));
        ImportField numberCustomsGroupField = new ImportField(findProperty("numberCustomsGroup"));
        ImportField nameCustomsZoneField = new ImportField(findProperty("nameCustomsZone"));
        ImportField hasCodeCustomsGroupField = new ImportField(findProperty("hasCodeCustomsGroup"));
        ImportField vatField = new ImportField(findProperty("dataValueSupplierVATCustomsGroupDate"));
        ImportField dateField = new ImportField(DateClass.instance);

        ImportKey<?> customsGroupKey = new ImportKey((CustomClass) findClass("CustomsGroup"), findProperty("customsGroupCode").getMapping(codeCustomsGroupField));
        ImportKey<?> customsZoneKey = new ImportKey((CustomClass) findClass("CustomsZone"), findProperty("customsZoneName").getMapping(nameCustomsZoneField));
        ImportKey<?> VATKey = new ImportKey((ConcreteCustomClass) findClass("Range"), findProperty("valueCurrentVATDefaultValue").getMapping(vatField));

        List<ImportProperty<?>> properties = new ArrayList<ImportProperty<?>>();
        properties.add(new ImportProperty(codeCustomsGroupField, findProperty("codeCustomsGroup").getMapping(customsGroupKey)));
        properties.add(new ImportProperty(nameCustomsGroupField, findProperty("nameCustomsGroup").getMapping(customsGroupKey)));
        properties.add(new ImportProperty(numberCustomsGroupField, findProperty("numberCustomsGroup").getMapping(customsGroupKey)));
        properties.add(new ImportProperty(nameCustomsZoneField, findProperty("nameCustomsZone").getMapping(customsZoneKey)));
        properties.add(new ImportProperty(nameCustomsZoneField, findProperty("customsZoneCustomsGroup").getMapping(customsGroupKey),
                object(findClass("CustomsZone")).getMapping(customsZoneKey)));
        properties.add(new ImportProperty(hasCodeCustomsGroupField, findProperty("hasCodeCustomsGroup").getMapping(customsGroupKey)));

        ImportTable table = new ImportTable(Arrays.asList(codeCustomsGroupField, nameCustomsGroupField,
                numberCustomsGroupField, nameCustomsZoneField, hasCodeCustomsGroupField, vatField, dateField), data);

        DataSession session = context.createSession();
        IntegrationService service = new IntegrationService(session, table,
                Arrays.asList(customsGroupKey, customsZoneKey, VATKey), properties);
        service.synchronize(true, false);
        session.apply(context);
        session.close();
    }

    private void importParents(ExecutionContext<ClassPropertyInterface> context, byte[] fileBytes) throws IOException, xBaseJException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        File tempFile = File.createTempFile("tempTnved", ".dbf");
        IOUtils.putFileBytes(tempFile, fileBytes);

        DBF file = new DBF(tempFile.getPath());

        List<List<Object>> data = new ArrayList<List<Object>>();
        List<String> groupIDsList = new ArrayList<String>();
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

            data.add(Arrays.asList((Object) groupID, parentID));
            groupIDsList.add(groupID);
        }
        file.close();
        tempFile.delete();
        
        ImportField groupIDField = new ImportField(findProperty("codeCustomsGroup"));
        ImportField parentIDField = new ImportField(findProperty("codeCustomsGroup"));

        ImportKey<?> customsGroupKey = new ImportKey((CustomClass) findClass("CustomsGroup"), findProperty("customsGroupCode").getMapping(groupIDField));
        ImportKey<?> parentCustomsGroupKey = new ImportKey((CustomClass) findClass("CustomsGroup"), findProperty("customsGroupCode").getMapping(parentIDField));

        List<ImportProperty<?>> properties = new ArrayList<ImportProperty<?>>();
        properties.add(new ImportProperty(parentIDField, findProperty("parentCustomsGroup").getMapping(customsGroupKey),
                object(findClass("CustomsGroup")).getMapping(parentCustomsGroupKey)));

        ImportTable table = new ImportTable(Arrays.asList(groupIDField, parentIDField), data);

        DataSession session = context.createSession();
        IntegrationService service = new IntegrationService(session, table,
                Arrays.asList(customsGroupKey, parentCustomsGroupKey), properties);
        service.synchronize(true, false);
        session.apply(context);
        session.close();
    }
}