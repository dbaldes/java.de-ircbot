package de.throughput.ircbot.handler;

import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.pircbotx.PircBotX;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reminder command handler.
 * !remindme <when>: <text> - reminds the user of <text> at a specified time.
 */
@Component
public class ReminderCommandHandler implements CommandHandler {

    private static final Command CMD_REMINDME = new Command("remindme",
            "remindme <when>: <text> - set a reminder. <when> can be a date (YYYY-MM-DD) or a duration (e.g., '1 year', '3 days'). "
            + "Lowest resolution is one day.");
    public static final int MAX_MESSAGE_LENGTH = 200;

    private final JdbcTemplate jdbc;
    private final PircBotX bot;

    @Autowired
    public ReminderCommandHandler(JdbcTemplate jdbc, @Lazy PircBotX bot) {
        this.jdbc = jdbc;
        this.bot = bot;
    }

    @Override
    public Set<Command> getCommands() {
        return Set.of(CMD_REMINDME);
    }

    @Override
    public boolean onCommand(CommandEvent command) {
        if (CMD_REMINDME.equals(command.getCommand())) {
            String[] parts = command.getArgLine().orElse("").split(":", 2);
            if (parts.length < 2) {
                command.respond(CMD_REMINDME.getUsage());
                return true;
            }

            String when = parts[0].trim();
            String message = parts[1].trim();

            if (message.length() > MAX_MESSAGE_LENGTH) {
                command.respond("Message too long. Please keep it below 200 characters.");
                return true;
            }

            try {
                LocalDate date = parseWhenSpecification(when);
                storeReminder(command.getEvent().getChannel().getName(), command.getEvent().getUser().getNick(), date, when, message);
                command.respond("OK, I will remind you on " + date.format(DateTimeFormatter.ISO_LOCAL_DATE));
            } catch (DateTimeParseException e) {
                command.respond("Could not parse the date. Please use the format 'yyyy-MM-dd' or durations like '1 year', '3 days'.");
            }
            return true;
        }
        return false;
    }

    private static LocalDate parseWhenSpecification(String when) throws DateTimeParseException {
        // Specific date format
        if (when.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return LocalDate.parse(when);
        }

        // Duration format (e.g., '1 year', '3 days')
        Pattern pattern = Pattern.compile("(\\d+)\\s*(year|month|week|day)s?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(when);
        if (!matcher.matches()) {
            throw new DateTimeParseException("Invalid date format", when, 0);
        }

        int amount = Integer.parseInt(matcher.group(1));
        String unit = matcher.group(2).toLowerCase();
        return switch (unit) {
            case "year" -> LocalDate.now().plusYears(amount);
            case "month" -> LocalDate.now().plusMonths(amount);
            case "week" -> LocalDate.now().plusWeeks(amount);
            case "day" -> LocalDate.now().plusDays(amount);
            default -> throw new DateTimeParseException("Unknown date unit", when, 0);
        };
    }

    /**
     * Scheduled task to check for reminders.
     */
    @Scheduled(fixedDelay = 600000) // every 10 minutes
    public void checkReminders() {
        LocalDate today = LocalDate.now();
        List<Reminder> reminders = jdbc.query("SELECT * FROM reminder WHERE ondate <= ?", new Object[]{today}, reminderRowMapper());

        reminders.forEach(reminder -> {
            if (bot.getUserChannelDao().containsChannel(reminder.getChannel())) {
                bot.getUserChannelDao().getChannel(reminder.getChannel()).getUsers().stream()
                        .filter(user -> user.getNick().equals(reminder.getNick()))
                        .findFirst()
                        .ifPresent(user -> {
                            String message = String.format("%s: you asked me on %s to remind you today of this: %s. You're welcome.",
                                    reminder.getNick(), reminder.getTimestamp().toLocalDate(), reminder.getMessage());
                            bot.send().message(reminder.getChannel(), message);
                            deleteReminder(reminder.getId());
                        });
            }
        });
    }

    private void storeReminder(String channel, String nick, LocalDate ondate, String whenspec, String message) {
        jdbc.update("INSERT INTO reminder (nick, channel, ondate, whenspec, message) VALUES (?, ?, ?, ?, ?)",
                nick, channel, ondate, whenspec, message);
    }

    private void deleteReminder(int id) {
        jdbc.update("DELETE FROM reminder WHERE id = ?", id);
    }

    private RowMapper<Reminder> reminderRowMapper() {
        return (rs, rowNum) -> new Reminder(
                rs.getInt("id"),
                rs.getTimestamp("timestamp").toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime(),
                rs.getString("nick"),
                rs.getString("channel"),
                rs.getDate("ondate").toLocalDate(),
                rs.getString("whenspec"),
                rs.getString("message")
        );
    }

    @Getter
    @AllArgsConstructor
    private static class Reminder {
        private final int id;
        private final LocalDateTime timestamp;
        private final String nick;
        private final String channel;
        private final LocalDate ondate;
        private final String whenspec;
        private final String message;
    }
}
