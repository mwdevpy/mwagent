import asyncio
import json
import threading

from telethon import TelegramClient, events
from telethon.sessions import StringSession

import tg

_lock = threading.Lock()
_state = {"thread": None, "halt": None, "loop": None, "client": None}


class SessionInvalid(Exception):
    pass


def start(api_id, api_hash, session, sink):
    stop()
    halt = threading.Event()
    thread = threading.Thread(target=_serve, args=(int(api_id), api_hash, session, sink, halt), name="tg-watch", daemon=True)
    with _lock:
        _state.update(thread=thread, halt=halt, loop=None, client=None)
    thread.start()


def stop():
    with _lock:
        thread, halt, loop, client = _state["thread"], _state["halt"], _state["loop"], _state["client"]
        _state.update(thread=None, halt=None, loop=None, client=None)
    if halt is None:
        return
    halt.set()
    if loop is not None and client is not None and loop.is_running():
        asyncio.run_coroutine_threadsafe(client.disconnect(), loop)
    thread.join(10)


def _serve(api_id, api_hash, session, sink, halt):
    delay = 5
    while not halt.is_set():
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        try:
            loop.run_until_complete(_listen(api_id, api_hash, session, sink, halt, loop))
            delay = 5
        except SessionInvalid as error:
            _emit(sink, {"error": str(error)})
            return
        except Exception as error:
            _emit(sink, {"error": "%s: %s" % (type(error).__name__, error)})
            delay = min(delay * 2, 300)
        finally:
            loop.close()
        halt.wait(delay)


async def _listen(api_id, api_hash, session, sink, halt, loop):
    client = TelegramClient(StringSession(session), api_id, api_hash)
    await client.connect()
    if not await client.is_user_authorized():
        await client.disconnect()
        raise SessionInvalid("The linked Telegram session is no longer valid")
    with _lock:
        if _state["halt"] is halt:
            _state.update(loop=loop, client=client)
    if halt.is_set():
        await client.disconnect()
        return

    async def on_message(event):
        _emit(sink, await _describe(event))

    client.add_event_handler(on_message, events.NewMessage(incoming=True))
    await client.get_me()
    await client.run_until_disconnected()


async def _describe(event):
    message = event.message
    chat = await event.get_chat()
    sender = await event.get_sender()
    media = type(message.media).__name__.replace("MessageMedia", "").lower() if message.media is not None else None
    return {
        "id": message.id,
        "chat_id": event.chat_id,
        "chat": tg._name(chat),
        "sender": tg._name(sender),
        "username": getattr(sender, "username", None) or "",
        "text": (message.text or "")[:4000],
        "media": media,
        "direct": bool(event.is_private),
    }


def _emit(sink, payload):
    try:
        sink.accept(json.dumps(payload, ensure_ascii=False, default=str))
    except Exception:
        pass
