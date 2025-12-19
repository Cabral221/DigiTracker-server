package org.traccar.api.resource;

import com.stripe.model.Event;
import com.stripe.net.ApiResource;
import com.stripe.net.Webhook;
import org.traccar.api.BaseResource;
import org.traccar.config.Config;
import org.traccar.model.User;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.core.Response;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

@Path("payments")
public class StripeResource extends BaseResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(StripeResource.class);
    @Inject
    private Config config;
    
    @PermitAll
    @POST
    @Path("webhook")
    public Response handleWebhook(String payload, @HeaderParam("Stripe-Signature") String sigHeader) {
        
        String endpointSecret = config.getString("stripe.webhookSecret");

        if (endpointSecret == null || endpointSecret.isEmpty()) {
            LOGGER.error("Le secret Stripe Webhook n'est pas configuré dans traccar.xml !");
            return Response.serverError().build();
        }
        
        Event event;
        try {
            if (sigHeader != null) {
                // MODE RÉEL : Validation de la signature (Stripe CLI ou Production)
                event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
            } else {
                // MODE TEST : Pour votre commande PowerShell
                LOGGER.warn("⚠️ Webhook reçu sans signature (Test manuel).");
                event = ApiResource.GSON.fromJson(payload, Event.class);
            }

            LOGGER.info("Webhook reçu type : " + event.getType());

            if ("checkout.session.completed".equals(event.getType())) {
                String userEmail = null;

                try {
                    // 1. On parse le payload brut reçu au début de la fonction
                    com.google.gson.JsonObject jsonPayload = ApiResource.GSON.fromJson(payload, com.google.gson.JsonObject.class);
                    
                    // 2. On descend dans l'arborescence : data -> object
                    com.google.gson.JsonObject dataObj = jsonPayload.getAsJsonObject("data").getAsJsonObject("object");

                    // 3. Extraction sécurisée de l'email
                    if (dataObj.has("customer_email") && !dataObj.get("customer_email").isJsonNull()) {
                        userEmail = dataObj.get("customer_email").getAsString();
                    } else if (dataObj.has("customer_details") && !dataObj.get("customer_details").isJsonNull()) {
                        com.google.gson.JsonObject details = dataObj.getAsJsonObject("customer_details");
                        if (details.has("email") && !details.get("email").isJsonNull()) {
                            userEmail = details.get("email").getAsString();
                        }
                    }

                    if (userEmail != null) {
                        LOGGER.info("✅ Email identifié depuis le payload : " + userEmail);
                        updateUserSubscription(userEmail);
                    } else {
                        LOGGER.warn("❌ Aucun email trouvé dans le JSON brut.");
                    }

                } catch (Exception e) {
                    LOGGER.error("💥 Erreur d'analyse JSON : " + e.getMessage());
                }
            }

            return Response.ok().build();

        } catch (Exception e) {
            LOGGER.error("Erreur lors du traitement du Webhook : " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }

    private void updateUserSubscription(String email) throws StorageException {
        // 1. Recherche de l'utilisateur avec toutes les colonnes explicitement définies
        User user = storage.getObjects(User.class, new Request(
                new Columns.All(),
                new Condition.Equals("email", email)))
                .stream().findFirst().orElse(null);

        if (user != null) {
            // 1. Préparation des dates
            Calendar calendar = Calendar.getInstance();
            Date startDate = calendar.getTime();
            calendar.add(Calendar.YEAR, 1);
            Date endDate = calendar.getTime();

            // 2. Formatage des dates en String (Format Traccar standard)
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            String startDateStr = sdf.format(startDate);
            String endDateStr = sdf.format(endDate);

            // 3. Mise à jour des attributs (Harmonisation avec User 1)
            user.set("isSubscriber", "true"); // On garde le booléen, c'est mieux
            user.set("subscriptionStartDate", startDateStr); // Devient "2025-12-18"
            user.set("subscriptionEndDate", endDateStr);     // Devient "2026-01-18"

            // 4. Envoi de la mise à jour à la base de données
            // On spécifie Columns.All() pour qu'il sache qu'il doit sauvegarder les attributs modifiés
            storage.updateObject(user, new Request(
                    new Columns.All(), 
                    new Condition.Equals("id", user.getId())));

            LOGGER.info("✅ Abonnement activé avec succès pour : " + email);
        } else {
            LOGGER.warn("❌ Utilisateur introuvable pour l'email : " + email);
        }
    }
}