/**
 * Semantic Release Configuration
 *
 * This configuration enables automatic versioning and changelog generation
 * based on conventional commit messages.
 *
 * Commit Message Format:
 * - feat: New feature → Minor version bump (1.0.0 → 1.1.0)
 * - fix: Bug fix → Patch version bump (1.0.0 → 1.0.1)
 * - BREAKING CHANGE: → Major version bump (1.0.0 → 2.0.0)
 * - docs: Documentation only → No release
 * - chore: Maintenance → No release
 *
 * Examples:
 * - feat: add user authentication
 * - fix: resolve login timeout issue
 * - feat!: redesign API structure (BREAKING CHANGE)
 */

module.exports = {
  branches: [
    'main',
    'master',
    {
      name: 'develop',
      prerelease: 'beta',
      channel: 'beta'
    },
    {
      name: 'alpha',
      prerelease: true
    }
  ],

  plugins: [
    // Analyze commits to determine version bump
    [
      '@semantic-release/commit-analyzer',
      {
        preset: 'conventionalcommits',
        releaseRules: [
          { type: 'feat', release: 'minor' },
          { type: 'fix', release: 'patch' },
          { type: 'perf', release: 'patch' },
          { type: 'revert', release: 'patch' },
          { type: 'docs', release: false },
          { type: 'style', release: false },
          { type: 'chore', release: false },
          { type: 'refactor', release: 'patch' },
          { type: 'test', release: false },
          { type: 'build', release: false },
          { type: 'ci', release: false },
          { scope: 'no-release', release: false },
        ],
        parserOpts: {
          noteKeywords: ['BREAKING CHANGE', 'BREAKING CHANGES', 'BREAKING']
        }
      }
    ],

    // Generate release notes
    [
      '@semantic-release/release-notes-generator',
      {
        preset: 'conventionalcommits',
        presetConfig: {
          types: [
            { type: 'feat', section: '✨ Features' },
            { type: 'fix', section: '🐛 Bug Fixes' },
            { type: 'perf', section: '⚡ Performance Improvements' },
            { type: 'revert', section: '⏪ Reverts' },
            { type: 'docs', section: '📚 Documentation', hidden: false },
            { type: 'style', section: '💎 Styles', hidden: true },
            { type: 'chore', section: '🔧 Miscellaneous Chores', hidden: true },
            { type: 'refactor', section: '♻️ Code Refactoring' },
            { type: 'test', section: '✅ Tests', hidden: true },
            { type: 'build', section: '🏗️ Build System', hidden: true },
            { type: 'ci', section: '👷 CI/CD', hidden: true }
          ]
        }
      }
    ],

    // Update CHANGELOG.md
    [
      '@semantic-release/changelog',
      {
        changelogFile: 'CHANGELOG.md',
        changelogTitle: '# Changelog\n\nAll notable changes to the DATA4CIRC Portal will be documented in this file.\n\nThe format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),\nand this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).'
      }
    ],

    // Update version in pom.xml
    [
      '@semantic-release/exec',
      {
        prepareCmd: 'mvn versions:set -DnewVersion=${nextRelease.version} -DgenerateBackupPoms=false'
      }
    ],

    // Create GitHub release
    [
      '@semantic-release/github',
      {
        assets: [
          {
            // The runnable jar specifically (classifier "exec" — see pom.xml's
            // spring-boot-maven-plugin config); target/ also has a plain library jar
            // published separately to GitHub Packages, which an unqualified glob would
            // also match.
            path: 'target/*-exec.jar',
            label: 'Application JAR (v${nextRelease.version})'
          }
        ],
        successComment: false,
        releasedLabels: ['released'],
        addReleases: 'bottom'
      }
    ],

    // Commit changes back to repository
    [
      '@semantic-release/git',
      {
        assets: ['CHANGELOG.md', 'pom.xml'],
        message: 'chore(release): ${nextRelease.version} [skip ci]\n\n${nextRelease.notes}'
      }
    ]
  ]
};
