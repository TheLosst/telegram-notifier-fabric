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

	/**
	 * Лимит Telegram на поле text — 4096 символов (UTF-16 code units, то есть
	 * ровно то, что считает String.length()). Больше — 400 message is too long.
	 */
	private static final int MAX_TEXT_LENGTH = 4096;

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

		String payload = sanitize(text);
		if (payload.isEmpty()) {
			// Telegram отвечает 400 message text is empty — незачем ходить в сеть.
			return CompletableFuture.completedFuture(
					SendResult.failure(-1, "Пустой текст сообщения"));
		}

		// Экранирование берёт на себя URL-кодирование тела запроса: &, =, %, #,
		// переносы строк и эмодзи внутри text сломать запрос не могут.
		// parse_mode намеренно не задаётся — текст уходит как есть, поэтому
		// Markdown-символы (* _ [ ] ` и прочие) не требуют экранирования и
		// не вызывают "can't parse entities". Если когда-нибудь понадобится
		// разметка, экранировать текст придётся здесь же.
		String body = "chat_id=" + URLEncoder.encode(chatId, StandardCharsets.UTF_8)
				+ "&text=" + URLEncoder.encode(payload, StandardCharsets.UTF_8);

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

	/**
	 * Приводит текст к тому, что Telegram точно примет: убирает пустоту по краям
	 * и режет по лимиту длины. Шаблоны правит пользователь, так что слишком
	 * длинный текст — вопрос времени, а обрезанное сообщение лучше отказа.
	 */
	private static String sanitize(String text) {
		if (text == null) {
			return "";
		}

		String result = text.strip();
		if (result.length() <= MAX_TEXT_LENGTH) {
			return result;
		}

		int cut = MAX_TEXT_LENGTH - 1;
		// Не разрезаем суррогатную пару пополам: иначе на конце окажется
		// «половина» эмодзи и битый символ.
		if (Character.isHighSurrogate(result.charAt(cut - 1))) {
			cut--;
		}
		return result.substring(0, cut) + "…";
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
