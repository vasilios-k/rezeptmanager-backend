# Rezeptmanager – Backend

[![Backend CI](https://github.com/vasilios-k/rezeptmanager-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/vasilios-k/rezeptmanager-backend/actions/workflows/ci.yml)

REST-Backend für eine persönliche Rezeptsammlung.
Das Projekt wird als Einzelprojekt im Modul Web-Technologien umgesetzt.

Das Backend speichert Rezepte in PostgreSQL, prüft die Zugehörigkeit
zum angemeldeten Benutzer und stellt eine Anbindung an die externe
Preisdatenquelle Open Prices bereit.

## Links

- [Webanwendung](https://rezeptmanager-frontend.onrender.com/)
- [Frontend-Repository](https://github.com/vasilios-k/rezeptmanager-frontend)
- [Öffentlicher Healthcheck](https://rezeptmanager-backend.onrender.com/api/health)
- [Rezept-Endpunkt – Anmeldung erforderlich](https://rezeptmanager-backend.onrender.com/api/recipes)

## Technologien

- Java 25
- Spring Boot 4.1.1
- Gradle Wrapper
- Spring Web MVC
- Spring Data JPA und Hibernate
- PostgreSQL
- Jakarta Bean Validation
- Spring Security als OAuth2 Resource Server
- Auth0 mit signierten JWT-Zugriffstokens
- JUnit, Spring Boot Test und Spring Security Test
- H2 für Integrationstests
- Docker, GitHub Actions und Render

## Datenmodell

| Modell | Aufgabe |
|---|---|
| `Recipe` | Rezept mit Titel, Zubereitung, Dauer, Portionen und Besitzerzuordnung |
| `Ingredient` | Wiederverwendbare Zutat mit Name und normalisiertem Suchnamen |
| `RecipeIngredient` | Verbindung zwischen Rezept und Zutat mit rezeptbezogener Menge und Einheit |
| `Unit` | Unterstützte Maßeinheiten als Enum |

Eine Zutat wie Mehl kann von mehreren Rezepten verwendet werden.
Die jeweilige Menge steht an der Verbindung `RecipeIngredient`.

Beim Löschen eines Rezepts werden seine Zutatenzuordnungen entfernt.
Gemeinsam verwendete Zutaten bleiben erhalten.

Unterstützte Einheiten:

```text
GRAM
KILOGRAM
MILLILITER
LITER
TEASPOON
TABLESPOON
PIECE
PINCH
```

Das Frontend zeigt dazu deutsche Bezeichnungen beziehungsweise
Abkürzungen an.

## REST-Schnittstelle

Basisadresse:

```text
https://rezeptmanager-backend.onrender.com
```

| Methode | Route | Funktion | Erfolgsstatus | Anmeldung |
|---|---|---|---|---|
| GET | `/api/health` | Erreichbarkeit prüfen | 200 | Öffentlich |
| GET | `/api/recipes` | Eigene Rezepte auflisten | 200 | Erforderlich |
| GET | `/api/recipes/{id}` | Eigenes Rezept laden | 200 | Erforderlich |
| POST | `/api/recipes` | Rezept anlegen | 201 | Erforderlich |
| PUT | `/api/recipes/{id}` | Eigenes Rezept aktualisieren | 200 | Erforderlich |
| DELETE | `/api/recipes/{id}` | Eigenes Rezept löschen | 204 | Erforderlich |
| GET | `/api/prices` | Produktpreise suchen | 200 | Erforderlich |

Geschützte Anfragen benötigen diesen Header:

```http
Authorization: Bearer <access_token>
```

Das Frontend übernimmt die Tokenbeschaffung und die Übermittlung
des Headers über das Auth0-SDK.

Wichtige Fehlerantworten:

| Status | Bedeutung |
|---|---|
| 400 | Ungültige Anfrage oder Eingabedaten |
| 401 | Fehlendes, ungültiges oder abgelaufenes Zugriffstoken |
| 404 | Rezept nicht vorhanden oder nicht dem angemeldeten Benutzer zugeordnet |
| 502 | Ungültige Antwort des externen Preisdienstes |
| 503 | Externer Preisdienst vorübergehend nicht erreichbar |

### Beispiel: Rezept anlegen

`POST /api/recipes` mit `Content-Type: application/json`:

```json
{
  "title": "Pfannkuchen",
  "ingredients": [
    {
      "ingredientName": "Mehl",
      "quantity": 200,
      "unit": "GRAM"
    },
    {
      "ingredientName": "Eier",
      "quantity": 2,
      "unit": "PIECE"
    },
    {
      "ingredientName": "Milch",
      "quantity": 300,
      "unit": "MILLILITER"
    }
  ],
  "instructions": "Zutaten verrühren.\nPortionsweise in einer Pfanne ausbacken.",
  "prepTimeMinutes": 25,
  "servings": 2
}
```

Das Backend vergibt die Rezept-ID und ordnet das Rezept dem
angemeldeten Benutzer zu. Die Antwort enthält das gespeicherte Rezept
und einen `Location`-Header mit seiner API-Adresse.

`PUT /api/recipes/{id}` verwendet dieselbe Anfragestruktur.

### Beispiel: Preissuche

```text
/api/prices?query=Mehl&unit=GRAM&page=1
```

Die Suche verarbeitet Suchbegriffe mit 2 bis 60 Zeichen und
Seitennummern von 1 bis 100.

Berücksichtigt werden passende, erfasste Normalpreise aus Deutschland
in Euro. Produkt, Packungsgröße, Händler und Datum werden zurückgegeben.

## Eingabevalidierung

Anfragen werden im Backend über validierte Request-DTOs geprüft.

Unter anderem gelten folgende Regeln:

- Titel und Zubereitung dürfen nicht leer sein.
- Ein Rezept benötigt mindestens eine Zutat.
- Zutatenmengen müssen positiv sein.
- Mengen unterstützen bis zu drei Nachkommastellen.
- Die Portionszahl muss eine positive ganze Zahl sein.
- Die Zubereitungsdauer darf nicht negativ sein.
- Eine Zutat darf innerhalb eines Rezepts nur einmal vorkommen.
- Namen, Texte und Zutatenlisten haben Größenbegrenzungen.

## Authentifizierung und Benutzertrennung

Auth0 übernimmt Registrierung und Anmeldung.

Das Backend validiert bei Zugriffstokens:

- Signatur mit RS256
- Aussteller (`iss`)
- API-Identifier (`aud`)
- Gültigkeitszeitraum
- vorhandene Benutzerkennung (`sub`)

Die Besitzerkennung eines Rezepts stammt aus dem geprüften Token.
Rezeptabfragen und Änderungen berücksichtigen diese Kennung im Backend.

Anfragen auf fremde Rezept-IDs liefern HTTP 404.

### Auth0-Konfiguration

Für das Frontend wird eine Single Page Application verwendet.

In Auth0 ist die Backend-API mit diesem Identifier registriert:

```text
https://rezeptmanager-backend.onrender.com
```

Konfiguration:

- JWT-Signaturverfahren: RS256
- User-Delegated Access: Per-app authorization
- Die Frontend-Anwendung erhält ausdrücklich User-Delegated Access.
- Allow Offline Access ist aktiviert.
- Für die SPA sind Refresh Tokens und Refresh Token Rotation aktiviert.

Bei der Frontend-Anwendung sind diese Adressen als
Allowed Callback URLs, Allowed Logout URLs und Allowed Web Origins
eingetragen:

```text
http://localhost:5173, https://rezeptmanager-frontend.onrender.com
```

## Konfiguration des Backends

| Umgebungsvariable | Bedeutung |
|---|---|
| `DB_URL` | JDBC-Verbindungsadresse der PostgreSQL-Datenbank |
| `DB_USERNAME` | Datenbankbenutzer |
| `DB_PASSWORD` | Datenbankpasswort |
| `CORS_ALLOWED_ORIGINS` | Erlaubte Frontend-Ursprünge, durch Kommas getrennt |
| `AUTH_ISSUER` | Auth0-Ausstelleradresse mit abschließendem `/` |
| `AUTH_AUDIENCE` | Identifier der registrierten Backend-API |
| `LEGACY_OWNER_SUBJECT` | Optional: Auth0-Benutzerkennung für bisherige Rezepte ohne Besitzer |
| `PORT` | Serverport; lokal standardmäßig 8080, auf Render von Render gesetzt |

Für dieses Projekt gelten:

```dotenv
AUTH_ISSUER=https://rezeptmanager.eu.auth0.com/
AUTH_AUDIENCE=https://rezeptmanager-backend.onrender.com
CORS_ALLOWED_ORIGINS=http://localhost:5173,https://rezeptmanager-frontend.onrender.com
```

Eine PostgreSQL-JDBC-Adresse hat beispielsweise diesen Aufbau:

```text
jdbc:postgresql://<host>:5432/<database>
```

Datenbankzugangsdaten werden über Umgebungsvariablen bereitgestellt.

Wenn `LEGACY_OWNER_SUBJECT` gesetzt ist, werden beim Start bisherige
Rezepte ohne Besitzer dieser ausdrücklich angegebenen Benutzerkennung
zugeordnet.

## Lokal starten

Voraussetzungen:

- Java 25
- Erreichbare PostgreSQL-Datenbank
- Gesetzte Datenbank-Umgebungsvariablen

```powershell
git clone https://github.com/vasilios-k/rezeptmanager-backend.git
cd rezeptmanager-backend
```

Beispielkonfiguration für eine lokale Datenbank:

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/rezeptmanager"
$env:DB_USERNAME="rezeptmanager_user"
$env:DB_PASSWORD="<dein lokales Datenbankpasswort>"
.\gradlew.bat bootRun
```

Die angegebenen Datenbankwerte müssen zur tatsächlich eingerichteten
Datenbank passen.

Anschließend ist der öffentliche Healthcheck erreichbar:

```text
http://localhost:8080/api/health
```

Zum Verwenden dieses Backends im lokalen Frontend dessen
`VITE_API_BASE_URL` auf `http://localhost:8080` setzen.

## Tests und Build

Unter Windows:

```powershell
.\gradlew.bat clean build
```

Unter Linux oder macOS:

```bash
bash ./gradlew clean build
```

Die Testsuite umfasst 33 Tests.

Die Datenbanktests verwenden das Profil `test` mit einer H2-Datenbank
im PostgreSQL-Modus. Antworten des externen Preisdienstes werden in
den entsprechenden Tests simuliert.

Geprüft werden unter anderem:

- Anlegen, Lesen, Aktualisieren und Löschen von Rezepten
- Validierung und Wiederverwendung von Zutaten
- Erhalt gemeinsam genutzter Zutaten beim Löschen
- Ungültige und fehlende Zugriffstokens
- Trennung mehrerer Benutzer
- Schutz fremder Rezept-IDs
- Besitzerzuordnung bisheriger Rezepte
- CORS-Anfragen und öffentlicher Healthcheck
- Verarbeitung und Filterung externer Preisdaten
- Stückangaben bei Produktpackungen

Der HTML-Testbericht wird unter
`build/reports/tests/test/index.html` erzeugt.

## Projektstruktur

| Package | Aufgabe |
|---|---|
| `controller` | REST-Routen und HTTP-Antworten |
| `dto.request` | Validierte Eingabestrukturen |
| `dto.response` | Ausgabestrukturen der API |
| `service` | Rezeptverwaltung und Verarbeitung von Preisdaten |
| `repository` | Datenbankzugriff |
| `model` | Persistente Datenmodelle und Einheiten |
| `security` | Zugriff auf die geprüfte Benutzerkennung |
| `config` | Sicherheit, CORS und Besitzerzuordnung bisheriger Daten |
| `integration` | HTTP-Anbindung an Open Prices |

## Continuous Integration und Deployment

GitHub Actions führt bei Pushes und Pull Requests die Backendtests
und den Build mit Java 25 und dem Gradle Wrapper aus.

Das Dockerfile verwendet einen Build-Schritt und ein getrenntes
Laufzeit-Image für die Spring-Boot-Anwendung.

Render-Konfiguration:

| Einstellung | Wert |
|---|---|
| Service-Typ | Web Service |
| Runtime | Docker |
| Branch | `main` |
| Dockerfile | `Dockerfile` im Repository-Hauptordner |
| Health Check Path | `/api/health` |
| Auto-Deploy | After CI Checks Pass |

Die PostgreSQL-Verbindung und Auth0-Konfiguration werden in Render
über Environment Variables gesetzt.

## Preisdaten und Betriebsgrenzen

Die externe Datenquelle ist
[Open Prices / Open Food Facts](https://prices.openfoodfacts.org/).

Suchergebnisse werden vorübergehend im Arbeitsspeicher
zwischengespeichert. Preisbeobachtungen können veraltet sein oder für
einzelne Zutaten fehlen. Das Frontend ermöglicht deshalb auch die
Eingabe eigener Preise.

Bei einem kostenlosen Render-Web-Service kann der erste Aufruf nach
einer Pause länger dauern, weil der Dienst zunächst starten muss.

Kostenlose Render-Postgres-Datenbanken laufen nach 30 Tagen ab.
Für den Weiterbetrieb müssen Verfügbarkeit und Datensicherung
rechtzeitig organisiert werden.

Weitere Informationen:
[Grenzen kostenloser Render-Instanzen](https://render.com/docs/free).