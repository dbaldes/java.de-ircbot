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
        The following is a dump of multiple news feeds of various sources.
        Read it, and give me a short, 300-character summary of what's going on in the world today:
        
        -----
        %s
        -----
        
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

        if (command.getArgLine().isEmpty()) {
            // No arguments
            String response = simpleAiService.query(NEWS_PROMPT.formatted(newsDump,
                    "What's in the news today?"));
            sendSplitMessage(command, response);
        } else {
            // With arguments
            String response = simpleAiService.query(NEWS_PROMPT.formatted(newsDump,
                    "What's in the news today? Focus on: '" + command.getArgLine().get()) + "'. "
                  + "If the news don't say anything about that topic, say just that.");
            sendSplitMessage(command, response);
        }
        return true;
    }

    private void sendSplitMessage(CommandEvent command, String message) {
        int maxLength = 420; // Keep well below the IRC 512-byte limit
        int start = 0;

        while (start < message.length()) {
            int end = Math.min(start + maxLength, message.length());

            // Ensure we don't split words by looking for the last space before end
            if (end < message.length()) {
                int lastSpace = message.lastIndexOf(' ', end);
                if (lastSpace > start) {
                    end = lastSpace; // Adjust split point to avoid breaking words
                }
            }

            // Send the chunk
            command.getEvent().getChannel().send().message(message.substring(start, end));
            start = end + 1; // Move to the next chunk
        }
    }
}
