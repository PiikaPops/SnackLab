# Snack Lab

Mod client Fabric pour **Cobblemon 1.8.4 (Minecraft 1.21.1)** : crée en un clic n'importe quel Poké Snack ou
Poké Appât assaisonné, sans passer par la marmite.

## Utilisation

- **L** (modifiable dans les touches, catégorie « Snack Lab ») ou la commande `/snacklab` ouvre le labo.
- **À gauche** : tous les assaisonnements que la marmite accepte pour ces recettes. Tape dans la recherche (nom ou
  effet, accents facultatifs : « electrik », « shiny », « alpha »…) ou change le filtre (types, groupes d'œufs,
  shiny et rareté, EV/IV/natures, autres). Survole un ingrédient pour voir ses effets.
- **Clic** : ajoute l'ingrédient au mélange. **Maj + clic** : remplit les emplacements restants avec lui.
  **Clic sur un emplacement du mélange** : le retire. 3 emplacements maximum, comme la marmite.
- **À droite** : Poké Snack ou Poké Appât, quantité (1, 16 ou 64), et les effets du mélange tels que Cobblemon les
  décrit. Survole le résultat pour voir son infobulle complète.
- **Créer** (ou **Entrée**) : l'objet arrive dans l'inventaire.
- **Récents** : les derniers mélanges créés, à refaire d'un clic (gardés dans `config/snacklab.json`).

## Où ça marche

Un mod client ne fabrique rien tout seul : c'est le serveur qui donne l'objet.

- **En mode créatif**, sur n'importe quel serveur : l'objet passe par le même chemin que l'inventaire créatif.
- **En survie, avec la permission `/give`** (opérateur) : le mod envoie une commande `/give` avec les composants exacts
  de l'objet.
- Sinon, le serveur refuse et le labo l'indique.

## Fidèle à la marmite

L'objet est construit par le code de Cobblemon lui-même : mêmes ingrédients acceptés (tag
`cobblemon:recipe_filters/bait_seasoning`), 3 assaisonnements au maximum, et les mêmes traitements que la recette de la
marmite, dans le même ordre (couleur, ingrédients, effets d'appât pour le snack ; effets d'appât et couleur pour l'appât).

