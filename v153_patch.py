from pathlib import Path


def replace_between(text, start, end, replacement):
    a = text.find(start)
    if a < 0:
        raise SystemExit('Missing start marker: ' + start)
    b = text.find(end, a)
    if b < 0:
        raise SystemExit('Missing end marker: ' + end)
    return text[:a] + replacement + text[b:]

main = Path('app/src/main/java/com/baileyanns/mileage/MainActivity.java')
s = main.read_text()

# Gold BA-L2 logo must blend directly into the Bailey Navy screen.
s = s.replace('logo.setBackgroundColor(Color.WHITE);', '')

s = replace_between(s, '    private void chooseEstateAndStart(){', '    private void startDetails(String type,DatabaseHelper.Estate estate){', '''    private void chooseEstateAndStart(){
        ensureCorePermissions();
        List<DatabaseHelper.Estate> estates=db.estates();
        if(estates.isEmpty()){
            manualEstateStart();
            return;
        }
        String[] names=new String[estates.size()+1];
        names[0]="Enter Estate / Client Manually";
        for(int i=0;i<estates.size();i++)names[i+1]=estates.get(i).name+(estates.get(i).client.isEmpty()?"":" — "+estates.get(i).client);
        new AlertDialog.Builder(this).setTitle("Select Estate / Client").setItems(names,(d,which)->{
            if(which==0)manualEstateStart();
            else startDetails("ESTATE",estates.get(which-1));
        }).setNegativeButton("Cancel",null).show();
    }

    private void manualEstateStart(){
        final EditText name=new EditText(this);
        name.setHint("Estate / Client name");
        name.setSingleLine(true);
        int pad=dp(20);name.setPadding(pad,dp(8),pad,dp(8));
        new AlertDialog.Builder(this).setTitle("Estate / Client").setMessage("Business Manager sync is optional. Enter the Estate or Client name to continue.").setView(name)
            .setPositiveButton("CONTINUE",(d,w)->{
                String value=name.getText().toString().trim();
                if(value.isEmpty()){toast("Enter an Estate or Client name.");handler.postDelayed(this::manualEstateStart,250);return;}
                DatabaseHelper.Estate e=new DatabaseHelper.Estate();e.id="";e.name=value;e.client="";e.address="";e.status="Manual";
                startDetails("ESTATE",e);
            }).setNegativeButton("Cancel",null).show();
    }

''')

# Clarify that syncing is optional rather than a prerequisite.
s = s.replace('root.addView(text("Business Manager Sync",19,Color.WHITE,true));', 'root.addView(text("Business Manager Sync (Optional)",19,Color.WHITE,true));')
s = s.replace('root.addView(button("SAVE CODE & SYNC ESTATES",', 'root.addView(button("SAVE CODE & SYNC ESTATES",')

main.write_text(s)

ui = Path('app/src/main/java/com/baileyanns/mileage/PremiumUi.java')
t = ui.read_text()
t = t.replace('logo.setBackgroundColor(WHITE);', '')
t = t.replace('title("BUSINESS MANAGER SYNC")', 'title("BUSINESS MANAGER SYNC (OPTIONAL)")')
ui.write_text(t)
