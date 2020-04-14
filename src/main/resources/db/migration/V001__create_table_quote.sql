CREATE TABLE quote (
  "id"           SERIAL,
  "timestamp"    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  "nick"         VARCHAR(255) NOT NULL,
  "channel"      VARCHAR(255) NOT NULL,
  "message"      TEXT NOT NULL,
  
  PRIMARY KEY (id)
);

ALTER TABLE quote OWNER TO ircbot;

CREATE INDEX idx_quote_nick ON quote ("nick");
CREATE INDEX idx_quote_channel ON quote ("channel");
