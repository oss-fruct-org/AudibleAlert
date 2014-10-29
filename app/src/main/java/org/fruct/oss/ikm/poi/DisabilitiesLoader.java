package org.fruct.oss.ikm.poi;


import org.fruct.oss.ikm.storage.ContentConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DisabilitiesLoader {

    private static final Logger log = LoggerFactory.getLogger(DisabilitiesLoader.class);
    private static final int MAX_RECURSION = 10;
    public static final String source = "http://gets.cs.petrsu.ru/obstacle/config/disabilities.xml";

    public static Disabilities disabilities;

    public DisabilitiesLoader(){
        InputStream bufferedStream;

        try {
            bufferedStream = loadToBuffer(source).getStream();

            disabilities = Disabilities.createFromStream(bufferedStream);
            log.trace("Disabilities loaded in a number of:" + disabilities.getDisabilities().size());
        }catch(IOException e){
            log.error("Content link " + source + " broken: ", e);

        }
    }


    public ContentConnection loadToBuffer(String urlStr) throws IOException {
        final HttpURLConnection conn = getConnection(urlStr);

        return new ContentConnection() {
            private InputStream stream;

            @Override
            public InputStream getStream() throws IOException {
                return stream = new BufferedInputStream(conn.getInputStream());
            }

            @Override
            public void close() {
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                conn.disconnect();
            }
        };
    }


    private int recursionDepth = 0;
    public HttpURLConnection getConnection(String urlStr) throws IOException {
        log.info("Downloading {}", urlStr);
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setReadTimeout(10000);
        conn.setConnectTimeout(10000);

        conn.setRequestMethod("GET");
        conn.setDoInput(true);

        conn.connect();
        int code = conn.getResponseCode();
        log.info("Code {}", code);

        // TODO: not tested
        if (code != HttpURLConnection.HTTP_ACCEPTED) {
            if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP) {
                try {
                    if (++recursionDepth > MAX_RECURSION)
                        throw new IOException("Too many redirects");

                    String newLocation = conn.getHeaderField("Location");
                    log.info("Redirecting to {}", newLocation);

                    conn.disconnect();
                    return getConnection(newLocation);
                } finally {
                    recursionDepth--;
                }
            }
        }

        return conn;
    }
}
