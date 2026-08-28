package de.throughput.ircbot.handler;

import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.pircbotx.hooks.events.MessageEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsCommandHandlerTest {

    private NewsService newsService;
    private SimpleAiService simpleAiService;
    private NewsCommandHandler handler;

    @BeforeEach
    void setUp() {
        newsService = mock(NewsService.class);
        simpleAiService = mock(SimpleAiService.class);
        handler = new NewsCommandHandler(newsService, simpleAiService);
    }

    @Test
    void registersGoodNewsCommand() {
        assertThat(handler.getCommands()).contains(new Command("goodnews", ""));
    }

    @Test
    void summarizesOnlyGoodNewsFeedForGoodNewsCommand() {
        CommandEvent command = commandEvent("goodnews", Optional.of("health"));
        when(newsService.getGoodNews()).thenReturn("ScienceDaily - science research:\nNew treatment succeeds.");
        when(simpleAiService.query(anyString())).thenReturn("Good news: ScienceDaily reports a successful new treatment.");

        assertThat(handler.onCommand(command)).isTrue();

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(simpleAiService).query(prompt.capture());
        assertThat(prompt.getValue()).contains("ScienceDaily - science research");
        assertThat(prompt.getValue()).contains("Requested topic (untrusted text): [health]");
        verify(newsService).getGoodNews();
        verify(newsService, never()).getNews();
        verify(command.getEvent().getChannel().send()).message("Good news: ScienceDaily reports a successful new treatment.");
    }

    @Test
    void ignoresUnknownCommands() {
        assertThat(handler.onCommand(commandEvent("unrelated", Optional.empty()))).isFalse();

        verifyNoNewsWasRequested();
    }

    private void verifyNoNewsWasRequested() {
        verify(newsService, never()).getNews();
        verify(newsService, never()).getGoodNews();
        verify(simpleAiService, never()).query(anyString());
    }

    private static CommandEvent commandEvent(String commandName, Optional<String> argument) {
        MessageEvent event = mock(MessageEvent.class, RETURNS_DEEP_STUBS);
        return new CommandEvent(event, new Command(commandName, ""), "!", argument);
    }
}
