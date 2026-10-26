package com.smms.assistance.infrastructure.controller.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class GraphQLErrorHandler implements DataFetcherExceptionHandler {

    private static final Logger LOG = Logger.getLogger(GraphQLErrorHandler.class);

    @Override
    public CompletableFuture<DataFetcherExceptionHandlerResult> handleException(DataFetcherExceptionHandlerParameters handlerParameters) {
        Throwable exception = handlerParameters.getException();
        LOG.errorf(exception, "[GRAPHQL] Error en operación");

        String message;
        Throwable cause = exception;

        if (exception instanceof SecurityException) {
            message = exception.getMessage();
        } else if (exception instanceof IllegalArgumentException) {
            message = exception.getMessage();
        } else if (exception instanceof IllegalStateException) {
            message = exception.getMessage();
        } else {
            if (exception.getCause() != null) {
                cause = exception.getCause();
            }
            message = cause.getMessage() != null ? cause.getMessage() : "Error interno del servidor";
        }

        GraphQLError error = GraphqlErrorBuilder.newError()
                .message(message)
                .build();

        return CompletableFuture.completedFuture(
                DataFetcherExceptionHandlerResult.newResult().error(error).build()
        );
    }
}
