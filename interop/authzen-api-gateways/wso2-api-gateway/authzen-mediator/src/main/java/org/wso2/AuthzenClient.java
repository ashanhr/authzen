package org.wso2;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;

import javax.net.ssl.SSLContext;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

/**
 * Authzen Client
 */
public class AuthzenClient {
    private static final Log log = LogFactory.getLog(AuthzenClient.class);

    private int maxOpenConnections = 500;
    private int maxPerRoute = 200;
    private int connectionTimeout = 30;

    private CloseableHttpClient httpClient = null;

    /**
     * Constructor for the Authzen client.
     *
     * @param url URL of the Authzen server
     * @throws AuthzenSecurityException if an error occurs while creating the http client
     */
    public AuthzenClient(String url) throws AuthzenSecurityException {
        httpClient = createHttpClient(url);
    }

    /**
     * Create an http client for the Authzen client.
     *
     * @param url URL of the Authzen server
     * @return CloseableHttpClient
     * @throws AuthzenSecurityException if an error occurs while creating the http client
     */
    private CloseableHttpClient createHttpClient(String url) throws AuthzenSecurityException {

        PoolingHttpClientConnectionManager pool;
        try {
            String protocol = new URL(url).getProtocol();
            pool = getPoolingHttpClientConnectionManager(protocol);
        } catch (Exception e) {
            log.error("Error while creating the http client for the Authzen client", e);
            throw new AuthzenSecurityException(AuthzenSecurityException.INTERNAL_ERROR,
                    AuthzenSecurityException.INTERNAL_ERROR_MESSAGE, e);
        }

        pool.setMaxTotal(maxOpenConnections);
        pool.setDefaultMaxPerRoute(maxPerRoute);

        // Socket timeout is set to 10 seconds
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(connectionTimeout * 1000)
                .setSocketTimeout((connectionTimeout + 10) * 10000).build();

        return HttpClients.custom().setConnectionManager(pool).setDefaultRequestConfig(requestConfig).build();
    }

    /**
     * Create a pooling http client connection manager.
     *
     * @param protocol protocol of the URL
     * @return PoolingHttpClientConnectionManager
     * @throws AuthzenSecurityException if an error occurs while creating the http client
     */
    private PoolingHttpClientConnectionManager getPoolingHttpClientConnectionManager(String protocol)
            throws AuthzenSecurityException {

        PoolingHttpClientConnectionManager poolManager;
        if (AuthzenConstants.HTTPS.equals(protocol)) {
            char[] trustStorePassword = System.getProperty(AuthzenConstants.TRUST_STORE_PASSWORD_SYSTEM_PROPERTY)
                    .toCharArray();
            String trustStoreLocation = System.getProperty(AuthzenConstants.TRUST_STORE_LOCATION_SYSTEM_PROPERTY);
            File trustStoreFile = new File(trustStoreLocation);
            try (InputStream localTrustStoreStream = Files.newInputStream(trustStoreFile.toPath())) {
                KeyStore trustStore = KeyStore.getInstance(AuthzenConstants.KEY_STORE_TYPE);
                trustStore.load(localTrustStoreStream, trustStorePassword);
                SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(trustStore, null).build();

                X509HostnameVerifier hostnameVerifier;
                String hostnameVerifierOption = System.getProperty(AuthzenConstants.HOST_NAME_VERIFIER);

                if (AuthzenConstants.ALLOW_ALL.equalsIgnoreCase(hostnameVerifierOption)) {
                    hostnameVerifier = SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;
                } else if (AuthzenConstants.STRICT.equalsIgnoreCase(hostnameVerifierOption)) {
                    hostnameVerifier = SSLConnectionSocketFactory.STRICT_HOSTNAME_VERIFIER;
                } else {
                    hostnameVerifier = SSLConnectionSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER;
                }

                SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext, hostnameVerifier);
                Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory> create()
                        .register(AuthzenConstants.HTTPS, sslsf).build();
                poolManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
            } catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException |
                    KeyManagementException e) {
                log.error("Error while creating the http client for the Authzen client", e);
                throw new AuthzenSecurityException(AuthzenSecurityException.INTERNAL_ERROR,
                        AuthzenSecurityException.INTERNAL_ERROR_MESSAGE, e);
            }
        } else {
            poolManager = new PoolingHttpClientConnectionManager();
        }
        return poolManager;
    }

    /**
     * Publish the evaluation request to the Authzen server.
     *
     * @param evaluationUrl URL of the Authzen server
     * @param payload       Payload of the evaluation request
     * @param accessKey     Access key for the Authzen server
     * @return Authzen response
     * @throws AuthzenSecurityException if an error occurs while publishing the evaluation request
     */
    public String publish(String evaluationUrl, String payload, String accessKey) throws AuthzenSecurityException {
        if (log.isDebugEnabled()) {
            log.debug("Initializing authzen evaluation request: [evaluation-endpoint] " + evaluationUrl);
        }

        HttpPost httpPost = new HttpPost(evaluationUrl);
        httpPost.setHeader(AuthzenConstants.CONTENT_TYPE_HEADER, AuthzenConstants.APPLICATION_JSON);
        if (accessKey != null) {
            httpPost.setHeader(AuthzenConstants.AUTHORIZATION_HEADER, accessKey);
        }
        CloseableHttpResponse response = null;
        try {
            httpPost.setEntity(new StringEntity(payload));
            response = httpClient.execute(httpPost);
            return extractResponse(response);
        } catch (IOException e) {
            log.error("Error occurred while publishing to AuthZEN server", e);
            throw new AuthzenSecurityException(AuthzenSecurityException.INTERNAL_ERROR,
                    AuthzenSecurityException.INTERNAL_ERROR_MESSAGE, e);
        } finally {
            httpPost.releaseConnection();
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    log.error("Error when closing the response of the AuthZEN request", e);
                }
            }
        }
    }

    /**
     * Extract the response from the Authzen server.
     *
     * @param response CloseableHttpResponse
     * @return Authzen response
     * @throws AuthzenSecurityException if an error occurs while extracting the response
     */
    private String extractResponse(CloseableHttpResponse response) throws AuthzenSecurityException {

        String authzenResponse = null;
        try {
            int responseCode = response.getStatusLine().getStatusCode();
            if (responseCode != HttpStatus.SC_OK) {
                log.error(
                        "Error occurred while connecting to the AuthZEN server. " + responseCode + " response returned");
                throw new AuthzenSecurityException(AuthzenSecurityException.INTERNAL_ERROR,
                        "Error while accessing the AuthZEN server URL. " + response.getStatusLine());
            } else {
                HttpEntity entity = response.getEntity();
                Charset charset = ContentType.getOrDefault(entity).getCharset();
                if (charset == null) {
                    charset = StandardCharsets.UTF_8;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent(), charset));
                String inputLine;
                StringBuilder stringBuilder = new StringBuilder();

                while ((inputLine = reader.readLine()) != null) {
                    stringBuilder.append(inputLine);
                }
                authzenResponse = stringBuilder.toString();
                if (log.isDebugEnabled()) {
                    log.debug("Response: [status-code] " + responseCode + " [message] " + authzenResponse);
                }
            }
        } catch (IOException e) {
            log.error("Error while reading the AuthZEN evaluation response", e);
            throw new AuthzenSecurityException(AuthzenSecurityException.INTERNAL_ERROR,
                    "Error while reading the AuthZEN evaluation responsee", e);
        }

        return authzenResponse;
    }
}
