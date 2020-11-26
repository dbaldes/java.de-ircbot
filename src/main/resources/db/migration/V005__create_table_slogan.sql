CREATE TABLE slogan (
  "id"           SERIAL,
  "timestamp"    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  "nick"         VARCHAR(255) NOT NULL,
  "channel"      VARCHAR(255) NOT NULL,
  "slogan"       TEXT NOT NULL,
  
  PRIMARY KEY (id)
);

ALTER TABLE slogan OWNER TO ircbot;
