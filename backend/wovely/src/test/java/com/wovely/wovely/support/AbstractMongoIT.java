package com.wovely.wovely.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * Base class for integration tests (*IT.java, run by failsafe on {@code mvn verify}).
 *
 * <p>Boots the full Spring context against a throw-away MongoDB started by Testcontainers.
 * The container is a JVM-wide singleton, so it is started once and shared by every IT class;
 * tests must therefore create their own data and not rely on a clean database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractMongoIT {

    @ServiceConnection
    protected static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    static {
        MONGO.start();
    }
}
