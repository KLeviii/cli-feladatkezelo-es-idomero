# Smart Task Tracker & Time Logger

Egy minimalista, parancssorból (CLI) vezérelhető feladatkezelő és időmérő alkalmazás. A projekt célja egy letisztult, „okos” és gyors eszköz biztosítása a feladatok és az azokra fordított munkaidő hatékony nyomon követésére, grafikus felület túlzsúfoltsága nélkül.

## Főbb funkciók

* **Gyors feladatkezelés:** Feladatok létrehozása, áttekintése és menedzselése közvetlenül a terminálból.
* **Állapotkezelés:** A feladatok életciklusának követése (`TODO`, `IN_PROGRESS`, `DONE`).
* **Beépített időmérés:** Automatikus munkaidő-számítás a feladatok indításakor és leállításakor.
* **Adatperzisztencia:** Az adatok folyamatos és biztonságos mentése helyi fájlba, így a munkafolyamat az alkalmazás újraindítása után is folytatható.

## Architektúra és tervezés

A szoftver Java nyelven készül, fókuszban a tiszta kód, a SOLID elvek és a jól elkülönített, objektumorientált rétegek (üzleti logika, adatkezelés, felhasználói interakció) alkalmazásával.

A megvalósítási terv a [rendszerspecifikációban](system-specification.md) található.

## Futtatás

Egy új PowerShell-ablakban, a projekt gyökerében:

```powershell
javac -encoding UTF-8 -d out (Get-ChildItem -Recurse -Filter *.java src/main/java | Select-Object -ExpandProperty FullName)
java -cp out app.Application
```

Az alkalmazás a `data/tasks.json` fájlba ment. A használható parancsokat az indulás után a `help` jeleníti meg.
Windows Terminalból vagy PowerShellből indítva a `↑` és `↓` nyilakkal a korábbi parancsok visszahívhatók.

## Teszt

Az integrációs teszt külső tesztkeretrendszer nélkül, Java 17+-on fordítható és futtatható:

```powershell
javac -encoding UTF-8 -cp out -d out-test (Get-ChildItem -Recurse -Filter *.java src/test/java | Select-Object -ExpandProperty FullName)
java -ea -cp "out;out-test" app.IntegrationTest
```
