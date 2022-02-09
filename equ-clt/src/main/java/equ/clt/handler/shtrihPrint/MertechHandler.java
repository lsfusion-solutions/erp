package equ.clt.handler.shtrihPrint;

import org.springframework.context.support.FileSystemXmlApplicationContext;

public class MertechHandler extends ShtrihPrintHandler {

    public MertechHandler(FileSystemXmlApplicationContext springContext) {
        super(springContext);
    }

    @Override
    protected String getLogPrefix() {
        return "Mertech: ";
    }

    @Override
    protected String getPassword() {
        return "1234";
    }

}