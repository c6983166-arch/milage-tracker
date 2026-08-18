from pathlib import Path


def replace_between(text, start, end, replacement):
    a = text.find(start)
    if a < 0:
        raise SystemExit('Missing start marker: ' + start)
    b = text.find(end, a)
    if b < 0:
        raise SystemExit('Missing end marker: ' + end)
    return text[:a] + replacement + text[b:]

# -----------------------------------------------------------------------------
# MainActivity: manual START/END flow, optional Estate after trip, no odometer UI,
# explicit Send to Business Manager action, and quiet estate-list refresh.
# -----------------------------------------------------------------------------
p = Path('app/src/main/java/com/baileyanns/mileage/MainActivity.java')
s = p.read_text()

old_create = '@Override public void onCreate(Bundle b){super.onCreate(b);db=new DatabaseHelper(this);premiumUi=new PremiumUi(this,db);showDashboard();handleAction(getIntent());}'
new_create = '@Override public void onCreate(Bundle b){super.onCreate(b);db=new DatabaseHelper(this);premiumUi=new PremiumUi(this,db);showDashboard();syncEstatesQuietly();handleAction(getIntent());}'
if old_create not in s:
    raise SystemExit('Missing onCreate marker')
s = s.replace(old_create, new_create, 1)

# Car-related legacy entry points now start the same manual trip flow.
s = replace_between(s, '    private void handleAction(Intent i){', '    private void base(', '''    private void handleAction(Intent i){
        if(i==null)return;
        if(ACTION_CAR_ESTATE.equals(i.getAction())||ACTION_CAR_BUSINESS.equals(i.getAction()))uiNewTrip();
        else if(ACTION_REVIEW_END.equals(i.getAction()))reviewEndTrip();
    }

''')

# Remove the old start-time Estate / odometer flow. START TRIP begins immediately.
s = replace_between(s, '    private void chooseEstateAndStart(){', '    private void startTrip(', '''    private void chooseEstateAndStart(){uiNewTrip();}

    private void manualEstateAndStart(){uiNewTrip();}

    private void startDetails(String type,DatabaseHelper.Estate estate){
        startTrip(type,estate,"",0);
    }

''')

# Review after driving: Estate is optional, purpose and notes are entered here.
s = replace_between(s, '    private void reviewEndTrip(){', '    private void showTrips(){', '''    private void reviewEndTrip(){
        DatabaseHelper.Trip t=db.activeTrip();
        if(t==null){toast("There is no active trip.");showDashboard();return;}

        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),0,dp(20),0);
        box.addView(text(t.date+"   Started "+t.startTime,13,Color.DKGRAY,false));
        box.addView(text(String.format(Locale.US,"Current GPS Mileage: %.1f miles",t.miles),22,GOLD,true));

        List<DatabaseHelper.Estate> estates=db.estates();
        List<String> estateChoices=new ArrayList<>();
        estateChoices.add("No Estate / General Business");
        for(DatabaseHelper.Estate e:estates)estateChoices.add(e.name+(e.client.isEmpty()?"":" — "+e.client));
        Spinner estate=new Spinner(this);estate.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,estateChoices));
        box.addView(label("Estate (Optional)"));box.addView(estate);
        if(estates.isEmpty())box.addView(text("No synced estates yet. You can still save this as General Business.",12,Color.DKGRAY,false));

        String[] purposes={"No purpose / Skip","Estate Sale","Consultation","Bank","Supplies","Advertising / Signs","Client Meeting","Donation Run","Other / Custom"};
        Spinner purpose=new Spinner(this);purpose.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,purposes));
        EditText custom=new EditText(this);custom.setHint("Custom purpose (only if Other / Custom)");custom.setSingleLine(true);
        EditText notes=new EditText(this);notes.setHint("Trip notes (optional)");notes.setMinLines(2);notes.setMaxLines(4);
        box.addView(label("Trip Purpose"));box.addView(purpose);box.addView(custom);box.addView(label("Notes"));box.addView(notes);

        new AlertDialog.Builder(this).setTitle("Review Trip Before Saving").setView(box)
            .setPositiveButton("END & REVIEW",(d,w)->{
                DatabaseHelper.Estate e=estate.getSelectedItemPosition()>0?estates.get(estate.getSelectedItemPosition()-1):null;
                String selected=String.valueOf(purpose.getSelectedItem());
                String pp="No purpose / Skip".equals(selected)?"":"Other / Custom".equals(selected)?custom.getText().toString().trim():selected;
                String tt=e==null?"BUSINESS":"ESTATE";
                db.updateTripDetails(t.id,tt,e==null?"":e.id,e==null?"":e.name,pp,notes.getText().toString().trim());
                startService(new Intent(this,MileageTrackingService.class).setAction(MileageTrackingService.ACTION_STOP));
                toast("Trip ended and saved.");
                handler.postDelayed(()->showTripSummaryAfterEnd(t.id),900);
            })
            .setNegativeButton("KEEP TRACKING",null).show();
    }

    private void showTripSummaryAfterEnd(String tripId){
        DatabaseHelper.Trip t=db.tripById(tripId);if(t==null){showDashboard();return;}
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),0,dp(20),0);
        box.addView(text("TRIP SUMMARY",15,NAVY,true));
        box.addView(text(String.format(Locale.US,"%.1f Miles",t.miles),30,GOLD,true));
        box.addView(text("Date: "+t.date,14,Color.DKGRAY,false));
        box.addView(text("Start: "+t.startTime+"     End: "+t.endTime,14,Color.DKGRAY,false));
        box.addView(text("Estate: "+(t.estateName.isEmpty()?"No Estate / General Business":t.estateName),14,Color.DKGRAY,false));
        if(!t.purpose.isEmpty())box.addView(text("Purpose: "+t.purpose,14,Color.DKGRAY,false));
        if(t.notes!=null&&!t.notes.isEmpty())box.addView(text("Notes: "+t.notes,14,Color.DKGRAY,false));
        box.addView(text("Status: "+(t.synced==1?"Synced":"Not Sent"),14,t.synced==1?Color.rgb(30,130,70):GOLD,true));
        new AlertDialog.Builder(this).setTitle("Trip Saved").setView(box)
            .setPositiveButton("SEND TO BUSINESS MANAGER",(d,w)->sendTripToBusinessManager(t))
            .setNeutralButton("EDIT BEFORE SENDING",(d,w)->editTrip(t))
            .setNegativeButton("SAVE FOR LATER",(d,w)->showDashboard()).show();
    }

    private void sendTripToBusinessManager(DatabaseHelper.Trip t){
        if(t==null)return;
        if(Prefs.serverUrl(this).isEmpty()||Prefs.pairingCode(this).isEmpty()){
            toast("Trip saved as Not Sent. Connect to Business Manager when ready.");
            showDashboard();return;
        }
        toast("Sending trip to Business Manager...");
        SyncClient.syncTrip(this,t.id,(ok,msg)->runOnUiThread(()->{toast(msg);if(ok)syncEstatesQuietly();showDashboard();}));
    }

''')

# Edit screen keeps Estate optional and removes all odometer fields.
s = replace_between(s, '    private void editTrip(DatabaseHelper.Trip t){', '    private void confirmDelete(', '''    private void editTrip(DatabaseHelper.Trip t){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),0,dp(20),0);
        List<DatabaseHelper.Estate> estates=db.estates();List<String> estateNames=new ArrayList<>();estateNames.add("No Estate / General Business");int selectedEstate=0;
        for(int i=0;i<estates.size();i++){DatabaseHelper.Estate e=estates.get(i);estateNames.add(e.name+(e.client.isEmpty()?"":" — "+e.client));if(e.id.equals(t.estateId))selectedEstate=i+1;}
        Spinner estate=new Spinner(this);estate.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,estateNames));estate.setSelection(selectedEstate);
        EditText purpose=new EditText(this);purpose.setHint("Purpose");purpose.setText(t.purpose);
        EditText notes=new EditText(this);notes.setHint("Notes");notes.setText(t.notes==null?"":t.notes);notes.setMinLines(2);notes.setMaxLines(4);
        EditText miles=new EditText(this);miles.setHint("Miles");miles.setInputType(8194);miles.setText(String.format(Locale.US,"%.1f",t.miles));
        box.addView(label("Estate (Optional)"));box.addView(estate);box.addView(label("Purpose"));box.addView(purpose);box.addView(label("Notes"));box.addView(notes);box.addView(label("Miles"));box.addView(miles);
        new AlertDialog.Builder(this).setTitle("Edit Saved Trip").setView(box).setPositiveButton("SAVE CHANGES",(d,w)->{
            DatabaseHelper.Estate e=estate.getSelectedItemPosition()>0?estates.get(estate.getSelectedItemPosition()-1):null;
            String tt=e==null?"BUSINESS":"ESTATE";
            db.updateTrip(t.id,tt,e==null?"":e.id,e==null?"":e.name,purpose.getText().toString().trim(),notes.getText().toString().trim(),number(miles.getText().toString()));
            toast("Trip updated. Status changed to Not Sent.");showTrips();
        }).setNegativeButton("Cancel",null).show();
    }

''')

# Add quiet Estate refresh and simplify the public UI callbacks used by PremiumUi.
old_ui = '''    void uiNewTrip(){
        if(db.activeTrip()!=null){premiumUi.showActive();return;}
        new AlertDialog.Builder(this).setTitle("New Trip").setItems(new String[]{"Estate Miles","Business Miles"},(d,which)->{if(which==0)chooseEstateAndStart();else startDetails("BUSINESS",null);}).setNegativeButton("Cancel",null).show();
    }
    void uiEndTrip(){reviewEndTrip();}
    void uiSettings(){showSettings();}
    void uiSync(){syncNow();}
    void uiExport(){exportCsv();}
    void uiEdit(DatabaseHelper.Trip t){editTrip(t);}
    void uiDelete(DatabaseHelper.Trip t){confirmDelete(t);}
    void uiToast(String s){toast(s);}
'''
new_ui = '''    private void syncEstatesQuietly(){
        if(Prefs.serverUrl(this).isEmpty()||Prefs.pairingCode(this).isEmpty())return;
        SyncClient.syncEstates(this,(ok,msg)->{});
    }

    void uiNewTrip(){
        if(db.activeTrip()!=null){premiumUi.showActive();return;}
        startTrip("BUSINESS",null,"",0);
    }
    void uiEndTrip(){reviewEndTrip();}
    void uiSettings(){showSettings();}
    void uiSync(){syncNow();}
    void uiSend(DatabaseHelper.Trip t){sendTripToBusinessManager(t);}
    void uiExport(){exportCsv();}
    void uiEdit(DatabaseHelper.Trip t){editTrip(t);}
    void uiDelete(DatabaseHelper.Trip t){confirmDelete(t);}
    void uiToast(String s){toast(s);}
'''
if old_ui not in s:
    raise SystemExit('Missing PremiumUi callback marker')
s = s.replace(old_ui, new_ui, 1)
p.write_text(s)

# -----------------------------------------------------------------------------
# Database: add Notes, keep old odometer columns only for backward compatibility,
# but remove odometer fields from user-facing CSV and new edit/send workflows.
# -----------------------------------------------------------------------------
d = Path('app/src/main/java/com/baileyanns/mileage/DatabaseHelper.java')
x = d.read_text()
x = x.replace('public String id, tripType, estateId, estateName, purpose, date, startTime, endTime;', 'public String id, tripType, estateId, estateName, purpose, notes, date, startTime, endTime;', 1)
x = x.replace('super(c, "ba_mileage.db", null, 2);', 'super(c, "ba_mileage.db", null, 3);', 1)
x = x.replace('purpose TEXT, date TEXT', 'purpose TEXT, notes TEXT, date TEXT', 1)
old_upgrade = '''    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion < 2){
            db.execSQL("ALTER TABLE trips ADD COLUMN start_odometer REAL DEFAULT 0");
            db.execSQL("ALTER TABLE trips ADD COLUMN end_odometer REAL DEFAULT 0");
        }
    }
'''
new_upgrade = '''    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion < 2){
            db.execSQL("ALTER TABLE trips ADD COLUMN start_odometer REAL DEFAULT 0");
            db.execSQL("ALTER TABLE trips ADD COLUMN end_odometer REAL DEFAULT 0");
        }
        if(oldVersion < 3){
            db.execSQL("ALTER TABLE trips ADD COLUMN notes TEXT DEFAULT ''");
        }
    }
'''
if old_upgrade not in x:
    raise SystemExit('Missing DB upgrade marker')
x = x.replace(old_upgrade, new_upgrade, 1)
x = x.replace('v.put("purpose", purpose); v.put("date", date);', 'v.put("purpose", purpose); v.put("notes", ""); v.put("date", date);', 1)

insert_marker = '    public void deleteTrip(String id){getWritableDatabase().delete("trips","id=? AND active=0",new String[]{id});writeAutomaticBackup();}\n'
insert_methods = '''    public void updateTripDetails(String id,String tripType,String estateId,String estateName,String purpose,String notes){
        ContentValues v=new ContentValues();v.put("trip_type",tripType);v.put("estate_id",estateId);v.put("estate_name",estateName);v.put("purpose",purpose);v.put("notes",notes);v.put("synced",0);
        getWritableDatabase().update("trips",v,"id=?",new String[]{id});
    }
    public void updateTrip(String id,String tripType,String estateId,String estateName,String purpose,String notes,double miles){
        ContentValues v=new ContentValues();v.put("trip_type",tripType);v.put("estate_id",estateId);v.put("estate_name",estateName);v.put("purpose",purpose);v.put("notes",notes);v.put("miles",Math.max(0,miles));v.put("synced",0);
        getWritableDatabase().update("trips",v,"id=?",new String[]{id});writeAutomaticBackup();
    }
'''
if insert_marker not in x:
    raise SystemExit('Missing deleteTrip marker')
x = x.replace(insert_marker, insert_methods + insert_marker, 1)

x = replace_between(x, '    public String csv(){', '    public File writeAutomaticBackup(){', '''    public String csv(){
        StringBuilder b=new StringBuilder("Date,Type,Estate,Purpose,Notes,Start Time,End Time,Miles,Sync Status,Start Latitude,Start Longitude,End Latitude,End Longitude,Trip ID\\n");
        for(Trip t:allTrips()){
            b.append(q(t.date)).append(',').append(q("ESTATE".equals(t.tripType)?"Estate Miles":"Business Miles")).append(',').append(q(t.estateName)).append(',').append(q(t.purpose)).append(',').append(q(t.notes)).append(',').append(q(t.startTime)).append(',').append(q(t.endTime)).append(',').append(fmt(t.miles)).append(',').append(q(t.synced==1?"Synced":"Not Sent")).append(',').append(t.startLat).append(',').append(t.startLon).append(',').append(t.endLat).append(',').append(t.endLon).append(',').append(q(t.id)).append('\\n');
        }
        return b.toString();
    }
''')
x = x.replace('t.id=s(c,"id"); t.tripType=s(c,"trip_type"); t.estateId=s(c,"estate_id"); t.estateName=s(c,"estate_name"); t.purpose=s(c,"purpose"); t.date=s(c,"date");', 't.id=s(c,"id"); t.tripType=s(c,"trip_type"); t.estateId=s(c,"estate_id"); t.estateName=s(c,"estate_name"); t.purpose=s(c,"purpose"); t.notes=s(c,"notes"); t.date=s(c,"date");', 1)
d.write_text(x)

# -----------------------------------------------------------------------------
# Sync client: support sending one selected trip and include Notes.
# -----------------------------------------------------------------------------
c = Path('app/src/main/java/com/baileyanns/mileage/SyncClient.java')
y = c.read_text()
y = replace_between(y, '    public static void syncTrips(Context c, Callback cb){', '    private static String base(', '''    public static void syncTrips(Context c, Callback cb){ new Thread(()->{ try{
        DatabaseHelper db=new DatabaseHelper(c); List<DatabaseHelper.Trip> trips=db.unsyncedTrips();
        if(trips.isEmpty()){cb.done(true,"All trips are already synced.");return;}
        String base=base(c),code=Prefs.pairingCode(c); if(base.isEmpty()||code.isEmpty())throw new Exception("Find Business Manager and enter the pairing code first.");
        JSONObject body=new JSONObject(); body.put("code",code); JSONArray a=new JSONArray(); for(DatabaseHelper.Trip t:trips)a.put(tripJson(t)); body.put("trips",a);
        JSONObject r=post(base+"/api/trips",body); if(!r.optBoolean("ok"))throw new Exception(r.optString("error","Sync failed"));
        JSONArray accepted=r.optJSONArray("accepted");if(accepted==null){accepted=new JSONArray();for(DatabaseHelper.Trip t:trips)accepted.put(t.id);}db.markSynced(accepted);
        cb.done(true,r.optInt("count",trips.size())+" trip(s) synced to Business Manager.");
    }catch(Exception e){cb.done(false,e.getMessage());} }).start(); }

    public static void syncTrip(Context c,String tripId,Callback cb){ new Thread(()->{ try{
        DatabaseHelper db=new DatabaseHelper(c);DatabaseHelper.Trip t=db.tripById(tripId);if(t==null)throw new Exception("Trip was not found.");if(t.synced==1){cb.done(true,"Trip is already synced.");return;}
        String base=base(c),code=Prefs.pairingCode(c);if(base.isEmpty()||code.isEmpty())throw new Exception("Find Business Manager and enter the pairing code first.");
        JSONObject body=new JSONObject();body.put("code",code);JSONArray a=new JSONArray();a.put(tripJson(t));body.put("trips",a);
        JSONObject r=post(base+"/api/trips",body);if(!r.optBoolean("ok"))throw new Exception(r.optString("error","Send failed"));
        JSONArray accepted=r.optJSONArray("accepted");if(accepted==null){accepted=new JSONArray();accepted.put(t.id);}db.markSynced(accepted);
        cb.done(true,"Trip synced to Business Manager.");
    }catch(Exception e){cb.done(false,e.getMessage());} }).start(); }

    private static JSONObject tripJson(DatabaseHelper.Trip t)throws Exception{
        JSONObject o=new JSONObject();o.put("id",t.id);o.put("tripType",t.tripType);o.put("estateId",t.estateId);o.put("estateName",t.estateName);o.put("purpose",t.purpose);o.put("notes",t.notes==null?"":t.notes);o.put("date",t.date);o.put("startTime",t.startTime);o.put("endTime",t.endTime);o.put("miles",t.miles);o.put("startLat",t.startLat);o.put("startLon",t.startLon);o.put("endLat",t.endLat);o.put("endLon",t.endLon);return o;
    }

''')
c.write_text(y)

# -----------------------------------------------------------------------------
# Premium UI labels and per-trip Send action.
# -----------------------------------------------------------------------------
u = Path('app/src/main/java/com/baileyanns/mileage/PremiumUi.java')
t = u.read_text()
t = t.replace('u>0?"Not Synced":"Synced"', 'u>0?"Not Sent":"Synced"', 1)
t = t.replace('u+" trip"+(u==1?"":"s")+" need to be synced"', 'u+" trip"+(u==1?"":"s")+" waiting to send"', 1)
t = t.replace('t.synced==1?"Synced":"Not Synced"', 't.synced==1?"Synced":"Not Sent"', 1)
t = t.replace('s.addView(primary("SYNC NOW",v->host.uiSync()));', 's.addView(primary(db.unsyncedCount()>0?"SEND ALL UNSENT":"SYNC NOW",v->host.uiSync()));', 1)
old_menu = '    private void menu(View anchor,DatabaseHelper.Trip t){PopupMenu p=new PopupMenu(host,anchor);p.getMenu().add("Edit Trip");p.getMenu().add("Delete Trip");p.setOnMenuItemClickListener(item->{if("Edit Trip".contentEquals(item.getTitle()))host.uiEdit(t);else host.uiDelete(t);return true;});p.show();}'
new_menu = '    private void menu(View anchor,DatabaseHelper.Trip t){PopupMenu p=new PopupMenu(host,anchor);if(t.synced==0)p.getMenu().add("Send to Business Manager");p.getMenu().add("Edit Trip");p.getMenu().add("Delete Trip");p.setOnMenuItemClickListener(item->{String a=String.valueOf(item.getTitle());if("Send to Business Manager".equals(a))host.uiSend(t);else if("Edit Trip".equals(a))host.uiEdit(t);else host.uiDelete(t);return true;});p.show();}'
if old_menu not in t:
    raise SystemExit('Missing PremiumUi trip menu marker')
t = t.replace(old_menu, new_menu, 1)
u.write_text(t)

# Version used by this trial build.
b = Path('app/build.gradle')
z = b.read_text().replace("versionCode 9","versionCode 10",1).replace("versionName '1.5.3'","versionName '1.6.0'",1)
b.write_text(z)
