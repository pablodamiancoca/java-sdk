package com.global.api.gateways;

import com.global.api.entities.exceptions.GatewayException;
import com.global.api.utils.IOUtils;
import com.global.api.utils.StringUtils;

import org.apache.http.entity.mime.MultipartEntity;
import sun.net.www.protocol.https.HttpsURLConnectionImpl;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import java.util.zip.GZIPInputStream;

abstract class Gateway {
    private String contentType;
    private boolean enableLogging;
    protected HashMap<String, String> headers;
    protected int timeout;
    protected String serviceUrl;

    public String getContentType() {
        return contentType;
    }
    public void setEnableLogging(boolean enableLogging) {
		this.enableLogging = enableLogging;
	}
    public boolean getEnableLogging() {
        return enableLogging;
    }
	public HashMap<String, String> getHeaders() {
        return headers;
    }
    public void setHeaders(HashMap<String, String> headers) {
        this.headers = headers;
    }
    public int getTimeout() {
        return timeout;
    }
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
    public String getServiceUrl() {
        return serviceUrl;
    }
    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public Gateway(String contentType) {
        headers = new HashMap<String, String>();
        this.contentType = contentType;
    }

    protected GatewayResponse sendRequest(String verb, String endpoint) throws GatewayException {
        return sendRequest(verb, endpoint, null, null);
    }
    protected GatewayResponse sendRequest(String verb, String endpoint, String data) throws GatewayException {
        return sendRequest(verb, endpoint, data, null);
    }
    protected GatewayResponse sendRequest(String verb, String endpoint, String data, HashMap<String, String> queryStringParams) throws GatewayException {
        HttpsURLConnection conn;
        try{
            String queryString = buildQueryString(queryStringParams);
            conn = (HttpsURLConnection)new URL((serviceUrl + endpoint + queryString).trim()).openConnection();
            conn.setSSLSocketFactory(new SSLSocketFactoryEx());
            conn.setConnectTimeout(timeout);
            conn.setDoInput(true);
            // ----------------------------------------------------------------------
            // Fix: Supports PATCH requests on HttpsURLConnection
            // https://stackoverflow.com/questions/25163131/httpurlconnection-invalid-http-method-patch
            // ----------------------------------------------------------------------
            if ("PATCH".equalsIgnoreCase(verb)) {
                supportVerbs("PATCH");
                setRequestMethod(conn, verb);
            } else {
                conn.setRequestMethod(verb);
            }
            // ----------------------------------------------------------------------
            conn.addRequestProperty("Content-Type", String.format("%s; charset=UTF-8", contentType));

            for (Map.Entry<String, String> header: headers.entrySet()) {
                conn.addRequestProperty(header.getKey(), header.getValue());
            }

            if(!verb.equals("GET")) {
                byte[] request = data.getBytes();

                conn.setDoOutput(true);
                conn.addRequestProperty("Content-Length", String.valueOf(request.length));

                if (this.enableLogging)
                    System.out.println("Request: " + StringUtils.mask(data));
                DataOutputStream requestStream = new DataOutputStream(conn.getOutputStream());
                requestStream.write(request);
                requestStream.flush();
                requestStream.close();
            }
            else if (this.enableLogging) {
                System.out.println("Request: " + endpoint);
            }

            InputStream responseStream = conn.getInputStream();

            String rawResponse;
            if (headers.containsKey("Accept-Encoding") && headers.get("Accept-Encoding").equalsIgnoreCase("gzip")) {
                // Decompress GZIP response if specified
                GZIPInputStream gzis = new GZIPInputStream(responseStream);
                InputStreamReader reader = new InputStreamReader(gzis);
                BufferedReader in = new BufferedReader(reader);

                StringBuilder decompressedResponse = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    decompressedResponse.append(line);
                }
                rawResponse = decompressedResponse.toString();
            } else {
                rawResponse = IOUtils.readFully(responseStream);
            }

            responseStream.close();
            if (this.enableLogging) {
                System.out.println("Response: " + rawResponse);
            }

            GatewayResponse response = new GatewayResponse();
            response.setStatusCode(conn.getResponseCode());
            response.setRawResponse(rawResponse);
            return response;
        }
        catch(Exception exc) {
            throw new GatewayException("Error occurred while communicating with gateway.", exc);
        }
    }
    protected GatewayResponse sendRequest(String endpoint, MultipartEntity content) throws GatewayException {
        HttpsURLConnection conn;
        try{
            conn = (HttpsURLConnection)new URL((serviceUrl + endpoint).trim()).openConnection();
            conn.setSSLSocketFactory(new SSLSocketFactoryEx());
            conn.setConnectTimeout(timeout);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.addRequestProperty("Content-Type", content.getContentType().getValue());
            conn.addRequestProperty("Content-Length", String.valueOf(content.getContentLength()));

            OutputStream out = conn.getOutputStream();
			if (this.enableLogging) {
                System.out.println("Request: " + content);
            }
            content.writeTo(out);
            out.flush();
            out.close();

            InputStream responseStream = conn.getInputStream();
            String rawResponse = IOUtils.readFully(responseStream);
            responseStream.close();
			if (this.enableLogging) {
                System.out.println("Response: " + rawResponse);
            }

            GatewayResponse response = new GatewayResponse();
            response.setStatusCode(conn.getResponseCode());
            response.setRawResponse(rawResponse);
            return response;
        }
        catch(Exception exc) {
            throw new GatewayException("Error occurred while communicating with gateway.", exc);
        }
    }

    private String buildQueryString(HashMap<String, String> queryStringParams) throws UnsupportedEncodingException {
        if(queryStringParams == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("?");
        for (Map.Entry<String, String> entry : queryStringParams.entrySet()) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(String.format("%s=%s", URLEncoder.encode(entry.getKey(), "UTF-8"), URLEncoder.encode(entry.getValue(), "UTF-8")));
        }
        return sb.toString();
    }

    private static void supportVerbs(String... newVerbs) {
        try {
            Field methodsField = HttpURLConnection.class.getDeclaredField("methods");

            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(methodsField, methodsField.getModifiers() & ~Modifier.FINAL);

            methodsField.setAccessible(true);

            String[] oldMethods = (String[]) methodsField.get(null);
            Set<String> methodsSet = new LinkedHashSet<>(Arrays.asList(oldMethods));
            methodsSet.addAll(Arrays.asList(newVerbs));
            String[] newMethods = methodsSet.toArray(new String[0]);

            methodsField.set(null/*static field*/, newMethods);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }

    }

    private void setRequestMethod(final HttpURLConnection c, final String value) {
        try {
            final Object target;
            if (c instanceof HttpsURLConnectionImpl) {
                final Field delegate = HttpsURLConnectionImpl.class.getDeclaredField("delegate");
                delegate.setAccessible(true);
                target = delegate.get(c);
            } else {
                target = c;
            }
            final Field f = HttpURLConnection.class.getDeclaredField("method");
            f.setAccessible(true);
            f.set(target, value);
        } catch (IllegalAccessException | NoSuchFieldException ex) {
            throw new AssertionError(ex);
        }
    }

}
