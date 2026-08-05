package ru.afknotifier.telegram;

/**
 * Результат отправки в Telegram. Нужен кнопке Test, чтобы показать игроку
 * конкретную причину отказа, а не просто «не получилось».
 *
 * @param ok          связка токен+chat_id сработала (HTTP 200 и ok:true)
 * @param httpCode    HTTP-код ответа, или -1 если запрос вообще не ушёл
 * @param description поле description из ответа Telegram либо текст ошибки
 */
public record SendResult(boolean ok, int httpCode, String description) {

	public static SendResult failure(int httpCode, String description) {
		return new SendResult(false, httpCode, description);
	}
}
