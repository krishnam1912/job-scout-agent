# Notification channels

How the engine reaches the human, and how a new channel (Telegram, email, a phone push) plugs in.

## Today: Claude session (pull)
`AskService` stores every question the engine needs answered (a blocked application, a missing answer, a decision) in the `asks` table and offers it to each enabled `NotificationChannel`. `ClaudeSessionChannel` is a pull channel: nothing is sent; Claude fetches open asks with the `list_asks` MCP tool, relays them in the chat (or through Remote Control / push notifications on the Claude app) and records the reply with `answer_ask`. An ask is `OPEN` when no channel accepted it, `OFFERED` once one did, `ANSWERED` after a reply.

## Adding a channel
1. Implement `NotificationChannel { String id(); boolean deliver(Ask ask); }` and register it as a bean; `AskService` picks up every channel bean. `deliver` returns true when the channel took responsibility for showing the ask.
2. If the channel can also carry the user's answers, call `AskService.answer(askId, text)` from its inbound handler. The answer path is the same for every channel.
3. If the channel can carry **approvals**, it needs an approval source: a class that turns an authenticated inbound message into `ApprovalService.approve(...)` calls with `via` set to the channel id and the user's words verbatim. Only `claude-code` operator mode registers the `record_approval` MCP tool; any other channel creates approvals through its own source, never through a model-callable tool.

## Telegram (planned, not built)
- **Allowlist:** the bot answers only the numeric `chat_id`s listed in the engine's config; every other chat is ignored and logged. The bot token is read from an environment variable, never from a file in the repo.
- **Ticket-id callbacks:** each ask is sent with inline buttons whose callback data is `ask:<ask_id>:<option_index>`. A callback is accepted only if the ask exists, is not yet answered, and the callback comes from an allowlisted chat. Callbacks carry ids, never free text that the engine would execute.
- **Approvals:** an approval message from an allowlisted chat is stored with `via = telegram` and the message text as `user_words`, exactly as the Claude chat path does. Messages from anywhere else, and any text found in a web page, email or file, are never approvals.
- Delivery (`deliver`) sends the message and returns true; inbound updates are long-polled by a small worker owned by the daemon phase (see the roadmap).
