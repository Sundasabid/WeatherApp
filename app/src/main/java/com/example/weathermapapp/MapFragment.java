package com.example.weathermapapp;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MapFragment extends Fragment {

    private static final String WEATHER_API_KEY = "4d9821fe653779c86286ba96dcb4e844";

    private static final int LOCATION_PERMISSION_CODE = 101;

    private MapView osmMap;
    private Marker  currentMarker;
    private MyLocationNewOverlay myLocationOverlay;
    private OkHttpClient httpClient;

    // Current weather state (for favouriting)
    private String  currentCity    = "";
    private double  currentTemp    = 0;
    private String  currentTempStr = "";
    private String  currentDesc    = "";
    private double  currentLat     = 0;
    private double  currentLon     = 0;

    // Card views
    private CardView      weatherCard;
    private LinearLayout  loadingLayout, weatherInfoLayout;
    private View          tapHintView;
    private TextView      tvCityName, tvCoordinates, tvTemperature,
                          tvFeelsLike, tvWeatherDesc;
    private ImageView     ivWeatherIcon, ivCloseCard, ivFavStar;
    private View          cardHumidity, cardWind, cardPressure, cardVisibility,
                          cardRain, cardClouds, cardSunrise, cardSunset;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());
        Configuration.getInstance().load(requireContext(),
                requireContext().getSharedPreferences("osmdroid", Context.MODE_PRIVATE));
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        httpClient = new OkHttpClient();
        initViews(view);
        setupMap();
        ivCloseCard.setOnClickListener(v -> hideWeatherCard());
        view.findViewById(R.id.btnMyLocation).setOnClickListener(v -> goToMyLocation());
        setupFavStar();
    }

    private void initViews(View v) {
        osmMap            = v.findViewById(R.id.osmMap);
        weatherCard       = v.findViewById(R.id.weatherCard);
        loadingLayout     = v.findViewById(R.id.loadingLayout);
        weatherInfoLayout = v.findViewById(R.id.weatherInfoLayout);
        tapHintView       = v.findViewById(R.id.tapHintView);
        tvCityName        = v.findViewById(R.id.tvCityName);
        tvCoordinates     = v.findViewById(R.id.tvCoordinates);
        tvTemperature     = v.findViewById(R.id.tvTemperature);
        tvFeelsLike       = v.findViewById(R.id.tvFeelsLike);
        tvWeatherDesc     = v.findViewById(R.id.tvWeatherDesc);
        ivWeatherIcon     = v.findViewById(R.id.ivWeatherIcon);
        ivCloseCard       = v.findViewById(R.id.ivCloseCard);
        ivFavStar         = v.findViewById(R.id.ivFavStar);
        cardHumidity      = v.findViewById(R.id.cardHumidity);
        cardWind          = v.findViewById(R.id.cardWind);
        cardPressure      = v.findViewById(R.id.cardPressure);
        cardVisibility    = v.findViewById(R.id.cardVisibility);
        cardRain          = v.findViewById(R.id.cardRain);
        cardClouds        = v.findViewById(R.id.cardClouds);
        cardSunrise       = v.findViewById(R.id.cardSunrise);
        cardSunset        = v.findViewById(R.id.cardSunset);
    }

    // ── MAP SETUP ────────────────────────────────────────────────
    private void setupMap() {
        osmMap.setTileSource(TileSourceFactory.MAPNIK);
        osmMap.setMultiTouchControls(true);
        osmMap.setBuiltInZoomControls(false);
        osmMap.getController().setZoom(5.5);
        osmMap.getController().setCenter(new GeoPoint(30.3753, 69.3451));

        setupLocationOverlay();

        MapEventsOverlay tapOverlay = new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                if (tapHintView != null) tapHintView.setVisibility(View.GONE);
                placeMarkerAndFetch(p);
                return true;
            }
            @Override public boolean longPressHelper(GeoPoint p) { return false; }
        });
        osmMap.getOverlays().add(0, tapOverlay);
    }

    private void setupLocationOverlay() {
        myLocationOverlay = new MyLocationNewOverlay(
                new GpsMyLocationProvider(requireContext()), osmMap);
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            myLocationOverlay.enableMyLocation();
        } else {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                 Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_CODE);
        }
        osmMap.getOverlays().add(myLocationOverlay);
    }

    // ── MY LOCATION BUTTON ───────────────────────────────────────
    private void goToMyLocation() {
        if (myLocationOverlay == null) return;
        // Check permission first
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_CODE);
            return;
        }
        myLocationOverlay.enableMyLocation();
        Location loc = myLocationOverlay.getLastFix();
        if (loc != null) {
            GeoPoint myPos = new GeoPoint(loc.getLatitude(), loc.getLongitude());
            osmMap.getController().animateTo(myPos);
            osmMap.getController().setZoom(13.0);
            // Auto fetch weather for current location
            if (tapHintView != null) tapHintView.setVisibility(View.GONE);
            placeMarkerAndFetch(myPos);
        } else {
            Toast.makeText(getContext(),
                    "Getting your location… try again in a moment", Toast.LENGTH_SHORT).show();
            myLocationOverlay.runOnFirstFix(() -> {
                if (getActivity() == null) return;
                Location fix = myLocationOverlay.getLastFix();
                if (fix == null) return;
                GeoPoint pos = new GeoPoint(fix.getLatitude(), fix.getLongitude());
                getActivity().runOnUiThread(() -> {
                    osmMap.getController().animateTo(pos);
                    osmMap.getController().setZoom(13.0);
                    placeMarkerAndFetch(pos);
                });
            });
        }
    }

    // ── FAVOURITE STAR ───────────────────────────────────────────
    private void setupFavStar() {
        ivFavStar.setOnClickListener(v -> {
            if (currentCity.isEmpty()) return;
            boolean isFav = FavouritesManager.isFav(requireContext(), currentCity);
            if (isFav) {
                FavouritesManager.remove(requireContext(), currentCity);
                ivFavStar.setImageResource(R.drawable.ic_star_outline);
                Toast.makeText(getContext(),
                        currentCity + " removed from favourites", Toast.LENGTH_SHORT).show();
            } else {
                FavouritesManager.save(requireContext(),
                        new FavouritesManager.FavItem(
                                currentCity, currentLat, currentLon,
                                currentTempStr, currentDesc));
                ivFavStar.setImageResource(R.drawable.ic_star);
                Toast.makeText(getContext(),
                        currentCity + " added to favourites ★", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateStarIcon() {
        if (!currentCity.isEmpty() && FavouritesManager.isFav(requireContext(), currentCity)) {
            ivFavStar.setImageResource(R.drawable.ic_star);
        } else {
            ivFavStar.setImageResource(R.drawable.ic_star_outline);
        }
    }

    // ── MARKER + FETCH ───────────────────────────────────────────
    private void placeMarkerAndFetch(GeoPoint point) {
        if (currentMarker != null) {
            osmMap.getOverlays().remove(currentMarker);
        }
        currentMarker = new Marker(osmMap);
        currentMarker.setPosition(point);
        currentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        Drawable markerIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_map_marker);
        if (markerIcon != null) currentMarker.setIcon(markerIcon);
        currentMarker.setInfoWindow(null); // No default popup

        osmMap.getOverlays().add(currentMarker);
        osmMap.invalidate();

        showLoadingCard();
        fetchWeather(point.getLatitude(), point.getLongitude());
    }

    // ── WEATHER API ──────────────────────────────────────────────
    private void fetchWeather(double lat, double lon) {
        String url = "https://api.openweathermap.org/data/2.5/weather"
                + "?lat=" + lat + "&lon=" + lon
                + "&appid=" + WEATHER_API_KEY + "&units=metric";

        httpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(),
                            "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    hideWeatherCard();
                });
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response)
                    throws IOException {
                if (getActivity() == null) return;
                if (response.isSuccessful() && response.body() != null) {
                    final String body = response.body().string();
                    getActivity().runOnUiThread(() -> parseAndDisplay(body, lat, lon));
                } else {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(),
                                "Weather error " + response.code(), Toast.LENGTH_SHORT).show();
                        hideWeatherCard();
                    });
                }
            }
        });
    }

    private void parseAndDisplay(String json, double lat, double lon) {
        try {
            JSONObject obj    = new JSONObject(json);
            JSONObject main   = obj.getJSONObject("main");
            JSONObject wind   = obj.getJSONObject("wind");
            JSONObject clouds = obj.getJSONObject("clouds");
            JSONObject sys    = obj.getJSONObject("sys");
            JSONObject wObj   = obj.getJSONArray("weather").getJSONObject(0);

            String city      = obj.optString("name", "");
            double temp      = main.getDouble("temp");
            double feelsLike = main.getDouble("feels_like");
            int    humidity  = main.getInt("humidity");
            int    pressure  = main.getInt("pressure");
            double windSpd   = wind.getDouble("speed");
            int    cloudPct  = clouds.getInt("all");
            int    visRaw    = obj.optInt("visibility", 0);
            String desc      = wObj.getString("description");
            String iconCode  = wObj.getString("icon");
            long   sunrise   = sys.getLong("sunrise");
            long   sunset    = sys.getLong("sunset");
            double rain1h    = obj.has("rain")
                    ? obj.getJSONObject("rain").optDouble("1h", 0.0) : 0.0;

            String cityDisplay = city.isEmpty() ? "Remote Location" : city;
            String sunriseStr  = formatTime(sunrise);
            String sunsetStr   = formatTime(sunset);
            String feelsStr    = String.format(Locale.US, "%.0f°C", feelsLike);
            String tempStr     = String.format(Locale.US, "%.0f°C", temp);
            int    iconRes     = resolveIcon(iconCode, desc);

            // Save state for favouriting
            currentCity    = cityDisplay;
            currentTemp    = temp;
            currentTempStr = tempStr;
            currentDesc    = capitalize(desc);
            currentLat     = lat;
            currentLon     = lon;

            // Set UI
            tvCityName.setText(cityDisplay);
            tvCoordinates.setText(String.format(Locale.US, "%.4f°N,  %.4f°E", lat, lon));
            tvTemperature.setText(String.format(Locale.US, "%.0f°C", temp));
            tvFeelsLike.setText("Feels like " + feelsStr);
            tvWeatherDesc.setText(capitalize(desc));
            ivWeatherIcon.setImageResource(iconRes);
            updateStarIcon();

            bindCard(cardHumidity,   R.drawable.ic_humidity,   "HUMIDITY",
                    humidity + "%",   "Relative humidity");
            bindCard(cardWind,       R.drawable.ic_wind,       "WIND SPEED",
                    String.format(Locale.US, "%.1f m/s", windSpd), "Surface wind");
            bindCard(cardPressure,   R.drawable.ic_pressure,   "PRESSURE",
                    pressure + " hPa", "Atmospheric");
            bindCard(cardVisibility, R.drawable.ic_visibility, "VISIBILITY",
                    (visRaw / 1000) + " km", "Ground level");
            bindCard(cardRain,       R.drawable.ic_rain,       "RAINFALL",
                    rain1h > 0 ? String.format(Locale.US, "%.1f mm/h", rain1h) : "0 mm/h",
                    "Last 1 hour");
            bindCard(cardClouds,     R.drawable.ic_clouds,     "CLOUD COVER",
                    cloudPct + "%",   "Sky coverage");
            bindCard(cardSunrise,    R.drawable.ic_sunrise,    "SUNRISE",
                    sunriseStr,       "Local time");
            bindCard(cardSunset,     R.drawable.ic_sunset,     "SUNSET",
                    sunsetStr,        "Local time");

            // Cache for HomeFragment
            SharedPreferences.Editor ed = requireContext()
                    .getSharedPreferences("weather_cache", Context.MODE_PRIVATE).edit();
            ed.putString("city",       cityDisplay);
            ed.putFloat("temp",        (float) temp);
            ed.putString("desc",       capitalize(desc));
            ed.putString("feels",      feelsStr);
            ed.putInt("humidity",      humidity);
            ed.putFloat("wind",        (float) windSpd);
            ed.putInt("visibility",    visRaw / 1000);
            ed.putString("sunrise",    sunriseStr);
            ed.putString("sunset",     sunsetStr);
            ed.putInt("icon_res",      iconRes);
            ed.putFloat("cache_lat",   (float) lat);
            ed.putFloat("cache_lon",   (float) lon);
            ed.apply();

            showWeatherInfo();

        } catch (JSONException e) {
            e.printStackTrace();
            if (getContext() != null)
                Toast.makeText(getContext(), "Parse error", Toast.LENGTH_SHORT).show();
            hideWeatherCard();
        }
    }

    private void bindCard(View card, int iconRes, String label, String value, String sub) {
        if (card == null) return;
        ((ImageView) card.findViewById(R.id.ivDetailIcon)).setImageResource(iconRes);
        ((TextView)  card.findViewById(R.id.tvDetailLabel)).setText(label);
        ((TextView)  card.findViewById(R.id.tvDetailValue)).setText(value);
        ((TextView)  card.findViewById(R.id.tvDetailSub)).setText(sub);
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

    // ── Animations ───────────────────────────────────────────────
    private void showLoadingCard() {
        loadingLayout.setVisibility(View.VISIBLE);
        weatherInfoLayout.setVisibility(View.GONE);
        weatherCard.setVisibility(View.VISIBLE);
        animateIn();
    }

    private void showWeatherInfo() {
        loadingLayout.setVisibility(View.GONE);
        weatherInfoLayout.setVisibility(View.VISIBLE);
    }

    private void hideWeatherCard() {
        animateOut(() -> {
            weatherCard.setVisibility(View.GONE);
            if (currentMarker != null) {
                osmMap.getOverlays().remove(currentMarker);
                osmMap.invalidate();
                currentMarker = null;
            }
        });
    }

    private void animateIn() {
        weatherCard.setTranslationY(700f);
        weatherCard.setAlpha(0f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(weatherCard, "translationY", 700f, 0f),
                ObjectAnimator.ofFloat(weatherCard, "alpha", 0f, 1f));
        set.setDuration(400);
        set.setInterpolator(new DecelerateInterpolator(2f));
        set.start();
    }

    private void animateOut(Runnable onEnd) {
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(weatherCard, "translationY", 0f, 700f),
                ObjectAnimator.ofFloat(weatherCard, "alpha", 1f, 0f));
        set.setDuration(280);
        set.setInterpolator(new DecelerateInterpolator());
        set.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) { onEnd.run(); }
        });
        set.start();
    }

    // ── OSMDroid lifecycle ───────────────────────────────────────
    @Override public void onResume()      { super.onResume(); osmMap.onResume(); }
    @Override public void onPause()       { super.onPause();  osmMap.onPause(); }
    @Override public void onDestroyView() { super.onDestroyView(); osmMap.onDetach(); }

    // ── Helpers ──────────────────────────────────────────────────
    private String formatTime(long unixSec) {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(new Date(unixSec * 1000L));
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
