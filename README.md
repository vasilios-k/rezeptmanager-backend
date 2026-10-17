# Rezeptmanager

Webanwendung zum Anlegen und Anzeigen von Rezepten.
Das Projekt wird als Einzelprojekt umgesetzt.

## Technologien
- Java 25
- Spring Boot 4.1.1
- Gradle mit Gradle Wrapper

## Stand: Milestone 1
- Spring-Boot-Anwendung mit GET-Route `/api/recipes`
- Rezeptmodell mit Zutaten, Mengen und Einheiten
- Gemeinsame Zutaten können in mehreren Rezepten verwendet werden
- Zwei feste Beispielrezepte; noch keine Datenbankanbindung

## Lokal starten
Im Backend-Projektordner: `.\gradlew.bat bootRun`

Anschließend aufrufen: http://localhost:8080/api/recipes