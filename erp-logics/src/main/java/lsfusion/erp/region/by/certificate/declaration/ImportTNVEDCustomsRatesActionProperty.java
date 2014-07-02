package lsfusion.erp.region.by.certificate.declaration;

import lsfusion.base.IOUtils;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.classes.CustomStaticFormatFileClass;
import lsfusion.server.classes.DateClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.integration.*;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;
import org.apache.commons.lang.time.DateUtils;
import org.xBaseJ.DBF;
import org.xBaseJ.xBaseJException;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;

public class ImportTNVEDCustomsRatesActionProperty extends ScriptingActionProperty {

    public ImportTNVEDCustomsRatesActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {
            CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, "Файлы DBF", "DBF");
            ObjectValue objectValue = context.requestUserData(valueClass, null);

            if (objectValue != null) {
                List<byte[]> fileList = valueClass.getFiles(objectValue.getValue());
                for (byte[] file : fileList) {

                    List<List<List<Object>>> data = importDutiesFromDBF(context, file);

                    if (data != null && data.size() >= 1)
                        importDuty(context, data.get(0));
                    if (data != null && data.size() >= 2)
                        importVAT(context, data.get(1));
                }
            }

        } catch (xBaseJException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private void importDuty(ExecutionContext<ClassPropertyInterface> context, List<List<Object>> data) throws IOException, xBaseJException, ScriptingErrorLog.SemanticErrorException, SQLException, ParseException, SQLHandledException {

        List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
        List<ImportField> fields = new ArrayList<ImportField>();
        List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

        ImportField codeCustomsGroupField = new ImportField(getLCP("codeCustomsGroup"));
        ImportKey<?> customsGroupKey = new ImportKey((CustomClass) getClass("CustomsGroup"),
                getLCP("customsGroupCode").getMapping(codeCustomsGroupField));
        keys.add(customsGroupKey);
        fields.add(codeCustomsGroupField);

        ImportField idCustomsRateField = new ImportField(getLCP("idRegistrationCustomsRate"));
        fields.add(idCustomsRateField);

        ImportKey<?> registrationCustomsRateKey = new ImportKey((CustomClass) getClass("RegistrationCustomsRate"),
                getLCP("registrationCustomsRateId").getMapping(idCustomsRateField));
        keys.add(registrationCustomsRateKey);
        props.add(new ImportProperty(idCustomsRateField, getLCP("idRegistrationCustomsRate").getMapping(registrationCustomsRateKey)));

        ImportKey<?> dutyCustomsRateKey = new ImportKey((CustomClass) getClass("DutyCustomsRate"),
                getLCP("dutyCustomsRateId").getMapping(idCustomsRateField));
        keys.add(dutyCustomsRateKey);
        props.add(new ImportProperty(idCustomsRateField, getLCP("idDutyCustomsRate").getMapping(dutyCustomsRateKey)));

        ImportField sumRegistrationCustomsRateField = new ImportField(getLCP("sumRegistrationCustomsRate"));
        props.add(new ImportProperty(sumRegistrationCustomsRateField, getLCP("sumRegistrationCustomsRate").getMapping(registrationCustomsRateKey)));
        props.add(new ImportProperty(codeCustomsGroupField, getLCP("customsGroupRegistrationCustomsRate").getMapping(registrationCustomsRateKey),
                object(getClass("CustomsGroup")).getMapping(customsGroupKey)));
        fields.add(sumRegistrationCustomsRateField);

        ImportField percentDutyDutyCustomsRateField = new ImportField(getLCP("percentDutyDutyCustomsRate"));
        props.add(new ImportProperty(percentDutyDutyCustomsRateField, getLCP("percentDutyDutyCustomsRate").getMapping(dutyCustomsRateKey)));
        props.add(new ImportProperty(codeCustomsGroupField, getLCP("customsGroupDutyCustomsRate").getMapping(dutyCustomsRateKey),
                object(getClass("CustomsGroup")).getMapping(customsGroupKey)));
        fields.add(percentDutyDutyCustomsRateField);

        ImportField weightDutyDutyCustomsRateField = new ImportField(getLCP("weightDutyDutyCustomsRate"));
        props.add(new ImportProperty(weightDutyDutyCustomsRateField, getLCP("weightDutyDutyCustomsRate").getMapping(dutyCustomsRateKey)));
        fields.add(weightDutyDutyCustomsRateField);

        ImportField dateFromCustomsGroupField = new ImportField(DateClass.instance);
        props.add(new ImportProperty(dateFromCustomsGroupField, getLCP("dateFromCustomsGroup").getMapping(customsGroupKey)));
        props.add(new ImportProperty(dateFromCustomsGroupField, getLCP("dateFromRegistrationCustomsRate").getMapping(registrationCustomsRateKey)));
        props.add(new ImportProperty(dateFromCustomsGroupField, getLCP("dateFromDutyCustomsRate").getMapping(dutyCustomsRateKey)));
        fields.add(dateFromCustomsGroupField);

        ImportField dateToCustomsGroupField = new ImportField(DateClass.instance);
        props.add(new ImportProperty(dateToCustomsGroupField, getLCP("dateToCustomsGroup").getMapping(customsGroupKey)));
        props.add(new ImportProperty(dateToCustomsGroupField, getLCP("dateToRegistrationCustomsRate").getMapping(registrationCustomsRateKey)));
        props.add(new ImportProperty(dateToCustomsGroupField, getLCP("dateToDutyCustomsRate").getMapping(dutyCustomsRateKey)));
        fields.add(dateToCustomsGroupField);

        ImportTable table = new ImportTable(fields, data);

        DataSession session = context.createSession();
        IntegrationService service = new IntegrationService(session, table, keys, props);
        service.synchronize(true, false);
        session.apply(context);
        session.close();
    }

    private void importVAT(ExecutionContext<ClassPropertyInterface> context, List<List<Object>> data) throws IOException, xBaseJException, ScriptingErrorLog.SemanticErrorException, SQLException, ParseException, SQLHandledException {

        List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
        List<ImportField> fields = new ArrayList<ImportField>();
        List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

        ImportField codeCustomsGroupField = new ImportField(getLCP("codeCustomsGroup"));
        ImportKey<?> customsGroupKey = new ImportKey((CustomClass) getClass("CustomsGroup"),
                getLCP("customsGroupCode").getMapping(codeCustomsGroupField));
        keys.add(customsGroupKey);
        fields.add(codeCustomsGroupField);

        ImportField idCustomsRateField = new ImportField(getLCP("idRegistrationCustomsRate"));
        fields.add(idCustomsRateField);
       
        ImportKey<?> VATCustomsRateKey = new ImportKey((CustomClass) getClass("VATCustomsRate"),
                getLCP("VATCustomsRateId").getMapping(idCustomsRateField));
        keys.add(VATCustomsRateKey);
        props.add(new ImportProperty(idCustomsRateField, getLCP("idVATCustomsRate").getMapping(VATCustomsRateKey)));
       
        ImportField rangeField = new ImportField(getLCP("dataValueSupplierVATCustomsGroupDate"));
        ImportKey<?> rangeKey = new ImportKey((ConcreteCustomClass) getClass("Range"),
                getLCP("valueCurrentVATDefaultValue").getMapping(rangeField));
        props.add(new ImportProperty(rangeField, getLCP("rangeVATCustomsRate").getMapping(VATCustomsRateKey),
                object(getClass("Range")).getMapping(rangeKey)));
        props.add(new ImportProperty(codeCustomsGroupField, getLCP("customsGroupVATCustomsRate").getMapping(VATCustomsRateKey),
                object(getClass("CustomsGroup")).getMapping(customsGroupKey)));
        fields.add(rangeField);

        ImportField dateFromCustomsGroupField = new ImportField(DateClass.instance);
        props.add(new ImportProperty(dateFromCustomsGroupField, getLCP("dateFromVATCustomsRate").getMapping(VATCustomsRateKey)));
        fields.add(dateFromCustomsGroupField);

        ImportField dateToCustomsGroupField = new ImportField(DateClass.instance);
        props.add(new ImportProperty(dateToCustomsGroupField, getLCP("dateToVATCustomsRate").getMapping(VATCustomsRateKey)));
        fields.add(dateToCustomsGroupField);

        ImportTable table = new ImportTable(fields, data);

        DataSession session = context.createSession();
        IntegrationService service = new IntegrationService(session, table, keys, props);
        service.synchronize(true, false);
        session.apply(context);
        session.close();
    }

    private List<List<List<Object>>> importDutiesFromDBF(ExecutionContext context, byte[] fileBytes) throws IOException, xBaseJException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        Set<String> tnvedSet = getTNVEDSet(context);

        File tempFile = File.createTempFile("tempTnved", ".dbf");
        IOUtils.putFileBytes(tempFile, fileBytes);

        DBF file = new DBF(tempFile.getPath());

        Map<String, BigDecimal> registrationMap = new HashMap<String, BigDecimal>();

        List<List<Object>> dataDuty = new ArrayList<List<Object>>();
        List<List<Object>> dataVAT = new ArrayList<List<Object>>();
        Map<String, List<Object>> dataVATMap = new HashMap<String, List<Object>>();

        int recordCount = file.getRecordCount();
        for (int i = 1; i <= recordCount; i++) {
            file.read();

            Integer type = Integer.parseInt(new String(file.getField("PP").getBytes(), "Cp866").trim());
            String codeCustomsGroup = new String(file.getField("KOD").getBytes(), "Cp866").trim();
            BigDecimal stav_a = new BigDecimal(new String(file.getField("STAV_A").getBytes(), "Cp866").trim());
            BigDecimal stav_s = new BigDecimal(new String(file.getField("STAV_S").getBytes(), "Cp866").trim());
            Date dateFrom = new Date(DateUtils.parseDate(new String(file.getField("DATE1").getBytes(), "Cp866").trim(), new String[]{"yyyyMMdd"}).getTime());
            Date dateTo = new Date(DateUtils.parseDate(new String(file.getField("DATE2").getBytes(), "Cp866").trim(), new String[]{"yyyyMMdd"}).getTime());

            switch (type) {
                case 1:
                    if (codeCustomsGroup.length() == 2)
                        registrationMap.put(codeCustomsGroup, stav_a);
                    break;
                case 2:
                    dataDuty.add(Arrays.asList((Object) codeCustomsGroup, codeCustomsGroup + String.valueOf(dateTo), registrationMap.get(codeCustomsGroup.substring(0, 2)), stav_a, stav_s, /*null, */dateFrom, dateTo));
                    break;
                case 4:
                    dataVATMap.put(codeCustomsGroup, Arrays.asList((Object) codeCustomsGroup, codeCustomsGroup + String.valueOf(dateTo), null, null, null, stav_a, dateFrom, dateTo));
                    break;
            }
        }
        file.close();
        tempFile.delete();

        Date defaultDateFrom = new Date(2010 - 1900, 0, 1);
        Date defaultDateTo = new Date(2040 - 1900, 11, 31);

        for (String tnved : tnvedSet) {
            List<Object> entry = dataVATMap.get(tnved);
            dataVAT.add(Arrays.asList(tnved, entry == null ? tnved + String.valueOf(defaultDateTo) : entry.get(1), entry == null ? BigDecimal.valueOf(20) : entry.get(5),
                    entry == null ? defaultDateFrom : entry.get(6), entry == null ? defaultDateTo : entry.get(7)));
        }
        return Arrays.asList(dataDuty, dataVAT);
    }

    private Set<String> getTNVEDSet(ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        Set<String> tnvedSet = new HashSet<String>();

        LCP<?> isCustomsGroup = is(getClass("CustomsGroup"));
        ImRevMap<Object, KeyExpr> keys = (ImRevMap<Object, KeyExpr>) isCustomsGroup.getMapKeys();
        KeyExpr key = keys.singleValue();
        QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);
        query.addProperty("codeCustomsGroup", getLCP("codeCustomsGroup").getExpr(context.getModifier(), key));
        query.and(isCustomsGroup.getExpr(key).getWhere());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(context);

        for (ImMap<Object, Object> entry : result.values()) {
            String tnved = (String) entry.get("codeCustomsGroup");
            if (tnved != null && tnved.trim().length() == 10)
                tnvedSet.add(tnved.trim());
        }
        return tnvedSet;
    }
}