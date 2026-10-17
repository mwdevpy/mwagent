"""Entry points for the app's built-in telegram_* tools. Each returns a JSON string."""

import json

import tg
import tg_user


def _reply(produce):
    try:
        return json.dumps(produce(), ensure_ascii=False, default=str)
    except Exception as error:  # reported back to the model as a tool error
        return json.dumps({"error": "%s: %s" % (type(error).__name__, error)}, ensure_ascii=False)
    finally:
        tg_user.close_session()


def me():
    def produce():
        info = dict(tg.me())
        try:
            from telethon.tl.functions.users import GetFullUserRequest

            _, client = tg.raw()
            full = tg.run(client(GetFullUserRequest("me")))
            info["about"] = full.full_user.about or ""
        except Exception:
            info["about"] = ""
        return info

    return _reply(produce)


def dialogs(limit):
    return _reply(lambda: [dict(item) for item in tg.dialogs(int(limit))])


def messages(chat, limit):
    return _reply(lambda: [dict(item) for item in tg.messages(chat, int(limit))])


def send(chat, text):
    return _reply(lambda: dict(tg.send(chat, text)))
