# 📦 Projet Boîte Noire (Pigeon)

Service Spring Boot d'ingestion et d'analyse de journaux d'événements (logs) couplé à une base documentaire **MongoDB**.

---

## 1. Prérequis et configuration de l'environnement

### A. Vérifier le JDK (Java)
Vérifiez que Java 21+ est correctement installé sur votre machine :
```bash
java --version
where javac
```

### B. Installer Maven et configurer les variables d'environnement (Windows)
1. Téléchargez Apache Maven et décompressez-le (ex: `C:\Program Files\apache-maven-x.x.x`).
2. Ouvrez les **Variables d'environnement système** :
   * Ajoutez une nouvelle variable système :
     * Nom : `JAVA_HOME` ➔ Valeur : Chemin vers votre JDK (ex: `C:\Program Files\Java\jdk-21`)
   * Ajoutez une nouvelle variable système :
     * Nom : `MAVEN_HOME` ➔ Valeur : Chemin vers Maven (ex: `C:\Program Files\apache-maven-x.x.x`)
   * Modifiez la variable système `Path` :
     * Cliquez sur **Modifier** > **Nouveau** > ajoutez `%MAVEN_HOME%\bin`.
3. Vérifiez l'installation dans un nouveau terminal :
```bash
mvn --version
```

---

## 2. Génération des données (Étape 3 : X événements)

Le projet intègre un générateur qui simule 1 000 000 événements sur une année.

### Commande de génération :
Sous PowerShell / Terminal :
```powershell
./mvnw spring-boot:run "-Dspring-boot.run.profiles=generator"
```
*(ou `mvn spring-boot:run "-Dspring-boot.run.profiles=generator"`)*

> **Remarque importante sur la base MongoDB** :  
> Par défaut, si `spring.data.mongodb.database=` n'est pas renseigné dans `src/main/resources/application.properties`, les données sont enregistrées dans la base par défaut nommée **`test`**.  
> Pour cibler une base dédiée, indiquez par exemple :  
> `spring.data.mongodb.database=boite_noire`

---

## 3. Vérification des données dans MongoDB (`mongosh`)

Ouvrez un terminal `mongosh` pour inspecter la base de données :

```javascript
// Lister les bases de données
show dbs

// Se positionner sur la base (test par défaut ou boite_noire)
use test

// Lister les collections existantes
show collections

// Compter le nombre d'événements générés (doit être >= 100 000)
db.events.countDocuments()

// Voir un exemple de document
db.events.findOne()

// Consulter les premiers documents
db.events.find().limit(5)
```

---

##  4. Démarrage du serveur et consultation de l'API (Swagger)

### Lancer l'application en mode normal (serveur API) :
```powershell
mvn spring-boot:run
```
*(ou `./mvnw spring-boot:run`)*

### Accéder à la documentation interactive Swagger :
Une fois l'application démarrée, rendez-vous sur :  
 **[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)** *(ou `http://localhost:8080/swagger-ui.html`)*

### Format des dates attendu pour les filtres :
Les endpoints acceptent les dates au format standard **ISO 8601 UTC** :
```text
2026-10-01T00:00:00Z
```

Exemple de test pour une période annuelle :
* `beginDate` : `2026-01-01T00:00:00Z`
* `endDate` : `2026-12-31T23:59:59Z`

---

## 📊 5. Endpoints d'analyse disponibles (`/api/analytics`)

* **`GET /api/analytics/top-users`** : Top des utilisateurs les plus actifs sur une période donnée en paramètre.
* **`GET /api/analytics/errors-by-day`** : Répartition et décompte des erreurs par type et par jour sur une période.
* **`GET /api/analytics/endpoint-performance`** : Temps de réponse moyen et percentile 95 (`p95`) par endpoint.
