package de.throughput.ircbot.handler;

import com.openai.client.OpenAIClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Lagerfeld AI command handler.
 * <p>
 * !lagerfeld <text> - responds with an AI-generated Lagerfeld quote.
 */
@Component
@RequiredArgsConstructor
public class LagerfeldAiCommandHandler implements CommandHandler {

    private static final Logger LOG = LoggerFactory.getLogger(LagerfeldAiCommandHandler.class);

    private static final ChatModel MODEL_GPT_3_5_TURBO = ChatModel.GPT_3_5_TURBO;
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

    private final OpenAIClient openAiClient;

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

            ChatCompletionUserMessageParam userMessage = ChatCompletionUserMessageParam.builder()
                    .content(prompt)
                    .name(command.getEvent().getUser().getNick())
                    .build();

            ChatCompletionCreateParams request = ChatCompletionCreateParams.builder()
                    .model(MODEL_GPT_3_5_TURBO)
                    .maxCompletionTokens((long) MAX_TOKENS)
                    .messages(List.of(ChatCompletionMessageParam.ofUser(userMessage)))
                    .build();

            ChatCompletion completionResult = openAiClient.chat().completions().create(request);

            String response = completionResult.choices()
                    .stream()
                    .findFirst()
                    .flatMap(choice -> choice.message().content())
                    .orElse("");
            command.getEvent()
                    .getChannel()
                    .send()
                    .message("\"" + response + "\" -- Karl Lagerfeld.");
        } catch (Exception e) {
            command.respond(e.getMessage());
            LOG.error(e.getMessage(), e);
        }
    }
}
