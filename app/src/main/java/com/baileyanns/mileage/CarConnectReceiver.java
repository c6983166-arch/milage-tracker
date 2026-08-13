package com.baileyanns.mileage;

import android.Manifest;
import android.app.*;
import android.bluetooth.BluetoothDevice;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;

public class CarConnectReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i){
        if(!BluetoothDevice.ACTION_ACL_CONNECTED.equals(i.getAction()) || !Prefs.carReminder(c)) return;
        if(Build.VERSION.SDK_INT>=31 && c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) return;
        BluetoothDevice d=i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if(d==null)return; String saved=Prefs.carAddress(c); if(saved.isEmpty() || !saved.equalsIgnoreCase(d.getAddress()))return;
        NotificationManager nm=c.getSystemService(NotificationManager.class); if(Build.VERSION.SDK_INT>=26) nm.createNotificationChannel(new NotificationChannel("car_reminder","Car Trip Reminder",NotificationManager.IMPORTANCE_HIGH));
        Intent estate=new Intent(c,MainActivity.class).setAction(MainActivity.ACTION_CAR_ESTATE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        Intent business=new Intent(c,MainActivity.class).setAction(MainActivity.ACTION_CAR_BUSINESS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent ep=PendingIntent.getActivity(c,101,estate,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE); PendingIntent bp=PendingIntent.getActivity(c,102,business,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(c,"car_reminder").setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("Business trip?").setContentText("Your saved car connected. Track this drive?").setAutoCancel(true).addAction(new Notification.Action.Builder(null,"Estate Miles",ep).build()).addAction(new Notification.Action.Builder(null,"Business Miles",bp).build()).build();
        nm.notify(2002,n);
    }
}
