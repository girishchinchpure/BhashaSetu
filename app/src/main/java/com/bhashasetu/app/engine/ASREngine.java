package com.bhashasetu.app.engine;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ASREngine {

    public interface ResultCallback {
        void onResult(String text);
        void onError(String error);
    }

    private static final int SAMPLE_RATE = 16000;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private volatile boolean recording = false;

    private AudioRecord audioRecord;
    private OfflineRecognizer recognizer;

    public ASREngine() {
    }

    public void startListening(
            Context context,
            ResultCallback callback
    ) {

        if (context == null || callback == null) {
            return;
        }

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {

            postError(
                    callback,
                    "Microphone permission not granted"
            );

            return;
        }

        if (recording) {
            return;
        }

        executor.execute(() -> {

            try {

                initializeRecognizer(context);

                int minBufferSize =
                        AudioRecord.getMinBufferSize(
                                SAMPLE_RATE,
                                AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT
                        );

                if (minBufferSize <= 0) {

                    postError(
                            callback,
                            "Invalid microphone buffer size"
                    );

                    return;
                }

                int bufferSize =
                        Math.max(
                                minBufferSize * 2,
                                SAMPLE_RATE
                        );

                audioRecord =
                        new AudioRecord(
                                MediaRecorder.AudioSource.MIC,
                                SAMPLE_RATE,
                                AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT,
                                bufferSize
                        );

                if (audioRecord.getState()
                        != AudioRecord.STATE_INITIALIZED) {

                    releaseRecorder();

                    postError(
                            callback,
                            "Could not initialize microphone"
                    );

                    return;
                }

                List<float[]> chunks =
                        new ArrayList<>();

                short[] buffer =
                        new short[1024];

                recording = true;

                audioRecord.startRecording();

                while (recording) {

                    int count =
                            audioRecord.read(
                                    buffer,
                                    0,
                                    buffer.length
                            );

                    if (count <= 0) {
                        continue;
                    }

                    float[] chunk =
                            new float[count];

                    for (int i = 0; i < count; i++) {
                        chunk[i] =
                                buffer[i] / 32768.0f;
                    }

                    chunks.add(chunk);
                }

                releaseRecorder();

                if (chunks.isEmpty()) {

                    postError(
                            callback,
                            "No speech captured"
                    );

                    return;
                }

                float[] samples =
                        mergeChunks(chunks);

                if (samples.length <
                        SAMPLE_RATE / 10) {

                    postError(
                            callback,
                            "Speech is too short"
                    );

                    return;
                }

                String result =
                        recognize(samples);

                final String finalResult =
                        result.trim();

                mainHandler.post(() ->
                        callback.onResult(finalResult)
                );

            } catch (Exception e) {

                e.printStackTrace();

                String message =
                        e.getMessage();

                if (message == null ||
                        message.trim().isEmpty()) {

                    message =
                            "Offline speech recognition failed";
                }

                postError(
                        callback,
                        message
                );
            }
        });
    }

    private void initializeRecognizer(
            Context context
    ) throws Exception {

        if (recognizer != null) {
            return;
        }

        synchronized (this) {

            if (recognizer != null) {
                return;
            }

            File modelDirectory =
                    new File(
                            context.getFilesDir(),
                            "indicconformer"
                    );

            if (!modelDirectory.exists()) {

                if (!modelDirectory.mkdirs() &&
                        !modelDirectory.exists()) {

                    throw new Exception(
                            "Unable to create ASR model directory"
                    );
                }
            }

            File modelFile =
                    new File(
                            modelDirectory,
                            "model.int8.onnx"
                    );

            File tokensFile =
                    new File(
                            modelDirectory,
                            "tokens.txt"
                    );

            copyAssetIfNeeded(
                    context,
                    "indicconformer/model.int8.onnx",
                    modelFile
            );

            copyAssetIfNeeded(
                    context,
                    "indicconformer/tokens.txt",
                    tokensFile
            );

            /*
             * NeMo CTC model configuration.
             */
            OfflineNemoEncDecCtcModelConfig nemoConfig =
                    new OfflineNemoEncDecCtcModelConfig();

            nemoConfig.setModel(
                    modelFile.getAbsolutePath()
            );

            /*
             * General offline model configuration.
             */
            OfflineModelConfig modelConfig =
                    new OfflineModelConfig();

            modelConfig.setNemo(
                    nemoConfig
            );

            modelConfig.setTokens(
                    tokensFile.getAbsolutePath()
            );

            modelConfig.setNumThreads(2);

            modelConfig.setDebug(false);

            modelConfig.setProvider("cpu");

            /*
             * Recognizer configuration.
             *
             * IMPORTANT:
             * The property is "modelConfig", so the
             * generated Java setter is setModelConfig().
             */
            OfflineRecognizerConfig recognizerConfig =
                    new OfflineRecognizerConfig();

            recognizerConfig.setModelConfig(
                    modelConfig
            );

            recognizerConfig.setDecodingMethod(
                    "greedy_search"
            );

            /*
             * v1.13.4 exposes the constructor:
             *
             * OfflineRecognizer(
             *     AssetManager?,
             *     OfflineRecognizerConfig
             * )
             *
             * null means load model files from disk.
             */
            recognizer =
                    new OfflineRecognizer(
                            null,
                            recognizerConfig
                    );
        }
    }

    private String recognize(
            float[] samples
    ) {

        if (recognizer == null) {

            throw new IllegalStateException(
                    "Recognizer is not initialized"
            );
        }

        OfflineStream stream = null;

        try {

            stream =
                    recognizer.createStream();

            stream.acceptWaveform(
                    samples,
                    SAMPLE_RATE
            );

            recognizer.decode(
                    stream
            );

            return recognizer
                    .getResult(stream)
                    .getText();

        } finally {

            if (stream != null) {
                stream.release();
            }
        }
    }

    public void stopListening() {

        recording = false;

        releaseRecorder();
    }

    private void releaseRecorder() {

        try {

            if (audioRecord != null) {

                if (audioRecord.getRecordingState()
                        == AudioRecord.RECORDSTATE_RECORDING) {

                    audioRecord.stop();
                }

                audioRecord.release();
            }

        } catch (Exception ignored) {
        }

        audioRecord = null;
    }

    public void release() {

        stopListening();

        synchronized (this) {

            if (recognizer != null) {

                try {
                    recognizer.release();
                } catch (Exception ignored) {
                }

                recognizer = null;
            }
        }

        executor.shutdownNow();
    }

    private float[] mergeChunks(
            List<float[]> chunks
    ) {

        int total = 0;

        for (float[] chunk : chunks) {
            total += chunk.length;
        }

        float[] merged =
                new float[total];

        int position = 0;

        for (float[] chunk : chunks) {

            System.arraycopy(
                    chunk,
                    0,
                    merged,
                    position,
                    chunk.length
            );

            position += chunk.length;
        }

        return merged;
    }

    private void copyAssetIfNeeded(
            Context context,
            String assetPath,
            File destination
    ) throws Exception {

        if (destination.exists() &&
                destination.length() > 0) {

            return;
        }

        File parent =
                destination.getParentFile();

        if (parent != null &&
                !parent.exists()) {

            if (!parent.mkdirs() &&
                    !parent.exists()) {

                throw new Exception(
                        "Unable to create directory: "
                                + parent.getAbsolutePath()
                );
            }
        }

        try (
                InputStream input =
                        context.getAssets()
                                .open(assetPath);

                FileOutputStream output =
                        new FileOutputStream(
                                destination
                        )
        ) {

            byte[] buffer =
                    new byte[8192];

            int count;

            while ((count =
                    input.read(buffer)) != -1) {

                output.write(
                        buffer,
                        0,
                        count
                );
            }

            output.flush();
        }
    }

    private void postError(
            ResultCallback callback,
            String error
    ) {

        mainHandler.post(() ->
                callback.onError(error)
        );
    }
}