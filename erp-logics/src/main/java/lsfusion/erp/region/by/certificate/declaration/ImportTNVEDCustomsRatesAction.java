package lsfusion.erp.region.by.certificate.declaration;

import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.base.file.RawFileData;
import lsfusion.erp.integration.DefaultIntegrationAction;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.data.time.DateClass;
import lsfusion.server.logics.classes.user.CustomClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.service.*;
import org.apache.commons.lang3.time.DateUtils;
import org.xBaseJ.DBF;
import org.xBaseJ.xBaseJException;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.*;

public class ImportTNVEDCustomsRatesAction extends DefaultIntegrationAction {

    public ImportTNVEDCustomsRatesAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            ObjectValue objectValue = requestUserData(context, "Файлы DBF", "dbf");
            if (objectValue != null) {
                List<List<List<Object>>> data = importDutiesFromDBF(context, (RawFileData) objectValue.getValue());

                if (data.size() >= 1)
                    importDuty(context, data.get(0));
                if (data.size() >= 2)
                    importVAT(context, data.get(1));
            }

        } catch (xBaseJException | IOException | ScriptingErrorLog.SemanticErrorException | ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private void importDuty(ExecutionContext<ClassPropertyInterface> context, List<List<Object>> data) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<ImportProperty<?>> props = new ArrayList<>();
        List<ImportField> fields = new ArrayList<>();
        List<ImportKey<?>> keys = new ArrayList<>();

        ImportField codeCustomsGroupField = new ImportField(findProperty("code[CustomsGroup]"));
        ImportKey<?> customsGroupKey = new ImportKey((CustomClass) findClass("CustomsGroup"),
                findProperty("customsGroup[BPSTRING[10]]").getMapping(codeCustomsGroupField));
        keys.add(customsGroupKey);
        fields.add(codeCustomsGroupField);

        ImportField idCustomsRateField = new ImportField(findProperty("id[RegistrationCustomsRate]"));
        fields.add(idCustomsRateField);

        ImportKey<?> registrationCustomsRateKey = new ImportKey((CustomClass) findClass("RegistrationCustomsRate"),
                findProperty("registrationCustomsRate[STRING[100]]").getMapping(idCustomsRateField));
        keys.add(registrationCustomsRateKey);
        props.add(new ImportProperty(idCustomsRateField, findProperty("id[RegistrationCustomsRate]").getMapping(registrationCustomsRateKey)));

        ImportKey<?> dutyCustomsRateKey = new ImportKey((CustomClass) findClass("DutyCustomsRate"),
                findProperty("dutyCustomsRate[STRING[100]]").getMapping(idCustomsRateField));
        keys.add(dutyCustomsRateKey);
        props.add(new ImportProperty(idCustomsRateField, findProperty("id[DutyCustomsRate]").getMapping(dutyCustomsRateKey)));

        ImportField sumRegistrationCustomsRateField = new ImportField(findProperty("sum[RegistrationCustomsRate]"));
        props.add(new ImportProperty(sumRegistrationCustomsRateField, findProperty("sum[RegistrationCustomsRate]").getMapping(registrationCustomsRateKey)));
        props.add(new ImportProperty(codeCustomsGroupField, findProperty("customsGroup[RegistrationCustomsRate]").getMapping(registrationCustomsRateKey),
                object(findClass("CustomsGroup")).getMapping(customsGroupKey)));
        fields.add(sumRegistrationCustomsRateField);

        ImportField percentDutyDutyCustomsRateField = new ImportField(findProperty("percentDuty[DutyCustomsRate]"));
        props.add(new ImportProperty(percentDutyDutyCustomsRateField, findProperty("percentDuty[DutyCustomsRate]").getMapping(dutyCustomsRateKey)));
        props.add(new ImportProperty(codeCustomsGroupField, findProperty("customsGroup[DutyCustomsRate]").getMapping(dutyCustomsRateKey),
                object(findClass("CustomsGroup")).getMapping(customsGroupKey)));
        fields.add(percentDutyDutyCustomsRateField);

        ImportField weightDutyDutyCustomsRateField = new ImportField(findProperty("weightDuty[DutyCustomsRate]"));
        props.add(new ImportProperty(weightDutyDutyCustomsRateField, findProperty("weightDuty[DutyCustomsRate]").getMapping(dutyCustomsRateKey)));
        fields.add(weightDutyDutyCustomsRateField);

        ImportField dateFromCustomsGroupField = new ImportField(DateClass.instance);
        props.add(new ImportProperty(dateFromCustomsGroupField, findProperty("dateFrom[CustomsGroup]").getMapping(customsGroupKey)));
        props.add(new ImportProperty(dateFromCustomsGroupField, findProperty("dateFrom[RegistrationCustomsRate]").getMapping(registrationCustomsRateKey)));
        props.add(new ImportProperty(dateFromCustomsGroupField, findProperty("dateFrom[DutyCustomsRate]").getMapping(dutyCustomsRateKey)));
        fields.add(dateFromCustomsGroupField);

        ImportField dateToCustomsGroupField = new ImportField(DateClass.instance);
        props.add(new ImportProperty(dateToCustomsGroupField, findProperty("dateTo[CustomsGroup]").getMapping(customsGroupKey)));
        props.add(new ImportProperty(dateToCustomsGroupField, findProperty("dateTo[RegistrationCustomsRate]").getMapping(registrationCustomsRateKey)));
        props.add(new ImportProperty(dateToCustomsGroupField, findProperty("dateTo[DutyCustomsRate]").getMapping(dutyCustomsRateKey)));
        fields.add(dateToCustomsGroupField);

        try (ExecutionContext.NewSession newContext = context.newSession()) {
            integrationServiceSynchronize(newContext, fields, data, keys, props);
            newContext.apply();
        }
    }

    private void importVAT(ExecutionContext<ClassPropertyInterface> context, List<List<Object>> data) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<ImportProperty<?>> props = new ArrayList<>();
        List<ImportField> fields = new ArrayList<>();
        List<ImportKey<?>> keys = new ArrayList<>();

        ImportField codeCustomsGroupField = new ImportField(findProperty("code[CustomsGroup]"));
        ImportKey<?> customsGroupKey = new ImportKey((CustomClass) findClass("CustomsGroup"),
                findProperty("customsGroup[BPSTRING[10]]").getMapping(codeCustomsGroupField));
        keys.add(customsGroupKey);
        fields.add(codeCustomsGroupField);

        ImportField idCustomsRateField = new ImportField(findProperty("id[RegistrationCustomsRate]"));
        fields.add(idCustomsRateField);
       
        ImportKey<?> VATCustomsRateKey = new ImportKey((CustomClass) findClass("VATCustomsRate"),
                findProperty("VATCustomsRate[STRING[100]]").getMapping(idCustomsRateField));
        keys.add(VATCustomsRateKey);
        props.add(new ImportProperty(idCustomsRateField, findProperty("id[VATCustomsRate]").getMapping(VATCustomsRateKey)));
       
        ImportField rangeField = new ImportField(findProperty("dataValueSupplierVAT[CustomsGroup,DATE]"));
        ImportKey<?> rangeKey = new ImportKey((CustomClass) findClass("Range"),
                findProperty("valueCurrentVATDefault[NUMERIC[10,5]]").getMapping(rangeField));
        props.add(new ImportProperty(rangeField, findProperty("range[VATCustomsRate]").getMapping(VATCustomsRateKey),
                object(findClass("Range")).getMapping(rangeKey)));
        props.add(new ImportProperty(codeCustomsGroupField, findProperty("customsGroup[VATCustomsRate]").getMapping(VATCustomsRateKey),
                object(findClass("CustomsGroup")).getMapping(customsGroupKey)));
        fields.add(rangeField);

        ImportField dateFromCustomsGroupField = new ImportField(DateClass.instance);
        props.add(new ImportProperty(dateFromCustomsGroupField, findProperty("dateFrom[VATCustomsRate]").getMapping(VATCustomsRateKey)));
        fields.add(dateFromCustomsGroupField);

        ImportField dateToCustomsGroupField = new ImportField(DateClass.instance);
        props.add(new ImportProperty(dateToCustomsGroupField, findProperty("dateTo[VATCustomsRate]").getMapping(VATCustomsRateKey)));
        fields.add(dateToCustomsGroupField);

        try (ExecutionContext.NewSession newContext = context.newSession()) {
            integrationServiceSynchronize(newContext, fields, data, keys, props);
            newContext.apply();
        }
    }

    private List<List<List<Object>>> importDutiesFromDBF(ExecutionContext<ClassPropertyInterface> context, RawFileData fileBytes) throws IOException, xBaseJException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        Set<String> tnvedSet = getTNVEDSet(context);
        Map<String, BigDecimal> registrationMap = new HashMap<>();
        List<List<Object>> dataDuty = new ArrayList<>();
        List<List<Object>> dataVAT = new ArrayList<>();
        Map<String, List<Object>> dataVATMap = new HashMap<>();

        File tempFile = null;
        DBF dbfFile = null;
        try {

            tempFile = File.createTempFile("tempTnved", ".dbf");
            fileBytes.write(tempFile);

            dbfFile = new DBF(tempFile.getPath());
            int recordCount = dbfFile.getRecordCount();

            for (int i = 1; i <= recordCount; i++) {
                dbfFile.read();

                Integer type = Integer.parseInt(new String(dbfFile.getField("PP").getBytes(), "Cp866").trim());
                String codeCustomsGroup = new String(dbfFile.getField("KOD").getBytes(), "Cp866").trim();
                BigDecimal stav_a = new BigDecimal(new String(dbfFile.getField("STAV_A").getBytes(), "Cp866").trim());
                BigDecimal stav_s = new BigDecimal(new String(dbfFile.getField("STAV_S").getBytes(), "Cp866").trim());
                Date dateFrom = new Date(DateUtils.parseDate(new String(dbfFile.getField("DATE1").getBytes(), "Cp866").trim(), "yyyyMMdd").getTime());
                Date dateTo = new Date(DateUtils.parseDate(new String(dbfFile.getField("DATE2").getBytes(), "Cp866").trim(), "yyyyMMdd").getTime());

                switch (type) {
                    case 1:
                        if (codeCustomsGroup.length() == 2)
                            registrationMap.put(codeCustomsGroup, stav_a);
                        break;
                    case 2:
                        dataDuty.add(Arrays.asList(codeCustomsGroup, codeCustomsGroup + dateTo, registrationMap.get(codeCustomsGroup.substring(0, 2)), stav_a, stav_s, /*null, */sqlDateToLocalDate(dateFrom), sqlDateToLocalDate(dateTo)));
                        break;
                    case 4:
                        dataVATMap.put(codeCustomsGroup, Arrays.asList(codeCustomsGroup, codeCustomsGroup + dateTo, null, null, null, stav_a, sqlDateToLocalDate(dateFrom), sqlDateToLocalDate(dateTo)));
                        break;
                }
            }
        } finally {
            if(dbfFile != null)
                dbfFile.close();
            safeFileDelete(tempFile);
        }
        
        LocalDate defaultDateFrom = LocalDate.of(2001, 1, 1);
        LocalDate defaultDateTo = LocalDate.of(2040, 12, 31);

        for (String tnved : tnvedSet) {
            List<Object> entry = dataVATMap.get(tnved);
            dataVAT.add(Arrays.asList(tnved, entry == null ? tnved + defaultDateTo : entry.get(1), entry == null ? BigDecimal.valueOf(20) : entry.get(5),
                    entry == null ? defaultDateFrom : entry.get(6), entry == null ? defaultDateTo : entry.get(7)));
        }
        return Arrays.asList(dataDuty, dataVAT);
    }

    private Set<String> getTNVEDSet(ExecutionContext<ClassPropertyInterface> context) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        Set<String> tnvedSet = new HashSet<>();

        LP<?> isCustomsGroup = is(findClass("CustomsGroup"));
        ImRevMap<Object, KeyExpr> keys = (ImRevMap<Object, KeyExpr>) isCustomsGroup.getMapKeys();
        KeyExpr key = keys.singleValue();
        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
        query.addProperty("codeCustomsGroup", findProperty("code[CustomsGroup]").getExpr(context.getModifier(), key));
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