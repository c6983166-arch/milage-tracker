package com.baileyanns.mileage;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private static final String NAME = "ba_mileage_prefs";
    public static SharedPreferences get(Context c) { return c.getSharedPreferences(NAME, Context.MODE_PRIVATE); }
    public static String serverUrl(Context c) { return get(c).getString("server_url", ""); }
    public static String pairingCode(Context c) { return get(c).getString("pairing_code", ""); }
    public static boolean carReminder(Context c) { return get(c).getBoolean("car_reminder", false); }
    public static String carAddress(Context c) { return get(c).getString("car_address", ""); }
    public static String carName(Context c) { return get(c).getString("car_name", ""); }
    private Prefs() {}
}
