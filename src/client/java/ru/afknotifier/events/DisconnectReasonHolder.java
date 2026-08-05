package ru.afknotifier.events;

/**
 * Переносит причину дисконнекта из миксина в обработчик события.
 *
 * Событие ClientPlayConnectionEvents.DISCONNECT причину не передаёт — она есть
 * только в аргументе ClientCommonPacketListenerImpl#onDisconnect, поэтому её
 * перехватывает миксин и кладёт сюда.
 */
public final class DisconnectReasonHolder {

	private static volatile String reason;

	private DisconnectReasonHolder() {
	}

	public static void set(String value) {
		reason = value;
	}

	/** Читает причину и сразу очищает, чтобы она не «протекла» в следующий дисконнект. */
	public static String consume() {
		String value = reason;
		reason = null;
		return value;
	}
}
