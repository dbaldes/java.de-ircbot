package de.throughput.ircbot.handler;

import org.springframework.context.ApplicationEvent;

import lombok.Getter;

@Getter
public class TitleEvent extends ApplicationEvent {
  
  private String title;
  
  public TitleEvent(Object source, String title) {
    super(source);
    this.title = title;
  }
  
}
