# PNJ autonomes – étape 1 : amitiés, rancunes et discussions entre PNJ

## Ce que MCA fait déjà (à compléter, pas à refaire)

- **Mariages auto** : `VillageMarriageManager.marry()` (appelé par `Village.tick`), chance `marriageChancePerMinute` = 0,05/min. Prend le célibataire qui a le moins de « cœurs » **avec les joueurs**, puis le **premier** partenaire compatible non apparenté : c'est du hasard, aucune affinité entre PNJ.
- **Bébés** : `VillageProcreationManager.procreate()` choisit une femme mariée au hasard (l'addon modifie déjà la chance via `VillageProcreationManagerMixin`).
- **Mémoires / cœurs** : `VillagerBrain.getMemories()` = `Map<UUID, Memories>` mais uniquement **PNJ → joueur** (`getMemoriesForPlayer`). Aucune relation PNJ ↔ PNJ n'existe.
- **Ragots vanilla** : la tâche `GatherItemsVillagerTask` (« GOSSIP TASK » dans les paquets IDLE et MEET) appelle `VillagerEntity.talkWithVillager()` quand deux villageois sont proches ; ils s'échangent seulement la réputation **des joueurs**.
- **Se rejoindre / se regarder** : dans IDLE, `FindEntityTask` (villageois mâle/femelle, 8 blocs) met `INTERACTION_TARGET` et les fait marcher à 2 blocs ; `getFullLookBehavior` les fait se regarder. MEET = place du village (cloche).
- **Parler** : `Messenger.sendChatToAllAround()` envoie une phrase traduite dans le **chat** des joueurs à 20 blocs (grise au-delà de 10), avec préfixe `villagerChatPrefix`. Pas de bulle au-dessus de la tête. (Un chat IA existe, `enableVillagerChatAI`, désactivé : on l'ignore.)
- **Addon** : le bonus « entouré » (`Wellbeing.update`, 1 fois sur 5) compte les `VillagerEntityMCA` à 32 blocs (isolé) et à 16 blocs (≥ 2 = entouré). `VillageJustice.daily` connaît le voleur (UUID).

## Plan

1. **Stockage** : `social/SocialRegistry` (PersistentState, comme `WellbeingRegistry`) : par PNJ, ≤ 20 liens `{autre UUID, note −100..+100, dernier jour vu}`. Lien plein : on remplace le plus proche de 0. Oubli : chaque jour, note → 0 de 1 point si pas vus depuis 7 jours ; lien supprimé à 0. Nettoyage à la mort.
2. **Montée / baisse** (valeurs en haut de `social/Social.java`) :
   - discussion réussie +2 (dé selon personnalités MCA ; 10 % de dispute −4) ;
   - collègues (même métier ET même zone de travail, à la journée) +1 ;
   - famille (parent, enfant, frère/sœur, conjoint via `FamilyTree`) : départ à +40 la 1re fois ;
   - vol (`VillageJustice`) : −15 chez tous les habitants qui l'apprennent, −30 pour le garde qui arrête ;
   - se faire taper par un PNJ −10 (hook `damage` existant).
3. **Effet sur le bonheur** : chaque jour +1 si ≥ 1 ami (≥ 50) à moins de 32 blocs, −1 si un ennemi (≤ −50) habite la même maison ; mort d'un ami −10. Remplace le bonus « entouré » seulement si un ami est présent (pas de double compte).
4. **Mariages** : mixin `@Redirect`/`@ModifyExpressionValue` sur le `findFirst()` de `marry()` → prendre le meilleur ami compatible (note mutuelle ≥ 30), sinon comportement MCA ; jamais un ennemi (≤ −50).
5. **Discussions visibles** : mixin `HEAD` sur `VillagerEntity.talkWithVillager` (déclenché par la tâche ragots de MCA, donc ils sont déjà arrêtés face à face). Cooldown 2 min par PNJ. On fixe `LOOK_TARGET` l'un sur l'autre et `WALK_TARGET` vide pendant 3 s. Phrase choisie selon la note (amis / neutre / dispute), envoyée **seulement aux joueurs à 12 blocs** via `sendChatToAllAround`-like (texte gris « Erich → Anna : … »). Option bulle : petite ligne au-dessus de la tête (3 s) via l'`EntityRendererMixin` déjà présent + paquet S2C. Réglage `config/mca_vie_social.json` : `showChats` (chat / bulle / off), `chatRadius`.
6. **PNJ non chargés** : au passage du jour (même endroit que `VillageJustice.daily`), pour chaque village : 2 « discussions virtuelles » par habitant avec un autre habitant au hasard (mêmes règles, sans texte), + collègues, + oubli.
7. **Commande** `/surnametest social` : liste les liens du PNJ le plus proche ; `/surnametest social talk` force une discussion entre les 2 PNJ les plus proches ; `/surnametest social day` lance la simulation journalière.
8. **Fichiers** : `social/Social.java`, `SocialRegistry.java`, `SocialConfig.java`, `SocialNetwork.java` (bulle) ; mixins `mixin/social/VillagerTalkMixin` (vanilla `VillagerEntity`), `VillageMarriageManagerMixin` ; appels dans `Wellbeing`, `VillageJustice`, `DevTestCommand` ; textes dans `fr_fr.json`/`en_us.json`.

## Découpage

- **1a** : registre + notes (discussion, collègues, famille, vol, coup) + oubli + simulation journalière + `/surnametest social`. Rien de visible, testable à la commande.
- **1b** : effets (bonheur, choix du conjoint).
- **1c** : discussions visibles (regard maintenu chaque tick, phrases ami/famille/neutre/dispute dans le chat proche, 1 ligne par joueur toutes les 20 s, réglage `config/mca_vie_social.json`). Bulle au-dessus de la tête : pas faite.
