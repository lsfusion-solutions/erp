package lsfusion.erp.region.by.integration.excel;

import jxl.write.WriteException;
import lsfusion.base.RawFileData;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

public class CreateExcelTemplateUOMsActionProperty extends CreateExcelTemplateActionProperty {

    public CreateExcelTemplateUOMsActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public Map<String, RawFileData> createFile() throws IOException, WriteException {
        return createFile("importUOMsTemplate",
                Arrays.asList("Код ед.изм.", "Ед.изм.", "Краткая ед.изм."),
                Arrays.asList(Arrays.asList("UOM1", "Штука", "шт")));
    }
}