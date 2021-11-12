package lsfusion.erp.machinery.scales.dibal;

import com.google.common.base.Throwables;
import lsfusion.base.Pair;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.commons.lang.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class DibalSendImageToScaleAction extends InternalAction {
    public final ClassPropertyInterface ipInterface;
    public final ClassPropertyInterface indexImageInterface;
    public final ClassPropertyInterface imagePathInterface;

    public DibalSendImageToScaleAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        ipInterface = i.next();
        indexImageInterface = i.next();
        imagePathInterface = i.next();
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {
        String ip = (String) context.getDataKeyValue(ipInterface).getValue();
        Integer indexImage = (Integer) context.getDataKeyValue(indexImageInterface).getValue();
        String imagePath = (String) context.getDataKeyValue(imagePathInterface).getValue();

        try {
            List<byte[]> imageCommands = create8bitImage(getImageBytes(imagePath), indexImage);
            try (Socket socket = new Socket(ip, 3000)) {
                for (byte[] imageCommand : imageCommands) {
                    socket.getOutputStream().write(imageCommand);
                }
            }
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    //resize and convert to bmp
    private byte[] getImageBytes(String imagePath) throws IOException {
        File file = new File(imagePath);
        BufferedImage image = ImageIO.read(file);
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        double scale = (double) 210 / Math.max(imageWidth, imageHeight); //max width / height is 210
        try (ByteArrayOutputStream resizedOS = new ByteArrayOutputStream()) {
            Thumbnails.of(file).scale(scale, scale).toOutputStream(resizedOS);
            BufferedImage resizedImage = ImageIO.read(new ByteArrayInputStream(resizedOS.toByteArray()));
            try (ByteArrayOutputStream convertedOS = new ByteArrayOutputStream()) {
                ImageIO.write(resizedImage, "bmp", convertedOS);
                return convertedOS.toByteArray();
            }
        }
    }

    public final List<byte[]> create8bitImage(byte[] imageBytes, int article) {
        // ширина - 4 байта с 18 по 21
        int width = convertByteArrayToInt(new byte[]{imageBytes[18], imageBytes[19], imageBytes[20], imageBytes[21]});
        // высота - 4 байта с 18 по 21
        int height = convertByteArrayToInt(new byte[]{imageBytes[22], imageBytes[23], imageBytes[24], imageBytes[25]});

        //конвертирование пикселей из 32 битных в 16 битные
        Pair<List<Character>, int[]> convertedBytesAndImageColorsExists = createConvertPixelsFrom32To16Bit(imageBytes, width);
        List<Character> image16Bytes = convertedBytesAndImageColorsExists.first; //картинка, каждый пиксель сконвертирован в 16 битный
        int[] colorsExistArray = convertedBytesAndImageColorsExists.second; //массив существования цветов, цвет - индекс в массиве, значение - количество повторов

        List<Color> pallete = createPalette(colorsExistArray);
        byte[] palleteBytes = convertPalleteColorsToBytes(pallete);
        byte[] indexedPixels = convertPixelsColorsToPalleteColorsIndexes(pallete, image16Bytes);

        ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + palleteBytes.length + indexedPixels.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(width);
        buffer.putInt(height);
        buffer.put(palleteBytes);
        buffer.put(indexedPixels);

        return createRegistersFromImage(buffer.array(), article);
    }

    public int convertByteArrayToInt(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        return buffer.getInt();
    }

    // конвертирование всех пикселей картинки из 32 битных в 16 битные
    public final Pair<List<Character>, int[]> createConvertPixelsFrom32To16Bit(byte[] imageBytes, int width) {
        // глубина цвета, картинка должна быть rgb обычная 32 - разрядная
        int numBytesColor = convertByteArrayToInt(new byte[]{imageBytes[28], imageBytes[29], 0, 0}) / 8;
        // в файле картинки в каждой строке данных есть байты отступа, равные 0, которые дополняют строку
        // до количества байт, которое будет делиться на 4. весы эти байты ломают, их надо пропустить
        int skipPaddingBytes = (width * numBytesColor) % 4;

        List<Character> image16Bytes = new ArrayList<>(); //картинка, каждый пиксель сконвертирован в 16 битный
        List<Character> image16BytesOneRow = new ArrayList<>(); //одна строка сконвертированная
        int[] colorsExistArray = new int[65536]; // массив существования цветов, цвет - индекс в массиве, значение - количество повторов

        int startImage = convertByteArrayToInt(new byte[]{imageBytes[10], imageBytes[11], imageBytes[12], imageBytes[13]});
        if (startImage == 0) {
            startImage = 54;
        }
        int columnIndex = 0;
        while (startImage + 2 < imageBytes.length) {
            if (columnIndex * numBytesColor + skipPaddingBytes >= width * numBytesColor) {
                image16Bytes.addAll(0, image16BytesOneRow);
                image16BytesOneRow.clear();

                startImage += skipPaddingBytes;
                columnIndex = 0;

                continue;
            }

            char pix = (char) ((((imageBytes[startImage + 2] & 0xFF) >> 3) << 11) | (((imageBytes[startImage + 1] & 0xFF) >> 2) << 5) | ((imageBytes[startImage] & 0xFF) >> 3));
            image16BytesOneRow.add(pix);

            colorsExistArray[pix] += 1;

            startImage += numBytesColor;
            columnIndex++;
        }
        return Pair.create(image16Bytes, colorsExistArray);
    }

    public final List<Color> createColorsListFromExistsList(int[] colorsExistArray) {
        List<Color> colors = new ArrayList<>();
        for (int j = 0; j < 65536; j++) {
            if (colorsExistArray[j] != 0) {
                char colorCode = (char) j;
                colors.add(new Color(colorCode, (byte) ((colorCode >>> 11) & 0x1F), (byte) ((colorCode >>> 5) & 0x3F), (byte) (colorCode & 0x1F), colorsExistArray[j]));
            }
        }
        //sort by descending repetitions
        colors.sort((o1, o2) -> o1 != null && o2 != null ? (o2.repeated - o1.repeated) : 1);
        return colors;
    }

    public double getColorsDistance(double redColor, double greenColor, double blueColor, Color paletteColor) {
        double redColorDistance = Math.pow(redColor - paletteColor.red, 2.0);
        double greenColorDistance = Math.pow(greenColor - paletteColor.green, 2.0);
        double blueColorDistance = Math.pow(blueColor - paletteColor.blue, 2.0);
        return Math.sqrt(redColorDistance + greenColorDistance + blueColorDistance);
    }

    public final byte getNearestColor(List<Color> pallete, Character color) {
        byte greenColor = (byte) ((color >>> 5) & 0x3F);
        byte redColor = (byte) ((color >>> 11) & 0x1F);
        byte blueColor = (byte) (color & 0x1F);

        double minDistance = 1000000.0;
        byte nearestPaletteColorIndex = 0;

        for (int i = 0; i < pallete.size(); i++) {
            if (color == pallete.get(i).colorCode) {
                return (byte) i;
            } else {
                double colorsDistance = getColorsDistance(redColor, greenColor, blueColor, pallete.get(i));

                if (colorsDistance < minDistance) {
                    minDistance = colorsDistance;
                    nearestPaletteColorIndex = (byte) i;
                }
            }
        }
        return nearestPaletteColorIndex;
    }

    public final boolean isSimilarColor(List<Color> pallete, Color color, double criticalDistance) {
        for (Color c : pallete) {
            double redColorDistance = Math.pow(color.red - c.red, 2.0);
            double greenColorDistance = Math.pow(color.green - c.green, 2.0);
            double blueColorDistance = Math.pow(color.blue - c.blue, 2.0);
            double colorsDistance = Math.sqrt(redColorDistance + greenColorDistance + blueColorDistance);
            if (colorsDistance < criticalDistance) {
                return true;
            }
        }
        return false;
    }

    public final List<Color> createNewPalleteList(List<Color> colors) {
        List<Color> pallete = new ArrayList<>();
        //Если в изображении есть белый цвет, то добавляем его в палитру
        if (colors.stream().anyMatch(color -> color.colorCode == (char) 0xFFFF)) {
            pallete.add(new Color((char) 0xFFFF, (byte) 0x1F, (byte) 0x3F, (byte) 0x1F, 1));
        }
        //Если в изображении есть чёрный цвет, то добавляем его в палитру
        if (colors.stream().anyMatch(color -> color.colorCode == (char) 0x0000)) {
            pallete.add(new Color((char) 0x0000, (byte) 0, (byte) 0, (byte) 0, 1));
        }
        return pallete;
    }

    public List<Color> createPalette(int[] colorsExistArray) {
        List<Color> colors = createColorsListFromExistsList(colorsExistArray);
        List<Color> pallete = null;
        if (colors.size() > 255) {
            double distance;

            //Определение критического значения дистанции между цветами
            if (colors.size() < 500) {
                distance = 1.0;
            } else if (colors.size() < 1000) {
                distance = 1.5;
            } else if (colors.size() < 1250) {
                distance = 1.8;
            } else {
                distance = 2.0;
            }

            while (distance < 7.5) {
                pallete = createNewPalleteList(colors);

                //Формируем палитру из цветов по заданному значению расстояния
                for (Color color : colors) {
                    if (color.colorCode != 0 && color.colorCode != (char) 0xFFFF) {
                        if (!isSimilarColor(pallete, color, distance)) {
                            pallete.add(color);
                        }
                    }

                    if (pallete.size() >= 256) {
                        break;
                    }
                }

                //Если не удалось вместить цвета в палитру при таком критическом значении расстояния
                if (pallete.size() >= 256) {
                    //Создаём новую палитру
                    distance += 0.1;
                } else {
                    //Получена корректная палитра цветов
                    break;
                }
            }

            //Если палитра не заполнена до конца, то дополняем её цветами, которых в ней нет
            if (pallete.size() < 255) {
                for (Color c : colors) {
                    if (pallete.stream().noneMatch(color -> color.colorCode == c.colorCode)) {
                        pallete.add(c);
                    }
                    if (pallete.size() >= 256) {
                        break;
                    }
                }
            }

            //Для прозрачности
            pallete.set(255, new Color((char) 0xFFFF, (byte) 0x1F, (byte) 0x3F, (byte) 0x1F, 1));
        } else {
            pallete = createNewPalleteList(colors);

            //добавляем в палитру все цвета
            pallete.addAll(colors);

            //если цветов в палитре не хватает, то добавляем белый до конца
            if (colors.size() < 256) {
                for (int j = pallete.size(); j < 256; j++) {
                    pallete.add(new Color((char) 0xFFFF, (byte) 0x1F, (byte) 0x3F, (byte) 0x1F, 1));
                }
            }
        }
        return pallete;
    }

    //Создание таблицы соответствия между цветами и их индексами в палитре
    public int[] createMappingBetweenPalleteAndColors(List<Color> pallete) {
        int[] colorsMapping = new int[65536];

        //Устанавливаем соответствие между цветом и его индексом в палитре цветов
        for (int i = 0; i < pallete.size(); i++) {
            colorsMapping[pallete.get(i).colorCode] = i;
        }

        //Белый цвет должен быть 256-ым в палитре
        if (colorsMapping[0xFFFF] == 0) {
            colorsMapping[0xFFFF] = 255;
        }

        return colorsMapping;
    }

    //Замена цветов пикселей на индексы цветов в палитре
    public final byte[] convertPixelsColorsToPalleteColorsIndexes(List<Color> pallete, List<Character> image16Bytes) {
        int[] colorsMapping = createMappingBetweenPalleteAndColors(pallete);
        ByteBuffer buffer = ByteBuffer.allocate(image16Bytes.size());
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        for (Character item : image16Bytes) {
            if (colorsMapping[item] == 0) {
                colorsMapping[item] = getNearestColor(pallete, item);
            }
            buffer.put((byte) colorsMapping[item]);
        }

        return buffer.array();
    }

    //Преобразование цветов палитры из класс Color в массив байт
    public final byte[] convertPalleteColorsToBytes(List<Color> pallete) {
        ByteBuffer buffer = ByteBuffer.allocate(pallete.size() * 2);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (Color color : pallete) {
            buffer.putChar(color.colorCode);
        }
        return buffer.array();
    }

    // разбить картинку на регистры в соответствии с правилами
    public List<byte[]> createRegistersFromImage(byte[] imageBytes, int article) {
        List<byte[]> listMessages = new ArrayList<>();
        int countMessages = (imageBytes.length / 118) + 1;
        int nextByteInBmp = 0;
        int numMessage = 0;
        for (int i = 0; i < countMessages; i++) {
            String numMessageStr = prependZeroes(numMessage, 2);
            String posByte = getPosByte(i, countMessages);
            String startData = "01"/*scaleId*/ + "DL" + numMessageStr + "" + posByte + "2" + prependZeroes(article, 4);
            byte[] startDataBytes = startData.getBytes();

            byte[] message = new byte[130];
            System.arraycopy(startDataBytes, 0, message, 0, startDataBytes.length);

            for (int j = startDataBytes.length; j < 118 + startDataBytes.length; j++) {
                if (posByte.equals("3") && imageBytes.length >= nextByteInBmp) {
                    message[j] = 48;
                    continue;
                }
                message[j] = imageBytes[nextByteInBmp];
                nextByteInBmp++;
            }

            listMessages.add(message);

            numMessage++;
            if (numMessage == 100) numMessage = 0;
        }

        return listMessages;
    }

    public String prependZeroes(Object value, int length) {
        return StringUtils.leftPad(String.valueOf(value), length, "0");
    }

    // получить байт в команде, означающий первое, последнее или промежуточное сообщение формируется
    public final String getPosByte(int numMessage, int countMessages) {
        String posByte;
        if (numMessage == 0) {
            posByte = "1";
        } else if (numMessage != countMessages - 1) {
            posByte = "2";
        } else {
            posByte = "3";
        }
        return posByte;
    }

    public class Color {
        char colorCode;
        byte red;
        byte green;
        byte blue;
        int repeated;

        public Color(char colorCode, byte green, byte red, byte blue, int repeated) {
            this.colorCode = colorCode;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.repeated = repeated;
        }
    }
}
