package de.throughput.ircbot.migration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Date;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Command line tool for importing DrRockit database dumps.
 */
//@SpringBootApplication
public class ImportDrRockItDump {

    private static final Pattern PATTERN_KEY_VALUE = Pattern.compile("^\\s*(\\S.*{1,255}\\S)\\s*###>###\\s*(\\S.*\\S)\\s*$");
    private static final Pattern PATTERN_KEY_NUMVALUE = Pattern.compile("^\\s*(\\S.*{1,255}\\S)\\s*###>###(-?\\d+)\\s*$");
    private static final Pattern PATTERN_SEEN = Pattern.compile("^\\s*(\\S.*{1,255}\\S)\\s*###>###(\\d+).(#java\\.de).\\s*(\\S.*\\S)\\s*$");

    private final JdbcTemplate jdbc;

    @Autowired
    public ImportDrRockItDump(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static void main(String[] args) throws IOException {
        SpringApplication.run(ImportDrRockItDump.class, args);
    }

    @Bean
    public CommandLineRunner runner() {
        return this::importMain;
    }

    @Transactional
    private void importMain(String... args) throws IOException {
        if (args.length != 2) {
            System.err.println("Usage: ImportDrRockItDump <file.dump> <type>");
            return;
        }

        Path path = Paths.get(args[0]);
        if (!Files.exists(path) || !Files.isReadable(path)) {
            System.err.printf("%s is not readable.\n", path);
            return;
        }


        if ("karma".equals(args[1])) {

            Consumer<Matcher> consumer = matcher -> {
                String key = matcher.group(1)
                        .toLowerCase();
                int karma = Integer.parseInt(matcher.group(2));

                System.out.printf("%s => %d\n", key, karma);
                jdbc.update("INSERT INTO karma (key, karma) VALUES (?, ?)", key, karma);
            };

            readDumpFile(path, PATTERN_KEY_NUMVALUE, consumer);

        } else if ("is".equals(args[1]) || "are".contentEquals(args[1])) {
            String verb = args[1];
            Consumer<Matcher> consumer = matcher -> {
                String key = matcher.group(1)
                        .toLowerCase();
                String fact = matcher.group(2);
                fact = fact.replace("<reply>", "")
                        .strip();

                if (fact.length() > 0
                        && !(key.startsWith("\"") && fact.endsWith("\""))
                        && !key.startsWith("^ youtube")) {

                    System.out.printf("%s %s %s\n", key, verb, fact);
                    jdbc.update("INSERT INTO factoid (key, verb, fact) VALUES(?, ?, ?)", key, verb, fact);
                }
            };

            readDumpFile(path, PATTERN_KEY_VALUE, consumer);
        } else if ("seen".equals(args[1])) {

            Consumer<Matcher> consumer = matcher -> {
                String nick = matcher.group(1)
                        .toLowerCase();
                long timestamp = Long.parseLong(matcher.group(2));
                String channel = matcher.group(3);
                String message = matcher.group(4);


                System.out.printf("%s => %d: %s> %s\n", nick, timestamp, channel, message);
                jdbc.update("INSERT INTO seen (channel, nick, timestamp, message) VALUES (?, ?, ?, ?)", channel, nick,
                        Date.from(Instant.ofEpochSecond(timestamp)), message);
            };

            readDumpFile(path, PATTERN_SEEN, consumer);
        }
    }

    private void readDumpFile(Path path, Pattern pattern, Consumer<Matcher> consumer) throws IOException {
        CharsetDecoder charsetDecoder = createCharsetDecoder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(path, StandardOpenOption.READ), charsetDecoder))) {
            reader.lines()
                    .filter(line -> !line.contains("\uFFFD"))
                    .map(pattern::matcher)
                    .filter(Matcher::matches)
                    .forEach(consumer);
        }
    }

    private CharsetDecoder createCharsetDecoder() {
        CharsetDecoder charsetDecoder = StandardCharsets.UTF_8.newDecoder();
        charsetDecoder.onMalformedInput(CodingErrorAction.REPLACE);
        charsetDecoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
        return charsetDecoder;
    }
}
