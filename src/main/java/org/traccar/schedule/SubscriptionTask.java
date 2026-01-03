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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import jakarta.mail.MessagingException;

public class SubscriptionTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionTask.class);
    private static final long CHECK_INTERVAL = 1; // Toutes les heures

    private final Storage storage;
    private final MailManager mailManager;
    private final ScheduledExecutorService executorService;

    @Inject
    public SubscriptionTask(
            Storage storage, MailManager mailManager, ScheduledExecutorService executorService) {
        this.storage = storage;
        this.mailManager = mailManager;
        this.executorService = executorService;
    }

    public void start() {
        executorService.scheduleAtFixedRate(this::checkExpirations, 0, CHECK_INTERVAL, TimeUnit.HOURS);
        LOGGER.info("ðŸš€ TÃ¢che de vÃ©rification des abonnements SenBus dÃ©marrÃ©e.");
    }

    private void checkExpirations() {
        try {
            // 1. RÃ©cupÃ©rer tous les abonnÃ©s actifs
            Collection<User> subscribers = storage.getObjects(User.class, new Request(
                    new Columns.All(),
                    new Condition.Equals("isSubscriber", "true")));

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date today = new Date();

            for (User user : subscribers) {
                String endDateStr = (String) user.getAttributes().get("subscriptionEndDate");
                if (endDateStr != null) {
                    Date endDate = sdf.parse(endDateStr);

                    // 2. Si l'abonnement est expirÃ©
                    if (endDate.before(today)) {
                        handleExpiration(user);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Erreur lors de la vÃ©rification des abonnements : ", e);
        }
    }

    private void handleExpiration(User user) throws Exception {
        LOGGER.info("â³ Abonnement expirÃ© pour : " + user.getEmail() + ". RÃ©initialisation...");

        // 3. Mise Ã  jour des limites (Retour au mode public)
        user.set("isSubscriber", "false");
        user.setDeviceLimit(0); // Plus de camions privÃ©s
        user.setReadonly(true); // Ne peut plus modifier ses donnÃ©es

        storage.updateObject(user, new Request(
                new Columns.All(), new Condition.Equals("id", user.getId())));

        // 4. RÃ©-inscription au groupe public "Flotte SenBus"
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
                String subject = "Votre abonnement SenBus a expirÃ© ðŸ›‘";
                String body = "Bonjour " + user.getName() + ",\n\n"
                        + "Votre abonnement est arrivÃ© Ã  son terme. "
                        + "Vos accÃ¨s privÃ©s ont Ã©tÃ© restreints.\n"
                        + "Vous pouvez toujours consulter la flotte publique ou renouveler votre pack "
                        + "sur votre tableau de bord.\n\n"
                        + "L'Ã©quipe SenBus.";

                // On entoure l'appel qui pose problÃ¨me
                mailManager.sendMessage(user, false, subject, body);

                LOGGER.info("ðŸ“§ Email d'expiration envoyÃ© Ã  : " + user.getEmail());
            } catch (MessagingException e) {
                // On logue l'erreur sans bloquer la suite de la tÃ¢che
                LOGGER.error("ðŸ’¥ Impossible d'envoyer l'email d'expiration Ã  " + user.getEmail(), e);
            }
        }
    }
}
