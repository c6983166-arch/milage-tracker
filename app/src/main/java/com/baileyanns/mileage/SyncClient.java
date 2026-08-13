package com.baileyanns.mileage;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class SyncClient {
    public interface Callback { void done(boolean ok, String message); }

    public static void discover(Context c, Callback cb){ new Thread(()->{
        try(DatagramSocket s=new DatagramSocket()){ s.setBroadcast(true); s.setSoTimeout(2500); byte[] out="BA_MILEAGE_DISCOVER_V1".getBytes(StandardCharsets.UTF_8); DatagramPacket p=new DatagramPacket(out,out.length,InetAddress.getByName("255.255.255.255"),8769); s.send(p); byte[] buf=new byte[512]; DatagramPacket r=new DatagramPacket(buf,buf.length); s.receive(r); JSONObject o=new JSONObject(new String(r.getData(),0,r.getLength(),StandardCharsets.UTF_8)); String url="http://"+r.getAddress().getHostAddress()+":"+o.optInt("port",8768); Prefs.get(c).edit().putString("server_url",url).apply(); cb.done(true,"Found Business Manager at "+url); }
        catch(Exception e){cb.done(false,"Business Manager was not found. Make sure the Windows Business Manager is open and both devices are on the same Wi-Fi.");}
    }).start(); }

    public static void syncEstates(Context c, Callback cb){ new Thread(()->{ try{ String base=base(c),code=Prefs.pairingCode(c); if(base.isEmpty()||code.isEmpty())throw new Exception("Find Business Manager and enter the pairing code first."); JSONObject r=get(base+"/api/estates?code="+URLEncoder.encode(code,"UTF-8")); if(!r.optBoolean("ok"))throw new Exception(r.optString("error","Sync failed")); new DatabaseHelper(c).replaceEstates(r.getJSONArray("estates")); cb.done(true,"Estate list synced."); }catch(Exception e){cb.done(false,e.getMessage());} }).start(); }

    public static void syncTrips(Context c, Callback cb){ new Thread(()->{ try{ DatabaseHelper db=new DatabaseHelper(c); List<DatabaseHelper.Trip> trips=db.unsyncedTrips(); if(trips.isEmpty()){cb.done(true,"All trips are already synced.");return;} String base=base(c),code=Prefs.pairingCode(c); if(base.isEmpty()||code.isEmpty())throw new Exception("Find Business Manager and enter the pairing code first."); JSONObject body=new JSONObject(); body.put("code",code); JSONArray a=new JSONArray(); for(DatabaseHelper.Trip t:trips){ JSONObject o=new JSONObject(); o.put("id",t.id);o.put("tripType",t.tripType);o.put("estateId",t.estateId);o.put("estateName",t.estateName);o.put("purpose",t.purpose);o.put("date",t.date);o.put("startTime",t.startTime);o.put("endTime",t.endTime);o.put("miles",t.miles);o.put("startLat",t.startLat);o.put("startLon",t.startLon);o.put("endLat",t.endLat);o.put("endLon",t.endLon);o.put("startOdometer",t.startOdometer);o.put("endOdometer",t.endOdometer);a.put(o);} body.put("trips",a); JSONObject r=post(base+"/api/trips",body); if(!r.optBoolean("ok"))throw new Exception(r.optString("error","Sync failed")); db.markSynced(r.optJSONArray("accepted")!=null?r.getJSONArray("accepted"):new JSONArray()); cb.done(true,r.optInt("count",0)+" trip(s) synced to Business Manager."); }catch(Exception e){cb.done(false,e.getMessage());} }).start(); }

    private static String base(Context c){String b=Prefs.serverUrl(c).trim();while(b.endsWith("/"))b=b.substring(0,b.length()-1);return b;}
    private static JSONObject get(String u)throws Exception{ HttpURLConnection h=(HttpURLConnection)new URL(u).openConnection();h.setConnectTimeout(3000);h.setReadTimeout(4000);h.setRequestProperty("Accept","application/json");return read(h); }
    private static JSONObject post(String u,JSONObject body)throws Exception{ HttpURLConnection h=(HttpURLConnection)new URL(u).openConnection();h.setConnectTimeout(3000);h.setReadTimeout(5000);h.setRequestMethod("POST");h.setDoOutput(true);h.setRequestProperty("Content-Type","application/json");try(OutputStream os=h.getOutputStream()){os.write(body.toString().getBytes(StandardCharsets.UTF_8));}return read(h); }
    private static JSONObject read(HttpURLConnection h)throws Exception{ InputStream in=h.getResponseCode()>=400?h.getErrorStream():h.getInputStream(); if(in==null)throw new IOException("No response from Business Manager"); try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){StringBuilder sb=new StringBuilder();String line;while((line=br.readLine())!=null)sb.append(line);return new JSONObject(sb.toString());} }
    private SyncClient(){}
}
