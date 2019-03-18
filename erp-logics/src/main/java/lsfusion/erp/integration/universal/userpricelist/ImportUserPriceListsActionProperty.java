package lsfusion.erp.integration.universal.userpricelist;

import lsfusion.base.file.RawFileData;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.integration.DefaultImportActionProperty;
import lsfusion.erp.integration.universal.ImportColumnDetail;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.builder.QueryBuilder;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.property.oraction.PropertyInterface;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.io.File;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ImportUserPriceListsActionProperty extends DefaultImportActionProperty {

    public ImportUserPriceListsActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            LP<PropertyInterface> isImportUserPriceListType = (LP<PropertyInterface>) is(findClass("ImportUserPriceListType"));
            ImRevMap<PropertyInterface, KeyExpr> importUserPriceListTypeKeys = isImportUserPriceListType.getMapKeys();
            KeyExpr importUserPriceListTypeKey = importUserPriceListTypeKeys.singleValue();
            QueryBuilder<PropertyInterface, Object> importUserPriceListTypeQuery = new QueryBuilder<>(importUserPriceListTypeKeys);
            importUserPriceListTypeQuery.addProperty("autoImportDirectoryImportUserPriceListType", findProperty("autoImportDirectory[ImportUserPriceListType]").getExpr(context.getModifier(), importUserPriceListTypeKey));

            importUserPriceListTypeQuery.and(isImportUserPriceListType.getExpr(importUserPriceListTypeKey).getWhere());
            importUserPriceListTypeQuery.and(findProperty("autoImport[ImportUserPriceListType]").getExpr(importUserPriceListTypeKey).getWhere());
            importUserPriceListTypeQuery.and(findProperty("autoImportDirectory[ImportUserPriceListType]").getExpr(importUserPriceListTypeKey).getWhere());
            ImOrderMap<ImMap<PropertyInterface, DataObject>, ImMap<Object, ObjectValue>> importUserPriceListTypeResult = importUserPriceListTypeQuery.executeClasses(context);

            for (int i = 0, size = importUserPriceListTypeResult.size(); i < size; i++) {
                ImMap<Object, ObjectValue> entryValue = importUserPriceListTypeResult.getValue(i);

                DataObject importUserPriceListTypeObject = importUserPriceListTypeResult.getKey(i).valueIt().iterator().next();

                String directory = (String) entryValue.get("autoImportDirectoryImportUserPriceListType").getValue();

                ImportUserPriceListActionProperty imp = new ImportUserPriceListActionProperty(LM);
                List<LinkedHashMap<String, ImportColumnDetail>> importColumns = imp.readImportColumns(context, importUserPriceListTypeObject);
                ImportPriceListSettings settings = imp.readImportPriceListSettings(context, importUserPriceListTypeObject);
                Map<DataObject, String[]> priceColumns = imp.readPriceImportColumns(context, importUserPriceListTypeObject);

                if (directory != null && settings.getFileExtension() != null) {
                    File dir = new File(trim(directory));

                    if (dir.exists()) {

                        File[] listFiles = dir.listFiles();
                        if (listFiles != null) {
                            for (File f : listFiles) {
                                if (f.getName().toLowerCase().endsWith(settings.getFileExtension().toLowerCase())) {
                                    DataObject userPriceListObject = context.addObject((ConcreteCustomClass) findClass("UserPriceList"));

                                    try {

                                        boolean importResult = new ImportUserPriceListActionProperty(LM).importData(context,
                                                userPriceListObject, settings, priceColumns, importColumns.get(0), importColumns.get(1), new RawFileData(f),
                                                true);

                                        if (importResult)
                                            renameImportedFile(context, f.getAbsolutePath(), "." + settings.getFileExtension());

                                    } catch (Exception e) {
                                        ServerLoggers.importLogger.error("ImportUserPriceLists Error: ", e);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }

    protected void renameImportedFile(ExecutionContext context, String oldPath, String extension) {
        File importedFile = new File(oldPath);
        String newExtensionUpCase = extension.substring(0, extension.length() - 1) + "E";
        String newExtensionLowCase = extension.toLowerCase().substring(0, extension.length() - 1) + "e";
        if (importedFile.isFile()) {
            File renamedFile = oldPath.endsWith(extension) ? new File(oldPath.replace(extension, newExtensionUpCase)) :
                    (oldPath.endsWith(extension.toLowerCase()) ? new File(oldPath.replace(extension.toLowerCase(), newExtensionLowCase)) : null);
            int i = 1;
            while (renamedFile != null && renamedFile.exists()) {
                renamedFile = oldPath.endsWith(extension) ? new File(oldPath.replace(extension, "") + "(" + i + ")" + newExtensionUpCase) :
                        (oldPath.endsWith(extension.toLowerCase()) ? new File(oldPath.replace(extension.toLowerCase(), "") + "(" + i + ")" + newExtensionLowCase) : null);
                i += 1;
            }
            if (renamedFile == null || !importedFile.renameTo(renamedFile))
                context.requestUserInteraction(new MessageClientAction("Ошибка при переименовании импортированного файла " + oldPath, "Ошибка"));
        }
    }
}