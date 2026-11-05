package equ.clt.handler.bizerba;

import org.springframework.context.support.FileSystemXmlApplicationContext;

public class BizerbaBCIIHandler extends BizerbaHandler {

    public BizerbaBCIIHandler(FileSystemXmlApplicationContext springContext) {
        super(springContext);
    }

    @Override
    protected String getModel() {
        return "bizerbabcii";
    }
}
