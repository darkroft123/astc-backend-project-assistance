package com.smms.assistance.infrastructure.config;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jose4j.jwa.AlgorithmFactoryFactory;
import org.jboss.logging.Logger;
import io.vertx.core.Vertx;

@ApplicationScoped
public class CryptoEagerInit {
    private static final Logger LOG = Logger.getLogger(CryptoEagerInit.class);
    
    void onStart(@Observes StartupEvent ev, Vertx vertx) {
        vertx.executeBlocking(promise -> {
            try {
                LOG.info("Inicializando proveedores criptogr\u00e1ficos (jose4j/SunPKCS11) para evitar bloqueos en el Event Loop...");
                AlgorithmFactoryFactory.getInstance();
                java.security.Security.getProviders();
                
                // Warm up RSA signature verification
                java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance("RSA");
                kpg.initialize(2048);
                java.security.KeyPair kp = kpg.generateKeyPair();
                
                org.jose4j.jwt.JwtClaims claims = new org.jose4j.jwt.JwtClaims();
                claims.setSubject("warmup");
                org.jose4j.jws.JsonWebSignature jws = new org.jose4j.jws.JsonWebSignature();
                jws.setPayload(claims.toJson());
                jws.setKey(kp.getPrivate());
                jws.setAlgorithmHeaderValue(org.jose4j.jws.AlgorithmIdentifiers.RSA_USING_SHA256);
                String jwt = jws.getCompactSerialization();
                
                org.jose4j.jwt.consumer.JwtConsumer consumer = new org.jose4j.jwt.consumer.JwtConsumerBuilder()
                        .setVerificationKey(kp.getPublic())
                        .build();
                consumer.processToClaims(jwt);
                
                // Warm up GraphQL parser
                try {
                    graphql.parser.Parser parser = new graphql.parser.Parser();
                    parser.parseDocument("query { __typename }");
                } catch (Throwable t) {
                    // ignore if graphql is not in classpath
                }
                
                LOG.info("Inicializaci\u00f3n criptogr\u00e1fica completada.");
                promise.complete();
            } catch(Exception e) {
                LOG.warn("Error durante el warmup criptogr\u00e1fico", e);
                promise.complete();
            }
        }, false);
    }
}
