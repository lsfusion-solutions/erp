package equ.clt.handler.cas;

import org.springframework.context.support.FileSystemXmlApplicationContext;

public class CL5000DHandler extends CL5000JHandler {
    public CL5000DHandler(FileSystemXmlApplicationContext springContext) {
        super(springContext, 400);
    }

    @Override
    protected String getLogPrefix() {
        return "CL5000D: ";
    }
}