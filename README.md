# BhashaSetu — Prototype

Offline-first Hindi ⇄ Ho teaching assistant for tribal-area primary school
teachers in Jharkhand. This is a **working Android Studio prototype**: it
compiles, installs, and runs the full Hindi→Ho pipeline end-to-end using
on-device placeholders in place of the production AI models (see below).

## Stack

| Layer | Choice | Why |
|---|---|---|
| Language | Java | matches original spec, widest low-end-device compatibility |
| Min/Target SDK | 21 / 33 | runs on the 2GB-RAM / Android 9 tablets this is designed for |
| Persistence | Room (SQLite) | offline translation cache + correction log |
| Translation (NMT) | Dictionary + fuzzy match, real-model slot documented | see "Placeholder Model Strategy" |
| Speech-to-text (ASR) | Android `SpeechRecognizer` | on-device on most modern builds; real-Whisper slot documented |
| Text-to-speech (TTS) | Android `TextToSpeech` (Hindi voice) | no Ho voice exists on-device; real-FastSpeech2 slot documented |
| PDF export | Android's built-in `PdfDocument` | zero extra dependency for the prototype; iText7 upgrade path noted |

## Project layout

```
BhashaSetu/
├── app/src/main/java/com/bhashasetu/app/
│   ├── ui/            SplashActivity, HomeActivity, TextToSpeechActivity,
│   │                   SpeechToTextActivity, VoiceToVoiceActivity, LessonPlanActivity
│   ├── engine/         NMTEngine, ASREngine, TTSEngine, TranslationPipeline, CorrectionLogger
│   ├── curriculum/     LessonPlanGenerator (NIPUN-outcome templates)
│   └── database/       Room: AppDatabase, TranslationCacheEntity/Dao, CorrectionLogEntity/Dao
├── app/src/main/assets/seed_data.json   20 seed Hindi→Ho pairs + 3 NIPUN outcomes
└── app/src/main/res/                    layouts, colors, theme (forest-green Material)
```

## How to run it

1. Open the `BhashaSetu/` folder in Android Studio (Hedgehog or newer) — it
   will sync Gradle automatically (needs internet for the first sync, to
   pull down AppCompat/Material/Room, etc. — no calls to the internet at
   runtime).
2. Run on an emulator (API 28+, e.g. **Pixel Tablet API 33**) or a physical
   Android 9+ device.
3. Grant the microphone permission when prompted (needed for the Speech-to-Text
   and Live Classroom screens).
4. From Home:
   - **Speech to Text** — tap the mic, say a Hindi classroom phrase (e.g.
     "बैठ जाओ" or "शाबाश"), see + hear the Ho translation.
   - **Text to Speech** — type or tap a quick phrase, translate & speak, try
     Loop / Slow mode.
   - **Live Classroom Mode** — hold the mic button, speak, watch the pipeline
     status and rolling average latency update per turn.
   - **Lesson Plan Generator** — pick a NIPUN code (FLN-G1-L1 / FLN-G1-N1 /
     FLN-G2-L1), generate, then Export PDF (saved to
     `Documents/BhashaSetu/` on the device).

Every translation writes through `TranslationPipeline` into the Room
`translation_cache` table, so repeat phrases become instant "cache HIT"s —
you can watch the Home screen's daily counter and the Recent Translations
list on the Speech-to-Text screen grow as you use it.

## Placeholder model strategy (why, and how to upgrade)

Real IndicTrans2 / NLLB-200 / Whisper / FastSpeech2 `.tflite` models need a
conversion + quantization pipeline and multi-hundred-MB downloads that
can't be produced or fetched in this build environment. Rather than fake
that with mock UI, every engine class is written so a real model drops in
with **one localized change**:

- **`NMTEngine.java`** — has a commented `useRealModel` slot at the top of
  `initialize()`. Drop `indictrans2_hi_ho.tflite` into `assets/models/`,
  uncomment the TFLite `Interpreter` loading block, implement
  `runRealModelInference()`, flip the flag.
- **`ASREngine.java`** — currently wraps Android's `SpeechRecognizer`.
  Swap in `whisper_small_int8.tflite` + a mel-spectrogram preprocessor and
  TFLite `Interpreter` inside `startListening()`.
- **`TTSEngine.java`** — currently wraps Android `TextToSpeech` with the
  Hindi voice as an audible stand-in for Ho. Swap in a Ho-trained
  FastSpeech2/VITS `.tflite` + `AudioTrack` playback inside `speak()`.

The Room schema (`TranslationCacheEntity`, `CorrectionLogEntity`) and the
`TranslationPipeline` cache-first flow are already production-shaped — they
don't need to change when you upgrade the engines above.

## What I'd extend first

1. **Worksheets & Flashcards screens** — the Home screen has both cards
   wired but greyed out. `LessonPlanGenerator` already shows the pattern
   (load JSON template → NMT → render); a `WorksheetGenerator` and
   `FlashcardEngine` following the same shape are the fastest next win,
   since the PDF and Room plumbing already exist.
2. **Swap Android `PdfDocument` for iText7** once you need real multi-page,
   two-column bilingual layout with QR-linked audio — the current export is
   single-page, no table layout.
3. **Real ASR/NMT/TTS models** per the slots above — this is the biggest
   lift, and where the <3s latency budget and the "load → infer → unload"
   RAM discipline from the original spec actually get tested.
4. **`DeltaSyncWorker` (WorkManager)** — not implemented in this prototype.
   `CorrectionLogEntity.uploaded` and the `translation_cache` table are
   already shaped to support it: a WorkManager job on WiFi that pushes
   unuploaded corrections and pulls new curriculum JSON.
5. **Settings screen** — model versions, confidence threshold slider, cache
   size, TTS rate/volume. Straightforward `SharedPreferences`-backed screen;
   no new architecture needed.
6. **Santhali / Mundari** — the language Spinner and `targetLang` plumbing
   already support more than Ho; you just need seed dictionaries (or real
   models) per language.

## Known prototype limitations

- NMT is a 20-pair seed dictionary + fuzzy match, not a real translation
  model — anything outside the seed set returns
  `[Ho translation pending: ...]`.
- ASR uses Android's built-in recognizer, not offline Whisper — it may
  briefly need connectivity on first use on some OEM builds.
- TTS speaks with a Hindi voice, not a native Ho voice — there's no
  on-device Ho TTS voice to fall back to.
- Ho translations in the seed data are illustrative for the demo pipeline,
  not linguist-validated — production content needs review by Ho language
  experts (e.g. TRI Jharkhand / CIIL).
- PDF export is single-page/plain-text, not the bilingual two-column layout
  from the full spec.
- Worksheets, Flashcards, Settings, and background delta-sync are not
  implemented yet (see "What I'd extend first").
