package de.throughput.ircbot.handler;

import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.pircbotx.hooks.events.MessageEvent;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PictureCommandHandlerTest {

    private JdbcTemplate jdbc;
    private ImageCommandHandler imageCommandHandler;
    private SimpleAiService simpleAiService;
    private PictureCommandHandler handler;

    @BeforeEach
    void setup() {
        jdbc = Mockito.mock(JdbcTemplate.class);
        imageCommandHandler = Mockito.mock(ImageCommandHandler.class);
        simpleAiService = Mockito.mock(SimpleAiService.class);
        handler = new PictureCommandHandler(jdbc, imageCommandHandler, simpleAiService);
    }

    @Test
    void respondsWhenNoFactoidExists() {
        MessageEvent event = mockMessageEvent();
        CommandEvent commandEvent = mockCommandEvent(event, "foobarbaz");
        when(jdbc.queryForList(eq("SELECT verb, fact FROM factoid WHERE key = ?"), eq("foobarbaz")))
                .thenReturn(List.of());

        handler.onCommand(commandEvent);

        verify(commandEvent).respond("I can't imagine foobarbaz.");
        verify(imageCommandHandler, never()).enqueueImageGeneration(Mockito.any(), anyString(), anyBoolean());
    }

    @Test
    void alwaysUsesPersonPrompt() {
        MessageEvent event = mockMessageEvent();
        when(jdbc.queryForList(eq("SELECT verb, fact FROM factoid WHERE key = ?"), eq("alice")))
                .thenReturn(List.of(
                        Map.of("verb", "is", "fact", "wearing a red hat"),
                        Map.of("verb", "are", "fact", "standing near a bicycle")
                ));
        when(simpleAiService.query(anyString())).thenReturn("A realistic portrait of Alice with a red hat next to a bicycle");

        CommandEvent commandEvent = mockCommandEvent(event, "alice");
        handler.onCommand(commandEvent);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> aiPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(simpleAiService).query(aiPromptCaptor.capture());
        verify(imageCommandHandler).enqueueImageGeneration(eq(commandEvent), promptCaptor.capture(), eq(false));
        assertThat(aiPromptCaptor.getValue()).contains("Input word: \"alice\"");
        assertThat(aiPromptCaptor.getValue()).contains("alice is wearing a red hat");
        assertThat(aiPromptCaptor.getValue()).contains("alice are standing near a bicycle");
        assertThat(promptCaptor.getValue()).contains("realistic portrait of Alice");
    }

    private static MessageEvent mockMessageEvent() {
        return Mockito.mock(MessageEvent.class);
    }

    private static CommandEvent mockCommandEvent(MessageEvent event, String argLine) {
        CommandEvent commandEvent = Mockito.mock(CommandEvent.class);
        when(commandEvent.getArgLine()).thenReturn(Optional.of(argLine));
        when(commandEvent.getEvent()).thenReturn(event);
        when(commandEvent.getCommand()).thenReturn(new Command("picture", ""));
        return commandEvent;
    }
}
