package com.example.liushiudmjlinebot.controller;

import com.example.liushiudmjlinebot.service.ScoreService;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@LineMessageHandler
public class MahjongBotController {
	
	private static final Logger log = LoggerFactory.getLogger(MahjongBotController.class);
	
	@Autowired
	private ScoreService scoreService;

	@EventMapping
	public TextMessage handleTextMessage(MessageEvent<TextMessageContent> event) {
		String text = event.getMessage().getText().trim();
		
		log.info("input message = " + text);
		
		if (text.startsWith("/add")) {
			return new TextMessage(scoreService.addByFormattedLine(text.substring(4)));
		} else if (text.equals("/status") || text.equals("排行榜")) {
			return new TextMessage(scoreService.status());
		} else if (text.equals("/show") || text.equals("全部戰績")) {
			return new TextMessage(scoreService.showAllRounds());
		} else if (text.equals("/show") || text.equals("全部排行榜")) {
			return new TextMessage(scoreService.statusAll());
		}
		
		return null;
	}
}
