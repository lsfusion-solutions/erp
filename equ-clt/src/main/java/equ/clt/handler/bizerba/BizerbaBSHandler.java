package equ.clt.handler.bizerba;

import org.springframework.context.support.FileSystemXmlApplicationContext;

public class BizerbaBSHandler extends BizerbaHandler {

    public BizerbaBSHandler(FileSystemXmlApplicationContext springContext) {
        super(springContext);
    }

    @Override
    protected String getModel() {
        return "bizerbabs";
    }

    @Override
    protected String getCharset() {
        return "cp866";
    }

    @Override
    protected boolean isEncode() {
        return true;
    }

    @Override
    protected String getPricesCommand(int price, int retailPrice, boolean notInvertPrices) {
        return notInvertPrices ? super.getPricesCommand(price, retailPrice, true) : ("GPR1" + price + separator + "EXPR" + retailPrice + separator);
    }
}
