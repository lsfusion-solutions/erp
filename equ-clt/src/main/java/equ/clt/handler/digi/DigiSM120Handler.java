package equ.clt.handler.digi;

import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItem;
import equ.api.scales.TransactionScalesInfo;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static equ.clt.handler.HandlerUtils.safeMultiply;

public class DigiSM120Handler extends DigiHandler {

    private static String separator = ",";

    public DigiSM120Handler(FileSystemXmlApplicationContext springContext) {
        super(springContext);
    }

    @Override
    protected String getLogPrefix() {
        return "Digi SM120: ";
    }

    @Override
    protected SendTransactionTask getTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales) {
        return new DigiSM120SendTransactionTask(transaction, scales);
    }

    class DigiSM120SendTransactionTask extends DigiSendTransactionTask {
        private Integer nameLineFont;
        private Integer nameLineLength;
        private Integer descriptionLineFont;
        private Integer descriptionLineLength;

        public DigiSM120SendTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales) {
            super(transaction, scales);
        }

        @Override
        protected void initSettings() {
            DigiSM120Settings digiSettings = springContext.containsBean("digiSM120Settings") ? (DigiSM120Settings) springContext.getBean("digiSM120Settings") : null;
            nameLineFont = digiSettings != null ? digiSettings.getNameLineFont() : null;
            if (nameLineFont == null)
                nameLineFont = 7;
            nameLineLength = digiSettings != null ? digiSettings.getNameLineLength() : null;
            if (nameLineLength == null)
                nameLineLength = 28;
            descriptionLineFont = digiSettings != null ? digiSettings.getDescriptionLineFont() : null;
            if (descriptionLineFont == null)
                descriptionLineFont = 2;
            descriptionLineLength = digiSettings != null ? digiSettings.getDescriptionLineLength() : null;
            if (descriptionLineLength == null)
                descriptionLineLength = 55;
        }

        @Override
        protected boolean clearFiles(DataSocket socket, List<String> localErrors, boolean clearImages) throws IOException {
            return super.clearFiles(socket, localErrors, clearImages)
                    && clearFile(socket, localErrors, scales.port, fileIngredient)
                    && clearFile(socket, localErrors, scales.port, fileKeyAssignment);
        }

        @Override
        protected boolean sendPLU(DataSocket socket, List<String> localErrors, ScalesItem item, Integer plu) throws IOException {
            byte[] record = makePLURecord(item, plu, scales.pieceCodeGroupScales, scales.weightCodeGroupScales, nameLineFont, nameLineLength);
            processTransactionLogger.info(String.format(getLogPrefix() + "Sending plu file item %s to scales %s", plu, scales.port));
            int reply = sendRecord(socket, cmdWrite, filePLU, record);
            if(reply != 0)
                logError(localErrors, String.format(getLogPrefix() + "Send plu %s to scales %s failed. Error: %s", plu, scales.port, reply));
            return reply == 0;
        }

        @Override
        protected boolean sendIngredient(DataSocket socket, List<String> localErrors, ScalesItem item, Integer plu) throws IOException {
            if(item.description != null) {
                String description = item.description.replace("\r", "");
                int lineNumber = 1;
                int reply = sendIngredientRecord(socket, localErrors, plu, "delete", lineNumber, 2, descriptionLineFont);
                while (!description.isEmpty() && reply == 0) {
                    String lineData = description.substring(0, Math.min(description.length(), descriptionLineLength));
                    description = description.substring(lineData.length());
                    reply = sendIngredientRecord(socket, localErrors, plu, encodeText(lineData), lineNumber, 0, descriptionLineFont);
                    lineNumber++;
                }
                return reply == 0;
            } else return true;
        }

        @Override
        protected boolean sendKeyAssignment(DataSocket socket, List<String> localErrors, ScalesItem item, Integer plu) throws IOException {
            byte[] record = makeKeyAssignmentRecord(plu);
            if (record != null) {
                processTransactionLogger.info(String.format(getLogPrefix() + "Sending keyAssignment file item %s to scales %s", plu, scales.port));
                int reply = sendRecord(socket, cmdWrite, fileKeyAssignment, record);
                if (reply != 0)
                    logError(localErrors, String.format(getLogPrefix() + "Send keyAssignment %s to scales %s failed. Error: %s", plu, scales.port, reply));
                return reply == 0;
            } else return true;
        }

        private byte[] makePLURecord(ScalesItem item, Integer plu, String piecePrefix, String weightPrefix, Integer nameLineFont, Integer nameLineLength) throws IOException {
            int flagForDelete = 0; //No data/0: Add or Change, 1: Delete
            //временно весовой товар определяется как в старых Digi
            //int isWeight = item.splitItem ? 0 : 1; //0: Weighed item   1: Non-weighed item
            boolean isWeight = item.shortNameUOM != null && item.shortNameUOM.toUpperCase().startsWith("ШТ");
            int isWeightCode = isWeight ? 1 : 0;
            String price = getPrice(item.price); //max 9999.99
            String labelFormat1 = "017";
            String labelFormat2 = "0";
            String barcodeFormat = "5"; //F1F2 CCCCC XXXXX CD
            String barcodeFlagOfEANData = isWeight ? (piecePrefix != null ? piecePrefix : "21") : (weightPrefix != null ? weightPrefix : "20");

            String itemCodeOfEANData = plu + "00000"; //6-digit Item code + 4-digit Expanded item code
            String extendItemCodeOfEANData = "";
            String barcodeTypeOfEANData = "0"; //0: EAN 9: ITF
            String rightSideDataOfEANData = "1"; //0: Price 1: Weight 2: QTY 3: Original price 4: Weight/QTY 5: U.P. 6: U.P. after discount

            //Integer daysExpiry = item.expiryDate != null ? getDifferenceDaysFromToday(item.expiryDate) : item.daysExpiry != null ? item.daysExpiry : 0;
            Integer cellByDate = 0;//daysExpiry * 24; //days * 24
            String cellByTime = fillLeadingZeroes(item.hoursExpiry == null ? 0 : item.hoursExpiry, 2) + "00";//HHmm //не отображается

            String quantity = "0001";
            String quantitySymbol = "00"; //0 No print, 1 PCS, 2 FOR, 3 kg, 4 lb, 5 g, 6 oz

            String stepDiscountStartDate = "000000"; //YY MM DD
            String stepDiscountStartTime = "0000"; //HH MM
            String stepDiscountEndDate = "000000"; //YY MM DD
            String stepDiscountEndTime = "0000"; //HH MM

            String stepDiscountPoint1 = "000000"; //Weight or Quantity
            String stepDiscountValue1 = getPrice(item.price); //Price value or Percent value
            String stepDiscountPoint2 = "99999"; //Weight or Quantity
            String stepDiscountValue2 = getPrice(item.price); //Price value or Percent value
            String stepDiscountType = "02"; //"0: No step discount, 1: Free item, 2: Unit price discount, 3: Unit price % discount, 4: Total price discount, 5: Total price % discount, 6: Fixed price discount, 11: U.P./PCS - U.P./kg
            String typeOfMarkdown = "0"; //"0: No markdown, 1: Unit price markdown, 2: Price markdown, 3: Unit price and price markdown

            byte[] dataBytes = getBytes(plu + separator + flagForDelete + separator + isWeightCode + separator + "0" + separator +
                    "1" + separator + "1" + separator + "1" + separator + "1" + separator + "1" + separator  + "0" + separator +
                    "0" + separator + "0" + separator + "0" + separator + "0" + separator + "0" + separator + "0" + separator +
                    "0" + separator + "0" + separator + price + separator + labelFormat1 + separator + labelFormat2 + separator +
                    barcodeFormat + separator + barcodeFlagOfEANData + separator + itemCodeOfEANData + separator + extendItemCodeOfEANData + separator +
                    barcodeTypeOfEANData + separator + rightSideDataOfEANData + separator + "000000" + separator + "000000" + separator +
                    cellByDate + separator + cellByTime + separator + "000" + separator + "000" + separator + "0000" + separator +
                    "000000" + separator + "0000" + separator + quantity + separator + quantitySymbol + separator + "0" + separator +
                    "0000" + separator + "00" + separator + "00" + separator + "00" + separator + "00" + separator + "00" + separator +
                    "00" + separator + "00" + separator + "00" + separator + "00" + separator + "00" + separator + plu + separator +
                    plu + separator + "000000" + separator + "000000" + separator + "000000" + separator + "000000" + separator +
                    "000000" + separator + "000000" + separator + "000000" + separator + "000000" + separator + "000000" + separator +
                    "000000" + separator + "000000" + separator + "000000" + separator + "000000" + separator + "000000" + separator +
                    "000000" + separator + "000000" + separator + "000000" + separator + "000000" + separator + "000000" + separator +
                    "000000" + separator + "000000" + separator + "000000" + separator + stepDiscountStartDate + separator + stepDiscountStartTime + separator +
                    stepDiscountEndDate + separator + stepDiscountEndTime + separator + stepDiscountPoint1 + separator + stepDiscountValue1 + separator +
                    stepDiscountPoint2 + separator + stepDiscountValue2 + separator + stepDiscountType + separator + typeOfMarkdown + separator +
                    "0" + separator + "0" + separator + "0" + separator + "0" + separator + "0" + separator + "0" + separator +
                    "0" + separator + "0" + separator + "000000" + separator + "000000" + separator + "000000" + separator +
                    "000000" + separator + "000000" + separator + "00000000" + separator + "00000000" + separator + "00000" + separator +
                    "000000" + separator + getNameLines(item.name, nameLineFont, nameLineLength) + separator + "000000" + separator + "000000" + separator + "0" + separator + "000000" + separator +
                    "000000" + separator + "00" + separator + "00");

            int totalSize = dataBytes.length + 8;
            ByteBuffer bytes = ByteBuffer.allocate(totalSize);
            bytes.putInt(plu); //4 bytes
            bytes.putShort((short) totalSize); //2 bytes
            bytes.put(dataBytes);
            bytes.put(new byte[] {0x0d, 0x0a});

            return bytes.array();
        }

        private int sendIngredientRecord(DataSocket socket, List<String> localErrors, Integer plu, String lineData, int lineNumber, int flagForDelete, int fontSize) throws IOException {
            //FlagForDelete: No data/0: Add or Change   1: Delete line   2: Delete record
            byte[] record = makeIngredientRecord(plu, lineData, lineNumber, flagForDelete, fontSize);
            processTransactionLogger.info(String.format(getLogPrefix() + "Sending ingredient file item %s line %s to scales %s", plu, lineNumber, scales.port));
            int reply = sendRecord(socket, cmdWrite, fileIngredient, record);
            if (reply != 0)
                logError(localErrors, String.format(getLogPrefix() + "Send ingredient file item %s line %s to scales %s failed. Error: %s", plu, lineNumber, scales.port, reply));
            return reply;
        }

        private byte[] makeIngredientRecord(Integer plu, String lineData, Integer lineNumber, int flagForDelete, int fontSize) throws IOException {

            byte[] dataBytes = getBytes(plu + separator + lineNumber + separator + flagForDelete + separator + fontSize + separator + lineData);

            int totalSize = dataBytes.length + 8;
            ByteBuffer bytes = ByteBuffer.allocate(totalSize);

            //bytes.put(plu >>> 24); //first byte
            bytes.put((byte) (plu >>> 16)); //second byte
            bytes.put((byte) (plu >>> 8)); //third byte
            bytes.put(plu.byteValue()); //fourth byte
            bytes.put(lineNumber.byteValue()); // line number
            bytes.putShort((short) totalSize); //2 bytes
            bytes.put(dataBytes);
            bytes.put(new byte[]{0x0d, 0x0a});

            return bytes.array();
        }

        private byte[] makeKeyAssignmentRecord(Integer plu) throws IOException {
            String pluNumber = fillLeadingZeroes(plu, 6);
            int flagForDelete = 0; //No data/0: Add or Change, 1: Delete

            //хотя по документации можно до 256 товаров на каждой из 3 страниц, но кнопок на табло только 56
            if(plu > 0 && plu <= 56) {
                Integer pageNumber = 0;
                byte[] dataBytes = getBytes(pageNumber + separator + pluNumber + separator + flagForDelete + separator + pluNumber + separator +
                        "0" + separator + pluNumber);

                int totalSize = dataBytes.length + 8;
                ByteBuffer bytes = ByteBuffer.allocate(totalSize);
                bytes.putInt(plu); //4 bytes
                bytes.putShort((short) totalSize); //2 bytes
                bytes.put(dataBytes);
                bytes.put(new byte[]{0x0d, 0x0a});

                return bytes.array();
            } else return null;
        }

        private String getPrice(BigDecimal price) {
            price = safeMultiply(price, 100);
            return fillLeadingZeroes(price == null ? 0 : price.intValue(), 6);
        }

        private String getNameLines(String name, Integer lineFont, Integer lineLength) {
            String font = fillLeadingZeroes(lineFont, 2);
            String first = getNameLine(font, name.substring(0, Math.min(name.length(), lineLength)));
            String second = getNameLine(font, name.substring(Math.min(name.length(), lineLength), Math.min(name.length(), lineLength * 2)));
            String third = getNameLine(font, name.substring(Math.min(name.length(), lineLength * 2), Math.min(name.length(), lineLength * 3)));
            String fourth = getNameLine(font, name.substring(Math.min(name.length(), lineLength * 3), Math.min(name.length(), lineLength * 4)));
            return first + separator + second + separator + third + separator + fourth;
        }

        private String getNameLine(String font, String line) {
            return font + separator + encodeText(line);
        }

        private String encodeText(String line) {
            return "\"" + line.replace("\"", "\"\"") + "\"";
        }

        private int getDifferenceDaysFromToday(LocalDate date) {
            return (int) Math.max(0, Duration.between(date.atStartOfDay(), LocalDateTime.now()).toDays());
        }
    }
}