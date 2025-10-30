package lsfusion.erp.utils;

import com.google.common.base.Throwables;
import lsfusion.base.col.interfaces.immutable.ImList;
import lsfusion.erp.integration.DefaultExportAction;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.form.struct.FormEntity;
import lsfusion.server.logics.form.struct.property.PropertyDrawEntity;
import lsfusion.server.logics.form.interactive.instance.FormData;
import lsfusion.server.logics.form.interactive.instance.FormInstance;
import lsfusion.server.logics.form.interactive.instance.FormRow;
import lsfusion.server.logics.form.interactive.instance.property.PropertyDrawInstance;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

import java.io.*;
import java.sql.SQLException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class ExportCSVAction extends DefaultExportAction {
    String idForm;
    String idGroupObject;

    public ExportCSVAction(ScriptingLogicsModule LM, String idForm, String idGroupObject, ValueClass... classes) {
        super(LM, classes);
        this.idForm = idForm;
        this.idGroupObject = idGroupObject;
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context, Map<String, DataObject> valuesMap, String filePath, boolean printHeader, String separator, String charset)
            throws SQLException, SQLHandledException {

        if (separator == null)
            separator = ";";
        if (charset == null)
            charset = "cp1251";

        try {

            if (idForm != null && idGroupObject != null) {

                FormEntity formEntity = findForm(idForm);
                //todo: replace deprecated createFormInstance after upgrading erp to 6.1
                FormInstance formInstance = context.createFormInstance(formEntity);

                if (valuesMap != null)
                    for (Map.Entry<String, DataObject> entry : valuesMap.entrySet())
                        formInstance.seekObject(formInstance.instanceFactory.getInstance(LM.getObjectEntityByName(formEntity, entry.getKey())), entry.getValue());

                    /*ftp://username:password@host:port/path_to_file*/
                Pattern connectionStringPattern = Pattern.compile("ftp:\\/\\/(.*):(.*)@(.*):([^\\/]*)(?:\\/(.*))?");
                Matcher connectionStringMatcher = connectionStringPattern.matcher(filePath);
                if (connectionStringMatcher.matches()) {
                    String username = connectionStringMatcher.group(1); //lstradeby
                    String password = connectionStringMatcher.group(2); //12345
                    String server = connectionStringMatcher.group(3); //ftp.harmony.neolocation.net
                    Integer port = Integer.parseInt(connectionStringMatcher.group(4)); //21
                    String remoteFile = connectionStringMatcher.group(5);

                    FTPClient ftpClient = new FTPClient();
                    File localFile = null;
                    try {

                        localFile = File.createTempFile("tmp", ".csv");
                        exportFile(formEntity, formInstance, localFile.getAbsolutePath(), separator, charset, printHeader);

                        ftpClient.setControlEncoding("UTF-8");
                        ftpClient.connect(server, port);
                        ftpClient.login(username, password);
                        ftpClient.enterLocalPassiveMode();
                        ftpClient.setFileType(FTP.BINARY_FILE_TYPE, FTP.BINARY_FILE_TYPE);
                        ftpClient.setFileTransferMode(FTP.BINARY_FILE_TYPE);

                        InputStream inputStream = new FileInputStream(localFile);
                        boolean done = ftpClient.storeFile(remoteFile, inputStream);
                        inputStream.close();
                        if (!done) {
                            throw new RuntimeException("Some error occurred while uploading file to ftp");
                        }

                    } catch (IOException e) {
                        throw Throwables.propagate(e);
                    } finally {
                        try {
                            safeFileDelete(localFile);
                            if (ftpClient.isConnected()) {
                                ftpClient.logout();
                                ftpClient.disconnect();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    if (filePath.startsWith("ftp://"))
                        context.delayUserInteraction(new MessageClientAction("Неверный формат ftp connection string. Правильный формат: ftp://username:password@host:port/path_to_file", "Ошибка"));
                    else
                        exportFile(formEntity, formInstance, filePath, separator, charset, printHeader);
                }
            }

        } catch (ScriptingErrorLog.SemanticErrorException | IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private void exportFile(FormEntity formEntity, FormInstance formInstance, String filePath, String separator, String charset, boolean printHeader)
            throws IOException, SQLException, SQLHandledException {
        File exportFile = new File(filePath);

        OutputStream os = new FileOutputStream(exportFile);
        if (charset.equals("UTF-8-BOM")) {
            os.write(239);
            os.write(187);
            os.write(191);
            charset = "UTF-8";
        }

        PrintWriter bw = new PrintWriter(new OutputStreamWriter(os, charset));

        FormData formData = formInstance.getFormData(0);

        for (FormRow row : formData.rows) {
            if (printHeader) {
                String headerString = "";
                ImList propertyDrawsList = formEntity.getPropertyDrawsList();
                for (int i = 0; i < propertyDrawsList.size(); i++) {
                    PropertyDrawEntity property = (PropertyDrawEntity) propertyDrawsList.get(i);
                    if(!isVirtualProperty(property)) {
                        PropertyDrawInstance instance = property.getInstance(formInstance.instanceFactory);
                        if (instance.toDraw != null) {
                            headerString += instance.getValueProperty().property.caption.toString() + separator;
                        }
                    }
                }
                headerString = headerString.isEmpty() ? headerString : headerString.substring(0, headerString.length() - separator.length());
                bw.print(headerString + "\r\n");
                printHeader = false;
            }
            String rowString = "";

            ImList propertyDrawsList = formEntity.getPropertyDrawsList();
            for (int i = 0; i < propertyDrawsList.size(); i++) {
                PropertyDrawEntity property = (PropertyDrawEntity) propertyDrawsList.get(i);
                if(!isVirtualProperty(property)) {
                    PropertyDrawInstance instance = property.getInstance(formInstance.instanceFactory);
                    if (instance.toDraw != null && instance.toDraw.getSID() != null && instance.toDraw.getSID().equals(idGroupObject)) {
                        Object value = row.values.get(instance);
                        rowString += (value == null ? "" : value.toString()).trim() + separator;
                    }
                }
            }
            rowString = rowString.isEmpty() ? rowString : rowString.substring(0, rowString.length() - separator.length());
            bw.print(rowString + "\r\n");
        }
        bw.close();
    }

    protected boolean checkDirectory(String directory) {
        return directory != null && (directory.startsWith("ftp://") || new File(directory).exists());
    }

    private boolean isVirtualProperty(PropertyDrawEntity property) {
        String sid = property.getSID();
        return sid != null && sid.equals("count()");
    }
}