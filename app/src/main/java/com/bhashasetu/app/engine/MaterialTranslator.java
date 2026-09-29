package com.bhashasetu.app.engine;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * MaterialTranslator
 *
 * Takes the Hindi translation_queue returned by the server and
 * translates each item locally using the offline TranslationPipeline.
 *
 * Flow:
 *
 * FastAPI
 *    ↓
 * translation_queue
 *    ↓
 * MaterialTranslator
 *    ↓
 * TranslationPipeline
 *    ↓
 * NMTEngine / IndicTrans2
 *    ↓
 * Santali
 */
public class MaterialTranslator {

    private static final String TAG =
            "MaterialTranslator";

    private static final String TARGET_LANGUAGE =
            "Santhali";

    private final TranslationPipeline pipeline;

    private volatile boolean closed = false;

    public interface Callback {

        void onComplete(
                JSONArray translatedQueue
        );

        void onError(
                String error
        );
    }

    public MaterialTranslator(
            Context context
    ) {

        pipeline =
                new TranslationPipeline(
                        context.getApplicationContext()
                );
    }

    /**
     * Translate the complete translation queue.
     */
    public void translateQueue(
            JSONArray translationQueue,
            Callback callback
    ) {

        if (callback == null) {
            return;
        }

        if (closed) {

            callback.onError(
                    "Material translator has been closed."
            );

            return;
        }

        if (translationQueue == null) {

            callback.onError(
                    "Translation queue is null."
            );

            return;
        }

        if (translationQueue.length() == 0) {

            callback.onError(
                    "Translation queue is empty."
            );

            return;
        }

        JSONArray resultQueue =
                new JSONArray();

        /*
         * Per-request cache.
         *
         * Prevents the exact same Hindi phrase from being
         * translated repeatedly in the same request.
         */
        Map<String, String> localCache =
                new HashMap<>();

        translateNext(
                translationQueue,
                0,
                resultQueue,
                localCache,
                callback
        );
    }

    /**
     * Process one item and continue recursively with the next item.
     */
    private void translateNext(
            JSONArray queue,
            int index,
            JSONArray resultQueue,
            Map<String, String> localCache,
            Callback callback
    ) {

        if (closed) {

            callback.onError(
                    "Material translator has been closed."
            );

            return;
        }

        /*
         * Finished.
         */
        if (index >= queue.length()) {

            Log.i(
                    TAG,
                    "Translation queue completed. Items: "
                            + resultQueue.length()
            );

            callback.onComplete(
                    resultQueue
            );

            return;
        }

        JSONObject item;

        try {

            item =
                    queue.getJSONObject(index);

        } catch (Exception e) {

            callback.onError(
                    "Invalid translation queue item at index "
                            + index
                            + ": "
                            + e.getMessage()
            );

            return;
        }

        /*
         * Preserve all original fields from the server.
         */
        JSONObject resultItem =
                cloneJSONObject(item);

        String type =
                item.optString(
                        "type",
                        ""
                );

        String hindiTextRaw =
                item.optString(
                        "text_hi",
                        ""
                );

        /*
         * IMPORTANT:
         *
         * hindiTextRaw is never modified after this point.
         * This allows us to safely use the final values inside
         * the lambda below.
         */
        final String typeFinal =
                type;

        final String hindiText =
                hindiTextRaw == null
                        ? ""
                        : hindiTextRaw.trim();

        /*
         * Nothing to translate.
         */
        if (hindiText.isEmpty()) {

            setTranslation(
                    resultItem,
                    ""
            );

            resultQueue.put(
                    resultItem
            );

            translateNext(
                    queue,
                    index + 1,
                    resultQueue,
                    localCache,
                    callback
            );

            return;
        }

        /*
         * Final immutable cache key.
         */
        final String cacheKey =
                hindiText;

        /*
         * Request-local cache hit.
         */
        if (localCache.containsKey(cacheKey)) {

            String cachedTranslation =
                    localCache.get(cacheKey);

            setTranslation(
                    resultItem,
                    cachedTranslation
            );

            resultQueue.put(
                    resultItem
            );

            translateNext(
                    queue,
                    index + 1,
                    resultQueue,
                    localCache,
                    callback
            );

            return;
        }

        /*
         * Structural content does not need NMT.
         *
         * Examples:
         *
         * 1
         * 1, 2, 3, 4, 5
         * 1-10
         * 2 + 3
         */
        if (isStructuralText(hindiText)) {

            localCache.put(
                    cacheKey,
                    hindiText
            );

            setTranslation(
                    resultItem,
                    hindiText
            );

            resultQueue.put(
                    resultItem
            );

            translateNext(
                    queue,
                    index + 1,
                    resultQueue,
                    localCache,
                    callback
            );

            return;
        }

        Log.i(
                TAG,
                "Translating item "
                        + index
                        + " / "
                        + queue.length()
                        + " | type="
                        + typeFinal
                        + " | Hindi="
                        + hindiText
        );

        /*
         * These references are intentionally final so they can
         * safely be used inside the asynchronous callback.
         */
        final JSONObject resultItemFinal =
                resultItem;

        final JSONArray resultQueueFinal =
                resultQueue;

        final Map<String, String> localCacheFinal =
                localCache;

        final JSONArray queueFinal =
                queue;

        final int nextIndex =
                index + 1;

        /*
         * Actual offline Hindi -> Santali translation.
         */
        pipeline.translate(
                hindiText,
                TARGET_LANGUAGE,
                (
                        translatedText,
                        confidence,
                        source,
                        latencyMs
                ) -> {

                    if (closed) {

                        callback.onError(
                                "Material translator has been closed."
                        );

                        return;
                    }

                    String santhali =
                            translatedText;

                    if (santhali == null) {
                        santhali = "";
                    }

                    santhali =
                            santhali.trim();

                    Log.i(
                            TAG,
                            "Translation completed"
                                    + " | type="
                                    + typeFinal
                                    + " | source="
                                    + source
                                    + " | confidence="
                                    + confidence
                                    + " | latency="
                                    + latencyMs
                                    + " ms"
                    );

                    /*
                     * An NMT error must never be presented as a
                     * successful Santali translation.
                     */
                    if (source != null
                            && source.toLowerCase()
                            .contains("error")) {

                        callback.onError(
                                "Offline translation failed for: "
                                        + hindiText
                                        + "\n\n"
                                        + santhali
                        );

                        return;
                    }

                    if ("[MODEL NOT READY]"
                            .equalsIgnoreCase(
                                    santhali
                            )) {

                        callback.onError(
                                "Offline translation model is not ready."
                        );

                        return;
                    }

                    if (santhali.isEmpty()) {

                        callback.onError(
                                "Offline translation returned empty output for: "
                                        + hindiText
                        );

                        return;
                    }

                    /*
                     * Store successful translation in the
                     * request-local cache.
                     */
                    localCacheFinal.put(
                            cacheKey,
                            santhali
                    );

                    /*
                     * Write the translation into text_sat.
                     */
                    setTranslation(
                            resultItemFinal,
                            santhali
                    );

                    resultQueueFinal.put(
                            resultItemFinal
                    );

                    /*
                     * Continue with the next queue item.
                     */
                    translateNext(
                            queueFinal,
                            nextIndex,
                            resultQueueFinal,
                            localCacheFinal,
                            callback
                    );
                }
        );
    }

    /**
     * Write the Santali translation field.
     */
    private void setTranslation(
            JSONObject item,
            String santhaliText
    ) {

        try {

            item.put(
                    "text_sat",
                    santhaliText == null
                            ? ""
                            : santhaliText
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Could not write text_sat",
                    e
            );
        }
    }

    /**
     * Copy a JSONObject while preserving all original fields.
     */
    private JSONObject cloneJSONObject(
            JSONObject original
    ) {

        JSONObject clone =
                new JSONObject();

        if (original == null) {
            return clone;
        }

        try {

            JSONArray names =
                    original.names();

            if (names == null) {
                return clone;
            }

            for (int i = 0;
                 i < names.length();
                 i++) {

                String name =
                        names.optString(i);

                if (name == null
                        || name.isEmpty()) {

                    continue;
                }

                clone.put(
                        name,
                        original.opt(name)
                );
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Could not clone translation item",
                    e
            );
        }

        return clone;
    }

    /**
     * Determine whether text contains only structural
     * characters and therefore does not require NMT.
     */
    private boolean isStructuralText(
            String text
    ) {

        if (text == null) {
            return true;
        }

        String clean =
                text.trim();

        if (clean.isEmpty()) {
            return true;
        }

        return clean.matches(
                "[0-9\\s,.;:/()\\[\\]{}+*=_\\-]+"
        );
    }

    /**
     * Release resources.
     */
    public void close() {

        if (closed) {
            return;
        }

        closed = true;

        try {

            if (pipeline != null) {
                pipeline.close();
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Error closing translation pipeline",
                    e
            );
        }
    }
}