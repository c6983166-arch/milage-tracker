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

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    public static final String ACTION_CAR_ESTATE="com.baileyanns.mileage.CAR_ESTATE";
    public static final String ACTION_CAR_BUSINESS="com.baileyanns.mileage.CAR_BUSINESS";
    public static final String ACTION_REVIEW_END="com.baileyanns.mileage.REVIEW_END";
    private static final int EXPORT_CSV=71;
    private final int NAVY=Color.rgb(6,26,67),GOLD=Color.rgb(217,140,0),BG=Color.rgb(245,247,251);
    private LinearLayout root; private DatabaseHelper db; private Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=()->{ if(root!=null && root.getTag()!=null && "dashboard".equals(root.getTag())) showDashboard(); };

    @Override public void onCreate(Bundle b){super.onCreate(b);db=new DatabaseHelper(this);showDashboard();handleAction(getIntent());}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handleAction(i);}
    @Override protected void onResume(){super.onResume();handler.postDelayed(refresh,800);}
    @Override protected void onPause(){super.onPause();handler.removeCallbacks(refresh);}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==EXPORT_CSV && resultCode==RESULT_OK && data!=null && data.getData()!=null){
            try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
                if(out==null)throw new Exception("Could not open the selected file.");
                out.write(db.csv().getBytes(StandardCharsets.UTF_8));
                toast("Mileage CSV exported.");
            }catch(Exception e){toast("Could not export mileage: "+e.getMessage());}
        }
    }

    private void handleAction(Intent i){
        if(i==null)return;
        if(ACTION_CAR_ESTATE.equals(i.getAction()))chooseEstateAndStart();
        else if(ACTION_CAR_BUSINESS.equals(i.getAction()))startDetails("BUSINESS",null);
        else if(ACTION_REVIEW_END.equals(i.getAction()))reviewEndTrip();
    }

    private void base(String tag,String title){
        ScrollView sc=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(16),dp(18),dp(28));root.setBackgroundColor(BG);root.setTag(tag);sc.addView(root);setContentView(sc);
        ImageView logo=new ImageView(this);logo.setImageResource(com.baileyanns.mileage.R.drawable.ba1_logo);logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);root.addView(logo,new LinearLayout.LayoutParams(-1,dp(105))); TextView t=text(title,24,NAVY,true);t.setPadding(0,dp(8),0,dp(14));root.addView(t);
    }

    private void showDashboard(){
        handler.removeCallbacks(refresh);
        base("dashboard","Mileage Tracker");
        DatabaseHelper.Trip active=db.activeTrip();
        if(active!=null){
            LinearLayout card=card();
            card.addView(text("TRIP ACTIVE",28,GOLD,true));
            card.addView(text(("ESTATE".equals(active.tripType)?active.estateName:"Business Miles"),20,NAVY,true));
            if(!active.purpose.isEmpty())card.addView(text(active.purpose,14,Color.DKGRAY,false));
            card.addView(text(String.format(Locale.US,"%.1f miles",active.miles),34,NAVY,true));
            card.addView(button("END TRIP",v->reviewEndTrip(),true));
            root.addView(card);
        } else {
            root.addView(button("START ESTATE MILES",v->chooseEstateAndStart(),true));
            root.addView(space(10));
            root.addView(button("START BUSINESS MILES",v->startDetails("BUSINESS",null),false));
        }

        root.addView(space(16));
        addDashboardTotals();

        int unsynced=db.unsyncedCount();
        if(unsynced>0){
            root.addView(space(12));
            LinearLayout warn=card();
            warn.addView(text(unsynced+" trip"+(unsynced==1?"":"s")+" waiting to sync",18,GOLD,true));
            warn.addView(text("These trips are saved on your phone but have not reached Business Manager yet.",13,Color.DKGRAY,false));
            warn.addView(button("SYNC NOW",v->syncNow(),false));
            root.addView(warn);
        }

        root.addView(space(18));
        LinearLayout c=card();c.addView(text("Mileage Records",18,NAVY,true));
        c.addView(button("TRIP HISTORY",v->showTrips(),false));c.addView(space(8));
        c.addView(button("YEAR-END TAX REPORT",v->showYearEnd(),false));c.addView(space(8));
        c.addView(button("SYNC WITH BUSINESS MANAGER",v->syncNow(),false));c.addView(space(8));
        c.addView(button("EXPORT ALL MILEAGE (CSV)",v->exportCsv(),false));
        root.addView(c);
        root.addView(space(16));root.addView(button("SETTINGS / CAR CONNECTION",v->showSettings(),false));
        handler.postDelayed(refresh,1200);
    }

    private void addDashboardTotals(){
        Calendar now=Calendar.getInstance();
        Calendar day=(Calendar)now.clone();day.set(Calendar.HOUR_OF_DAY,0);day.set(Calendar.MINUTE,0);day.set(Calendar.SECOND,0);day.set(Calendar.MILLISECOND,0);
        Calendar nextDay=(Calendar)day.clone();nextDay.add(Calendar.DAY_OF_MONTH,1);
        Calendar month=(Calendar)day.clone();month.set(Calendar.DAY_OF_MONTH,1);
        Calendar nextMonth=(Calendar)month.clone();nextMonth.add(Calendar.MONTH,1);
        Calendar year=(Calendar)day.clone();year.set(Calendar.DAY_OF_YEAR,1);
        Calendar nextYear=(Calendar)year.clone();nextYear.add(Calendar.YEAR,1);
        DatabaseHelper.Stats td=db.stats(day.getTimeInMillis(),nextDay.getTimeInMillis());
        DatabaseHelper.Stats tm=db.stats(month.getTimeInMillis(),nextMonth.getTimeInMillis());
        DatabaseHelper.Stats ty=db.stats(year.getTimeInMillis(),nextYear.getTimeInMillis());
        LinearLayout c=card();c.addView(text("Mileage Dashboard",18,NAVY,true));
        c.addView(text(String.format(Locale.US,"Today: %.1f miles",td.total),16,Color.DKGRAY,false));
        c.addView(text(String.format(Locale.US,"This Month: %.1f miles",tm.total),16,Color.DKGRAY,false));
        c.addView(text(String.format(Locale.US,"This Year: %.1f miles",ty.total),16,NAVY,true));
        c.addView(text(String.format(Locale.US,"Estate Miles: %.1f     Business Miles: %.1f",ty.estate,ty.business),15,GOLD,true));
        root.addView(c);
    }

    private void chooseEstateAndStart(){
        ensureCorePermissions();
        List<DatabaseHelper.Estate> estates=db.estates();
        if(estates.isEmpty()){toast("Sync the Estate list from Business Manager first.");showSettings();return;}
        String[] names=new String[estates.size()];for(int i=0;i<names.length;i++)names[i]=estates.get(i).name+(estates.get(i).client.isEmpty()?"":" — "+estates.get(i).client);
        new AlertDialog.Builder(this).setTitle("Select Estate").setItems(names,(d,which)->startDetails("ESTATE",estates.get(which))).setNegativeButton("Cancel",null).show();
    }

    private void startDetails(String type,DatabaseHelper.Estate estate){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(4),dp(20),0);
        String[] purposes={"No purpose / Skip","Bank","Supplies","Advertising","Client Meeting","Donation Drop-Off","Other / Custom"};
        Spinner purpose=new Spinner(this);purpose.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,purposes));
        EditText custom=new EditText(this);custom.setHint("Custom purpose (optional)");custom.setSingleLine(true);
        EditText odo=new EditText(this);odo.setHint("Starting odometer (optional)");odo.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(label("Favorite / Common Purpose"));box.addView(purpose);box.addView(custom);box.addView(space(8));box.addView(label("Starting Odometer"));box.addView(odo);
        String title="ESTATE".equals(type)?"Start Estate Miles":"Start Business Miles";
        String msg="ESTATE".equals(type)?estate.name:"Enter trip details";
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setView(box).setPositiveButton("Start Trip",(d,w)->{
            String selected=String.valueOf(purpose.getSelectedItem());
            String p="No purpose / Skip".equals(selected)?"":"Other / Custom".equals(selected)?custom.getText().toString().trim():selected;
            startTrip(type,estate,p,number(odo.getText().toString()));
        }).setNegativeButton("Cancel",null).show();
    }

    private void startTrip(String type,DatabaseHelper.Estate e,String purpose,double startOdometer){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},20);toast("Allow precise location, then tap Start again.");return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},21);
        Intent i=new Intent(this,MileageTrackingService.class).setAction(MileageTrackingService.ACTION_START).putExtra("tripType",type).putExtra("estateId",e==null?"":e.id).putExtra("estateName",e==null?"":e.name).putExtra("purpose",purpose).putExtra("startOdometer",startOdometer);
        startForegroundService(i);toast("Mileage tracking started.");handler.postDelayed(this::showDashboard,500);
    }

    private void reviewEndTrip(){
        DatabaseHelper.Trip t=db.activeTrip();if(t==null){toast("There is no active trip.");showDashboard();return;}
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),0,dp(20),0);
        String name="ESTATE".equals(t.tripType)?"Estate Miles — "+t.estateName:"Business Miles";
        box.addView(text(name,17,NAVY,true));
        box.addView(text(t.date+"   Started "+t.startTime,13,Color.DKGRAY,false));
        if(!t.purpose.isEmpty())box.addView(text("Purpose: "+t.purpose,14,Color.DKGRAY,false));
        box.addView(text(String.format(Locale.US,"GPS Mileage: %.1f miles",t.miles),22,GOLD,true));
        if(t.startOdometer>0)box.addView(text(String.format(Locale.US,"Starting Odometer: %.1f",t.startOdometer),14,Color.DKGRAY,false));
        EditText endOdo=new EditText(this);endOdo.setHint("Ending odometer (optional)");endOdo.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);box.addView(endOdo);
        new AlertDialog.Builder(this).setTitle("Review Trip Before Saving").setView(box).setPositiveButton("SAVE TRIP",(d,w)->{
            double eo=number(endOdo.getText().toString());
            startService(new Intent(this,MileageTrackingService.class).setAction(MileageTrackingService.ACTION_STOP).putExtra("endOdometer",eo));
            toast("Trip saved.");handler.postDelayed(this::showDashboard,700);
        }).setNegativeButton("KEEP TRACKING",null).show();
    }

    private void showTrips(){
        base("trips","Trip History");root.addView(back());
        List<DatabaseHelper.Trip> trips=db.recentTrips(100);
        if(trips.isEmpty()){root.addView(text("No completed trips yet.",16,Color.DKGRAY,false));return;}
        for(DatabaseHelper.Trip t:trips){
            LinearLayout c=card();String name="ESTATE".equals(t.tripType)?"Estate Miles — "+t.estateName:"Business Miles";
            c.addView(text(name,17,NAVY,true));c.addView(text(t.date+"   "+t.startTime+" – "+t.endTime,13,Color.DKGRAY,false));
            if(!t.purpose.isEmpty())c.addView(text(t.purpose,14,Color.DKGRAY,false));
            c.addView(text(String.format(Locale.US,"%.1f miles   • %s",t.miles,t.synced==1?"Synced":"Not Synced"),18,GOLD,true));
            if(t.startOdometer>0||t.endOdometer>0)c.addView(text(String.format(Locale.US,"Odometer: %s → %s",t.startOdometer>0?String.format(Locale.US,"%.1f",t.startOdometer):"—",t.endOdometer>0?String.format(Locale.US,"%.1f",t.endOdometer):"—"),13,Color.DKGRAY,false));
            c.addView(button("EDIT TRIP",v->editTrip(t),false));c.addView(space(6));c.addView(button("DELETE TRIP",v->confirmDelete(t),false));
            root.addView(c);root.addView(space(8));
        }
    }

    private void editTrip(DatabaseHelper.Trip t){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),0,dp(20),0);
        Spinner type=new Spinner(this);String[] types={"Estate Miles","Business Miles"};type.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,types));type.setSelection("ESTATE".equals(t.tripType)?0:1);
        List<DatabaseHelper.Estate> estates=db.estates();List<String> estateNames=new ArrayList<>();estateNames.add("No Estate / Not Applicable");int selectedEstate=0;
        for(int i=0;i<estates.size();i++){DatabaseHelper.Estate e=estates.get(i);estateNames.add(e.name+(e.client.isEmpty()?"":" — "+e.client));if(e.id.equals(t.estateId))selectedEstate=i+1;}
        Spinner estate=new Spinner(this);estate.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,estateNames));estate.setSelection(selectedEstate);
        EditText purpose=new EditText(this);purpose.setHint("Purpose");purpose.setText(t.purpose);
        EditText miles=new EditText(this);miles.setHint("Miles");miles.setInputType(8194);miles.setText(String.format(Locale.US,"%.1f",t.miles));
        EditText startOdo=new EditText(this);startOdo.setHint("Starting odometer");startOdo.setInputType(8194);if(t.startOdometer>0)startOdo.setText(String.format(Locale.US,"%.1f",t.startOdometer));
        EditText endOdo=new EditText(this);endOdo.setHint("Ending odometer");endOdo.setInputType(8194);if(t.endOdometer>0)endOdo.setText(String.format(Locale.US,"%.1f",t.endOdometer));
        box.addView(label("Category"));box.addView(type);box.addView(label("Estate"));box.addView(estate);box.addView(purpose);box.addView(miles);box.addView(startOdo);box.addView(endOdo);
        new AlertDialog.Builder(this).setTitle("Edit Saved Trip").setView(box).setPositiveButton("SAVE CHANGES",(d,w)->{
            boolean isEstate=type.getSelectedItemPosition()==0;DatabaseHelper.Estate e=isEstate&&estate.getSelectedItemPosition()>0?estates.get(estate.getSelectedItemPosition()-1):null;
            String tt=isEstate?"ESTATE":"BUSINESS";db.updateTrip(t.id,tt,e==null?"":e.id,e==null?"":e.name,purpose.getText().toString().trim(),number(miles.getText().toString()),number(startOdo.getText().toString()),number(endOdo.getText().toString()));
            toast("Trip updated. It will sync again.");showTrips();
        }).setNegativeButton("Cancel",null).show();
    }

    private void confirmDelete(DatabaseHelper.Trip t){
        new AlertDialog.Builder(this).setTitle("Delete Trip?").setMessage(t.date+" — "+String.format(Locale.US,"%.1f miles",t.miles)+"\n\nThis removes the trip from this phone.").setPositiveButton("DELETE",(d,w)->{db.deleteTrip(t.id);toast("Trip deleted.");showTrips();}).setNegativeButton("Cancel",null).show();
    }

    private void showYearEnd(){
        base("year","Year-End Tax Report");root.addView(back());EditText year=new EditText(this);year.setInputType(2);year.setText(String.valueOf(Calendar.getInstance().get(Calendar.YEAR)));root.addView(label("Tax Year"));root.addView(year);TextView summary=text("",17,NAVY,true);root.addView(summary);
        Runnable calc=()->{try{int y=Integer.parseInt(year.getText().toString());List<DatabaseHelper.Trip> list=db.tripsForYear(y);double e=0,b=0;for(DatabaseHelper.Trip t:list){if("ESTATE".equals(t.tripType))e+=t.miles;else b+=t.miles;}summary.setText(String.format(Locale.US,"Estate Miles: %.1f\nBusiness Miles: %.1f\nTotal Business Mileage: %.1f",e,b,e+b));}catch(Exception ignored){}};
        calc.run();root.addView(button("REFRESH TOTALS",v->calc.run(),false));root.addView(space(8));root.addView(button("PRINT YEAR-END REPORT",v->{try{int y=Integer.parseInt(year.getText().toString());List<DatabaseHelper.Trip> list=db.tripsForYear(y);YearEndReport.print(this,YearEndReport.create(this,y,list),y);}catch(Exception ex){toast("Could not create report: "+ex.getMessage());}},true));
    }

    private void exportCsv(){
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/csv");i.putExtra(Intent.EXTRA_TITLE,"Bailey_Anns_Mileage_Tracker_All_Trips.csv");startActivityForResult(i,EXPORT_CSV);
    }

    private void syncNow(){
        SyncClient.syncEstates(this,(ok,msg)->runOnUiThread(()->{toast(msg);if(ok)SyncClient.syncTrips(this,(ok2,msg2)->runOnUiThread(()->{toast(msg2);showDashboard();}));}));
    }

    private void showSettings(){
        base("settings","Settings");root.addView(back());root.addView(text("Business Manager Sync",19,NAVY,true));TextView status=text(Prefs.serverUrl(this).isEmpty()?"Not connected":"Windows: "+Prefs.serverUrl(this),14,Color.DKGRAY,false);root.addView(status);root.addView(button("FIND BUSINESS MANAGER",v->SyncClient.discover(this,(ok,msg)->runOnUiThread(()->{status.setText(msg);toast(msg);})),true));root.addView(space(8));EditText code=new EditText(this);code.setHint("6-digit pairing code shown in Business Manager");code.setInputType(2);code.setText(Prefs.pairingCode(this));root.addView(label("Pairing Code"));root.addView(code);root.addView(button("SAVE CODE & SYNC ESTATES",v->{Prefs.get(this).edit().putString("pairing_code",code.getText().toString().trim()).apply();SyncClient.syncEstates(this,(ok,msg)->runOnUiThread(()->toast(msg)));},false));
        root.addView(space(20));root.addView(text("Automatic Mileage Backup",19,NAVY,true));root.addView(text("A CSV backup is refreshed automatically after trips are saved, edited, deleted, or synced.",14,Color.DKGRAY,false));root.addView(button("EXPORT A COPY NOW",v->exportCsv(),false));
        root.addView(space(20));root.addView(text("Car Connection Reminder",19,NAVY,true));root.addView(text("When your saved car connects by Bluetooth, the app asks whether to track Estate Miles or Business Miles. It does not record every drive automatically.",14,Color.DKGRAY,false));Switch sw=new Switch(this);sw.setText("Enable car connection reminder");sw.setChecked(Prefs.carReminder(this));sw.setOnCheckedChangeListener((b,on)->Prefs.get(this).edit().putBoolean("car_reminder",on).apply());root.addView(sw);root.addView(button(Prefs.carName(this).isEmpty()?"CHOOSE MY CAR":"CAR: "+Prefs.carName(this),v->chooseCar(),false));root.addView(space(18));root.addView(text("Permissions",17,NAVY,true));root.addView(button("ALLOW LOCATION / BLUETOOTH",v->ensureCorePermissions(),false));
    }

    private void chooseCar(){
        if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},30);toast("Allow Nearby devices, then choose your car again.");return;}
        BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();if(a==null){toast("Bluetooth is not available on this phone.");return;}Set<BluetoothDevice> set=a.getBondedDevices();if(set.isEmpty()){toast("Pair your phone with the car in Android Bluetooth settings first.");return;}List<BluetoothDevice> list=new ArrayList<>(set);String[] names=new String[list.size()];for(int i=0;i<list.size();i++){BluetoothDevice d=list.get(i);names[i]=(d.getName()==null?"Bluetooth device":d.getName())+"\n"+d.getAddress();}new AlertDialog.Builder(this).setTitle("Choose Your Car").setItems(names,(x,which)->{BluetoothDevice d=list.get(which);Prefs.get(this).edit().putString("car_address",d.getAddress()).putString("car_name",d.getName()==null?"My Car":d.getName()).apply();toast("Car saved.");showSettings();}).setNegativeButton("Cancel",null).show();
    }

    private void ensureCorePermissions(){List<String> p=new ArrayList<>();if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.ACCESS_FINE_LOCATION);if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.BLUETOOTH_CONNECT);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.POST_NOTIFICATIONS);if(!p.isEmpty())requestPermissions(p.toArray(new String[0]),9);}

    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(16),dp(16),dp(16));android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();g.setColor(Color.WHITE);g.setCornerRadius(dp(12));g.setStroke(dp(1),Color.rgb(220,226,235));c.setBackground(g);return c;}
    private Button button(String s,View.OnClickListener l,boolean primary){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(16);b.setTextColor(primary?Color.WHITE:NAVY);android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();g.setColor(primary?NAVY:Color.WHITE);g.setStroke(dp(2),primary?NAVY:GOLD);g.setCornerRadius(dp(10));b.setBackground(g);b.setPadding(dp(12),dp(12),dp(12),dp(12));b.setOnClickListener(l);return b;}
    private TextView text(String s,float size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);t.setPadding(0,dp(4),0,dp(6));return t;}
    private TextView label(String s){return text(s,13,Color.DKGRAY,true);}private View space(int h){Space s=new Space(this);s.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h)));return s;}private Button back(){return button("← BACK",v->showDashboard(),false);}private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private double number(String s){try{return Math.max(0,Double.parseDouble(s.trim()));}catch(Exception e){return 0;}}
}
