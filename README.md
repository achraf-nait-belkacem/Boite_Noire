# La_boite_noire

## Générer les données

Démarrer MongoDB (port 27017), puis :

```bash
cd boitenoire
mvn spring-boot:run -Dspring-boot.run.profiles=generator
```

La commande vide les collections `users` et `events` (elles sont supprimées puis recréées sans index autre que `_id`), puis génère ~2 000 utilisateurs et ~150 000 événements sur les 365 derniers jours. La seed est configurable (`generator.seed` dans `application.properties`, ou `-Dspring-boot.run.arguments=--generator.seed=7`). Un démarrage sans profil ne génère rien.

## Lancer l'API

Démarrer MongoDB (port 27017) avec les données générées (voir « Générer les données »), puis :

```bash
cd boitenoire
mvn spring-boot:run
```

L'API écoute sur http://localhost:8080. La documentation interactive (Swagger UI) est sur http://localhost:8080/swagger-ui.html et la spécification OpenAPI sur http://localhost:8080/v3/api-docs.

### Endpoints d'analyse (GET, préfixe `/api/analytics`)

Les dates sont au format `yyyy-MM-dd`, interprétées en UTC, `to` est inclus. Une date invalide, un paramètre obligatoire manquant ou `from` après `to` renvoie une erreur 400 avec un message explicite. Chaque analyse est une seule agrégation MongoDB exécutée côté base.

| Endpoint | Rôle | Exemple |
|---|---|---|
| `/top-users` | utilisateurs les plus actifs (`limit` de 1 à 100, 10 par défaut) | `curl "http://localhost:8080/api/analytics/top-users?from=2026-03-01&to=2026-03-31&limit=10"` |
| `/errors` | erreurs par jour, service et message | `curl "http://localhost:8080/api/analytics/errors?from=2026-03-01&to=2026-03-31"` |
| `/latency` | nombre d'appels, moyenne, p95 et max par endpoint (période optionnelle) | `curl "http://localhost:8080/api/analytics/latency?from=2026-03-01&to=2026-03-31"` |
| `/funnel` | entonnoir LOGIN réussi, puis `POST /messages`, puis PAYMENT `SUCCESS` | `curl "http://localhost:8080/api/analytics/funnel?from=2026-03-01&to=2026-03-31"` |

## Optimisation

L'étape 5 ajoute un index composé `{ type: 1, timestamp: 1 }` sur `events` (mesures et justification dans `docs/performance.md`).

Créer l'index (idempotent, la JVM s'arrête à la fin) :

```bash
cd boitenoire
mvn spring-boot:run -Dspring-boot.run.profiles=optimize
```

Équivalent mongosh :

```js
use pigeon
db.events.createIndex({ type: 1, timestamp: 1 }, { name: "type_1_timestamp_1" })
```

Revenir à l'état « avant » (sans index) :

```js
db.events.dropIndex("type_1_timestamp_1")
```

Relancer le générateur supprime les collections, donc aussi l'index : relancer ensuite le profil `optimize`.
