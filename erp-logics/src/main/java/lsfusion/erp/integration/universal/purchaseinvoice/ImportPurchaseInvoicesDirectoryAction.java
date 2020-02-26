package lsfusion.erp.integration.universal.purchaseinvoice;

import com.google.common.base.Throwables;
import jxl.read.biff.BiffException;
import lsfusion.base.BaseUtils;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.base.file.FTPPath;
import lsfusion.base.file.RawFileData;
import lsfusion.erp.ERPLoggers;
import lsfusion.erp.integration.universal.ImportDocumentAction;
import lsfusion.erp.integration.universal.ImportDocumentSettings;
import lsfusion.erp.integration.universal.UniversalImportException;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.property.oraction.PropertyInterface;
import lsfusion.server.physics.dev.integration.external.to.file.FileUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.xBaseJ.xBaseJException;

import java.io.*;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;

public class ImportPurchaseInvoicesDirectoryAction extends ImportDocumentAction {

    public ImportPurchaseInvoicesDirectoryAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public int makeImport(ExecutionContext.NewSession<ClassPropertyInterface> newContext, DataObject invoiceObject, DataObject importTypeObject, File f, String fileExtension,
                          ImportDocumentSettings settings, String staticNameImportType, String staticCaptionImportType, boolean completeIdItemAsEAN) throws ScriptingErrorLog.SemanticErrorException, IOException, ParseException, UniversalImportException, SQLHandledException, SQLException, BiffException, xBaseJException {
        return new ImportPurchaseInvoiceAction(LM).makeImport(newContext, invoiceObject,
                importTypeObject, new RawFileData(f), fileExtension, settings, staticNameImportType, staticCaptionImportType,
                completeIdItemAsEAN, false, false);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        super.executeInternal(context);
        try {

            LP<PropertyInterface> isImportType = (LP<PropertyInterface>) is(findClass("ImportType"));
            ImRevMap<PropertyInterface, KeyExpr> importTypeKeys = isImportType.getMapKeys();
            KeyExpr importTypeKey = importTypeKeys.singleValue();
            QueryBuilder<PropertyInterface, Object> importTypeQuery = new QueryBuilder<>(importTypeKeys);
            importTypeQuery.addProperty("autoImportDirectory", findProperty("autoImportDirectory[ImportType]").getExpr(context.getModifier(), importTypeKey));

            importTypeQuery.and(isImportType.getExpr(importTypeKey).getWhere());
            importTypeQuery.and(findProperty("autoImport[ImportType]").getExpr(importTypeKey).getWhere());
            importTypeQuery.and(findProperty("autoImportDirectory[ImportType]").getExpr(importTypeKey).getWhere());
            ImOrderMap<ImMap<PropertyInterface, DataObject>, ImMap<Object, ObjectValue>> importTypeResult = importTypeQuery.executeClasses(context);

            for (int i = 0, size = importTypeResult.size(); i < size; i++) {
                ImMap<Object, ObjectValue> entryValue = importTypeResult.getValue(i);

                DataObject importTypeObject = importTypeResult.getKey(i).valueIt().iterator().next();

                String directory = trim((String) entryValue.get("autoImportDirectory").getValue());
                String staticNameImportType = (String) findProperty("staticNameImportTypeDetail[ImportType]").read(context, importTypeObject);
                String staticCaptionImportType = (String) findProperty("staticCaptionImportTypeDetail[ImportType]").read(context, importTypeObject);
                boolean completeIdItemAsEAN = findProperty("completeIdItemAsEAN[ImportType]").read(context, importTypeObject) != null;

                ImportDocumentSettings settings = readImportDocumentSettings(context, importTypeObject);
                String fileExtension = settings.getFileExtension();
                boolean multipleDocuments = settings.isMultipleDocuments();

                if (directory != null && fileExtension != null) {
                    boolean ftp = directory.startsWith("ftp://");
                    Map<String, File> listFiles = listFiles(directory, ftp);
                    try {
                        for (Map.Entry<String, File> fileEntry : listFiles.entrySet()) {
                            String name = fileEntry.getKey();
                            File file = fileEntry.getValue();
                            if (file.getName().toLowerCase().endsWith(fileExtension.toLowerCase())) {
                                try (ExecutionContext.NewSession<ClassPropertyInterface> newContext = context.newSession()) {
                                    DataObject invoiceObject = multipleDocuments ? null : newContext.addObject((ConcreteCustomClass) findClass("Purchase.UserInvoice"));
                                    try {

                                        findAction("executeLocalEvents[TEXT]").execute(newContext, new DataObject("Purchase.UserInvoice"));

                                        int importResult = makeImport(newContext, invoiceObject, importTypeObject, file, fileExtension, settings, staticNameImportType, staticCaptionImportType, completeIdItemAsEAN);

                                        if (importResult != IMPORT_RESULT_ERROR) {
                                            if(ftp) {
                                                renameImportedFTP(context, directory, name, "." + fileExtension);
                                            } else {
                                                renameImportedFile(context, file.getAbsolutePath(), "." + fileExtension);
                                            }
                                        }

                                    } catch (Exception e) {
                                        ERPLoggers.importLogger.error("ImportPurchaseInvoices Error: ", e);
                                    }

                                    newContext.apply();
                                }
                            }
                        }
                    } finally {
                        if (ftp) {
                            for (File f : listFiles.values()) {
                                if (!f.delete())
                                    f.deleteOnExit();
                            }
                        }
                    }
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException | IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private Map<String, File> listFiles(String directory, boolean ftp) throws IOException {
        Map<String, File> result = new HashMap<>();

        if (ftp) {

            FTPPath ftpPath = FTPPath.parseFTPPath(directory.replace("ftp://", ""));
            FTPClient ftpClient = new FTPClient();
            try {
                ftpClient.setConnectTimeout(60000); //1 minute = 60 sec
                if (ftpPath.charset != null) ftpClient.setControlEncoding(ftpPath.charset);
                ftpClient.connect(ftpPath.server, ftpPath.port);
                ftpClient.login(ftpPath.username, ftpPath.password);
                if (ftpPath.passiveMode) {
                    ftpClient.enterLocalPassiveMode();
                }
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

                if (ftpPath.remoteFile == null || ftpPath.remoteFile.isEmpty() || ftpClient.changeWorkingDirectory(ftpPath.remoteFile)) {

                    for (FTPFile ftpFile : ftpClient.listFiles()) {
                        if (ftpFile.isFile()) {
                            File file = File.createTempFile("purchaseInvoiceFTP", "." + BaseUtils.getFileExtension(ftpFile.getName()));
                            OutputStream outputStream = new FileOutputStream(file);
                            boolean done = ftpClient.retrieveFile(ftpFile.getName(), outputStream);
                            outputStream.close();
                            if (done) {
                                result.put(ftpFile.getName(), file);
                            } else {
                                throw new RuntimeException(String.format("Path '%s' read error for %s", ftpFile.getName(), directory));
                            }
                        }
                    }

                } else {
                    throw new RuntimeException(String.format("Path '%s' not found for %s", ftpPath.remoteFile, directory));
                }
            } finally {
                if (ftpClient.isConnected()) {
                    ftpClient.logout();
                    ftpClient.disconnect();
                }
            }

        } else {

            File dir = new File(directory);
            if (dir.exists()) {
                File[] listFiles = dir.listFiles();
                if (listFiles != null) {
                    for(File f : listFiles) {
                        result.put(f.getName(), f);
                    }
                }
            }

        }
        return result;
    }

    protected void renameImportedFTP(ExecutionContext<ClassPropertyInterface> context, String directory, String name, String extension) throws IOException {

        String newExtensionUpCase = extension.substring(0, extension.length() - 1) + "E";
        String newExtensionLowCase = extension.toLowerCase().substring(0, extension.length() - 1) + "e";

        String renamed = name.endsWith(extension) ? name.replace(extension, newExtensionUpCase) : (name.endsWith(extension.toLowerCase()) ? name.replace(extension.toLowerCase(), newExtensionLowCase) : null);

        int i = 1;
        while (renamed != null && FileUtils.checkFileExists(directory + "/" + renamed)) {
            renamed = name.endsWith(extension) ? (name.replace(extension, "") + "(" + i + ")" + newExtensionUpCase) : (name.endsWith(extension.toLowerCase()) ? (name.replace(extension.toLowerCase(), "") + "(" + i + ")" + newExtensionLowCase) : null);
            i += 1;
        }

        FTPPath ftpPath = FTPPath.parseFTPPath(directory.replace("ftp://", ""));
        FTPClient ftpClient = new FTPClient();
        try {
            ftpClient.setConnectTimeout(60000); //1 minute = 60 sec
            if (ftpPath.charset != null) ftpClient.setControlEncoding(ftpPath.charset);
            ftpClient.connect(ftpPath.server, ftpPath.port);
            ftpClient.login(ftpPath.username, ftpPath.password);
            if (ftpPath.passiveMode) {
                ftpClient.enterLocalPassiveMode();
            }
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

            if (ftpPath.remoteFile == null || ftpPath.remoteFile.isEmpty() || ftpClient.changeWorkingDirectory(ftpPath.remoteFile)) {
                if (!ftpClient.rename(name, renamed)) {
                    context.requestUserInteraction(new MessageClientAction("Ошибка при переименовании импортированного файла " + name, "Ошибка"));
                }
            } else {
                throw new RuntimeException(String.format("Path '%s' not found for %s", ftpPath.remoteFile, directory));
            }
        } finally {
            if (ftpClient.isConnected()) {
                ftpClient.logout();
                ftpClient.disconnect();
            }
        }
    }
}