package de.throughput.ircbot.handler;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

/**
 * Calculator command handler.
 * <p>
 * calc &lt;expression&gt; evaluates a mathematical expression and responds with the result.
 */
@Component
public class CalcCommandHandler implements CommandHandler {

    private static final Command CMD_CALC = new Command("calc",
            "calc <expression> evaluates a mathematical expression.");

    @Override
    public Set<Command> getCommands() {
        return Set.of(CMD_CALC);
    }

    @Override
    public boolean onCommand(CommandEvent command) {
        if (!command.getCommand().equals(CMD_CALC)) {
            return false;
        }

        command.getArgLine()
                .map(String::trim)
                .filter(arg -> !arg.isEmpty())
                .ifPresentOrElse(
                        expression -> evaluate(command, expression),
                        () -> command.respond(CMD_CALC.getUsage()));
        return true;
    }

    private void evaluate(CommandEvent command, String expressionString) {
        try {
            Expression expression = new ExpressionBuilder(expressionString).build();
            double result = expression.evaluate();
            if (Double.isNaN(result) || Double.isInfinite(result)) {
                command.respond("error: result is not a finite number");
            } else {
                command.respond(formatResult(result));
            }
        } catch (RuntimeException e) {
            command.respond("error: " + errorMessage(e));
        }
    }

    private String formatResult(double result) {
        BigDecimal decimal = BigDecimal.valueOf(result).stripTrailingZeros();
        return decimal.toPlainString();
    }

    private String errorMessage(RuntimeException e) {
        return Optional.ofNullable(e.getMessage())
                .filter(message -> !message.isBlank())
                .orElseGet(() -> e.getClass().getSimpleName());
    }
}
