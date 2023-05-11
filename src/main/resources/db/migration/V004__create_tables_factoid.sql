CREATE TABLE factoid
(
    "key"  VARCHAR(255) NOT NULL,
    "verb" VARCHAR(10)  NOT NULL,
    "fact" TEXT         NOT NULL,
  
  PRIMARY KEY ("key", "verb")
);

ALTER TABLE factoid OWNER TO ircbot;
