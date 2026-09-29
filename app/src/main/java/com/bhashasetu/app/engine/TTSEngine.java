package com.bhashasetu.app.engine;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.Locale;
import java.util.UUID;

public class TTSEngine {

    private static final String TAG = "TTSEngine";

    private TextToSpeech tts;

    private boolean ready = false;
    private boolean santaliSupported = false;

    private float rate = 1.0f;

    public interface OnReady {
        void onReady(boolean success);
    }

    public void initialize(
            Context context,
            OnReady callback
    ) {

        tts =
                new TextToSpeech(
                        context.getApplicationContext(),
                        status -> {

                            if (status != TextToSpeech.SUCCESS) {

                                ready = false;
                                santaliSupported = false;

                                Log.e(
                                        TAG,
                                        "TTS initialization failed"
                                );

                                if (callback != null) {
                                    callback.onReady(false);
                                }

                                return;
                            }

                            /*
                             * Santali language.
                             *
                             * ISO 639-2 language code:
                             * sat
                             */
                            Locale santali =
                                    new Locale(
                                            "sat",
                                            "IN"
                                    );

                            int availability =
                                    tts.isLanguageAvailable(
                                            santali
                                    );

                            Log.i(
                                    TAG,
                                    "Santali TTS availability = "
                                            + availability
                            );

                            if (
                                    availability ==
                                            TextToSpeech.LANG_AVAILABLE
                                            ||
                                            availability ==
                                                    TextToSpeech.LANG_COUNTRY_AVAILABLE
                                            ||
                                            availability ==
                                                    TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
                            ) {

                                int result =
                                        tts.setLanguage(
                                                santali
                                        );

                                santaliSupported =
                                        result ==
                                                TextToSpeech.LANG_AVAILABLE
                                                ||
                                                result ==
                                                        TextToSpeech.LANG_COUNTRY_AVAILABLE
                                                ||
                                                result ==
                                                        TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE;

                                ready =
                                        santaliSupported;

                                Log.i(
                                        TAG,
                                        "Santali TTS supported = "
                                                + santaliSupported
                                );

                            } else {

                                ready = false;
                                santaliSupported = false;

                                Log.w(
                                        TAG,
                                        "Santali TTS is NOT installed"
                                );
                            }

                            tts.setSpeechRate(rate);

                            if (callback != null) {
                                callback.onReady(
                                        santaliSupported
                                );
                            }
                        }
                );
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isSantaliSupported() {
        return santaliSupported;
    }

    public void setSlowMode(
            boolean slow
    ) {

        rate =
                slow
                        ? 0.75f
                        : 1.0f;

        if (tts != null) {
            tts.setSpeechRate(rate);
        }
    }

    public void speak(
            String text
    ) {

        if (!ready ||
                !santaliSupported ||
                tts == null) {

            Log.w(
                    TAG,
                    "Santali TTS unavailable"
            );

            return;
        }

        if (text == null ||
                text.trim().isEmpty()) {

            return;
        }

        String utteranceId =
                UUID.randomUUID()
                        .toString();

        tts.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                utteranceId
        );
    }

    public void stop() {

        if (tts != null) {
            tts.stop();
        }
    }

    public void release() {

        if (tts != null) {

            tts.stop();
            tts.shutdown();
            tts = null;
        }

        ready = false;
        santaliSupported = false;
    }
}