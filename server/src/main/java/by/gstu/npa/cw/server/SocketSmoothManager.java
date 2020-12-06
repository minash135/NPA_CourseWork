package by.gstu.npa.cw.server;

import by.gstu.npa.cw.core.Common;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public final class SocketSmoothManager implements AutoCloseable {

    private static final Logger LOGGER = LogManager.getLogger(SocketSmoothManager.class);

    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private final Object clientsMutex = new Object();
    private final List<Socket> clients = Collections.synchronizedList(new ArrayList<>());
    private final List<Socket> busyClients = Collections.synchronizedList(new ArrayList<>());
    private ServerSocket serverSocket;
    private long processingTime;
    private Consumer<Integer> activeClientsCountCallback;
    private final Thread socketConnectionThread = new Thread(this::connectWithSockets);
    private final Thread activeSocketCheckThread = new Thread(this::checkActiveSockets);

    public SocketSmoothManager() {
        try {
            serverSocket = new ServerSocket(Common.PORT);
            socketConnectionThread.start();
            activeSocketCheckThread.start();
        } catch (IOException e) {
            LOGGER.fatal(e);
        }
    }

    @Override
    public void close() {
        try {
            socketConnectionThread.interrupt();
            activeSocketCheckThread.interrupt();
            threadPool.shutdown();
            synchronized (clientsMutex) {
                for (Socket client : clients) {
                    client.close();
                }
                clients.clear();
                busyClients.clear();
            }
            activeClientsCountCallback.accept(activeClientsCount());
            serverSocket.close();
        } catch (IOException e) {
            LOGGER.error(e);
        }
    }

    public void connectWithSockets() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                clients.add(socket);
                activeClientsCountCallback.accept(activeClientsCount());
            } catch (IOException e) {
                LOGGER.info(e);
            }
        }
    }

    public void checkActiveSockets() {
        while (!serverSocket.isClosed()) {
            synchronized (clientsMutex) {
                for (int i = 0; i < clients.size(); i++) {
                    Socket client = clients.get(i);
                    if (busyClients.contains(client)) {
                        continue;
                    }
                    try {
                        OutputStream outputStream = client.getOutputStream();
                        outputStream.write(0);
                        outputStream.flush();
                    } catch (IOException e) {
                        LOGGER.info(e);
                        clients.remove(i);
                        activeClientsCountCallback.accept(activeClientsCount());
                    }
                }
            }
            try {
                //noinspection BusyWait
                Thread.sleep(100);
            } catch (InterruptedException e) {
                //noinspection ResultOfMethodCallIgnored
                Thread.interrupted();
                LOGGER.info(e);
            }
        }
    }

    public int activeClientsCount() {
        return clients.size();
    }

    public void setClientCountCallback(Consumer<Integer> callback) {
        activeClientsCountCallback = Objects.requireNonNull(callback);
    }

    public void smoothImage(final BufferedImage original, final int clientsCount, final int kernelSize, final int repeatCount, SmoothCallback callback) {
        threadPool.submit(() -> callback.callback(smoothImage(original, clientsCount, kernelSize, repeatCount), processingTime));
    }

    public BufferedImage smoothImage(final BufferedImage original, final int clientsCount, final int kernelSize, final int repeatCount) {
        // noinspection StatementWithEmptyBody
        while (activeClientsCount() < clientsCount) ;
        processingTime = System.nanoTime();
        int partWidth = original.getWidth() / clientsCount;
        int remainingWidth = original.getWidth() % clientsCount;
        List<Future<BufferedImage>> futures;
        int paddingSize = (int) ((int) (partWidth * 0.05) * (kernelSize * 0.15 * repeatCount * 0.35));
        synchronized (clientsMutex) {
            futures = smoothMap(original, clientsCount, partWidth, remainingWidth, kernelSize, repeatCount, paddingSize);
        }
        BufferedImage smoothed = smoothReduce(original, clientsCount, partWidth, remainingWidth, paddingSize, futures);
        busyClients.clear();
        processingTime = System.nanoTime() - processingTime;
        return smoothed;
    }

    private List<Future<BufferedImage>> smoothMap(final BufferedImage image, final int clientsCount, final int partWidth, final int remainingWidth, final int kernelSize, final int repeatCount, final int paddingSize) {
        int height = image.getHeight();
        List<Future<BufferedImage>> futures = new ArrayList<>(clientsCount);
        for (int i = 0; i < clientsCount; i++) {
            boolean first = (i == 0);
            int currentPadding = first ? 0 : paddingSize;
            boolean last = (i == clientsCount - 1);
            int currentPartWidth = partWidth;
            if (clientsCount > 1) {
                currentPartWidth += (last ? remainingWidth : 0) + ((first || last) ? paddingSize : paddingSize * 2);
            }
            BufferedImage partImage = new BufferedImage(currentPartWidth, height, BufferedImage.TYPE_INT_RGB);
            Graphics graphics = partImage.createGraphics();
            graphics.drawImage(image,
                    0,
                    0,
                    currentPartWidth,
                    height,
                    partWidth * i - currentPadding,
                    0,
                    partWidth * i - currentPadding + currentPartWidth,
                    height,
                    null);
            graphics.dispose();
            Socket client = clients.get(i);
            busyClients.add(client);
            futures.add(threadPool.submit(() -> sendImageToClient(client, partImage, kernelSize, repeatCount)));
        }
        return futures;
    }

    private BufferedImage smoothReduce(final BufferedImage image, final int clientsCount, final int partWidth, final int remainingWidth, final int paddingSize, final List<Future<BufferedImage>> futures) {
        int height = image.getHeight();
        BufferedImage smoothed = new BufferedImage(image.getWidth(), height, BufferedImage.TYPE_INT_RGB);
        Graphics graphics = smoothed.createGraphics();
        for (int i = 0; i < clientsCount; i++) {
            int currentPadding = (i == 0) ? 0 : paddingSize;
            int currentPartWidth = partWidth + ((i == clientsCount - 1) ? remainingWidth : 0);
            try {
                BufferedImage smoothPartImage = futures.get(i).get();
                graphics.drawImage(smoothPartImage,
                        partWidth * i,
                        0,
                        partWidth * i + currentPartWidth,
                        height,
                        currentPadding,
                        0,
                        currentPartWidth + currentPadding,
                        height,
                        null);
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.warn(e);
            }
        }
        graphics.dispose();
        return smoothed;
    }

    private BufferedImage sendImageToClient(Socket socket, BufferedImage image, int kernelSize, int repeatCount) throws IOException {
        OutputStream outputStream = socket.getOutputStream();
        outputStream.write(1);
        Common.putIntToSocket(outputStream, kernelSize);
        Common.putIntToSocket(outputStream, repeatCount);
        Common.send(outputStream, image);
        return Common.receive(socket.getInputStream());
    }


    public interface SmoothCallback {
        void callback(BufferedImage image, long processingTime);
    }
}
