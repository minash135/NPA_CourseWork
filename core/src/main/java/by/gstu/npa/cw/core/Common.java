package by.gstu.npa.cw.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;

/**
 * @author EpicDima
 */
public final class Common {

    public static final int PORT = 8383;

    public static void send(OutputStream outputStream, BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        byte[] byteArray = out.toByteArray();
        putIntToSocket(outputStream, byteArray.length);
        outputStream.write(byteArray);
        out.close();
        outputStream.flush();
    }

    public static BufferedImage receive(InputStream inputStream) throws IOException {
        int size = getIntFromSocket(inputStream);
        byte[] bytes = new byte[size];
        inputStream.readNBytes(bytes, 0, size);
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        BufferedImage receivedImage = ImageIO.read(in);
        in.close();
        return receivedImage;
    }

    public static void putIntToSocket(OutputStream outputStream, int value) throws IOException {
        outputStream.write(ByteBuffer.allocate(4).putInt(value).array());
    }

    public static int getIntFromSocket(InputStream inputStream) throws IOException {
        byte[] bytes = new byte[4];
        inputStream.readNBytes(bytes, 0, 4);
        return ByteBuffer.wrap(bytes).getInt();
    }

    private Common() {
        throw new AssertionError();
    }
}
