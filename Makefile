# Définit une variable pour le nom de votre JAR et le fichier de configuration
JAR_FILE=target/tracker-server.jar
CONFIG_FILE=setup/traccar.xml

# La commande par défaut quand vous tapez 'make start'
start:
	@echo "Starting Traccar server...":
	java --jar $(JAR_FILE) $(CONFIG_FILE)

# Une commande pour arrêter (si vous utilisez un script d'arrêt séparé ou un PID)
# Note: make ne gère pas l'arrêt propre d'un processus en cours d'exécution facilement
# via la même invocation de make. Cette cible est juste un exemple.
stop:
	@echo "Pour arrêter le serveur, appuyez simplement sur Ctrl+C dans la console de démarrage."
	@echo "Ou implémentez une méthode d'arrêt appropriée selon votre configuration."

#Une commande pour nettoyer les fichiers temporaires si nécessaire
clean:
	@echo "Nettoyage des fichiers temporaires..."
	# Ajoutez ici vos commandes de nettoyage (ex: rm -rf temp/*.log)

.PHONY: start stop clean