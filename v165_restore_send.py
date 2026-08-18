from pathlib import Path

p = Path('app/src/main/java/com/baileyanns/mileage/MainActivity.java')
s = p.read_text()

if 'private void sendTripToBusinessManager(DatabaseHelper.Trip t)' not in s:
    marker = '    private void showTrips(){'
    if marker not in s:
        raise SystemExit('Missing showTrips marker')
    method = '''    private void sendTripToBusinessManager(DatabaseHelper.Trip t){
        if(t==null)return;
        if(Prefs.serverUrl(this).isEmpty()||Prefs.pairingCode(this).isEmpty()){
            toast("Trip saved as Not Sent. Connect to Business Manager when ready.");
            showDashboard();return;
        }
        toast("Sending trip to Business Manager...");
        SyncClient.syncTrip(this,t.id,(ok,msg)->runOnUiThread(()->{toast(msg);if(ok)syncEstatesQuietly();showDashboard();}));
    }

'''
    s = s.replace(marker, method + marker, 1)

p.write_text(s)
