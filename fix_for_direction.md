# Softversko rešenje: Promena tipa senzora

Najbrži fiks je da u kodu promenite tip senzora koji koristite. Umesto **"Absolute Orientation"** (koji koristi kompas), prebacite se na **Relative Orientation**.

**Android:** Koristite `TYPE_GAME_ROTATION_VECTOR` umesto `TYPE_ROTATION_VECTOR`.
Ovaj senzor ignoriše magnetometar i koristi samo žiroskop i akcelerometar. Neće znati gde je pravi sever, ali će slika biti stabilna i neće se okretati sama od sebe zbog elektronike.

---

Ovo je zapravo najsigurniji "hirurški" rez koji možete da napravite u kodu da biste eliminisali uticaj amfiteatra.

## Razlika između senzora

**Standardni `ROTATION_VECTOR`** koristi tri izvora:

* **Akcelerometar** – da zna gde je "dole" (gravitacija)
* **Žiroskop** – da prati brzinu okretanja
* **Magnetometar (Kompas)** – da zna gde je sever

### Problem

U amfiteatru magnetometar vidi zvučnike i kablove kao "sever".
Zbog promena u magnetnom polju, šalje pogrešne ispravke → aplikacija "ludi".

---

## Zašto je `GAME_ROTATION_VECTOR` rešenje?

* Ignoriše magnetometar
* Koristi samo žiroskop + akcelerometar
* Imun je na smetnje

➡️ Rezultat: stabilna slika bez "plesanja"

---

## Implementacija (Android)

```kotlin
// Umesto ovoga:
sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

// Koristite ovo:
sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
```

---

## Promene u ponašanju

* ✅ Nema više "plesanja" slike
* ❌ Gubitak apsolutnog smera (nema severa)
* ⚠️ Mali drift nakon 10–15 min

---

## Rezime za demo

Korišćenjem `GAME_ROTATION_VECTOR`:

> "Veruj žiroskopu, ignoriši magnetna polja"

---

# Prelazak na relativni koordinatni sistem

## 1. Manual Calibration (Ručno nišanjenje)

Koraci:

1. Postavite predajnike
2. Stanite na poziciju
3. Uperite telefon u referentni predajnik
4. Kliknite **"Kalibriši"**

### Formula:

```
Relativni Azimut = Trenutni Azimut - Offset
```

---

## 2. Pozicioniranje ostalih predajnika

Ako znate raspored:

* Predajnik A → 0°
* Predajnik B → 90° desno



