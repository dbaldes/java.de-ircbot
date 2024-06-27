package de.throughput.ircbot.handler;

import java.util.List;
import java.util.Set;

import com.theokanning.openai.completion.CompletionResult;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;
import com.theokanning.openai.completion.CompletionRequest;

/**
 * Lagerfeld AI command handler.
 * <p>
 * !lagerfeld <text> - responds with an AI-generated Lagerfeld quote.
 */
@Component
@RequiredArgsConstructor
public class LagerfeldAiCommandHandler implements CommandHandler {

    private static final Logger LOG = LoggerFactory.getLogger(LagerfeldAiCommandHandler.class);

    private static final String MODEL_GPT_3_5_TURBO = "gpt-3.5-turbo";
    private static final int MAX_TOKENS = 100;

    private static final Command CMD_AILAGERFELD = new Command("lagerfeld", "lagerfeld <text> - responds with an AI-generated Lagerfeld quote.");

    public static final String PROMPT_TEMPLATE =
            """
            Erzeuge ein Lagerfeld-Zitat aus dem folgenden Wort oder der folgenden Phrase.
            Ein Lagerfeld-Zitat funktioniert so: 'Wer ..., hat die Kontrolle über sein Leben verloren.' 
            Verwende das Wort oder die Phrase, um einen grammatikalisch korrekten Satz als Lagerfeld-Zitat 
            zu bilden, zum Beispiel, indem du ein passendes Verb ergänzt. 
            Beispiel: Wort = Ohrenschützer; 
            Du antwortest: Wer Ohrenschützer trägt, hat die Kontrolle über sein Leben verloren.
            Füge der Antwort keine weiteren Kommentare hinzu.
            Also los. Das Wort oder die Phrase lautet: "%s"
            """;

    private final OpenAiService openAiService;

    @Override
    public Set<Command> getCommands() {
        return Set.of(CMD_AILAGERFELD);
    }

    @Override
    public boolean onCommand(CommandEvent command) {
        command.getArgLine()
                .ifPresentOrElse(
                        text -> respondWithQuote(command, text),
                        () -> command.respond(CMD_AILAGERFELD.getUsage()));
        return true;
    }

    private void respondWithQuote(CommandEvent command, String text) {
        try {
            String prompt = PROMPT_TEMPLATE.replace("\n", " ").formatted(text);

            var message = new ChatMessage(ChatMessageRole.USER.value(), prompt, command.getEvent().getUser().getNick());

            var request = ChatCompletionRequest.builder()
                    .model(MODEL_GPT_3_5_TURBO)
                    .maxTokens(MAX_TOKENS)
                    .messages(List.of(message))
                    .build();

            ChatCompletionResult completionResult = openAiService.createChatCompletion(request);

            ChatMessage responseMessage = completionResult.getChoices().get(0).getMessage();
            command.getEvent()
                    .getChannel()
                    .send()
                    .message("\"" + responseMessage.getContent() + "\" -- Karl Lagerfeld.");
        } catch (Exception e) {
            command.respond(e.getMessage());
            LOG.error(e.getMessage(), e);
        }
    }
}
