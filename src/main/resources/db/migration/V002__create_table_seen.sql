CREATE TABLE seen
(
    "channel"   VARCHAR(255)             NOT NULL,
    "nick"      VARCHAR(255)             NOT NULL,
    "timestamp" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    "message"   TEXT                     NOT NULL,
  
  PRIMARY KEY ("channel", "nick")
);

ALTER TABLE seen OWNER TO ircbot;
