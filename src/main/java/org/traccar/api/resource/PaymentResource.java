package org.traccar.api.resource;

import com.stripe.model.Event;
import com.stripe.net.ApiResource;
import com.stripe.net.Webhook;
import org.traccar.api.BaseResource;
import org.traccar.config.Config;
import org.traccar.model.User;
import org.traccar.model.Group;
import org.traccar.model.Permission;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.mail.MailManager;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.core.Response;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import jakarta.ws.rs.core.MediaType;

@Path("payments")
public class PaymentResource extends BaseResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentResource.class);

    @Inject
    private Config config;

    @Inject
    private MailManager mailManager; // Ajoutez cette injection

    // --- POINT D'ENTRÉE STRIPE  ---
    @PermitAll
    @POST
    @Path("stripe/webhook")
    public Response handleStripeWebhook(String payload, @HeaderParam("Stripe-Signature") String sigHeader) {
        String endpointSecret = config.getString("stripe.webhookSecret");
        Event event;

        try {
            if (sigHeader != null && endpointSecret != null) {
                event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
            } else {
                LOGGER.warn("⚠️ Webhook reçu sans signature (Test manuel).");
                event = ApiResource.GSON.fromJson(payload, Event.class);
            }

            if ("checkout.session.completed".equals(event.getType())) {
                com.google.gson.JsonObject jsonPayload = ApiResource.GSON.fromJson(payload, com.google.gson.JsonObject.class);
                com.google.gson.JsonObject dataObj = jsonPayload.getAsJsonObject("data").getAsJsonObject("object");

                String userEmail = null;
                if (dataObj.has("customer_email") && !dataObj.get("customer_email").isJsonNull()) {
                    userEmail = dataObj.get("customer_email").getAsString();
                } else if (dataObj.has("customer_details") && !dataObj.get("customer_details").isJsonNull()) {
                    com.google.gson.JsonObject details = dataObj.getAsJsonObject("customer_details");
                    if (details.has("email") && !details.get("email").isJsonNull()) {
                        userEmail = details.get("email").getAsString();
                    }
                }

                if (userEmail != null) {
                    // APPEL DE LA MÉTHODE UNIVERSELLE
                    processSubscription(userEmail, "Stripe");
                }
            }
            return Response.ok().build();
        } catch (Exception e) {
            LOGGER.error("Erreur Webhook : " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }

    // --- POINT D'ENTRÉE WAVE ---
    @PermitAll
    @POST
    @Path("wave/webhook")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response handleWaveWebhook(String payload, @HeaderParam("Wave-Signature") String sigHeader) {
        try {
            // 1. Parser le JSON de Wave
            com.google.gson.JsonObject jsonPayload = ApiResource.GSON.fromJson(payload, com.google.gson.JsonObject.class);
            
            // 2. Vérifier le type d'événement (Wave utilise souvent "checkout.session.completed")
            String type = jsonPayload.get("type").getAsString();
            
            if ("checkout.session.completed".equals(type)) {
                com.google.gson.JsonObject data = jsonPayload.getAsJsonObject("data");
                
                // Wave permet de récupérer l'email ou un identifiant client
                String userEmail = data.get("client_reference_id").getAsString(); // On utilise souvent l'ID ou l'Email passé au checkout
                
                if (userEmail != null && userEmail.contains("@")) {
                    processSubscription(userEmail, "Wave");
                }
            }
            
            return Response.ok().build();
        } catch (Exception e) {
            LOGGER.error("Erreur Webhook Wave : " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }

    /**
     * MÉTHODE D'ACTIVATION UNIVERSELLE
     * Peut être appelée par Stripe, Wave, ou un admin.
     */
    private void processSubscription(String email, String provider) throws StorageException {
        User user = storage.getObjects(User.class, new Request(
                new Columns.All(),
                new Condition.Equals("email", email)))
                .stream().findFirst().orElse(null);

        if (user != null) {
            // 1. Calcul des dates
            Calendar calendar = Calendar.getInstance();

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            String startDateStr = sdf.format(calendar.getTime());
            calendar.add(Calendar.YEAR, 1);
            String endDateStr = sdf.format(calendar.getTime());

            // 2. Mise à jour de l'utilisateur
            user.set("isSubscriber", "true");
            user.set("subscriptionStartDate", startDateStr);
            user.set("subscriptionEndDate", endDateStr);
            user.set("paymentProvider", provider); // Pour vos stats
            // IMPORTANT : On nettoie les attributs JSON qui pourraient forcer le readonly
            // car Traccar vérifie souvent les attributs avant la propriété de base
            user.getAttributes().remove("readonly");
            // --- MISES À JOUR CRUCIALES POUR LES TRANSPORTURS ---
            user.setDeviceLimit(5); // ✅ Débloque les 5 camions
            user.setReadonly(false); // ✅ Permet à l'utilisateur de modifier ses données
            user.setLimitCommands(false); // Optionnel : permet d'envoyer des commandes (coupure moteur)

            storage.updateObject(user, new Request(
                    new Columns.All(),
                    new Condition.Equals("id", user.getId())));

            // 3. ENVOI DE L'EMAIL DE CONFIRMATION
            sendEmail(user, endDateStr);

            LOGGER.info("✅ Abonnement activé via " + provider + " pour : " + email);
        } else {
            LOGGER.warn("❌ Utilisateur introuvable pour activation : " + email);
        }
    }

    private void sendEmail(User user, String endDate) {
        if (mailManager != null) {
            try {
                String subject = "Bienvenue sur SenBus - Votre abonnement est actif ! 🚀";
                String body = "Bonjour " + user.getName() + ",\n\n"
                        + "Votre paiement a été validé avec succès.\n"
                        + "Votre accès à la flotte SenBus est désormais actif jusqu'au " + endDate + ".\n\n"
                        + "Bonne navigation sur notre plateforme !\n"
                        + "L'équipe SenBus.";
                
                // On appelle directement 'send' sur l'objet injecté
                // La signature est généralement : mailManager.send(userId, subject, body) 
                // ou mailManager.send(user, subject, body)
                mailManager.sendMessage(user, false, subject, body); 
                
                LOGGER.info("📧 Email de confirmation envoyé à : " + user.getEmail());
            } catch (Exception e) {
                LOGGER.error("💥 Erreur lors de l'envoi de l'email : " + e.getMessage());
            }
        } else {
            LOGGER.warn("⚠️ MailManager est null. Vérifiez votre configuration SMTP dans traccar.xml.");
        }
    }
}