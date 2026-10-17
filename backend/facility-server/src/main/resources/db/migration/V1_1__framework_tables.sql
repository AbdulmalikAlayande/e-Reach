-- V1_1: tables that two framework libraries need. Flyway runs this as ereach_owner.
-- Both blocks are copied unchanged from the libraries' own schema files. When either
-- library is upgraded, compare its file again and add a new migration for any change.

-- Source: spring-modulith-events-jdbc-2.1.1.jar
--         org/springframework/modulith/events/jdbc/schemas/v2/schema-postgresql.sql
-- Purpose: remembers events modules send each other, so none are lost if the server
--          restarts before a listener finishes (ADR-001).
CREATE TABLE IF NOT EXISTS event_publication
(
    id                     UUID NOT NULL,
    listener_id            TEXT NOT NULL,
    event_type             TEXT NOT NULL,
    serialized_event       TEXT NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
    );
CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx ON event_publication USING hash(serialized_event);
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx ON event_publication (completion_date);

-- Source: spring-session-jdbc-4.1.1.jar
--         org/springframework/session/jdbc/schema-postgresql.sql
-- Purpose: server-side sign-in sessions, so a session can be ended at once (ADR-007).
CREATE TABLE SPRING_SESSION (
                                PRIMARY_ID CHAR(36) NOT NULL,
                                SESSION_ID CHAR(36) NOT NULL,
                                CREATION_TIME BIGINT NOT NULL,
                                LAST_ACCESS_TIME BIGINT NOT NULL,
                                MAX_INACTIVE_INTERVAL INT NOT NULL,
                                EXPIRY_TIME BIGINT NOT NULL,
                                PRINCIPAL_NAME VARCHAR(100),
                                CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
                                           SESSION_PRIMARY_ID CHAR(36) NOT NULL,
                                           ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
                                           ATTRIBUTE_BYTES BYTEA NOT NULL,
                                           CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
                                           CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
);

-- V1 already lets the app read, insert and update these tables. Sessions must also be
-- deleted when they expire or the user signs out, and finished events cleared, so the
-- app gets DELETE on these three transport tables only.
GRANT DELETE ON spring_session, spring_session_attributes, event_publication TO ereach_app;
