ALTER TABLE slogan
    ADD COLUMN "count" INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN "usedstamp" TIMESTAMP WITH TIME ZONE;