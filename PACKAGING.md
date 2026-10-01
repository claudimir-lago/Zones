# Windows portable releases

Publishing a release with a `vMAJOR.MINOR.PATCH` tag runs **Windows portable release**.
The tag must match the version in `pom.xml`. Update `USER_GUIDE.html` for the version
and user-visible changes before tagging. Publish releases through the GitHub UI or
an authenticated user tool; releases created with another workflow's `GITHUB_TOKEN`
do not trigger a new release workflow.

The workflow tests and builds the exact tagged application source on a standard
Windows runner. It uses Eclipse Temurin 17, jlink and jpackage to create a portable
app image, then attaches a ZIP, SHA-256 checksum and standalone English HTML guide.
No AI service or API key is used. There is no scheduled polling, Actions artifact
storage or dependency cache configured by this workflow.

The packaging tools come from the workflow revision; both user guides and application
code always comes from the release tag. This allows the initial v0.2.0 release,
which predates packaging, to receive a package without changing its tag. Both
source and packaging revisions are recorded in BUILD-INFO.txt. The bundled runtime
version is recorded too; later rebuilds may pick up a newer Temurin 17 security update.

For an existing release, open Actions → Windows portable release → Run workflow,
select main and enter its tag (for example `v0.5.2`). Existing assets are not
overwritten: investigate failures before retrying or replacing a published binary.

The checks exercise reading synthetic data, baseline processing, HVL peak fitting,
mobility conversion, diagnostic ZIP export, Swing rendering with the bundled runtime
and startup of the native launcher. These are packaging checks, not a replacement
for scientific validation. The existing suite includes explicitly skipped legacy
and opt-in tests. Test results appear in the workflow log.

## Local build

Use Windows x64, PowerShell 7, Git, Maven and Eclipse Temurin JDK 17 (x64).
Check out the requested release tag in a clean source directory, then run:

```powershell
./package-windows.ps1 -SourceDir ../Zones-tag -Tag v0.5.2 -JavaHome 'C:/path/to/temurin-17'
```

Optional: `-Maven` selects Maven's executable; `-MavenRepository` selects a local
dependency cache; `-OutputDir` selects the build destination. Each build gets a
new directory; the script does not recursively delete existing output.

Keep the original license texts, JFreeChart source, application source archive and
Java notices with the binary. The launcher is not code signed. Publish the ZIP as a
Release asset (not a short-lived Actions artifact). Users extract the entire ZIP and
open Zones.exe; no system Java is required.
