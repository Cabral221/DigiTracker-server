package org.traccar.api.resource;

import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import org.traccar.api.BaseResource;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.model.User;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.core.Response;
import java.util.Calendar;
import java.util.Date;

@Path("payments")
public class StripeResource extends BaseResource {

    @Inject
    private Config config;

    @POST
    @Path("webhook")
    public Response handleWebhook(String payload, @HeaderParam("Stripe-Signature") String sigHeader) {
        String endpointSecret = config.getString("stripe.webhookSecret");
        Event event;

        try {
            // Vérification de la signature pour bloquer les faux appels
            event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        // On traite uniquement quand le paiement est réussi
        if ("checkout.session.completed".equals(event.getType())) {
            Session session = (Session) event.getDataObjectDeserializer().getObject().get();
            String userEmail = session.getCustomerDetails().getEmail();

            try {
                updateUserSubscription(userEmail);
            } catch (StorageException e) {
                return Response.serverError().build();
            }
        }

        return Response.ok().build();
    }

    private void updateUserSubscription(String email) throws StorageException {
        User user = storage.getObject(User.class, new Request(
                new Columns.All(), new Condition.Equals("email", email)));

        if (user != null) {
            Calendar cal = Calendar.getInstance();
            
            // Mise à jour des attributs
            user.getAttributes().put("isSubscriber", "true");
            user.getAttributes().put("subscriptionStartDate", new Date().toString());
            
            cal.add(Calendar.MONTH, 1); // +31 jours
            user.getAttributes().put("subscriptionEndDate", cal.getTime().toString());

            storage.updateObject(user, new Request(
                    new Columns.All(), new Condition.Equals("id", user.getId())));
        }
    }
}