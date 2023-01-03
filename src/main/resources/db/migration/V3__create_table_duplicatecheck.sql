create table duplicatecheck
(
    legeerklaring_id VARCHAR PRIMARY KEY,
    sha256_legeerklaering VARCHAR,
    mottak_id VARCHAR NOT NULL,
    msg_id VARCHAR NOT NULL,
    mottatt_date TIMESTAMP NOT NULL,
    org_number VARCHAR NULL
);