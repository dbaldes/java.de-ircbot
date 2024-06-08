package de.throughput.ircbot.handler;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;

/**
 * Command handler providing links to aviation information.
 */
@Component
public class AviationInfoCommandHandler implements CommandHandler {

    private static final Command CMD_FLIGHT = new Command("flight",
            "Usage: !flight <flight number> - "
                    + "a flight number consists of the two-letter IATA "
                    + "or three-letter ICAO airline code and "
                    + "a one to four digits flight number.");

    private static final Command CMD_AIRCRAFT = new Command("aircraft",
            "Usage: !aircraft <registration> - "
                    + "that doesn't look like an aircraft registration number.");

    private static final Command CMD_AIRPORT = new Command("airport",
            "Usage: !airport <IATA or ICAO code> - "
                    + "an airport IATA code is three letters, an ICAO code four letters.");

    private static final Command CMD_AVHERALD = new Command("avherald",
            "Usage: !avherald <search query>");

    @Override
    public Set<Command> getCommands() {
        return Set.of(CMD_FLIGHT, CMD_AIRCRAFT, CMD_AIRPORT, CMD_AVHERALD);
    }

    @Override
    public boolean onCommand(CommandEvent command) {
        boolean handled = false;
        if (command.getCommand()
                .equals(CMD_FLIGHT)) {
            saneFlightNumber(command.getArgLine()).ifPresentOrElse(
                    flightNumber ->
                            command.respond(
                                    "https://flightaware.com/live/flight/" + urlEnc(flightNumber)
                                            + " - https://www.flightradar24.com/data/flights/" + urlEnc(flightNumber)),
                    () -> usage(command));
            handled = true;
        } else if (command.getCommand()
                .equals(CMD_AIRCRAFT)) {
            saneAircraftRegistration(command.getArgLine()).ifPresentOrElse(
                    registration ->
                            command.respond("https://www.flightradar24.com/data/aircraft/" + urlEnc(registration)),
                    () -> usage(command));
            handled = true;
        } else if (command.getCommand()
                .equals(CMD_AIRPORT)) {
            saneAirportCode(command.getArgLine(),
                    iataCode ->
                            command.respond(
                                    "https://flightaware.com/live/airport/" + urlEnc(iataCode)
                                            + " - https://www.flightradar24.com/airport/" + urlEnc(iataCode)
                                            + " - https://www.world-airport-codes.com/search/?s=" + urlEnc(iataCode)),
                    icaoCode ->
                            command.respond(
                                    "https://flightaware.com/live/airport/" + urlEnc(icaoCode)
                                            + " - https://www.world-airport-codes.com/search/?s=" + urlEnc(icaoCode)),
                    () -> usage(command));
            handled = true;
        } else if (command.getCommand()
                .equals(CMD_AVHERALD)) {
            command.getArgLine()
                    .ifPresentOrElse(
                            query ->
                                    command.respond(String.format(
                                            "https://avherald.com/h?search_term=%s&opt=0&dosearch=1", urlEnc(query))),
                            () -> usage(command));
            handled = true;
        }
        return handled;
    }

    private void usage(CommandEvent command) {
        command.respond(command.getCommand()
                .getUsage());
    }

    private String urlEnc(String arg) {
        return URLEncoder.encode(arg, StandardCharsets.UTF_8);
    }

    private Optional<String> saneFlightNumber(Optional<String> input) {
        return input
                .map(flightNumber -> flightNumber.replace(" ", "")
                        .toUpperCase(Locale.ROOT))
                .filter(flightNumber -> flightNumber.matches("^[0-9A-Z]{2,3}\\d{1,4}$"));
    }

    private Optional<String> saneAircraftRegistration(Optional<String> input) {
        return input
                .map(registration -> registration.toUpperCase(Locale.ROOT))
                .filter(registration -> registration.matches("^[A-Z0-9-]{3,10}$"));
    }

    private void saneAirportCode(Optional<String> input, Consumer<String> iataCode, Consumer<String> icaoCode, Runnable invalid) {
        input
                .map(code -> code.toUpperCase(Locale.ROOT))
                .ifPresentOrElse(
                        code -> {
                            if (code.matches("^[A-Z]{3}$")) {
                                iataCode.accept(code);
                            } else if (code.matches("^[A-Z]{4}$")) {
                                icaoCode.accept(code);
                            } else {
                                invalid.run();
                            }
                        },
                        invalid);
    }

}
