# Optimisation de performance (étape 5)

**Projet Boîte Noire (Pigeon)**

Toutes les mesures ont été faites sur MongoDB 8.0.32 en local, avec les 149 997 événements générés (seed 42), période d'un an (2025-10-05 au 2026-10-04, UTC). Chaque mesure est la médiane de 3 `explain("executionStats")` consécutifs (le temps est `executionTimeMillis`). Les pipelines mesurés sont exactement ceux envoyés par `AnalyticsService` (relevés dans les logs DEBUG de `MongoTemplate`).

## 1. Les 4 requêtes sans index (hors `_id`)

| Requête | 3 mesures (ms) | Médiane | Accès | Documents examinés | Documents passés au pipeline | Lignes renvoyées |
|---|---|---|---|---|---|---|
| `top-users` | 70, 68, 51 | 68 ms | COLLSCAN | 149 997 | 149 997 | 10 |
| `errors` | 57, 50, 51 | 51 ms | COLLSCAN | 149 997 | 8 821 | 2 930 |
| `latency` | 159, 158, 143 | **158 ms** | COLLSCAN | 149 997 | 82 881 | 8 |
| `funnel` | 115, 89, 84 | 89 ms | COLLSCAN | 149 997 | 34 594 | 1 |

« Documents passés au pipeline » = documents qui satisfont le `$match` (compté avec `countDocuments`, même filtre).

## 2. Requête choisie : `latency`

Les 4 requêtes lisent les 149 997 documents (COLLSCAN). Ce qui les distingue, c'est le temps : `latency` est la plus lente (158 ms, soit 1,7 à 3 fois les autres), et c'est elle qui fait travailler le plus le `$group` (82 881 documents avec `$avg`, `$percentile` et `$max`). C'est donc la plus coûteuse mesurée.

## 3. Explain avant

Sortie complète : [measures/explain-before.json](measures/explain-before.json).

- Accès : `COLLSCAN` (filtre sur `type` et `timestamp`)
- Documents examinés : 149 997 (clés examinées : 0)
- Documents retournés par l'accès (entrée du `$group`) : 82 881
- Temps : 158 ms (médiane de 159, 158, 143)

## 4. Index créé

```js
db.events.createIndex({ type: 1, timestamp: 1 }, { name: "type_1_timestamp_1" })
```

### Ordre des champs : Equality, Sort, Range

- **Equality** : `type` (`type = "API_CALL"`) vient en premier.
- **Sort** : aucun champ. Le `$sort` du pipeline est placé après le `$group`, sur des champs calculés (`p95Ms`) : aucun index ne peut le servir.
- **Range** : `timestamp` (`$gte` / `$lt`) vient en dernier.

Avec `type` en premier, MongoDB se place directement sur la plage `API_CALL` de l'index, puis lit uniquement la plage de dates dans cette zone : les bornes de l'index sont serrées sur les deux champs.

Sélectivité : `type` n'a que 5 valeurs et `API_CALL` en représente 55 %, donc seul, il filtre peu. C'est la combinaison avec `timestamp` qui est sélective (surtout sur une période courte). La règle ESR passe avant la cardinalité : une égalité avant une plage donne des bornes serrées.

Ordre inverse `{ timestamp: 1, type: 1 }`, mesuré avec un `hint` sur le même pipeline :

| Période | `{type, timestamp}` | `{timestamp, type}` |
|---|---|---|
| 1 an | 132 ms, 82 881 clés examinées | 204 ms, 149 854 clés examinées |
| 1 mois (mars 2026) | 12 ms, 7 363 clés examinées | 19 ms, 13 179 clés examinées |

Avec la plage en premier, `type` ne peut pas resserrer les bornes : l'index parcourt les clés de tous les types de la plage de dates, puis filtre.

### Index couvrant

Un index couvrant est possible : `{ type: 1, timestamp: 1, "payload.endpoint": 1, "payload.method": 1, "payload.durationMs": 1 }` contient tous les champs lus par le pipeline. Mesuré, le stage FETCH disparaît (0 document examiné), mais :

| Période | `{type, timestamp}` | Index couvrant |
|---|---|---|
| 1 an | 132 ms | 172 ms |
| 1 mois | 12 ms | 14 ms |

L'index couvrant est plus lent ici et plus gros (3 780 608 octets contre 1 708 032), et il est lié à ce seul pipeline (si l'on ajoute un champ lu, il n'est plus couvrant). Il n'est pas pertinent ; on garde l'index simple.

## 5. Explain après

Sortie complète : [measures/explain-after.json](measures/explain-after.json).

- Accès : `IXSCAN` sur `type_1_timestamp_1`, puis `FETCH`
- Documents examinés : 82 881 (clés examinées : 82 881)
- Documents retournés par l'accès : 82 881
- Temps : 134 ms (médiane de 147, 134, 131)

## 6. Avant / après (`latency`, même pipeline, même période)

| | Avant | Après |
|---|---|---|
| Stage d'accès | COLLSCAN | IXSCAN |
| Documents examinés (1 an) | 149 997 | 82 881 |
| Temps (1 an, médiane) | 158 ms | 134 ms |
| Documents examinés (1 mois) | 149 997 | 7 363 |
| Temps (1 mois, médiane) | 53 ms | 13 ms |

Résultat de l'endpoint strictement identique avant et après : les réponses JSON des 4 endpoints sur l'année complète sont identiques octet par octet (vérifié avec `cmp`).

Précision sur la mesure d'un an : une autre série de 3 mesures sans index, faite plus tard pour comparer les candidats, a donné 133 ms (132, 133, 141). Le gain en temps sur un an est donc dans la marge de bruit ; seul le nombre de documents examinés est un résultat net. Un an de données, c'est 55 % de la collection pour `API_CALL` : l'index ne peut pas faire beaucoup mieux qu'un scan complet.

## 7. Effet de l'index sur les 3 autres analyses

| Analyse | Verdict | 1 an (avant → après) | 1 mois (avant → après) |
|---|---|---|---|
| `errors` | profite nettement : IXSCAN, seuls les ERROR sont lus | 51 → 20 ms, 8 821 documents examinés | 36 → 2 ms, 776 documents examinés |
| `funnel` | profite sur une période courte : IXSCAN sur LOGIN, API_CALL et PAYMENT | 89 → 100 ms, 109 266 documents examinés (pas de gain) | 66 → 10 ms, 9 640 documents examinés |
| `top-users` | aucun effet : le `$match` ne porte que sur `timestamp`, l'index commence par `type`, donc COLLSCAN avant et après | 68 → 46 ms (même plan, variation de mesure) | 35 → 34 ms |

## Création, retour en arrière

Création (reproductible, idempotente, la JVM s'arrête à la fin) :

```bash
cd boitenoire
mvn spring-boot:run -Dspring-boot.run.profiles=optimize
```

Équivalent mongosh :

```js
use pigeon
db.events.createIndex({ type: 1, timestamp: 1 }, { name: "type_1_timestamp_1" })
db.events.getIndexes()
```

Retour à l'état « avant » :

```js
db.events.dropIndex("type_1_timestamp_1")
```

Le générateur supprime les collections avant de les regénérer : le relancer supprime aussi l'index. Il faut alors relancer le profil `optimize`.

## Constat

1. Sans index, les 4 analyses font un COLLSCAN de 149 997 documents et `latency` est la plus coûteuse (158 ms).
2. L'index `{ type, timestamp }` ne fait passer `latency` que de 158 à 134 ms sur un an (55 % des documents sont lus), mais de 53 à 13 ms sur un mois (7 363 documents examinés).
3. Le même index accélère aussi `errors` (51 à 20 ms sur un an, 36 à 2 ms sur un mois) et `funnel` sur une période courte, mais pas `top-users`.
