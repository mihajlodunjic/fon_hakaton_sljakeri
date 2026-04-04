## Opcija A: Digitalni kompas (Magnetometar)

Ova opcija koristi unutrašnji senzor telefona kako bi odredila gde korisnik gleda u odnosu na beacon. 

**Princip:** Svaki beacon ima upisan svoj "azimut" (npr. Sever = 0°). Aplikacija upoređuje taj azimut sa trenutnim smerom telefona (Heading).

**Logika:** Ako je razlika između azimuta beacona i smera telefona pozitivna, objekat je desno; ako je negativna, levo je.

**Prednost:** Potreban je samo jedan beacon po lokaciji.

**Glavni izazov:** Zahteva da telefon bude usmeren u pravcu kretanja (fiksni položaj) da bi kalkulacija bila tačna.

---

### DETALJNIJI OPIS OPCIJE A:

#### 1. Definisanje "Azimuta" Beacona

Kada Beacon Operator postavlja uređaj (npr. na ulazna vrata apoteke), on u aplikaciji mora da definiše njegovu poziciju u odnosu na sever.

Recimo da se vrata nalaze na severnoj strani zgrade → azimut = **0°**.
Ako je beacon na stubu sa desne strane trotoara koji ide ka istoku → azimut = **90°**.

#### 2. Izračunavanje Relativnog Ugla

Aplikacija radi dve stvari:

* Prima BLE signal (zna da je blizu beacona X)
* Očitava smer telefona (heading)

```math
Relativni Ugao = Azimut Beacona - Smer Korisnika
```

#### 3. Mapiranje na Audio Poruku (Levo/Desno)

* **-20° do +20°** → Beacon je ispred
  → "Ulaz je ispred vas."

* **+20° do +160°** → Beacon je desno
  → "Ulaz je sa vaše desne strane."

* **-20° do -160°** → Beacon je levo
  → "Ulaz je sa vaše leve strane."

---

### Zašto je ovo dobro za vaš projekat?

* **Hardverska dostupnost:** Svi Android telefoni imaju magnetometar
* **Preciznost:** Stabilnije od samog BLE signala
* **Jednostavno za operatora:** Dugme "Kalibriši smer"
* **Hackathon workaround:** Telefon držati ispred grudi

---

### Potencijalni izazov: držanje telefona

Slepe osobe često drže telefon u džepu ili ruci koja se njiše.

**Rešenje:**
Naglasiti držanje telefona ispred tela radi preciznosti.

---

## Opcija B: Dual-Beacon Gate (Sistemski smer)

Koriste se **dva beacona** za određivanje pozicije.

**Princip:**

* Jedan beacon levo
* Jedan beacon desno

**Logika:**

* RSSI_levo > RSSI_desno → objekat je levo
* RSSI_desno > RSSI_levo → objekat je desno
* približno jednako → objekat je ispred

**Prednost:**
Radi čak i kada je telefon u džepu

**Mana:**

* Potreban dupli broj uređaja
* Precizna instalacija

---

## Opcija C: Fingerprinting (Akcelerometar + Žiroskop)

Koristi **kretanje korisnika**, ne smer telefona.

**Princip:**

* Akcelerometar detektuje hodanje
* Žiroskop prati skretanje

**Logika:**

1. Beleži se promena RSSI tokom kretanja
2. Kombinuje se sa promenom pravca

---

### DETALJNIJI OPIS OPCIJE C:

#### Sistem dve tačke

* **Tačka A:** RSSI = -80 dBm, kretanje pravo
* **Tačka B:** RSSI = -70 dBm

➡ Zaključak: Beacon je ispred

---

#### Kako određuje levo/desno?

* Skretanje ulevo + slabiji signal → beacon je desno
* Skretanje ulevo + jači signal → beacon je levo

---

### Zašto "Fingerprinting"?

Klasično: mapa signala unapred

Ovde:
➡ **Dynamic Fingerprinting** (mapa se pravi u hodu)

---

### Prednosti i mane

**Prednosti:**

* Radi iz džepa
* Ne treba drugi beacon

**Mane:**

* Kašnjenje (2–3 m kretanja)
* Potrebna filtracija signala (Moving Average / Kalman)

---

### Implementacija u kodu

Potrebna klasa `MovementAnalyzer`:

* koristi `Sensor.TYPE_ACCELEROMETER`
* koristi `Sensor.TYPE_GYROSCOPE`
* beleži:

  ```
  (ugao_skretanja, jačina_signala)
  ```
* analizira poslednjih 5 zapisa

**Logika:**

* signal raste → objekat ispred
* signal opada nakon skretanja → levo/desno zaključak

---

### Zaključak

Opcija C je najnaprednija i najimpresivnija za hackathon jer pokazuje razumevanje:

* fizike signala
* senzora
* obrade podataka
