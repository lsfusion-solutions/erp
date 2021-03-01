package equ.clt.handler.mettlerToledo;

import com.google.common.base.Throwables;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItem;
import equ.api.scales.TransactionScalesInfo;
import equ.clt.handler.MultithreadScalesHandler;
import lsfusion.base.ExceptionUtils;
import lsfusion.base.Pair;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static equ.clt.handler.HandlerUtils.safeMultiply;

public class MettlerToledoBPlusHandler extends MultithreadScalesHandler {

    protected String getLogPrefix() {
        return "Mettler Toledo BPlus: ";
    }

    @Override
    protected SendTransactionTask getTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales) {
        return new BPlusSendTransactionTask(transaction, scales);
    }

    class BPlusSendTransactionTask extends SendTransactionTask {

        public BPlusSendTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales) {
            super(transaction, scales);
        }

        @Override
        protected Pair<List<String>, Boolean> run() {
            String error = null;
            try {
                processTransactionLogger.info(getLogPrefix() + "Sending " + transaction.itemsList.size() + " items..." + scales.port);
                if(!loadPLU(scales, transaction)) {
                    processTransactionLogger.error(getLogPrefix() + "Failed to load PLU");
                }
            } catch (Throwable t) {
                error = String.format(getLogPrefix() + "IP %s error, transaction %s error: %s", scales.port, transaction.id, ExceptionUtils.getStackTraceString(t));
                processTransactionLogger.error(error, t);
            } finally {
                processTransactionLogger.info(getLogPrefix() + "Finally disconnecting..." + scales.port);
            }
            processTransactionLogger.info(getLogPrefix() + "Completed ip: " + scales.port);
            return Pair.create(error != null ? Collections.singletonList(error) : new ArrayList<>(), transaction.snapshot && error == null);
        }
    }

    private boolean loadPLU(ScalesInfo scales, TransactionScalesInfo transaction) throws IOException {
        File tmpFile = File.createTempFile("mettlertoledo", ".csv");
        File pluFile = new File(scales.directory + (transaction.snapshot ? "/pluSnapshot.csv" : "/plu.csv"));
        try {
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpFile), "cp1251"));

            bw.write(StringUtils.join(Arrays.asList("PLU Nr", "Article Nr", "PLU Name 1", "Unit Price 1", "Unit Of Measures 1", "Extended Text Nr", "Sell-By", "Segment1").iterator(), ";"));

            for (ScalesItem item : transaction.itemsList) {
                bw.newLine();

                String pluNumber = item.pluNumber != null ? String.valueOf(item.pluNumber) : item.idBarcode;
                String price = String.valueOf((double) safeMultiply(item.price, 100).intValue() / 100).replace(".", ",");
                String daysExpiry = item.daysExpiry != null ? String.valueOf(item.daysExpiry) : "0";
                String description = item.description != null ? item.description.replace("\n", "").replace(";", ",") : "";

                bw.write(StringUtils.join(Arrays.asList(pluNumber, item.idBarcode, item.name, price, item.idUOM, item.idBarcode, daysExpiry, description).iterator(), ";"));
            }
            bw.close();

            FileUtils.copyFile(tmpFile, pluFile);
        } finally {
            safeDelete(tmpFile);
        }

        return waitForDeletion(pluFile);
    }

    private boolean waitForDeletion(File file) {
        int count = 0;
        while (count < 60 && !Thread.currentThread().isInterrupted()) {
            try {
                count++;
                if (!file.exists()) {
                    processTransactionLogger.info(getLogPrefix() + String.format("file %s has been processed", file.getAbsolutePath()));
                    return true;
                }
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
            }
        }
        processTransactionLogger.info(getLogPrefix() + String.format("file %s hasn't been processed, timeout reached", file.getAbsolutePath()));
        return false;
    }
}