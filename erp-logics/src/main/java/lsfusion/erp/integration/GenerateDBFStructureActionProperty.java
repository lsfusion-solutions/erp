package lsfusion.erp.integration;

import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.xBaseJ.DBF;
import org.xBaseJ.fields.Field;
import org.xBaseJ.xBaseJException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;

public class GenerateDBFStructureActionProperty extends ScriptingActionProperty {

    public GenerateDBFStructureActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, new ValueClass[]{});
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {
            String path = (String) LM.findLCPByCompoundOldName("generateDBFStructurePath").read(context);

            File dir = new File(path);
            if (dir.isDirectory()) {
                for (String filePath : dir.list()) {
                    if (filePath.endsWith("dbf") || filePath.endsWith("DBF")) {

                        PrintWriter writer = new PrintWriter(dir  + "\\" + filePath.substring(0, filePath.length()-3) + "txt");
                        writer.println("OverJDBField[] fields = {");

                        DBF dbfFile = new DBF(dir + "\\" + filePath);

                        for (int i = 1; i <= dbfFile.getFieldCount(); i++) {
                            Field field = dbfFile.getField(i);
                            if (i < dbfFile.getFieldCount()) {
                                writer.println(String.format("new OverJDBField(\"%s\", \'%s\', %d, %d),", field.getName(), field.getType(), field.getLength(), field.getDecimalPositionCount()));
                            } else {
                                writer.println(String.format("new OverJDBField(\"%s\", \'%s\', %d, %d)", field.getName(), field.getType(), field.getLength(), field.getDecimalPositionCount()));
                                writer.println("};");
                            }
                        }
                        
                        writer.println("DBFWriter dbfwriter = new DBFWriter(path, fields, \"CP866\");");
                        writer.println("dbfwriter.addRecord(new Object[]{});");
                        writer.println("dbfwriter.close();");
                        writer.close();
                    }
                }
            }


        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        } catch (xBaseJException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

