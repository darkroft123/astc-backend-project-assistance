package com.smms.assistance.infrastructure.client;

import io.smallrye.graphql.client.typesafe.api.TypesafeGraphQLClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

@ApplicationScoped
public class AssistanceClientWrapper {

    @Inject
    JsonWebToken jwt;

    public AssistanceClient getClient() {
        var builder = TypesafeGraphQLClientBuilder.newBuilder()
                .configKey("assistance-service");

        if (jwt != null && jwt.getRawToken() != null) {
            builder = builder.header("Authorization", "Bearer " + jwt.getRawToken());
        }

        return builder.build(AssistanceClient.class);
    }
}
