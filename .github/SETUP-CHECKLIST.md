# GitHub Actions Setup Checklist

Use this checklist to track your CI/CD setup progress.

## Pre-Push Checklist

- [ ] Review workflow files in `.github/workflows/`
- [ ] Update Dependabot reviewer in `.github/dependabot.yml`
- [ ] Understand conventional commit format
- [ ] Read quick start guide: `docs/QUICK-START-CICD.md`

## Initial Setup (One-Time)

### 1. Push to GitHub
- [ ] Commit workflow files:
  ```bash
  git add .github/ docs/ CICD-IMPLEMENTATION-SUMMARY.md
  git commit -m "ci: implement GitHub Actions CI/CD pipeline"
  git push origin main
  ```

### 2. Enable GitHub Actions
- [ ] Go to: Settings → Actions → General
- [ ] Enable: "Allow all actions and reusable workflows"
- [ ] Enable: "Read and write permissions"
- [ ] Enable: "Allow GitHub Actions to create and approve pull requests"
- [ ] Click Save

### 3. Create Initial Version
- [ ] Create and push initial tag:
  ```bash
  git tag v0.1.0
  git push origin v0.1.0
  ```

### 4. Verify Setup
- [ ] Go to Actions tab
- [ ] Verify CI workflow runs successfully
- [ ] Verify Build & Push workflow runs successfully
- [ ] Check Releases tab for new release
- [ ] Check Packages tab for Docker image

### 5. Optional: Make Image Public
- [ ] Go to Packages
- [ ] Click package name
- [ ] Package settings → Change visibility → Public

## First Use

### 6. Test Conventional Commits
- [ ] Make a small change
- [ ] Commit with conventional message:
  ```bash
  git commit -m "feat: test CI/CD pipeline"
  ```
- [ ] Push to main
- [ ] Verify new version created (v0.2.0)
- [ ] Verify Docker image built
- [ ] Verify GitHub release created

### 7. Test Docker Image
- [ ] Pull image:
  ```bash
  docker pull ghcr.io/YOUR-USERNAME/d4c-portal:latest
  ```
- [ ] Run image locally:
  ```bash
  docker run -p 8080:8080 ghcr.io/YOUR-USERNAME/d4c-portal:latest
  ```
- [ ] Verify application starts

### 8. Update README (Optional)
- [ ] Add status badges (see `docs/README-CICD-BADGES.md`)
- [ ] Add CI/CD section to README
- [ ] Document Docker image location
- [ ] Add quick start instructions

## Configuration

### 9. Branch Protection (Recommended)
- [ ] Go to: Settings → Branches
- [ ] Add rule for `main` branch
- [ ] Require pull request reviews
- [ ] Require status checks to pass
- [ ] Include administrators in restrictions

### 10. Notifications (Optional)
- [ ] Set up Slack notifications
- [ ] Configure email alerts
- [ ] Set up Discord webhook

## Daily Workflow

### 11. Development Process
- [ ] Understand feature branch workflow
- [ ] Know how to write conventional commits
- [ ] Know how to check CI results
- [ ] Know how to view Docker images
- [ ] Know how to pull specific versions

### 12. Monitoring
- [ ] Bookmark Actions tab
- [ ] Bookmark Packages tab
- [ ] Bookmark Releases tab
- [ ] Set up email notifications for failures

## Troubleshooting Reference

### Common Issues
- [ ] Know how to check CI logs
- [ ] Know how to re-run failed workflows
- [ ] Know where to find documentation
- [ ] Know how to test Docker builds locally

### Resources Bookmarked
- [ ] `docs/GITHUB-ACTIONS-SETUP.md` - Complete guide
- [ ] `docs/QUICK-START-CICD.md` - Quick reference
- [ ] `docs/README-CICD-BADGES.md` - Badge templates
- [ ] `CICD-IMPLEMENTATION-SUMMARY.md` - Overview

## Team Onboarding

### 13. Share with Team
- [ ] Share conventional commits guide with team
- [ ] Explain version bumping rules
- [ ] Show how to check build status
- [ ] Demonstrate Docker image usage
- [ ] Review branch protection rules

### 14. Documentation
- [ ] Ensure team has access to docs
- [ ] Create team-specific guidelines (if needed)
- [ ] Document custom workflows (if any)

## Verification Commands

```bash
# View workflows
gh workflow list

# Check recent runs
gh run list --limit 5

# View latest release
gh release view

# List Docker image tags
docker search ghcr.io/YOUR-USERNAME/d4c-portal

# Pull and test
docker pull ghcr.io/YOUR-USERNAME/d4c-portal:latest
docker run --rm ghcr.io/YOUR-USERNAME/d4c-portal:latest java -version
```

## Success Criteria

Your setup is complete when:

- ✅ All checkboxes above are complete
- ✅ CI runs on every push/PR
- ✅ Docker images build automatically
- ✅ Versions increment correctly
- ✅ Releases appear in GitHub
- ✅ Team understands the workflow
- ✅ Documentation is accessible

## Quick Links

Replace `YOUR-USERNAME` with your GitHub username:

- Actions: `https://github.com/YOUR-USERNAME/d4c-portal/actions`
- Packages: `https://github.com/YOUR-USERNAME/d4c-portal/packages`
- Releases: `https://github.com/YOUR-USERNAME/d4c-portal/releases`
- Settings: `https://github.com/YOUR-USERNAME/d4c-portal/settings/actions`
- Workflows: `https://github.com/YOUR-USERNAME/d4c-portal/tree/main/.github/workflows`

## Next Steps After Setup

1. Start using conventional commits for all changes
2. Review Dependabot PRs weekly
3. Monitor build status regularly
4. Keep documentation updated
5. Share knowledge with team members

---

**Need Help?**
- Quick Start: `docs/QUICK-START-CICD.md`
- Full Guide: `docs/GITHUB-ACTIONS-SETUP.md`
- Troubleshooting: See documentation

**Ready to Start?**
Begin with item #1 in the checklist above!
