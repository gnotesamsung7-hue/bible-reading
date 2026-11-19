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

## Turn on group sharing (one time)
Groups pass progress between phones through a free Firebase Realtime Database.
1. Go to console.firebase.google.com, **Create a project** (for example `bible-journey`). Analytics can be off.
2. **Build → Realtime Database → Create database**, location **Singapore (asia-southeast1)**, start in **locked mode**.
3. Open the **Rules** tab, replace everything with the rules below, and click **Publish**.
4. On the **Data** tab, copy the database URL at the top (it ends in `firebasedatabase.app`).
5. In this repo, edit `index.html`, find `const SYNC_URL='';`, paste the URL between the quotes, and commit. The web app and a new APK update automatically.

```json
{
  "rules": {
    "bj": {
      "groups": {
        "$code": {
          ".read": true,
          ".write": true
        }
      }
    }
  }
}
```
Anyone with a group's code can see that group's progress, and nobody can list all groups. Only reading progress is shared (name, chapters, streak), never recordings.

## Updating
Edit `index.html` and commit. A new APK builds automatically. It's signed with the same key (`android/app/biblejourney.keystore`), so it installs over the old one and keeps your progress. Don't delete or replace that keystore.
