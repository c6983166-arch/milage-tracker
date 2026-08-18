from pathlib import Path


def replace_between(text, start, end, replacement):
    a = text.find(start)
    if a < 0:
        raise SystemExit('Missing start marker: ' + start)
    b = text.find(end, a)
    if b < 0:
        raise SystemExit('Missing end marker: ' + end)
    return text[:a] + replacement + text[b:]

p = Path('app/src/main/java/com/baileyanns/mileage/MainActivity.java')
s = p.read_text()
s = replace_between(s, '    private void base(String tag,String title){', '    private void showDashboard(){', '''    private void base(String tag,String title){
        final int bg=Color.rgb(6,26,67);
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(bg);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(16),dp(18),dp(28));root.setBackgroundColor(bg);root.setTag(tag);sc.addView(root);setContentView(sc);
        ImageView logo=new ImageView(this);logo.setImageResource(com.baileyanns.mileage.R.drawable.ba_l2_shiny_gold_logo);logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);logo.setBackgroundColor(Color.WHITE);logo.setPadding(dp(8),dp(8),dp(8),dp(8));root.addView(logo,new LinearLayout.LayoutParams(-1,dp(96)));
        TextView t=text(title,24,Color.WHITE,true);t.setPadding(0,dp(12),0,dp(8));root.addView(t);
        TextView tagLine=text("Helping Families Through Life's Transitions.",13,Color.WHITE,false);tagLine.setPadding(0,0,0,dp(14));root.addView(tagLine);
    }

''')
s = replace_between(s, '    private void reviewEndTrip(){', '    private void showTrips(){', '''    private void reviewEndTrip(){
        DatabaseHelper.Trip t=db.activeTrip();if(t==null){toast("There is no active trip.");showDashboard();return;}
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),0,dp(20),0);
        String name="ESTATE".equals(t.tripType)?"Estate Miles — "+t.estateName:"Business Miles";
        box.addView(text(name,17,NAVY,true));
        box.addView(text(t.date+"   Started "+t.startTime,13,Color.DKGRAY,false));
        if(!t.purpose.isEmpty())box.addView(text("Purpose: "+t.purpose,14,Color.DKGRAY,false));
        box.addView(text(String.format(Locale.US,"Current GPS Mileage: %.1f miles",t.miles),22,GOLD,true));
        EditText endOdo=new EditText(this);endOdo.setHint("Ending odometer (optional)");endOdo.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);box.addView(endOdo);
        new AlertDialog.Builder(this).setTitle("End This Trip?").setView(box).setPositiveButton("END TRIP",(d,w)->{
            double eo=number(endOdo.getText().toString());
            startService(new Intent(this,MileageTrackingService.class).setAction(MileageTrackingService.ACTION_STOP).putExtra("endOdometer",eo));
            toast("Trip ended.");handler.postDelayed(this::showTripSummaryAfterEnd,900);
        }).setNegativeButton("KEEP TRACKING",null).show();
    }

    private void showTripSummaryAfterEnd(){
        List<DatabaseHelper.Trip> recent=db.recentTrips(1);if(recent.isEmpty()){showDashboard();return;}
        DatabaseHelper.Trip t=recent.get(0);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),0,dp(20),0);
        box.addView(text("TRIP SUMMARY",15,NAVY,true));
        box.addView(text(String.format(Locale.US,"%.1f Miles",t.miles),30,GOLD,true));
        box.addView(text("Start: "+t.startTime+"     End: "+t.endTime,14,Color.DKGRAY,false));
        box.addView(text("Type: "+("ESTATE".equals(t.tripType)?"Estate Miles":"Business Miles"),14,Color.DKGRAY,false));
        if(!t.estateName.isEmpty())box.addView(text("Estate / Client: "+t.estateName,14,Color.DKGRAY,false));
        if(!t.purpose.isEmpty())box.addView(text("Purpose: "+t.purpose,14,Color.DKGRAY,false));
        new AlertDialog.Builder(this).setTitle("Trip Saved").setView(box)
            .setPositiveButton("EDIT / SAVE DETAILS",(d,w)->editTrip(t))
            .setNeutralButton("VIEW TRIPS",(d,w)->showTrips())
            .setNegativeButton("DONE",(d,w)->showDashboard()).show();
    }

''')
s = replace_between(s, '    private void showSettings(){', '    private void chooseCar(){', '''    private void showSettings(){
        base("settings","Settings");root.addView(back());
        root.addView(text("Business Manager Sync",19,Color.WHITE,true));
        TextView status=text(Prefs.serverUrl(this).isEmpty()?"Not connected":"Windows: "+Prefs.serverUrl(this),14,Color.LTGRAY,false);root.addView(status);
        root.addView(button("FIND BUSINESS MANAGER",v->SyncClient.discover(this,(ok,msg)->runOnUiThread(()->{status.setText(msg);toast(msg);})),true));root.addView(space(8));
        EditText code=new EditText(this);code.setHint("6-digit pairing code shown in Business Manager");code.setHintTextColor(Color.LTGRAY);code.setTextColor(Color.WHITE);code.setInputType(2);code.setText(Prefs.pairingCode(this));root.addView(label("Pairing Code"));root.addView(code);
        root.addView(button("SAVE CODE & SYNC ESTATES",v->{Prefs.get(this).edit().putString("pairing_code",code.getText().toString().trim()).apply();SyncClient.syncEstates(this,(ok,msg)->runOnUiThread(()->toast(msg)));},false));
        root.addView(space(20));root.addView(text("Manual Trip Tracking",19,Color.WHITE,true));
        root.addView(text("Trips start only when you tap START TRIP and end only when you tap END TRIP. There is no automatic vehicle detection or automatic trip start/stop.",14,Color.LTGRAY,false));
        root.addView(button("ALLOW LOCATION",v->ensureCorePermissions(),false));
        root.addView(space(20));root.addView(text("Mileage Backup",19,Color.WHITE,true));
        root.addView(text("A CSV backup is refreshed after trips are saved, edited, deleted, or synced.",14,Color.LTGRAY,false));
        root.addView(button("EXPORT A COPY NOW",v->exportCsv(),false));
    }

''')
old_perm = '    private void ensureCorePermissions(){List<String> p=new ArrayList<>();if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.ACCESS_FINE_LOCATION);if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.BLUETOOTH_CONNECT);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.POST_NOTIFICATIONS);if(!p.isEmpty())requestPermissions(p.toArray(new String[0]),9);}'
new_perm = '    private void ensureCorePermissions(){List<String> p=new ArrayList<>();if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.ACCESS_FINE_LOCATION);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.POST_NOTIFICATIONS);if(!p.isEmpty())requestPermissions(p.toArray(new String[0]),9);}'
if old_perm not in s:
    raise SystemExit('Missing permissions marker')
s = s.replace(old_perm, new_perm, 1)
p.write_text(s)

u = Path('app/src/main/java/com/baileyanns/mileage/PremiumUi.java')
t = u.read_text()
t = t.replace('    private final int GOLD=Color.rgb(217,140,0), BG=Color.rgb(1,14,29), CARD=Color.rgb(5,31,58), CARD_ALT=Color.rgb(6,38,70), LINE=Color.rgb(47,67,88), WHITE=Color.WHITE, MUTED=Color.rgb(192,201,212), GREEN=Color.rgb(112,210,75);','    private final int GOLD=Color.rgb(217,188,0), BG=Color.rgb(6,26,67), CARD=Color.rgb(8,34,70), CARD_ALT=Color.rgb(11,43,82), LINE=Color.rgb(54,75,108), WHITE=Color.WHITE, MUTED=Color.rgb(205,213,227), GREEN=Color.rgb(112,210,75);', 1)
old_dashboard = '        syncCard();root.addView(space(12));purposesCard(s.trips);root.addView(space(14));root.addView(primary("+  NEW TRIP",v->host.uiNewTrip()));'
new_dashboard = '        syncCard();root.addView(space(12));purposesCard(s.trips);root.addView(space(14));DatabaseHelper.Trip active=db.activeTrip();if(active!=null){LinearLayout a=card(true);a.addView(title("TRIP IN PROGRESS"));a.addView(text(("ESTATE".equals(active.tripType)?active.estateName:"Business Miles"),16,WHITE,true));a.addView(text(fmt(active.miles)+" miles",26,GOLD,true));root.addView(a);root.addView(space(10));root.addView(primary("END TRIP",v->host.uiEndTrip()));}else{root.addView(primary("START TRIP",v->host.uiNewTrip()));}TextView manual=text("Manual tracking — you control START and END. No automatic vehicle detection.",12,MUTED,false);manual.setGravity(Gravity.CENTER);manual.setPadding(dp(6),dp(8),dp(6),0);root.addView(manual);'
if old_dashboard not in t:
    raise SystemExit('Missing dashboard action marker')
t = t.replace(old_dashboard, new_dashboard, 1)
t = t.replace('c.addView(primary("+  NEW TRIP",v->host.uiNewTrip()));root.addView(c);return;', 'c.addView(primary("START TRIP",v->host.uiNewTrip()));root.addView(c);return;', 1)
t = t.replace('root.addView(space(8));root.addView(primary("+  NEW TRIP",v->host.uiNewTrip()));', 'root.addView(space(8));root.addView(primary("START TRIP",v->host.uiNewTrip()));', 1)
t = t.replace('primary("PRINT YEAR-END REPORT"', 'primary("CREATE / PRINT PDF REPORT"', 1)
t = replace_between(t, '    void showMore(){', '    private void shell(', '''    void showMore(){
        shell("more","MORE",false);
        LinearLayout s=card(true);s.addView(title("BUSINESS MANAGER SYNC"));String st=Prefs.serverUrl(host).isEmpty()?"Not connected":"Business Manager connected";s.addView(text(st,14,Prefs.serverUrl(host).isEmpty()?GOLD:GREEN,true));s.addView(space(9));s.addView(primary("SYNC NOW",v->host.uiSync()));s.addView(space(8));s.addView(secondary("SETTINGS",v->host.uiSettings()));root.addView(s);
        root.addView(space(12));LinearLayout b=card(false);b.addView(title("MANUAL TRIP TRACKING"));b.addView(text("START TRIP begins GPS mileage tracking. END TRIP stops it. No automatic vehicle detection or automatic start/stop is used.",13,MUTED,false));root.addView(b);
        root.addView(space(12));LinearLayout x=card(false);x.addView(title("BACKUP & EXPORT"));x.addView(text("Your mileage backup stays available for your records.",13,MUTED,false));x.addView(space(8));x.addView(secondary("EXPORT A COPY NOW",v->host.uiExport()));root.addView(x);
    }

''')
t = replace_between(t, '    private void brandHeader(){', '    private void pageHeader(', '''    private void brandHeader(){
        LinearLayout top=row();top.setGravity(Gravity.CENTER_VERTICAL);
        TextView menu=glyph("☰",26,WHITE);menu.setOnClickListener(v->showMore());top.addView(menu,new LinearLayout.LayoutParams(dp(38),dp(72)));
        ImageView logo=new ImageView(host);logo.setImageResource(com.baileyanns.mileage.R.drawable.ba_l2_shiny_gold_logo);logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);logo.setBackgroundColor(WHITE);logo.setPadding(dp(5),dp(4),dp(5),dp(4));LinearLayout.LayoutParams lpLogo=new LinearLayout.LayoutParams(0,dp(72),1);lpLogo.setMargins(dp(4),0,dp(4),0);top.addView(logo,lpLogo);
        TextView more=glyph("⋮",28,WHITE);more.setOnClickListener(v->showMore());top.addView(more,new LinearLayout.LayoutParams(dp(38),dp(72)));
        root.addView(top);
        TextView sub=text("MILES TRACKER",14,GOLD,true);sub.setLetterSpacing(.16f);sub.setGravity(Gravity.CENTER);sub.setPadding(0,dp(7),0,0);root.addView(sub);
        TextView tag=text("Helping Families Through Life's Transitions.",12,WHITE,false);tag.setGravity(Gravity.CENTER);tag.setPadding(0,dp(2),0,dp(7));root.addView(tag);
        View line=new View(host);line.setBackgroundColor(GOLD);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.setMargins(0,dp(4),0,dp(12));root.addView(line,lp);
    }
''')
u.write_text(t)
