package com.smms.assistance.infrastructure.client;

import io.smallrye.graphql.client.typesafe.api.GraphQLClientApi;
import io.smallrye.graphql.client.typesafe.api.ErrorOr;
import org.eclipse.microprofile.graphql.Query;
import org.eclipse.microprofile.graphql.Name;
import java.util.List;
import com.smms.assistance.infrastructure.controller.graphql.dto.UserOutput;

@GraphQLClientApi(configKey = "backoffice-service")
public interface BackofficeClient {

    @Query("listUsers")
    ErrorOr<List<UserOutput>> listUsers();

    @Query("getUser")
    ErrorOr<UserOutput> getUser(@Name("id") String id);
}


