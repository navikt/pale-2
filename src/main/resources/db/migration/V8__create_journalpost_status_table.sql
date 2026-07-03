create table journalpost_status
(
    reference_id        Text primary key,
    legeerklaring_id    Text      NOT NULL,
    journalpost_id      Text,
    msg_id              Text      NOT NULL,
    processing_status   Text      NOT NULL,
    journalpost_status  Text,
    arena_payload       jsonb,
    mottatt_dato        TIMESTAMP NOT NULL,
    oppdatert_dato      TIMESTAMP
);
