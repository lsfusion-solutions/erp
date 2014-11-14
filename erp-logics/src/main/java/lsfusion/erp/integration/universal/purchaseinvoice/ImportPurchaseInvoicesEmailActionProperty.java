package lsfusion.erp.integration.universal.purchaseinvoice;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.impl.FileVolumeManager;
import com.github.junrar.rarfile.FileHeader;
import com.google.common.base.Throwables;
import lsfusion.base.BaseUtils;
import lsfusion.base.IOUtils;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.integration.universal.ImportDocumentActionProperty;
import lsfusion.erp.integration.universal.ImportDocumentSettings;
import lsfusion.interop.Compare;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.property.PropertyInterface;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ImportPurchaseInvoicesEmailActionProperty extends ImportDocumentActionProperty {

    public ImportPurchaseInvoicesEmailActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            DataSession session = context.getSession();

            LCP<PropertyInterface> isImportType = (LCP<PropertyInterface>) is(findClass("ImportType"));
            ImRevMap<PropertyInterface, KeyExpr> importTypeKeys = isImportType.getMapKeys();
            KeyExpr importTypeKey = importTypeKeys.singleValue();
            QueryBuilder<PropertyInterface, Object> importTypeQuery = new QueryBuilder<PropertyInterface, Object>(importTypeKeys);
            importTypeQuery.addProperty("autoImportEmailImportType", findProperty("autoImportEmailImportType").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("autoImportAccountImportType", findProperty("autoImportAccountImportType").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("autoImportCheckInvoiceExistenceImportType", findProperty("autoImportCheckInvoiceExistenceImportType").getExpr(session.getModifier(), importTypeKey));

            importTypeQuery.and(isImportType.getExpr(importTypeKey).getWhere());
            importTypeQuery.and(findProperty("autoImportImportType").getExpr(importTypeKey).getWhere());
            importTypeQuery.and(findProperty("autoImportEmailImportType").getExpr(importTypeKey).getWhere());
            ImOrderMap<ImMap<PropertyInterface, DataObject>, ImMap<Object, ObjectValue>> importTypeResult = importTypeQuery.executeClasses(session);

            for (int i = 0, size = importTypeResult.size(); i < size; i++) {
                ImMap<Object, ObjectValue> entryValue = importTypeResult.getValue(i);

                DataObject importTypeObject = importTypeResult.getKey(i).valueIt().iterator().next();

                ObjectValue accountObject = entryValue.get("autoImportAccountImportType");
                ObjectValue emailObject = entryValue.get("autoImportEmailImportType");
                boolean checkInvoiceExistence = entryValue.get("autoImportCheckInvoiceExistenceImportType") instanceof DataObject;
                String emailPattern = emailObject instanceof DataObject ? ((String) ((DataObject) emailObject).object).replace("*", ".*") : null;
                String staticNameImportType = (String) findProperty("staticNameImportTypeDetailImportType").read(session, importTypeObject);
                String staticCaptionImportType = (String) findProperty("staticCaptionImportTypeDetailImportType").read(session, importTypeObject);
                
                ImportDocumentSettings importDocumentSettings = readImportDocumentSettings(session, importTypeObject);
                String fileExtension = importDocumentSettings.getFileExtension();
                
                if (fileExtension != null && emailObject instanceof DataObject && accountObject instanceof DataObject) {

                    KeyExpr emailExpr = new KeyExpr("email");
                    KeyExpr attachmentEmailExpr = new KeyExpr("attachmentEmail");
                    ImRevMap<Object, KeyExpr> emailKeys = MapFact.toRevMap((Object) "email", emailExpr, "attachmentEmail", attachmentEmailExpr);

                    QueryBuilder<Object, Object> emailQuery = new QueryBuilder<Object, Object>(emailKeys);
                    emailQuery.addProperty("fromAddressEmail", findProperty("fromAddressEmail").getExpr(session.getModifier(), emailExpr));
                    emailQuery.addProperty("dateTimeReceivedEmail", findProperty("dateTimeReceivedEmail").getExpr(session.getModifier(), emailExpr));
                    emailQuery.addProperty("fileAttachmentEmail", findProperty("fileAttachmentEmail").getExpr(session.getModifier(), attachmentEmailExpr));
                    emailQuery.addProperty("nameAttachmentEmail", findProperty("nameAttachmentEmail").getExpr(session.getModifier(), attachmentEmailExpr));

                    emailQuery.and(findProperty("emailAttachmentEmail").getExpr(session.getModifier(), attachmentEmailExpr).compare(emailExpr, Compare.EQUALS));
                    emailQuery.and(findProperty("accountEmail").getExpr(session.getModifier(), emailExpr).compare(accountObject.getExpr(), Compare.EQUALS));
                    emailQuery.and(findProperty("notImportedAttachmentEmail").getExpr(session.getModifier(), attachmentEmailExpr).getWhere());
                    emailQuery.and(findProperty("fileAttachmentEmail").getExpr(session.getModifier(), attachmentEmailExpr).getWhere());

                    ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> emailResult = emailQuery.executeClasses(session);

                    for (int j = 0, sizej = emailResult.size(); j < sizej; j++) {
                        ImMap<Object, ObjectValue> emailEntryValue = emailResult.getValue(j);
                        ObjectValue attachmentEmailObject = emailResult.getKey(j).get("attachmentEmail");
                        Timestamp dateTimeReceivedEmail = (Timestamp) emailEntryValue.get("dateTimeReceivedEmail").getValue();
                        boolean isOld = (Calendar.getInstance().getTime().getTime() - dateTimeReceivedEmail.getTime()) > (24*60*60*1000); //старше 24 часов
                        String fromAddressEmail = (String) emailEntryValue.get("fromAddressEmail").getValue();
                        if (fromAddressEmail != null && emailPattern != null && fromAddressEmail.matches(emailPattern)) {
                            byte[] fileAttachment = BaseUtils.getFile((byte[]) emailEntryValue.get("fileAttachmentEmail").getValue());
                            String nameAttachmentEmail = trim((String) emailEntryValue.get("nameAttachmentEmail").getValue());
                            List<byte[]> files = new ArrayList<byte[]>();
                            if (nameAttachmentEmail != null) {
                                if (nameAttachmentEmail.toLowerCase().endsWith(".rar")) {
                                    files = unpackRARFile(fileAttachment, fileExtension);
                                    if(files.isEmpty())
                                        logImportError(context, attachmentEmailObject, "Архив пуст или повреждён", isOld);                                    
                                } else if (nameAttachmentEmail.toLowerCase().endsWith(".zip")) {
                                    files = unpackZIPFile(fileAttachment, fileExtension);
                                    if(files.isEmpty())
                                        logImportError(context, attachmentEmailObject, "Архив пуст или повреждён", isOld);
                                } else
                                    files.add(fileAttachment);
                            } 
                            
                            for(byte[] file : files) {
                                DataSession currentSession = context.createSession();
                                DataObject invoiceObject = currentSession.addObject((ConcreteCustomClass) findClass("Purchase.UserInvoice"));

                                try {

                                    int importResult = new ImportPurchaseInvoiceActionProperty(LM).makeImport(context,
                                            currentSession, invoiceObject, importTypeObject, file, fileExtension,
                                            importDocumentSettings, staticNameImportType, staticCaptionImportType, checkInvoiceExistence);
                                    if (importResult >= IMPORT_RESULT_OK)
                                        currentSession.apply(context);

                                    if (importResult >= IMPORT_RESULT_OK || isOld) {
                                        DataSession postImportSession = context.createSession();
                                        findProperty("importedAttachmentEmail").change(true, postImportSession, (DataObject) attachmentEmailObject);
                                        postImportSession.apply(context);
                                    }

                                } catch (Exception e) {
                                    logImportError(context, attachmentEmailObject, e.toString(), isOld);
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

    private void logImportError(ExecutionContext context, ObjectValue attachmentEmailObject, String error, boolean isOld) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        DataSession postImportSession = context.createSession();
        findProperty("lastErrorAttachmentEmail").change(error, postImportSession, (DataObject) attachmentEmailObject);
        if(isOld)
            findProperty("importedAttachmentEmail").change(true, postImportSession, (DataObject) attachmentEmailObject);
        postImportSession.apply(context);
    }
    
    private List<byte[]> unpackRARFile(byte[] fileBytes, String extensionFilter) {

        List<byte[]> result = new ArrayList<byte[]>();
        File inputFile = null;
        File outputFile = null;
        try {
            inputFile = File.createTempFile("email", ".rar");
            FileOutputStream stream = new FileOutputStream(inputFile);
            try {
                stream.write(fileBytes);
            } finally {
                stream.close();
            }

            List<File> dirList = new ArrayList<File>();
            File outputDirectory = new File(inputFile.getParent() + "/" + getFileName(inputFile));
            if(inputFile.exists() && (outputDirectory.exists() || outputDirectory.mkdir())) {
                dirList.add(outputDirectory);
                Archive a = new Archive(new FileVolumeManager(inputFile));

                FileHeader fh = a.nextFileHeader();

                while (fh != null) {
                    outputFile = new File(outputDirectory.getPath() + "/" + (fh.isUnicode() ? fh.getFileNameW() : fh.getFileNameString()));
                    File dir = outputFile.getParentFile();
                    dir.mkdirs();
                    if(!dirList.contains(dir))
                        dirList.add(dir);
                    if(!outputFile.isDirectory()) {
                        FileOutputStream os = new FileOutputStream(outputFile);
                        try {
                            a.extractFile(fh, os);
                        } finally {
                            os.close();
                        }
                        String outExtension = BaseUtils.getFileExtension(outputFile);
                        if (outExtension != null && extensionFilter.toLowerCase().equals(outExtension.toLowerCase()))
                            result.add(IOUtils.getFileBytes(outputFile));
                        outputFile.delete();
                    }
                    fh = a.nextFileHeader();
                }
                a.close();
            }

            for(File dir : dirList)
                if(dir != null && dir.exists())
                    dir.delete();
            
        } catch (RarException e) {
            throw Throwables.propagate(e);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        } finally {
            if(inputFile != null)
                inputFile.delete();
            if(outputFile != null)
                outputFile.delete();
        }
        return result;
    }
    
    private List<byte[]> unpackZIPFile(byte[] fileBytes, String extensionFilter) {

        List<byte[]> result = new ArrayList<byte[]>();
        File inputFile = null;
        File outputFile = null;
        try {
            inputFile = File.createTempFile("email", ".zip");
            FileOutputStream stream = new FileOutputStream(inputFile);
            try {
                stream.write(fileBytes);
            } finally {
                stream.close();
            }

            byte[] buffer = new byte[1024];
            Set<File> dirList = new HashSet<File>();
            File outputDirectory = new File(inputFile.getParent() + "/" + getFileName(inputFile));
            if(inputFile.exists() && (outputDirectory.exists() || outputDirectory.mkdir())) {
                dirList.add(outputDirectory);
                ZipInputStream inputStream = new ZipInputStream(new FileInputStream(inputFile), Charset.forName("cp866"));

                ZipEntry ze = inputStream.getNextEntry();
                while (ze != null) {
                    if(ze.isDirectory()) {
                        File dir = new File(outputDirectory.getPath() + "/" + ze.getName());
                        dir.mkdirs();
                        dirList.add(dir);
                    }
                    else {
                        outputFile = new File(outputDirectory.getPath() + "/" + ze.getName());
                        FileOutputStream outputStream = new FileOutputStream(outputFile);
                        int len;
                        while ((len = inputStream.read(buffer)) > 0) {
                            outputStream.write(buffer, 0, len);
                        }
                        outputStream.close();
                        String outExtension = BaseUtils.getFileExtension(outputFile);
                        if (outExtension != null && extensionFilter.toLowerCase().equals(outExtension.toLowerCase()))
                            result.add(IOUtils.getFileBytes(outputFile));      
                        outputFile.delete();
                    }
                    ze = inputStream.getNextEntry();
                }
                inputStream.closeEntry();
                inputStream.close();
            }
            
            for(File dir : dirList)
                if(dir != null && dir.exists())
                    dir.delete();
                    
        } catch (IOException e) {
            throw Throwables.propagate(e);
        } finally {
            if(inputFile != null) 
                inputFile.delete();
            if(outputFile != null)
                outputFile.delete();
        }
        return result;
    }

    public static String getFileName(File file) {
        String name = file.getName();
        int index = name.lastIndexOf(".");
        return (index == -1) ? "" : name.substring(0, index);
    }
}