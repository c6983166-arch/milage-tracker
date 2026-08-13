from pathlib import Path
p=Path('app/src/main/java/com/baileyanns/mileage/MainActivity.java')
s=p.read_text()

s=s.replace(
    '    private LinearLayout root; private DatabaseHelper db; private Handler handler=new Handler(Looper.getMainLooper());\n',
    '    private LinearLayout root; private DatabaseHelper db; private PremiumUi premiumUi; private Handler handler=new Handler(Looper.getMainLooper());\n',1)
s=s.replace(
    '    private final Runnable refresh=()->{ if(root!=null && root.getTag()!=null && "dashboard".equals(root.getTag())) showDashboard(); };\n',
    '    private final Runnable refresh=()->{};\n',1)
s=s.replace(
    '@Override public void onCreate(Bundle b){super.onCreate(b);db=new DatabaseHelper(this);showDashboard();handleAction(getIntent());}',
    '@Override public void onCreate(Bundle b){super.onCreate(b);db=new DatabaseHelper(this);premiumUi=new PremiumUi(this,db);showDashboard();handleAction(getIntent());}',1)

def replace_between(text,start,end,repl):
    a=text.find(start)
    if a<0: raise SystemExit('Missing start marker: '+start)
    b=text.find(end,a)
    if b<0: raise SystemExit('Missing end marker: '+end)
    return text[:a]+repl+text[b:]

s=replace_between(s,'    private void showDashboard(){','    private void addDashboardTotals(){',
'''    private void showDashboard(){premiumUi.showDashboard();}\n\n''')
s=replace_between(s,'    private void showTrips(){','    private void editTrip(DatabaseHelper.Trip t){',
'''    private void showTrips(){premiumUi.showTrips();}\n\n''')
s=replace_between(s,'    private void showYearEnd(){','    private void exportCsv(){',
'''    private void showYearEnd(){premiumUi.showReports();}\n\n''')

insert='''\n    void uiNewTrip(){\n        if(db.activeTrip()!=null){premiumUi.showActive();return;}\n        new AlertDialog.Builder(this).setTitle("New Trip").setItems(new String[]{"Estate Miles","Business Miles"},(d,which)->{if(which==0)chooseEstateAndStart();else startDetails("BUSINESS",null);}).setNegativeButton("Cancel",null).show();\n    }\n    void uiEndTrip(){reviewEndTrip();}\n    void uiSettings(){showSettings();}\n    void uiSync(){syncNow();}\n    void uiExport(){exportCsv();}\n    void uiEdit(DatabaseHelper.Trip t){editTrip(t);}\n    void uiDelete(DatabaseHelper.Trip t){confirmDelete(t);}\n    void uiToast(String s){toast(s);}\n\n'''
marker='    private double number(String s){try{return Math.max(0,Double.parseDouble(s.trim()));}catch(Exception e){return 0;}}\n}'
if marker not in s: raise SystemExit('Missing final marker')
s=s.replace(marker,insert+marker,1)

p.write_text(s)
