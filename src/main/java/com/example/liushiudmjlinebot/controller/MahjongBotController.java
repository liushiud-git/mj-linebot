package com.example.liushiudmjlinebot.controller;

import com.example.liushiudmjlinebot.service.ScoreService;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;
import org.springframework.beans.factory.annotation.Autowired;

@LineMessageHandler
public class MahjongBotController {

    @Autowired
    private ScoreService scoreService;

    @EventMapping
    public TextMessage handleTextMessage(MessageEvent<TextMessageContent> event) {
        String text = event.getMessage().getText().trim();
        if (text.startsWith("/add")) {
            String res = scoreService.addRound(text);
            return new TextMessage(res);
        } else if (text.equals("/status")) {
            return new TextMessage(scoreService.status());
        } else if (text.equals("/show10")) {
            return new TextMessage(scoreService.show10());
        }
        return new TextMessage("指令：\n/add (日期可選) A +2000 B -1500 ...\n/status\n/show10");
    }
}
