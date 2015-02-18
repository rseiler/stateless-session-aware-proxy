package at.rseiler.proxy;

import com.sun.net.httpserver.HttpServer;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyMain {

    private static final Logger LOG = Logger.getLogger(ProxyMain.class);

    private final int port;
    private ExecutorService executorService;
    private HttpServer httpServer;

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 80;
        ProxyMain ProxyMain = new ProxyMain(port);

        try {
            ProxyMain.run();
        } catch (IOException e) {
            LOG.error("Couldn't start the server. ", e);
        }

        LOG.info("ProxyMain running on port " + port + ".");
    }

    public ProxyMain(int port) {
        this.port = port;
    }

    public void run() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(port), -1);
        executorService = Executors.newFixedThreadPool(8);
        httpServer.createContext("/", new RequestHandler());
        httpServer.setExecutor(executorService);
        httpServer.start();
    }

    private void shutdown() {
        executorService.shutdown();
        httpServer.stop(1);
    }

}
