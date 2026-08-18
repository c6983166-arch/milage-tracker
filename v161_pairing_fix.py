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

s = replace_between(s, '    private void showSettings(){', '    private void chooseCar(){', '''    private void showSettings(){
        base("settings","Settings");root.addView(back());
        root.addView(text("Business Manager Sync (Optional)",19,Color.WHITE,true));
        TextView status=text(Prefs.serverUrl(this).isEmpty()?"Business Manager: Not connected":"Business Manager: Connected at "+Prefs.serverUrl(this),14,Color.LTGRAY,false);root.addView(status);
        root.addView(button("FIND BUSINESS MANAGER",v->SyncClient.discover(this,(ok,msg)->runOnUiThread(()->{status.setText(ok?"Business Manager: Connected":"Business Manager: Not connected");toast(msg);})),true));
        root.addView(space(10));

        EditText code=new EditText(this);code.setHint("6-digit pairing code shown in Business Manager");code.setHintTextColor(Color.LTGRAY);code.setTextColor(Color.WHITE);code.setInputType(2);code.setText(Prefs.pairingCode(this));root.addView(label("Pairing Code"));root.addView(code);
        TextView saved=text(Prefs.pairingCode(this).isEmpty()?"No pairing code saved":"Saved code: "+Prefs.pairingCode(this),13,Color.LTGRAY,false);root.addView(saved);

        root.addView(button("SAVE PAIRING CODE",v->{
            String value=code.getText().toString().trim();
            if(!value.matches("\\d{6}")){toast("Enter the 6-digit pairing code shown in Business Manager.");return;}
            boolean ok=Prefs.get(this).edit().putString("pairing_code",value).commit();
            if(ok){saved.setText("Saved code: "+value);toast("Pairing code saved.");}
            else toast("Pairing code could not be saved. Try again.");
        },false));
        root.addView(space(8));
        root.addView(button("SYNC ESTATES NOW",v->{
            String value=code.getText().toString().trim();
            if(!value.matches("\\d{6}")){toast("Save the 6-digit pairing code first.");return;}
            if(!value.equals(Prefs.pairingCode(this))){
                boolean ok=Prefs.get(this).edit().putString("pairing_code",value).commit();
                if(!ok){toast("Pairing code could not be saved.");return;}
                saved.setText("Saved code: "+value);
            }
            if(Prefs.serverUrl(this).isEmpty()){toast("Tap FIND BUSINESS MANAGER first, then sync again.");return;}
            toast("Syncing estate list...");
            SyncClient.syncEstates(this,(ok,msg)->runOnUiThread(()->{toast(msg);if(ok)status.setText("Business Manager: Connected • Estates synced");}));
        },true));

        root.addView(space(20));root.addView(text("Manual Trip Tracking",19,Color.WHITE,true));
        root.addView(text("Trips start only when you tap START TRIP and end only when you tap END TRIP. There is no automatic vehicle detection or automatic trip start/stop.",14,Color.LTGRAY,false));
        root.addView(button("ALLOW LOCATION",v->ensureCorePermissions(),false));
        root.addView(space(20));root.addView(text("Mileage Backup",19,Color.WHITE,true));
        root.addView(text("A CSV backup is refreshed after trips are saved, edited, deleted, or synced.",14,Color.LTGRAY,false));
        root.addView(button("EXPORT A COPY NOW",v->exportCsv(),false));
    }

''')
p.write_text(s)

b = Path('app/build.gradle')
z = b.read_text().replace("versionCode 10", "versionCode 11", 1).replace("versionName '1.6.0'", "versionName '1.6.1'", 1)
b.write_text(z)
