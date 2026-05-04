package com.example.weathermapapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class HomeFragment extends Fragment {

    private static final String WEATHER_API_KEY = "4d9821fe653779c86286ba96dcb4e844";

    private OkHttpClient httpClient;

    // Hero views
    private TextView tvGreeting, tvHeroCity, tvHeroDate, tvHeroTemp,
                     tvHeroDesc, tvHeroFeels, tvMiniHumidity, tvMiniWind,
                     tvMiniVis, tvHomeSunrise, tvHomeSunset, tvNoFavourites;
    private ImageView ivHeroIcon;

    // Search views
    private EditText etSearch;
    private ImageView ivSearchClear;
    private LinearLayout searchResultsContainer;

    // Containers
    private LinearLayout forecastContainer, favouritesContainer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        httpClient = new OkHttpClient();

        bindViews(view);
        setGreetingAndDate();
        loadCachedWeather();
        setupSearch();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh favourites every time screen opens
        loadFavourites();
        // Refresh cached weather
        loadCachedWeather();
    }

    // ── Bind views ────────────────────────────────────────────────
    private void bindViews(View v) {
        tvGreeting           = v.findViewById(R.id.tvGreeting);
        tvHeroCity           = v.findViewById(R.id.tvHeroCity);
        tvHeroDate           = v.findViewById(R.id.tvHeroDate);
        tvHeroTemp           = v.findViewById(R.id.tvHeroTemp);
        tvHeroDesc           = v.findViewById(R.id.tvHeroDesc);
        tvHeroFeels          = v.findViewById(R.id.tvHeroFeels);
        tvMiniHumidity       = v.findViewById(R.id.tvMiniHumidity);
        tvMiniWind           = v.findViewById(R.id.tvMiniWind);
        tvMiniVis            = v.findViewById(R.id.tvMiniVis);
        tvHomeSunrise        = v.findViewById(R.id.tvHomeSunrise);
        tvHomeSunset         = v.findViewById(R.id.tvHomeSunset);
        tvNoFavourites       = v.findViewById(R.id.tvNoFavourites);
        ivHeroIcon           = v.findViewById(R.id.ivHeroIcon);
        etSearch             = v.findViewById(R.id.etSearch);
        ivSearchClear        = v.findViewById(R.id.ivSearchClear);
        searchResultsContainer = v.findViewById(R.id.searchResultsContainer);
        forecastContainer    = v.findViewById(R.id.forecastContainer);
        favouritesContainer  = v.findViewById(R.id.favouritesContainer);
    }

    // ── Greeting + Date ───────────────────────────────────────────
    private void setGreetingAndDate() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greet = hour < 12 ? getString(R.string.good_morning)
                     : hour < 17 ? getString(R.string.good_afternoon)
                     : getString(R.string.good_evening);
        tvGreeting.setText(greet + ",");
        tvHeroDate.setText(new SimpleDateFormat("EEE, d MMM", Locale.getDefault())
                .format(new Date()));
    }

    // ── Load cached weather (set by MapFragment) ──────────────────
    private void loadCachedWeather() {
        SharedPreferences p = prefs();
        String city = p.getString("city", null);
        if (city == null) return;

        tvHeroCity.setText(city);
        tvHeroTemp.setText(String.format(Locale.US, "%.0f°", p.getFloat("temp", 0f)));
        tvHeroDesc.setText(p.getString("desc", ""));
        tvHeroFeels.setText("Feels like " + p.getString("feels", "--"));
        tvMiniHumidity.setText(p.getInt("humidity", 0) + "%");
        tvMiniWind.setText(String.format(Locale.US, "%.1f m/s", p.getFloat("wind", 0f)));
        tvMiniVis.setText(p.getInt("visibility", 0) + " km");
        tvHomeSunrise.setText(p.getString("sunrise", "--:--"));
        tvHomeSunset.setText(p.getString("sunset", "--:--"));
        ivHeroIcon.setImageResource(p.getInt("icon_res", R.drawable.ic_sunny));

        // Load forecast from cache
        loadForecastFromCache();
    }

    // ── SEARCH ────────────────────────────────────────────────────
    private void setupSearch() {
        // Show/hide clear button as user types
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                ivSearchClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                if (s.length() == 0) hideSearchResults();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Search on keyboard "Search" button press
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String query = etSearch.getText().toString().trim();
                if (!query.isEmpty()) searchCity(query);
                hideKeyboard();
                return true;
            }
            return false;
        });

        // Clear button
        ivSearchClear.setOnClickListener(v -> {
            etSearch.setText("");
            hideSearchResults();
            hideKeyboard();
        });
    }

    private void searchCity(String cityName) {
        // OpenWeatherMap city search — same API, just use city name instead of lat/lon
        String url = "https://api.openweathermap.org/data/2.5/weather"
                + "?q=" + cityName.replace(" ", "+")
                + "&appid=" + WEATHER_API_KEY
                + "&units=metric";

        httpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "Network error", Toast.LENGTH_SHORT).show());
            }

            @Override public void onResponse(@NonNull Call call, @NonNull Response response)
                    throws IOException {
                if (getActivity() == null) return;
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    getActivity().runOnUiThread(() -> showSearchResult(body));
                } else {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(),
                                    "City not found", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void showSearchResult(String json) {
        try {
            JSONObject obj  = new JSONObject(json);
            JSONObject main = obj.getJSONObject("main");
            JSONObject wObj = obj.getJSONArray("weather").getJSONObject(0);

            String city  = obj.optString("name", "Unknown");
            double temp  = main.getDouble("temp");
            String desc  = wObj.getString("description");
            double lat   = obj.getJSONObject("coord").getDouble("lat");
            double lon   = obj.getJSONObject("coord").getDouble("lon");
            int iconRes  = resolveIcon(wObj.getString("icon"), desc);
            String tempStr = String.format(Locale.US, "%.0f°C", temp);

            searchResultsContainer.removeAllViews();
            searchResultsContainer.setVisibility(View.VISIBLE);

            // Result row
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setBackground(getResources().getDrawable(R.drawable.search_result_bg, null));
            row.setPadding(dp(14), dp(12), dp(14), dp(12));

            ImageView icon = new ImageView(requireContext());
            icon.setImageResource(iconRes);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(40), dp(40));
            icon.setLayoutParams(iconLp);
            row.addView(icon);

            LinearLayout textBlock = new LinearLayout(requireContext());
            textBlock.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tbLp.leftMargin = dp(12);
            textBlock.setLayoutParams(tbLp);

            TextView tvCity = new TextView(requireContext());
            tvCity.setText(city);
            tvCity.setTextColor(getResources().getColor(R.color.text_primary, null));
            tvCity.setTextSize(15f);
            tvCity.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView tvDesc = new TextView(requireContext());
            tvDesc.setText(capitalize(desc));
            tvDesc.setTextColor(getResources().getColor(R.color.text_secondary, null));
            tvDesc.setTextSize(12f);

            textBlock.addView(tvCity);
            textBlock.addView(tvDesc);
            row.addView(textBlock);

            TextView tvTemp = new TextView(requireContext());
            tvTemp.setText(tempStr);
            tvTemp.setTextColor(getResources().getColor(R.color.accent_blue, null));
            tvTemp.setTextSize(18f);
            tvTemp.setTypeface(null, android.graphics.Typeface.BOLD);
            row.addView(tvTemp);

            // Tap → update hero + save cache + fetch forecast
            final String finalCity = city;
            final double finalLat  = lat;
            final double finalLon  = lon;
            row.setOnClickListener(v -> {
                hideSearchResults();
                etSearch.setText("");
                hideKeyboard();
                // Update hero from search
                updateHeroFromSearch(finalCity, temp, tempStr, capitalize(desc),
                        iconRes, finalLat, finalLon);
            });

            searchResultsContainer.addView(row);

        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Parse error", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateHeroFromSearch(String city, double temp, String tempStr,
                                       String desc, int iconRes,
                                       double lat, double lon) {
        tvHeroCity.setText(city);
        tvHeroTemp.setText(String.format(Locale.US, "%.0f°", temp));
        tvHeroDesc.setText(desc);
        ivHeroIcon.setImageResource(iconRes);

        // Save to cache
        prefs().edit()
                .putString("city",     city)
                .putFloat("temp",      (float) temp)
                .putString("desc",     desc)
                .putString("feels",    tempStr)
                .putInt("icon_res",    iconRes)
                .apply();

        // Fetch full weather for this city (for feels, humidity etc)
        fetchFullWeather(lat, lon);
        // Fetch 7-day forecast
        fetchForecast(lat, lon);

        Toast.makeText(getContext(), "Showing weather for " + city, Toast.LENGTH_SHORT).show();
    }

    // ── FULL WEATHER (after search tap) ──────────────────────────
    private void fetchFullWeather(double lat, double lon) {
        String url = "https://api.openweathermap.org/data/2.5/weather"
                + "?lat=" + lat + "&lon=" + lon
                + "&appid=" + WEATHER_API_KEY + "&units=metric";

        httpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}

            @Override public void onResponse(@NonNull Call call, @NonNull Response response)
                    throws IOException {
                if (getActivity() == null || !response.isSuccessful()) return;
                String body = response.body().string();
                getActivity().runOnUiThread(() -> applyFullWeather(body));
            }
        });
    }

    private void applyFullWeather(String json) {
        try {
            JSONObject obj    = new JSONObject(json);
            JSONObject main   = obj.getJSONObject("main");
            JSONObject wind   = obj.getJSONObject("wind");
            JSONObject sys    = obj.getJSONObject("sys");

            double feelsLike = main.getDouble("feels_like");
            int    humidity  = main.getInt("humidity");
            double windSpd   = wind.getDouble("speed");
            int    visRaw    = obj.optInt("visibility", 0);
            String feels     = String.format(Locale.US, "%.0f°C", feelsLike);

            tvHeroFeels.setText("Feels like " + feels);
            tvMiniHumidity.setText(humidity + "%");
            tvMiniWind.setText(String.format(Locale.US, "%.1f m/s", windSpd));
            tvMiniVis.setText((visRaw / 1000) + " km");
            tvHomeSunrise.setText(formatTime(sys.getLong("sunrise")));
            tvHomeSunset.setText(formatTime(sys.getLong("sunset")));

        } catch (JSONException ignored) {}
    }

    // ── 7-DAY FORECAST ────────────────────────────────────────────
    private void fetchForecast(double lat, double lon) {
        // OpenWeatherMap free tier: forecast/daily not available.
        // Using /forecast which gives 5-day/3-hour → we take 1 per day.
        String url = "https://api.openweathermap.org/data/2.5/forecast"
                + "?lat=" + lat + "&lon=" + lon
                + "&appid=" + WEATHER_API_KEY
                + "&units=metric&cnt=40";

        httpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}

            @Override public void onResponse(@NonNull Call call, @NonNull Response response)
                    throws IOException {
                if (getActivity() == null || !response.isSuccessful() || response.body() == null)
                    return;
                String body = response.body().string();
                // Save to cache
                prefs().edit().putString("forecast_json", body).apply();
                getActivity().runOnUiThread(() -> buildForecastUI(body));
            }
        });
    }

    private void loadForecastFromCache() {
        String cached = prefs().getString("forecast_json", null);
        if (cached != null) buildForecastUI(cached);
    }

    private void buildForecastUI(String json) {
        forecastContainer.removeAllViews();
        try {
            JSONObject root = new JSONObject(json);
            JSONArray  list = root.getJSONArray("list");

            // Take one entry per day (noon-ish) — max 7
            String lastDay = "";
            int count = 0;

            for (int i = 0; i < list.length() && count < 7; i++) {
                JSONObject entry = list.getJSONObject(i);
                String dtTxt = entry.getString("dt_txt"); // "2024-05-03 12:00:00"
                String day   = dtTxt.substring(0, 10);    // "2024-05-03"

                if (day.equals(lastDay)) continue; // already have this day
                lastDay = day;
                count++;

                double temp = entry.getJSONObject("main").getDouble("temp");
                String desc = entry.getJSONArray("weather").getJSONObject(0).getString("description");
                String icon = entry.getJSONArray("weather").getJSONObject(0).getString("icon");
                String dayLabel = i == 0 ? "Today"
                        : new SimpleDateFormat("EEE", Locale.getDefault())
                                .format(new Date(entry.getLong("dt") * 1000L));

                forecastContainer.addView(makeForecastItem(dayLabel, temp, desc, icon));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private View makeForecastItem(String day, double temp, String desc, String iconCode) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(android.view.Gravity.CENTER);
        card.setBackground(getResources().getDrawable(R.drawable.forecast_item_bg, null));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(78), dp(110));
        lp.rightMargin = dp(8);
        card.setLayoutParams(lp);
        card.setPadding(dp(8), dp(10), dp(8), dp(10));

        TextView tvDay = new TextView(requireContext());
        tvDay.setText(day);
        tvDay.setTextColor(getResources().getColor(R.color.text_secondary, null));
        tvDay.setTextSize(11f);
        tvDay.setGravity(android.view.Gravity.CENTER);

        ImageView iv = new ImageView(requireContext());
        iv.setImageResource(resolveIcon(iconCode, desc));
        LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(dp(32), dp(32));
        ivLp.topMargin = dp(6);
        ivLp.bottomMargin = dp(4);
        iv.setLayoutParams(ivLp);

        TextView tvTemp = new TextView(requireContext());
        tvTemp.setText(String.format(Locale.US, "%.0f°", temp));
        tvTemp.setTextColor(getResources().getColor(R.color.text_primary, null));
        tvTemp.setTextSize(15f);
        tvTemp.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTemp.setGravity(android.view.Gravity.CENTER);

        card.addView(tvDay);
        card.addView(iv);
        card.addView(tvTemp);
        return card;
    }

    // ── FAVOURITES ────────────────────────────────────────────────
    private void loadFavourites() {
        favouritesContainer.removeAllViews();
        List<FavouritesManager.FavItem> list =
                FavouritesManager.load(requireContext());

        if (list.isEmpty()) {
            tvNoFavourites.setVisibility(View.VISIBLE);
            return;
        }
        tvNoFavourites.setVisibility(View.GONE);

        for (FavouritesManager.FavItem fav : list) {
            favouritesContainer.addView(makeFavItem(fav));
        }
    }

    private View makeFavItem(FavouritesManager.FavItem fav) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setBackground(getResources().getDrawable(R.drawable.fav_item_bg, null));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));

        // Location icon
        ImageView locIcon = new ImageView(requireContext());
        locIcon.setImageResource(R.drawable.ic_location);
        locIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(18), dp(18)));

        // City name + desc
        LinearLayout textBlock = new LinearLayout(requireContext());
        textBlock.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tbLp.leftMargin = dp(10);
        textBlock.setLayoutParams(tbLp);

        TextView tvCity = new TextView(requireContext());
        tvCity.setText(fav.city);
        tvCity.setTextColor(getResources().getColor(R.color.text_primary, null));
        tvCity.setTextSize(14f);
        tvCity.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvDesc = new TextView(requireContext());
        tvDesc.setText(fav.lastDesc);
        tvDesc.setTextColor(getResources().getColor(R.color.text_secondary, null));
        tvDesc.setTextSize(11f);

        textBlock.addView(tvCity);
        textBlock.addView(tvDesc);

        // Temp
        TextView tvTemp = new TextView(requireContext());
        tvTemp.setText(fav.lastTemp);
        tvTemp.setTextColor(getResources().getColor(R.color.accent_blue, null));
        tvTemp.setTextSize(16f);
        tvTemp.setTypeface(null, android.graphics.Typeface.BOLD);

        // Remove star
        ImageView ivStar = new ImageView(requireContext());
        ivStar.setImageResource(R.drawable.ic_star);
        LinearLayout.LayoutParams starLp = new LinearLayout.LayoutParams(dp(20), dp(20));
        starLp.leftMargin = dp(10);
        ivStar.setLayoutParams(starLp);
        ivStar.setOnClickListener(v -> {
            FavouritesManager.remove(requireContext(), fav.city);
            loadFavourites();
            Toast.makeText(getContext(), fav.city + " removed", Toast.LENGTH_SHORT).show();
        });

        row.addView(locIcon);
        row.addView(textBlock);
        row.addView(tvTemp);
        row.addView(ivStar);

        // Tap → load this city weather
        row.setOnClickListener(v ->
                updateHeroFromSearch(fav.city, 0, fav.lastTemp, fav.lastDesc,
                        R.drawable.ic_sunny, fav.lat, fav.lon));

        return row;
    }

    // ── Helpers ───────────────────────────────────────────────────
    private void hideSearchResults() {
        searchResultsContainer.setVisibility(View.GONE);
        searchResultsContainer.removeAllViews();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && getView() != null)
            imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
    }

    private SharedPreferences prefs() {
        return requireContext().getSharedPreferences("weather_cache", Context.MODE_PRIVATE);
    }

    private int resolveIcon(String iconCode, String desc) {
        String d = desc.toLowerCase(Locale.US);
        if      (d.contains("thunder"))                                           return R.drawable.ic_thunderstorm;
        else if (d.contains("rain") || d.contains("drizzle"))                    return R.drawable.ic_rainy;
        else if (d.contains("snow"))                                              return R.drawable.ic_snowy;
        else if (d.contains("fog") || d.contains("mist") || d.contains("haze")) return R.drawable.ic_foggy;
        else if (d.contains("cloud"))                                             return R.drawable.ic_cloudy;
        else if (iconCode.contains("d"))                                          return R.drawable.ic_sunny;
        else                                                                      return R.drawable.ic_night;
    }

    private String formatTime(long unixSec) {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(new Date(unixSec * 1000L));
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
