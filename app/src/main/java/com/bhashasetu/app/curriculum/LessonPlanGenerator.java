package com.bhashasetu.app.curriculum;

import android.content.Context;

import com.bhashasetu.app.engine.NMTEngine;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class LessonPlanGenerator {

    private final NMTEngine nmtEngine;

    public LessonPlanGenerator(NMTEngine nmtEngine) {
        this.nmtEngine = nmtEngine;
    }

    public static class Activity {
        public String hindi;
        public String ho;
        public Activity(String hindi, String ho) { this.hindi = hindi; this.ho = ho; }
    }

    public static class LessonPlan {
        public String code;
        public String titleHi;
        public int grade;
        public String subject;
        public String objectiveHi;
        public String objectiveHo;
        public List<Activity> activities = new ArrayList<>();
    }

    /** Loads NIPUN outcome codes from seed_data.json for the Spinner. */
    public List<String> listOutcomeCodes(Context context) {
        List<String> codes = new ArrayList<>();
        JSONObject root = readSeed(context);
        if (root == null) return codes;
        try {
            JSONArray outcomes = root.getJSONArray("nipun_outcomes");
            for (int i = 0; i < outcomes.length(); i++) {
                codes.add(outcomes.getJSONObject(i).getString("code"));
            }
        } catch (Exception ignored) {}
        return codes;
    }

    public LessonPlan generate(Context context, String nipunCode) {
        JSONObject root = readSeed(context);
        LessonPlan plan = new LessonPlan();
        if (root == null) return plan;

        try {
            JSONArray outcomes = root.getJSONArray("nipun_outcomes");
            for (int i = 0; i < outcomes.length(); i++) {
                JSONObject o = outcomes.getJSONObject(i);
                if (!o.getString("code").equals(nipunCode)) continue;

                plan.code = nipunCode;
                plan.titleHi = o.getString("title_hi");
                plan.grade = o.getInt("grade");
                plan.subject = o.getString("subject");
                plan.objectiveHi = o.getString("objective_hi");
                plan.objectiveHo = nmtEngine.translate(plan.objectiveHi).hoText;

                JSONArray activities = o.getJSONArray("activities_hi");
                for (int j = 0; j < activities.length(); j++) {
                    String hi = activities.getString(j);
                    String ho = nmtEngine.translate(hi).hoText;
                    plan.activities.add(new Activity(hi, ho));
                }
                break;
            }
        } catch (Exception ignored) {}

        return plan;
    }

    private JSONObject readSeed(Context context) {
        try (InputStream is = context.getAssets().open("seed_data.json")) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return new JSONObject(sb.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
