**Szerepkör:** Senior Java Szoftverarchitekt és Rendszertervező.

**Kontextus:** Egy egyetemi/iskolai projektmunkán dolgozom egyedül, kétszemélyes csapatok ellen. A cél egy olyan Java alapú, konzolos (CLI) alkalmazás tervezése, amely technológiailag letisztult, „okos”, és robusztus architektúrával rendelkezik, hogy ellensúlyozza a grafikus felület (GUI) hiányát. A kódnak a SOLID elveket kell követnie, és minimum 5 jól elkülönülő osztályból kell állnia.

**A projekt:** „Smart Task Tracker & Time Logger” – Egy terminálból vezérelhető, fejlesztőknek szánt minimalista feladatkezelő és időmérő alkalmazás.

**Feladatod:** Tervezd meg a rendszert és írd meg a részletes specifikációt az alábbi szempontok alapján! A válaszod legyen strukturált, és térjen ki a követelményekre, az üzleti logikára és az osztálydiagram-szintű tervezésre.

**1. Funkcionális követelmények (Core Features):**

* **Feladatok kezelése (CRUD):** Új feladat felvétele, listázás, törlés.
* **Státuszmenedzsment:** A feladatok állapotának változtatása (`TODO`, `IN_PROGRESS`, `DONE`). Egyszerre csak egy feladat lehet `IN_PROGRESS`.
* **Időmérés (Time Tracking):** Ha egy feladat `IN_PROGRESS` státuszba kerül, induljon el a munkaidő mérése. Ha kikerül belőle, az eltelt idő adódjon hozzá a feladathoz (perc vagy másodperc alapú nyilvántartás).
* **Perzisztencia:** Az adatok mentése és betöltése (CSV vagy JSON formátumban) az alkalmazás indulásakor és leállításakor/módosításkor.
* **CLI Parser:** Interaktív parancssoros menü vagy parancs-alapú vezérlés (pl. `add "Tervezés"`, `start 1`, `list`).

**2. Nem funkcionális követelmények:**

* Nyelv: Java (17+ verzió).
* Függőségek: Lehetőleg beépített könyvtárak használata, maximum egy JSON parser (pl. Gson/Jackson) engedélyezett a perzisztenciához.
* Hibakezelés: Érvénytelen parancsok, nem létező ID-k, sérült mentési fájlok elegáns kezelése (Exception handling).

**3. Elvárt kimenet tőled (Kérlek, ezeket dolgozd ki részletesen!):**

* **Rendszerarchitektúra és működés:** Hogyan épül fel a rendszer? Milyen rétegek vannak (pl. UI réteg, üzleti logika réteg, adatréteg)?
* **Osztályterv (Class Design):** Sorold fel a szükséges osztályokat (minimum 5, de inkább több). Minden osztálynál add meg a felelősségét (Single Responsibility Principle), a főbb adattagokat (mezőket) és a publikus metódusokat (szignatúrákkal). *Kiemelt osztályok, amiket várok: `Task`, `TaskManager`, `TimeTracker`, `StorageHandler`, `CommandParser`.*
* **Üzleti logika folyamata:** Írd le lépésről lépésre, mi történik a háttérben, amikor a felhasználó kiadja a `start <id>` parancsot, majd később a `stop <id>` parancsot. Hogyan működik az időmérés anélkül, hogy blokkolná a konzolt (szükséges-e szálkezelés, vagy elég a timestamp-ek kivonása egymásból)?
* **Adatszerkezet:** Mutass egy példát a fájlformátumra (pl. egy minta JSON struktúra), ahogy a feladatokat tárolni fogja a program.

Kérlek, professzionális, tiszta és implementálásra kész specifikációt adj, ami alapján egy junior/medior fejlesztő azonnal el tudja kezdeni a kódolást!
