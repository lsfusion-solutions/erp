package lsfusion.erp.utils;

import com.google.common.base.Throwables;
import lsfusion.base.file.FileData;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import org.apache.commons.io.FileUtils;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ValidateXMLAction extends InternalAction {
    private final ClassPropertyInterface xsdInterface;
    private final ClassPropertyInterface xmlInterface;

    public ValidateXMLAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        xsdInterface = i.next();
        xmlInterface = i.next();
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) {

        try {

            FileData xsdFileData = (FileData) context.getKeyValue(xsdInterface).getValue();
            FileData xmlFileData = (FileData) context.getKeyValue(xmlInterface).getValue();

            File schemaFile = File.createTempFile("validate", ".xsd");
            FileUtils.writeByteArrayToFile(schemaFile, xsdFileData.getRawFile().getBytes());


            Schema schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(schemaFile);
            Validator validator = schema.newValidator();

            final List<SAXParseException> exceptions = new ArrayList<>();
            validator.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException exception) {
                    exceptions.add(exception);
                }

                @Override
                public void fatalError(SAXParseException exception) {
                    exceptions.add(exception);
                }

                @Override
                public void error(SAXParseException exception) {
                    exceptions.add(exception);
                }
            });

            validator.validate(new StreamSource(new ByteArrayInputStream(xmlFileData.getRawFile().getBytes())));

            String validateError = "";
            for(SAXParseException exception : exceptions) {
                validateError += exception.getMessage() + "\n";
            }

            findProperty("validateError[]").change(validateError.isEmpty() ? null : validateError, context);

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }

    }
}