"""Telegram for scripts: work with the user's own linked account.

    import tg
    print(tg.me())
    for chat in tg.dialogs(10):
        print(chat["name"])
    tg.send("@some_user", "hello")

For anything else, use the full Telethon client through `tg.raw()`, which returns
(loop, client). Run a Telethon coroutine with loop.run_until_complete(...), e.g.

    from telethon.tl.functions.users import GetFullUserRequest
    loop, client = tg.raw()
    me = loop.run_until_complete(client.get_me())
    full = loop.run_until_complete(client(GetFullUserRequest(me)))
    print(full.full_user.about)
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


def messages(chat, limit=20):
    async def fetch(client):
        result = []
        async for message in client.iter_messages(await _entity(client, chat), limit=limit):
            result.append(Record(
                id=message.id,
                date=message.date.isoformat() if message.date else "",
                out=bool(message.out),
                text=message.text or "",
            ))
        return result

    return _run(fetch)


def send(chat, text):
    async def push(client):
        sent = await client.send_message(await _entity(client, chat), text)
        return Record(id=sent.id)

    return _run(push)


def close():
    tg_user.close_session()
