package by.gstu.npa.cw.client;

import by.gstu.npa.cw.core.Common;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

public final class Client implements AutoCloseable {

    private static final Logger LOGGER = LogManager.getLogger(Client.class);

    public static void main(String[] args) {
        System.out.println("Enter '-h' or '-help' to get information about the arguments.");
        Client client = null;
        try {
            if (args.length == 1) {
                if ("-h".equals(args[0]) || "-help".equals(args[0])) {
                    System.out.println("The first argument is the server address, by default 'localhost'.");
                    return;
                }
                client = new Client(args[0]);
            } else {
                client = new Client();
            }
            client.start();
        } catch (Exception e) {
            LOGGER.fatal(e);
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    private final Socket socket;

    public Client(String host) throws IOException {
        socket = new Socket(host, Common.PORT);
        LOGGER.info("Client has started execution");
    }

    public Client() throws IOException {
        this("localhost");
    }

    public void start() {
        try {
            InputStream inputStream = socket.getInputStream();
            while (!socket.isClosed()) {
                int status = inputStream.read();
                if (status == -1) {
                    socket.close();
                    break;
                } else if (status == 0) {
                    continue;
                }
                receiveAndSend(inputStream);
            }
            LOGGER.info("Connection to the server is closed");
        } catch (IOException e) {
            LOGGER.fatal("Connection to the server is broken", e);
        }
    }

    private void receiveAndSend(InputStream inputStream) throws IOException {
        int kernelSize = 0;
        while (kernelSize == 0) {
            kernelSize = Common.getIntFromSocket(inputStream);
        }
        int repeatCount = Common.getIntFromSocket(inputStream);
        BufferedImage image = Common.receive(inputStream);
        if (kernelSize < 3 || kernelSize > 29 || kernelSize % 2 != 1) {
            kernelSize = 3;
        }
        if (repeatCount < 1 || repeatCount > 20) {
            repeatCount = 1;
        }
        processRequest(kernelSize, repeatCount, image);
    }

    private void processRequest(int kernelSize, int repeatCount, BufferedImage image) throws IOException {
        LOGGER.info("+++++++Request for smoothing+++++++");
        LOGGER.info("---------Request parameters--------");
        LOGGER.info("Kernel size: " + kernelSize);
        LOGGER.info("Repeat count: " + repeatCount);
        LOGGER.info("Image: " + image);
        LOGGER.info("------Start of image smoothing-----");
        image = smooth(image, kernelSize, repeatCount);
        LOGGER.info("---------Image is smoothed---------");
        LOGGER.info("----------Sending response---------");
        Common.send(socket.getOutputStream(), image);
        LOGGER.info("-----------Response sent-----------");
        LOGGER.info("++++Request has been processed+++++");
    }

    @Override
    public void close() {
        try {
            socket.close();
            LOGGER.info("Client has completed execution");
        } catch (IOException e) {
            LOGGER.error(e);
        }
    }

    private static BufferedImage smooth(BufferedImage image, int kernelSize, int repeatCount) {
        for (int i = 0; i < repeatCount; i++) {
            image = smoothHorizontally(smoothVertically(image, kernelSize), kernelSize);
        }
        return image;
    }

    private static BufferedImage smoothHorizontally(BufferedImage inputImage, int kernelSize) {
        int width = inputImage.getWidth(), height = inputImage.getHeight();
        BufferedImage outputImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Kernel kernel = new Kernel(kernelSize);
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                kernel.reset();
                for (int k = 0; (k < kernel.getCenter()) && (j - k >= 0); k++) {
                    kernel.add(inputImage.getRGB(i, j - k));
                }
                for (int k = kernel.getCenter(), k2 = 0; (k < kernel.getSize()) && (j + k2 < height); k++, k2++) {
                    kernel.add(inputImage.getRGB(i, j + k2));
                }
                outputImage.setRGB(i, j, kernel.getMeanRgb());
            }
        }
        return outputImage;
    }

    private static BufferedImage smoothVertically(final BufferedImage inputImage, int kernelSize) {
        final int width = inputImage.getWidth(), height = inputImage.getHeight();
        BufferedImage outputImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Kernel kernel = new Kernel(kernelSize);
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                kernel.reset();
                for (int k = 0; (k < kernel.getCenter()) && (i - k >= 0); k++) {
                    kernel.add(inputImage.getRGB(i - k, j));
                }
                for (int k = kernel.getCenter(), k2 = 0; (k < kernel.getSize()) && (i + k2 < width); k++, k2++) {
                    kernel.add(inputImage.getRGB(i + k2, j));
                }
                outputImage.setRGB(i, j, kernel.getMeanRgb());
            }
        }
        return outputImage;
    }


    private static final class Kernel {
        private final int size;
        private final int center;
        private int count;
        private int red;
        private int green;
        private int blue;

        private Kernel(int size) {
            this.size = size;
            this.center = (size % 2 == 0) ? (size / 2 - 1) : (size / 2 + 1);
        }

        public void reset() {
            count = red = green = blue = 0;
        }

        public int getSize() {
            return size;
        }

        public int getCenter() {
            return center;
        }

        public void add(Color color) {
            red += color.getRed();
            green += color.getGreen();
            blue += color.getBlue();
            count++;
        }

        public void add(int rgb) {
            add(new Color(rgb));
        }

        public Color getMeanColor() {
            return new Color(red / count, green / count, blue / count);
        }

        public int getMeanRgb() {
            return getMeanColor().getRGB();
        }
    }
}
