package lsfusion.erp.utils;

import com.google.common.base.Throwables;
import lsfusion.base.file.FileData;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import org.apache.commons.io.FileUtils;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.sql.SQLException;
import java.util.Iterator;

public class ValidateXMLAction extends InternalAction {
    private final ClassPropertyInterface xsdInterface;
    private final ClassPropertyInterface xmlInterface;

    public ValidateXMLAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        xsdInterface = i.next();
        xmlInterface = i.next();
    }
    
    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            FileData xsdFileData = (FileData) context.getKeyValue(xsdInterface).getValue();
            FileData xmlFileData = (FileData) context.getKeyValue(xmlInterface).getValue();

            File schemaFile = File.createTempFile("validate", ".xsd");
            FileUtils.writeByteArrayToFile(schemaFile, xsdFileData.getRawFile().getBytes());

            String validateError = null;
            try {
                Schema schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(schemaFile);
                Validator validator = schema.newValidator();
                validator.validate(new StreamSource(new ByteArrayInputStream(xmlFileData.getRawFile().getBytes())));
            } catch (Exception e) {
                validateError = e.getMessage();
            }

            findProperty("validateError[]").change(validateError, context);

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }

    }
}