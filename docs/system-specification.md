# Smart Task Tracker & Time Logger – rendszerspecifikáció

## 1. Cél és hatókör

A Smart Task Tracker & Time Logger egy helyben futó, interaktív Java CLI alkalmazás. Fejlesztői feladatok rögzítésére, az aktuálisan végzett munka mérésére és az adatok tartós tárolására szolgál. A program egyetlen aktív feladatot enged, minden módosítás után ment, és hiba esetén érthető visszajelzést ad anélkül, hogy elveszítené a már betöltött adatokat.

Nem része az első kiadásnak a többfelhasználós működés, a hálózati szinkronizáció, a határidőkezelés és a grafikus felület.

## 2. Követelmények

### 2.1 Funkcionális követelmények

| Azonosító | Követelmény | Elfogadási feltétel |
|---|---|---|
| FR-01 | Feladat felvétele | Az `add "Cím"` új, egyedi azonosítójú `TODO` feladatot hoz létre. |
| FR-02 | Feladatok listázása | A `list` kiírja az ID-t, címet, státuszt és az összesített munkaidőt. |
| FR-03 | Feladat indítása | A `start <id>` az adott feladatot aktívvá teszi; másik aktív feladat nem maradhat. |
| FR-04 | Feladat leállítása | A `stop <id>` lezárja az aktív mérést, és hozzáadja az eltelt másodperceket. |
| FR-05 | Státuszváltás | A `status <id> <TODO|IN_PROGRESS|DONE>` a megengedett állapotátmeneteket hajtja végre. |
| FR-06 | Törlés | A `delete <id>` eltávolít egy nem aktív feladatot. Aktív feladat törlése előtt a felhasználónak le kell állítania azt. |
| FR-07 | Perzisztencia | Induláskor betöltés, minden sikeres módosítás után atomi mentés történik JSON-fájlba. |
| FR-08 | CLI | A `help` felsorolja a parancsokat; az `exit` kilépés előtt ment. |

### 2.2 Nem funkcionális követelmények

* Java 25 LTS az ajánlott futtatókörnyezet; a Java 17-es nyelvi alapvonal is elegendő a tervezett kódhoz.
* Külső függőség nélkül is megvalósítható; JSON-hoz legfeljebb egy könyvtár (például Gson) használható.
* A domainréteg nem függhet a konzoltól vagy a fájlformátumtól.
* Hibás parancs, érvénytelen ID és sérült mentés nem állíthatja le az interaktív ciklust.
* A mentés UTF-8 kódolású, a dátumok ISO-8601 formátumúak.

## 3. Rétegzett architektúra

```text
Felhasználó
    │ stdin / stdout
    ▼
CommandParser ──► TaskManager ──► TimeTracker
                      │                 │
                      ▼                 ▼
                    Task ───────────► TaskStatus
                      │
                      ▼
                StorageHandler
                      │
                      ▼
               data/tasks.json
```

| Réteg | Elemei | Felelősség |
|---|---|---|
| Belépési pont és UI | `Application`, `CommandParser` | Konzolciklus, bemenet feldolgozása, válaszok kiírása. |
| Üzleti logika | `TaskManager`, `TimeTracker` | Invariánsok, állapotátmenetek és időelszámolás. |
| Domain | `Task`, `TaskStatus` | Feladatállapot és üzleti adatok reprezentálása. |
| Adatkezelés | `TaskRepository`, `StorageHandler` | Mentési szerződés és JSON-be/kiolvasás. |

### Tervezési elvek

* **SRP:** a parancsértelmezés, üzleti szabályok, időszámítás és fájlműveletek külön osztályokban vannak.
* **OCP/DIP:** a `TaskManager` a `TaskRepository` absztrakciót ismeri, ezért a JSON-tároló később lecserélhető.
* **ISP:** a repository szerződés csak a szükséges `load` és `save` műveleteket teszi közzé.
* **Encapsulation:** a `Task` csak metódusokon át módosítható; a teljes munkaidő nem írható felül tetszőlegesen.

## 4. Osztályterv

Javasolt csomagok: `app`, `cli`, `domain`, `service`, `persistence`.

### `domain.TaskStatus`

`enum` értékek: `TODO`, `IN_PROGRESS`, `DONE`.

### `domain.Task`

**Felelősség:** egyetlen feladat üzleti állapotának védelme.

**Mezők:** `long id`, `String title`, `TaskStatus status`, `long accumulatedSeconds`, `Instant startedAt`.

```java
public final class Task {
    public Task(long id, String title);
    public long getId();
    public String getTitle();
    public TaskStatus getStatus();
    public long getAccumulatedSeconds();
    public Optional<Instant> getStartedAt();
    public void start(Instant startedAt);
    public long stop(Instant stoppedAt);
    public void markTodo();
    public void markDone();
}
```

`start` csak `TODO` állapotból engedélyezett. `stop` csak aktív feladaton hívható, kiszámolja és hozzáadja a nem negatív másodperckülönbséget, majd `TODO` állapotba állít. A `markDone` szükség esetén előbb lezárja a mérést.

### `service.TimeTracker`

**Felelősség:** az időforrás és az időintervallum-számítás elkülönítése; nincs saját háttérszála.

**Mezők:** `Clock clock`.

```java
public final class TimeTracker {
    public TimeTracker(Clock clock);
    public Instant now();
    public long elapsedSeconds(Instant startedAt, Instant stoppedAt);
}
```

`Clock` befecskendezésével az időérzékeny logika determinisztikusan tesztelhető.

### `persistence.TaskRepository`

**Felelősség:** a tárolási megvalósítás absztrakciója.

```java
public interface TaskRepository {
    TaskData load() throws StorageException;
    void save(TaskData taskData) throws StorageException;
}
```

`TaskData` egy értékobjektum, amely a `nextId` számlálót és az aktuális feladatlistát együtt tárolja. Ez megakadályozza, hogy törlés után egy korábbi ID újrahasználódjon.

### `persistence.StorageHandler`

**Felelősség:** JSON-fájl olvasása és atomi mentése a `TaskRepository` szerződésen keresztül.

**Mezők:** `Path storagePath`, `Gson gson` (vagy kizárólag JDK-s serializer).

```java
public final class StorageHandler implements TaskRepository {
    public StorageHandler(Path storagePath);
    public TaskData load() throws StorageException;
    public void save(TaskData taskData) throws StorageException;
}
```

Mentéskor előbb a célfájllal azonos könyvtárban lévő ideiglenes fájl készül, majd `ATOMIC_MOVE` használatával kerül a helyére. Hibás JSON-nál `StorageException` keletkezik; az alkalmazás a mentést érintetlenül hagyja és javasolhat `.bak` másolatot.

### `service.TaskManager`

**Felelősség:** feladatok gyűjteménye, egyedi ID-k és az „egyszerre egy aktív feladat” invariáns kezelése.

**Mezők:** `Map<Long, Task> tasks`, `TaskRepository repository`, `TimeTracker timeTracker`, `long nextId`.

```java
public final class TaskManager {
    public TaskManager(TaskRepository repository, TimeTracker timeTracker);
    public void load() throws StorageException;
    public Task addTask(String title) throws ValidationException, StorageException;
    public List<Task> listTasks();
    public Task startTask(long id) throws TaskNotFoundException, TaskStateException, StorageException;
    public Task stopTask(long id) throws TaskNotFoundException, TaskStateException, StorageException;
    public Task changeStatus(long id, TaskStatus status)
            throws TaskNotFoundException, TaskStateException, StorageException;
    public void deleteTask(long id) throws TaskNotFoundException, TaskStateException, StorageException;
    public Optional<Task> getActiveTask();
}
```

Minden sikeres állapotmódosítás után meghívja a repository `save` metódusát. A visszatérő másolat vagy olvashatatlan nézet legyen, hogy a kliens ne sértse meg az invariánsokat.

### `cli.CommandParser`

**Felelősség:** egy parancssor lexikális elemzése, idézőjeles címek támogatása és a manager hívása. Nem tartalmaz üzleti szabályt.

**Mezők:** `TaskManager taskManager`, `PrintWriter output`.

```java
public final class CommandParser {
    public CommandParser(TaskManager taskManager, PrintWriter output);
    public boolean execute(String line);
    public void printHelp();
}
```

Az `execute` `true` értéke folytatja a konzolciklust, `false` értéke szabályos kilépést kér.

### `app.Application`

**Felelősség:** függőségek összedrótozása, betöltés, konzolciklus és szabályos leállítás.

```java
public final class Application {
    public static void main(String[] args);
    public void run();
}
```

## 5. Hibakezelési szerződés

### 5.1 Alapelv és felelősségi határok

A programban hiba nem jelenthet néma sikert, adatvesztést vagy a konzolciklus váratlan leállását. A várt, felhasználó által javítható hibákat **ellenőrzött (checked) kivételek** írják le; a `CommandParser` ezeket elkapja, kódolt hibaüzenetet ír ki, majd tovább várja a következő parancsot. Az alkalmazás csak szabályos `exit` parancsra áll le.

A kivételek a réteghatáron keletkeznek: a parser szintaktikai hibát, a domain/manager üzleti hibát, a repository tartós tárolási hibát jelez. Egy réteg nem írhat közvetlenül konzolra és nem nyelhet el hibát.

```text
ApplicationException (checked)
├── CommandSyntaxException
├── ValidationException
├── TaskNotFoundException
├── TaskStateException
│   └── TimeCalculationException
└── StorageException
    ├── StorageReadException
    │   └── StorageCorruptedException
    └── StorageWriteException
```

Minden saját kivétel tartalmazzon `ErrorCode code` értéket és felhasználónak szánt, magyar nyelvű üzenetet. Az eredeti `cause`-t (például `IOException`, `JsonParseException`) csak naplózásra kell megőrizni; nyers stack trace nem kerülhet a normál CLI-kimenetbe.

```java
public abstract class ApplicationException extends Exception {
    protected ApplicationException(ErrorCode code, String message, Throwable cause);
    protected ApplicationException(ErrorCode code, String message);
    public ErrorCode getCode();
}

public enum ErrorCode {
    CMD_EMPTY, CMD_UNKNOWN, CMD_ARGUMENTS, CMD_QUOTING, CMD_ID, CMD_STATUS,
    VALIDATION_TITLE,
    TASK_NOT_FOUND, TASK_ALREADY_RUNNING, TASK_NOT_RUNNING,
    TASK_INVALID_TRANSITION, TASK_DELETE_ACTIVE, TIME_CLOCK_REGRESSION,
    STORAGE_READ, STORAGE_WRITE, STORAGE_CORRUPTED, STORAGE_SCHEMA,
    STORAGE_INVALID_STATE
}
```

### 5.2 Keletkeztethető hibák

| Kód | Kivétel típusa | Mikor keletkezik? | CLI-válasz és következmény |
|---|---|---|---|
| `CMD_EMPTY` | `CommandSyntaxException` | Üres vagy csak whitespace-ből álló sor. | `HIBA [CMD_EMPTY]: Adj meg parancsot. Írd be: help.`; az állapot nem változik. |
| `CMD_UNKNOWN` | `CommandSyntaxException` | Nem ismert parancsnév. | `HIBA [CMD_UNKNOWN]: Ismeretlen parancs: ...`; az állapot nem változik. |
| `CMD_ARGUMENTS` | `CommandSyntaxException` | Hiányzó, túl sok vagy rossz sorrendű argumentum. | Kiírja a helyes használatot, például `Használat: start <id>`. |
| `CMD_QUOTING` | `CommandSyntaxException` | Lezáratlan idézőjel az `add` címében. | `HIBA [CMD_QUOTING]: Lezáratlan idézőjel.`; nem jön létre feladat. |
| `CMD_ID` | `CommandSyntaxException` | Az ID nem egész, nem pozitív, vagy túl nagy `long` értékhez. | `HIBA [CMD_ID]: Az ID pozitív egész szám legyen.` |
| `CMD_STATUS` | `CommandSyntaxException` | Nem létező státusznév. | Kiírja a megengedett értékeket: `TODO`, `IN_PROGRESS`, `DONE`. |
| `VALIDATION_TITLE` | `ValidationException` | Üres, csak whitespace-ből álló vagy a dokumentált maximális hossznál (pl. 200 karakter) hosszabb cím. | A feladat nem jön létre; a hiba jelzi a pontos korlátot. |
| `TASK_NOT_FOUND` | `TaskNotFoundException` | A megadott ID nincs a betöltött feladatok között. | `Nincs ilyen feladat: <id>.`; mentés sem történik. |
| `TASK_ALREADY_RUNNING` | `TaskStateException` | `start <id>` közben másik feladat már `IN_PROGRESS`. | Közli az aktív ID-t és azt, hogy előbb `stop <aktív-id>` szükséges. |
| `TASK_NOT_RUNNING` | `TaskStateException` | `stop <id>` nem aktív feladatra érkezik. | Nem módosít időt vagy státuszt. |
| `TASK_INVALID_TRANSITION` | `TaskStateException` | Nem megengedett állapotváltás, például közvetlen `DONE → IN_PROGRESS`, ha a szabályzat ezt tiltja. | A jelenlegi és a kért státuszt is kiírja; mentés nem történik. |
| `TASK_DELETE_ACTIVE` | `TaskStateException` | Aktív feladat törlése. | `Előbb állítsd le a feladatot.`; a feladat megmarad. |
| `TIME_CLOCK_REGRESSION` | `TimeCalculationException` | A leállítás időpontja korábbi a kezdésnél (rendszeróra-visszaállítás vagy hibás `Clock`). | Nem ír jóvá negatív időt, nem ment; a mérést aktívan hagyja a felhasználói döntésig. |
| `STORAGE_READ` | `StorageReadException` | A fájl megnyitása, olvasása vagy jogosultsági ellenőrzése sikertelen. | Induláskor a CLI jelzi a helyet és új fájl indítását ajánlja; a meglévő fájlt nem írja felül. |
| `STORAGE_CORRUPTED` | `StorageCorruptedException` | A JSON szintaktikailag hibás vagy nem olvasható. | A fájl érintetlen marad; az alkalmazás biztonsági okból nem indul tovább. |
| `STORAGE_SCHEMA` | `StorageCorruptedException` | Hiányzó/későbbi `schemaVersion`, hiányzó kötelező mező vagy hibás mezőtípus. | Kompatibilitási hibaüzenet; automatikus felülírás tilos. |
| `STORAGE_INVALID_STATE` | `StorageCorruptedException` | Betöltött adatok sértik az invariánsokat: ismétlődő/nem pozitív ID, negatív idő, ismeretlen státusz vagy több aktív feladat. | Betöltés megszakad; a sérült fájl megmarad diagnosztikára. |
| `STORAGE_WRITE` | `StorageWriteException` | Könyvtár létrehozása, ideiglenes fájl írása, fsync vagy atomi átnevezés sikertelen. | A memóriabeli művelet sikeres lehet, de a CLI egyértelműen jelzi, hogy nem maradandó; kilépés előtt újrapróbálható a mentés. |

### 5.3 Állapot- és mentésbiztonság

* A `TaskManager` **előbb** validálja az üzleti szabályt, **utána** módosít memóriában, végül ment. Ha a mentés sikertelen, a memóriabeli állapot és a lemezen lévő állapot eltérhet; ezt `STORAGE_WRITE` hibaként nyíltan jelezni kell, és a folyamatban egy `unsavedChanges` jelzőnek meg kell maradnia.
* A `StorageHandler` csak sikeres ideiglenes fájlírás után cserélheti le a régi fájlt. Így írási hiba nem ronthatja el az előző, még érvényes mentést.
* Az `Application` legfelső szinten elkapja a nem várt `RuntimeException` kivételeket, naplózza őket, majd rövid általános hibaüzenetet ad. Ezek nem helyettesítik a fenti, definiált hibákat, és fejlesztéskor külön hibajegyként kezelendők.

### 5.4 `CommandParser` hibakezelési viselkedése

```java
public final class CommandParser {
    public boolean execute(String line); // kivételeket elkap, hibaüzenetet ír, nem áll le
    public void printHelp();
    Command parse(String line) throws CommandSyntaxException;
}
```

Az `execute` metódus kizárólag a `CommandSyntaxException` és az `ApplicationException` publikus üzenetét jeleníti meg. A `StorageWriteException` után nem írhatja azt, hogy a művelet tartósan sikeres volt. A `help` mindig felsorolja a parancsszintaxisokat és a státuszértékeket.

## 6. Parancsok és válaszok

| Parancs | Példa | Hatás |
|---|---|---|
| `add <cím>` | `add "API dokumentáció"` | Új `TODO` feladat. |
| `list` | `list` | Rendezett feladatlista. |
| `start <id>` | `start 3` | A 3-as feladat mérése indul. |
| `stop <id>` | `stop 3` | A 3-as feladat mérése megáll. |
| `status <id> <állapot>` | `status 3 DONE` | Explicit státuszváltás. |
| `delete <id>` | `delete 3` | Feladat törlése, ha nem aktív. |
| `help` | `help` | Súgó. |
| `exit` | `exit` | Mentés és kilépés. |

Példa lista: `#3 [TODO] API dokumentáció — 01:12:09`.

## 7. Üzleti folyamat

### `start <id>`

1. A `CommandParser` validálja, hogy az ID pozitív egész, majd meghívja a `TaskManager.startTask(id)` metódust.
2. A manager megkeresi a feladatot; hiány esetén `TaskNotFoundException` kerül a CLI-hez.
3. A manager lekéri az aktív feladatot. Ha az másik ID, `TaskStateException` keletkezik; a felhasználó előbb `stop` parancsot kap javaslatként.
4. A manager a `TimeTracker.now()` eredményével meghívja a `Task.start(...)` metódust.
5. A feladat `IN_PROGRESS` lesz, a kezdő időpont rögzül, majd a manager atomi mentést kér.
6. A CLI visszaigazolja az indítást.

### `stop <id>`

1. A parser validálja az ID-t, majd meghívja a `TaskManager.stopTask(id)` metódust.
2. A manager ellenőrzi a létezést és azt, hogy a feladat valóban `IN_PROGRESS` állapotú.
3. A `TimeTracker.now()` aktuális időpontot ad; a `Task.stop(...)` a kezdő idő és az aktuális idő különbségét másodpercben hozzáadja az `accumulatedSeconds` értékhez.
4. A feladat státusza `TODO` lesz, a `startedAt` értéke kiürül.
5. A manager ment, a CLI pedig kiírja a mostani szakasz és az összesített időt.

Ehhez **nem szükséges szálkezelés**. A futó mérés állapota egy kezdő időbélyeg; a kijelzett élő idő a `now - startedAt + accumulatedSeconds` képlettel számítható. A konzol így végig fogad új parancsokat, és nincs időzítőszál, amely versenyhelyzetet okozhatna.

## 8. JSON-adatformátum

A tároló alapértelmezett helye: `data/tasks.json`. Egy mentés az alkalmazás teljes állapotát tárolja, hogy az `nextId` újraindítás után is egyértelmű maradjon.

```json
{
  "schemaVersion": 1,
  "nextId": 4,
  "tasks": [
    {
      "id": 1,
      "title": "Projektterv elkészítése",
      "status": "DONE",
      "accumulatedSeconds": 5400,
      "startedAt": null
    },
    {
      "id": 3,
      "title": "Perzisztencia implementálása",
      "status": "IN_PROGRESS",
      "accumulatedSeconds": 1200,
      "startedAt": "2026-10-29T09:15:00Z"
    }
  ]
}
```

Betöltéskor validálni kell a sémaverziót, az egyedi pozitív ID-ket, a nem negatív időt, az engedélyezett státuszokat és azt, hogy legfeljebb egy feladat legyen `IN_PROGRESS`. Érvénytelen állomány esetén az alkalmazás hibaüzenettel induljon, üres memóriabeli állapotot ajánljon fel, de ne írja felül automatikusan a sérült fájlt.

## 9. Indítási és tesztelési terv

1. A `main` létrehozza a `StorageHandler`, `TimeTracker(Clock.systemUTC())`, `TaskManager` és `CommandParser` példányokat.
2. A manager betölti a `data/tasks.json` fájlt, ha létezik.
3. Az alkalmazás a szabványos bemenet sorait addig küldi a parsernek, amíg az `exit` parancs `false` választ nem ad.
4. Kilépéskor utolsó mentés történik.

Minimális automata tesztek:

* feladat létrehozása üres és túl hosszú cím elutasításával;
* második aktív feladat indításának elutasítása;
* `start`–`stop` esetben az összesített idő helyes növelése fix `Clock` mellett;
* aktív feladat törlésének elutasítása;
* mentés–betöltés után az ID-k, státuszok és idők megőrzése;
* sérült JSON kezelése és ismeretlen parancs hibajelzése.
* a 5.2 fejezet minden `ErrorCode` értékének célzott kiváltása, a megfelelő kivételtípus és a változatlan/konzisztens állapot ellenőrzésével;
* írási hiba szimulálása: a korábbi JSON-fájl érintetlen marad, az `unsavedChanges` jelző pedig aktív.
