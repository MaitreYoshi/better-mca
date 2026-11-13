Outils du portage 26.2 (non livrés dans le jar).

Compiler en listant toutes les erreurs :
    gradlew.bat compileJava -q -I tools/port26/err.gradle > c26.log 2>&1
Erreurs lisibles (fichier:ligne, symbole, code) :
    python -I tools/port26/errs.py c26.log [motif de chemin]
Corrections guidées par les erreurs (toujours sur un journal FRAIS, les numéros de ligne bougent) :
    python -I tools/port26/fix26.py c26.log
Les autres scripts ont déjà été appliqués (renommages globaux, écrans, sauvegardes, rendus).
