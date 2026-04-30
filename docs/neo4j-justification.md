# Begründung: Neo4j als Datenbanktechnologie

## Domäne

Das Domänenmodell beschreibt Nutzer in einem sozialen Netzwerk. Kernoperationen:
- Freundschaften verwalten (`addFriend`, `removeFriend`, `getFriends`)
- Freunde zweiter Ordnung ermitteln (`getFriendsOfFriends`), sortiert nach gemeinsamen Freunden

Diese Operationen sind **Graph-Traversals** — sie folgen Kanten (Beziehungen) zwischen Knoten (Nutzern).

## Warum Neo4j?

### 1. Natürliche Modellierung

Freundschaften sind Kanten im Graphen:
```
(alice:User)-[:FRIENDS_WITH]->(bob:User)
```
Das Modell entspricht direkt der Domäne — keine JOIN-Tabellen, keine Fremdschlüssel-Arrays.

### 2. Effiziente Graphabfragen

`getFriendsOfFriends` in Cypher:
```cypher
MATCH (u:User {id: $id})-[:FRIENDS_WITH]->(f)-[:FRIENDS_WITH]->(fof)
WHERE NOT (u)-[:FRIENDS_WITH]->(fof) AND fof <> u
WITH fof, count(f) AS commonCount
ORDER BY commonCount DESC
RETURN fof
```

Äquivalent in SQL würde drei Self-Joins auf einer `friendships`-Tabelle benötigen und skaliert quadratisch mit der Datenmenge. Neo4j traversiert Kanten in O(1) pro Hop — unabhängig von der Gesamtgröße des Graphen (Index-Free Adjacency).

### 3. Skalierbarkeit für soziale Graphen

Neo4j ist auf tiefe Traversals optimiert. Bei 1 Million Nutzern bleibt eine 2-Hop-Abfrage schnell, weil die Engine nur die relevanten Kanten liest — nicht die gesamte Tabelle.

### 4. Schemaflexibilität

Nutzerprofile können erweitert werden (neue Felder wie `profilbild`) ohne Migrationsskripte. Neo4j-Knoten sind schema-optional.

## Alternativen und Abwägung

| Technologie | Stärke | Schwäche für diese Domäne |
|-------------|--------|--------------------------|
| PostgreSQL | ACID, reif, weit verbreitet | Self-Joins für Graphabfragen teuer |
| MongoDB | Flexibles Schema | Keine nativen Graphabfragen |
| Redis | Sehr schnell für einfache Strukturen | Kein Graphmodell |
| **Neo4j** | **Native Graphabfragen, Cypher, Index-Free Adjacency** | **Weniger verbreitet als relationale DBs** |

## Fazit

Neo4j ist die natürlichste Wahl für ein soziales Netzwerk mit Freundschaftsbeziehungen und Empfehlungsabfragen (Freunde zweiter Ordnung). Die Abfragesprache Cypher bildet die Domäne direkt ab und ist effizienter als SQL-Äquivalente für Graph-Traversals.
