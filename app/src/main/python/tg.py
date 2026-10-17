"""Telegram for scripts: work with the user's own linked account.

    import tg
    print(tg.me())
    for chat in tg.dialogs(10):
        print(chat["name"])
    tg.send("@some_user", "hello")

For anything else, use the full Telethon client through `tg.raw()`, which returns
(loop, client). Run a Telethon coroutine with loop.run_until_complete(...).
Docs: https://docs.telethon.dev
"""

import tg_user


def raw():
    return tg_user.open_session()


def _run(coroutine_factory):
    loop, client = tg_user.open_session()
    return loop.run_until_complete(coroutine_factory(client))


def _chat(value):
    text = str(value).strip()
    return int(text) if text.lstrip("-").isdigit() else text


def me():
    async def fetch(client):
        user = await client.get_me()
        return {"id": user.id, "username": user.username or "", "first_name": user.first_name or "", "phone": user.phone or ""}

    return _run(fetch)


def dialogs(limit=20):
    async def fetch(client):
        result = []
        async for dialog in client.iter_dialogs(limit=limit):
            result.append({"id": dialog.id, "name": dialog.name or "", "unread": dialog.unread_count})
        return result

    return _run(fetch)


def messages(chat, limit=20):
    async def fetch(client):
        result = []
        async for message in client.iter_messages(_chat(chat), limit=limit):
            result.append({
                "id": message.id,
                "date": message.date.isoformat() if message.date else "",
                "out": bool(message.out),
                "text": message.text or "",
            })
        return result

    return _run(fetch)


def send(chat, text):
    async def push(client):
        sent = await client.send_message(_chat(chat), text)
        return {"id": sent.id}

    return _run(push)


def close():
    tg_user.close_session()
