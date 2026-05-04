package com.example.weathermapapp;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Favourites — SharedPreferences mein JSON array save karta hai.
 * Koi database nahi, koi backend nahi — simple aur clean.
 */
public class FavouritesManager {

    private static final String PREFS   = "favourites_prefs";
    private static final String KEY_FAV = "fav_list";
    private static final int    MAX_FAV = 10;

    // ── Model ────────────────────────────────────────────────────────
    public static class FavItem {
        public String city;
        public double lat, lon;
        public String lastTemp;   // e.g. "34°C"
        public String lastDesc;   // e.g. "Clear sky"

        public FavItem(String city, double lat, double lon,
                       String lastTemp, String lastDesc) {
            this.city     = city;
            this.lat      = lat;
            this.lon      = lon;
            this.lastTemp = lastTemp;
            this.lastDesc = lastDesc;
        }
    }

    // ── Load all ────────────────────────────────────────────────────
    public static List<FavItem> load(Context ctx) {
        List<FavItem> list = new ArrayList<>();
        String raw = prefs(ctx).getString(KEY_FAV, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new FavItem(
                        o.getString("city"),
                        o.getDouble("lat"),
                        o.getDouble("lon"),
                        o.optString("temp", "--"),
                        o.optString("desc", "")));
            }
        } catch (JSONException ignored) {}
        return list;
    }

    // ── Save one ────────────────────────────────────────────────────
    public static boolean save(Context ctx, FavItem item) {
        List<FavItem> list = load(ctx);
        // Already exists? update
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).city.equalsIgnoreCase(item.city)) {
                list.set(i, item);
                persist(ctx, list);
                return true;
            }
        }
        if (list.size() >= MAX_FAV) list.remove(0); // oldest hata do
        list.add(item);
        persist(ctx, list);
        return true;
    }

    // ── Remove ──────────────────────────────────────────────────────
    public static void remove(Context ctx, String city) {
        List<FavItem> list = load(ctx);
        list.removeIf(f -> f.city.equalsIgnoreCase(city));
        persist(ctx, list);
    }

    // ── Is favourite? ────────────────────────────────────────────────
    public static boolean isFav(Context ctx, String city) {
        for (FavItem f : load(ctx))
            if (f.city.equalsIgnoreCase(city)) return true;
        return false;
    }

    // ── Helpers ──────────────────────────────────────────────────────
    private static void persist(Context ctx, List<FavItem> list) {
        JSONArray arr = new JSONArray();
        try {
            for (FavItem f : list) {
                JSONObject o = new JSONObject();
                o.put("city", f.city);
                o.put("lat",  f.lat);
                o.put("lon",  f.lon);
                o.put("temp", f.lastTemp);
                o.put("desc", f.lastDesc);
                arr.put(o);
            }
        } catch (JSONException ignored) {}
        prefs(ctx).edit().putString(KEY_FAV, arr.toString()).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
