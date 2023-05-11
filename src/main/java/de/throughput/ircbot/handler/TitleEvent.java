package de.throughput.ircbot.handler;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class TitleEvent extends ApplicationEvent {

    private String title;

    public TitleEvent(Object source, String title) {
        super(source);
        this.title = title;
    }

}
