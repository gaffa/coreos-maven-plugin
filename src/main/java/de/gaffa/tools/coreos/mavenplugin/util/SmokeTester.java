package de.gaffa.tools.coreos.mavenplugin.util;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;

/**
 * Helps to perform a smoke test (used after deployment)
 */
public class SmokeTester {

    /**
     * @return true if a get request against given url returns status code 200 within 60 seconds
     */
    public static boolean test(String url, Log log) {

        final RequestConfig defaultRequestConfig = RequestConfig.custom()
                .setConnectTimeout(1000)
                .setConnectionRequestTimeout(1000)
                .setSocketTimeout(1000)
                .build();

        final CloseableHttpClient httpClient = HttpClientBuilder.create()
                .disableAutomaticRetries()
                .setDefaultRequestConfig(defaultRequestConfig)
                .build();

        for (int i = 0; i < 60; i++) {
            long time = System.currentTimeMillis();
            try {
                log.info("trying to connect to deployed service...");
                final HttpGet get = new HttpGet(url);
                final CloseableHttpResponse response = httpClient.execute(get);
                final int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 200) {
                    log.info("successfully connected to service and got HTTP 200");
                    return true;
                }
                log.info("invalid status code: " + statusCode + " (expecting 200).");
                Thread.sleep(1000);
            } catch (IOException | InterruptedException e) {
                log.warn("problem connecting to service: " + e.getMessage());
            }
            // wait until a second is over
            while (System.currentTimeMillis() - time < 1000) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        log.warn("did not get HTTP 200 from service after 60 attempts.");
        return false;
    }
}
