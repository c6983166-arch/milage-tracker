from pathlib import Path
p=Path('app/src/main/java/com/baileyanns/mileage/MainActivity.java')
s=p.read_text()
bad='value.matches("\\d{6}")'
good='value.matches("\\\\d{6}")'
count=s.count(bad)
if count != 2:
    raise SystemExit(f'Expected 2 pairing regex markers, found {count}')
s=s.replace(bad,good)
p.write_text(s)
