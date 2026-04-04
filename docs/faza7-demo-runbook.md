# Faza 7: Demo runbook

## Svrha

Ovaj dokument zakljucava jedan ponovljiv demo tok za hackathon prezentaciju. Cilj nije dalje razvijanje funkcionalnosti, vec sigurno i brzo izvodjenje demonstracije na dva Android telefona bez improvizacije na sceni.

Ako `docs/faza6-integraciono-testiranje.md` nema popunjene finalne pragove, koristi se poslednji potvrden set iz Faze 6. Ako ni to nije popunjeno, privremeni default ostaje:

- `RSSI_THRESHOLD_DBM = -75`
- `REQUIRED_CONSECUTIVE_READS = 3`
- `RESET_AFTER_SIGNAL_GAP_MS = 2000`
- `COOLDOWN_WINDOW_MS = 10000`

## Uredjaji i uloge

| Stavka | Vrednost |
| --- | --- |
| Uredjaj A | Beacon |
| Uredjaj B | Receiver |
| Rezervni beacon uredjaj |  |
| Finalni build |  |
| Datum freeze-a |  |

Ako je u Fazi 6 utvrdjeno da jedan uredjaj ima slab advertising, uloga se ovde menja i vise se ne vraca na staru raspodelu.

## Finalni demo scenariji

### Primarni scenariji uzivo

| Redosled | Point type | Message code | Ocekivani TTS |
| --- | --- | --- | --- |
| 1 | CROSSWALK | 1 | Pesacki prelaz ispred vas. |
| 2 | STAIRS | 2 | Paznja, stepenice. |
| 3 | ENTRANCE | 4 | Ulaz u objekat sa desne strane. |

### Rezervni scenariji

| Prioritet rezerve | Point type | Message code | Ocekivani TTS |
| --- | --- | --- | --- |
| 1 | POLE | 3 | Paznja, stub u blizini. |
| 2 | BUS_STOP | 5 | Autobusko stajaliste ispred vas. |

Ako Faza 6 pokaze bolji trio od podrazumevanog, ovde zameniti tabelu pre demo-a i ne menjati je vise na dan prezentacije.

## Narativ koji se govori ziriju

Koristi se sledeca kratka poruka, bez improvizacije:

> Ovo je offline-first Android MVP za audio navigaciju. Jedan telefon moze da bude beacon, drugi receiver. Receiver koristi RSSI stabilizaciju i cooldown da izbegne lazna i duplirana audio upozorenja. MVP nema cloud backend i koristi demo manufacturer ID samo za hackathon prezentaciju.

## Operativni tok demonstracije

Ciljano trajanje: `2.5 do 3.5 minuta`.

### Pocetno stanje pre izlaska pred zirijem

- Receiver telefon je na ekranu `Receiver mod`.
- Beacon telefon je na ekranu `Beacon mod`.
- Na beacon telefonu je vec podesena konfiguracija za prvi scenario.
- Beacon jos ne emituje, osim ako je na probi potvrdeno da je stabilnije da emitovanje vec radi.
- Receiver jos ne skenira, osim ako je na probi potvrdeno da je stabilnije da skeniranje vec radi.

### Tacan redosled koraka

1. Narator pokazuje pocetni ili vec otvoreni `Receiver mod` ekran i kratko kaze da sistem radi offline.
2. Narator ukratko objasnjava da aplikacija radi u dva moda: `Beacon mod` i `Receiver mod`.
3. Beacon operator na uredjaju A proverava prvi scenario:
   - `Point type = CROSSWALK`
   - `Message = 1 - Pesacki prelaz`
4. Beacon operator pritiska `Pokreni emitovanje`.
5. Receiver korisnik na uredjaju B pritiska `Pokreni skeniranje`.
6. Receiver korisnik prilazi fizickoj tacki ili beacon lokaciji.
7. Na receiver ekranu treba da se vidi napredak stabilizacije do `3/3`, zatim:
   - `Najava dozvoljena po gate-u.`
   - TTS poruka
8. Narator kratko istice da cooldown sprecava audio spam.
9. Receiver korisnik odmah pokusava kratki ponovni prilaz istom beacon-u.
10. Tim pokazuje da se ista najava ne ponavlja unutar cooldown prozora.
11. Ako vreme dozvoljava, prelazi se na drugi scenario:
   - Beacon operator menja konfiguraciju na `STAIRS / 2`
   - ponavlja se skraceni tok bez dugog objasnjenja
12. Treca tacka (`ENTRANCE / 4`) se prikazuje samo ako prethodna dva prolaze glatko i jos ima vremena.

## Tacni klikovi po ekranu

### Beacon uredjaj

- Otvoriti `Beacon mod`
- Proveriti `Tip tacke`
- Proveriti `Poruka`
- Po potrebi proveriti `Prioritet`
- Pritisnuti `Pokreni emitovanje`
- Posle scenarija:
  - ili ostaviti beacon aktivan za isti scenario
  - ili promeniti konfiguraciju i ponovo pritisnuti `Pokreni emitovanje`

### Receiver uredjaj

- Otvoriti `Receiver mod`
- Proveriti:
  - `Status skeniranja`
  - `TTS status`
  - da nema crvene greske
- Pritisnuti `Pokreni skeniranje`
- Tokom demonstracije gledati:
  - `Stabilizacija`
  - `Cooldown i odluka`
  - `TTS status`
- Ako treba prekinuti:
  - pritisnuti `Zaustavi`

## Ljudske uloge

### Varijanta sa 3 osobe

| Osoba | Uloga | Tacna odgovornost |
| --- | --- | --- |
| 1 | Narator | govori pitch, objasnjava offline model, stabilizaciju i cooldown |
| 2 | Beacon operator | drzi uredjaj A, menja scenario, pokrece emitovanje |
| 3 | Receiver korisnik | drzi uredjaj B, pokrece skeniranje, prilazi tacki, pokazuje UI/TTS |

### Varijanta sa 2 osobe

| Osoba | Uloga | Tacna odgovornost |
| --- | --- | --- |
| 1 | Narator + beacon operator | govori pitch i upravlja beacon uredjajem |
| 2 | Receiver korisnik | upravlja receiver uredjajem i fizicki prolazi kroz demo |

## Operativna checklista

### T-30 min

- Oba telefona napunjena najmanje `70%`
- Bluetooth ukljucen na oba telefona
- Permission prompt-ovi vec reseni
- Receiver glasnoca izmedju `80%` i `100%`
- Potvrdjen finalni build
- Potvrdjeni finalni pragovi iz Faze 6
- Runbook procitan od svih clanova tima

### T-10 min

- Beacon telefon otvoren na prvom scenariju
- Receiver telefon otvoren na `Receiver mod` ekranu
- `TTS status` je `Spreman` ili `Fallback jezik`
- Ekrani nisu zakljucani i timeout je dovoljno dug
- Upisan redosled scenarija za danasnji demo

### T-2 min

- Provereno da niko osim beacon operatora ne dira konfiguraciju
- Beacon nije aktivan, osim ako je na probi potvrden suprotan setup
- Receiver ne skenira, osim ako je na probi potvrden suprotan setup
- Notifikacioni haos je smanjen koliko je moguce
- Tim zna ko govori, ko pritiska dugmad i ko prilazi tacki

## Fallback redosled

### Fallback A: prvi scenario ne radi

- Ne ulaziti u debug.
- Beacon operator odmah prelazi na rezervni scenario `POLE / 3`.
- Ako ni to ne radi, prelazi se na `BUS_STOP / 5`.
- Narator ne pominje kvar, vec samo kaze da prelazimo na sledeci primer.

### Fallback B: TTS ne krece, ali receiver UI radi

- Demo se nastavlja preko receiver UI-ja.
- Obavezno pokazati:
  - `lastGateDecisionText`
  - `lastDecodedText`
  - `TTS status`
- Narator kaze da je detekcija prosla, ali audio engine na tom uredjaju trenutno nije stabilan.

### Fallback C: beacon advertising otkaze

- Ako postoji rezervni beacon uredjaj, odmah ga zameniti.
- Ako ne postoji, prekinuti live deo i pokazati UI tok i objasnjenje bez dodatnog debugovanja.

### Fallback D: cooldown blokira ponavljanje

- To se koristi kao pozitivan deo price.
- Narator kaze da sistem namerno sprecava audio spam.
- Za novi live okidac odmah preci na sledeci unapred pripremljeni scenario.

## Generalna proba

Moraju postojati **2 kompletne probe bez izmene koda izmedju**.

### Proba 1

| Stavka | Rezultat |
| --- | --- |
| Datum i vreme |  |
| Ukupno trajanje |  |
| Prvi scenario prosao iz prve |  |
| TTS bio cujan |  |
| Fallback bio potreban |  |
| Cooldown demonstracija bila jasna |  |
| Napomene |  |

### Proba 2

| Stavka | Rezultat |
| --- | --- |
| Datum i vreme |  |
| Ukupno trajanje |  |
| Prvi scenario prosao iz prve |  |
| TTS bio cujan |  |
| Fallback bio potreban |  |
| Cooldown demonstracija bila jasna |  |
| Napomene |  |

## Freeze pravilo

Ako su dve uzastopne probe uspesne:

- nema novih kod promena
- nema novih pragova
- nema novih UI izmena

Izuzetak je samo kritican blocker koji direktno sprecava demo.

## Acceptance kriterijumi za Fazu 7

- Zakljucan je finalni par uredjaja i njihove uloge.
- Zakljucana su 3 primarna i 2 rezervna scenarija.
- Postoji ovaj popunjen `docs/faza7-demo-runbook.md`.
- Dve uzastopne generalne probe prolaze bez debugovanja i bez promene koda.
- Tim zna tacno ko govori, ko upravlja beacon uredjajem, a ko receiver uredjajem.
- Postoji jedna kratka i jasna recenica o ogranicenjima MVP-a.
