package com.baileyanns.mileage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DatabaseHelper extends SQLiteOpenHelper {
    public static class Estate {
        public String id, name, client, address, status;
    }
    public static class Trip {
        public String id, tripType, estateId, estateName, purpose, date, startTime, endTime;
        public double miles, startLat, startLon, endLat, endLon;
        public long startedAt, endedAt;
        public int synced;
    }

    public DatabaseHelper(Context c) { super(c, "ba_mileage.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE trips (id TEXT PRIMARY KEY, trip_type TEXT NOT NULL, estate_id TEXT, estate_name TEXT, purpose TEXT, date TEXT, start_time TEXT, end_time TEXT, started_at INTEGER, ended_at INTEGER, miles REAL DEFAULT 0, start_lat REAL DEFAULT 0, start_lon REAL DEFAULT 0, end_lat REAL DEFAULT 0, end_lon REAL DEFAULT 0, synced INTEGER DEFAULT 0, active INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE estates (id TEXT PRIMARY KEY, name TEXT NOT NULL, client TEXT, address TEXT, status TEXT)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    public String createTrip(String type, String estateId, String estateName, String purpose, String date, String startTime, long startedAt) {
        String id = UUID.randomUUID().toString();
        ContentValues v = new ContentValues();
        v.put("id", id); v.put("trip_type", type); v.put("estate_id", estateId); v.put("estate_name", estateName);
        v.put("purpose", purpose); v.put("date", date); v.put("start_time", startTime); v.put("started_at", startedAt); v.put("active", 1);
        getWritableDatabase().insertOrThrow("trips", null, v); return id;
    }

    public void setStartLocation(String id, double lat, double lon) {
        ContentValues v = new ContentValues(); v.put("start_lat", lat); v.put("start_lon", lon);
        getWritableDatabase().update("trips", v, "id=?", new String[]{id});
    }
    public void updateDistance(String id, double miles, double lat, double lon) {
        ContentValues v = new ContentValues(); v.put("miles", miles); v.put("end_lat", lat); v.put("end_lon", lon);
        getWritableDatabase().update("trips", v, "id=?", new String[]{id});
    }
    public void finishTrip(String id, String endTime, long endedAt, double miles, double lat, double lon) {
        ContentValues v = new ContentValues(); v.put("end_time", endTime); v.put("ended_at", endedAt); v.put("miles", miles); v.put("end_lat", lat); v.put("end_lon", lon); v.put("active", 0); v.put("synced", 0);
        getWritableDatabase().update("trips", v, "id=?", new String[]{id});
    }
    public Trip activeTrip() {
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM trips WHERE active=1 ORDER BY started_at DESC LIMIT 1", null);
        try { return c.moveToFirst() ? from(c) : null; } finally { c.close(); }
    }
    public List<Trip> tripsForYear(int year) {
        List<Trip> out = new ArrayList<>(); Cursor c = getReadableDatabase().rawQuery("SELECT * FROM trips WHERE date LIKE ? AND active=0 ORDER BY date DESC, started_at DESC", new String[]{year + "%"});
        try { while(c.moveToNext()) out.add(from(c)); } finally { c.close(); } return out;
    }
    public List<Trip> recentTrips(int max) {
        List<Trip> out = new ArrayList<>(); Cursor c = getReadableDatabase().rawQuery("SELECT * FROM trips WHERE active=0 ORDER BY started_at DESC LIMIT " + Math.max(1,max), null);
        try { while(c.moveToNext()) out.add(from(c)); } finally { c.close(); } return out;
    }
    public List<Trip> unsyncedTrips() {
        List<Trip> out = new ArrayList<>(); Cursor c = getReadableDatabase().rawQuery("SELECT * FROM trips WHERE active=0 AND synced=0 ORDER BY started_at", null);
        try { while(c.moveToNext()) out.add(from(c)); } finally { c.close(); } return out;
    }
    public void markSynced(JSONArray ids) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try { for(int i=0;i<ids.length();i++){ ContentValues v=new ContentValues(); v.put("synced",1); db.update("trips",v,"id=?",new String[]{ids.optString(i)}); } db.setTransactionSuccessful(); } finally { db.endTransaction(); }
    }

    public void replaceEstates(JSONArray arr) throws Exception {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try { db.delete("estates",null,null); for(int i=0;i<arr.length();i++){ JSONObject o=arr.getJSONObject(i); ContentValues v=new ContentValues(); v.put("id",o.optString("id")); v.put("name",o.optString("estateName")); v.put("client",o.optString("clientName")); v.put("address",o.optString("address")); v.put("status",o.optString("status")); db.insertWithOnConflict("estates",null,v,SQLiteDatabase.CONFLICT_REPLACE);} db.setTransactionSuccessful(); } finally { db.endTransaction(); }
    }
    public List<Estate> estates() {
        List<Estate> out=new ArrayList<>(); Cursor c=getReadableDatabase().rawQuery("SELECT * FROM estates ORDER BY CASE status WHEN 'Active' THEN 0 ELSE 1 END, name",null);
        try { while(c.moveToNext()){ Estate e=new Estate(); e.id=c.getString(c.getColumnIndexOrThrow("id")); e.name=c.getString(c.getColumnIndexOrThrow("name")); e.client=c.getString(c.getColumnIndexOrThrow("client")); e.address=c.getString(c.getColumnIndexOrThrow("address")); e.status=c.getString(c.getColumnIndexOrThrow("status")); out.add(e);} } finally {c.close();} return out;
    }

    private Trip from(Cursor c) {
        Trip t=new Trip(); t.id=s(c,"id"); t.tripType=s(c,"trip_type"); t.estateId=s(c,"estate_id"); t.estateName=s(c,"estate_name"); t.purpose=s(c,"purpose"); t.date=s(c,"date"); t.startTime=s(c,"start_time"); t.endTime=s(c,"end_time");
        t.startedAt=l(c,"started_at"); t.endedAt=l(c,"ended_at"); t.miles=d(c,"miles"); t.startLat=d(c,"start_lat"); t.startLon=d(c,"start_lon"); t.endLat=d(c,"end_lat"); t.endLon=d(c,"end_lon"); t.synced=(int)l(c,"synced"); return t;
    }
    private String s(Cursor c,String n){int i=c.getColumnIndex(n);return i<0||c.isNull(i)?"":c.getString(i);} private long l(Cursor c,String n){int i=c.getColumnIndex(n);return i<0||c.isNull(i)?0:c.getLong(i);} private double d(Cursor c,String n){int i=c.getColumnIndex(n);return i<0||c.isNull(i)?0:c.getDouble(i);}
}
