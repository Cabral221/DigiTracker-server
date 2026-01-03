package org.traccar.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.model.User;
import org.traccar.model.Group;
import org.traccar.model.Permission;
import org.traccar.storage.Storage;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;
import org.traccar.mail.MailManager;

import jakarta.inject.Inject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Collection;
import java.util.concurrent.Executors; 
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import jakarta.mail.MessagingException;
// 1. Changez l'import de l'exécuteur

public class SubscriptionTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionTask.class);
    private static final long CHECK_INTERVAL = 1; // Toutes les heures

    private final Storage storage;
    private final MailManager mailManager;
    private final ScheduledExecutorService executorService;

    @Inject
    public SubscriptionTask(
            Storage storage, MailManager mailManager) {
        this.storage = storage;
        this.mailManager = mailManager;
        this.executorService = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        executorService.scheduleAtFixedRate(this::checkExpirations, 0, CHECK_INTERVAL, TimeUnit.HOURS);
        LOGGER.info("⏳ Tâche de vérification des abonnements SenBus démarrée.");
    }

    private void checkExpirations() {
        try {
            // 1. Récupérer TOUS les utilisateurs (Traccar gère bien cela en cache)
            Collection<User> allUsers = storage.getObjects(User.class, new Request(new Columns.All()));

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date today = new Date();

            for (User user : allUsers) {
                // 2. Vérifier manuellement l'attribut dans le JSON
                Object isSub = user.getAttributes().get("isSubscriber");
                
                // On vérifie si c'est un abonné (gestion du String ou Boolean selon l'origine)
                if (isSub != null && isSub.toString().equalsIgnoreCase("true")) {
                    
                    String endDateStr = (String) user.getAttributes().get("subscriptionEndDate");
                    if (endDateStr != null) {
                        Date endDate = sdf.parse(endDateStr);

                        if (endDate.before(today)) {
                            handleExpiration(user);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Erreur lors de la vérification des abonnements : ", e);
        }
    }

    private void handleExpiration(User user) throws Exception {
        LOGGER.info("⏳ Abonnement expiré pour : " + user.getEmail() + ". Réinitialisation...");

        // 3. Mise à jour des limites (Retour au mode public)
        user.set("isSubscriber", "false");
        user.setDeviceLimit(0); // Plus de camions privés
        user.setReadonly(true); // Ne peut plus modifier ses données

        storage.updateObject(user, new Request(
                new Columns.All(), new Condition.Equals("id", user.getId())));

        // 4. Ré-inscription au groupe public "Flotte SenBus"
        Group fleetGroup = storage.getObjects(Group.class, new Request(
                new Columns.All(), new Condition.Equals("name", "Flotte SenBus")))
                .stream().findFirst().orElse(null);

        if (fleetGroup != null) {
            storage.addPermission(new Permission(User.class, user.getId(), Group.class, fleetGroup.getId()));
        }

        // 5. Notification Email
        sendExpirationEmail(user);
    }

    private void sendExpirationEmail(User user) {
        if (mailManager != null) {
            try {
                String subject = "Votre abonnement SenBus a expiré ðŸ›‘";
                String body = "Bonjour " + user.getName() + ",\n\n"
                        + "Votre abonnement est arrivé à son terme. "
                        + "Vos accès privés ont été restreints.\n"
                        + "Vous pouvez toujours consulter la flotte publique ou renouveler votre pack "
                        + "sur votre tableau de bord.\n\n"
                        + "L'équipe SenBus.";
                // On entoure l'appel qui pose problème
                mailManager.sendMessage(user, false, subject, body);

                LOGGER.info("📧 Email d'expiration envoyé à : " + user.getEmail());
            } catch (MessagingException e) {
                // On logue l'erreur sans bloquer la suite de la tâche
                LOGGER.error("💥 Impossible d'envoyer l'email d'expiration à " + user.getEmail(), e);
            }
        }
    }
}
