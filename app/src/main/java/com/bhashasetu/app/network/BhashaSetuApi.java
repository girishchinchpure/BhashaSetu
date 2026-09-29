package com.bhashasetu.app.network;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BhashaSetuApi {

    /*
     * LOCAL DEVELOPMENT SERVER
     *
     * For Android Emulator:
     * 10.0.2.2 points to the host computer's localhost.
     *
     * Example:
     * http://10.0.2.2:8000
     *
     * When we deploy the server publicly later, this will become
     * the HTTPS server URL.
     */
    private static final String BASE_URL =
            "http://192.168.1.110:8000";

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(JSONObject response);

        void onError(String error);
    }

    public void generateMaterial(
            String curriculum,
            String className,
            String subject,
            JSONArray topics,
            String difficulty,
            JSONArray output,
            Callback callback
    ) {

        executor.execute(() -> {

            HttpURLConnection connection = null;

            try {

                URL url = new URL(
                        BASE_URL + "/generate-material"
                );

                connection =
                        (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("POST");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(30000);

                connection.setDoInput(true);
                connection.setDoOutput(true);

                connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                );

                connection.setRequestProperty(
                        "Accept",
                        "application/json"
                );

                /*
                 * Build request JSON.
                 */
                JSONObject request =
                        new JSONObject();

                request.put(
                        "curriculum",
                        curriculum
                );

                request.put(
                        "class_name",
                        className
                );

                request.put(
                        "subject",
                        subject
                );

                request.put(
                        "topics",
                        topics
                );

                request.put(
                        "difficulty",
                        difficulty
                );

                request.put(
                        "output",
                        output
                );

                request.put(
                        "language",
                        "Santhali"
                );

                byte[] requestBytes =
                        request.toString()
                                .getBytes(
                                        StandardCharsets.UTF_8
                                );

                /*
                 * Send request.
                 */
                try (
                        OutputStream outputStream =
                                connection.getOutputStream()
                ) {

                    outputStream.write(
                            requestBytes
                    );

                    outputStream.flush();
                }

                /*
                 * Read response.
                 */
                int responseCode =
                        connection.getResponseCode();

                InputStream inputStream;

                if (responseCode >= 200
                        && responseCode < 300) {

                    inputStream =
                            connection.getInputStream();

                } else {

                    inputStream =
                            connection.getErrorStream();
                }

                String responseText =
                        readStream(inputStream);

                if (responseCode >= 200
                        && responseCode < 300) {

                    JSONObject response =
                            new JSONObject(
                                    responseText
                            );

                    mainHandler.post(
                            () -> callback.onSuccess(
                                    response
                            )
                    );

                } else {

                    String error =
                            "Server returned HTTP "
                                    + responseCode
                                    + ": "
                                    + responseText;

                    mainHandler.post(
                            () -> callback.onError(
                                    error
                            )
                    );
                }

            } catch (Exception e) {

                String message =
                        e.getMessage();

                if (message == null
                        || message.trim().isEmpty()) {

                    message =
                            e.getClass()
                                    .getSimpleName();
                }

                String finalMessage =
                        message;

                mainHandler.post(
                        () -> callback.onError(
                                finalMessage
                        )
                );

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private String readStream(
            InputStream inputStream
    ) throws Exception {

        if (inputStream == null) {
            return "";
        }

        StringBuilder builder =
                new StringBuilder();

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        inputStream,
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {

            String line;

            while (
                    (line = reader.readLine())
                            != null
            ) {

                builder.append(line);
            }
        }

        return builder.toString();
    }

    public void close() {
        executor.shutdownNow();
    }
}