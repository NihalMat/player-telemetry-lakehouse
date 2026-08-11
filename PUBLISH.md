# Publish to GitHub before sending the linked resume

Create an empty public repository named `player-telemetry-lakehouse` under the `NihalMat` account. Do not initialize it with a README, license, or gitignore.

## Browser upload

1. Unzip the project.
2. Open the empty repository on GitHub.
3. Choose **Add file**, then **Upload files**.
4. Upload the contents of the unzipped folder so `README.md` appears at the repository root.
5. Commit the upload to `main`.

## Command line upload

From the unzipped project folder, run:

```bash
git init
git add .
git commit -m "Build player telemetry lakehouse and game analytics platform"
git branch -M main
git remote add origin https://github.com/NihalMat/player-telemetry-lakehouse.git
git push -u origin main
```

Wait for GitHub Actions to finish and confirm that the repository opens before sending the resume.
