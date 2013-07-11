package lsfusion.erp.region.by.customs;

import lsfusion.base.IOUtils;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.classes.CustomStaticFormatFileClass;
import lsfusion.server.classes.DateClass;
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
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {

        try {
            CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, "Файлы DBF", "DBF");
            ObjectValue objectValue = context.requestUserData(valueClass, null);

            if (objectValue != null) {
                List<byte[]> fileList = valueClass.getFiles(objectValue.getValue());
                for (byte[] file : fileList) {
                    importDuties(context, file);
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

    private void importDuties(ExecutionContext<ClassPropertyInterface> context, byte[] fileBytes) throws IOException, xBaseJException, ScriptingErrorLog.SemanticErrorException, SQLException, ParseException {

        List<List<Object>> data = importDutiesFromDBF(context, fileBytes);

        List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
        List<ImportField> fields = new ArrayList<ImportField>();
        List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

        ImportField codeCustomsGroupField = new ImportField(LM.findLCPByCompoundName("codeCustomsGroup"));
        ImportKey<?> customsGroupKey = new ImportKey((CustomClass) LM.findClassByCompoundName("CustomsGroup"),
                LM.findLCPByCompoundName("customsGroupCode").getMapping(codeCustomsGroupField));
        keys.add(customsGroupKey);
        fields.add(codeCustomsGroupField);

        ImportField idCustomsRateField = new ImportField(LM.findLCPByCompoundName("idRegistrationCustomsRate"));
        fields.add(idCustomsRateField);

        ImportKey<?> registrationCustomsRateKey = new ImportKey((CustomClass) LM.findClassByCompoundName("RegistrationCustomsRate"),
                LM.findLCPByCompoundName("registrationCustomsRateId").getMapping(idCustomsRateField));
        keys.add(registrationCustomsRateKey);
        props.add(new ImportProperty(idCustomsRateField, LM.findLCPByCompoundName("idRegistrationCustomsRate").getMapping(registrationCustomsRateKey)));

        ImportKey<?> dutyCustomsRateKey = new ImportKey((CustomClass) LM.findClassByCompoundName("DutyCustomsRate"),
                LM.findLCPByCompoundName("dutyCustomsRateId").getMapping(idCustomsRateField));
        keys.add(dutyCustomsRateKey);
        props.add(new ImportProperty(idCustomsRateField, LM.findLCPByCompoundName("idDutyCustomsRate").getMapping(dutyCustomsRateKey)));

        ImportKey<?> VATCustomsRateKey = new ImportKey((CustomClass) LM.findClassByCompoundName("VATCustomsRate"),
                LM.findLCPByCompoundName("VATCustomsRateId").getMapping(idCustomsRateField));
        keys.add(VATCustomsRateKey);
        props.add(new ImportProperty(idCustomsRateField, LM.findLCPByCompoundName("idVATCustomsRate").getMapping(VATCustomsRateKey)));

        ImportField sumRegistrationCustomsRateField = new ImportField(LM.findLCPByCompoundName("sumRegistrationCustomsRate"));
        props.add(new ImportProperty(sumRegistrationCustomsRateField, LM.findLCPByCompoundName("sumRegistrationCustomsRate").getMapping(registrationCustomsRateKey)));
        props.add(new ImportProperty(codeCustomsGroupField, LM.findLCPByCompoundName("customsGroupRegistrationCustomsRate").getMapping(registrationCustomsRateKey),
                LM.object(LM.findClassByCompoundName("CustomsGroup")).getMapping(customsGroupKey)));
        fields.add(sumRegistrationCustomsRateField);

        ImportField percentDutyDutyCustomsRateField = new ImportField(LM.findLCPByCompoundName("percentDutyDutyCustomsRate"));
        props.add(new ImportProperty(percentDutyDutyCustomsRateField, LM.findLCPByCompoundName("percentDutyDutyCustomsRate").getMapping(dutyCustomsRateKey)));
        props.add(new ImportProperty(codeCustomsGroupField, LM.findLCPByCompoundName("customsGroupDutyCustomsRate").getMapping(dutyCustomsRateKey),
                LM.object(LM.findClassByCompoundName("CustomsGroup")).getMapping(customsGroupKey)));
        fields.add(percentDutyDutyCustomsRateField);

        ImportField weightDutyDutyCustomsRateField = new ImportField(LM.findLCPByCompoundName("weightDutyDutyCustomsRate"));
        props.add(new ImportProperty(weightDutyDutyCustomsRateField, LM.findLCPByCompoundName("weightDutyDutyCustomsRate").getMapping(dutyCustomsRateKey)));
        fields.add(weightDutyDutyCustomsRateField);

        ImportField rangeField = new ImportField(LM.findLCPByCompoundName("dataValueSupplierVATCustomsGroupDate"));
        ImportKey<?> rangeKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Range"),
                LM.findLCPByCompoundName("valueCurrentVATDefaultValue").getMapping(rangeField));
        props.add(new ImportProperty(rangeField, LM.findLCPByCompoundName("rangeVATCustomsRate").getMapping(VATCustomsRateKey),
                LM.object(LM.findClassByCompoundName("Range")).getMapping(rangeKey)));
        props.add(new ImportProperty(codeCustomsGroupField, LM.findLCPByCompoundName("customsGroupVATCustomsRate").getMapping(VATCustomsRateKey),
                LM.object(LM.findClassByCompoundName("CustomsGroup")).getMapping(customsGroupKey)));
        fields.add(rangeField);

        ImportField dateFromCustomsGroupField = new ImportField(DateClass.instance);
        props.add(new ImportProperty(dateFromCustomsGroupField, LM.findLCPByCompoundName("dateFromCustomsGroup").getMapping(customsGroupKey)));
        props.add(new ImportProperty(dateFromCustomsGroupField, LM.findLCPByCompoundName("dateFromRegistrationCustomsRate").getMapping(registrationCustomsRateKey)));
        props.add(new ImportProperty(dateFromCustomsGroupField, LM.findLCPByCompoundName("dateFromDutyCustomsRate").getMapping(dutyCustomsRateKey)));
        props.add(new ImportProperty(dateFromCustomsGroupField, LM.findLCPByCompoundName("dateFromVATCustomsRate").getMapping(VATCustomsRateKey)));
        fields.add(dateFromCustomsGroupField);

        ImportField dateToCustomsGroupField = new ImportField(DateClass.instance);
        props.add(new ImportProperty(dateToCustomsGroupField, LM.findLCPByCompoundName("dateToCustomsGroup").getMapping(customsGroupKey)));
        props.add(new ImportProperty(dateToCustomsGroupField, LM.findLCPByCompoundName("dateToRegistrationCustomsRate").getMapping(registrationCustomsRateKey)));
        props.add(new ImportProperty(dateToCustomsGroupField, LM.findLCPByCompoundName("dateToDutyCustomsRate").getMapping(dutyCustomsRateKey)));
        props.add(new ImportProperty(dateToCustomsGroupField, LM.findLCPByCompoundName("dateToVATCustomsRate").getMapping(VATCustomsRateKey)));
        fields.add(dateToCustomsGroupField);

        ImportTable table = new ImportTable(fields, data);

        DataSession session = context.createSession();
        IntegrationService service = new IntegrationService(session, table, keys, props);
        service.synchronize(true, false);
        session.apply(context.getBL());
        session.close();
    }

    private List<List<Object>> importDutiesFromDBF(ExecutionContext context, byte[] fileBytes) throws IOException, xBaseJException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException {

        Set<String> tnvedSet = getTNVEDSet(context);

        File tempFile = File.createTempFile("tempTnved", ".dbf");
        IOUtils.putFileBytes(tempFile, fileBytes);

        DBF file = new DBF(tempFile.getPath());

        Map<String, BigDecimal> registrationMap = new HashMap<String, BigDecimal>();

        List<List<Object>> data = new ArrayList<List<Object>>();
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
                        registrationMap.put(codeCustomsGroup, stav_s);
                    break;
                case 2:
                    data.add(Arrays.asList((Object) codeCustomsGroup, codeCustomsGroup + String.valueOf(dateTo), registrationMap.get(codeCustomsGroup.substring(0, 2)), stav_s, stav_a, null, dateFrom, dateTo));
                    break;
                case 4:
                    dataVATMap.put(codeCustomsGroup, Arrays.asList((Object) codeCustomsGroup, codeCustomsGroup + String.valueOf(dateTo), null, null, null, stav_a, dateFrom, dateTo));
                    break;
            }
        }

        Date defaultDateFrom = new Date(2010 - 1900, 0, 1);
        Date defaultDateTo = new Date(2040 - 1900, 11, 31);

        for (String tnved : tnvedSet) {
            List<Object> entry = dataVATMap.get(tnved);
            data.add(Arrays.asList(tnved, entry == null ? tnved + String.valueOf(defaultDateTo) : entry.get(1), entry == null ? null : entry.get(2),
                    entry == null ? null : entry.get(3), entry == null ? null : entry.get(4), entry == null ? BigDecimal.valueOf(20) : entry.get(5),
                    entry == null ? defaultDateFrom : entry.get(6), entry == null ? defaultDateTo : entry.get(7)));
        }

        return data;
    }

    private Set<String> getTNVEDSet(ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, SQLException {

        Set<String> tnvedSet = new HashSet<String>();

        LCP<?> isCustomsGroup = LM.is(getClass("CustomsGroup"));
        ImRevMap<Object, KeyExpr> keys = (ImRevMap<Object, KeyExpr>) isCustomsGroup.getMapKeys();
        KeyExpr key = keys.singleValue();
        QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);
        query.addProperty("codeCustomsGroup", getLCP("codeCustomsGroup").getExpr(context.getModifier(), key));
        query.and(isCustomsGroup.getExpr(key).getWhere());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(context.getSession().sql);

        for (ImMap<Object, Object> entry : result.values()) {
            String tnved = (String) entry.get("codeCustomsGroup");
            if (tnved != null && tnved.trim().length() == 10)
                tnvedSet.add(tnved.trim());
        }
        return tnvedSet;
    }
}