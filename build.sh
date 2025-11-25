#!/bin/bash

# ================================
# CONFIGURATION
# ================================
TOMCAT_HOME="/home/antonio/Documents/apache-tomcat-10.1.48"
PROJECT_DIR="/home/antonio/Bureau/S5/mr-naina/framework"
WEBAPP_NAME="test"   # Nom du projet dans Tomcat (webapps/test)

# ================================
# Dossiers
# ================================
SRC_DIR="$PROJECT_DIR/src/main/java"
BUILD_DIR="$PROJECT_DIR/build"
DIST_DIR="$PROJECT_DIR/dist"
WEB_INF="$TOMCAT_HOME/webapps/$WEBAPP_NAME/WEB-INF"
LIB_DIR="$WEB_INF/lib"
# Crée le répertoire lib s'il n'existe pas
mkdir -p "$LIB_DIR"
TOMCAT_WORK_DIR="$TOMCAT_HOME/work/Catalina/localhost/$WEBAPP_NAME"

# ================================
# Étape 1 : Nettoyage
# ================================
echo ">>> Nettoyage des anciens fichiers..."
rm -rf "$BUILD_DIR" "$DIST_DIR"
mkdir -p "$BUILD_DIR" "$DIST_DIR"

# ================================
# Étape 2 : Compilation
# ================================
echo ">>> Compilation du projet..."
javac -d "$BUILD_DIR" -classpath "$TOMCAT_HOME/lib/*" $(find "$SRC_DIR" -name "*.java")

if [ $? -ne 0 ]; then
    echo "❌ Erreur lors de la compilation. Script arrêté."
    exit 1
fi

# ================================
# Étape 3 : Génération du JAR
# ================================
echo ">>> Génération du JAR..."
jar cvf "$DIST_DIR/$WEBAPP_NAME.jar" -C "$BUILD_DIR" .

if [ $? -ne 0 ]; then
    echo "❌ Erreur lors de la création du JAR. Script arrêté."
    exit 1
fi

# ================================
# Étape 4 : Déploiement dans Tomcat
# ================================
echo ">>> Suppression de l'ancien JAR..."
rm -f "$LIB_DIR/$WEBAPP_NAME.jar"

echo ">>> Copie du nouveau JAR dans Tomcat..."
cp "$DIST_DIR/$WEBAPP_NAME.jar" "$LIB_DIR/"

echo ">>> Suppression du cache Tomcat pour forcer le rechargement..."
rm -rf "$TOMCAT_WORK_DIR"

# ================================
# Étape 5 : Redémarrage de Tomcat
# ================================
echo ">>> Redémarrage de Tomcat..."
"$TOMCAT_HOME/bin/shutdown.sh"
sleep 2
"$TOMCAT_HOME/bin/startup.sh"

echo "✅ Déploiement terminé avec succès !"
echo "Le fichier JAR est maintenant dans : $LIB_DIR"
