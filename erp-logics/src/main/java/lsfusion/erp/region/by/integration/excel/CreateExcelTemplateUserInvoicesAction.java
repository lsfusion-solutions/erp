package lsfusion.erp.region.by.integration.excel;

import jxl.write.WriteException;
import lsfusion.base.Pair;
import lsfusion.base.file.RawFileData;
import lsfusion.server.language.ScriptingLogicsModule;

import java.io.IOException;
import java.util.Arrays;

public class CreateExcelTemplateUserInvoicesAction extends CreateExcelTemplateAction {

    public CreateExcelTemplateUserInvoicesAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public Pair<String, RawFileData> createFile() throws IOException, WriteException {
        return createFile("importUserInvoicesTemplate",
                Arrays.asList("Серия", "Номер", "Дата", "Код товара", "Кол-во", "Поставщик", "Склад покупателя",
                        "Склад поставщика", "Цена", "Цена услуг", "Розничная цена", "Розничная надбавка", "Сертификат"),
                Arrays.asList(Arrays.asList("AA", "12345678", "12.12.2012", "1111", "150", "ПС0010325", "4444", "3333",
                        "5000", "300", "7000", "30", "№123456789")));
    }
}