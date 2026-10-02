# Schéma des événements

**Projet Boîte Noire (Pigeon)**

## Champs communs

Tous les événements de la collection `events` partagent cette structure de base :

| Champ | Type | Rôle |
|---|---|---|
| `_id` | ObjectId | identifiant du document, généré par MongoDB |
| `type` | string | type de l'événement (`LOGIN`, `PAYMENT`, `ERROR`, `API_CALL`, `NOTIFICATION`) |
| `userId` | ObjectId | référence vers la collection `users` |
| `timestamp` | date | date et heure de l'événement |
| `payload` | object | contenu propre au type d'événement |

## Types d'événements

### LOGIN

```json
{
  "_id": "ObjectId(...)",
  "type": "LOGIN",
  "userId": "ObjectId(...)",
  "timestamp": "2026-09-29T10:15:00Z",
  "payload": {
    "ip": "192.168.1.10",
    "device": "mobile",
    "success": true
  }
}
```

### PAYMENT

```json
{
  "_id": "ObjectId(...)",
  "type": "PAYMENT",
  "userId": "ObjectId(...)",
  "timestamp": "2026-09-29T10:20:00Z",
  "payload": {
    "amount": 9.99,
    "currency": "EUR",
    "plan": "PRO",
    "status": "SUCCESS"
  }
}
```

### ERROR

```json
{
  "_id": "ObjectId(...)",
  "type": "ERROR",
  "userId": "ObjectId(...)",
  "timestamp": "2026-09-29T10:22:00Z",
  "payload": {
    "service": "messaging",
    "message": "Connection refused",
    "severity": "HIGH"
  }
}
```

### API_CALL

```json
{
  "_id": "ObjectId(...)",
  "type": "API_CALL",
  "userId": "ObjectId(...)",
  "timestamp": "2026-09-29T10:25:00Z",
  "payload": {
    "endpoint": "/messages",
    "method": "POST",
    "durationMs": 120,
    "statusCode": 200
  }
}
```

### NOTIFICATION

```json
{
  "_id": "ObjectId(...)",
  "type": "NOTIFICATION",
  "userId": "ObjectId(...)",
  "timestamp": "2026-09-29T10:30:00Z",
  "payload": {
    "channel": "push",
    "title": "Nouveau message",
    "read": false
  }
}
```

## Collection `users`

```json
{
  "_id": "ObjectId(...)",
  "name": "Achraf",
  "email": "achraf@example.com",
  "createdAt": "2025-01-15T08:00:00Z"
}
```

## Choix d'embedding

Le champ `payload` est **embedded** dans chaque événement : il est petit, fixe, et n'a de sens que rattaché à son événement précis. Le lire ne demande aucun accès supplémentaire.

## Choix de referencing

Le champ `userId` **référence** la collection `users` plutôt que d'embedder le profil complet de l'utilisateur dans chaque événement. Le nombre d'événements par utilisateur est illimité : les embedder dans le document utilisateur ferait dépasser la limite de 16 Mo par document et rendrait les analyses transverses (top utilisateurs, répartition des erreurs) beaucoup plus lentes.