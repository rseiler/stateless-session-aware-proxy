package at.rseiler.proxy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.rmi.server.ExportException;
import java.util.Arrays;
import java.util.HashMap;

public class RequestHandler implements HttpHandler {

    private static final Logger LOG = Logger.getLogger(RequestHandler.class);
    private static final String AUTHORIZATION = "Authorization";
    private static final String[] ALLOWED_HEADERS = {"Refresh", "WWW-Authenticate"};

    private HashMap<String, HttpClient> httpClientMap = new HashMap<>();

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String requestURI = httpExchange.getRequestURI().toString().substring(1);
        String domain = requestURI.split("/", 3)[1];
        LOG.info(requestURI);

        if (!httpClientMap.containsKey(domain)) {
            httpClientMap.put(domain, HttpClientBuilder.create().setConnectionManager(new PoolingHttpClientConnectionManager()).build());
        }

        HttpClient httpClient = httpClientMap.get(domain);

        try {
            HttpGet request = createHttpGet(httpExchange, requestURI);
            HttpResponse response = httpClient.execute(request);
            logHeaders(response);
            HttpEntity entity = response.getEntity();

            StringWriter writer = new StringWriter();
            writer.append("<base href='").append(requestURI).append("'>");
            IOUtils.copy(entity.getContent(), writer, "UTF-8");
            writer.close();
            entity.getContent().close();

            httpExchange.getResponseHeaders().add("Content-Type", "text/html;charset=UTF-8");
            addHeaders(httpExchange, response);

            byte[] bytes = writer.toString().getBytes("UTF-8");
            httpExchange.sendResponseHeaders(response.getStatusLine().getStatusCode(), bytes.length);

            OutputStream outputStream = httpExchange.getResponseBody();
            outputStream.write(bytes);
            outputStream.close();
            httpExchange.close();
        } catch (ExportException e) {
            LOG.error("An error occurred", e);
        } finally {
            httpExchange.close();
        }
    }

    private HttpGet createHttpGet(HttpExchange httpExchange, String requestURI) {
        HttpGet request = new HttpGet(requestURI);

        if (httpExchange.getRequestHeaders().containsKey(AUTHORIZATION)) {
            request.setHeader(AUTHORIZATION, httpExchange.getRequestHeaders().getFirst(AUTHORIZATION));
        }

        return request;
    }

    private void logHeaders(HttpResponse response) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("headers: " + Arrays.toString(response.getAllHeaders()));
        }
    }

    private void addHeaders(HttpExchange httpExchange, HttpResponse response) {
        for (Header header : response.getAllHeaders()) {
            for (String allowedHeader : ALLOWED_HEADERS) {
                if (allowedHeader.equalsIgnoreCase(header.getName())) {
                    httpExchange.getResponseHeaders().add(header.getName(), header.getValue());
                }
            }
        }
    }

}
