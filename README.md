# Bible Journey

Read the whole Bible on a plan.

*All Glory to God*

- **Web app:** https://gnotesamsung7-hue.github.io/bible-journey/
- **Android APK:** https://github.com/gnotesamsung7-hue/bible-journey/releases/latest/download/BibleJourney.apk

## What it does
- Pick a finish date (3 years, 2 years, 1 year, 6 months, 90 days, or your own). The app splits the 1,189 chapters into a daily reading and re-spreads what's left if you miss a day.
- Three reading orders: Genesis to Revelation, Old and New Testament together, or New Testament first.
- Two Bible versions: the King James Version is built in and works offline; the NIV opens from YouVersion (bible.com) inside the app, with a "Mark as read" button. Switch with the KJV/NIV buttons in the reader or in More.
- Books screen to tick off chapters, and "I've read this book before" for books finished before you started.
- Streaks, a 14-day chart, a reading-days calendar, and a projected finish date.
- Daily reminder notification (APK), celebrations when you finish a book, and a certificate when you finish the whole Bible.
- Backup and restore progress as a file. QR codes to share the app.
- **Record yourself reading** (microphone button in the reader): audio or front-camera video. The recording keeps going as you turn chapters. Recordings stay on the phone; "Save to phone" puts a copy in Movies/Bible Journey or Music/Bible Journey.
- **Group tab** for discipleship groups: start a group, share the 6-character code, and see each disciple's progress, streak, today's reading, and who hasn't read in 3+ days.

## Files
- `index.html` – the whole app (also used inside the APK)
- `kjv.json` – King James Version text
- `android/` – the Android wrapper
- `.github/workflows/build-apk.yml` – builds the APK on every commit and posts it to Releases

## Setup (all on github.com)
1. Create a **public** repo named `bible-journey` and upload everything, including the `.github` folder.
2. If the **Actions** tab shows "Get started" instead of "Build APK", the `.github` folder didn't upload. Go to **Add file → Create new file**, name it `.github/workflows/build-apk.yml`, and paste in the workflow.
3. **Settings → Pages**: Source "Deploy from a branch", branch `main`, folder `/ (root)`. The web app goes live in a minute or two.
4. Wait for the green check in **Actions**, then download `BibleJourney.apk` from **Releases**.

## Group sharing
Group progress goes through the Firebase Realtime Database `bible-journey-837f9` (Singapore), already set in `index.html` as `SYNC_URL`.

Security rules in use (Firebase console → Realtime Database → Rules):
```json
{
  "rules": {
    "bj": {
      "groups": {
        "$code": {
          ".read": true,
          ".write": "$code.matches(/^[A-Z0-9]{6}$/)",
          "info": {
            ".validate": "newData.hasChildren(['name', 'leader'])",
            "name": { ".validate": "newData.isString() && newData.val().length <= 40" },
            "leader": { ".validate": "newData.isString() && newData.val().length <= 20" },
            "leaderName": { ".validate": "newData.isString() && newData.val().length <= 40" },
            "created": { ".validate": "newData.isNumber()" },
            "$other": { ".validate": false }
          },
          "members": {
            "$uid": {
              ".validate": "$uid.matches(/^[A-Z0-9]{14}$/) && newData.child('name').isString() && newData.child('name').val().length <= 40"
            }
          },
          "removed": { "$uid": { ".validate": "newData.val() === true" } },
          "$other": { ".validate": false }
        }
      }
    }
  }
}
```
Anyone with a group's code can see that group's progress; nobody can list all groups. Only reading progress is shared (name, chapters, streak), never recordings.

## Updating
Edit `index.html` and commit. A new APK builds automatically. It's signed with the same key (`android/app/biblejourney.keystore`), so it installs over the old one and keeps your progress. Don't delete or replace that keystore.
