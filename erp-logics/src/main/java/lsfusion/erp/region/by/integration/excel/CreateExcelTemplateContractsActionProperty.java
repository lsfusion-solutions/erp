package lsfusion.erp.region.by.integration.excel;

import jxl.write.WriteException;
import lsfusion.base.Pair;
import lsfusion.base.RawFileData;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.io.IOException;
import java.util.Arrays;

public class CreateExcelTemplateContractsActionProperty extends CreateExcelTemplateActionProperty {

    public CreateExcelTemplateContractsActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public Pair<String, RawFileData> createFile() throws IOException, WriteException {
        return createFile("importContractsTemplate",
                Arrays.asList("Номер договора", "Поставщик", "Покупатель", "Дата начала",
                        "Дата окончания", "Валюта"),
                Arrays.asList(Arrays.asList("123456", "ПС0010325", "ПС0010326", "01.01.2011", "31.12.2013", "BYN")));
    }
}