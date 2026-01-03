/*
 * Copyright 2015 - 2022 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.api;

import org.traccar.api.security.PermissionsService;
import org.traccar.api.security.UserPrincipal;
import org.traccar.model.User;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;

import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.text.SimpleDateFormat;
import java.util.Date;

public class BaseResource {

    @Context
    private SecurityContext securityContext;

    @Inject
    protected Storage storage;

    @Inject
    protected PermissionsService permissionsService;

    protected long getUserId() {
        UserPrincipal principal = (UserPrincipal) securityContext.getUserPrincipal();
        if (principal != null) {
            return principal.getUserId();
        }
        return 0;
    }

    // --- NOUVELLE MÉTHODE DE SÉCURITÉ ---
    protected void checkSubscription() throws StorageException {
        long userId = getUserId();
        if (userId == 0) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        }

        User user = permissionsService.getUser(userId);

        // 1. Les administrateurs sont exemptés
        if (user.getAdministrator()) {
            return;
        }

        // 2. Vérification du statut d'abonné (Format String "true")
        Object isSubscribed = user.getAttributes().get("isSubscriber");
        if (isSubscribed == null || !isSubscribed.toString().equals("true")) {
            // On renvoie une erreur 403 (Forbidden) avec un message clair
            throw new WebApplicationException("Subscription required", Response.Status.FORBIDDEN);
        }

        // 3. Vérification de la date d'expiration (Format String "yyyy-MM-dd")
        Object endDateObj = user.getAttributes().get("subscriptionEndDate");
        if (endDateObj != null) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                Date endDate = sdf.parse(endDateObj.toString());

                if (new Date().after(endDate)) {
                    throw new WebApplicationException("Subscription expired", Response.Status.FORBIDDEN);
                }
            } catch (Exception e) {
                throw new WebApplicationException("Invalid subscription date", Response.Status.FORBIDDEN);
            }
        } else {
            throw new WebApplicationException("No subscription date set", Response.Status.FORBIDDEN);
        }
    }
}
