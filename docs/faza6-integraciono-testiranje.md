# Faza 6: Integraciono testiranje i kalibracija

## Trenutni podrazumevani pragovi

- `RSSI_THRESHOLD_DBM = -75`
- `REQUIRED_CONSECUTIVE_READS = 3`
- `RESET_AFTER_SIGNAL_GAP_MS = 2000`
- `COOLDOWN_WINDOW_MS = 10000`

## Uredjaji i uloge

| Uredjaj | Model | Android | Uloga | Napomena |
| --- | --- | --- | --- | --- |
| A |  |  | Beacon |  |
| B |  |  | Receiver |  |

## Beacon konfiguracije za MVP scenarije

| Scenario | Point type | Message code | Operator label | Ocekivani TTS |
| --- | --- | --- | --- | --- |
| 1 | CROSSWALK | 1 | Pesacki prelaz | Pesacki prelaz ispred vas. |
| 2 | STAIRS | 2 | Stepenice | Paznja, stepenice. |
| 3 | POLE | 3 | Stub | Paznja, stub u blizini. |
| 4 | ENTRANCE | 4 | Ulaz | Ulaz u objekat sa desne strane. |
| 5 | BUS_STOP | 5 | Autobusko stajaliste | Autobusko stajaliste ispred vas. |

## Serija 1: Sto test na kratkoj distanci

| Scenario | Lokacija | Udaljenost pri prvom TTS-u | Vreme do TTS najave | Cooldown radio | Ishod | Napomena |
| --- | --- | --- | --- | --- | --- | --- |
| CROSSWALK / 1 |  |  |  |  |  |  |
| STAIRS / 2 |  |  |  |  |  |  |
| POLE / 3 |  |  |  |  |  |  |
| ENTRANCE / 4 |  |  |  |  |  |  |
| BUS_STOP / 5 |  |  |  |  |  |  |

## Serija 2: Realno kretanje u unutrasnjem prostoru

### Spor prilazak

| Scenario | Lokacija | RSSI pri okidanju | Procena udaljenosti | Vreme do TTS | Ishod | Napomena |
| --- | --- | --- | --- | --- | --- | --- |
| CROSSWALK / 1 |  |  |  |  |  |  |
| STAIRS / 2 |  |  |  |  |  |  |
| POLE / 3 |  |  |  |  |  |  |
| ENTRANCE / 4 |  |  |  |  |  |  |
| BUS_STOP / 5 |  |  |  |  |  |  |

### Normalan hod

| Scenario | Lokacija | RSSI pri okidanju | Procena udaljenosti | Vreme do TTS | Ishod | Napomena |
| --- | --- | --- | --- | --- | --- | --- |
| CROSSWALK / 1 |  |  |  |  |  |  |
| STAIRS / 2 |  |  |  |  |  |  |
| POLE / 3 |  |  |  |  |  |  |
| ENTRANCE / 4 |  |  |  |  |  |  |
| BUS_STOP / 5 |  |  |  |  |  |  |

### Prilazak sa zastajanjem i okretanjem telefona

| Scenario | Lokacija | RSSI pri okidanju | Procena udaljenosti | Vreme do TTS | Ishod | Napomena |
| --- | --- | --- | --- | --- | --- | --- |
| CROSSWALK / 1 |  |  |  |  |  |  |
| STAIRS / 2 |  |  |  |  |  |  |
| POLE / 3 |  |  |  |  |  |  |
| ENTRANCE / 4 |  |  |  |  |  |  |
| BUS_STOP / 5 |  |  |  |  |  |  |

## Serija 3: Stres i recovery test

| Scenario | Koraci | Ocekivano | Ishod | Napomena |
| --- | --- | --- | --- | --- |
| Bluetooth off na receiveru | Iskljuci Bluetooth tokom aktivnog skeniranja | Nema crash-a, UI trazi novi start posle vracanja Bluetooth-a |  |  |
| Bluetooth on + restart skeniranja | Ukljuci Bluetooth i pritisni `Pokreni skeniranje` | Scan ponovo radi |  |  |
| Restart beacon aplikacije | Zatvori i otvori beacon app, pa pokreni emitovanje | Receiver ponovo vidi signal |  |  |
| Restart receiver aplikacije | Zatvori i otvori receiver app, pa pokreni skeniranje | TTS i scan ponovo rade |  |  |
| 15 min kontinualnog rada | Ostaviti beacon + receiver aktivne 15 min | Nema runaway TTS-a, nema kriticnog zagrevanja |  |  |
| Malformed packet | Poslati pogresan manufacturer/protocol payload | Receiver ga ignorise bez promene UI-ja |  |  |

## Acceptance checklist

- Beacon startuje bez greske za svih 5 MVP konfiguracija.
- Receiver prolazi `1/3 -> 2/3 -> 3/3` pre TTS-a.
- `lastGateDecisionText` postaje `Najava dozvoljena.` za validan scenario.
- TTS status ostaje `READY_SR` ili `READY_FALLBACK_LOCALE`.
- Ista najava se ne ponavlja unutar cooldown prozora.
- Receiver se ne rusi kada se Bluetooth iskljuci.
- Posle ponovnog ukljucivanja Bluetooth-a receiver moze ponovo da startuje skeniranje.
- Nevalidan payload ne menja UI i ne pusta TTS.
- Beacon advertising i receiver scanning ostaju aktivni tokom 15 min testa.

## Kalibracioni dnevnik

| Iteracija | Promenjen parametar | Stara vrednost | Nova vrednost | Razlog promene | Rezultat posle ponovljenog unutrasnjeg testa |
| --- | --- | --- | --- | --- | --- |
| 1 |  |  |  |  |  |
| 2 |  |  |  |  |  |
| 3 |  |  |  |  |  |

## Finalni pragovi potvrdeni za demo

- `RSSI_THRESHOLD_DBM = `
- `REQUIRED_CONSECUTIVE_READS = `
- `RESET_AFTER_SIGNAL_GAP_MS = `
- `COOLDOWN_WINDOW_MS = `
- Datum potvrde: 
- Lokacija potvrde: 

## Poznata ogranicenja za demo

- 
- 
- 

## Ako laptop i Logcat nisu dostupni

Kao izvor istine koristiti receiver UI:

- `stabilizationProgress`
- `lastGateDecisionText`
- `lastAnnouncementAt`
- `lastSpokenAt`
- `lastTtsError`
