package equ.clt.handler.bizerba;

import equ.api.scales.ScalesHandler;
import org.apache.log4j.Logger;

import javax.naming.CommunicationException;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BizerbaHandler extends ScalesHandler {

    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    protected static final int[] encoders1 = new int[]{65, 192, 66, 193, 194, 69, 195, 197, 198, 199, 75, 200, 77, 72, 79, 201, 80, 67, 84, 202, 203, 88, 208, 209, 210, 211, 212, 213, 215, 216, 217, 218, 97, 224, 236, 225, 226, 101, 227, 229, 230, 231, 237, 232, 238, 239, 111, 233};
    protected static final int[] encoders2 = new int[]{112, 99, 253, 234, 235, 120, 240, 241, 242, 243, 244, 245, 247, 248, 249, 250};
    
    protected static String charset = "UTF-8";
    protected static char separator = '\u001b';
    protected static String endCommand = separator + "BLK " + separator;
    
    protected String receiveReply(List<String> errors, TCPPort port) throws CommunicationException {
        String reply;
        Pattern var3 = Pattern.compile("QUIT(\\d+)");
        byte[] var4 = new byte[500];

        try {
            long var5 = (new Date()).getTime();

            long var7;
            do {
                if(port.getBisStream().available() != 0) {
                    port.getBisStream().read(var4);
                    reply = new String(var4, charset);

                    Matcher var10 = var3.matcher(reply);
                    if(var10.find()) {
                        return var10.group(1);
                    }
                }

                Thread.sleep(10L);
                var7 = (new Date()).getTime();
            } while(var7 - var5 <= 10000L);

            logError(errors, "Scales reply timeout");
        } catch (Exception e) {
            logError(errors, "Receive Reply Error", e);
        }
        return "-1";
    }

    protected String zeroedInt(int var1, int var2) {
        String var3;
        for (var3 = (new Integer(var1)).toString(); var3.length() < var2; var3 = "0" + var3) {
        }
        return var3.substring(var3.length() - 2);
    }
    
    protected void clearReceiveBuffer(TCPPort port) {
        while (true) {
            try {
                if (port.getBisStream().available() > 0) {
                    port.getBisStream().read();
                    continue;
                }
            } catch (Exception ignored) {
            }
            return;
        }
    }
    
    protected String makeString(int var1) {
        String var2 = Integer.toHexString(var1);
        if(var2.length() > 8) {
            var2 = var2.substring(0, 8);
        }
        while(var2.length() < 8) {
            var2 = '0' + var2;
        }
        var2 = var2.substring(0, 2) + '@' + var2.substring(2, 4) + '@' + var2.substring(4, 6) + '@' + var2.substring(6, 8);
        return var2.toUpperCase();
    }

    protected void encode(byte[] var1) {
        for(int var2 = 0; var2 < var1.length; ++var2) {
            if(var1[var2] <= -81 && var1[var2] >= -128) {
                var1[var2] = (byte)encoders1[128 + var1[var2]];
            } else if(var1[var2] <= -17 && var1[var2] >= -32) {
                var1[var2] = (byte)encoders2[32 + var1[var2]];
            }
        }
    }

    protected void decode(byte[] var1) {
        for(int var4 = 0; var4 < var1.length; ++var4) {
            boolean var3 = false;
            int var2 = var1[var4];
            if(var2 < 0) {
                var2 += 256;
            }

            int var5;
            for(var5 = 0; var5 < encoders1.length; ++var5) {
                if(var2 == encoders1[var5]) {
                    var1[var4] = (byte)(-128 + var5);
                    var3 = true;
                }
            }

            if(!var3) {
                for(var5 = 0; var5 < encoders2.length; ++var5) {
                    if(var2 == encoders2[var5]) {
                        var1[var4] = (byte)(-32 + var5);
                        var3 = true;
                    }
                }
            }
        }
        
    }
    
    protected String replaceDelimiter(String var1) {
        String var2 = var1.replace('\u0007', '\n');
        return var2;
    }

    protected void logError(List<String> errors, String errorText) {
        logError(errors, errorText, null);
    }

    protected void logError(List<String> errors, String errorText, Throwable t) {
        errors.add(errorText.replace("\u001b", "").replace("\u0000", "") + (t == null ? "" : ('\n' + t.toString())));
        processTransactionLogger.error(errorText, t);
    }
    
}
