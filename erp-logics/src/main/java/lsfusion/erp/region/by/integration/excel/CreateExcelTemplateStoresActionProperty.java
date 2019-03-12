package lsfusion.erp.region.by.integration.excel;

import jxl.write.WriteException;
import lsfusion.base.Pair;
import lsfusion.base.file.RawFileData;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.io.IOException;
import java.util.Arrays;

public class CreateExcelTemplateStoresActionProperty extends CreateExcelTemplateActionProperty {

    public CreateExcelTemplateStoresActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public Pair<String, RawFileData> createFile() throws IOException, WriteException {
        return createFile("importStoresTemplate",
                Arrays.asList("Код магазина", "Имя", "Адрес магазина", "Код организации"),
                Arrays.asList(Arrays.asList("12345", "Магазин №1", "ЛИДА,СОВЕТСКАЯ, 24,231300", "ПС0010325")));
    }
}