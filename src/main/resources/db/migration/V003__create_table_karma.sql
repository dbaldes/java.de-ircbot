CREATE TABLE karma
(
    "key"   VARCHAR(255) NOT NULL,
    "karma" INTEGER      NOT NULL,
  
  PRIMARY KEY ("key")
);

ALTER TABLE karma OWNER TO ircbot;
