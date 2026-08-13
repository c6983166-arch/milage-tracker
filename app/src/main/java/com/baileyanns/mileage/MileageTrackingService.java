package com.baileyanns.mileage;

import android.Manifest;
import android.app.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MileageTrackingService extends Service implements LocationListener {
    public static final String ACTION_START="com.baileyanns.mileage.START";
    public static final String ACTION_STOP="com.baileyanns.mileage.STOP";
    public static final String ACTION_UPDATED="com.baileyanns.mileage.UPDATED";
    private static final int NOTIFY_ID=1001;
    private DatabaseHelper db; private LocationManager lm; private String tripId=""; private Location last; private double meters=0;

    @Override public void onCreate(){ super.onCreate(); db=new DatabaseHelper(this); lm=(LocationManager)getSystemService(LOCATION_SERVICE); createChannel(); }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?"":intent.getAction();
        if(ACTION_STOP.equals(action)){ stopTrip(intent); return START_NOT_STICKY; }
        if(ACTION_START.equals(action)){ startTrip(intent); }
        else if((action==null||action.isEmpty()) && db.activeTrip()!=null){ startTrip(new Intent()); }
        return START_STICKY;
    }
    private void startTrip(Intent i){
        DatabaseHelper.Trip active=db.activeTrip();
        if(active!=null){ tripId=active.id; meters=active.miles*1609.344; }
        else {
            String type=i.getStringExtra("tripType"),estateId=i.getStringExtra("estateId"),estateName=i.getStringExtra("estateName"),purpose=i.getStringExtra("purpose");
            double startOdometer=i.getDoubleExtra("startOdometer",0);
            long now=System.currentTimeMillis(); tripId=db.createTrip(type,nvl(estateId),nvl(estateName),nvl(purpose),fmt("yyyy-MM-dd",now),fmt("h:mm a",now),now,startOdometer);
        }
        startForeground(NOTIFY_ID,notification("Mileage tracking is active"));
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){
            try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,3000,5,this); } catch(Exception ignored){}
            try { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,5000,10,this); } catch(Exception ignored){}
        }
    }
    private void stopTrip(Intent intent){
        try{lm.removeUpdates(this);}catch(Exception ignored){}
        DatabaseHelper.Trip t=db.activeTrip(); if(t!=null){ double lat=last==null?t.endLat:last.getLatitude(),lon=last==null?t.endLon:last.getLongitude(); long now=System.currentTimeMillis(); double endOdometer=intent==null?0:intent.getDoubleExtra("endOdometer",0); db.finishTrip(t.id,fmt("h:mm a",now),now,meters/1609.344,lat,lon,endOdometer); }
        sendBroadcast(new Intent(ACTION_UPDATED).setPackage(getPackageName())); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }
    @Override public void onLocationChanged(Location loc){
        if(loc==null || tripId.isEmpty())return; if(loc.hasAccuracy() && loc.getAccuracy()>75)return;
        if(last!=null && loc.getTime()>0 && last.getTime()>0 && loc.getTime()<last.getTime()) return;
        if(last==null){ last=loc; db.setStartLocation(tripId,loc.getLatitude(),loc.getLongitude()); db.updateDistance(tripId,meters/1609.344,loc.getLatitude(),loc.getLongitude()); return; }
        float delta=last.distanceTo(loc); long dt=Math.max(1,loc.getTime()-last.getTime()); double mph=(delta/(dt/1000.0))*2.236936;
        if(delta>=4 && mph<125){ meters+=delta; last=loc; db.updateDistance(tripId,meters/1609.344,loc.getLatitude(),loc.getLongitude()); getSystemService(NotificationManager.class).notify(NOTIFY_ID,notification(String.format(Locale.US,"Tracking • %.1f miles",meters/1609.344))); sendBroadcast(new Intent(ACTION_UPDATED).setPackage(getPackageName())); }
        else if(delta<4){ last=loc; }
    }
    @Override public void onProviderEnabled(String p){} @Override public void onProviderDisabled(String p){} @Override public void onStatusChanged(String p,int s,Bundle b){}
    @Override public IBinder onBind(Intent intent){return null;}
    private void createChannel(){ if(android.os.Build.VERSION.SDK_INT>=26){ NotificationChannel c=new NotificationChannel("mileage_tracking","Mileage Tracking",NotificationManager.IMPORTANCE_LOW); c.setDescription("Shown while a business trip is being recorded."); getSystemService(NotificationManager.class).createNotificationChannel(c);} }
    private Notification notification(String text){
        Intent review=new Intent(this,MainActivity.class).setAction(MainActivity.ACTION_REVIEW_END).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi=PendingIntent.getActivity(this,2,review,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent open=new Intent(this,MainActivity.class); PendingIntent oi=PendingIntent.getActivity(this,3,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,"mileage_tracking").setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("Bailey Ann's Mileage Tracker").setContentText(text).setOngoing(true).setContentIntent(oi).addAction(new Notification.Action.Builder(null,"Review / End Trip",pi).build()).build();
    }
    private static String nvl(String s){return s==null?"":s;} private static String fmt(String f,long t){return new SimpleDateFormat(f,Locale.US).format(new Date(t));}
}
