# Rapport qualité SonarCloud — Avant / Après ajout des 2 fonctionnalités

**Projet SonarCloud** : `Andrimirana_Skillhub-BC03`
**Organisation** : `andrimirana`
**URL** : https://sonarcloud.io/project/overview?id=Andrimirana_Skillhub-BC03

**Périmètre du rapport** :
- Avant : `dev` au commit `81ebe24` (analyse du 2026-05-08 06:21)
- Après : impact des 2 PRs ouvertes vers `dev` :
  - PR #3 — `feat: notation des formations` (branche `feature/notation-formations`)
  - PR #4 — `feat: liste des apprenants inscrits` (branche `feature/liste-apprenants`)

---

## 1. Tableau comparatif — Vue Overall Code (cumul projet)

| Métrique                 | AVANT (dev) | APRÈS PR #3 (cumul) | APRÈS PR #4 (cumul) | Évolution               |
| ------------------------ | -----------:| -------------------:| -------------------:| ----------------------- |
| **Quality Gate**         | ❌ ERROR    | ✅ OK               | ✅ OK               | **passe en vert**       |
| **Bugs**                 | 0           | 0                   | 0                   | stable (rating A)       |
| **Vulnerabilities**      | 3           | 0 ¹                 | 0 ¹                 | aucune nouvelle ajoutée |
| **Code Smells**          | 172         | 1 ¹                 | 4 ¹                 | +5 cumulés ajoutés      |
| **Security Hotspots**    | 7           | 0 ¹                 | 0 ¹                 | aucun nouveau ajouté    |
| **Duplications (lignes)** | 19.7 %     | 19.7 %              | 19.9 %              | +0.2 pt (cosmétique)    |
| **Coverage**             | n/d ²       | n/d ²               | n/d ²               | non mesurée             |
| **Reliability Rating**   | A (1.0)     | A (1.0)             | A (1.0)             | stable                  |
| **Security Rating**      | C (3.0)     | A (1.0) ¹           | A (1.0) ¹           | hérité dev (préexistant)|
| **Maintainability (SQALE)** | A (1.0)  | A (1.0)             | A (1.0)             | stable                  |
| **Lines of code (ncloc)** | 11 761     | +380 nouvelles      | +213 nouvelles      | +593 lignes au total    |

> ¹ Les valeurs « overall » d'une PR ne couvrent que les fichiers modifiés par la PR — pas l'ensemble du projet. Pour la vue projet complète après merge, voir §3.
> ² Aucune donnée de couverture remontée à SonarCloud pour ce projet (cf. §4 « Limites »).

---

## 2. Tableau comparatif — Vue New Code (changements introduits)

C'est sur le **New Code** que SonarCloud calcule la Quality Gate (Sonar way par défaut : 0 new bug, 0 new vuln, ≤ 3 % new duplications, ratings A, ≥ 80 % new coverage).

| Métrique                 | dev (réf. 30 j) | PR #3 Notation | PR #4 Liste apprenants |
| ------------------------ | ---------------:| --------------:| ----------------------:|
| **New lines**            | 1 688           | 380            | 213                    |
| **New bugs**             | 0 ✓             | 0 ✓            | 0 ✓                    |
| **New vulnerabilities**  | 1 ✗             | 0 ✓            | 0 ✓                    |
| **New code smells**      | (≥ 1)           | 1              | 4                      |
| **New security hotspots** | 1 ✗            | 0 ✓            | 0 ✓                    |
| **New duplications**     | 7.23 % ✗        | 1.05 % ✓       | 1.88 % ✓               |
| **Quality Gate**         | ❌ ERROR        | ✅ **OK**      | ✅ **OK**              |

**Lecture** :
- `dev` échoue le QG à cause d'1 vulnerability + 1 hotspot + 7.23 % duplications dans le code des 30 derniers jours.
- Les **deux PRs passent le QG** : 0 bug, 0 vuln, 0 hotspot, duplications maîtrisées (< 3 %).
- Les 5 nouveaux code smells introduits (1 + 4) sont mineurs et ne bloquent pas le QG.

---

## 3. État cumulé projeté après merge des 2 PRs

Si on merge les 2 PRs sur `dev`, le projet absorbe **+593 nouvelles lignes** maîtrisées :

| Aspect                | Effet du merge                                              |
| --------------------- | ----------------------------------------------------------- |
| Bugs                  | 0 → 0 (stable)                                              |
| Vulnerabilities       | 3 → 3 (les 3 préexistantes restent, aucune ajoutée)         |
| Code Smells           | 172 → ~177 (+5 nouveaux, mineurs)                           |
| Duplications          | 19.7 % → ~19.8 % (variation négligeable)                    |
| Security Hotspots     | 7 → 7 (aucun nouveau)                                       |
| Quality Gate (overall) | ERROR → ERROR ³                                            |
| Quality Gate (PR)      | OK ✓ pour chacune                                           |

> ³ Le QG « overall » dépend de la fenêtre New Code sur `dev` après le merge. Si la fenêtre est *previous_version* (par défaut dans Sonar way), le merge peut faire passer ou non le QG global selon le seuil glissant. Les 2 features individuellement n'apportent aucun nouveau défaut bloquant.

---

## 4. Limites de l'analyse

1. **Coverage non remontée** sur le projet `Andrimirana_Skillhub-BC03`. Pourtant la pipeline CI génère bien les fichiers `services/auth/target/site/jacoco/jacoco.xml`, `services/catalog/coverage.xml` et `services/inscription/coverage.xml`, et `sonar-project.properties` pointe dessus. À investiguer (chemin relatif vs absolu, pas chargé dans le scanner GitHub Action ?).
2. **Branches feature/** non analysées comme branches longues** par SonarCloud : seul `dev` apparaît dans `/api/project_branches/list`. Le push direct sur `feature/notation-formations` et `feature/liste-apprenants` n'a pas créé d'analyse de branche (probablement plan SonarCloud ou config branch detection désactivée). Solution adoptée : ouvrir des PRs (#3 et #4), Sonar les analyse alors comme PR avec metrics New Code.
3. **3 vulnérabilités et 7 hotspots préexistants** sur `dev` ne sont pas liés aux 2 fonctionnalités. Ils étaient déjà là avant ma session (analyse du 2026-05-07 12:40 sur le commit `7ce970` montrait déjà 4 vulns / 9 hotspots, réduits depuis). Voir le ticket d'audit séparé.

---

## 5. Conclusion

**Les 2 fonctionnalités passent la Quality Gate Sonar Way** sur leur New Code respectif :

| Fonctionnalité            | PR  | Lignes ajoutées | Bugs | Vulns | Smells | Duplications | QG     |
| ------------------------- | --- | ---------------:| ----:| -----:| ------:| ------------:| ------ |
| Notation des formations   | #3  | 380             | 0    | 0     | 1      | 1.05 %       | ✅ OK |
| Liste des apprenants      | #4  | 213             | 0    | 0     | 4      | 1.88 %       | ✅ OK |

Le code introduit est **propre et conforme** : 0 vulnérabilité, 0 bug, 0 hotspot, duplications maîtrisées sous le seuil de 3 %.

L'état de `dev` reste perfectible (3 vulns, 7 hotspots, 19.7 % duplications héritées) mais ces problèmes sont **antérieurs** à l'ajout des 2 fonctionnalités et ne sont pas dans le périmètre de ce rapport.

---

## 6. Liens rapides

- Vue projet : https://sonarcloud.io/project/overview?id=Andrimirana_Skillhub-BC03
- PR #3 sur Sonar : https://sonarcloud.io/project/pull_requests_list?id=Andrimirana_Skillhub-BC03 (sélectionner PR #3)
- PR #3 sur GitHub : https://github.com/Andrimirana/Skillhub-BC03/pull/3
- PR #4 sur Sonar : https://sonarcloud.io/project/pull_requests_list?id=Andrimirana_Skillhub-BC03 (sélectionner PR #4)
- PR #4 sur GitHub : https://github.com/Andrimirana/Skillhub-BC03/pull/4

---

*Rapport généré le 2026-05-08 à partir des données API SonarCloud.*
