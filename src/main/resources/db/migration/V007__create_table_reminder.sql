CREATE TABLE reminder
(
    "id"        SERIAL,
    "timestamp" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    "nick"      VARCHAR(255)             NOT NULL,
    "channel"   VARCHAR(255)             NOT NULL,
    "ondate"    DATE                     NOT NULL,
    "whenspec"  TEXT                     NOT NULL,
    "message"   TEXT                     NOT NULL,

    PRIMARY KEY (id)
);

ALTER TABLE reminder OWNER TO ircbot;
