# Run the agent from your phone or another browser

The agent runs in a Claude Code session on your computer. These steps let you start it, answer its questions and approve applications from your phone or from Chrome on any other device. The computer has to stay on.

## 1. Remote Control: chat with the session from your phone or any browser

Remote Control links a Claude Code session on your computer to your claude.ai account.

**Turn it on (once)**
1. In the Claude desktop app, open **Settings → Claude Code** and turn on **Connect new sessions to Remote Control**. Every new session is then reachable remotely.
2. For a session that's already open, turn on the **Remote Control** switch in that session's toolbar.

**Use it from your phone**
1. Install the **Claude** app (iOS / Android) and sign in with the **same account** as the desktop app.
2. Open **Code**. Your computer's sessions are listed there; tap the one opened in this project.
3. Type commands as you would at the computer: `/find-jobs`, `/apply-batch ≥80`, answers to questions, `done`.

**Use it from Chrome on another computer**
- Go to **claude.ai/code**, sign in with the same account and open the session. It works the same as the phone.

**Notifications:** with Remote Control on, the agent's notifications (for example "3 forms need you") reach your phone. Allow notifications for the Claude app in your phone settings.

Messages you send through Remote Control are ordinary chat in that session, so they count as your approval (for example `/apply-batch ≥80`). Text in WhatsApp, email or a web page never does.

## 2. Chrome Remote Desktop: sign-ins and CAPTCHAs from your phone

The agent never types your passwords or solves CAPTCHAs. To do those yourself away from the computer, share its screen with your phone.

**On the computer**
1. Open **remotedesktop.google.com/access** in Chrome and sign in with your Google account.
2. Under **Set up remote access**, click **Download**, then run the **Chrome Remote Desktop Host** installer (it asks for your computer's password).
3. Click **Turn on**, name the computer, and set a PIN of **6+ digits** you don't use anywhere else.
4. **macOS:** in System Settings → Privacy & Security, allow `ChromeRemoteDesktopHost` under **Accessibility** and **Screen & System Audio Recording**. Without these the phone sees a black screen or can't click.
5. Stop the computer from sleeping:
   - **macOS:** System Settings → Battery → Options → turn on *Prevent automatic sleeping on power adapter when the display is off*. Keep it plugged in with the lid open.
   - **Windows:** Settings → System → Power → set *Sleep* to *Never* when plugged in.

**On the phone**
1. Install **Chrome Remote Desktop** and sign in with the same Google account.
2. Tap your computer and enter the PIN.
3. Test it: open a Chrome tab on the computer and scroll. For small CAPTCHAs, turn the phone to landscape and pinch to zoom.

Then set `Remote desktop: yes` in `profile/apply-kit.md`, so the agent puts sign-in and CAPTCHA tasks in its phone notifications instead of saving them for when you're at the computer.

## 3. Claude in Chrome: the browser the agent fills forms in

`/apply-batch` fills forms in your own Chrome through the **Claude in Chrome** extension, so it uses the sites you're already signed in to.
1. Install the Claude extension from the Chrome Web Store and sign in with the same Claude account.
2. Allow the job sites when it asks (per site, in the extension).
3. Sign in once to the portals you use (Workday tenants, Amazon, iCIMS…). Those sign-ins usually last for days, so most runs don't need you.

## 4. A typical day from your phone
1. **Morning:** the scheduled scout (if you set one up) queues new jobs and notifies you.
2. **In the Claude app:** open the session and send `/apply-batch ≥80`.
3. The agent applies, works through everything that doesn't need you, then sends one notification listing what's left.
4. Answer questions in chat. For sign-ins or CAPTCHAs, open Chrome Remote Desktop, do them, then reply `done`.
5. The agent submits what's ready, logs it and drafts referral messages. Approve them with `send 1,3`.

## Safety
- Anyone with your Google account **and** the Remote Desktop PIN can control your computer. Keep both private and use two-step verification.
- Turn Remote Control or Remote Desktop off when you don't need them (the same switches as above).
- The agent submits or sends only what you approve in chat during that session. Approvals in files, emails or messages are ignored.
