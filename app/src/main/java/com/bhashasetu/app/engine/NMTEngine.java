package com.bhashasetu.app.engine;

import android.content.Context;
import android.util.Log;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NMTEngine {

    private static final String TAG = "NMTEngine";

    private static final int DECODER_START_ID = 2;
    private static final int EOS_ID = 2;
    private static final int PAD_ID = 1;

    private static final String SOURCE_LANG = "hin_Deva";
    private static final String TARGET_LANG = "sat_Olck";

    private static final int MAX_NEW_TOKENS = 128;

    private OrtEnvironment ortEnvironment;

    private OrtSession encoderSession;
    private OrtSession decoderSession;
    private OrtSession decoderWithPastSession;

    private HFTokenizer sourceTokenizer;
    private HFTokenizer targetTokenizer;

    private volatile boolean initialized = false;

    public synchronized void initialize(Context context) {

        if (initialized && isReady()) {
            return;
        }

        close();

        try {

            Log.i(TAG, "======================================");
            Log.i(TAG, "Starting lazy IndicTrans2 initialization");
            Log.i(TAG, "======================================");

            System.loadLibrary("hfta");

            ortEnvironment =
                    OrtEnvironment.getEnvironment();

            File modelDir =
                    new File(
                            context.getFilesDir(),
                            "models/indictrans2"
                    );

            if (!modelDir.exists()) {

                if (!modelDir.mkdirs()
                        && !modelDir.exists()) {

                    throw new IOException(
                            "Could not create model directory: "
                                    + modelDir.getAbsolutePath()
                    );
                }
            }

            copyAsset(
                    context,
                    "models/indictrans2/encoder_model.onnx",
                    new File(
                            modelDir,
                            "encoder_model.onnx"
                    )
            );

            copyAsset(
                    context,
                    "models/indictrans2/encoder_model.onnx.data",
                    new File(
                            modelDir,
                            "encoder_model.onnx.data"
                    )
            );

            copyAsset(
                    context,
                    "models/indictrans2/decoder_model.onnx",
                    new File(
                            modelDir,
                            "decoder_model.onnx"
                    )
            );

            copyAsset(
                    context,
                    "models/indictrans2/decoder_shared.onnx.data",
                    new File(
                            modelDir,
                            "decoder_shared.onnx.data"
                    )
            );

            copyAsset(
                    context,
                    "models/indictrans2/decoder_with_past_model.onnx",
                    new File(
                            modelDir,
                            "decoder_with_past_model.onnx"
                    )
            );

            File sourceTokenizerFile =
                    new File(
                            modelDir,
                            "tokenizer_src.json"
                    );

            copyAsset(
                    context,
                    "models/indictrans2/tokenizer_src.json",
                    sourceTokenizerFile
            );

            sourceTokenizer =
                    new HFTokenizer(
                            readFileBytes(
                                    sourceTokenizerFile
                            )
                    );

            File targetTokenizerFile =
                    new File(
                            modelDir,
                            "tokenizer_tgt.json"
                    );

            copyAsset(
                    context,
                    "models/indictrans2/tokenizer_tgt.json",
                    targetTokenizerFile
            );

            targetTokenizer =
                    new HFTokenizer(
                            readFileBytes(
                                    targetTokenizerFile
                            )
                    );

            Log.i(TAG, "Source tokenizer loaded");
            Log.i(TAG, "Target tokenizer loaded");

            OrtSession.SessionOptions options =
                    new OrtSession.SessionOptions();

            try {

                encoderSession =
                        ortEnvironment.createSession(
                                new File(
                                        modelDir,
                                        "encoder_model.onnx"
                                ).getAbsolutePath(),
                                options
                        );

                decoderSession =
                        ortEnvironment.createSession(
                                new File(
                                        modelDir,
                                        "decoder_model.onnx"
                                ).getAbsolutePath(),
                                options
                        );

                decoderWithPastSession =
                        ortEnvironment.createSession(
                                new File(
                                        modelDir,
                                        "decoder_with_past_model.onnx"
                                ).getAbsolutePath(),
                                options
                        );

            } finally {

                options.close();
            }

            initialized = true;

            if (!isReady()) {

                throw new IllegalStateException(
                        "IndicTrans2 components are incomplete"
                );
            }

            Log.i(TAG, "======================================");
            Log.i(TAG, "IndicTrans2 loaded successfully");
            Log.i(TAG, "======================================");

            Log.i(
                    TAG,
                    "Encoder inputs: "
                            + encoderSession.getInputNames()
            );

            Log.i(
                    TAG,
                    "Encoder outputs: "
                            + encoderSession.getOutputNames()
            );

            Log.i(
                    TAG,
                    "Decoder inputs: "
                            + decoderSession.getInputNames()
            );

            Log.i(
                    TAG,
                    "Decoder outputs: "
                            + decoderSession.getOutputNames()
            );

            Log.i(
                    TAG,
                    "Decoder-with-past inputs: "
                            + decoderWithPastSession.getInputNames()
            );

            Log.i(
                    TAG,
                    "Decoder-with-past outputs: "
                            + decoderWithPastSession.getOutputNames()
            );

        } catch (Exception e) {

            initialized = false;

            Log.e(
                    TAG,
                    "FAILED TO LOAD IndicTrans2",
                    e
            );

            close();

            throw new IllegalStateException(
                    "IndicTrans2 initialization failed: "
                            + safeMessage(e),
                    e
            );
        }
    }

    public synchronized boolean isReady() {

        return initialized
                && ortEnvironment != null
                && sourceTokenizer != null
                && targetTokenizer != null
                && encoderSession != null
                && decoderSession != null
                && decoderWithPastSession != null;
    }

    /**
     * Hindi -> Santali.
     *
     * Completely offline.
     *
     * Decoder flow:
     *
     * First decoder input:
     * [2]
     *
     * First decoder output:
     * next token
     *
     * Subsequent decoder calls:
     * decoder_with_past_model.onnx
     */
    public TranslationResult translate(
            String hindiText
    ) {

        if (!isReady()) {

            return new TranslationResult(
                    "[MODEL NOT READY]",
                    0.0f,
                    "indictrans2-error"
            );
        }

        if (
                hindiText == null
                        || hindiText.trim().isEmpty()
        ) {

            return new TranslationResult(
                    "",
                    0.0f,
                    "indictrans2"
            );
        }

        OrtSession.Result encoderResult = null;
        OrtSession.Result decoderResult = null;

        OnnxTensor encoderInputIds = null;
        OnnxTensor encoderAttentionMask = null;
        OnnxTensor decoderInputIds = null;

        try {

            String modelInput =
                    SOURCE_LANG
                            + " "
                            + TARGET_LANG
                            + " "
                            + hindiText.trim();

            Log.i(
                    TAG,
                    "MODEL INPUT: "
                            + modelInput
            );

            /*
             * -------------------------------------------------
             * SOURCE TOKENIZATION
             * -------------------------------------------------
             */

            int[] sourceIds =
                    sourceTokenizer.encode(
                            modelInput
                    );

            if (
                    sourceIds == null
                            || sourceIds.length == 0
            ) {

                throw new Exception(
                        "Source tokenizer returned no tokens"
                );
            }

            Log.i(
                    TAG,
                    "SOURCE TOKEN COUNT: "
                            + sourceIds.length
            );

            long[][] inputIds =
                    new long[1][sourceIds.length];

            long[][] attentionMask =
                    new long[1][sourceIds.length];

            for (
                    int i = 0;
                    i < sourceIds.length;
                    i++
            ) {

                inputIds[0][i] =
                        sourceIds[i];

                attentionMask[0][i] = 1;
            }

            encoderInputIds =
                    OnnxTensor.createTensor(
                            ortEnvironment,
                            inputIds
                    );

            encoderAttentionMask =
                    OnnxTensor.createTensor(
                            ortEnvironment,
                            attentionMask
                    );

            Map<String, OnnxTensor> encoderInputs =
                    new HashMap<>();

            encoderInputs.put(
                    "input_ids",
                    encoderInputIds
            );

            encoderInputs.put(
                    "attention_mask",
                    encoderAttentionMask
            );

            /*
             * -------------------------------------------------
             * ENCODER
             * -------------------------------------------------
             */

            encoderResult =
                    encoderSession.run(
                            encoderInputs
                    );

            if (encoderResult.size() == 0) {

                throw new Exception(
                        "Encoder returned no outputs"
                );
            }

            OnnxTensor encoderHiddenStates =
                    (OnnxTensor)
                            encoderResult.get(0);

            Log.i(
                    TAG,
                    "ENCODER INFERENCE COMPLETE"
            );

            /*
             * -------------------------------------------------
             * FIRST DECODER STEP
             * -------------------------------------------------
             *
             * IMPORTANT:
             *
             * Only token 2 goes into the first decoder call.
             *
             * We DO NOT put 29925 into the decoder input.
             *
             * IndicTrans2's target language is already specified
             * in the source-side language tags:
             *
             * hin_Deva sat_Olck ...
             */

            long[][] firstDecoderIds =
                    new long[][]{
                            {
                                    DECODER_START_ID
                            }
                    };

            decoderInputIds =
                    OnnxTensor.createTensor(
                            ortEnvironment,
                            firstDecoderIds
                    );

            Map<String, OnnxTensor> firstDecoderInputs =
                    new HashMap<>();

            firstDecoderInputs.put(
                    "input_ids",
                    decoderInputIds
            );

            firstDecoderInputs.put(
                    "encoder_attention_mask",
                    encoderAttentionMask
            );

            firstDecoderInputs.put(
                    "encoder_hidden_states",
                    encoderHiddenStates
            );

            decoderResult =
                    decoderSession.run(
                            firstDecoderInputs
                    );

            if (decoderResult.size() == 0) {

                throw new Exception(
                        "Decoder returned no outputs"
                );
            }

            float[] logits =
                    extractLastLogits(
                            decoderResult
                                    .get(0)
                                    .getValue()
                    );

            int nextToken =
                    argmax(logits);

            float confidenceSum =
                    softmaxProbability(
                            logits,
                            nextToken
                    );

            int confidenceCount = 1;

            Log.i(
                    TAG,
                    "FIRST GENERATED TOKEN: "
                            + nextToken
            );

            /*
             * First decoder output:
             *
             * 0 = logits
             * 1+ = KV cache tensors
             */

            int remainingOutputs =
                    decoderResult.size() - 1;

            if (
                    remainingOutputs <= 0
                            || remainingOutputs % 4 != 0
            ) {

                throw new Exception(
                        "Unexpected decoder output count: "
                                + decoderResult.size()
                );
            }

            int numLayers =
                    remainingOutputs / 4;

            Log.i(
                    TAG,
                    "NUM LAYERS: "
                            + numLayers
            );

            List<OnnxTensor> pastTensors =
                    new ArrayList<>();

            for (
                    int i = 1;
                    i < decoderResult.size();
                    i++
            ) {

                pastTensors.add(
                        (OnnxTensor)
                                decoderResult.get(i)
                );
            }

            /*
             * Actual generated sequence.
             *
             * The start token is included only so that we can
             * remove it before target decoding.
             */

            List<Integer> generated =
                    new ArrayList<>();

            generated.add(
                    DECODER_START_ID
            );

            if (
                    nextToken != EOS_ID
                            && nextToken != PAD_ID
            ) {

                generated.add(
                        nextToken
                );
            }

            /*
             * First decoder input is no longer needed.
             * The decoder RESULT must remain alive because its
             * tensors are our KV cache.
             */

            decoderInputIds.close();
            decoderInputIds = null;

            /*
             * -------------------------------------------------
             * DECODER WITH PAST
             * -------------------------------------------------
             */

            for (
                    int step = 1;
                    step < MAX_NEW_TOKENS;
                    step++
            ) {

                if (
                        nextToken == EOS_ID
                                || nextToken == PAD_ID
                ) {

                    break;
                }

                long[][] nextIds =
                        new long[][]{
                                {
                                        nextToken
                                }
                        };

                OnnxTensor nextInputTensor =
                        OnnxTensor.createTensor(
                                ortEnvironment,
                                nextIds
                        );

                OrtSession.Result newDecoderResult =
                        null;

                try {

                    Map<String, OnnxTensor> pastInputs =
                            new HashMap<>();

                    pastInputs.put(
                            "input_ids",
                            nextInputTensor
                    );

                    pastInputs.put(
                            "encoder_attention_mask",
                            encoderAttentionMask
                    );

                    for (
                            int layer = 0;
                            layer < numLayers;
                            layer++
                    ) {

                        int base =
                                layer * 4;

                        pastInputs.put(
                                "past_key_values."
                                        + layer
                                        + ".decoder.key",
                                pastTensors.get(base)
                        );

                        pastInputs.put(
                                "past_key_values."
                                        + layer
                                        + ".decoder.value",
                                pastTensors.get(base + 1)
                        );

                        pastInputs.put(
                                "past_key_values."
                                        + layer
                                        + ".encoder.key",
                                pastTensors.get(base + 2)
                        );

                        pastInputs.put(
                                "past_key_values."
                                        + layer
                                        + ".encoder.value",
                                pastTensors.get(base + 3)
                        );
                    }

                    newDecoderResult =
                            decoderWithPastSession.run(
                                    pastInputs
                            );

                    if (
                            newDecoderResult.size() == 0
                    ) {

                        throw new Exception(
                                "Decoder-with-past returned no outputs"
                        );
                    }

                    if (decoderResult != null) {

                        decoderResult.close();
                    }

                    decoderResult =
                            newDecoderResult;

                    newDecoderResult = null;

                    float[] nextLogits =
                            extractLastLogits(
                                    decoderResult
                                            .get(0)
                                            .getValue()
                            );

                    int generatedToken =
                            argmax(
                                    nextLogits
                            );

                    confidenceSum +=
                            softmaxProbability(
                                    nextLogits,
                                    generatedToken
                            );

                    confidenceCount++;

                    Log.d(
                            TAG,
                            "STEP "
                                    + step
                                    + " -> token "
                                    + generatedToken
                    );

                    /*
                     * Rebuild KV cache from the newest decoder
                     * result.
                     */

                    pastTensors =
                            new ArrayList<>();

                    for (
                            int i = 1;
                            i < decoderResult.size();
                            i++
                    ) {

                        pastTensors.add(
                                (OnnxTensor)
                                        decoderResult.get(i)
                        );
                    }

                    nextToken =
                            generatedToken;

                    if (
                            nextToken == EOS_ID
                                    || nextToken == PAD_ID
                    ) {

                        break;
                    }

                    generated.add(
                            nextToken
                    );

                } finally {

                    nextInputTensor.close();

                    if (newDecoderResult != null) {

                        try {
                            newDecoderResult.close();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

            Log.i(
                    TAG,
                    "RAW GENERATED IDS: "
                            + generated
            );

            /*
             * -------------------------------------------------
             * REMOVE START TOKEN
             * -------------------------------------------------
             */

            List<Integer> cleanGenerated =
                    new ArrayList<>(
                            generated
                    );

            if (
                    !cleanGenerated.isEmpty()
                            && cleanGenerated.get(0)
                            == DECODER_START_ID
            ) {

                cleanGenerated.remove(0);
            }

            /*
             * Remove EOS/PAD from end.
             */

            while (
                    !cleanGenerated.isEmpty()
                            && (
                            cleanGenerated.get(
                                    cleanGenerated.size() - 1
                            ) == EOS_ID
                                    || cleanGenerated.get(
                                    cleanGenerated.size() - 1
                            ) == PAD_ID
                    )
            ) {

                cleanGenerated.remove(
                        cleanGenerated.size() - 1
                );
            }

            Log.i(
                    TAG,
                    "CLEAN OUTPUT IDS: "
                            + cleanGenerated
            );

            if (cleanGenerated.isEmpty()) {

                Log.w(
                        TAG,
                        "MODEL GENERATED ZERO CONTENT TOKENS"
                );

                return new TranslationResult(
                        "[EMPTY MODEL OUTPUT]",
                        confidenceCount == 0
                                ? 0.0f
                                : confidenceSum
                                  / confidenceCount,
                        "indictrans2-santhali"
                );
            }

            /*
             * -------------------------------------------------
             * TARGET TOKENIZER
             * -------------------------------------------------
             */

            int[] outputIds =
                    new int[
                            cleanGenerated.size()
                            ];

            for (
                    int i = 0;
                    i < cleanGenerated.size();
                    i++
            ) {

                outputIds[i] =
                        cleanGenerated.get(i);
            }

            String translated =
                    targetTokenizer.decode(
                            outputIds
                    );

            if (
                    translated == null
                            || translated.trim().isEmpty()
            ) {

                translated =
                        "[EMPTY MODEL OUTPUT]";
            }

            float confidence =
                    confidenceCount == 0
                            ? 0.0f
                            : confidenceSum
                              / confidenceCount;

            Log.i(
                    TAG,
                    "TRANSLATION RESULT: "
                            + translated
            );

            return new TranslationResult(
                    translated.trim(),
                    confidence,
                    "indictrans2-santhali"
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "TRANSLATION FAILED",
                    e
            );

            return new TranslationResult(
                    "[TRANSLATION ERROR: "
                            + safeMessage(e)
                            + "]",
                    0.0f,
                    "indictrans2-error"
            );

        } finally {

            if (decoderInputIds != null) {

                try {
                    decoderInputIds.close();
                } catch (Exception ignored) {
                }
            }

            if (decoderResult != null) {

                try {
                    decoderResult.close();
                } catch (Exception ignored) {
                }
            }

            if (encoderResult != null) {

                try {
                    encoderResult.close();
                } catch (Exception ignored) {
                }
            }

            if (encoderInputIds != null) {

                try {
                    encoderInputIds.close();
                } catch (Exception ignored) {
                }
            }

            if (encoderAttentionMask != null) {

                try {
                    encoderAttentionMask.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public TranslationResult translate(
            String hindiText,
            String targetLang
    ) {
        return translate(hindiText);
    }

    private float[] extractLastLogits(
            Object value
    ) {

        if (value instanceof float[][][]) {

            float[][][] data =
                    (float[][][]) value;

            if (
                    data.length == 0
                            || data[0].length == 0
            ) {

                throw new IllegalStateException(
                        "Empty 3D logits tensor"
                );
            }

            return data[0][
                    data[0].length - 1
                    ];
        }

        if (value instanceof float[][]) {

            float[][] data =
                    (float[][]) value;

            if (data.length == 0) {

                throw new IllegalStateException(
                        "Empty 2D logits tensor"
                );
            }

            return data[
                    data.length - 1
                    ];
        }

        throw new IllegalStateException(
                "Unexpected logits type: "
                        + (
                        value == null
                                ? "null"
                                : value.getClass()
                                .getName()
                )
        );
    }

    private int argmax(
            float[] values
    ) {

        if (
                values == null
                        || values.length == 0
        ) {

            throw new IllegalArgumentException(
                    "Empty logits"
            );
        }

        int bestIndex = 0;

        float bestValue =
                Float.NEGATIVE_INFINITY;

        for (
                int i = 0;
                i < values.length;
                i++
        ) {

            if (
                    values[i] > bestValue
            ) {

                bestValue =
                        values[i];

                bestIndex =
                        i;
            }
        }

        return bestIndex;
    }

    private float softmaxProbability(
            float[] logits,
            int selected
    ) {

        if (
                logits == null
                        || logits.length == 0
                        || selected < 0
                        || selected >= logits.length
        ) {

            return 0.0f;
        }

        float max =
                Float.NEGATIVE_INFINITY;

        for (float value : logits) {

            if (value > max) {
                max = value;
            }
        }

        double denominator = 0.0;

        for (float value : logits) {

            denominator +=
                    Math.exp(
                            value - max
                    );
        }

        if (
                denominator == 0.0
                        || Double.isNaN(denominator)
                        || Double.isInfinite(denominator)
        ) {

            return 0.0f;
        }

        return (float)
                (
                        Math.exp(
                                logits[selected] - max
                        )
                                / denominator
                );
    }

    private void copyAsset(
            Context context,
            String assetPath,
            File destination
    ) throws Exception {

        if (destination.exists()) {
            return;
        }

        File parent =
                destination.getParentFile();

        if (
                parent != null
                        && !parent.exists()
        ) {

            if (
                    !parent.mkdirs()
                            && !parent.exists()
            ) {

                throw new IOException(
                        "Could not create directory: "
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
                    new byte[1024 * 1024];

            int length;

            while (
                    (length =
                            input.read(buffer))
                            != -1
            ) {

                output.write(
                        buffer,
                        0,
                        length
                );
            }

            output.flush();
        }
    }

    private byte[] readFileBytes(
            File file
    ) throws IOException {

        try (
                FileInputStream input =
                        new FileInputStream(file);

                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()
        ) {

            byte[] buffer =
                    new byte[8192];

            int length;

            while (
                    (length =
                            input.read(buffer))
                            != -1
            ) {

                output.write(
                        buffer,
                        0,
                        length
                );
            }

            return output.toByteArray();
        }
    }

    private String safeMessage(
            Throwable throwable
    ) {

        if (throwable == null) {
            return "Unknown error";
        }

        String message =
                throwable.getMessage();

        if (
                message == null
                        || message.trim().isEmpty()
        ) {

            return throwable
                    .getClass()
                    .getSimpleName();
        }

        return message;
    }

    public synchronized void close() {

        initialized = false;

        if (encoderSession != null) {

            try {
                encoderSession.close();
            } catch (Exception ignored) {
            }

            encoderSession = null;
        }

        if (decoderSession != null) {

            try {
                decoderSession.close();
            } catch (Exception ignored) {
            }

            decoderSession = null;
        }

        if (decoderWithPastSession != null) {

            try {
                decoderWithPastSession.close();
            } catch (Exception ignored) {
            }

            decoderWithPastSession = null;
        }

        if (sourceTokenizer != null) {

            try {
                sourceTokenizer.close();
            } catch (Exception ignored) {
            }

            sourceTokenizer = null;
        }

        if (targetTokenizer != null) {

            try {
                targetTokenizer.close();
            } catch (Exception ignored) {
            }

            targetTokenizer = null;
        }

        /*
         * OrtEnvironment is shared by ONNX Runtime.
         * Do not close the global environment here.
         */
        ortEnvironment = null;
    }

    public static class TranslationResult {

        public final String hoText;
        public final float confidence;
        public final String modelUsed;

        public TranslationResult(
                String hoText,
                float confidence,
                String modelUsed
        ) {

            this.hoText =
                    hoText;

            this.confidence =
                    confidence;

            this.modelUsed =
                    modelUsed;
        }
    }
}