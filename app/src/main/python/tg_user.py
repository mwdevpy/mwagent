"""Telegram user-account login through Telethon (MTProto user API).

Every call takes the Telethon StringSession saved from the previous step, so the
flow works without keeping a client alive between screens. Each function returns
a JSON string; on a known Telegram error it returns {"error": "<code>"}.
"""

import asyncio
import inspect
import json

from telethon import TelegramClient
from telethon.errors import (
    FloodWaitError,
    PasswordHashInvalidError,
    PhoneCodeExpiredError,
    PhoneCodeInvalidError,
    PhoneNumberInvalidError,
    SessionPasswordNeededError,
)
from telethon.sessions import StringSession

KNOWN_ERRORS = (
    PhoneCodeInvalidError,
    PhoneCodeExpiredError,
    PhoneNumberInvalidError,
    PasswordHashInvalidError,
)


def _client(api_id, api_hash, session):
    return TelegramClient(StringSession(session or ""), int(api_id), api_hash)


def _run(coroutine):
    try:
        return json.dumps(asyncio.run(coroutine))
    except KNOWN_ERRORS as error:
        return json.dumps({"error": type(error).__name__})
    except FloodWaitError as error:
        return json.dumps({"error": "FloodWaitError", "seconds": error.seconds})


async def _send_code(api_id, api_hash, phone, session):
    client = _client(api_id, api_hash, session)
    await client.connect()
    try:
        sent = await client.send_code_request(phone)
        return {"session": client.session.save(), "phone_code_hash": sent.phone_code_hash}
    finally:
        await client.disconnect()


def send_code(api_id, api_hash, phone, session):
    return _run(_send_code(api_id, api_hash, phone, session))


async def _sign_in(api_id, api_hash, session, phone, code, phone_code_hash, password):
    client = _client(api_id, api_hash, session)
    await client.connect()
    try:
        if password:
            await client.sign_in(password=password)
        else:
            try:
                await client.sign_in(phone=phone, code=code, phone_code_hash=phone_code_hash)
            except SessionPasswordNeededError:
                return {"need_password": True, "session": client.session.save()}
        me = await client.get_me()
        return {
            "session": client.session.save(),
            "user_id": me.id,
            "username": me.username or "",
            "first_name": me.first_name or "",
        }
    finally:
        await client.disconnect()


def sign_in(api_id, api_hash, session, phone, code, phone_code_hash, password):
    return _run(_sign_in(api_id, api_hash, session, phone, code, phone_code_hash, password or ""))


async def _log_out(api_id, api_hash, session):
    client = _client(api_id, api_hash, session)
    await client.connect()
    try:
        if await client.is_user_authorized():
            await client.log_out()
        return {"ok": True}
    finally:
        await client.disconnect()


def log_out(api_id, api_hash, session):
    return _run(_log_out(api_id, api_hash, session))


# ---- Used by scripts (see tg.py). The app sets the credentials before each run. ----

_creds = {"api_id": None, "api_hash": None, "session": None}
_session = {"loop": None, "client": None}


def configure(api_id, api_hash, session):
    """Called by the app before a script runs. Passing None clears the account for scripts."""
    _creds["api_id"] = int(api_id) if api_id is not None else None
    _creds["api_hash"] = api_hash
    _creds["session"] = session
    close_session()


def wait(loop, value):
    """Runs a Telethon call to completion. After `import telethon.sync` its methods return results directly."""
    return loop.run_until_complete(value) if inspect.isawaitable(value) else value


def open_session():
    """Returns (loop, client) for the linked account, connected and signed in. Reused for the whole script."""
    if _session["client"] is not None:
        asyncio.set_event_loop(_session["loop"])
        return _session["loop"], _session["client"]
    if _creds["api_id"] is None or not _creds["session"]:
        raise RuntimeError("No Telegram account is linked. Link it in Settings > Telegram first.")
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    client = TelegramClient(StringSession(_creds["session"]), _creds["api_id"], _creds["api_hash"])
    wait(loop, client.connect())
    if not wait(loop, client.is_user_authorized()):
        wait(loop, client.disconnect())
        _forget(loop)
        raise RuntimeError("The linked Telegram session is no longer valid. Link the account again in Settings > Telegram.")
    _session["loop"] = loop
    _session["client"] = client
    return loop, client


def close_session():
    client, loop = _session["client"], _session["loop"]
    _session["client"] = None
    _session["loop"] = None
    if client is not None:
        asyncio.set_event_loop(loop)
        try:
            wait(loop, client.disconnect())
        finally:
            _forget(loop)


def _forget(loop):
    loop.close()
    try:
        current = asyncio.get_event_loop_policy().get_event_loop()
    except RuntimeError:
        current = None
    if current is loop:
        asyncio.set_event_loop(None)
