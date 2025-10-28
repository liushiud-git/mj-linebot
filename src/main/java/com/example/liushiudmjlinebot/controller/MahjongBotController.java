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
			return new TextMessage(scoreService.addByFormattedLine(text.substring(4)));
		} else if (text.startsWith("/del")) {
			return new TextMessage(scoreService.deleteByDateCommand(text));
		} else if (text.equals("/status") || text.equals("目前戰績")) {
			return new TextMessage(scoreService.status());
		} else if (text.equals("/show") || text.equals("顯示")) {
			return new TextMessage(scoreService.showAllRounds());
		}
		return new TextMessage("指令：\n" + "1) 新增：/add 20251017 戰績：隨 -7700,蕭 -2100,馬 5700,堂 3700,鳥 400\n"
				+ "2) 刪除：/del 20251017\n" + "3) 查詢：/status\n" + "4) 列出：/show");
	}
}
