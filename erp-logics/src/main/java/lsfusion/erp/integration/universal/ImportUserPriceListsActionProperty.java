package lsfusion.erp.integration.universal;

import lsfusion.base.IOUtils;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.NullValue;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.io.File;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class ImportUserPriceListsActionProperty extends ScriptingActionProperty {

    public ImportUserPriceListsActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {

        try {

            LCP<?> isImportUserPriceListType = LM.is(getClass("ImportUserPriceListType"));
            ImRevMap<Object, KeyExpr> importUserPriceListTypeKeys = (ImRevMap<Object, KeyExpr>) isImportUserPriceListType.getMapKeys();
            KeyExpr importUserPriceListTypeKey = importUserPriceListTypeKeys.singleValue();
            QueryBuilder<Object, Object> importUserPriceListTypeQuery = new QueryBuilder<Object, Object>(importUserPriceListTypeKeys);
            importUserPriceListTypeQuery.addProperty("autoImportDirectoryImportUserPriceListType", getLCP("autoImportDirectoryImportUserPriceListType").getExpr(context.getModifier(), importUserPriceListTypeKey));
            importUserPriceListTypeQuery.addProperty("captionImportUserPriceListTypeFileExtensionImportUserPriceListType", getLCP("captionImportUserPriceListTypeFileExtensionImportUserPriceListType").getExpr(context.getModifier(), importUserPriceListTypeKey));
            importUserPriceListTypeQuery.addProperty("startRowImportUserPriceListType", getLCP("startRowImportUserPriceListType").getExpr(context.getModifier(), importUserPriceListTypeKey));
            importUserPriceListTypeQuery.addProperty("separatorImportUserPriceListType", getLCP("separatorImportUserPriceListType").getExpr(context.getModifier(), importUserPriceListTypeKey));
            importUserPriceListTypeQuery.addProperty("captionImportUserPriceListKeyTypeImportUserPriceListType", getLCP("captionImportUserPriceListKeyTypeImportUserPriceListType").getExpr(context.getModifier(), importUserPriceListTypeKey));

            importUserPriceListTypeQuery.addProperty("operationImportUserPriceListType", getLCP("operationImportUserPriceListType").getExpr(context.getModifier(), importUserPriceListTypeKey));
            
            importUserPriceListTypeQuery.and(isImportUserPriceListType.getExpr(importUserPriceListTypeKey).getWhere());
            importUserPriceListTypeQuery.and(getLCP("autoImportImportUserPriceListType").getExpr(importUserPriceListTypeKey).getWhere());
            importUserPriceListTypeQuery.and(getLCP("autoImportDirectoryImportUserPriceListType").getExpr(importUserPriceListTypeKey).getWhere());
            ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> importUserPriceListTypeResult = importUserPriceListTypeQuery.executeClasses(context);

            for (int i = 0, size = importUserPriceListTypeResult.size(); i < size; i++) {
                ImMap<Object, ObjectValue> entryValue = importUserPriceListTypeResult.getValue(i);

                DataObject importUserPriceListTypeObject = importUserPriceListTypeResult.getKey(i).valueIt().iterator().next();

                String directory = (String) entryValue.get("autoImportDirectoryImportUserPriceListType").getValue();
                String fileExtension = (String) entryValue.get("captionImportUserPriceListTypeFileExtensionImportUserPriceListType").getValue();
                Integer startRow = (Integer) entryValue.get("startRowImportUserPriceListType").getValue();
                startRow = startRow == null ? 1 : startRow;
                String csvSeparator = (String) entryValue.get("separatorImportUserPriceListType").getValue();
                String keyType = (String) entryValue.get("captionImportUserPriceListKeyTypeImportUserPriceListType").getValue();
                String dateRowString = (String) LM.findLCPByCompoundName("dateRowImportUserPriceListType").read(context, importUserPriceListTypeObject);
                Integer dateRow;
                try {
                    dateRow = dateRowString == null ? null : Integer.parseInt(dateRowString);
                } catch (Exception e) {
                    dateRow = null;
                }
                String dateColumnString = (String) LM.findLCPByCompoundName("dateColumnImportUserPriceListType").read(context, importUserPriceListTypeObject);
                Integer dateColumn;
                try {
                    dateColumn = dateColumnString == null ? null : Integer.parseInt(dateColumnString);
                } catch (Exception e) {
                    dateColumn = null;
                }

                ObjectValue operation = entryValue.get("operationImportUserPriceListType");
                DataObject operationObject = operation instanceof NullValue ? null : (DataObject) operation;
               
                Map<String, String[]> importColumns = ImportUserPriceListActionProperty.readImportColumns(context, LM, importUserPriceListTypeObject);
                Map<String, String[]> importPriceColumns = ImportUserPriceListActionProperty.readPriceImportColumns(context, LM, importUserPriceListTypeObject);               

                if (directory != null && fileExtension != null) {
                    File dir = new File(directory.trim());

                    if (dir.exists()) {

                        for (File f : dir.listFiles()) {
                            if (f.getName().toLowerCase().endsWith(fileExtension.trim().toLowerCase())) {
                                DataObject userPriceListObject = context.addObject((ConcreteCustomClass) LM.findClassByCompoundName("UserPriceList"));

                                try {

                                    boolean importResult = new ImportUserPriceListActionProperty(LM).importData(context, userPriceListObject, importColumns,
                                            importPriceColumns, IOUtils.getFileBytes(f), fileExtension.trim(), startRow,
                                            dateRow, dateColumn, csvSeparator == null ? null : csvSeparator.trim(), keyType, operationObject);

                                    if (importResult)
                                        renameImportedFile(context, f.getAbsolutePath(), "." + fileExtension.trim());

                                } catch (Exception e) {
                                    ServerLoggers.systemLogger.error(e);
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
            if (renamedFile != null && !importedFile.renameTo(renamedFile))
                context.requestUserInteraction(new MessageClientAction("Ошибка при переименовании импортированного файла " + oldPath, "Ошибка"));
        }
    }
}