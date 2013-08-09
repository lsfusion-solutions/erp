package lsfusion.erp.region.by.machinery.cashregister.fiscalmercury;

import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.Dispatch;

import java.math.BigDecimal;

public class FiscalMercury {

    static Dispatch cashDispatch;
    static ActiveXComponent cashRegister;

    public final static int CASH_IN = 5;
    public final static int CASH_OUT = 6;
    public final static int FONT = 2;
    public final static int WIDTH = 28;
    public final static String delimiter = "";

    static void init(int comPort, int baudRate) {
        cashRegister = new ActiveXComponent("Incotex.MercuryFPrtX");
        cashRegister.setProperty("PortNum", comPort);
        cashRegister.setProperty("BaudRate", baudRate/*115200*/);
        cashRegister.setProperty("Password", "0000");

        cashDispatch = cashRegister.getObject();
        Dispatch.call(cashDispatch, "Open");
        Dispatch.call(cashDispatch, "SetDisplayBaudRate", 9600);
        try {
            Thread.sleep(100);
        }
        catch (Exception e) {
        }

    }

    public static void printReceipt(int comPort, int baudRate, int type, ReceiptInstance receipt) {
        //if (dispose) {
            dispose("Before PrintReceipt");
        //}

        openDocument(comPort, baudRate, type);

        try {
            //печать заголовка
            int k = printHeaderAndNumbers(cashDispatch, receipt);

            //печать товаров
            for (ReceiptItem item : receipt.receiptList) {
                Dispatch.call(cashDispatch, "AddCustom", item.barcode, FONT, 0, k++);
                String name = item.name.substring(0, Math.min(item.name.length(), WIDTH));
                Dispatch.call(cashDispatch, "AddCustom", name, FONT, 0, k++);

                Dispatch.invoke(cashDispatch, "AddItem", Dispatch.Method, new Object[]{0, item.price, false,
                        0, 1, 0, item.quantity.multiply(BigDecimal.valueOf(1000)), 3, 0, "шт.", 0, 0, k++, 0}, new int[1]);

                if (item.articleDiscSum.doubleValue() > 0) {
                    String msg = "Скидка " + item.articleDisc + "%, всего " + item.articleDiscSum;
                    Dispatch.call(cashDispatch, "AddCustom", msg, FONT, 0, k++);
                }
            }

            Dispatch.call(cashDispatch, "AddCustom", "Всего: " + receipt.sumTotal, FONT, 0, k++);
            if (receipt.clientDiscount != null) {
                Dispatch.call(cashDispatch, "AddCustom", "Скидка: " + receipt.clientDiscount, FONT, 0, k++);
            }

            //Общая информация
            Dispatch.call(cashDispatch, "AddCustom", delimiter, FONT, 0, k++);
            if (receipt.sumDisc > 0) {
                Dispatch.call(cashDispatch, "AddDocAmountAdj", -receipt.sumDisc, 0, FONT, 0, k++, 15);
            }
            Dispatch.call(cashDispatch, "AddTotal", FONT, 0, k++, 15);

            if (type == 1) {
                //выбор варианта оплаты
                boolean needChange = true;
                if (receipt.sumCash > 0 && receipt.sumCard > 0) {
                    Dispatch.call(cashDispatch, "AddPay", 4, receipt.sumCash, receipt.sumCard, "Pay", FONT, 0, k++, 15);
                } else if (receipt.sumCard > 0) {
                    Dispatch.call(cashDispatch, "AddPay", 2, receipt.sumCard, receipt.sumCard, "Pay", FONT, 0, k++, 25);
                    needChange = false;
                } else {
                    Dispatch.call(cashDispatch, "AddPay", 0, receipt.sumCash, receipt.sumCard, "Pay", FONT, 0, k++, 15);
                }

                if (needChange) {
                    Dispatch.call(cashDispatch, "AddChange", FONT, 0, k++, 15);
                }
            }
            Dispatch.call(cashDispatch, "CloseFiscalDoc");

        } catch (RuntimeException e) {
            cancelReceipt();
            throw e;
        }
        Dispatch.call(cashDispatch, "ExternalPulse", 1, 60, 10, 1);

        //if (dispose) {
            FiscalMercury.dispose("After PrintReceipt");
        //}
    }

    public static void openDocument(int comPort, int baudRate, int type) throws RuntimeException {
        Dispatch cashDispatch = getDispatch(comPort, baudRate);
        Dispatch.call(cashDispatch, "OpenFiscalDoc", type);
    }

    public static void closePort() throws RuntimeException {
        dispose("closePort");
    }

    public static void cancelReceipt() throws RuntimeException {
        Dispatch.call(cashDispatch, "CancelFiscalDoc", false);
    }

    public static void xReport(int comPort, int baudRate) throws RuntimeException {
        Dispatch cashDispatch = getDispatch(comPort, baudRate);
        Dispatch.call(cashDispatch, "XReport", 0/*FONT*/);
    }

    public static void zReport(int comPort, int baudRate) throws RuntimeException {
        Dispatch cashDispatch = getDispatch(comPort, baudRate);
        Dispatch.call(cashDispatch, "ZReport", 0/*FONT*/);
    }

    public static void advancePaper() throws RuntimeException {
        //Пока ничего не делаем
    }

    public static boolean inOut(int comPort, int baudRate, int type, double sum) throws RuntimeException {

        openDocument(comPort, baudRate, type);

        try {

            int k = printHeaderAndNumbers(cashDispatch, null);

            Dispatch.invoke(cashDispatch, "AddItem", Dispatch.Method, new Object[]{0, sum, false,
                    0, 1, 0, 1, 1, 0, "шт.", FONT, 0, k++, 0}, new int[1]);

            Dispatch.call(cashDispatch, "AddTotal", FONT, 0, k++, 15);

            Dispatch.call(cashDispatch, "CloseFiscalDoc");
        } catch (RuntimeException e) {
            cancelReceipt();
            throw e;
        }
        Dispatch.call(cashDispatch, "ExternalPulse", 1, 60, 10, 1);

        return true;
    }

    public static boolean openDrawer(int comPort, int baudRate) throws RuntimeException {
        Dispatch cashDispatch = getDispatch(comPort, baudRate);
        Dispatch.call(cashDispatch, "ExternalPulse", 1, 60, 10, 1);
        return true;
    }

    public static void displayText(ReceiptItem item) throws RuntimeException {

/*        int comPort = parameters.getScreenComPort();
        int fiscalCom = parameters.getFiscalComPort();
        int size = 20;
        if (comPort < 0) {
            if (fiscalCom < 0) {
                return;
            }
            size = 26;
        }

        String out[] = new String[5];
        for (Map.Entry<ExternalScreenComponent, ExternalScreenConstraints> entry : components.entrySet()) {
            out[entry.getValue().order] = entry.getKey().getValue();
        }

        String output = format(check(out[1]), check(out[2]), size) + format(check(out[3]), check(out[4]), size);
//        System.out.println(output);


        ActiveXComponent commActive = null;
        if (comPort > 0) {
            try {
                logger.info("Before creating ActiveX");
                commActive = new ActiveXComponent("MSCommLib.MSComm");
                commActive.setProperty("CommPort", comPort);
                commActive.setProperty("PortOpen", true);
                commActive.setProperty("Output", new String(output.getBytes("Cp866"), "Cp1251"));
                commActive.setProperty("PortOpen", false);
                logger.info("After ActiveX work");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (commActive != null) {
                    try {
                        if (commActive.getPropertyAsBoolean("PortOpen"))
                            commActive.setProperty("PortOpen", false);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } else {
            if (fiscalCom <= 0) {
                return;
            }
            if (comPort == 0) {
                return;
            }
            try {
                Dispatch cashDispatch = FiscalReg.getDispatch(fiscalCom);
                Dispatch.call(cashDispatch, "ShowDisplay", output, true, true);
                if (parameters.dispose) {
                    FiscalReg.dispose("ShowDisplay");
                }
            } catch (Exception e) {
                // пока игнорируем
            }*/


        /*try {
            String firstLine = " " + toStr(item.quantity) + "x" + toStr(BigDecimal.valueOf(item.price));
            firstLine = item.name.substring(0, 16 - Math.min(16, firstLine.length())) + firstLine;
            String secondLine = toStr(BigDecimal.valueOf(item.sumPos));
            while (secondLine.length() < 11)
                secondLine = " " + secondLine;
            secondLine = "ИТОГ:" + secondLine;
            if (!mercuryDLL.mercury.mercury_indik((firstLine + "\0").getBytes("cp1251"), (new String(secondLine + "\0")).getBytes("cp1251")))
                checkErrors(true);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }*/
    }

    public static int printHeaderAndNumbers(Dispatch cashDispatch, ReceiptInstance receipt) {
        int k = 0;
        //печать заголовка
        Dispatch.call(cashDispatch, "AddHeaderLine", 1, FONT, 0, k++);
        Dispatch.call(cashDispatch, "AddHeaderLine", 2, FONT, 0, k++);
        Dispatch.call(cashDispatch, "AddHeaderLine", 3, FONT, 0, k++);
        Dispatch.call(cashDispatch, "AddHeaderLine", 4, FONT, 0, k++);

        //печать номеров и даты
        Dispatch.call(cashDispatch, "AddSerialNumber", FONT, 0, k++);
        Dispatch.call(cashDispatch, "AddTaxPayerNumber", FONT, 0, k++);
        Dispatch.call(cashDispatch, "AddDateTime", FONT, 0, k++);
        Dispatch.call(cashDispatch, "AddDocNumber", FONT, 0, k++);
        Dispatch.call(cashDispatch, "AddReceiptNumber", FONT, 0, k++);
        Dispatch.call(cashDispatch, "AddOperInfo", 0, FONT, 0, k++);
        if (receipt != null) {
            if (receipt.cashierName != null) {
                Dispatch.call(cashDispatch, "AddCustom", getFiscalString("Продавец: " + receipt.cashierName), FONT, 0, k++);
            }

            if (receipt.clientName != null) {
                Dispatch.call(cashDispatch, "AddCustom", getFiscalString("Покупатель: " + receipt.clientName), FONT, 0, k++);

                if (receipt.clientSum != null) {
                    Dispatch.call(cashDispatch, "AddCustom", getFiscalString("Накопленная сумма: " + receipt.clientSum.intValue()), FONT, 0, k++);
                }
            }
        }
        Dispatch.call(cashDispatch, "AddCustom", delimiter, FONT, 0, k++);
        return k;
    }

    public static String getFiscalString(String str) {
        return str.substring(0, Math.min(str.length(), 28));
    }

    public static void dispose(String reason) {
        if (cashDispatch != null) {
            try {
                Dispatch.call(cashDispatch, "Close", false);
            } catch (Exception e) {
                throw new RuntimeException("Ошибка при закрытии соединения с фискальным регистратором\n" + reason, e);
            }
            cashDispatch = null;
            System.gc();
        }
    }

    public static Dispatch getDispatch(int comPort, int baudRate) {
        initDispatch(comPort, baudRate);
        return cashDispatch;
    }

    public static void initDispatch(int comPort, int baudRate) {
        if (cashDispatch == null) {
            init(comPort, baudRate);
        } else {
            try {
                Dispatch.call(cashDispatch, "TestConnection");
                if (!cashRegister.getProperty("Active").getBoolean()) {
                    init(comPort, baudRate);
                }
            } catch (Exception e) {
                init(comPort, baudRate);
            }
        }
    }
}