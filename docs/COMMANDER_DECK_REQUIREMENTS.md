# Commander Deck Requirements für AI-Simulation

**Version:** 1.0.0  
**Datum:** 2026-04-07  
**Zweck:** Anforderungen und Best Practices für Commander Decklist JSON-Dateien zur Verwendung mit Forge AI-Simulation

---

## 1. Übersicht

Commander Decks werden für AI-Simulationen als separate JSON-Dateien nach der [Commander Decklist Specification v1.0.0](../mtg-replay-notation/spec/commander-decklist-spec.md) definiert. Diese Dateien dienen sowohl als Deck-Registry als auch als Verhaltenskonfiguration für Replay-Analyse und Mulligan-Entscheidungen.

---

## 2. Pflichtfelder (Minimum Requirements)

### 2.1 Deck-Struktur

```json
{
    "format": "mtg-commander-decklist",
    "version": "1.0.0",
    "meta": {
        "deck_name": "Name des Decks",
        "format": "Commander"
    },
    "commander": [
        {
            "quantity": 1,
            "name": "Commander Name",
            "edition": "SET",
            "collector_number": "123",
            "primary_mechanic": "ramp"
        }
    ],
    "main": [
        /* 99 Karten (oder 98 bei Companion) */
    ]
}
```

### 2.2 Pflichtfelder pro Karte

| Feld | Typ | Beschreibung |
|------|-----|--------------|
| `quantity` | integer | Immer `1` in Commander (außer Basic Lands) |
| `name` | string | Exakter englischer Kartenname |
| `edition` | string | Set-Code (z.B. `"C16"`, `"MH2"`) |
| `collector_number` | string | Collector-Nummer (z.B. `"35"`, `"263a"`) |
| `primary_mechanic` | string | Hauptrolle der Karte (siehe §2.3) |

### 2.3 Standard Mechanic Categories

**Mana & Resources:**
- `ramp`, `mana-rock`, `mana-dork`, `land-ramp`, `fixing`

**Card Advantage:**
- `card-draw`, `tutor`, `looting`, `recursion`

**Interaction:**
- `removal`, `board-wipe`, `counter`, `hate-piece`, `protection`

**Threats:**
- `win-condition`, `threat`, `combo-piece`, `token`, `tribal`

**Strategy:**
- `counters`, `proliferate`, `synergy`, `flicker`, `reanimation`, `stax`, `political`, `multicolor`, `enchantress`, `spellslinger`

---

## 3. Empfohlene Felder (Best Practices)

### 3.1 Deck-Metadaten

```json
{
    "meta": {
        "deck_id": "uuid-v4-hier",
        "deck_name": "Atraxa Superfriends",
        "format": "Commander",
        "colors": ["W", "U", "B", "G"],
        "created": "2026-04-07",
        "updated": "2026-04-07",
        "author": "Ihr Name",
        "description": "Kurze Strategie-Beschreibung"
    }
}
```

**Warum wichtig:**
- `deck_id` (UUID): Eindeutige Referenzierung in Replay-Logs
- `colors`: Automatische Validierung der Farbidentität
- `created`/`updated`: Versions-Tracking für Deck-Entwicklung

### 3.2 Mulligan-Regeln

```json
{
    "deck_rules": {
        "mulligan": {
            "card_values": {
                "land": 1.0,
                "cmc_0_to_2": 0.8,
                "cmc_3": 0.5,
                "other": 0.3
            },
            "card_overrides": [
                {
                    "name": "Sol Ring",
                    "value": 1.2,
                    "reason": "Beste T1-Play in Commander"
                }
            ],
            "thresholds": [
                {"round": 0, "hand_size": 7, "min_value": 3.5},
                {"round": 1, "hand_size": 6, "min_value": 3.0},
                {"round": 2, "hand_size": 5, "min_value": 2.5},
                {"round": 3, "hand_size": 4, "min_value": 2.0}
            ]
        }
    }
}
```

**Warum wichtig:**
- Mulligan-Entscheidungen beeinflussen stark die Konsistenz
- `card_overrides` für Schlüsselkarten (z.B. Fast Mana, Combos)
- Thresholds anpassbar je nach Deck-Geschwindigkeit

### 3.3 Combo-Deklaration

```json
{
    "deck_rules": {
        "combos": [
            {
                "id": "combo_inf_mana",
                "name": "Dramatic Reversal + Isochron Scepter",
                "pieces": ["Dramatic Reversal", "Isochron Scepter"],
                "result": "Infinite mana with 2+ mana from rocks",
                "tags": ["infinite", "mana", "win-condition"]
            }
        ],
        "dont_combos": [
            {
                "id": "dc_stax_conflict",
                "name": "Stax piece conflicts with combo",
                "pieces": ["Rule of Law", "Thousand-Year Storm"],
                "reason": "Rule of Law prevents multiple spells per turn",
                "severity": "critical"
            }
        ]
    }
}
```

**Warum wichtig:**
- Combo-Tracking in Replay-Analyse
- Learning-Engine kann Combo-Assembling bewerten
- Anti-Synergien vermeiden Deck-Build-Fehler

---

## 4. Validierungsregeln

### 4.1 Deck-Count

- **Commander:** Exakt 1 Karte (oder 2 bei Partner)
- **Main:** Exakt 99 Karten (oder 98 bei Companion)
- **Summe:** Commander + Main = 100 Karten

### 4.2 Edition & Collector Number

- **Beide Felder:** Müssen zusammen angegeben werden
- **Format:** Edition = 3-4 Großbuchstaben (z.B. `"C16"`), Collector Number = String (z.B. `"35"`, `"263a"`)
- **Zweck:** Eindeutige Identifikation der exakten Artwork-Version

### 4.3 Primary Mechanic

- **Pflicht:** Jede Karte MUSS ein `primary_mechanic` haben
- **Unknown Values:** Erlaubt, aber Analysetools behandeln sie als generisch

---

## 5. Workflow: Von JSON zu Forge-Simulation

### 5.1 Schritt 1: Commander Decklist JSON erstellen

```bash
# Erstellen Sie Ihr Deck im JSON-Format
D:\Forge\commander-decklists\my_deck.json
```

### 5.2 Schritt 2: Konvertierung zu .dck-Format

```powershell
# Konvertieren Sie JSON zu Forge .dck
python convert_decklist_to_dck.py my_deck.json
# Output: %APPDATA%\Forge\decks\commander\my_deck.dck
```

### 5.3 Schritt 3: Simulation ausführen

```powershell
# 100 Spiele simulieren
.\run_commander_simulation.ps1 -DeckName "my_deck" -Games 100
```

### 5.4 Schritt 4: Statistiken analysieren

```powershell
# Statistiken extrahieren und JSON-Report generieren
python analyze_commander_stats.py
# Output: commander_simulation_report.json
```

---

## 6. Beispiel: Minimal Commander Deck

```json
{
    "format": "mtg-commander-decklist",
    "version": "1.0.0",
    "meta": {
        "deck_name": "Krenko Mob Boss",
        "format": "Commander",
        "colors": ["R"]
    },
    "commander": [
        {
            "quantity": 1,
            "name": "Krenko, Mob Boss",
            "edition": "M13",
            "collector_number": "138",
            "primary_mechanic": "token"
        }
    ],
    "main": [
        {
            "quantity": 1,
            "name": "Sol Ring",
            "edition": "C21",
            "collector_number": "263",
            "primary_mechanic": "ramp"
        },
        /* ... 98 weitere Karten ... */
    ]
}
```

---

## 7. Set-Code Referenz (häufige Sets)

| Code | Set Name | Hinweise |
|------|----------|----------|
| `C16` | Commander 2016 | Viele Staples (Atraxa, etc.) |
| `C21` | Commander 2021 | Neuere Drucke (Sol Ring, etc.) |
| `MH2` | Modern Horizons 2 | Neue Mechanics |
| `CMR` | Commander Legends | Draft-Set |
| `LTR` | Lord of the Rings | Special Edition |
| `SLD` | Secret Lair Drop | Promo-Versionen |
| `DOM` | Dominaria | Sagas, Legendaries |
| `ELD` | Throne of Eldraine | Adventures |

**Tipp:** Verwenden Sie [Scryfall](https://scryfall.com/) für genaue Set-Codes und Collector Numbers.

---

## 8. Häufige Fehler

### 8.1 Fehlende Edition/Collector Number

❌ **Falsch:**
```json
{
    "name": "Sol Ring",
    "primary_mechanic": "ramp"
}
```

✅ **Richtig:**
```json
{
    "name": "Sol Ring",
    "edition": "C21",
    "collector_number": "263",
    "primary_mechanic": "ramp"
}
```

### 8.2 Falsche Card Count

❌ **Falsch:** 98 Main-Karten ohne Companion
✅ **Richtig:** 99 Main-Karten (oder 98 + Companion in `commander`)

### 8.3 Inkonsistente Farbidentität

❌ **Falsch:** Commander ist WUBG, aber `colors: ["W", "U"]` in Meta
✅ **Richtig:** `colors: ["W", "U", "B", "G"]` matcht Commander

---

## 9. Performance-Empfehlungen für AI-Simulation

### 9.1 Mulligan-Optimierung

- **Aggressive Decks:** `min_value` senken (z.B. 3.0 statt 3.5 für Round 0)
- **Control Decks:** `land` value erhöhen auf 1.2, `card-draw` overrides nutzen
- **Combo Decks:** Combo-Pieces als `card_overrides` mit hoher Value (1.5+)

### 9.2 Deck-Geschwindigkeit vs. AI-Performance

- **Schnelle Decks** (Turns 5-7 zum Win): AI performt besser
- **Grindy Decks** (Turns 15+): AI kann suboptimal spielen
- **Empfehlung:** Testen Sie mit 10 Spielen, passen Sie Mulligan an, dann 100 Spiele

---

## 10. Tools & Ressourcen

| Tool | Zweck |
|------|-------|
| `convert_decklist_to_dck.py` | JSON → .dck Konverter |
| `run_commander_simulation.ps1` | Batch-Simulation Runner |
| `analyze_commander_stats.py` | Statistik-Extraktor |
| [Scryfall](https://scryfall.com/) | Set Codes & Collector Numbers |
| [EDHREC](https://edhrec.com/) | Commander-Statistiken |

---

## 11. Support & Troubleshooting

### Problem: "Could not load deck"
- **Ursache:** .dck-Format fehlerhaft oder Karte nicht in Forge-Datenbank
- **Lösung:** Überprüfen Sie Kartennamen mit Forge's `res/cardsfolder/`

### Problem: "Deck hash mismatch"
- **Ursache:** Commander oder Main-Sektion wurde geändert
- **Lösung:** Deck-Hash neu berechnen (automatisch bei .dck-Export)

### Problem: Simulation hängt
- **Ursache:** Komplexe Board-States oder Infinite-Loops
- **Lösung:** Timeout erhöhen mit `-c 180` (3 Minuten statt 2)

---

**Letzte Aktualisierung:** 2026-04-07

