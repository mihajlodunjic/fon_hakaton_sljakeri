# Implementacioni plan MVP-a za 24h hackathon

## Sažetak

- Plan ne menja `specifikacija.md`; prevodi je u redosled rada za greenfield Android aplikaciju.
- Prioritet je najraniji end-to-end tok na dva fizička Android uređaja: beacon šalje paket, receiver ga dekodira, potvrđuje blizinu, poštuje cooldown i pušta TTS.
- Posle Faze 1 tim može da radi paralelno na beacon delu, receiver delu i UI/persistenciji, ali svaka faza mora da se zatvori integracijom pre prelaska dalje.

## Ključni interni interfejsi i tehnički dogovori

- Aplikacija ostaje jedan Android modul sa paketima `ui`, `domain`, `ble`, `tts`, `storage`, `viewmodel`.
- `BeaconPayloadCodec` enkodira/dekodira manufacturer-specific payload u formatu: `protocolVersion(1B)`, `beaconUuid(16B)`, `pointType(1B)`, `priority(1B)`, `messageCode(2B)`.
- `BeaconAdvertiserController` upravlja start/stop oglašavanjem i prijavljuje status UI-ju.
- `BeaconScannerController` filtrira samo očekivani manufacturer/protokol, odbacuje nevalidne pakete i prosleđuje validne rezultate dalje.
- `RssiStabilizer` za MVP koristi prag `-75 dBm`, `3` uzastopna validna očitavanja i reset ako isti beacon nestane duže od `2s`.
- `CooldownRepository` čuva cooldown po ključu `(beaconId, messageCode)`; podrazumevani cooldown je `10s`.
- `TtsAnnouncer` mapira `(pointType, messageCode)` u lokalni srpski tekst; ako TTS nije dostupan, greška se loguje bez rušenja aplikacije.
- Za MVP koristiti `Preferences DataStore`; Room preskočiti. Čuvati aktivni beacon config, receiver state, cooldown mapu i poslednjih 20 događaja za debug.

---

## Faza 0: Setup projekta i osnovni kostur aplikacije

### Cilj faze

Postaviti pokretljiv Android projekat sa svim osnovnim zavisnostima, dozvolama i skeletom ekrana.

### Konkretni zadaci

- Kreirati novi Android projekat u Kotlin + Jetpack Compose, `minSdk 26`, sa jasnom strukturom paketa.
- Dodati zavisnosti za BLE rad, DataStore, lifecycle/viewmodel i TTS korišćenje iz Android SDK-a.
- Definisati manifest dozvole i runtime tok za Bluetooth/Location dozvole prema API nivou.
- Napraviti tri prazna Compose ekrana: izbor moda, beacon konfiguracija, receiver ekran.
- Uvesti osnovnu navigaciju i zajednički `AppState` ili ViewModel skelet.
- Uvesti centralni Logcat tagging dogovor za `BLE_ADV`, `BLE_SCAN`, `RSSI`, `TTS`, `STORE`.

### Očekivani rezultat

Aplikacija se pokreće na telefonu, navigacija između tri ekrana radi, a projekat ima spremnu infrastrukturu za dalje faze.

### Zavisnosti od prethodnih faza

- Nema.

### Rizici i napomene

- BLE dozvole se razlikuju po Android verzijama; proveru uraditi odmah na oba demo uređaja.
- Emulator nije relevantan za BLE testiranje; cilj od početka mora biti fizički uređaj.
- Ako tim ima više uređaja, odmah evidentirati koji ima stabilniji BLE advertiser.

### Kriterijum završetka

Projekat build-uje bez greške, instalira se na fizički uređaj i sva tri ekrana su dostupna kroz UI.

---

## Faza 1: Domen, payload protokol i lokalna mapa poruka

### Cilj faze

Zaključati model podataka i format BLE paketa pre rada na advertiser/scanner logici.

### Konkretni zadaci

- Implementaciono specifikovati `BeaconConfig`, `DetectedBeaconEvent`, `ReceiverState`, `CooldownEntry`, `PointType`, `Priority`.
- Definisati konačne integer vrednosti za `PointType` i `Priority` koje se šalju preko BLE-a.
- Definisati `messageCode` mapu za MVP poruke na srpskom, najmanje za `crosswalk`, `stairs`, `pole`, `entrance` i `bus stop`.
- Zaključati manufacturer ID i `protocolVersion` za demo; dokumentovati da je manufacturer ID demo-konstanta, ne proizvodna SIG registracija.
- Odrediti binarni raspored bajtova i validaciona pravila za decode: očekivana dužina, podržana verzija, validan enum opseg.
- Napisati plan jedinčnih testova za encode/decode, nevalidne pakete i mapiranje poruka.

### Očekivani rezultat

Svi u timu rade protiv istog payload ugovora i nema kasnijeg prepakivanja BLE poruka.

### Zavisnosti od prethodnih faza

- Faza 0.

### Rizici i napomene

- Najveća greška u hackathon tempu je da beacon i receiver razvijaju različite formate paketa.
- UUID mora biti sirovih 16 bajtova; string format bi nepotrebno trošio payload.
- Ako manufacturer-specific data pravi problem na nekim uređajima, fallback je isti binarni payload u service data, ali samo ako fizički test potvrdi blokadu.

### Kriterijum završetka

Postoji jednoznačno definisan payload format, `messageCode` tabela i lista validacionih pravila koju koriste i beacon i receiver.

---

## Faza 2: Beacon mode, konfiguracija i lokalna perzistencija

### Cilj faze

Omogućiti operatoru da podesi beacon i stabilno emituje payload sa fizičkog Android uređaja.

### Konkretni zadaci

- Na beacon ekranu dodati polja `label`, `pointType`, `priority`, `messageCode`, plus prikaz generisanog `beaconId`.
- Uvesti validaciju unosa i jasna UI stanja za `idle`, `ready`, `advertising`, `error`.
- Sačuvati poslednju beacon konfiguraciju u `Preferences DataStore` i vratiti je pri sledećem pokretanju aplikacije.
- Implementirati start/stop BLE advertiser toka sa manufacturer-specific payload-om iz Faze 1.
- Obezbediti da `label` ostane samo lokalna vrednost i nikada ne ulazi u payload.
- Prikazati operatoru aktivni status, poslednju izmenjenu vrednost i eventualnu BLE grešku iz advertiser API-ja.
- Ručno proveriti payload preko nRF Connect-a na drugom uređaju.

### Očekivani rezultat

Jedan Android telefon može da se ponaša kao beacon, da čuva konfiguraciju i da pouzdano emituje očekivane podatke.

### Zavisnosti od prethodnih faza

- Faza 0.
- Faza 1.

### Rizici i napomene

- Neki telefoni slabije podržavaju advertising u zavisnosti od čipseta i OEM ograničenja.
- Ako advertising ne radi na jednom uređaju, odmah prebaciti beacon ulogu na drugi telefon umesto trošenja vremena na OEM-specifičan debugging.
- Beacon ekran mora biti minimalan i brz za demo; ne uvoditi dodatne forme van specifikacije.

### Kriterijum završetka

Beacon se može startovati i stopirati iz UI-ja, konfiguracija se vraća posle restarta aplikacije, a nRF Connect vidi tačan payload.

---

## Faza 3: Receiver mode, skeniranje, dekodiranje i minimalni end-to-end tok

### Cilj faze

Omogućiti receiver uređaju da pronađe samo validne pakete, dekodira ih i prikaže rezultat u UI-ju.

### Konkretni zadaci

- Implementirati receiver ekran sa velikim Start/Stop dugmetom, statusom skeniranja i prikazom poslednjeg detektovanog paketa.
- Uvesti BLE scan filter logiku po manufacturer ID i protocol version i tihu ignoraciju svih nevalidnih paketa.
- Implementirati decode pipeline koji iz payload-a vraća `beaconId`, `pointType`, `priority`, `messageCode`.
- Mapirati `(pointType, messageCode)` u lokalni tekst i prikazati ga u UI-ju bez TTS-a u ovoj fazi.
- U `ReceiverState` sačuvati `scanningEnabled`, poslednji beacon, poslednji `messageCode` i vreme detekcije.
- Dodati osnovni retry mehanizam: ako scan neočekivano stane, pokušati restart posle 2 do 5 sekundi.
- Obraditi scenario gašenja Bluetooth-a: prikaz poruke korisniku, pauziranje skeniranja bez crash-a.

### Očekivani rezultat

Dva fizička uređaja ostvaruju prvi potpuni tok beacon -> receiver -> dekodiran prikaz poruke na ekranu.

### Zavisnosti od prethodnih faza

- Faza 0.
- Faza 1.
- Faza 2.

### Rizici i napomene

- Najbitniji cilj ove faze je stvarni signal između dva uređaja, ne savršen UI.
- Scan callback može davati više duplih rezultata; još ne uvoditi audio trigger dok se ne potvrdi sirovi tok.
- Logcat mora jasno razlikovati odbijene pakete od validnih detekcija.

### Kriterijum završetka

Receiver na fizičkom uređaju vidi validan beacon iz Faze 2, prikazuje dekodiran sadržaj i ne ruši se pri gašenju Bluetooth-a.

---

## Faza 4: RSSI stabilizacija i cooldown logika

### Cilj faze

Sprečiti lažna i prečesta okidanja kako bi audio upozorenja bila upotrebljiva u realnom prostoru.

### Konkretni zadaci

- Uvesti `RssiStabilizer` koji vodi kratko stanje po beacon-u i traži 3 uzastopna očitavanja iznad `-75 dBm`.
- Resetovati sekvencu ako isti beacon padne ispod praga ili nestane duže od 2 sekunde.
- Uvesti cooldown proveru po ključu `(beaconId, messageCode)` sa podrazumevanim trajanjem od 10 sekundi.
- Persistirati cooldown mapu u `Preferences DataStore` kako kratki restart aplikacije ne bi proizvodio spam u demo situaciji.
- Sačuvati `DetectedBeaconEvent` za poslednjih 20 detekcija sa poljem `wasAnnounced` radi debug uvida.
- Dopuniti receiver UI poslednjom RSSI vrednošću, statusom stabilizacije i razlogom zašto događaj nije najavljen.
- Napisati i izvršiti jedinčne test scenarije za stabilizaciju i cooldown pravila.

### Očekivani rezultat

Receiver ne reaguje na slučajne skokove signala i ne ponavlja istu poruku na svakom scan callback-u.

### Zavisnosti od prethodnih faza

- Faza 3.

### Rizici i napomene

- Prag `-75 dBm` i broj od 3 uzastopna očitavanja su MVP podrazumevane vrednosti; možda će tražiti korekciju na lokaciji demo-a.
- Predug cooldown usporava demo, prekratak cooldown pravi spam; 10 sekundi je početna vrednost, ne finalna dogma.
- Fizičko kretanje i orijentacija telefona znatno menjaju RSSI, pa validacija mora biti na terenu, ne samo na stolu.

### Kriterijum završetka

Na fizičkom testu receiver objavljuje događaj tek posle stabilizovanog prilaženja i ne ponavlja isti događaj unutar cooldown prozora.

---

## Faza 5: TTS, osnovni UI polish i robustno ponašanje aplikacije

### Cilj faze

Uvesti glasovno iskustvo i zatvoriti osnovne UX i robustness zahteve iz specifikacije.

### Konkretni zadaci

- Implementirati `TtsAnnouncer` sa inicijalizacijom, proverom dostupnosti motora i srpskim porukama iz lokalne mape.
- Povezati TTS okidanje isključivo na događaj koji je prošao RSSI stabilizaciju i cooldown.
- Obezbediti da TTS greška ne ruši aplikaciju i da se neuspeh vidi kroz log ili diskretni UI status.
- Dopuniti tri ekrana jasnim, velikim elementima, visokim kontrastom i TalkBack opisima za glavna dugmad i stanja.
- Na receiver ekranu prikazati poslednju izgovorenu poruku, vreme okidanja i status TTS sistema.
- Proveriti da se BLE i TTS resursi uredno gase pri izlasku iz moda ili zatvaranju ekrana.

### Očekivani rezultat

Receiver uređaj glasovno obaveštava korisnika bez duplih okidanja, a aplikacija ostaje jednostavna i pristupačna.

### Zavisnosti od prethodnih faza

- Faza 4.

### Rizici i napomene

- Na nekim uređajima TTS za srpski može koristiti različite glasove ili zahtevati dodatni jezički paket.
- Ako srpski glas nije dostupan, za demo zadržati srpski tekst i koristiti najbliži dostupan TTS engine uz proveru razumljivosti.
- Ne ulaziti u napredna podešavanja glasa, brzine i jačine osim ako ostane višak vremena.

### Kriterijum završetka

Validan beacon izaziva jedan jasan audio izlaz u manje od 2 sekunde od ulaska u proximity zonu, bez crash-a i bez audio spama.

---

## Faza 6: Integraciono testiranje na fizičkim uređajima i kalibracija

### Cilj faze

Potvrditi da sistem radi u realnim uslovima i podesiti pragove za stabilan demo.

### Konkretni zadaci

- Pripremiti najmanje dva Android telefona: jedan stalno u beacon modu, drugi u receiver modu.
- Izvesti testove za najmanje 5 tipova tačaka: `crosswalk`, `stairs`, `pole`, `entrance`, `bus stop`.
- Meriti vreme od ulaska u zonu do TTS objave i beležiti rezultate za svaku tačku.
- Proveriti ponašanje pri gašenju i uključivanju Bluetooth-a tokom rada, restartu aplikacije i brzom povratku u scan mod.
- Proveriti malformed packet scenario kroz nRF Connect ili modifikovan beacon payload, uz očekivanje da receiver tiho ignoriše paket.
- Kalibrisati RSSI prag i cooldown samo ako terenski test pokaže realan problem; svaku izmenu odmah dokumentovati.
- Proveriti potrošnju baterije i zagrevanje tokom kontinualnog scan/advertise testa od najmanje 15 minuta.

### Očekivani rezultat

Tim ima potvrđene radne parametre i listu poznatih ograničenja za demo lokaciju.

### Zavisnosti od prethodnih faza

- Faza 5.

### Rizici i napomene

- Unutrašnji i spoljašnji prostor daju različite RSSI profile; ne kalibrisati samo u kancelariji.
- Ako jedan telefon daje loš advertiser domet, zameniti uloge uređaja umesto produžavanja debug-a.
- Fokus je na demo pouzdanosti, ne na formalnoj laboratorijskoj validaciji.

### Kriterijum završetka

Najmanje 5 demo scenarija prolazi na fizičkim uređajima uz stabilna očitavanja i TTS u okviru ciljanog vremena.

---

## Faza 7: Priprema demo scenarija i operativna checklista

### Cilj faze

Pretvoriti tehnički MVP u siguran, brz i ponovljiv hackathon demo.

### Konkretni zadaci

- Izabrati 3 do 5 finalnih demo tačaka i dodeliti svakoj konkretan beacon telefon i `messageCode`.
- Napraviti kratku operativnu checklistu: punjenje baterije, Bluetooth uključen, beacon aktivan, receiver scan aktivan, glasnoća podešena, poslednja konfiguracija proverena.
- Definisati redosled demo priče: početni ekran, prelazak u beacon mod, aktivacija receiver moda, prilazak tački, TTS najava, cooldown demonstracija.
- Pripremiti fallback varijantu ako jedna demo tačka ne radi: rezervni beacon, rezervni `messageCode`, rezervna lokacija.
- Zabeležiti poznata ograničenja koja treba reći žiriju: offline-first, Android telefoni kao primarni beaconi, demo manufacturer ID, MVP bez cloud backend-a.
- Proći barem dve kompletne generalne probe bez izmena u kodu između.

### Očekivani rezultat

Demo je operativno spreman i tim zna tačno ko šta radi tokom prezentacije.

### Zavisnosti od prethodnih faza

- Faza 6.

### Rizici i napomene

- Najveći demo rizik nije kod nego operativa: ugašen Bluetooth, smanjena glasnoća, pogrešan beacon uređaj.
- Treba izbegavati promene parametara neposredno pre demo-a osim ako postoji kritičan kvar.
- Laptop beacon varijantu ne koristiti kao glavni scenario.

### Kriterijum završetka

Tim može dva puta zaredom da izvede celu demo priču bez improvizacije i bez ručnog debugging-a.

---

## Test plan i scenariji prihvatanja

- Encode/decode vraća iste vrednosti za validan payload i odbacuje pogrešnu verziju, dužinu i enum opseg.
- Beacon UI čuva konfiguraciju i vraća je posle restarta aplikacije.
- Receiver vidi samo validne pakete i ignoriše sve ostale bez promene stanja.
- RSSI stabilizacija ne objavljuje poruku na pojedinačni spike, ali objavljuje nakon 3 uzastopna validna očitavanja.
- Cooldown sprečava ponavljanje iste poruke unutar 10 sekundi, a dozvoljava novu objavu nakon isteka.
- TTS objava radi za svih 5 MVP poruka i aplikacija ne puca kada TTS nije dostupan.
- Bluetooth off tokom skeniranja ne ruši aplikaciju i skeniranje se može oporaviti.
- End-to-end vreme od ulaska u zonu do TTS objave ostaje ispod 2 sekunde na finalnom demo setapu.

## Pretpostavke i podrazumevane odluke

- Repo nema postojeći Android kod; plan podrazumeva greenfield implementaciju iz jedne aplikacije.
- Za MVP se koristi samo Android telefon kao beacon; laptop beacon je van kritičnog puta.
- `Preferences DataStore` je dovoljan za konfiguraciju, stanje i cooldown; Room se ne uvodi u 24h varijanti.
- Podrazumevani parametri su `RSSI prag -75 dBm`, `3 uzastopna očitavanja`, `reset posle 2s bez signala`, `cooldown 10s`.
- Jezik TTS poruka je srpski i mapa poruka je lokalna, statička i verzionisana u aplikaciji.
