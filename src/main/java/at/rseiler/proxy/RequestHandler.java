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
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.rmi.server.ExportException;
import java.util.*;

public class RequestHandler implements HttpHandler {

    private static final Logger LOG = Logger.getLogger(RequestHandler.class);
    private static final String AUTHORIZATION = "Authorization";
    private static final String[] ALLOWED_HEADERS = {"Refresh", "WWW-Authenticate"};

    private HashMap<String, HttpClient> httpClientMap = new HashMap<>();

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String requestURI = httpExchange.getRequestURI().toString().substring(1);
        String server = getServerName(requestURI);
        LOG.info(requestURI);

        HttpClient httpClient = getHttpClient(server);

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

    private String getServerName(String requestURI) throws UnsupportedEncodingException {
        String[] split = requestURI.split("\\?", 2);
        String query = split.length == 2 ? split[1] : "";
        Map<String, List<String>> parameters = parseQuery(query);
        return parameters.getOrDefault("server", Arrays.asList("default")).get(0);
    }

    private HttpClient getHttpClient(String server) {
        if (!httpClientMap.containsKey(server)) {
            httpClientMap.put(server, HttpClientBuilder.create().setConnectionManager(new PoolingHttpClientConnectionManager()).build());
        }

        return httpClientMap.get(server);
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

    private Map<String, List<String>> parseQuery(String query) throws UnsupportedEncodingException {
        Map<String, List<String>> parameters = new HashMap<>();
        if (query != null) {
            String pairs[] = query.split("[&]");

            for (String pair : pairs) {
                String param[] = pair.split("[=]");

                String key = null;
                String value = null;
                if (param.length > 0) {
                    key = URLDecoder.decode(param[0], System.getProperty("file.encoding"));
                }

                if (param.length > 1) {
                    value = URLDecoder.decode(param[1], System.getProperty("file.encoding"));
                }

                if (!parameters.containsKey(key)) {
                    parameters.put(key, new ArrayList<String>());
                }
                List<String> strings = parameters.get(key);
                strings.add(value);
            }
        }
        return parameters;
    }

}
