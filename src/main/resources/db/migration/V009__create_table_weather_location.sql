CREATE TABLE weather_location
(
    "channel"  VARCHAR(255) NOT NULL,
    "nick"     VARCHAR(255) NOT NULL,
    "location" TEXT         NOT NULL,

    PRIMARY KEY ("channel", "nick")
);

ALTER TABLE weather_location OWNER TO ircbot;
