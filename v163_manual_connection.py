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
        root.addView(button("FIND BUSINESS MANAGER AUTOMATICALLY",v->SyncClient.discover(this,(ok,msg)->runOnUiThread(()->{status.setText(ok?"Business Manager: Connected at "+Prefs.serverUrl(this):"Business Manager: Automatic discovery failed");toast(msg);})),true));
        root.addView(space(12));

        root.addView(text("Manual Connection",18,Color.WHITE,true));
        root.addView(text("Use this when automatic discovery cannot find the Windows Business Manager. Enter the computer address shown by the Bailey Ann's address helper, for example 192.168.1.25.",13,Color.LTGRAY,false));
        EditText address=new EditText(this);address.setHint("Computer address, e.g. 192.168.1.25");address.setHintTextColor(Color.LTGRAY);address.setTextColor(Color.WHITE);address.setSingleLine(true);
        String existing=Prefs.serverUrl(this);if(existing.startsWith("http://")){existing=existing.substring(7);if(existing.endsWith(":8768"))existing=existing.substring(0,existing.length()-5);}address.setText(existing);root.addView(address);
        root.addView(button("SAVE MANUAL ADDRESS",v->{
            String raw=address.getText().toString().trim();
            if(raw.startsWith("http://"))raw=raw.substring(7);
            if(raw.startsWith("https://"))raw=raw.substring(8);
            int slash=raw.indexOf('/');if(slash>=0)raw=raw.substring(0,slash);
            if(raw.isEmpty()||raw.indexOf(' ')>=0||raw.indexOf('.')<0){toast("Enter the computer's local address, such as 192.168.1.25.");return;}
            String host=raw.contains(":")?raw:raw+":8768";
            String url="http://"+host;
            boolean ok=Prefs.get(this).edit().putString("server_url",url).commit();
            if(ok){status.setText("Business Manager: Manual address saved at "+url);toast("Manual Business Manager address saved.");}
            else toast("Manual address could not be saved.");
        },false));
        root.addView(space(12));

        EditText code=new EditText(this);code.setHint("6-digit pairing code shown in Business Manager");code.setHintTextColor(Color.LTGRAY);code.setTextColor(Color.WHITE);code.setInputType(2);code.setText(Prefs.pairingCode(this));root.addView(label("Pairing Code"));root.addView(code);
        TextView saved=text(Prefs.pairingCode(this).isEmpty()?"No pairing code saved":"Saved code: "+Prefs.pairingCode(this),13,Color.LTGRAY,false);root.addView(saved);

        root.addView(button("SAVE PAIRING CODE",v->{
            String value=code.getText().toString().trim();
            if(value.length()!=6){toast("Enter the 6-digit pairing code shown in Business Manager.");return;}
            for(int i=0;i<value.length();i++){if(!Character.isDigit(value.charAt(i))){toast("Pairing code must contain 6 numbers.");return;}}
            boolean ok=Prefs.get(this).edit().putString("pairing_code",value).commit();
            if(ok){saved.setText("Saved code: "+value);toast("Pairing code saved.");}
            else toast("Pairing code could not be saved. Try again.");
        },false));
        root.addView(space(8));
        root.addView(button("TEST CONNECTION & SYNC ESTATES",v->{
            String value=code.getText().toString().trim();
            if(value.length()!=6){toast("Save the 6-digit pairing code first.");return;}
            if(!value.equals(Prefs.pairingCode(this))){
                boolean ok=Prefs.get(this).edit().putString("pairing_code",value).commit();
                if(!ok){toast("Pairing code could not be saved.");return;}
                saved.setText("Saved code: "+value);
            }
            if(Prefs.serverUrl(this).isEmpty()){toast("Find Business Manager automatically or save its manual address first.");return;}
            toast("Testing Business Manager connection...");
            SyncClient.syncEstates(this,(ok,msg)->runOnUiThread(()->{toast(msg);status.setText(ok?"Business Manager: CONNECTED • Estates synced":"Business Manager: Connection failed at "+Prefs.serverUrl(this));}));
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
z = b.read_text().replace("versionCode 11", "versionCode 12", 1).replace("versionName '1.6.1'", "versionName '1.6.2'", 1)
b.write_text(z)
