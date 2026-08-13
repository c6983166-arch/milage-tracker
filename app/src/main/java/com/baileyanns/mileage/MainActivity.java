package com.baileyanns.mileage;

import android.Manifest;
import android.app.*;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;

import java.util.*;

public class MainActivity extends Activity {
    public static final String ACTION_CAR_ESTATE="com.baileyanns.mileage.CAR_ESTATE";
    public static final String ACTION_CAR_BUSINESS="com.baileyanns.mileage.CAR_BUSINESS";
    private final int NAVY=Color.rgb(6,26,67),GOLD=Color.rgb(217,140,0),BG=Color.rgb(245,247,251);
    private LinearLayout root; private DatabaseHelper db; private Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=()->{ if(root!=null && root.getTag()!=null && "dashboard".equals(root.getTag())) showDashboard(); };

    @Override public void onCreate(Bundle b){super.onCreate(b);db=new DatabaseHelper(this);showDashboard();handleAction(getIntent());}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handleAction(i);}
    @Override protected void onResume(){super.onResume();handler.postDelayed(refresh,800);}
    @Override protected void onPause(){super.onPause();handler.removeCallbacks(refresh);}

    private void handleAction(Intent i){ if(i==null)return; if(ACTION_CAR_ESTATE.equals(i.getAction()))chooseEstateAndStart(); else if(ACTION_CAR_BUSINESS.equals(i.getAction()))askPurposeAndStart("BUSINESS",null); }

    private void base(String tag,String title){
        ScrollView sc=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(16),dp(18),dp(28));root.setBackgroundColor(BG);root.setTag(tag);sc.addView(root);setContentView(sc);
        ImageView logo=new ImageView(this);logo.setImageResource(com.baileyanns.mileage.R.drawable.ba1_logo);logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);root.addView(logo,new LinearLayout.LayoutParams(-1,dp(105))); TextView t=text(title,24,NAVY,true);t.setPadding(0,dp(8),0,dp(14));root.addView(t);
    }
    private void showDashboard(){
        handler.removeCallbacks(refresh);
        base("dashboard","Mileage Tracker"); DatabaseHelper.Trip active=db.activeTrip();
        if(active!=null){ LinearLayout card=card(); card.addView(text("TRIP IN PROGRESS",12,GOLD,true));card.addView(text(("ESTATE".equals(active.tripType)?active.estateName:"Business Miles"),20,NAVY,true));card.addView(text(String.format(Locale.US,"%.1f miles",active.miles),32,NAVY,true));card.addView(button("END TRIP",v->{startService(new Intent(this,MileageTrackingService.class).setAction(MileageTrackingService.ACTION_STOP));toast("Trip ended and saved.");handler.postDelayed(this::showDashboard,700);},true));root.addView(card); }
        else { root.addView(button("START ESTATE MILES",v->chooseEstateAndStart(),true)); root.addView(space(10)); root.addView(button("START BUSINESS MILES",v->askPurposeAndStart("BUSINESS",null),false)); }
        root.addView(space(18));LinearLayout c=card();c.addView(text("Mileage Records",18,NAVY,true));c.addView(button("TRIP HISTORY",v->showTrips(),false));c.addView(space(8));c.addView(button("YEAR-END TAX REPORT",v->showYearEnd(),false));c.addView(space(8));c.addView(button("SYNC WITH BUSINESS MANAGER",v->syncNow(),false));root.addView(c);
        root.addView(space(16));root.addView(button("SETTINGS / CAR CONNECTION",v->showSettings(),false));
        handler.postDelayed(refresh,1200);
    }
    private void chooseEstateAndStart(){
        ensureCorePermissions(); List<DatabaseHelper.Estate> estates=db.estates(); if(estates.isEmpty()){toast("Sync the Estate list from Business Manager first.");showSettings();return;} String[] names=new String[estates.size()];for(int i=0;i<names.length;i++)names[i]=estates.get(i).name+(estates.get(i).client.isEmpty()?"":" — "+estates.get(i).client); new AlertDialog.Builder(this).setTitle("Select Estate").setItems(names,(d,which)->askPurposeAndStart("ESTATE",estates.get(which))).setNegativeButton("Cancel",null).show();
    }
    private void askPurposeAndStart(String type,DatabaseHelper.Estate estate){ EditText e=new EditText(this);e.setHint("Purpose (optional)");e.setSingleLine(true);new AlertDialog.Builder(this).setTitle("ESTATE".equals(type)?"Estate Miles":"Business Miles").setMessage("ESTATE".equals(type)?estate.name:"What is this trip for?").setView(e).setPositiveButton("Start Trip",(d,w)->startTrip(type,estate,e.getText().toString().trim())).setNegativeButton("Cancel",null).show(); }
    private void startTrip(String type,DatabaseHelper.Estate e,String purpose){ if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},20);toast("Allow precise location, then tap Start again.");return;} if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},21);Intent i=new Intent(this,MileageTrackingService.class).setAction(MileageTrackingService.ACTION_START).putExtra("tripType",type).putExtra("estateId",e==null?"":e.id).putExtra("estateName",e==null?"":e.name).putExtra("purpose",purpose);startForegroundService(i);toast("Mileage tracking started.");handler.postDelayed(this::showDashboard,500); }

    private void showTrips(){ base("trips","Trip History");root.addView(back());List<DatabaseHelper.Trip> trips=db.recentTrips(100);if(trips.isEmpty()){root.addView(text("No completed trips yet.",16,Color.DKGRAY,false));return;}for(DatabaseHelper.Trip t:trips){LinearLayout c=card();String name="ESTATE".equals(t.tripType)?"Estate Miles — "+t.estateName:"Business Miles";c.addView(text(name,17,NAVY,true));c.addView(text(t.date+"   "+t.startTime+" – "+t.endTime,13,Color.DKGRAY,false));if(!t.purpose.isEmpty())c.addView(text(t.purpose,14,Color.DKGRAY,false));c.addView(text(String.format(Locale.US,"%.1f miles%s",t.miles,t.synced==1?"   • Synced":"   • Not synced"),18,GOLD,true));root.addView(c);root.addView(space(8));}}
    private void showYearEnd(){ base("year","Year-End Tax Report");root.addView(back());EditText year=new EditText(this);year.setInputType(2);year.setText(String.valueOf(Calendar.getInstance().get(Calendar.YEAR)));root.addView(label("Tax Year"));root.addView(year);TextView summary=text("",17,NAVY,true);root.addView(summary);Runnable calc=()->{try{int y=Integer.parseInt(year.getText().toString());List<DatabaseHelper.Trip> list=db.tripsForYear(y);double e=0,b=0;for(DatabaseHelper.Trip t:list){if("ESTATE".equals(t.tripType))e+=t.miles;else b+=t.miles;}summary.setText(String.format(Locale.US,"Estate Miles: %.1f\nBusiness Miles: %.1f\nTotal Business Mileage: %.1f",e,b,e+b));}catch(Exception ignored){}};calc.run();root.addView(button("REFRESH TOTALS",v->calc.run(),false));root.addView(space(8));root.addView(button("PRINT YEAR-END REPORT",v->{try{int y=Integer.parseInt(year.getText().toString());List<DatabaseHelper.Trip> list=db.tripsForYear(y);YearEndReport.print(this,YearEndReport.create(this,y,list),y);}catch(Exception ex){toast("Could not create report: "+ex.getMessage());}},true)); }

    private void syncNow(){ SyncClient.syncEstates(this,(ok,msg)->runOnUiThread(()->{toast(msg);if(ok)SyncClient.syncTrips(this,(ok2,msg2)->runOnUiThread(()->toast(msg2)));})); }
    private void showSettings(){
        base("settings","Settings");root.addView(back());root.addView(text("Business Manager Sync",19,NAVY,true));TextView status=text(Prefs.serverUrl(this).isEmpty()?"Not connected":"Windows: "+Prefs.serverUrl(this),14,Color.DKGRAY,false);root.addView(status);root.addView(button("FIND BUSINESS MANAGER",v->SyncClient.discover(this,(ok,msg)->runOnUiThread(()->{status.setText(msg);toast(msg);})),true));root.addView(space(8));EditText code=new EditText(this);code.setHint("6-digit pairing code shown in Business Manager");code.setInputType(2);code.setText(Prefs.pairingCode(this));root.addView(label("Pairing Code"));root.addView(code);root.addView(button("SAVE CODE & SYNC ESTATES",v->{Prefs.get(this).edit().putString("pairing_code",code.getText().toString().trim()).apply();SyncClient.syncEstates(this,(ok,msg)->runOnUiThread(()->toast(msg)));},false));
        root.addView(space(20));root.addView(text("Car Connection Reminder",19,NAVY,true));root.addView(text("When your saved car connects by Bluetooth, the app asks whether to track Estate Miles or Business Miles. It does not record every drive automatically.",14,Color.DKGRAY,false));Switch sw=new Switch(this);sw.setText("Enable car connection reminder");sw.setChecked(Prefs.carReminder(this));sw.setOnCheckedChangeListener((b,on)->Prefs.get(this).edit().putBoolean("car_reminder",on).apply());root.addView(sw);root.addView(button(Prefs.carName(this).isEmpty()?"CHOOSE MY CAR":"CAR: "+Prefs.carName(this),v->chooseCar(),false));root.addView(space(18));root.addView(text("Permissions",17,NAVY,true));root.addView(button("ALLOW LOCATION / BLUETOOTH",v->ensureCorePermissions(),false));
    }
    private void chooseCar(){ if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},30);toast("Allow Nearby devices, then choose your car again.");return;}BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();if(a==null){toast("Bluetooth is not available on this phone.");return;}Set<BluetoothDevice> set=a.getBondedDevices();if(set.isEmpty()){toast("Pair your phone with the car in Android Bluetooth settings first.");return;}List<BluetoothDevice> list=new ArrayList<>(set);String[] names=new String[list.size()];for(int i=0;i<list.size();i++){BluetoothDevice d=list.get(i);names[i]=(d.getName()==null?"Bluetooth device":d.getName())+"\n"+d.getAddress();}new AlertDialog.Builder(this).setTitle("Choose Your Car").setItems(names,(x,which)->{BluetoothDevice d=list.get(which);Prefs.get(this).edit().putString("car_address",d.getAddress()).putString("car_name",d.getName()==null?"My Car":d.getName()).apply();toast("Car saved.");showSettings();}).setNegativeButton("Cancel",null).show();}
    private void ensureCorePermissions(){List<String> p=new ArrayList<>();if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.ACCESS_FINE_LOCATION);if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.BLUETOOTH_CONNECT);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.POST_NOTIFICATIONS);if(!p.isEmpty())requestPermissions(p.toArray(new String[0]),9);}

    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(16),dp(16),dp(16));android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();g.setColor(Color.WHITE);g.setCornerRadius(dp(12));g.setStroke(dp(1),Color.rgb(220,226,235));c.setBackground(g);return c;}
    private Button button(String s,View.OnClickListener l,boolean primary){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(16);b.setTextColor(primary?Color.WHITE:NAVY);android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();g.setColor(primary?NAVY:Color.WHITE);g.setStroke(dp(2),primary?NAVY:GOLD);g.setCornerRadius(dp(10));b.setBackground(g);b.setPadding(dp(12),dp(12),dp(12),dp(12));b.setOnClickListener(l);return b;}
    private TextView text(String s,float size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);t.setPadding(0,dp(4),0,dp(6));return t;}private TextView label(String s){return text(s,13,Color.DKGRAY,true);}private View space(int h){Space s=new Space(this);s.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h)));return s;}private Button back(){return button("← BACK",v->showDashboard(),false);}private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
