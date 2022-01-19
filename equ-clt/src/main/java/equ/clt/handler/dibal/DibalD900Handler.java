package equ.clt.handler.dibal;

import equ.api.scales.ScalesItem;
import lsfusion.base.Pair;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DibalD900Handler extends DibalD500Handler {

    public DibalD900Handler(FileSystemXmlApplicationContext springContext) {
        super(springContext);
    }

    @Override
    protected String getLogPrefix() {
        return "Dibal D900: ";
    }

    @Override
    List<byte[]> getImageData(ScalesItem item, String idItemGroup) {
        List<byte[]> data = new ArrayList<>();
        if(item.groupImage != null) {
            data.addAll(createImageCommands(item.groupImage.getBytes(), idItemGroup));
        }
        if(item.itemImage != null) {
            data.addAll(createImageCommands(item.itemImage.getBytes(), String.valueOf(item.pluNumber + 100)));
        }
        return data;
    }

    private List<byte[]> createImageCommands(byte[] imageBytes, String article) {
        // ширина - 4 байта с 18 по 21 включая
        int width = convertByteArrayToInt(new byte[]{imageBytes[18], imageBytes[19], imageBytes[20], imageBytes[21]});

        // высота - 4 байта с 18 по 21 включая
        int height = convertByteArrayToInt(new byte[]{imageBytes[22], imageBytes[23], imageBytes[24], imageBytes[25]});

        //конвертирование пикселей из 32 битных в 16 битные
        Pair<List<Character>, int[]> convertedBytesAndImageColorsExists = createConvertPixelsFrom32To16Bit(imageBytes, width);
        List<Character> image16Bytes = convertedBytesAndImageColorsExists.first; //картинка, каждый пиксел сконвертирован в 16 битный
        int[] colorsExistArray = convertedBytesAndImageColorsExists.second; //массив существования цветов, цвет - индекс в массиве, значение - количество повторов

        List<Color> colors = createColorsListFromExistsList(colorsExistArray);
        sortColorsByDescendingRepetitionsCount(colors);

        List<Color> pallete = createPalette(colors);
        int[] colorsMapping = createMappingBetweenPalleteAndColors(pallete);

        List<Byte> indexedPixels = convertPixelsColorsToPalleteColorsIndexes(pallete, image16Bytes, colorsMapping);
        List<Byte> palleteBytes = convertPalleteColorsToBytes(pallete);

        List<Byte> resultImage = new ArrayList<>();
        resultImage.addAll(Arrays.asList(ArrayUtils.toObject(convertIntToByteArray(width))));
        resultImage.addAll(Arrays.asList(ArrayUtils.toObject(convertIntToByteArray(height))));
        resultImage.addAll(palleteBytes);
        resultImage.addAll(indexedPixels);


        return createRegistersFromImage(resultImage, article);
    }

    private int convertByteArrayToInt(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        return buffer.getInt();
    }

    private byte[] convertIntToByteArray(int value) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(value);
        return buffer.array();
    }

    // конвертирование всех пикселей картинки из 32 битных в 16 битные
    private Pair<List<Character>, int[]> createConvertPixelsFrom32To16Bit(byte[] imageBytes, int width) {
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

    private List<Color> createColorsListFromExistsList(int[] colorsExistArray) {
        List<Color> colors = new ArrayList<>();
        for (int j = 0; j < 65536; j++) {
            if (colorsExistArray[j] != 0) {
                char colorCode = (char) j;
                colors.add(new Color(colorCode, (byte) ((colorCode >>> 11) & 0x1F), (byte) ((colorCode >>> 5) & 0x3F), (byte) (colorCode & 0x1F), colorsExistArray[j]));
            }
        }
        return colors;
    }

    private void sortColorsByDescendingRepetitionsCount(List<Color> colors) {
        colors.sort((o1, o2) -> o1 != null && o2 != null ? (o2.repeated - o1.repeated) : 1);
    }

    private double getColorsDistance(double redColor, double greenColor, double blueColor, Color paletteColor) {
        double redColorDistance = Math.pow(redColor - paletteColor.red, 2.0);
        double greenColorDistance = Math.pow(greenColor - paletteColor.green, 2.0);
        double blueColorDistance = Math.pow(blueColor - paletteColor.blue, 2.0);

        return Math.sqrt(redColorDistance + greenColorDistance + blueColorDistance);
    }

    private byte getNearestColor(List<Color> pallete, Character color) {
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


    private boolean isSimilarColor(List<Color> pallete, Color color, double criticalDistance) {
        for (Color value : pallete) {
            double redColorDistance = Math.pow(color.red - value.red, 2.0);
            double greenColorDistance = Math.pow(color.green - value.green, 2.0);
            double blueColorDistance = Math.pow(color.blue - value.blue, 2.0);

            double colorsDistance = Math.sqrt(redColorDistance + greenColorDistance + blueColorDistance);

            if (colorsDistance < criticalDistance) {
                return true;
            }
        }

        return false;
    }


    private List<Color> createNewPalleteList(List<Color> colors) {
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


    private List<Color> createPalette(List<Color> colors) {
        List<Color> pallete = new ArrayList<>();
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
                for (int i = 0; i < colors.size(); i++) {
                    int finalI = i;
                    if (pallete.stream().noneMatch(color -> color.colorCode == colors.get(finalI).colorCode)) {
                        pallete.add(colors.get(i));
                    }

                    if (pallete.size() >= 256) {
                        break;
                    }
                }
            }

            //Для прозрачности
            if(pallete.size() >= 256) {
                pallete.set(255, new Color((char) 0xFFFF, (byte) 0x1F, (byte) 0x3F, (byte) 0x1F, 1));
            } else {
                //для некоторых изображений pallete содержит только 255 элементов вместо 256
                pallete.add(new Color((char) 0xFFFF, (byte) 0x1F, (byte) 0x3F, (byte) 0x1F, 1));
            }
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

    //Создание тблицы соответствия между цветами и их индексами в палитре
    private int[] createMappingBetweenPalleteAndColors(List<Color> pallete) {
        int[] colorsMapping = new int[65536];

        //Устанавливаем соответсвие между цветом и его индексов в палитре цветов
        for (int i = 0; i < pallete.size(); i++) {
            colorsMapping[pallete.get(i).colorCode] = i;
        }

        //Белый цвет должен быть 256-ым в палитре
        if (colorsMapping[0xFFFF] == 0) {
            colorsMapping[0xFFFF] = 255;
        }
        return colorsMapping;
    }

    //Замена цветов пикселей на индекты цветов в палитре
    private List<Byte> convertPixelsColorsToPalleteColorsIndexes(List<Color> pallete, List<Character> image16Bytes, int[] ColorsMapping) {
        List<Byte> indexedPixels = new ArrayList<>();

        for (Character item : image16Bytes) {
            if (ColorsMapping[item] == 0) {
                ColorsMapping[item] = getNearestColor(pallete, item);
            }
            indexedPixels.add((byte) ColorsMapping[item]);
        }

        return indexedPixels;
    }

    //Преобразование цветов палитры из класс Color в массив байт
    private List<Byte> convertPalleteColorsToBytes(List<Color> pallete) {
        ByteBuffer buffer = ByteBuffer.allocate(pallete.size() * 2);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (Color color : pallete) {
            buffer.putChar(color.colorCode);
        }
        return Arrays.asList(ArrayUtils.toObject(buffer.array()));
    }

    // разбить картинку на регистры в соответствии с правилами
    private List<byte[]> createRegistersFromImage(List<Byte> imageBytes, String article) {
        List<byte[]> listMessages = new ArrayList<>();
        int countMessages = (imageBytes.size() / 118) + 1;
        int nextByteInBmp = 0;
        int numMessage = 0;
        for (int i = 0; i < countMessages; i++) {
            String numMessageStr = prependZeroes(numMessage, 2);
            String posByte = getPosByte(i, countMessages);
            String startData = prependZeroes("01"/*scaleId*/, 2) + "DL" + numMessageStr + "" + posByte + "2" + prependZeroes(article, 4);
            byte[] startDataBytes = startData.getBytes();

            byte[] message = new byte[130];
            System.arraycopy(startDataBytes, 0, message, 0, startDataBytes.length);

            for (int j = startDataBytes.length; j < 118 + startDataBytes.length; j++) {
                if (posByte.equals("3") && imageBytes.size() >= nextByteInBmp) {
                    message[j] = 48;
                    continue;
                }
                message[j] = imageBytes.get(nextByteInBmp);
                nextByteInBmp++;
            }

            listMessages.add(message);

            numMessage++;
            if (numMessage == 100) numMessage = 0;
        }

        return listMessages;
    }

    private String prependZeroes(Object value, int length) {
        return prepend(value, '0', length);
    }

    private String prepend(Object value, Character c, int length) {
        String result = value == null ? "" : String.valueOf(value);
        if (result.length() > length) result = result.substring(0, length);
        while (result.length() < length) {
            result = c + result;
        }
        return result;
    }

    // получить байт в команде, означающий первое, последнее или промежуточное сообщение формируется
    private String getPosByte(int numMessage, int countMessages) {
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

    private class Color {
        char colorCode;
        byte green;
        byte red;
        byte blue;
        int repeated;

        public Color(char colorCode, byte red, byte green, byte blue, int repeated) {
            this.colorCode = colorCode;
            this.green = green;
            this.red = red;
            this.blue = blue;
            this.repeated = repeated;
        }
    }
}