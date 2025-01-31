package de.throughput.ircbot.handler;

import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class NewsCommandHandler implements CommandHandler {

    private static final Command CMD_NEWS = new Command("news", "news [topic] - show a short summary of current news, optionally focusing on a topic");
    private static final String NEWS_PROMPT = """
        The following is a dump of multiple RSS feeds of various sources.
        Read it and give me a short, 400-character summary of what's going on in the world today:
        
        %s
        """;

    private final NewsService newsService;
    private final SimpleAiService simpleAiService;

    public NewsCommandHandler(NewsService newsService, SimpleAiService simpleAiService) {
        this.newsService = newsService;
        this.simpleAiService = simpleAiService;
    }

    @Override
    public Set<Command> getCommands() {
        return Set.of(CMD_NEWS);
    }

    @Override
    public boolean onCommand(CommandEvent command) {
        if (!CMD_NEWS.equals(command.getCommand())) {
            return false;
        }

        String newsDump = newsService.getNews();
        String basePrompt = NEWS_PROMPT.formatted(newsDump);

        if (command.getArgLine().isEmpty()) {
            // No arguments
            String response = simpleAiService.query(basePrompt);
            command.respond(response);
        } else {
            // With arguments
            String focusPrompt = "Focus on " + command.getArgLine().get() + ". " + basePrompt;
            String response = simpleAiService.query(focusPrompt);
            command.respond(response);
        }
        return true;
    }
}
