package com.bhashasetu.app.engine;

import android.content.Context;
import android.content.res.AssetManager;

import org.json.JSONArray;
import org.json.JSONObject;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class IndicXlitEngine {

    private static final String MODEL_DIR =
            "models/indicxlit/";

    private static final String ENCODER_FILE =
            MODEL_DIR + "indicxlit_encoder.onnx";

    private static final String DECODER_FILE =
            MODEL_DIR + "indicxlit_decoder_v2.onnx";

    private static final String VOCAB_FILE =
            MODEL_DIR + "vocab.json";

    private static final String HINDI_LANG =
            "__hi__";

    private static final int BEAM_WIDTH = 4;
    private static final int MAX_OUTPUT_LENGTH = 20;
    private static final int MIN_OUTPUT_LENGTH = 1;

    private final Context context;

    private OrtEnvironment environment;
    private OrtSession encoderSession;
    private OrtSession decoderSession;

    private String[] targetVocabulary;

    private final Map<String, Integer> sourceTokenIds =
            new HashMap<>();

    private int bosId = 0;
    private int padId = 1;
    private int eosId = 2;
    private int unkId = 3;

    private boolean initialized = false;

    public IndicXlitEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    public synchronized void initialize() throws Exception {

        if (initialized) {
            return;
        }

        AssetManager assets = context.getAssets();

        // ---------------------------------------------------------
        // Load vocabulary
        // ---------------------------------------------------------

        String vocabText =
                readAsset(
                        assets,
                        VOCAB_FILE
                );

        JSONObject vocab =
                new JSONObject(vocabText);

        JSONArray srcArray =
                vocab.getJSONArray("src");

        JSONArray tgtArray =
                vocab.getJSONArray("tgt");

        sourceTokenIds.clear();

        for (int i = 0; i < srcArray.length(); i++) {

            String token =
                    srcArray.getString(i);

            sourceTokenIds.put(
                    token,
                    i
            );
        }

        targetVocabulary =
                new String[tgtArray.length()];

        for (int i = 0; i < tgtArray.length(); i++) {

            targetVocabulary[i] =
                    tgtArray.getString(i);
        }

        // ---------------------------------------------------------
        // Special tokens
        // ---------------------------------------------------------

        if (vocab.has("special_tokens")) {

            JSONObject special =
                    vocab.getJSONObject(
                            "special_tokens"
                    );

            bosId =
                    special.optInt(
                            "bos",
                            bosId
                    );

            padId =
                    special.optInt(
                            "pad",
                            padId
                    );

            eosId =
                    special.optInt(
                            "eos",
                            eosId
                    );

            unkId =
                    special.optInt(
                            "unk",
                            unkId
                    );
        }

        if (!sourceTokenIds.containsKey(HINDI_LANG)) {

            throw new IllegalStateException(
                    "IndicXlit Hindi token __hi__ not found"
            );
        }

        // ---------------------------------------------------------
        // ONNX Runtime
        // ---------------------------------------------------------

        environment =
                OrtEnvironment.getEnvironment();

        OrtSession.SessionOptions options =
                new OrtSession.SessionOptions();

        options.setIntraOpNumThreads(2);
        options.setInterOpNumThreads(1);

        byte[] encoderBytes =
                readAssetBytes(
                        assets,
                        ENCODER_FILE
                );

        byte[] decoderBytes =
                readAssetBytes(
                        assets,
                        DECODER_FILE
                );

        encoderSession =
                environment.createSession(
                        encoderBytes,
                        options
                );

        decoderSession =
                environment.createSession(
                        decoderBytes,
                        options
                );

        initialized = true;
    }

    public boolean isInitialized() {
        return initialized;
    }

    // =============================================================
    // PUBLIC SENTENCE API
    // =============================================================

    public String transliterateSentence(
            String sentence
    ) throws Exception {

        if (sentence == null ||
                sentence.trim().isEmpty()) {

            return "";
        }

        if (!initialized) {
            initialize();
        }

        String trimmed =
                sentence.trim();

        String[] words =
                trimmed.split("\\s+");

        StringBuilder result =
                new StringBuilder();

        for (String word : words) {

            if (word == null ||
                    word.isEmpty()) {

                continue;
            }

            String converted =
                    transliterateWord(word);

            if (result.length() > 0) {
                result.append(" ");
            }

            result.append(converted);
        }

        return result.toString();
    }

    // =============================================================
    // WORD TRANSLITERATION
    // =============================================================

    private String transliterateWord(
            String original
    ) throws Exception {

        if (original == null ||
                original.isEmpty()) {

            return original;
        }

        if (containsDevanagari(original)) {
            return original;
        }

        // ---------------------------------------------------------
        // Preserve punctuation
        // ---------------------------------------------------------

        StringBuilder prefix =
                new StringBuilder();

        StringBuilder suffix =
                new StringBuilder();

        String word = original;

        while (!word.isEmpty() &&
                !Character.isLetter(
                        word.charAt(0)
                )) {

            prefix.append(
                    word.charAt(0)
            );

            word =
                    word.substring(1);
        }

        while (!word.isEmpty() &&
                !Character.isLetter(
                        word.charAt(
                                word.length() - 1
                        )
                )) {

            suffix.insert(
                    0,
                    word.charAt(
                            word.length() - 1
                    )
            );

            word =
                    word.substring(
                            0,
                            word.length() - 1
                    );
        }

        if (word.isEmpty()) {
            return original;
        }

        // Only transliterate Latin-script words.
        // No fixed dictionary is used.
        if (!word.matches("[A-Za-z]+")) {
            return original;
        }

        int[] sourceIds =
                encodeWord(word);

        long[] inputIds =
                new long[sourceIds.length];

        for (int i = 0;
             i < sourceIds.length;
             i++) {

            inputIds[i] =
                    sourceIds[i];
        }

        // ---------------------------------------------------------
        // Encoder
        // ---------------------------------------------------------

        OnnxTensor sourceTensor =
                OnnxTensor.createTensor(
                        environment,
                        new long[][]{
                                inputIds
                        }
                );

        OrtSession.Result encoderResult =
                encoderSession.run(
                        createEncoderInputs(
                                sourceTensor
                        )
                );

        try {

            OnnxTensor encoderOut =
                    (OnnxTensor)
                            encoderResult
                                    .get("encoder_out")
                                    .get();

            String output =
                    beamSearch(
                            encoderOut
                    );

            return prefix
                    + output
                    + suffix;

        } finally {

            sourceTensor.close();
            encoderResult.close();
        }
    }

    // =============================================================
    // ENCODER INPUTS
    // =============================================================

    private Map<String, OnnxTensor>
    createEncoderInputs(
            OnnxTensor sourceTensor
    ) {

        Map<String, OnnxTensor> inputs =
                new HashMap<>();

        inputs.put(
                "src_tokens",
                sourceTensor
        );

        return inputs;
    }

    // =============================================================
    // BEAM SEARCH
    // =============================================================

    private String beamSearch(
            OnnxTensor encoderOut
    ) throws Exception {

        List<Beam> beams =
                new ArrayList<>();

        beams.add(
                new Beam(
                        0.0,
                        new ArrayList<Integer>() {{
                            add(eosId);
                        }},
                        false
                )
        );

        List<Beam> finished =
                new ArrayList<>();

        for (int step = 0;
             step < MAX_OUTPUT_LENGTH;
             step++) {

            List<Beam> candidates =
                    new ArrayList<>();

            for (Beam beam : beams) {

                if (beam.finished) {
                    continue;
                }

                long[] previousTokens =
                        new long[
                                beam.tokens.size()
                                ];

                for (int i = 0;
                     i < beam.tokens.size();
                     i++) {

                    previousTokens[i] =
                            beam.tokens.get(i);
                }

                OnnxTensor previousTensor =
                        OnnxTensor.createTensor(
                                environment,
                                new long[][]{
                                        previousTokens
                                }
                        );

                Map<String, OnnxTensor>
                        decoderInputs =
                        new HashMap<>();

                decoderInputs.put(
                        "prev_tokens",
                        previousTensor
                );

                decoderInputs.put(
                        "encoder_out",
                        encoderOut
                );

                OrtSession.Result decoderResult =
                        decoderSession.run(
                                decoderInputs
                        );

                try {

                    OnnxTensor decoderOut =
                            (OnnxTensor)
                                    decoderResult
                                            .get("decoder_out")
                                            .get();

                    Object rawValue =
                            decoderOut.getValue();

                    float[] logits =
                            extractLastLogits(
                                    rawValue
                            );

                    // Prevent padding and unknown-token output.
                    logits[padId] =
                            Float.NEGATIVE_INFINITY;

                    logits[unkId] =
                            Float.NEGATIVE_INFINITY;

                    // Do not allow EOS before at least
                    // one output token has been generated.
                    if (step <
                            MIN_OUTPUT_LENGTH) {

                        logits[eosId] =
                                Float.NEGATIVE_INFINITY;
                    }

                    List<TokenCandidate>
                            nextTokens =
                            getTopCandidates(
                                    logits,
                                    BEAM_WIDTH * 2
                            );

                    for (TokenCandidate token :
                            nextTokens) {

                        List<Integer>
                                newTokens =
                                new ArrayList<>(
                                        beam.tokens
                                );

                        newTokens.add(
                                token.id
                        );

                        double newScore =
                                beam.score +
                                        token.logProbability;

                        if (token.id == eosId) {

                            finished.add(
                                    new Beam(
                                            newScore,
                                            newTokens,
                                            true
                                    )
                            );

                        } else {

                            candidates.add(
                                    new Beam(
                                            newScore,
                                            newTokens,
                                            false
                                    )
                            );
                        }
                    }

                } finally {

                    previousTensor.close();
                    decoderResult.close();
                }
            }

            candidates.sort(
                    (a, b) ->
                            Double.compare(
                                    b.score,
                                    a.score
                            )
            );

            if (candidates.size() >
                    BEAM_WIDTH) {

                candidates =
                        new ArrayList<>(
                                candidates.subList(
                                        0,
                                        BEAM_WIDTH
                                )
                        );
            }

            beams = candidates;

            if (beams.isEmpty()) {
                break;
            }

            // Once enough completed sequences exist,
            // stop when unfinished beams cannot reasonably
            // overtake them.
            if (finished.size() >=
                    BEAM_WIDTH) {

                double bestFinished =
                        getBestNormalizedScore(
                                finished
                        );

                double bestUnfinished =
                        getBestNormalizedScore(
                                beams
                        );

                if (bestFinished >=
                        bestUnfinished) {

                    break;
                }
            }
        }

        // Add unfinished beams.
        finished.addAll(beams);

        if (finished.isEmpty()) {
            return "";
        }

        finished.sort(
                (a, b) ->
                        Double.compare(
                                normalizedScore(b),
                                normalizedScore(a)
                        )
        );

        Beam best =
                finished.get(0);

        List<Integer> outputTokens =
                new ArrayList<>();

        // Ignore initial EOS.
        for (int i = 1;
             i < best.tokens.size();
             i++) {

            int tokenId =
                    best.tokens.get(i);

            if (tokenId == eosId) {
                break;
            }

            if (tokenId == padId ||
                    tokenId == unkId ||
                    tokenId == bosId) {
                continue;
            }

            outputTokens.add(
                    tokenId
            );
        }

        return decodeTokens(
                outputTokens
        );
    }

    // =============================================================
    // TOP TOKEN CANDIDATES
    // =============================================================

    private List<TokenCandidate>
    getTopCandidates(
            float[] logits,
            int count
    ) {

        float maxLogit =
                Float.NEGATIVE_INFINITY;

        for (float value : logits) {

            if (value > maxLogit) {
                maxLogit = value;
            }
        }

        double sumExp = 0.0;

        for (float value : logits) {

            if (value !=
                    Float.NEGATIVE_INFINITY) {

                sumExp +=
                        Math.exp(
                                value - maxLogit
                        );
            }
        }

        double logSumExp =
                Math.log(sumExp);

        List<TokenCandidate> all =
                new ArrayList<>();

        for (int i = 0;
             i < logits.length;
             i++) {

            if (logits[i] ==
                    Float.NEGATIVE_INFINITY) {

                continue;
            }

            double logProbability =
                    (logits[i] - maxLogit)
                            - logSumExp;

            all.add(
                    new TokenCandidate(
                            i,
                            logProbability
                    )
            );
        }

        all.sort(
                (a, b) ->
                        Double.compare(
                                b.logProbability,
                                a.logProbability
                        )
        );

        if (all.size() > count) {

            return new ArrayList<>(
                    all.subList(
                            0,
                            count
                    )
            );
        }

        return all;
    }

    // =============================================================
    // LOGITS EXTRACTION
    // =============================================================

    private float[] extractLastLogits(
            Object value
    ) {

        if (value instanceof float[][][]) {

            float[][][] data =
                    (float[][][]) value;

            if (data.length == 0 ||
                    data[0].length == 0) {

                throw new IllegalStateException(
                        "IndicXlit decoder returned empty logits"
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
                        "IndicXlit decoder returned empty logits"
                );
            }

            return data[
                    data.length - 1
                    ];
        }

        throw new IllegalStateException(
                "Unexpected IndicXlit decoder output: "
                        + value.getClass().getName()
        );
    }

    // =============================================================
    // SOURCE TOKENIZATION
    // =============================================================

    private int[] encodeWord(
            String word
    ) {

        String normalized =
                word.toLowerCase(
                        Locale.ROOT
                );

        List<Integer> ids =
                new ArrayList<>();

        Integer languageId =
                sourceTokenIds.get(
                        HINDI_LANG
                );

        if (languageId == null) {

            throw new IllegalStateException(
                    "Hindi language token missing"
            );
        }

        ids.add(
                languageId
        );

        // Character-level encoding.
        // This is dynamic and does not use a dictionary.
        for (int i = 0;
             i < normalized.length();
             i++) {

            String character =
                    String.valueOf(
                            normalized.charAt(i)
                    );

            Integer tokenId =
                    sourceTokenIds.get(
                            character
                    );

            if (tokenId == null) {
                tokenId = unkId;
            }

            ids.add(
                    tokenId
            );
        }

        ids.add(
                eosId
        );

        int[] result =
                new int[ids.size()];

        for (int i = 0;
             i < ids.size();
             i++) {

            result[i] =
                    ids.get(i);
        }

        return result;
    }

    // =============================================================
    // TARGET DECODING
    // =============================================================

    private String decodeTokens(
            List<Integer> tokenIds
    ) {

        StringBuilder result =
                new StringBuilder();

        for (Integer id :
                tokenIds) {

            if (id == null) {
                continue;
            }

            if (id < 0 ||
                    id >= targetVocabulary.length) {
                continue;
            }

            String token =
                    targetVocabulary[id];

            if (token == null) {
                continue;
            }

            result.append(
                    token
            );
        }

        return result
                .toString()
                .replace(
                        " ",
                        ""
                );
    }

    // =============================================================
    // DEVANAGARI CHECK
    // =============================================================

    private boolean containsDevanagari(
            String text
    ) {

        for (int i = 0;
             i < text.length();
             i++) {

            Character.UnicodeBlock block =
                    Character.UnicodeBlock.of(
                            text.charAt(i)
                    );

            if (block ==
                    Character.UnicodeBlock.DEVANAGARI) {

                return true;
            }
        }

        return false;
    }

    // =============================================================
    // NORMALIZED SCORE
    // =============================================================

    private double normalizedScore(
            Beam beam
    ) {

        int length =
                Math.max(
                        1,
                        beam.tokens.size() - 1
                );

        double lengthPenalty =
                Math.pow(
                        (5.0 + length) / 6.0,
                        1.0
                );

        return beam.score /
                lengthPenalty;
    }

    private double getBestNormalizedScore(
            List<Beam> list
    ) {

        double best =
                Double.NEGATIVE_INFINITY;

        for (Beam beam : list) {

            double score =
                    normalizedScore(
                            beam
                    );

            if (score > best) {
                best = score;
            }
        }

        return best;
    }

    // =============================================================
    // ASSET READING
    // =============================================================

    private String readAsset(
            AssetManager assets,
            String path
    ) throws Exception {

        try (InputStream input =
                     assets.open(path)) {

            ByteArrayOutputStream output =
                    new ByteArrayOutputStream();

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

            return output.toString(
                    "UTF-8"
            );
        }
    }

    private byte[] readAssetBytes(
            AssetManager assets,
            String path
    ) throws Exception {

        try (InputStream input =
                     assets.open(path)) {

            ByteArrayOutputStream output =
                    new ByteArrayOutputStream();

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

            return output.toByteArray();
        }
    }

    // =============================================================
    // CLOSE
    // =============================================================

    public synchronized void close() {

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

        initialized = false;
    }

    // =============================================================
    // INTERNAL CLASSES
    // =============================================================

    private static class Beam {

        final double score;
        final List<Integer> tokens;
        final boolean finished;

        Beam(
                double score,
                List<Integer> tokens,
                boolean finished
        ) {

            this.score = score;
            this.tokens = tokens;
            this.finished = finished;
        }
    }

    private static class TokenCandidate {

        final int id;
        final double logProbability;

        TokenCandidate(
                int id,
                double logProbability
        ) {

            this.id = id;
            this.logProbability =
                    logProbability;
        }
    }
}