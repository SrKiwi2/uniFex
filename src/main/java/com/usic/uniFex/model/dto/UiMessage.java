package com.usic.uniFex.model.dto;

public class UiMessage {
      private final String type;  // success | info | warning | danger  (Bootstrap)
  private final String title;
  private final String text;

  public UiMessage(String type, String title, String text) {
    this.type = type; this.title = title; this.text = text;
  }
  public String getType() { return type; }
  public String getTitle() { return title; }
  public String getText() { return text; }
}
