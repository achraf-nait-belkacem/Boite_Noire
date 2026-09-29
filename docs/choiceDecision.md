# Note de décision : relationnel ou documentaire

Projet Boîte Noire (Pigeon) :
    Auteurs : achraf, hugo
    Date : 29/09/2026

## 1. Contexte

Qu'est-ce qu'on doit stocker ? (connexions, paiements, erreurs applicatives, appels API, notifications). Ces événements portent-ils les mêmes informations ?
non par exemple si on prend deux evenement different (payment,error) ils ont pas les meme champs 
PAYMENT : "amount","currency","plan"
ERROR : "service","message","severity" 



## 2. Modélisation relationnelle (SQL) et ses limites

Option A : une seule table `events` pour tous les types d'événements. Combien de colonnes faudrait-il ? Combien de valeurs `NULL` par ligne en moyenne ? Option B : une table par type d'événement. Que devient une recherche comme « tout ce que fait l'utilisateur 42 » ? Que se passe-t-il quand on ajoute un sixième type d'événement dans six mois ?


## 3. Modélisation documentaire (MongoDB)

du coup la solution est de utiliser la Modelisation documentaire en utilisant la collection dans mongoDB et du coup a chaque ajout d'evenement en creer just une nouvelle collection avec la structure mongoDB en bloc de JSON avec un champ commun qu'est le user_id pour permettre la recherche transversal


## 4. Conclusion et critère décisif

Utilisation de MongoDB pour la structuration des donnees de logs.
