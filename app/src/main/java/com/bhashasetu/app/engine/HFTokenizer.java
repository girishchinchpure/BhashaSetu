package com.bhashasetu.app.engine;

import android.util.Log;

public class HFTokenizer {

    static {
        System.loadLibrary("hfta");
    }

    private long tokenizerPtr;

    public HFTokenizer(byte[] tokenizerBytes) {
        Log.e("HFTokenizer", "STEP 1: Creating tokenizer");

        tokenizerPtr = createTokenizer(tokenizerBytes);

        Log.e("HFTokenizer", "STEP 2: Tokenizer pointer = " + tokenizerPtr);
    }

    private native long createTokenizer(byte[] tokenizerBytes);

    private native int[] encode(long tokenizerPtr, String text);

    private native String decode(long tokenizerPtr, int[] ids);

    private native void deleteTokenizer(long tokenizerPtr);

    public int[] encode(String text) {
        return encode(tokenizerPtr, text);
    }

    public String decode(int[] ids) {
        return decode(tokenizerPtr, ids);
    }

    public void testTokenizer() {

        String testText = "hin_Deva sat_Olck मैं एक लड़का हूँ";

        Log.e(
                "HFTokenizer",
                "STEP 3: Encoding test text: " + testText
        );

        int[] ids = encode(testText);

        if (ids == null) {
            Log.e(
                    "HFTokenizer",
                    "STEP 4: RESULT IS NULL"
            );
            return;
        }

        StringBuilder result = new StringBuilder();

        for (int id : ids) {

            if (result.length() > 0) {
                result.append(", ");
            }

            result.append(id);
        }

        Log.e(
                "HFTokenizer",
                "STEP 4: TOKEN IDS = [" + result + "]"
        );
    }

    public void close() {

        if (tokenizerPtr != 0) {

            deleteTokenizer(tokenizerPtr);

            tokenizerPtr = 0;
        }
    }
}