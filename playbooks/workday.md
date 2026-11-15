# Workday and other sign-in portals (site mechanics only)

**These portals need an account and password. Claude never enters passwords or creates accounts.** If the tab isn't already signed in by the user, skip and list "needs sign-in".

- Workday tenants (`<co>.wd5.myworkdayjobs.com`) go down for maintenance at times: retry once, then skip.
- Multi-page flow: My Information → Experience (resume upload, education) → Application Questions → Self-ID → Review.
- Some ask for a **passport-size photo** or ID numbers: skip and list.
- Education "field of study" lists can lack your exact branch; choose the closest and note it.
- **iCIMS** portals load the form in an iframe that auto-saves and reloads on edits, which shifts click positions. Don't type by coordinates there; use `find`/`form_input`, or leave it to the user.
- **Oracle HCM** (e.g. `*.fa.oraclecloud.com`) and **Eightfold** portals also need the user's sign-in.
