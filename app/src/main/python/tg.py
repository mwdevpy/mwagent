"""Telegram for scripts: work with the user's own linked account.

    import tg
    print(tg.me())
    for chat in tg.dialogs(10):
        print(chat["name"])
    tg.send("@some_user", "hello")
    tg.send_file("me", "report.pdf", caption="for later")
    for found in tg.search("invoice"):
        print(found.chat, found.text)

For anything else, use the full Telethon client through `tg.raw()`, which returns
(loop, client). Run any Telethon call with tg.run(...), e.g.

    from telethon.tl.functions.users import GetFullUserRequest
    loop, client = tg.raw()
    me = tg.run(client.get_me())
    full = tg.run(client(GetFullUserRequest(me)))
    print(full.full_user.about)

Do not create your own TelegramClient and do not import telethon.sync: use tg.
Docs: https://docs.telethon.dev
"""

import tg_user


class Record(dict):
    """A result that works both as tg.me()["username"] and tg.me().username."""

    def __getattr__(self, name):
        try:
            return self[name]
        except KeyError:
            raise AttributeError(name)


def raw():
    return tg_user.open_session()


def run(call):
    """Finishes a Telethon call on the linked account's loop and returns its result."""
    loop, _ = tg_user.open_session()
    return tg_user.wait(loop, call)


def _run(coroutine_factory):
    loop, client = tg_user.open_session()
    return loop.run_until_complete(coroutine_factory(client))


def _chat(value):
    text = str(value).strip()
    return int(text) if text.lstrip("-").isdigit() else text


async def _entity(client, chat):
    """Finds a chat by @username, phone, numeric id, "me", or by its name as shown in the chat list."""
    target = _chat(chat)
    try:
        return await client.get_entity(target)
    except (ValueError, TypeError):
        pass
    wanted = str(chat).strip().lstrip("@").casefold()
    fallback = None
    async for dialog in client.iter_dialogs():
        name = (dialog.name or "").casefold()
        if name == wanted:
            return dialog.entity
        if fallback is None and wanted and wanted in name:
            fallback = dialog.entity
    if fallback is not None:
        return fallback
    raise ValueError("No chat matches %r. Use tg.dialogs() to see the chat names." % chat)


def me():
    async def fetch(client):
        user = await client.get_me()
        return Record(
            id=user.id,
            username=user.username or "",
            first_name=user.first_name or "",
            last_name=user.last_name or "",
            phone=user.phone or "",
            premium=bool(getattr(user, "premium", False)),
            bot=bool(user.bot),
        )

    return _run(fetch)


def dialogs(limit=20):
    async def fetch(client):
        result = []
        async for dialog in client.iter_dialogs(limit=limit):
            result.append(Record(id=dialog.id, name=dialog.name or "", unread=dialog.unread_count))
        return result

    return _run(fetch)


def _message(message, chat_name=None):
    record = Record(
        id=message.id,
        date=message.date.isoformat() if message.date else "",
        out=bool(message.out),
        text=message.text or "",
    )
    if message.media is not None:
        record["media"] = type(message.media).__name__.replace("MessageMedia", "").lower()
    if message.reply_to_msg_id:
        record["reply_to"] = message.reply_to_msg_id
    if chat_name is not None:
        record["chat"] = chat_name
        record["chat_id"] = message.chat_id
    return record


def _name(entity):
    if entity is None:
        return ""
    title = getattr(entity, "title", None)
    if title:
        return title
    return " ".join(part for part in (getattr(entity, "first_name", None), getattr(entity, "last_name", None)) if part)


def messages(chat, limit=20):
    async def fetch(client):
        result = []
        async for message in client.iter_messages(await _entity(client, chat), limit=limit):
            result.append(_message(message))
        return result

    return _run(fetch)


def search(query, chat=None, limit=20):
    """Messages containing `query`, newest first: in one chat, or in all chats when chat is None."""
    async def fetch(client):
        target = await _entity(client, chat) if chat not in (None, "") else None
        result = []
        async for message in client.iter_messages(target, search=query, limit=limit):
            result.append(_message(message, _name(message.chat)))
        return result

    return _run(fetch)


def send(chat, text, reply_to=None):
    async def push(client):
        sent = await client.send_message(await _entity(client, chat), text, reply_to=reply_to)
        return Record(id=sent.id)

    return _run(push)


def send_file(chat, path, caption="", reply_to=None):
    async def push(client):
        sent = await client.send_file(await _entity(client, chat), path, caption=caption or None, reply_to=reply_to)
        return Record(id=sent.id)

    return _run(push)


def close():
    tg_user.close_session()
