package lsfusion.erp.utils;

import com.google.common.base.Throwables;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImList;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.form.entity.FormEntity;
import lsfusion.server.form.entity.ObjectEntity;
import lsfusion.server.form.entity.PropertyDrawEntity;
import lsfusion.server.form.instance.*;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.io.*;
import java.sql.SQLException;
import java.util.Map;

public abstract class ExportCSVActionProperty extends ScriptingActionProperty {
    String idForm;
    String idGroupObject;
    
    public ExportCSVActionProperty(ScriptingLogicsModule LM, String idForm, String idGroupObject, ValueClass... classes) {
        super(LM, classes);
        this.idForm = idForm;
        this.idGroupObject = idGroupObject;
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context, Map<String, DataObject> valuesMap, String filePath, boolean printHeader, String separator) throws SQLException, SQLHandledException {

        try {

            if (idForm != null && idGroupObject != null) {

                FormEntity formEntity = findForm(idForm);
                FormInstance formInstance = context.createFormInstance(formEntity, MapFact.<ObjectEntity, DataObject>EMPTY(),
                        context.getSession(), false, FormSessionScope.OLDSESSION, false, false, false, null);

                if (valuesMap != null)
                    for (Map.Entry<String, DataObject> entry : valuesMap.entrySet())
                        formInstance.forceChangeObject(formInstance.instanceFactory.getInstance(LM.getObjectEntityByName(formEntity, entry.getKey())), entry.getValue());
                
                File exportFile = new File(filePath);
                PrintWriter bw = new PrintWriter(exportFile, "cp1251");                             

                FormData formData = formInstance.getFormData(0);

                for(FormRow row : formData.rows) {
                    if(printHeader) {
                        String headerString = "";
                        ImList propertyDrawsList = formEntity.getPropertyDrawsList();
                        for(int i = 0; i<propertyDrawsList.size();i++) {
                            PropertyDrawInstance instance = ((PropertyDrawEntity) propertyDrawsList.get(i)).getInstance(formInstance.instanceFactory);
                            if(instance.toDraw != null) {
                                headerString += instance.propertyObject.property.caption + separator;
                            }
                        }
                        headerString = headerString.isEmpty() ? headerString : headerString.substring(0, headerString.length() - separator.length());
                        bw.println(headerString);
                        printHeader = false;
                    }
                    String rowString = "";
                    
                    ImList propertyDrawsList = formEntity.getPropertyDrawsList();
                    for(int i = 0; i<propertyDrawsList.size();i++) {
                        PropertyDrawInstance instance = ((PropertyDrawEntity) propertyDrawsList.get(i)).getInstance(formInstance.instanceFactory);
                        if(instance.toDraw != null) {
                            Object value = row.values.get(instance);
                            rowString += (value == null ? "" : value.toString()).trim() + separator;
                        }
                    }
                    rowString = rowString.isEmpty() ? rowString : rowString.substring(0, rowString.length() - separator.length());
                    bw.println(rowString);
                }
                bw.close();
                
            }

        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        } catch (FileNotFoundException e) {
            throw Throwables.propagate(e);
        } catch (UnsupportedEncodingException e) {
            throw Throwables.propagate(e);
        }
    }
}