"""Unread Telegram chats with their latest messages. params: limit (chats, default 15), per_chat (messages per chat, default 3)"""

import tg

limit = int(params.get("limit", 15))
per_chat = int(params.get("per_chat", 3))

unread = [chat for chat in tg.dialogs(200) if chat["unread"]][:limit]
if not unread:
    print("No unread chats")
for chat in unread:
    print("%s: %d unread" % (chat["name"], chat["unread"]))
    for message in reversed(tg.messages(chat["id"], min(per_chat, chat["unread"]))):
        text = message["text"] or "[%s]" % message.get("media", "media")
        print("  - " + text.replace("\n", " ")[:200])
