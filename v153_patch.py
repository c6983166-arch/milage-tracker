from pathlib import Path


def replace_between(text, start, end, replacement):
    a = text.find(start)
    if a < 0:
        raise SystemExit('Missing start marker: ' + start)
    b = text.find(end, a)
    if b < 0:
        raise SystemExit('Missing end marker: ' + end)
    return text[:a] + replacement + text[b:]

# MainActivity: Business Manager sync is optional for Estate Miles and fallback header logo is transparent.
p = Path('app/src/main/java/com/baileyanns/mileage/MainActivity.java')
s = p.read_text()

s = replace_between(s, '    private void chooseEstateAndStart(){', '    private void startDetails(', '''    private void chooseEstateAndStart(){
        ensureCorePermissions();
        List<DatabaseHelper.Estate> estates=db.estates();
        List<String> choices=new ArrayList<>();
        choices.add("Enter Estate / Client Manually");
        for(DatabaseHelper.Estate e:estates) choices.add(e.name+(e.client.isEmpty()?"":" — "+e.client));
        new AlertDialog.Builder(this)
            .setTitle("Estate Miles")
            .setMessage(estates.isEmpty()?"Business Manager sync is optional. Enter the Estate / Client name manually to continue.":"Choose a synced Estate / Client, or enter one manually.")
            .setItems(choices.toArray(new String[0]),(d,which)->{
                if(which==0) manualEstateAndStart();
                else startDetails("ESTATE",estates.get(which-1));
            })
            .setNegativeButton("Cancel",null).show();
    }

    private void manualEstateAndStart(){
        EditText name=new EditText(this);
        name.setHint("Estate / Client name");
        name.setSingleLine(true);
        int pad=dp(20);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(pad,0,pad,0);box.addView(name);
        new AlertDialog.Builder(this)
            .setTitle("Enter Estate / Client")
            .setMessage("No Business Manager sync is required.")
            .setView(box)
            .setPositiveButton("CONTINUE",(d,w)->{
                String n=name.getText().toString().trim();
                if(n.isEmpty()){toast("Enter an Estate / Client name, then try again.");return;}
                DatabaseHelper.Estate e=new DatabaseHelper.Estate();e.id="";e.name=n;e.client="";e.address="";e.status="Manual";
                startDetails("ESTATE",e);
            })
            .setNegativeButton("Cancel",null).show();
    }

''')

if 'logo.setBackgroundColor(Color.WHITE);' not in s:
    raise SystemExit('Missing MainActivity logo background marker')
s=s.replace('logo.setBackgroundColor(Color.WHITE);','logo.setBackgroundColor(Color.TRANSPARENT);',1)
s=s.replace('root.addView(text("Business Manager Sync",19,Color.WHITE,true));','root.addView(text("Business Manager Sync (Optional)",19,Color.WHITE,true));',1)
p.write_text(s)

# Premium UI: exact BA-L2 logo appears on every app page and always sits directly on navy with transparency.
u = Path('app/src/main/java/com/baileyanns/mileage/PremiumUi.java')
t = u.read_text()
t = t.replace('title("BUSINESS MANAGER SYNC")','title("BUSINESS MANAGER SYNC (OPTIONAL)")',1)
t = t.replace('"Connect in More"','"Optional - connect in More"',1)

t = replace_between(t, '    private void brandHeader(){', '    private LinearLayout bottom(', '''    private void brandHeader(){
        LinearLayout top=row();top.setGravity(Gravity.CENTER_VERTICAL);
        TextView menu=glyph("☰",26,WHITE);menu.setOnClickListener(v->showMore());top.addView(menu,new LinearLayout.LayoutParams(dp(38),dp(76)));
        ImageView logo=new ImageView(host);logo.setImageResource(com.baileyanns.mileage.R.drawable.ba_l2_shiny_gold_logo);logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);logo.setBackgroundColor(Color.TRANSPARENT);logo.setPadding(dp(4),dp(2),dp(4),dp(2));LinearLayout.LayoutParams lpLogo=new LinearLayout.LayoutParams(0,dp(76),1);lpLogo.setMargins(dp(4),0,dp(4),0);top.addView(logo,lpLogo);
        TextView more=glyph("⋮",28,WHITE);more.setOnClickListener(v->showMore());top.addView(more,new LinearLayout.LayoutParams(dp(38),dp(76)));
        root.addView(top);
        TextView sub=text("MILES TRACKER",14,GOLD,true);sub.setLetterSpacing(.16f);sub.setGravity(Gravity.CENTER);sub.setPadding(0,dp(5),0,0);root.addView(sub);
        TextView tag=text("Helping Families Through Life's Transitions.",12,WHITE,false);tag.setGravity(Gravity.CENTER);tag.setPadding(0,dp(2),0,dp(7));root.addView(tag);
        View line=new View(host);line.setBackgroundColor(GOLD);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.setMargins(0,dp(4),0,dp(12));root.addView(line,lp);
    }

    private void pageHeader(String s){
        LinearLayout top=row();top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=glyph("‹",34,WHITE);back.setOnClickListener(v->showDashboard());top.addView(back,new LinearLayout.LayoutParams(dp(44),dp(70)));
        ImageView logo=new ImageView(host);logo.setImageResource(com.baileyanns.mileage.R.drawable.ba_l2_shiny_gold_logo);logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);logo.setBackgroundColor(Color.TRANSPARENT);logo.setPadding(dp(4),dp(2),dp(4),dp(2));top.addView(logo,new LinearLayout.LayoutParams(0,dp(70),1));
        TextView more=glyph("⋮",28,GOLD);more.setOnClickListener(v->showMore());top.addView(more,new LinearLayout.LayoutParams(dp(44),dp(70)));
        root.addView(top);
        TextView heading=text(s,18,WHITE,true);heading.setGravity(Gravity.CENTER);heading.setPadding(0,dp(4),0,dp(8));root.addView(heading);
        View line=new View(host);line.setBackgroundColor(GOLD);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.setMargins(0,0,0,dp(10));root.addView(line,lp);
    }

''')
u.write_text(t)
