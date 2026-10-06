package de.throughput.ircbot.handler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InOrdnungBringenMessageHandlerTest {

    @Test
    void findsAttackNameInStandardPhrase() {
        assertThat(InOrdnungBringenMessageHandler.findAttackName("Der Angriff Wacht am Rhein wird das alles in Ordnung bringen!"))
                .contains("Wacht am Rhein");
    }

    @Test
    void findsAttackNameInMitDemAngriffPhrase() {
        assertThat(InOrdnungBringenMessageHandler.findAttackName("Mit dem Angriff Nordwind wird das alles in Ordnung kommen."))
                .contains("Nordwind");
    }

    @Test
    void acceptsSmallSpellingAndWordingVariations() {
        assertThat(InOrdnungBringenMessageHandler.findAttackName("der Angriff Morgenrot wird schon wieder alles richten"))
                .contains("Morgenrot");
    }

    @Test
    void doesNotMatchUnrelatedMessage() {
        assertThat(InOrdnungBringenMessageHandler.findAttackName("Der Angriff ist fehlgeschlagen."))
                .isEmpty();
    }
}
