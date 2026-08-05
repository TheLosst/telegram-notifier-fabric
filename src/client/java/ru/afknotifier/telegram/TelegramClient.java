package ru.afknotifier.telegram;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ru.afknotifier.AfkNotifierClient;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Отправка сообщений через Telegram Bot API.
 *
 * Все вызовы асинхронные (sendAsync) — блокирующий HTTP в игровом потоке
 * фризит клиент.
 */
public final class TelegramClient {

	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	private TelegramClient() {
	}

	/**
	 * Шлёт сообщение и возвращает результат — он нужен кнопке Test.
	 * Метод никогда не бросает исключений: любая ошибка приезжает как SendResult.
	 */
	public static CompletableFuture<SendResult> send(String botToken, String chatId, String text) {
		if (botToken == null || botToken.isBlank() || chatId == null || chatId.isBlank()) {
			return CompletableFuture.completedFuture(
					SendResult.failure(-1, "Не заполнен токен или chat_id"));
		}

		String body = "chat_id=" + URLEncoder.encode(chatId, StandardCharsets.UTF_8)
				+ "&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8);

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("https://api.telegram.org/bot" + botToken + "/sendMessage"))
				.timeout(Duration.ofSeconds(15))
				.header("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
				.build();

		return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
				.thenApply(TelegramClient::parse)
				.exceptionally(e -> {
					Throwable cause = e.getCause() != null ? e.getCause() : e;
					AfkNotifierClient.LOGGER.error("Ошибка отправки в Telegram", cause);
					return SendResult.failure(-1, cause.getClass().getSimpleName() + ": " + cause.getMessage());
				});
	}

	/** Разбираем ответ Telegram: поле ok и человекочитаемое description. */
	private static SendResult parse(HttpResponse<String> response) {
		int code = response.statusCode();
		String description = "";
		boolean ok = false;

		try {
			JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
			ok = json.has("ok") && json.get("ok").getAsBoolean();
			if (json.has("description")) {
				description = json.get("description").getAsString();
			}
		} catch (RuntimeException e) {
			description = "Не удалось разобрать ответ Telegram: " + response.body();
		}

		if (ok && code == 200) {
			return new SendResult(true, code, description.isBlank() ? "OK" : description);
		}
		if (description.isBlank()) {
			description = "HTTP " + code;
		}
		return SendResult.failure(code, description);
	}

	/**
	 * Отправка «по событию»: результат только логируем, в чат не спамим
	 * и мод не роняем.
	 */
	public static void sendAndLog(String botToken, String chatId, String text) {
		send(botToken, chatId, text).thenAccept(result -> {
			if (!result.ok()) {
				AfkNotifierClient.LOGGER.warn("Telegram отклонил сообщение: HTTP {} — {}",
						result.httpCode(), result.description());
			}
		});
	}
}
